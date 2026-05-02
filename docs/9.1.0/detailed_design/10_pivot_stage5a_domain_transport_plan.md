# Pivot Stage 5A: Large-Domain Transport Plan

## 1. 目标与非目标 (Goals and Non-Goals)

**目标 (Goals):**
- 解决 Pivot 非可加 rollup (Non-additive rollup) 中 surviving domain 超过 `500` 时被 fail-closed 的限制，提供可落地的生产级域传输机制 (Domain Transport)。
- 定义 `DomainTransportPlan` 和 `DomainRelationRenderer` 以支持跨数据库的大域参数传递，并将其安全包裹在现有的 queryModel 引擎生命周期内。

**非目标 (Non-Goals):**
- 不改变公开的 Pivot DSL。
- 不绕过 `queryModel` 的核心生命周期，必须保留 permissions、preAgg、physical column checks、sanitizer 和 logging 等机制。
- Stage 5A production 行为已在 B2 里打开，但仅通过内部 carrier 生效；公共 Pivot DSL 不变。
- 不引入级联 Generate (Cascade Generate) 和多级 TopN (Multi-level TopN) 语义（此为 Stage 5B 内容）。

## 2. 为什么不能绕过 queryModel 生命周期

如果直接在引擎外部（例如直接拼装最终 SQL 字符串）处理 Domain Transport，会导致致命的架构断层：
1. **权限安全漏洞**：绕过 `systemSlice` 和列级/行级数据访问控制。
2. **预聚合失效**：无法重用并下推命中 `preAgg` 的物化视图。
3. **参数注入与绑定问题**：打破 `queryModel` 对参数绑定顺序和过滤的安全管理 (`sanitizer`)。
4. **日志与审计缺失**：监控指标、审计和缓存依赖于标准的 `SemanticRequestContext` 与模型查询管线。

因此，域传输必须作为一个内部执行计划参数（Internal Plan），由底层的 `JdbcModelQueryEngine` 消费并渲染。

## 3. 核心设计：DomainTransportPlan 与 DomainRelationRenderer

### Internal `DomainTransportPlan` 职责
- **数据结构**：承载二维的 tuple 矩阵 (list of lists) 以及对应的轴字段名 (`axisFields`)。
- **生命周期**：在 `NonAdditiveRollupExecutor` 识别到 `domain > 500` 时构建（取代直接抛异常）。
- **传递机制**：通过 `SemanticQueryRequest` 的内置上下文或透传字段流入底层的查询构建引擎中。

### Dialect-facing `DomainRelationRenderer` 职责
- **SPI 契约**：面向各方言提供的渲染器组件。
- **职责**：接收 `DomainTransportPlan`，生成一段可与之 `JOIN` 的关系代数 SQL（如 CTE 或 Derived Table）及平铺的参数列表。
- **安全性判定**：有权检查参数量级，若超出方言安全限制（如 `65535`）或不支持，则拒绝渲染，向外抛出 fallback 信号。

## 4. 方言矩阵 (Dialect Matrix)

| 方言 (Dialect) | 域传输策略 (Transport Strategy) | NULL-Safe 匹配策略 |
|---------|---------------------|-----------------|
| **SQLite** | `VALUES` CTE (`WITH _d(f1) AS (VALUES (?)...)`) | `IS` / `IS NOT DISTINCT FROM` (SQLite 3.39+) |
| **PostgreSQL** | `VALUES` CTE (`WITH _d(f1) AS (VALUES (?)...)`) | `IS NOT DISTINCT FROM` |
| **MySQL 8.0** | `VALUES` CTE (`WITH _d(f1) AS (VALUES ROW(?)...)`) | `<=>` |
| **MySQL 5.7** | Derived Table (`(SELECT ? AS f1 UNION ALL SELECT ?)`) | `<=>` |

**关于 MySQL 5.7 的明确结论：**
MySQL 5.7 **支持 Derived Table (UNION ALL) 策略** 作为 fallback 方案以兼容大域。
但渲染器必须严格限制 Tuple 数量（受限于 `max_allowed_packet` 机制及 SQL 解析树深度）。若生成的 SQL 超出安全限制，**必须明确返回 unsupported 状态，继续走 fail-closed 逻辑**，不退化为不稳定的内存大结果集拉取。

## 5. 核心规则

### 支持与拒绝条件 (Support & Refusal Conditions)
- **支持**：方言实现了对应的 `DomainRelationRenderer`，且当前 domain size 与维度数乘积小于方言的绑定参数上限。
- **拒绝条件 (Fail-closed)**：当 `Renderer` 不存在或参数量溢出时，触发 `NonAdditiveRollupDomainTooLargeException` 继续阻断，**严禁静默 fallback**。

### Tuple Domain Relation 形态
以 PostgreSQL 为例：
```sql
WITH _pivot_domain_transport(col_a, col_b) AS (
    VALUES (?, ?), (?, ?)
)
```

### NULL-Safe Tuple Matching 策略
由于 Surviving Domain 内部常包含 `NULL` 成员（特别是 Subtotal 补全或缺失数据场景），在与 Base Relation 进行 JOIN 时，**必须使用 NULL-safe 的比较操作符**，否则 `NULL = NULL` 评估为 `UNKNOWN` 将导致数据丢失：
```sql
INNER JOIN _pivot_domain_transport _d
  ON _base.col_a IS NOT DISTINCT FROM _d.col_a
 AND _base.col_b IS NOT DISTINCT FROM _d.col_b
```

### 参数顺序规则 (Parameter Order Rules)
- 参数列表顺序必须与 SQL 字符串中的 `?` 严格对应。
- 若采用 **CTE 策略**：由于 CTE 出现在 `WITH` 块最前，`domain params` 必须**前置**拼接到总参数列表中，随后才是 Base Relation 的参数。
- 若采用 **Derived Table 策略**：Derived Table 出现在 `FROM/JOIN` 内，参数需按渲染生成的精确 AST 顺序注入。

### 数据流 (Data Flow)
`preAgg + systemSlice + domain transport + non-additive subtotal` 的流转：
1. `PivotTopNSqlPlanner` 完成，提取 surviving domain。
2. `NonAdditiveRollupExecutor` 识别 `domain > 500`，构造 `DomainTransportPlan` 代替抛异常。
3. 将 Plan 附带于向 `SemanticQueryService` 发起的辅助查询请求中。
4. 底层提取 `preAgg` 与 `systemSlice` 生成 Base Relation。
5. `DomainRelationRenderer` 将 Plan 转化为 CTE 或 JOIN 表，包裹 Base Relation。
6. 结合 Pivot 要求的 Non-additive Metric 聚合（或批量 UNION ALL），并合并参数执行查询。

### 迁移策略：domain <= 500 与 domain > 500
- **`domain <= 500`**：继续使用 `OR-of-AND` 切片逻辑，避免引入不必要的 CTE 或 Derived Table 解析开销，保证绝大多数小域场景的极速响应。
- **`domain > 500`**：启用 `DomainTransportPlan`，走大域传输分支。如遇方言能力不足，Fail-closed。

## 6. SQL Oracle 示例

### 1. 单维 domain (Single Domain)
**场景**: 行轴 `[category]`, surviving domain 包含 600 个元素。
**PostgreSQL 示例**:
```sql
WITH _pivot_domain_transport(category) AS (
    VALUES (?), (?), ... -- 600 个参数
)
SELECT _base.category, COUNT(DISTINCT _base.user_id) AS metric_1
FROM (
    -- queryModel lifecycle applied (preAgg + systemSlice)
    SELECT category, user_id FROM sales_fact WHERE tenant_id = ?
) _base
INNER JOIN _pivot_domain_transport _d
  ON _base.category IS NOT DISTINCT FROM _d.category
GROUP BY _base.category
```

### 2. 多维 tuple domain (Multi-dimension tuple)
**场景**: 行轴 `[category, product]`。
**MySQL 8.0 示例**:
```sql
WITH _pivot_domain_transport(category, product) AS (
    VALUES ROW(?, ?), ROW(?, ?)
)
SELECT _base.category, _base.product, MAX(_base.price)
FROM ( /* base relation */ ) _base
INNER JOIN _pivot_domain_transport _d
  ON _base.category <=> _d.category AND _base.product <=> _d.product
GROUP BY _base.category, _base.product
```

### 3. 含 NULL tuple
**场景**: tuple 为 `["A", NULL]`，参数正常传入 `null`。
`VALUES ROW(?, ?)` 对应占位符填入 `null`，由于 JOIN 时采用了 `<=>` (MySQL) 或 `IS NOT DISTINCT FROM` (PostgreSQL)，它能够正确匹配 `_base.product IS NULL` 的事实记录。

### 4. TopN surviving domain
**场景**: 行轴限制为 Top 1000 categories。
Pivot Pipeline 首先执行主查询获得这 1000 个存活 category。随后在非可加度量的辅助查询 (Aux query) 时，这 1000 个 category 构建为 `DomainTransportPlan`，注入辅助查询限定范围，确保小计不混入跌出 TopN 的类目。

### 5. 非加性 subtotal/grandTotal
**场景**: Grand Total (无 GROUP BY)。
```sql
WITH _pivot_domain_transport(category) AS (
    VALUES ROW(?), ROW(?)
)
SELECT COUNT(DISTINCT _base.user_id) AS grand_total
FROM ( /* base relation */ ) _base
INNER JOIN _pivot_domain_transport _d
  ON _base.category <=> _d.category
-- 没有 GROUP BY，全局聚合仅针对 surviving domain 范围
```

### 6. 不匹配 / 拒绝案例 (Refusal cases)
**场景**: MySQL 5.7 参数数量达 70000 溢出。
渲染器拦截并拒绝渲染，系统回退抛出：
（保持 Fail-closed，保护数据库稳定性）。

## 7. B2 Implementation Contract

### Internal Carrier
To avoid leaking the transport mechanism into the public DSL or `SemanticQueryRequest` API, `DomainTransportPlan` is carried by `SemanticRequestContext` and forwarded through `ModelResultContext.extData` under an internal key. This ensures:
- Full decoupling from API serialization.
- Preservation of the existing queryModel lifecycle and `JdbcModelQueryEngine` isolation.
- Existing permission, systemSlice, preAgg, parameterization, SQL logging, and sanitizer paths still execute before the SQL is rendered.

### Production Hook

B2 wires the plan in the non-additive subtotal/grandTotal auxiliary query path:

1. `NonAdditiveRollupExecutor` keeps the existing `domain <= 500` `OR-of-AND` slice path.
2. For `domain > 500`, it creates one or more internal `DomainTransportPlan` instances and attaches them to the derived `SemanticRequestContext`.
3. `SemanticQueryServiceV3Impl` forwards the plans into `ModelResultContext.extData`.
4. `JdbcModelQueryEngine` renders each plan and injects a `WHERE EXISTS` semi-join against the base relation before grouping and aggregation.
5. Renderer refusal propagates as a fail-closed domain transport error; it does not silently fall back to an unbounded in-memory query.

### MySQL 8.0 Version Gate
The `VALUES ROW(...)` syntax is only supported in MySQL 8.0.19 and later.
- The MySQL8 renderer must check the database version (if available) or assume conservative bounds.
- If the version is < 8.0.19 or unknown, the renderer should refuse (or fallback to the derived-table strategy).

### MySQL 5.7 Derived-Table Safe Limits
MySQL 5.7 uses the `SELECT ? AS f1 UNION ALL SELECT ?` strategy. To prevent stack overflows during parsing and exceedance of `max_allowed_packet`, the renderer must enforce:
- **Max Tuples**: 2000
- **Max Bind Params**: 10000
- **Max SQL Length**: 1MB
- **Refusal**: If any threshold is exceeded, the renderer must throw a `DomainTransportRefusalException`, triggering the `domain > 500` fail-closed exception.

### Renderer Result Contract
The `DomainRelationRenderer` will return a `DomainRelationRenderResult` containing:
- **Rendered SQL Fragment**: The CTE definition or Derived Table subquery.
- **Params**: The flattened list of parameter values in the exact order required by the SQL fragment.
- **Join Predicate**: The ON clause (e.g., `base.col <=> _d.col`) to be injected into the base relation.
- **Placement**: An indicator (e.g., `CTE` or `DERIVED_TABLE`) so the query engine knows where to inject the fragment and params.
- **Refusal Reason**: If rendering is unsupported or unsafe, a clear reason why (e.g., "Too many parameters for MySQL 5.7").

### Verification Evidence

- SQLite queryModel parity for the large-domain transport path passed with real SQL oracle comparison.
- MySQL8 large-domain parity passed with JDBC metadata version-gated renderer selection.
- PostgreSQL large-domain parity passed with CTE transport.
- Existing direct `addAxisDomainSlice` tests still assert fail-closed behavior for callers that try to build an oversized in-memory slice directly.
