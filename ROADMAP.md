# Foggy Data MCP Bridge — Roadmap

## fsscript QM Compose（跨模型查询编排）

### 背景

单个 QM 内部已 JOIN 大量 TM（如 TMS 运单 QM 由十几个 TM JOIN 而成），权限控制在 QM 层。QM 对外等价于一个视图——外部消费者不需要关心内部 JOIN 细节。

QM Compose 解决的是**QM 之间的组合查询**——用 fsscript 编排多个 QM，支持 JOIN、参数传递、二次计算。

### 约束

- **仅支持同数据源 QM 组合**：参与组合的 QM 必须连接同一个 JDBC 数据源。跨数据源场景不在范围内。
- 这个约束大幅简化实现——CTE/子查询在同一个数据库连接内执行，无需分布式事务或内存合并。

### 核心设计：QM 即视图

每个 QM 已生成完整 SQL（含内部 TM JOIN + 权限 slice），对外等价于一个**黑盒视图**。组合层只需：
1. 调用各 QM 的 `toSql` 模式获取 SQL 文本 + 参数
2. 将 SQL 包装为 CTE 或子查询
3. 外层拼接 JOIN / UNION
4. 合并参数列表，单次执行

**不改动查询引擎内部**——SQL 生成与执行在 `JdbcModelQueryEngine` 中已解耦（`analysisQueryRequest()` 生成 SQL → `innerSql`/`values` 字段存储 → `queryJdbc()` 执行）。组合层在生成后、执行前截取。

### 三种组合模式

#### 模式 1：ID 下推（结果作为参数）

第一个 QM 的结果注入到第二个 QM 的 slice 中。

```javascript
const topCustomers = dsl({
    model: 'SaleOrderQM',
    columns: ['partner$id'],
    orderBy: ['-amountTotal'],
    limit: 10
});
const leads = dsl({
    model: 'CrmLeadQM',
    columns: ['partner$caption', 'stage$caption', 'expectedRevenue'],
    slice: [{ field: 'partner$id', op: 'in', value: topCustomers.column('partner$id') }]
});
return leads;
```

适合：第一步筛选 ID 列表，第二步按 ID 过滤明细。

#### 模式 2：CTE/子查询 JOIN（SQL 层合并）

将两个 QM 各自生成的 SQL 包装为 CTE（或子查询），在 SQL 层直接 JOIN。**QM 即视图**——组合层不关心各自内部 TM 细节。

```javascript
const composed = dsl({
    model: 'SaleOrderQM',
    columns: ['partner$caption', 'sum(amountTotal) as totalSales'],
}).withJoin({
    model: 'CrmLeadQM',
    on: 'partner$id',
    columns: ['count(id) as leadCount'],
    type: 'left'
});
return composed;
```

引擎生成的 SQL 结构：

```sql
-- 支持 CTE 的数据库（PostgreSQL 12+、MySQL 8.0+、SQL Server 2012+、SQLite 3.35+）
WITH cte_orders AS (
    -- SaleOrderQM 完整 SQL（黑盒，一个字符不改）
    SELECT partner_id, partner_name, SUM(amount_total) AS totalSales
    FROM sale_order JOIN res_partner ON ...
    WHERE ...  -- 权限 slice 已在内部注入
    GROUP BY partner_id, partner_name
),
cte_leads AS (
    -- CrmLeadQM 完整 SQL（黑盒）
    SELECT partner_id, COUNT(*) AS leadCount
    FROM crm_lead JOIN res_partner ON ...
    WHERE ...  -- 权限 slice 已在内部注入
    GROUP BY partner_id
)
SELECT o.partner_name, o.totalSales, l.leadCount
FROM cte_orders o
LEFT JOIN cte_leads l ON o.partner_id = l.partner_id;

-- 不支持 CTE 的数据库（MySQL 5.7）：回退为子查询
SELECT o.partner_name, o.totalSales, l.leadCount
FROM (SELECT ...) AS o
LEFT JOIN (SELECT ...) AS l ON o.partner_id = l.partner_id;
```

各 QM 的权限 slice 在各自 CTE/子查询 内独立注入，不交叉。单条 SQL，数据库优化器统一处理。

#### 模式 3：内存合并（保留设计，暂不实现）

两个 QM 各自执行 SQL，Java 侧对结果集做 hash join。仅在跨数据源场景有意义——**因当前约束为同数据源，CTE 模式完全覆盖此场景且性能更优**。如未来支持跨数据源再启用。

### 权限隔离

组合模式下权限 slice 都在**各自 QM 内部独立注入**，不交叉：
- 模式 1：各自执行时各自注入
- 模式 2：各自 CTE/子查询内各自注入

这是 QM 层做组合的核心优势——TM 层自动 JOIN 无法保证这一点。每个 QM 独立走完整 pipeline（beforeQuery → 权限注入 → SQL 生成），组合层仅拼接输出，**零侵入**。

### fsscript DataSet API 设计

```javascript
// 创建查询（延迟执行）
const ds = dsl({ model, columns, slice, orderBy, limit, ... });

// 执行与取值
ds.execute()              // → ResultSet（触发 SQL 执行）
ds.column('field')        // → 单列值数组（触发执行，用于注入下一个查询的 slice）

// SQL 层组合（CTE/子查询 JOIN）
ds1.withJoin({ model, on, columns, type })  // → 返回新的 ComposedDataSet

// 二次计算（内存，在执行结果上操作）
ds.compute('avgDeal', 'totalSales / leadCount')
ds.filter('totalSales > 10000')
ds.sort('-totalSales')
```

### 实现方案

#### toSql 模式（截取 SQL 不执行）

在 `SemanticQueryServiceV3` 中新增 `mode = "sql"`：复用现有 pipeline（beforeQuery → 权限注入 → SQL 生成），在 `queryJdbc()` 执行前截停，返回 SQL 文本 + 参数列表。

```java
// SemanticQueryServiceV3Impl
if ("sql".equals(mode)) {
    // 走完 beforeQuery pipeline（权限 slice 注入、AutoGroupBy 等）
    // 调用 JdbcModelQueryEngine.analysisQueryRequest() 生成 SQL
    // 截取 engine.getSql() + engine.getValues()
    // 不调用 queryJdbc()
}
```

基础设施已就绪：`JdbcModelQueryEngine` 的 `innerSql`/`values` 字段在 `analysisQueryRequest()` 后即可读取。

#### CteComposer（薄拼接层）

纯字符串拼接，不侵入查询引擎：

```java
public class CteComposer {
    // 输入：多个 {alias, sql, params} + JoinSpec
    // 输出：WITH cte_a AS (sql₁), cte_b AS (sql₂) SELECT ... FROM cte_a JOIN cte_b ON ...
    // MySQL 5.7 回退：FROM (sql₁) AS t1 LEFT JOIN (sql₂) AS t2 ON ...
    // 参数：按 CTE 顺序合并 params₁ + params₂
}
```

#### AI 沙箱执行

AI 生成的 fsscript 在受限沙箱中执行：
- 禁用 `@bean` 导入（阻断 Spring Bean 访问）
- 禁用 `java:` 导入（阻断任意 Java 类访问）
- 白名单注入：仅 `dsl()` 函数 + `JSON` + `console.log`
- FSScript 本身无文件 I/O、无网络访问

### 模块归属（不新建模块）

| 组件 | 归属模块 | 包路径 | 说明 |
|---|---|---|---|
| `toSql` 模式 | `foggy-dataset-model` | `semantic/service/impl/` | 扩展 `SemanticQueryServiceV3Impl`，新增截停逻辑 |
| `CteComposer` | `foggy-dataset-model` | `engine/compose/` | 新包，纯 SQL 拼接，依赖 `FDialect` 判断 CTE 支持 |
| `DataSetResult` | `foggy-dataset-model` | `engine/compose/` | 轻量结果包装，提供 `column()`/`withJoin()`/`compute()` |
| `DslQueryFunction` | `foggy-dataset-model` | `proxy/` | fsscript `dsl()` 函数，类似 `LoadTableModelFunction` |
| `ComposeQueryTool` | `foggy-dataset-mcp` | `tools/` | MCP 工具入口，接收 fsscript 脚本 → 沙箱执行 → 返回结果 |
| AI 沙箱配置 | `foggy-dataset-mcp` | `service/` | 创建受限 `ScriptEngine`，注入白名单函数 |

**不新建模块的理由**：
- 总新增代码 ~6 个类，不足以独立成模块
- `toSql` 和 `CteComposer` 与查询引擎紧耦合，放在 `foggy-dataset-model` 天然合理
- MCP 工具和沙箱属于对外暴露层，放在 `foggy-dataset-mcp` 符合职责划分
- 依赖方向不变：`mcp → model → dataset → fsscript`，无新依赖引入

### 分阶段实施

```
Phase 1 — MVP（~1 周）
├── dsl() 桥接函数（DslQueryFunction → SemanticQueryServiceV3）
├── DataSetResult 基础包装（execute, column, toList）
├── 模式 1（ID 下推）自动可用
├── ComposeQueryTool MCP 工具 + AI 沙箱
└── AI Prompt 模板（教 AI 写 fsscript 编排）

Phase 2 — CTE 组合（~1 周）
├── toSql 模式（SemanticQueryServiceV3Impl 截停）
├── CteComposer（CTE 拼接 + MySQL 5.7 子查询回退）
├── DataSetResult.withJoin() 延迟执行 API
├── FDialect.supportsCte() 方言检测
└── 模式 2（CTE/子查询 JOIN）可用

Phase 3 — 二次计算（~0.5 周）
├── DataSetResult.compute() / filter() / sort()
└── 内存结果集操作
```

### 与业界方案对比

| 方案 | 跨模型实现 | 权限模型 |
|------|-----------|---------|
| **Cube.js** | cube schema `joins` 声明，查询时自动 JOIN | 按 cube 粒度，无行级权限 |
| **Looker (LookML)** | `explore` + `join` 声明，支持多种关系类型 | 按 explore/model 粒度 |
| **dbt Semantic Layer** | MetricFlow entities + relationships，编译时 JOIN | 无运行时权限 |
| **Foggy (planned)** | fsscript 编程式，QM 即视图 CTE 合并 | QM 层行级权限独立注入 |

Foggy 的区分点：**编程式组合 + QM 即视图 + QM 级行级权限隔离**。声明式方案（Cube.js/Looker）在简单场景更便捷，但难以处理"同一事实表、不同业务场景、不同权限要求"的情况。

## 日期维度表

独立日历参考表，不修改业务表结构。

```sql
CREATE TABLE dim_date (
    date_id      INTEGER PRIMARY KEY,  -- YYYYMMDD
    full_date    DATE NOT NULL,
    year         SMALLINT,
    quarter      SMALLINT,
    month        SMALLINT,
    week_of_year SMALLINT,
    day_of_week  SMALLINT,
    is_weekend   BOOLEAN,
    month_name   VARCHAR(20),
    quarter_name VARCHAR(10)
);
-- 一次性灌 10 年数据（~3650 行），永不变
```

TM 中日期维度指向 dim_date，引擎自动 JOIN。支持"Q1 销售额"、"周末订单占比"、"去年同期对比"等自然语言时间表达。

## QM 预定义计算指标

在 QM calculatedFields 中预定义高频业务指标，LLM 直接引用：
- 客单价 = amountTotal / orderCount
- 赢单率 = won_count / total_count
- 毛利率 = (revenue - cost) / revenue
- 平均回款天数 = avg(payment_date - invoice_date)
