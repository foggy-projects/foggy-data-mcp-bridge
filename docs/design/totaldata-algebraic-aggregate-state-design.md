---
doc_role: architecture-decision
status: accepted
implementation_status: implemented
baseline: main-after-9.5.2-runtime-console
last_reviewed: 2026-08-29
review_status: passed-independent-review
affected_modules: foggy-dataset-model-engine, foggy-dataset-model-preagg
---

# `totalData` 代数聚合状态设计

> 后续结构性收敛见 [`totalData` 共享结果阶段 Renderer 设计](totaldata-shared-result-stage-renderer-design.md)。
> 该 ADR 以提交 `267dd887` 为功能基线，落实本文关于 MAIN/TOTAL 共用结果阶段 renderer 的要求。

## 1. 决策摘要

Foggy 不再把已经分组后的 `AVG` 标量当作可再次聚合的输入。`AVG(expr)` 在逻辑计划中降低为
可合并状态 `{SUM(expr), COUNT(expr)}`，状态作为内部列贯穿 aggregate、window、
post-aggregate 和 postSlice 阶段；只有在用户结果投影或 `totalData` 最终投影时才执行
`SUM(state_sum) / NULLIF(SUM(state_count), 0)`。

该状态通道只存在于查询计划和生成 SQL 中，不改变 `dataset.query_model` 的请求、响应和公开字段。
主结果继续返回每个分组的原始 AVG；`totalData` 返回完整有效范围或 postSlice 后幸存分组对应的
事实总体 AVG。

这项决策同时约束预聚合：新版物化表必须提供独立 SUM/COUNT 状态列才能承载 AVG 上卷；旧版只有
单 AVG 标量的物理表与新版运行时不兼容，必须在接入新版节点前按迁移手册停机重建并 FULL refresh。
HYBRID 与无法证明 lineage 的 advanced AVG 路径继续 fail closed 并回退事实范围。

## 2. 问题与根因

分组查询当前会先生成每组 AVG，再生成类似下面的总计 SQL：

```sql
SELECT AVG(tx.averageTransportAmountPerWaybill)
FROM (
    SELECT opening_year,
           AVG(transport_amount) AS averageTransportAmountPerWaybill
    FROM waybill
    GROUP BY opening_year
) tx
```

当各组样本量不同，这得到的是“分组平均值的无权重平均”，而不是事实总体平均。例如：

```text
2026: sum=65,570,288.70, count=7,189, avg=9,120.91927945
2025: sum=22,452,062.24, count=2,714, avg=8,272.68321297

错误: (9,120.91927945 + 8,272.68321297) / 2 = 8,696.80124621
正确: (65,570,288.70 + 22,452,062.24) / (7,189 + 2,714)
    = 8,888.45308896
```

根因不是 SQL 方言，而是聚合计划把 `AVG` 错误分类为可以对输出值再次应用同一函数的
distributive aggregate。AVG 实际是 algebraic aggregate：它可以由有限状态合并，但状态不是
AVG 标量，而是 SUM 与非空 COUNT。

## 3. 语义边界

### 3.1 查询阶段顺序

`totalData` 必须遵循与主结果相同的有效集合语义：

```text
事实行
  -> WHERE / 普通 slice / 权限谓词
  -> GROUP BY + aggregate states
  -> HAVING
  -> finalize 分组度量
  -> post-aggregate / window
  -> postSlice
  -> merge 幸存分组的 aggregate states
  -> finalize totalData
```

因此：

- `WHERE`、权限谓词和普通 slice 约束进入聚合的事实行；
- `HAVING` 约束可以进入总计的分组；
- `postSlice` 约束 window/post-aggregate 后可以进入总计的分组；
- `orderBy` 只决定显示顺序，不改变 totalData；
- `start`、`limit` 只作用于主结果分页，不改变 totalData；
- 无 `groupBy` 时直接在完整过滤事实范围执行原始聚合，不做二次合并。

### 3.2 主结果与总计结果

- 主结果的每组 AVG 保持 `AVG(expr)` 语义和原有数值；
- 总计 AVG 合并内部 SUM/COUNT 状态；
- 内部状态不能出现在 API items、totalData、metadata 或用户可引用字段中；
- window/postAggregate 产生的 rank、share、running value 等结果阶段字段只参与展示和
  postSlice，不作为 totalData 中可再次汇总的度量输出；
- `total` 仍表示分页前、最终结果阶段过滤后的结果行数。

### 3.3 postSlice 语义

本决策采用“postSlice 后总计”语义，与当前 final-stage count 策略保持一致。如果未来产品需要
“过滤前总计”，应增加显式请求策略，不能通过优化器隐式改变。

## 4. 聚合分类与状态契约

| 聚合 | partial state | merge | finalize | 可从单个结果标量上卷 |
|---|---|---|---|---|
| SUM | `SUM(expr)` | `SUM(state)` | identity | 是 |
| COUNT | `COUNT(expr)` | `SUM(state)` | identity | 是 |
| MIN | `MIN(expr)` | `MIN(state)` | identity | 是 |
| MAX | `MAX(expr)` | `MAX(state)` | identity | 是 |
| AVG | `SUM(expr)`, `COUNT(expr)` | 分别 SUM | `sum/count` | 否 |
| COUNT DISTINCT | 精确集合或可证明互斥状态 | 取决于状态 | cardinality | 否 |
| VAR/STDDEV | count、sum、二阶状态 | 状态公式 | 方差/标准差 | 否 |
| GROUP_CONCAT | 有序值状态 | 与顺序相关 | string aggregate | 通常否 |

本次实现只新增 AVG 状态。其他非 distributive 聚合继续沿现有路径或 fail closed，不借本修复扩展
语义范围。

同一查询只要包含不能安全 merge 的公开聚合字段，`TotalDataAggregatePlan` 整体进入 `REFUSED`，
不能让 AVG 状态计划接管后继续对 `COUNT_DISTINCT`、`GROUP_CONCAT`、VAR 或 STDDEV 使用错误的
标量上卷。SUM、COUNT、MIN、MAX 与 AVG 可以安全混合。

AVG 的 COUNT 必须是 `COUNT(expr)`，不能替换成 `COUNT(*)`，从而保持 SQL 对 NULL 的处理。

## 5. 逻辑模型

### 5.1 `TotalDataAggregatePlan`

新增 engine 内部 total 计划，不进入稳定 Model SPI，也不修改 `QueryStagePlan.Stage` 的全局字段：

```java
enum AggregateLoweringStatus {
    NOT_APPLICABLE,
    LOWERED,
    REFUSED
}

final class TotalDataAggregatePlan {
    AggregateLoweringStatus status;
    List<TotalOutputSpec> outputs;
    List<AggregateStateSpec> states;
    List<CalculatedTotalSpec> calculatedOutputs;
    String refusalCode;
    String refusalReason;
}

final class AggregateStateSpec {
    AggregateLeafId leafId;
    DbAggregation aggregation;
    DbColumnType resultType;
    List<AggregateStateColumn> partialStates;
    AggregateStateFinalizer finalizer;
}

final class AggregateStateColumn {
    String internalAlias;
    BoundSqlExpression partialArgument;
    DbAggregation partialAggregation;
    DbAggregation mergeAggregation;
    DbColumnType type;
}

record AggregateLeafId(String ownerAlias, int preorderOrdinal) {}

record AggregateLeafSpec(
    AggregateLeafId leafId,
    DbAggregation aggregation,
    BoundSqlExpression argument,
    DbColumnType resultType
) {}

record BoundSqlExpression(String sql, List<Object> params) {}

sealed interface TotalExpressionNode {}
record AggregateLeafRef(AggregateLeafId leafId) implements TotalExpressionNode {}
record CalculatedOutputRef(String alias) implements TotalExpressionNode {}

record CalculatedTotalSpec(
    String publicAlias,
    TotalExpressionNode expressionRoot
) {}

record AggregateSqlPlan(String sql, List<Object> params) {}
```

AVG 示例：

```text
leafId            = averageTransportAmountPerWaybill#agg0
partialStates     = [
  (__foggy_state_0_sum,   SUM(raw_expr),   SUM),
  (__foggy_state_0_count, COUNT(raw_expr), SUM)
]
finalize          = merged_sum / NULLIF(merged_count, 0)
```

内部别名由计划生成器分配，不从用户字段拼接，不允许请求引用。lowering 在 SQL renderer 之前一次性
完成；renderer 只能消费计划，不能在各阶段重新猜测聚合语义。

`leafId` 由所属公开/隐藏计算字段 alias 和 AST 前序遍历序号组成，只要求在单次计划内稳定。每个
aggregate function node 都有独立 leafId；`AVG(a)+AVG(b)` 会得到 `agg0`、`agg1` 两组状态。
允许后续按结构哈希去重完全相同的 leaves，但正确性不依赖去重。

### 5.2 来源表达式

状态生成必须使用表达式 AST 或 engine 内部分析结果保留的原始 aggregate argument：

- 预定义 AVG measure：保留被 `AVG(...)` 包装前的字段声明；
- 内联 `AVG(expr)`：保留 AST 生成的 argument SQL；
- 计算型 AVG：AST 每个 aggregate function node 产生结构化 `AggregateLeaf`；
- 标量包装 AVG：`COALESCE(AVG(x), 0)`、`ROUND(AVG(x), 2)`、`-AVG(x)`、`AVG(x)+1`
  保留“标量表达式 + aggregate leaves”，merge leaves 后重新求值；
- 计算型比率：递归解析依赖图，在叶子聚合状态 merge 后重新求值；
- 即使 SUM、COUNT 等依赖没有出现在公开 columns，也要作为隐藏叶子递归物化；
- 计算字段依赖必须检测缺失引用和循环，不能依赖声明顺序；
- `emptyDefault` 在 aggregate leaf finalize 后、外层标量表达式求值时应用，不能污染 SUM/COUNT 状态。

lowering 生成新的结构化 `TotalExpressionNode` 树；原 aggregate function node 被替换为带 leafId 的
`AggregateLeafRef`。TOTAL finalizer 递归渲染该树，并通过 leafId 查找 merge 后的状态表达式。
因此一个公开输出可无歧义绑定任意数量的 AVG/SUM/COUNT leaves，不能用 publicAlias 直接充当
aggregate leaf 身份。

禁止从已经生成的 `AVG(sql)` 字符串中用正则或括号解析恢复来源表达式。

直接 measure 与 groupBy wrapper 的 aggregate source 保存到 engine 内部
`AnalyzedAggregateColumn`/identity map；不向 `AggregationDbColumn` 增加状态字段，也不通过覆盖
`CalculatedDbColumn.getAggregation()` 改变所有优化器和预聚合消费者看到的全局语义。

### 5.3 `RenderMode` 与内部输出

当前 `QueryStagePlan.Stage` 只描述拓扑和诊断，renderer 并不按它的 output aliases 自动渲染。
因此不在该类上增加一个没有执行语义的 `internalOutputs` 字段。现有 renderer 接收同一个
`QueryStagePlan`，同时接收：

```text
RenderMode.MAIN  + empty/no-op total plan
RenderMode.TOTAL + lowered TotalDataAggregatePlan
```

renderer 内部的投影构造器统一处理两种模式：

- MAIN 保持当前公开 SQL，不添加或泄漏 states；
- TOTAL 在 aggregate stage 额外产生 partial states；
- TOTAL 的 post-aggregate/window stage 原样透传 states；
- postSlice 只过滤行，不 finalize 或修改 states；
- TOTAL final projection merge 并 finalize states；
- 两种模式复用同一 stage topology、filter builder、CTE composer 和参数收集器，不复制第二套
  window/postAggregate SQL 拼装逻辑。

### 5.4 lowering 三态与 fail closed

- `NOT_APPLICABLE`：没有 groupBy 或没有需要状态化/重算的输出，允许进入现有安全路径；
- `LOWERED`：所有公开 total 输出都获得了 merge/finalize 策略，必须使用生成的 total 计划；
- `REFUSED`：存在 AVG source 丢失、循环依赖、混合不可 merge 聚合或其他无法证明的语义，抛出
  明确的 `TOTAL_DATA_AGGREGATE_NOT_MERGEABLE`，绝不能退回 legacy `AVG(tx.groupAvg)`。

`null` 不能同时表达“不适用”和“拒绝”。预聚合候选缺少 states 不等于整个查询拒绝：此时只跳过
候选，继续使用已经 LOWERED 的事实表 total 计划。

### 5.5 独立 SQL、参数与根 CTE composer

`JdbcModelQueryEngine` 分别保存：

```text
sql + values             主结果计划
aggSql + aggValues       totalData 计划
```

`JdbcQueryModelImpl.queryTotalData` 在没有 preAgg total 计划时必须使用 `aggValues`，不能继续复用
主查询 `values`。`AggregateSqlPlan` 必须同时返回 SQL 和不可变参数列表。

新增 engine 内部根级 CTE composer，按依赖顺序组合：

```text
domain transport CTEs
  -> aggregate stage
  -> post-aggregate/window stage
  -> postSlice/final stage
```

参数按相同拓扑顺序收集，每个 CTE/stage 自带参数，不通过扫描 SQL 推断。禁止把 `WITH domain...`
嵌入另一个 derived table。当前明确不支持的“derived renderer + CTE domain transport”和
“window derived renderer”继续 fail closed，本次不顺带解除。

`BoundSqlExpression` 是不可变的 SQL+params 单元。每次将 argument 渲染进一个占位符位置，都必须
追加该单元的一份参数副本。例如参数化 `AVG(expr(?))` 降低为 `SUM(expr(?))` 与
`COUNT(expr(?))` 后，total SQL 有两个占位符，参数列表按 SQL 顺序包含两份原参数。不能复用一个
参数条目服务多个 `?`。

当前 `SqlFragment` 没有 params 字段；现有普通计算表达式产生的 `BoundSqlExpression.params` 为空。
如果 capability/custom expression 确实产生绑定参数，只有在调用链能提供完整参数时才允许
LOWERED；拿不到参数必须进入 `REFUSED`，不能把 `?` 当成无参数 SQL 继续执行。

### 5.6 保留命名空间

`__foggy_state_`、`__FOGGY_TOTAL_STAGE_` 等前缀是 engine 保留命名空间。请求 columns、计算字段、
postAggregate 名称、别名和模型字段在分析阶段若命中保留前缀必须拒绝，不能只依赖最终投影隐藏。

## 6. SQL 计划

### 6.1 普通分组

```sql
WITH grouped AS (
    SELECT opening_year,
           AVG(transport_amount) AS average_amount,
           SUM(transport_amount) AS __foggy_state_0_sum,
           COUNT(transport_amount) AS __foggy_state_0_count
    FROM waybill
    WHERE ...
    GROUP BY opening_year
    HAVING ...
)
SELECT <dialect-safe-ratio>(SUM(__foggy_state_0_sum),
                            SUM(__foggy_state_0_count)) AS average_amount,
       COUNT(*) AS total
FROM grouped
```

### 6.2 window/postAggregate/postSlice

```sql
WITH grouped AS (
    SELECT opening_year,
           AVG(transport_amount) AS average_amount,
           SUM(transport_amount) AS __foggy_state_0_sum,
           COUNT(transport_amount) AS __foggy_state_0_count,
           SUM(receivable_amount) AS receivable_amount
    FROM waybill
    WHERE ...
    GROUP BY opening_year
    HAVING ...
),
windowed AS (
    SELECT grouped.*,
           RANK() OVER (ORDER BY receivable_amount DESC) AS amount_rank,
           receivable_amount
             / NULLIF(SUM(receivable_amount) OVER (), 0) AS amount_share
    FROM grouped
),
surviving AS (
    SELECT *
    FROM windowed
    WHERE amount_rank <= ?
)
SELECT <dialect-safe-ratio>(SUM(__foggy_state_0_sum),
                            SUM(__foggy_state_0_count)) AS average_amount,
       COUNT(*) AS total
FROM surviving
```

主查询与 total 查询使用同一 stage topology 和 renderer。MAIN 模式生成当前公开结果；TOTAL 模式
额外生成、透传和合并 states，并返回独立 `AggregateSqlPlan(sql, params)`。

本次只覆盖 planner 已经支持的阶段组合：普通分组、window + 可选 postSlice，以及
postAggregate + 可选 postSlice。当前被 planner 拒绝的 window + postAggregate 混用、window
derived rendering 不因本设计而开放。

### 6.3 计算型平均

例如：

```text
totalSales = SUM(salesAmount)
rowCount   = COUNT(orderId)
average    = totalSales / rowCount
```

totalData 必须递归物化未公开选择的 `totalSales` 和 `rowCount`，先 merge 它们，再通过表达式 AST
计算 `average`；不能要求调用方显式选择依赖，也不能 SUM、AVG 或 Java 汇总各组 `average`。

标量包装表达式采用同一规则：AST lowering 记录 aggregate leaves，TOTAL finalizer 用 merge 后的
leaf expression 重新求值完整标量 AST。表达式引用图必须拓扑排序并检测循环。

## 7. Renderer 责任

### 7.1 共享 stage renderer

- 接收 `RenderMode` 和可选 `TotalDataAggregatePlan`；
- MAIN 生成现有公开分组指标；
- TOTAL 为 `AggregateStateSpec` 生成 partial state 列；
- 两种模式保持同一 GROUP BY、WHERE、HAVING、权限谓词和 filter builder；
- state expression 与公开 AVG expression 使用同一个结构化 raw argument；
- renderer 不自行做 lowering，也不从 SQL 解析 states。

### 7.2 Window/Post-aggregate renderer

- MAIN 显式投影公开输入；TOTAL 额外投影计划要求的内部状态；
- window 与 post-aggregate 表达式只使用公开/合法输入；
- 内部状态不能参与用户字段解析和 order/slice 引用；
- CTE 与当前已支持的 derived-table fallback 使用同一投影构造器；既有不支持组合继续拒绝。

### 7.3 Final renderer

- MAIN：仅公开列；
- TOTAL：merge state、按依赖拓扑 finalize 度量并生成 `total`；
- TOTAL 不生成 ORDER BY、LIMIT 或 OFFSET；
- TOTAL 的 rank/share/running 等结果阶段别名不参与 merge/finalize，但为保持提交 `267dd887` 的响应
  key-set 兼容，若用户公开选择则输出同名 NULL；这些字段仍可决定 postSlice 幸存集合。该兼容规则
  由后续共享 renderer ADR 覆盖并固定测试；
- 返回 `AggregateSqlPlan(sql, params)`，不修改主查询参数列表。

## 8. 预聚合契约

AVG 预聚合使用与查询 total 相同的代数状态。`measureColumnNames[measure]` 对 AVG 表示物理列前缀，
`PreAggMeasureStateContract` 从此前缀唯一派生：

```text
<prefix>__sum   = SUM(source_expr)
<prefix>__count = COUNT(source_expr)
```

DDL、FULL refresh 和 INCREMENTAL refresh 都必须写入这两列，不再创建或写入单一 `AVG(source_expr)`
物理值。MAIN 从更细粒度预聚合上卷时计算
`SUM(<prefix>__sum) / NULLIF(SUM(<prefix>__count), 0)`；algebraic TOTAL 直接把这两列绑定为内部
state 后再 finalize。safe divide 仍由方言适配器负责，SQLite 等方言不能发生整数除法。

匹配规则必须满足：

```text
query required states ⊆ pre-aggregation materialized states
```

当前支持边界：

1. FULL 预聚合查询允许 predefined AVG measure 使用双状态做等粒度读取和粗粒度 rollup；
2. HYBRID（预聚合表与原始事实 `UNION`）仍 fail closed，因为两个分支尚未统一暴露同构 state；
3. advanced final-stage/calculated AVG 在无法证明完整 leaf/state lineage 时仍回退事实计划；
4. 旧版只有单 AVG 列的物理表与新版运行时不兼容。运行时不会猜测、反射或在线迁移旧 schema，必须
   按 [`AVG 预聚合状态迁移手册`](preagg-avg-state-migration-runbook.md) 停机重建并执行 FULL refresh。

预聚合主分组结果与 totalData 路由可以独立决定，但不能用一个候选的证明替代另一个计划的证明。
预聚合拦截器只能在证明状态集合完备时替换 `AggregateSqlPlan` 的 SQL 与参数；否则不能覆盖它。

## 9. 方言、类型与 NULL

- AVG 分母使用 `COUNT(expr)`；
- 空有效集合或全部 expr 为 NULL 时返回 NULL；
- 使用 `NULLIF(merged_count, 0)` 避免除零；
- 不修改公开 `FDialect` SPI；在 engine 内部提供类型化安全比率 adapter，例如
  `renderRatio(FDialect, numerator, denominator, DbColumnType resultType)`；
- SQLite/SQL Server 必须显式避免整数除法；MySQL/PostgreSQL 仍由方言实现决定必要 cast；
- `DbColumnType.NUMBER/MONEY` 是当前可获得的结果类型边界。本次不虚构缺失的 precision/scale；
  方言实现使用安全 widening，并通过与原生 `AVG(expr)` 的集成测试证明数值等价；
- 后续若模型列暴露 precision/scale，finalizer 再升级为精确 cast contract；
- state alias 必须经过 dialect identifier quoting；
- agg 参数按根 CTE/stage 拓扑独立收集，不要求与主查询参数列表对象相同；
- CTE transport、WHERE、HAVING、postSlice 的参数顺序必须与 total SQL 中占位符顺序一致。

## 10. 不采用的方案

### 10.1 `AVG(group_avg)`

仅在每组非空样本量完全相同时偶然正确，不能作为优化。

### 10.2 postSlice 后按分组键回查事实表

理论上正确，但需要重新扫描事实表、重建所有分组表达式和 NULL-safe 键比较；当 groupBy 字段未公开
选择、存在表达式分组或多方言时复杂且容易漂移。只可作为无可合并状态聚合的显式 fallback。

### 10.3 Java 端汇总

需要取得全部未分页分组，破坏数据库端过滤、内存和分页边界，不采用。

### 10.4 只依赖 GROUPING SETS/ROLLUP

可优化普通 grand total，但 postSlice/window 发生在分组后，仍需保留可合并状态；且方言覆盖不一致。

### 10.5 为 totalData 复制一套 stage renderer

短期可工作，但 public/internal 字段、参数和过滤顺序会与主 renderer 漂移。应让同一 stage
renderer 接收 MAIN/TOTAL 模式，而不是复制 window/postAggregate 拼装代码。

## 11. 实施顺序

1. 保留已有回归测试，先增加 review 指出的失败用例；
2. 引入 lowering 三态、`TotalDataAggregatePlan`、`AggregateStateSpec` 和独立 agg params；
3. 扩展表达式 AST 元数据，形成递归 aggregate leaf/计算依赖闭包；
4. 抽取共享投影构造器与根级 CTE composer，让现有 stage renderer 接收 MAIN/TOTAL 模式；
5. TOTAL 模式让 aggregate、window、post-aggregate renderer 透传内部状态；
6. 改造 total final projection 为 merge/finalize，并使用独立 agg params；
7. 实现 pre-aggregation AVG 的 `<prefix>__sum/<prefix>__count` 物化、FULL rollup 与 HYBRID/advanced
   path fail-closed；
8. 删除临时 AVG SQL 分支以及对通用 support column 聚合语义的全局修改；
9. 完成定向、模块和 reactor 全量测试。

如果第 3–6 步需要修改公开 QueryModel SPI、请求 DTO，或扩散到 compose/pivot 的独立计划协议，
必须暂停并重新 review；本决策只授权 engine 内部的 total 计划与现有 renderer 模式化。

## 12. 测试矩阵

### 12.1 必须新增或保留

- 不等样本量分组：事实 AVG 与简单组均值明确不同；
- 预定义 AVG measure；
- 内联 `AVG(expr)`；
- 计算型 SUM/COUNT 比率，包括没有公开选择 SUM/COUNT 依赖；
- `COALESCE/ROUND/一元负号/算术` 包装的 AVG；
- 单一公开字段包含多个聚合叶子，例如 `AVG(unitPrice) + AVG(unitCost)` 和
  `AVG(unitPrice) / NULLIF(AVG(unitCost), 0)`；
- 计算字段依赖乱序、缺失与循环检测；
- 多维 groupBy；
- WHERE、权限谓词、HAVING；
- postAggregate calculation；
- window + postSlice；
- postAggregate + postSlice；
- postSlice 过滤后只合并幸存组状态；
- expr 含 NULL，验证 `COUNT(expr)`；
- 无 groupBy；
- start/limit/orderBy 不改变 totalData；
- optimizeAggSql 开启和关闭；
- SUM、COUNT、MIN、MAX 不回归；
- 预聚合 AVG DDL、FULL/INCREMENTAL refresh 只生成 SUM/COUNT states；
- FULL 预聚合 AVG 等粒度和 rollup 使用加权状态；
- HYBRID 与无法证明 lineage 的 advanced AVG 回退事实计划；
- 旧单值 AVG schema 迁移前不得接入新运行时；
- 当前支持的 CTE 与 derived-table fallback 组合；
- domain transport + WHERE + HAVING + postSlice 参数顺序；
- `BoundSqlExpression` 在 SUM/COUNT 两次渲染时复制参数并保持占位符顺序；
- SQLite 与其他方言的 ratio SQL 形状/Decimal 结果；
- AVG 与 COUNT_DISTINCT 等不可 merge 聚合混合时明确拒绝；
- 保留内部别名冲突检测；
- debug SQL 不暴露内部值或错误重复参数。

### 12.2 明确 fail closed

- `AVG(DISTINCT expr)` 在没有精确集合状态时；
- 无法从 AST/engine 分析结果取得 aggregate argument；
- 预聚合 HYBRID 的两个分支无法提供同构 SUM/COUNT state；
- advanced/calculated AVG 无法证明物化 state lineage；
- 自定义或 holistic aggregate 没有声明 merge/finalize contract；
- 公开别名命中 engine 保留前缀；
- lowering 依赖缺失或成环。

## 13. 验收标准

- 复现请求返回 `totalData.averageTransportAmountPerWaybill ≈ 8888.45308896`；
- 年度分组 AVG 不变；
- postSlice/window 场景按幸存分组事实样本量加权；
- SUM、COUNT、MIN、MAX 不受影响；
- totalData 不受分页和排序影响；
- 无 groupBy、WHERE、HAVING 和计算型平均通过；
- 内部状态不出现在公开结果；
- 不生成 `AVG(tx.<group average alias>)`；
- FULL 预聚合 AVG 只从 SUM/COUNT states 读取并与事实 AVG 等价；
- HYBRID/advanced AVG 无法证明状态完备时安全回源；
- 旧单 AVG 物化表按迁移手册完成停机重建和 FULL refresh；
- 定向测试通过后，最多执行三次 reactor 全量测试。

## 14. 可观测性

debug/explain 应记录结构化信息，而不是依赖 SQL 字符串判断：

```text
totalDataPlan=algebraic-state
aggregateLoweringStatus=NOT_APPLICABLE|LOWERED|REFUSED
aggregateStates=[averageTransportAmountPerWaybill:AVG(sum,count)]
resultStageFilter=true|false
preAggTotalRoute=fact|state-capable-preagg
preAggFallbackReason=AVG_STATE_NOT_MATERIALIZED
```

SQL debug 可以显示内部别名，但 API metadata 和数据结果不能显示内部列。

## 15. 独立 review 记录

2026-08-28 首次独立 review 结论为 NO-GO。设计已按以下意见修订，二次 review 通过前不得进入开发：

- 用 `TotalDataAggregatePlan + RenderMode` 替代无执行语义的全局 `Stage.internalOutputs`；
- 增加独立 `aggSql + aggValues`；
- lowering 改为 `NOT_APPLICABLE/LOWERED/REFUSED` 三态；
- 补齐隐藏依赖、标量包装、emptyDefault 和循环检测的表达式 DAG 契约；
- 增加根级 CTE composer 和拓扑参数收集；
- 增加方言安全比率 renderer，并明确当前类型信息边界；
- 混合不可 merge 聚合整计划拒绝；
- 不解除现有 window/postAggregate、window derived 等阶段限制；
- 增加 engine 保留别名命名空间；
- 明确结果阶段字段只过滤集合，不作为 totalData 输出；
- 最终实现不得依赖修改通用 support column 的全局聚合语义。

同日二次 review 已确认首轮问题全部关闭，但因多聚合叶子身份和参数化 argument 复制契约仍不完整，
结论仍为 NO-GO。本文已进一步增加：

- 单计划稳定的 `AggregateLeafId` 与 `AggregateLeafSpec`；
- 通过 leafId 绑定 merge state 的结构化 `TotalExpressionNode`；
- `BoundSqlExpression(sql, params)` 以及每个 SQL 占位符位置复制参数的规则；
- 参数来源不完整时 lowering 必须 `REFUSED`；
- 单公开字段多 AVG leaves 与参数复制测试。

第三次独立 review 获得 GO 前仍不得进入开发。

同日第三次独立 review 确认上述 leafId、参数复制和多叶子测试契约全部关闭，最终结论为 GO，
允许按本文进入测试优先开发。

## 16. 参考

- Apache Calcite `AggregateReduceFunctionsRule`：`AVG(x) -> SUM(x) / COUNT(x)`
  <https://calcite.apache.org/javadocAggregate/org/apache/calcite/rel/rules/AggregateReduceFunctionsRule.html>
- Apache DataFusion aggregate accumulator state：
  <https://datafusion.apache.org/library-user-guide/functions/adding-udfs.html>
- Gray 等，Data Cube 与 algebraic aggregate：
  <https://arxiv.org/abs/cs/0701155>
- Cube 非可加预聚合 measure 的 SUM/COUNT 分解：
  <https://docs.cube.dev/recipes/pre-aggregations/non-additivity>
