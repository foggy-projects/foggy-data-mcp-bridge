# Pivot TopN SQL Pushdown Refactor Plan

## 文档作用

- doc_type: implementation-plan
- intended_for: root-controller, reviewer, future-implementation-agent
- purpose: 评估并规划 Pivot 轴级 `limit/orderBy` 从内存截断升级为基于 queryModel 受管 SQL relation 的完整下放方案。

## 1. 背景

9.0.0.beta 已完成 Pivot Java Core 主链路，当前执行模型是：

1. Phase 1 通过 `queryModel` 生成普通 `GROUP BY` 叶子聚合结果。
2. Phase 2 在 Java 内存中执行 `having`、`AxisTopNTruncator`、`CrossJoin`、`Subtotal`。
3. Phase 3 在 Java 内存中整形为 `tree/grid/flat`。

其中 `AxisField.limit` 当前属于内存截断。该实现能快速覆盖低基数、单轴、单层 TopN 场景，但在正式生产数据量和多轴 Pivot 下存在语义与性能风险：

- 带 columns 轴时，当前实现容易截断物理叶子格子，而不是轴成员集合。
- Phase 1 先拉回最多 `DEFAULT_ROW_LIMIT * DEFAULT_COL_LIMIT` 的叶子结果，再在 JVM 中排序截断，存在候选集不完整和内存压力。
- 无 `orderBy` 时结果依赖数据库返回顺序，不稳定。
- subtotal/grandTotal 依赖 `having/TopN` 后 surviving domain；如果 TopN 截断语义错误，小计也会错误。

本方案讨论最高成本、最完整的改造路线：将 TopN 成员域选择下放到 SQL，同时必须保留 `queryModel` 的权限、systemSlice、预聚合、物理列权限、缓存、日志、错误脱敏和格式化能力。

## 2. 与版本目标的关系

本文最初作为 post-9.0.0 / 9.1.0 候选能力评估编写；后续 Stage 1-4 已在 9.0.0.beta 文档线内完成实现、测试覆盖审计和正式签收。

当前签收状态：

- Feature acceptance: `docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-acceptance.md`
- Coverage audit: `docs/9.0.0.beta/test_coverage/pivot-sql-topn-pushdown-coverage-audit.md`
- Version signoff: `docs/9.0.0.beta/acceptance/version-signoff.md`
- Decision: `accepted-with-risks`

保留边界不变：`级联 Generate` 仍为 `deferred / known-limitation`，tree / parentShare / baselineRatio 等非第一版场景仍不进入 SQL TopN pushdown 范围。

本方案支撑的后续目标是：

- 修正 Pivot `limit/orderBy` 在多轴场景下的成员域语义。
- 降低大基数 Pivot 查询对 JVM 内存和网络传输的压力。
- 为后续级联 Generate、SQL Planner、预聚合友好的 Pivot 执行奠定基础。

## 3. 核心目标

1. `limit/orderBy` 对轴成员域生效，而不是对 Phase 1 物理结果行生效。
2. SQL 下放不能绕过 queryModel 生命周期；如果需要外层 SQL 包装，也必须基于 queryModel 产出的受管 SQL relation。
3. 权限能力必须保留：
   - `fieldAccess`
   - `visibleColumns`
   - `systemSlice`
   - `deniedColumns`
   - 表达式依赖 fail-closed
4. 性能能力必须保留：
   - 预聚合匹配与 SQL rewrite
   - L2 cache 或 Pivot 专属 cache key 策略
   - SQL logging
   - QueryErrorSanitizer
5. TopN 下放结果必须和人工 SQL oracle 在 SQLite、MySQL8、PostgreSQL 上一致。

## 4. 非目标

第一阶段不建议直接覆盖以下能力：

- MySQL 5.7 SQL window pushdown。该方言应 fail-closed 或回退内存 guarded path。
- 任意 MDX Generate 集合拼接。
- `hierarchyMode=tree` 下递归树 TopN SQL 化。
- `parentShare` / `baselineRatio` 参与 `having/orderBy/limit`。
- 绕过 queryModel 直接拼物理表 SQL。
- 将 Pivot 外层包装 SQL 纳入公开 DSL。
- 第一版接入 outer Pivot L2 cache。第一版只保留 preAgg rewrite，outer cache 作为后续优化。

## 5. 关键架构判断

### 5.1 不能只使用现有 generateSql

`SemanticQueryServiceV3Impl.generateSql(...)` 和 `QueryFacade.buildSqlOnly(...)` 能执行 beforeQuery pipeline，并生成基础 SQL。但当前 `JdbcQueryModelImpl.generateSql(...)` 不执行 query execution steps。

这意味着它能保留：

- fieldAccess / visibleColumns / AutoGroupBy / InlineExpression 等 beforeQuery 能力。
- queryModel 字段解析和 SQL 生成。

但它不能完整保留：

- `PreAggRewriteStep`，因为预聚合在 `QueryExecutionStep.beforeExecute(...)` 阶段改写 SQL。
- `PhysicalColumnPermissionStep`，因为 deniedColumns 的物理列校验也在 beforeExecute 阶段。
- L2 cache 执行期行为。

因此，最高成本方案必须新增一个“受管 SQL relation prepare”能力，而不是直接用 `generateSql()` 包窗口函数。

### 5.2 推荐新增受管 relation 出口

新增内部能力，语义类似：

```java
ManagedSqlRelation prepareManagedRelation(ModelResultContext context, ManagedRelationOptions options);
ManagedQueryResult executeManagedRelation(ManagedSqlRelation relation, ManagedRelationExecutionOptions options);
```

命名不强制，后续实现 agent 可按项目风格确定。能力边界必须满足：

- 完整执行 `beforeQuery`。
- 完整执行 `JdbcModelQueryEngine.analysisQueryRequest(...)`。
- 可选择执行 query execution beforeExecute steps 中的 SQL rewrite / permission check 类步骤。
- 不直接执行 inner query。
- 返回可被外层 SQL 安全包装的 SQL、params、dialect、queryEngine metadata 和 extData。
- 返回显式 capability 标记，供外层 planner 防御性断言。

建议 relation metadata 至少包含：

```java
public interface ManagedSqlRelation {
    boolean isWrappable();
    boolean isPermissionValidated();
    boolean isPreAggApplied();
    DialectCapabilities dialectCapabilities();
    List<ManagedMetricMetadata> metrics();
}

public enum AdditiveKind {
    ADDITIVE,
    NON_ADDITIVE,
    UNKNOWN
}
```

`PivotTopNSqlPlanner` 收到 relation 后必须先校验 `isWrappable()`、`isPermissionValidated()`、必要方言能力和 metric metadata。任何关键 capability 不满足时必须 fail-closed，不能默默生成外层 SQL。

### 5.3 QueryExecutionStep 需要分类

当前 beforeExecute step 混合了多种语义：

- SQL rewrite：如预聚合。
- 权限校验：如物理列权限。
- 缓存检查：如 L2 cache 命中可跳过执行。

在 relation prepare 阶段，不能简单执行所有 step 并接受 skipExecution，因为外层 Pivot SQL 尚未执行。建议新增 step capability 或 execution mode：

| step 类型 | prepare 阶段策略 |
| --- | --- |
| SQL rewrite | 必须执行 |
| permission validation | 必须执行 |
| SQL metadata/debug 写入 | 可执行 |
| L2 cache read causing skipExecution | 禁止直接短路，或只作为未来 outer-cache 输入 |
| L2 cache write | 不执行 |

如果不做分类，容易出现两类错误：

- 为了拿预聚合而错误复用 inner query cache，导致外层 TopN 未执行。
- 为了避免 cache 影响而跳过 beforeExecute，导致预聚合和物理列权限丢失。

### 5.4 内部管道必须固定，不允许外层注入管道对象

受管 relation 的内部管道应由 queryModel 自己拥有和编排。PivotPipeline 不能把自定义的 query execution pipeline、step 列表或 step 实例塞进 context 传入 queryModel。外层只能通过受控的 `ManagedRelationOptions` / phase 表达意图，例如：

```java
ManagedRelationOptions options = ManagedRelationOptions.builder()
    .purpose(PIVOT_TOPN_SQL_PUSH_DOWN)
    .wrappableRequired(true)
    .disableInnerCacheShortCircuit(true)
    .requireStableAliases(true)
    .requireDialectCapabilities(WINDOW_FUNCTION, CTE)
    .build();
```

推荐内部 phase 设计：

```text
NORMAL_QUERY
PREPARE_MANAGED_RELATION
EXECUTE_MANAGED_RELATION
```

每个 `QueryExecutionStep` 自己声明支持哪些 phase：

```java
interface QueryExecutionStep {
    boolean supports(QueryExecutionPhase phase, QueryExecutionContext ctx);
    void execute(QueryExecutionContext ctx);
}
```

阶段策略示例：

| step | NORMAL_QUERY | PREPARE_MANAGED_RELATION | EXECUTE_MANAGED_RELATION |
| --- | --- | --- | --- |
| PreAggRewriteStep | run | run | no |
| PhysicalColumnPermissionStep | run | run | optional no-op / validate marker |
| L2CacheReadStep | run | no short-circuit | optional outer-cache only |
| L2CacheWriteStep | run | no | optional pivot-result cache |
| SQL logging / debug marker | run | run | run |

该约束的目的：

- 避免 Pivot 外层绕开 queryModel 对权限、预聚合、缓存语义的统一治理。
- 避免不同调用方按需拼装内部 step，导致同一 queryModel 请求在不同入口表现不一致。
- 允许 queryModel 后续新增治理 step 时，只需要声明 phase 支持矩阵，而不需要追踪所有外层调用方是否手动接入。
- 让 PivotTopNSqlPlanner 只消费一个已经受管、已校验、已预聚合改写的 relation，不重新理解 TM/QM 安全规则。

因此，本方案不是“外层发现需要介入时，把内层管道对象放入 context”。正确边界是：外层请求一种受管执行模式，内层用固定管道按 phase/options 运行。

### 5.5 Stage 1 前置：QueryExecutionStep context 读写依赖图

Stage 1 的第一交付物不是代码，而是 `QueryExecutionStep` 的 context 读写依赖图。原因是当前 `queryJdbc` 调用链把 analysis、beforeExecute、execute、afterExecute 串在一起，step 之间可能存在隐式状态依赖；如果直接拆成 prepare/execute 两段，隐式依赖可能在运行时静默失效。

依赖图至少记录：

- 每个 step 读取哪些 `QueryExecutionContext` 字段、extData key、flag、SQL/params 状态。
- 每个 step 写入哪些字段、extData key、flag、SQL/params 状态。
- 写入内容是否被后续 step、afterExecute、cache、debug、preAgg、permission 校验读取。
- 当前 step 是否允许在 `PREPARE_MANAGED_RELATION` 阶段运行。
- 如果不运行，后续是否需要替代 marker 或显式 no-op 记录。

只有依赖图完成后，才能冻结 phase 矩阵。若 context 读写无法被可靠枚举，Stage 1 风险应视为不可控，不应进入 SQL TopN pushdown 实现。

## 6. 推荐目标架构

### 6.1 执行链路

```text
PivotRequest
  -> PivotPipeline validates DSL
  -> build Phase 1 SemanticQueryRequest
  -> QueryFacade.prepareManagedRelation(...)
       -> beforeQuery
       -> analysisQueryRequest
       -> selected beforeExecute steps
       -> return ManagedSqlRelation(baseSql, params, dialect, metadata)
  -> PivotTopNSqlPlanner plans outer SQL
       -> base CTE
       -> row_domain / col_domain CTE
       -> row_ranked / col_ranked CTE
       -> final filtered leaf result SQL
  -> QueryFacade.executeManagedRelation(...)
       -> logging
       -> sanitizer
       -> formatting
       -> outer cache handling
  -> existing CrossJoin / Subtotal / derived metrics / shaping
```

### 6.2 SQL 形态

示意 SQL：

```sql
WITH base AS (
  /* queryModel managed SQL, may already use pre-aggregation */
),
row_domain AS (
  SELECT
    row_parent_keys,
    row_target_key,
    SUM(order_metric) AS order_metric_for_rank,
    SUM(having_metric_1) AS having_metric_1_for_filter
  FROM base
  GROUP BY row_parent_keys, row_target_key
),
row_domain_filtered AS (
  SELECT *
  FROM row_domain
  WHERE having_metric_1_for_filter > ?
),
row_ranked AS (
  SELECT *,
         ROW_NUMBER() OVER (
           PARTITION BY row_parent_keys
           ORDER BY order_metric_for_rank DESC, row_target_key ASC
         ) AS rn
  FROM row_domain_filtered
),
filtered AS (
  SELECT base.*
  FROM base
  JOIN row_ranked
    ON base.row_parent_keys = row_ranked.row_parent_keys
   AND base.row_target_key = row_ranked.row_target_key
  WHERE row_ranked.rn <= ?
)
SELECT * FROM filtered
```

列轴存在 `limit` 时，同理生成 `col_domain` / `col_ranked`，最终同时 join row 和 column surviving domain。

`row_domain` / `col_domain` 不是简单只计算 order metric。Planner 必须收集当前轴 domain 上所有需要的聚合表达式：

- `orderBy` 引用的 metric。
- axis `having` 谓词引用的 metric。
- 稳定排序需要的 tie-breaker key。

如果 having metric 与 orderBy metric 不同，domain CTE 必须同时计算两者；having 条件必须在 ranked CTE 之前过滤，保证语义顺序是 `having -> TopN`。

### 6.3 轴成员 TopN 语义

SQL 下放必须采用成员域排名，而不是叶子格子排名：

- `rows=[product limit 10], columns=[month]`：排名粒度是 product，排序指标需要跨 month 聚合。
- `rows=[category, subCategory limit 3], columns=[month]`：排名粒度是 `(category, subCategory)`，partition 是 `category`，排序指标需要跨 columns 聚合。
- `columns=[year, month limit 6]`：排名粒度是 `(year, month)`，partition 是 `year`，排序指标需要跨 rows 聚合。

排序指标聚合策略第一版建议：

- 对 `ManagedMetricMetadata.additiveKind=ADDITIVE` 的 SUM/COUNT 类型 order metric 使用 SUM。
- 对 `ManagedMetricMetadata.additiveKind=ADDITIVE` 且语义是 MIN/MAX 的 order metric 使用对应 MIN/MAX。
- 对 `additiveKind=NON_ADDITIVE` 或 `UNKNOWN` 的 AVG/COUNT_DISTINCT/ratio calculatedFields 第一版 fail-closed，或要求显式走辅助 domain query。
- 对 `parentShare` / `baselineRatio` fail-closed，因为它们是后置派生输出，不参与 limit。

可加性判断必须由 queryModel prepare 阶段写入 `ManagedSqlRelation` metadata。Pivot domain planner 不应在外层重新推断 calculatedFields 是否可加，避免 planner 与 queryModel 对同一字段得出不同判断。

### 6.4 Having 与 TopN 的关系

目标顺序保持现有语义：

```text
Phase 1 managed base relation
-> axis having
-> axis TopN
-> surviving domain
-> CrossJoin
-> Subtotal / GrandTotal
-> Properties
-> derived metrics
-> shaping
```

完整 SQL 化时，having 也应尽量下放为 domain CTE 过滤，避免先拉回大量候选成员。若第一阶段只做 TopN pushdown，则至少必须保证：

- having 仍先于 TopN。
- 如果 having 仍在内存，则 TopN 不应先在 SQL 中执行，否则语义顺序改变。

因此建议最高成本方案把 axis having 和 TopN 作为同一个 `PivotAxisDomainSqlPlanner` 处理。

`PivotAxisDomainSqlPlanner` 必须显式维护 domain aggregate registry：以 `(axis, domainGrain, metricRef, aggregateStrategy)` 为 key 合并 having 和 orderBy 所需聚合，避免实现时只处理 orderBy 而遗漏 having 谓词字段。

## 7. 阶段拆解

### Stage 0: Guardrail and Contract Freeze

目的：在大改前先冻结语义，避免继续扩大错误结果面。

建议内容：

- `limit` 必须配置 `orderBy`，否则 fail-closed 或给出明确 warning 并禁止生产启用。
- 多个 axis field 同时设置 limit 的级联场景保持 `deferred / known-limitation`，除非进入 Stage 5。
- 文档明确当前内存路径只适合受限场景。

可独立推进：是。

### Stage 1: Managed SQL Relation Prepare

目的：新增不绕过 queryModel 的受管 SQL relation 出口。

必须完成：

- 盘点并文档化所有 `QueryExecutionStep` 的 context 读写依赖；该依赖图是代码拆分前置条件。
- 抽出 `JdbcQueryModelImpl` 中 `analysisQueryRequest + createExecutionContext + selected beforeExecute` 能力。
- 定义 prepare mode，明确哪些 `QueryExecutionStep` 可执行。
- 内部管道固定，由 queryModel 根据 `QueryExecutionPhase + ManagedRelationOptions` 选择 step 行为；禁止外层注入自定义 step 列表或管道对象。
- 确保预聚合 SQL rewrite 能在 prepare relation 中生效。
- 确保 `PhysicalColumnPermissionStep` 能在 prepare relation 中生效。
- 禁止 inner L2 cache 命中直接 short-circuit outer Pivot。
- 第一版明确禁用 outer Pivot L2 cache；`EXECUTE_MANAGED_RELATION` 中 cache read/write no-op 或仅记录 debug marker。
- 为 relation 输出 `isWrappable/isPermissionValidated/isPreAggApplied/additiveKind` 等 capability metadata。

完成门：

- 同一普通语义查询，通过 prepare relation 得到的 SQL 与真实 queryModel 执行路径的最终 SQL 等价。
- preAgg 命中时，relation SQL 已经是预聚合改写后的 SQL。
- deniedColumns 命中时，prepare 阶段 fail-closed。
- 若 context 读写依赖图无法完成或存在无法拆分的隐式状态依赖，Stage 1 结论必须是 blocked，不进入 Stage 2。

### Stage 2: Pivot Axis Domain SQL Planner

目的：实现轴成员域的 SQL having / TopN。

必须完成：

- 从 Pivot rows/columns 构造 domain grain。
- 为每个 `limit` field 推导 partition keys、target keys、order specs。
- 生成 row/column domain CTE。
- 对 order metric 和 axis having metric 做合并聚合规划。
- 基于 queryModel metadata 的 `additiveKind` 做聚合策略判断。
- 对 unsupported order metric fail-closed。
- 为 null key、字符串排序、数值排序定义跨库一致性策略。

完成门：

- 单轴 TopN 与人工 SQL oracle 一致。
- 带 columns 轴时，row limit 仍按 row member 排名，而非 leaf cell。
- row limit 与 column limit 同时存在时 surviving domain 交集正确。

### Stage 3: Pivot Pipeline Integration

目的：在 PivotPipeline 中引入 SQL pushdown 分支，并保留现有内存分支作为 fallback 或 guarded path。

建议策略：

- 新增 planner capability detection。
- 命中能力矩阵：使用 SQL pushdown 替代 `AxisHavingFilter + AxisTopNTruncator`。
- 不命中能力矩阵：
  - 默认 fail-closed；或
  - 仅在显式 hint 下 fallback 到内存路径。

能力矩阵建议：

| 场景 | 第一版 SQL pushdown |
| --- | --- |
| MySQL8/PostgreSQL/SQLite | 支持 |
| MySQL5.7 | 不支持 |
| 单层 row limit | 支持 |
| 单层 col limit | 支持 |
| row + col 同时 limit | 支持 |
| 多层级联 limit | Stage 5 |
| hierarchyMode=tree | 不支持 |
| parentShare/baselineRatio orderBy | 不支持 |
| calculatedFields orderBy | 仅支持已在 base relation 输出且可聚合的字段 |

### Stage 4: Subtotal and Non-Additive Rollup Alignment

目的：确保 subtotal/grandTotal 继续基于 surviving domain。

必须完成：

- SQL pushdown 后返回的 resultSet 已经只包含 surviving members。
- `CardinalityBreaker.extractRowDomain/extractColumnDomain` 使用 SQL 后结果继续正确。
- `NonAdditiveRollupExecutor` 的辅助查询继续接收 surviving row/column domain。
- 辅助查询不能把已被 SQL TopN/Having 过滤掉的成员重新算回小计。

**[✅ 已完成（语义修正版）- 2026-05-02]**

核心语义约束（经两轮复核确认）：

> **`grainFields` 只决定辅助查询的 GROUP BY 粒度，不决定 WHERE 约束字段范围。**
> WHERE 约束始终基于完整 `axisFields` tuple（从 surviving domain 提取），
> 即使 subtotal grain 只有 `[category]`，辅助查询的 WHERE 也必须约束完整的 `(category, product)` tuple，
> 否则 AVG/COUNT_DISTINCT subtotal 会把被 TopN 过滤掉的 product 重新算回来。

实现要点：

1. **移除 `grainFields` from WHERE 约束逻辑**：`addAxisDomainSlice` 不再接受 `grainFields` 参数。
   修正前的错误：`axisFields.filter(grainFields::contains)` → grain=[category] 时只约束 category，丢失 product
   修正后：始终使用完整 `axisFields` 生成 tuple constraint

2. **grandTotal 空 grain 不再 early return**：
   修正前：`grainAxisFields.isEmpty() → return`，grandTotal 辅助查询完全不带 surviving domain 过滤
   修正后：`addAxisDomainSlice(rowFields, survivingRowDomain)` 始终执行，列轴约束同理

3. **多字段轴 OR-of-AND tuple constraint（精确）**：
   `OR(AND(category='A', product='p1'), AND(category='B', product='p2'))` —— 不包含 cross-product

4. **null 值支持**：
   - 单字段轴含 null：`OR(IN(non-null values), IS NULL)`；全 null：`IS NULL`
   - 多字段轴含 null 字段：AND 子组中用 `{op: "is null"}`，不再静默跳过该 tuple

5. **MAX_IN_LIST_SIZE 超限 fail-closed**：
   抛出 `NonAdditiveRollupDomainTooLargeException`，`PivotPipeline` 转换为明确错误提示

6. **新增类**：
   - `NonAdditiveRollupDomainTooLargeException`

7. **测试**（`NonAdditiveRollupExecutorDomainSliceTest`，9 个用例，2392 全量 pass）：
   - 单字段轴 IN（无 null）
   - 多字段轴 OR-of-AND，不含 cross-tuple ← **核心语义验证**
   - subtotal grain 粒度粗时 WHERE 仍约束完整 tuple（product 参与过滤）← **语义修正后的正确断言**
   - grandTotal 列轴 surviving domain 约束辅助查询
   - 空 axisFields 不生成过滤
   - 单字段轴全 null / 混合 null
   - 多字段轴含 null field
   - 超限 fail-closed（单/多字段）

完成门：

- `rowSubtotals + TopN` 与人工 SQL oracle 一致。✅
- `COUNT_DISTINCT/AVG + TopN + subtotal` 辅助查询只覆盖 surviving domain tuple。✅
- grandTotal 辅助查询不包含被 TopN 过滤的成员。✅
- null 维度值不被静默丢弃。✅
- 全量 2392 tests pass。✅

### Stage 5: Follow-Up Tracks

Stage 5 后续应拆成两个独立轨道，不能混在同一批实现中：

1. **Stage 5A: Large-domain transport spike**  
   目的：评估是否用 `VALUES CTE` / `UNION ALL CTE` / temp table 等内部 domain relation 替代 oversized `IN` / `OR-of-AND` 谓词，解决 non-additive subtotal/grandTotal 的 `domain > 500` fail-closed 边界。该轨道不改变外部 Pivot DSL，不改变 JSON Schema，不默认启用生产路径。详见 `docs/9.0.0.beta/detailed_design/09_pivot_stage5_domain_transport_spike_plan.md`。

2. **Stage 5B: Cascade Generate and advanced cases**  
   目的：覆盖多层 limit、级联 Generate、未来 tree TopN。该轨道涉及新的语义决策，应作为独立版本规划，不应借 Stage 5A spike 顺手实现。

建议作为单独立项，不建议和 Stage 1-4 同批完成。Stage 5A spike 已给出 9.0.0.beta No-Go 结论，后续生产化与 Stage 5B 语义设计统一迁移到 `docs/9.1.0/`。

需要重新评估：

- 多层 limit 的中间 domain 是否需要逐层 CTE。
- 中间层排序指标应基于子树聚合还是当前层聚合。
- tree hierarchy 是否能转换为 closure table domain ranking。
- expandDepth 是展示语义还是过滤语义，不能混入 SQL TopN。
- large-domain transport 是否需要 `DomainTransportPlan` / dialect capability matrix，以及 Python mirror 是否只需保留 fail-closed 契约。

## 8. Module Responsibility

### Root / workspace

- 维护本规划文档。
- 记录 Stage 1-4 已完成并纳入 9.0.0.beta 补充签收；后续 Stage 5A large-domain transport、Stage 5B cascade/tree advanced semantics、outer cache 已迁移到 9.1.0 路线图。
- 维护验收标准和版本进度记录。

### foggy-dataset-model

主要 owner。

负责：

- queryModel 受管 relation prepare 能力。
- Pivot SQL planner。
- PivotPipeline 集成。
- 三库 parity 测试。
- 权限、预聚合、缓存、错误脱敏的集成测试。

### addons/foggy-dataset-model-cache

配套 owner。

负责：

- 如果现有 L2 cache 参与 outer Pivot SQL，需要定义 cache key。
- 如果第一版不接 outer cache，需明确禁用策略和 debug 标记。

### addons/foggy-dataset-model-preagg

配套 owner。

负责：

- 验证 preAgg rewrite 后 SQL 可作为 base relation 被外层 CTE/window 包装。
- 验证 hybrid preAgg SQL 在外层 CTE 包装下参数顺序和方言兼容。

### foggy-dataset-mcp

消费方。

第一阶段不需要改 Schema；如果新增 fail-closed 错误码或 hint，需要更新 MCP 错误说明和 JSON-RPC 行为。

## 9. Code Inventory

```yaml
code_inventory:
  - repo: foggy-data-mcp-bridge-wt-dev-compose
    path: foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/service
    role: query facade lifecycle boundary
    expected_change: update
    notes: add managed relation prepare/execute capability without bypassing beforeQuery and selected beforeExecute steps

  - repo: foggy-data-mcp-bridge-wt-dev-compose
    path: foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/JdbcQueryModelImpl.java
    role: JDBC query lifecycle and SQL execution
    expected_change: update
    notes: extract analysis + execution context preparation for reusable managed relation flow

  - repo: foggy-data-mcp-bridge-wt-dev-compose
    path: foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/query_execution
    role: query execution steps
    expected_change: update
    notes: classify steps for prepare mode; PreAggRewrite and PhysicalColumnPermission must run; cache short-circuit must not bypass outer Pivot SQL

  - repo: foggy-data-mcp-bridge-wt-dev-compose
    path: foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot
    role: Pivot pipeline orchestration
    expected_change: update
    notes: add SQL pushdown branch and fallback/fail-closed decision

  - repo: foggy-data-mcp-bridge-wt-dev-compose
    path: foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/sql
    role: Pivot SQL planner package
    expected_change: create
    notes: candidate package for axis domain CTE/window planner; exact class names to be decided during implementation

  - repo: foggy-data-mcp-bridge-wt-dev-compose
    path: foggy-dataset-model/src/test
    role: unit and integration tests
    expected_change: update
    notes: add SQL planner unit tests and SQLite/MySQL8/PostgreSQL parity tests

  - repo: foggy-data-mcp-bridge-wt-dev-compose
    path: addons/foggy-dataset-model-cache
    role: optional cache provider
    expected_change: read-only-analysis
    notes: inspect before deciding whether outer Pivot SQL uses cache in first implementation

  - repo: foggy-data-mcp-bridge-wt-dev-compose
    path: addons/foggy-dataset-model-preagg
    role: optional pre-aggregation extension
    expected_change: read-only-analysis
    notes: verify extension wiring; core preAgg rewrite classes currently live under foggy-dataset-model

  - repo: foggy-data-mcp-bridge-wt-dev-compose
    path: foggy-dataset-mcp
    role: MCP consumer
    expected_change: read-only-analysis
    notes: update only if new errors/hints become public API
```

## 10. 约束与安全边界

必须继承根目录 `CLAUDE.md` 中的安全约束：

- 开源项目，不得上传私有 key、账号密码、token。
- fieldAccess 在 beforeQuery 阶段 fail-closed。
- deniedColumns 在 beforeExecute 阶段基于物理列 fail-closed。
- Namespace 必须通过 `SemanticRequestContext` / `ModelResultContext` 传递。

额外约束：

- 外层 Pivot SQL 只能引用 queryModel relation 输出列别名，不能引用物理表列。
- 任何 SQL 包装都必须保留原 params 顺序，新增参数只能按最终 SQL 渲染顺序追加。
- outer SQL 错误必须继续经过脱敏处理，不泄露物理表别名或列名。
- debug 信息可以暴露 `preAggUsed/preAggMode/pivotPushdownMode`，但不能暴露敏感 SQL 参数值。

## 11. 验收标准

### 功能验收

- `rows=[product limit 10], columns=[month]` 返回 Top 10 product，而不是 Top 10 product-month leaf cells。
- `rows=[category, subCategory limit 3], columns=[month]` 返回每个 category 下 Top 3 subCategory。
- `columns=[year, month limit 6]` 返回每个 year 下 Top 6 month。
- row limit 和 column limit 同时存在时，最终 leaf result 是两个 surviving domain 的交集。
- having 先于 TopN 生效。
- subtotal/grandTotal 只统计 surviving domain。

### 权限和治理验收

- `fieldAccess` 禁止的字段出现在 rows/columns/metrics/orderBy/having 时 fail-closed。
- `systemSlice` 必须出现在 base relation 中，外层 SQL 不得绕过。
- `deniedColumns` 命中物理列时在 prepare 或 execute 前 fail-closed。
- preAgg 命中时 outer SQL 包装的是预聚合改写后的 SQL。
- preAgg 未命中时结果与原始表查询一致。

### 方言验收

- SQLite parity pass。
- MySQL8 parity pass。
- PostgreSQL parity pass。
- MySQL5.7 明确 fail-closed 或 guarded fallback，不进入 window pushdown。

### 性能验收

- 大基数 rows/columns 场景下，SQL pushdown 返回行数明显小于内存路径 Phase 1 leaf rows。
- JVM 中 `AxisTopNTruncator` 不再处理 SQL pushdown 已覆盖的场景。
- CardinalityBreaker 仍在 CrossJoin/Subtotal 前生效。

## 12. 测试计划

### Unit Tests

- Axis domain grain 推导。
- partition keys 推导。
- order metric 聚合策略。
- metric `additiveKind` metadata 消费策略。
- having/orderBy domain aggregate registry 合并逻辑。
- unsupported order metric fail-closed。
- SQL 参数顺序。
- null key join 条件。
- capability matrix。

### Integration Tests

建议在 `foggy-dataset-model` 中新增或扩展 Pivot parity 测试：

- SQLite: basic row TopN with columns axis。
- SQLite: row + column simultaneous TopN。
- SQLite: having before TopN。
- SQLite: subtotal after TopN。
- MySQL8: same matrix。
- PostgreSQL: same matrix。
- deniedColumns fail-closed。
- fieldAccess fail-closed。
- systemSlice parity。
- preAgg hit parity。
- preAgg hybrid parity if environment supports it。
- preAgg hit + `limit` + `systemSlice` 同时存在时，最终 SQL 参数绑定顺序正确。

### Regression Tests

- 原有 Pivot flat/tree/grid 输出不因无 limit 请求变化。
- parentShare/baselineRatio 输出不变化。
- non-additive subtotal 不变化。
- properties post-join 不变化。

## 13. 推进建议

建议不要一次性承诺 Stage 1-5 全量交付。推荐决策路径：

1. 先执行 Stage 0，降低当前内存 limit 的错误结果风险。
2. 在 Stage 1 写代码前，先完成 `QueryExecutionStep` context 读写依赖图。
3. 单独做 Stage 1 技术 Spike，证明 managed relation prepare 可以保留 preAgg 和 deniedColumns。
4. Spike 通过后，再进入 Stage 2-4，交付非 tree、非级联的完整 SQL TopN pushdown。（已完成）
5. Stage 5 作为独立版本规划，不与 Stage 2-4 混排。

## 14. 预估成本

| 阶段 | 预估 | 风险 |
| --- | --- | --- |
| Stage 0 | 1-2 天 | 低 |
| Stage 1 | 1.5-2 周 | 极高 |
| Stage 2 | 1 周 | 中高 |
| Stage 3 | 3-5 天 | 中 |
| Stage 4 | 1 周 | 高 |
| Stage 5 | 2-4 周 | 很高 |

如果只做 Stage 1-4，整体约 4.5-7 周。  
如果连 Stage 5 一起做，整体约 6.5-11 周，并且需要更严格的版本管理和验收拆分。

## 15. 待确认问题

### Stage 0 前必须确认

1. MySQL5.7 是否仍属于必须支持的 Pivot TopN 方言？如果必须支持，`fail-closed` 不够，必须设计 guarded fallback。

### Stage 1 前必须确认

2. 第一版是否允许内存 fallback，还是不命中 SQL pushdown 就 fail-closed？该决策影响 planner 接口形态和错误模型。
3. outer Pivot SQL 第一版明确禁用 L2 cache，只保留 preAgg rewrite。若评审不接受禁用，需要重新设计 `EXECUTE_MANAGED_RELATION` 的 cache phase。

### Stage 2 前确认

4. `calculatedFields` 作为 orderBy 时，是否只允许 `additiveKind=ADDITIVE` 的可聚合数值字段？
5. 多个 axis field 同时配置 limit 是否继续保持 known-limitation？
6. 是否需要新增 public debug 字段暴露 `pivotPushdownMode`、`preAggUsed`、`managedRelationCapabilities`？

## 16. 方案评估

### 16.1 风险评估

| 风险 | 等级 | 说明 | 建议控制 |
| --- | --- | --- | --- |
| queryModel 生命周期拆分不干净 | 极高 | `queryJdbc` 当前把分析、beforeExecute、缓存、执行、afterExecute 串在一起；step 之间可能存在 context flag、extData、SQL/params 的隐式读写依赖。拆 relation prepare 时容易静默遗漏依赖，而不是编译期失败。 | Stage 1 spike 第一交付物必须是所有 `QueryExecutionStep` 的 context 读写依赖图；依赖图完成后才能冻结 phase 矩阵和写代码。 |
| 预聚合 SQL 被外层 CTE 包装后兼容性不足 | 高 | preAgg rewrite 可能产生方言相关 SQL、参数顺序或 alias 约束。 | 以 preAgg hit / miss / hybrid 三类集成测试作为 Stage 1 完成门。 |
| 物理列权限校验位置变化 | 高 | 如果 prepare 阶段没有执行 `PhysicalColumnPermissionStep`，外层 Pivot SQL 会拿到未校验 relation。 | prepare 阶段必须 fail-closed；relation metadata 标记 permissionValidated。 |
| metric 可加性判断来源不一致 | 高 | 如果 Pivot planner 自己推断 calculatedFields 可加性，可能和 queryModel 对指标语义的判断不一致。 | 由 prepare 阶段输出 `ManagedMetricMetadata.additiveKind`，planner 只消费 metadata。 |
| having/orderBy domain 聚合遗漏 | 中高 | having metric 与 orderBy metric 可能不同，domain CTE 如果只计算排序指标会改变语义。 | `PivotAxisDomainSqlPlanner` 必须维护 domain aggregate registry，合并 having 和 orderBy 所需聚合。 |
| TopN 语义与现有内存路径不一致 | 中高 | 现有内存路径本身存在 leaf cell 截断问题；SQL pushdown 应以目标语义为准，而不是完全复制旧行为。 | 用人工 SQL oracle 定义新语义；旧内存路径只作为兼容 fallback。 |
| 方言能力差异 | 中 | CTE/window/null ordering 在 SQLite、MySQL8、PostgreSQL 上细节不同，MySQL5.7 不适合下放。 | capability matrix + dialect renderer；不支持时 fail-closed 或显式 fallback。 |
| 参数顺序错误 | 中 | preAgg rewrite 可能重排或替换 base SQL 参数，外层 TopN/having 再追加参数后，类型可能匹配但值绑定错误。 | 增加 `preAgg hit + limit + systemSlice` 参数顺序集成测试。 |
| 代码复杂度上升 | 中高 | 会新增 relation prepare、phase/options、planner、测试矩阵。 | 严格限制第一版能力矩阵；Stage 5 级联/tree 不并入主交付。 |
| 后续维护理解成本 | 中 | 新增执行模式后，后续新增 query execution step 必须考虑 phase 支持。 | 在 step 接口或注册元数据中强制声明 phase；缺省策略 fail-closed。 |

### 16.2 复杂度是否无法避免

如果目标只是降低 JVM 内存压力，可以做较低成本的 guardrail，例如强制 `limit` 必须有 `orderBy`、缩小 Phase 1 默认上限、对多轴 limit fail-closed。但这不能解决核心语义问题：TopN 应该对轴成员域生效，而不是对叶子结果行生效。

如果目标是“SQL 下放且不绕过 queryModel 的权限、预聚合和物理列权限”，则以下复杂度基本无法避免：

- 需要一个 relation prepare 出口，因为现有 `generateSql()` 不执行 beforeExecute 阶段能力。
- 需要区分 prepare 与 execute phase，因为 cache read/write 和 SQL rewrite 不能用同一策略处理。
- 需要先枚举 `QueryExecutionStep` 的 context 读写依赖，因为当前生命周期拆分风险主要来自隐式状态依赖。
- 需要 Pivot 专用 domain planner，因为普通 queryModel SQL 生成器不知道 Pivot rows/columns 的成员域排名语义。
- 需要由 queryModel 输出 metric 可加性 metadata，因为外层 planner 不能重新解释 calculatedFields 语义。
- 需要跨库 parity 测试，因为窗口函数、CTE、null 排序和参数顺序都是结果正确性的组成部分。

可以避免的复杂度：

- 第一版不做 tree / cascade generate / recursive hierarchy SQL 化。
- 第一版不让 `parentShare`、`baselineRatio`、复杂 ratio calculatedFields 参与 limit。
- 第一版不接 outer Pivot L2 cache，只保留 preAgg rewrite；缓存作为后续优化。
- 第一版不开放 public DSL 让用户直接控制外层 CTE。

### 16.3 替代方案对比

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| A. 继续内存截断 + guardrail | 成本最低，1-2 天可落地。 | 不能修复多轴成员域 TopN 语义，只能减少误用面。 | 适合作为短期止血，不是最终方案。 |
| B. 直接用 `generateSql()` 外层包 CTE | 实现看似简单。 | 会绕过 preAgg rewrite 和物理列权限 beforeExecute，和用户约束冲突。 | 不建议。 |
| C. 在 `generateSql()` 内部直接加入 Pivot TopN | 调用链短，外层改动少。 | 污染通用 SQL 生成器，把 Pivot 语义耦合进 queryModel 基础能力；后续普通 query、MCP、preAgg 都更难维护。 | 不建议。 |
| D. 外层注入内层管道对象到 context | 灵活，短期能快速拼出路径。 | 破坏 queryModel 生命周期所有权，后续新增 step 时容易漏接；权限和缓存语义难治理。 | 不建议。 |
| E. 固定内部管道 + phase/options + Pivot domain planner | 边界清晰，能保留权限/预聚合/物理列校验，后续可扩展。 | 成本最高，需要重构生命周期和补测试矩阵。 | 推荐作为完整方案。 |

### 16.4 后续维护性判断

推荐方案的维护成本主要来自“多一种 queryModel 执行 phase”。但该成本是集中、显式、可测试的：

- 新增 step 时必须声明支持 `NORMAL_QUERY`、`PREPARE_MANAGED_RELATION`、`EXECUTE_MANAGED_RELATION` 中哪些阶段，并更新 context 读写依赖图。
- Pivot 不感知具体权限和预聚合实现，只依赖 `ManagedSqlRelation` 契约。
- Pivot 对 `ManagedSqlRelation` 的 `isWrappable/isPermissionValidated/isPreAggApplied/additiveKind` 做防御性断言，不依赖调用方口头保证。
- preAgg、cache、permission 的 owner 可以在各自模块维护 phase 行为，不需要 PivotPipeline 手动拼接。
- 如果后续新增 chart、report、export 等也需要“受管 SQL relation”，可以复用同一 prepare/execute 能力。

不推荐方案的维护成本更隐蔽：

- 如果外层直接注入管道或调用 `generateSql()` 硬包 SQL，后续权限 step、preAgg step、cache step 每新增一次，都要检查所有外层调用方。
- 如果把 Pivot 语义塞进通用 SQL generator，普通 query 的稳定性会被 Pivot 特例拖累。
- 如果继续只靠内存截断，用户看到的是偶发错误结果，排查成本会转嫁到业务侧。

因此，推荐方案虽然代码量更高，但复杂度主要是“为了保留 queryModel 治理能力而显式化生命周期”，不是无意义的抽象膨胀。

## 17. 最终结论

该方案已从候选评估推进到 Stage 1-4 完成交付，并完成正式签收。最终采用方案 E：固定内部管道 + phase/options + Pivot domain planner。

签收结论为 `accepted-with-risks`。已验证事项包括：

- managed relation prepare/execute 保留 queryModel 权限链路、预聚合 rewrite、物理列权限校验和参数绑定；
- `PivotAxisDomainSqlPlanner` 将 having 与 TopN 在同一 SQL domain planner 中处理，保证 having 先于 TopN；
- Stage 4 已修正 non-additive subtotal/grandTotal domain bounding，保留 tuple 相关性、null 语义和 grandTotal 过滤；
- SQLite、MySQL 5.7 fallback、PostgreSQL 验证已记录，MySQL 5.7 不声明 SQL-shape parity；
- non-additive subtotal/grandTotal domain 超过 500 时按设计 fail-closed，作为后续容量增强项跟踪。

后续不应在未评审的情况下扩大本能力边界。若要支持更大 domain、tree SQL 化、级联 Generate 或 outer Pivot cache，应按独立 follow-up 方案推进，继续遵守“不绕过 queryModel 生命周期”的约束。
