# 9.0.0 详细设计 (04)：不可加度量小计辅助聚合查询

## 一、 背景与目标

当前 Pivot P0/S8.2 已完成基础聚合、Having、TopN、CrossJoin、小计/总计、Properties 后置贴合和父子层级树。现有 `SubtotalInjector` 对所有度量默认做内存 SUM，这只对 `SUM/COUNT` 类可加度量成立。

S8.3 的目标是让小计、列小计和总计在遇到不可加度量时仍然返回正确结果，而不是把子节点值简单相加。

典型错误：

```text
COUNT_DISTINCT(customerId) 的品类小计 != 各子品类 COUNT_DISTINCT(customerId) 之和
grossMargin 的大区小计 != 各城市 grossMargin 之和
AVG(orderAmount) 的月份总计 != 各品类 AVG(orderAmount) 之和
```

S8.3 只解决 Pivot 小计/总计的正确性，不扩展新的 DSL 语义。

## 二、 前置门槛

S8.3 开工前必须先冻结一个前置事实：**Phase 1 叶子格聚合必须已经按 TM/QM 度量元数据执行，而不是对所有 metric 硬编码 `SUM`。**

当前 Pivot 主查询的叶子 grain 是后续所有小计、总计和辅助查询的基线。如果 `AVG`、`COUNT_DISTINCT`、`MIN/MAX` 等度量在叶子格阶段已经被错误地按 `SUM` 计算，那么后续 `RollupCache` 再正确也无法修复整体结果。

因此 S8.3 的第一项实现门槛是：

1. `PivotPipeline.executePhase1(...)` 构造 `GroupByItem(metric, agg)` 时，必须从 `QueryModel` / `DbMeasure.getAggregation()` 读取 metric 默认聚合。
2. 找不到度量元数据时，不能默认 `SUM`；应按既有查询模型规则解析，解析失败则 fail-closed。
3. `calculatedFields` 作为 metric 参与 Pivot 时，必须依据表达式处理器或字段定义推断聚合语义；推断失败不得进入 subtotal/grandTotal。
4. 叶子格聚合修正需要独立测试，不能被 S8.3 rollup 测试间接覆盖。

## 三、 非目标

1. 不把基础查询改成数据库 `ROLLUP/CUBE/GROUPING SETS` 主路径。
2. 不支持跨轴任意单元格引用、`ROLLUP_TO`、`CELL_AT`、`AXIS_REF`。
3. 不在 S8.3 中实现任意 N 时间窗口。
4. 不要求第一版支持所有聚合函数，未知或高风险聚合必须 fail-closed。
5. 不在内存中用子节点结果推导 `AVG/COUNT_DISTINCT/STDDEV/VAR`。

## 四、 度量可加性判定

### 1. 基础度量分类

引擎应从 `QueryModel` / `TableModel` 的 `DbMeasure.getAggregation()` 获取默认聚合类型，并将 Pivot 请求中的 `metrics` 分为以下策略：

| 聚合类型 | Rollup 策略 | 说明 |
| :--- | :--- | :--- |
| `SUM` | `IN_MEMORY_SUM` | 父级可由互斥子分组求和得到 |
| `COUNT` | `IN_MEMORY_SUM` | 子分组不重叠时 count 可求和 |
| `MIN` | `IN_MEMORY_MIN` | 父级可由子分组最小值再取 min |
| `MAX` | `IN_MEMORY_MAX` | 父级可由子分组最大值再取 max |
| `AVG` | `AUX_REQUERY` | 不能平均子分组 AVG，必须重查父级 grain |
| `COUNT_DISTINCT` | `AUX_REQUERY` | 去重集合可能跨子分组重叠，必须重查父级 grain |
| `STDDEV_POP` / `STDDEV_SAMP` / `VAR_POP` / `VAR_SAMP` | `AUX_REQUERY` 或第一版拒绝 | 若无稳定 SQL 下推，先 fail-closed |
| `GROUP_CONCAT` / `CUSTOM` / `WINDOW` / `NONE` / `PK` | `UNSUPPORTED` | 第一版不进入小计辅助聚合 |

> 注：`MIN/MAX` 不属于“可加”，但可以从子分组 rollup 得到正确父级值，因此归入内存可汇总策略。

### 2. 计算字段传染规则

`calculatedFields` 需要建立依赖图：

```json
[
  { "name": "grossProfit", "expression": "revenueAmount - costAmount" },
  { "name": "grossMargin", "expression": "grossProfit / NULLIF(revenueAmount, 0)" }
]
```

判定规则：

1. 如果计算字段只由 `IN_MEMORY_SUM` 度量通过 `+/-` 组成，例如 `revenueAmount - costAmount`，可标记为 `RECOMPUTE_FROM_BASE`，在父级 grain 上先得到 base metric，再重算表达式。
2. 如果计算字段包含 `/`、`AVG`、`COUNT_DISTINCT`、`CALCULATE`、窗口函数或不可识别函数，默认标记为 `AUX_REQUERY` 或 `UNSUPPORTED`。
3. 如果计算字段依赖另一个计算字段，按依赖图拓扑排序，依赖中出现 non-additive 后向上传染。
4. 如果表达式解析失败，必须 fail-closed，不允许按 SUM 处理。

计算字段计划必须输出 `requiredBaseMetrics`：

```text
grossMargin = grossProfit / NULLIF(revenueAmount, 0)
requiredBaseMetrics = [grossProfit, revenueAmount]
```

当用户只把 `grossMargin` 放进 `pivot.metrics` 时，辅助 rollup 查询仍必须自动补查 `grossProfit` 和 `revenueAmount`，用于在父级 grain 上重算 ratio。补查的 base metrics 只进入 `RollupCache`，不自动暴露到最终输出。

第一版建议只实现：

| 计算字段形态 | 策略 |
| :--- | :--- |
| `a + b` / `a - b` / `a + b - c` | `RECOMPUTE_FROM_BASE` |
| `(a - b) / NULLIF(a, 0)` | `RECOMPUTE_FROM_BASE`，自动补查 `a/b` 等 base metrics |
| 包含 `CALCULATE/OFFSET/REMOVE` | `UNSUPPORTED` |
| 包含待实现函数 `ROLLUP_TO/CELL_AT/AXIS_REF` | `UNSUPPORTED` |

## 五、 Rollup Grain 枚举

### 1. 基本定义

令：

```text
R = rows 字段列表
C = columns 字段列表
L = R + C，叶子 grain
```

Phase 1 主查询只负责叶子 grain：

```text
GROUP BY R全部字段 + C全部字段
```

辅助查询只为需要小计/总计的父级 grain 生成。

### 2. 行轴小计

当 `rowSubtotals = true` 且 `rows = [r1, r2, r3]` 时，需要：

```text
grain: [r1, r2] + C全部字段   // r3 汇总为 ALL
grain: [r1]     + C全部字段   // r2/r3 汇总为 ALL
```

不生成叶子 grain `[r1, r2, r3] + C全部字段`，因为主查询已经提供。

### 3. 列轴小计

当 `columnSubtotals = true` 且 `columns = [c1, c2]` 时，需要：

```text
grain: R全部字段 + [c1]       // c2 汇总为 ALL
```

### 4. 总计

`grandTotal = true` 至少需要：

```text
grain: C全部字段              // 所有 rows 汇总为 GRAND_TOTAL
```

如果同时启用列总计，还需要：

```text
grain: []                    // 全表总计
```

### 5. 行列小计交叉

当行小计和列小计同时启用，需要生成交叉父级 grain：

```text
rowSubtotalGrains x columnSubtotalGrains
```

例如：

```text
rows = [category, subCategory]
columns = [year, month]

需要的辅助 grain:
1. [category, year, month]        // 行小计
2. [category, year]               // 行小计 + 列小计
3. [category, subCategory, year]  // 列小计
4. [year, month]                  // grand row
5. [year]                         // grand row + column subtotal
6. []                             // full grand total
```

### 6. 去重规则

Grain 使用有序字段列表作为 key：

```text
RollupGrainKey = join(fieldNames, "\u001F")
```

枚举后必须去重，避免同一个 grain 被多个选项重复生成。

### 7. 可见域语义

S8.3 的 subtotal/grandTotal 语义必须与现有内存 subtotal 保持一致：**小计只统计经过 `having` 和 `TopN` 后仍然存活的可见成员域**。

因此辅助查询不能只按父级 grain 全量聚合，否则会把已被 TopN 截掉或 Having 过滤掉的成员重新算回小计。

执行规则：

1. `Having` 和 `TopN` 先执行，得到 surviving row domain 与 surviving column domain。
2. `RollupGrainEnumerator` 基于 surviving domain 生成辅助查询计划。
3. `NonAdditiveRollupExecutor` 必须把 surviving domain 转换为过滤条件，约束辅助 SQL 只聚合可见成员。
4. 如果某个 surviving domain 太大，不能生成超长 `IN (...)` 条件，应触发保护策略：拆批、临时表、或 fail-closed。第一版可先 fail-closed，并给出明确错误。

> 注：如果未来产品需要“父级全量总计，不受 TopN 可见域影响”，必须新增显式选项，不能复用当前 subtotal 语义。

## 六、 辅助 SQL 合并策略

### 1. 第一版采用 UNION ALL

为了避免 N+1 查询风暴，所有辅助 grain 合并成一条 SQL：

```sql
SELECT 'row_l1_col_leaf' AS _pivot_rollup_key,
       category,
       NULL AS subCategory,
       year,
       month,
       COUNT(DISTINCT customer_id) AS uniqueCustomerCount,
       AVG(order_amount) AS avgOrderAmount
FROM fact_sales
WHERE ...
GROUP BY category, year, month

UNION ALL

SELECT 'row_l1_col_l1' AS _pivot_rollup_key,
       category,
       NULL AS subCategory,
       year,
       NULL AS month,
       COUNT(DISTINCT customer_id) AS uniqueCustomerCount,
       AVG(order_amount) AS avgOrderAmount
FROM fact_sales
WHERE ...
GROUP BY category, year
```

每个分支必须输出相同列集合：

1. `_pivot_rollup_key`
2. 所有 row 字段，未保留的字段填 `NULL`
3. 所有 column 字段，未保留的字段填 `NULL`
4. 所有需要辅助聚合的度量

### 2. 不依赖数据库原生 ROLLUP

默认实现不使用 `ROLLUP/CUBE/GROUPING SETS`。原因：

1. SQLite 等测试和轻量部署环境不稳定支持。
2. 不同方言对 `GROUPING()`、NULL 标记、ROLLUP 顺序差异较大。
3. `UNION ALL` 更容易生成 `_pivot_rollup_key`，便于注入 cache。

后续可以在方言能力明确后新增优化路径：

```text
RollupSqlStrategy = UNION_ALL | GROUPING_SETS
```

### 3. 查询入口与完成门槛

第一版优先复用 `SemanticQueryServiceV3.queryModel(...)` 多次执行可能最省改动，但 S8.3 的目标是“批次合并”，因此建议新增内部组件：

```java
NonAdditiveRollupPlanner
NonAdditiveRollupExecutor
RollupCache
RollupGrain
RollupMetricPlan
```

实施口径分两级：

1. **Correctness Prototype**：`Executor` 可以先按 `RollupGrain` 生成多个内部聚合请求并串行执行，用于验证语义、cache key 和测试基线。
2. **S8.3 正式完成**：必须实现 `UNION ALL` 或等价批次合并，避免 N+1 查询风暴。

也就是说，多请求 executor 只能作为中间阶段，不能作为 S8.3 的最终验收形态。

## 七、 Rollup Cache 与注入规则

### 1. Cache Key

辅助查询结果进入 `RollupCache`：

```text
RollupCacheKey = rollupKey + rowCoord + colCoord
```

其中：

```text
rowCoord = rows 全字段坐标；汇总掉的字段使用 ALL
colCoord = columns 全字段坐标；汇总掉的字段使用 ALL
grand row 使用 GRAND_TOTAL
```

示例：

```text
row_l1_col_leaf | category=Electronics, subCategory=ALL | year=2024, month=03
grand_col_l1    | category=GRAND_TOTAL, subCategory=GRAND_TOTAL | year=2024, month=ALL
```

内部实现不得只依赖字符串 `ALL`、`GRAND_TOTAL` 或 `NULL` 表达汇总坐标，因为真实业务维度值也可能为这些字符串或 SQL NULL。

推荐结构化坐标：

```java
record RollupCoordinate(
    String field,
    Object value,
    boolean rolledUp,
    boolean grandTotal
) {}
```

序列化到 debug 日志或 `_sys_meta` 时可以显示为 `ALL/GRAND_TOTAL`，但 cache 比较必须使用结构化对象，区分：

1. 真实维度值为 `"ALL"`。
2. 真实 SQL 值为 `NULL`。
3. 该字段被 rollup 掉。
4. 该字段属于 grand total 坐标。

### 2. 注入顺序

`SubtotalInjector` 改造成两段：

1. 先按现有逻辑生成 subtotal/grandTotal 行的坐标和 `_sys_meta`。
2. 对每个 metric 按策略填值：
   - `IN_MEMORY_SUM`：对子节点求和。
   - `IN_MEMORY_MIN`：对子节点取最小值。
   - `IN_MEMORY_MAX`：对子节点取最大值。
   - `AUX_REQUERY`：从 `RollupCache` 读取。
   - `RECOMPUTE_FROM_BASE`：从当前 subtotal 行已填好的 base metrics 重算。
   - `UNSUPPORTED`：抛错或填 null，取决于 fail policy。

### 3. 失败策略

默认 fail-closed：

| 场景 | 行为 |
| :--- | :--- |
| cache miss | 抛 `InvalidPivotRollupException` |
| 不支持的聚合类型参与小计 | 抛 `UnsupportedPivotMetricException` |
| 计算字段依赖解析失败 | 抛 `UnsupportedPivotMetricException` |
| 辅助查询返回重复 cache key | 抛 `InvalidPivotRollupException` |
| 辅助查询结果为空 | 如果 leaf 也为空则返回空；否则抛错 |

只有父子层级动态深度超过阈值这一类已知风险，可以在产品确认后降级为父级 non-additive metric = `null` 并追加 warning。默认实现仍建议抛错。

## 八、 与现有 Pipeline 的集成点

现有流程：

```text
Phase 1: SQL leaf aggregation
Phase 1.5: hierarchy skeleton
Phase 2: Having -> TopN -> CrossJoin -> Subtotal
Phase 2.5: Properties
Phase 3: Shaping
```

S8.3 调整为：

```text
Phase 1: SQL leaf aggregation
Phase 1.5: hierarchy skeleton
Phase 1.6: detect rollup metric strategy + enumerate auxiliary grains
Phase 1.7: execute auxiliary rollup query and build RollupCache
Phase 2: Having -> TopN -> CrossJoin -> Subtotal(cache-aware)
Phase 2.5: Properties
Phase 3: Shaping
```

注意：

1. `Having/TopN` 会改变最终存活成员域。第一版辅助查询可以在 TopN 之后执行，以减少无用父级 grain；这需要把 Phase 1.6/1.7 放到 TopN 之后、Subtotal 之前。
2. 如果辅助查询在 TopN 之前执行，必须接受多查数据但实现简单。
3. 推荐第一版采用：

```text
Phase 2.1 Having
Phase 2.2 TopN
Phase 2.3 enumerate auxiliary grains based on surviving domains
Phase 2.4 execute auxiliary rollup query
Phase 2.5 CrossJoin
Phase 2.6 Subtotal(cache-aware)
```

CrossJoin 应在辅助查询之后执行，避免为人工补齐的空单元格生成无意义辅助聚合。

## 九、 父子层级特殊处理

父子层级 `hierarchyMode=tree` 的 subtotal 不是固定 rows 前缀，而是动态树父节点。

第一版建议：

1. `hierarchyMode=tree + non-additive metric + rowSubtotals` 先拒绝。
2. 如果只需要叶子和已有父节点本身的事实聚合值，可由 Phase 1 结果提供。
3. 后续增强再基于闭包表枚举每个父节点的 descendants，并为父节点 grain 生成辅助查询。

拒绝信息：

```text
hierarchyMode=tree 暂不支持不可加度量的小计/总计辅助聚合。请移除 rowSubtotals/grandTotal，或改用可加度量。
```

## 十、 测试矩阵

### 1. 单元测试

| 测试类 | 重点 |
| :--- | :--- |
| `MetricAdditivityAnalyzerTest` | 聚合类型分类、计算字段依赖传染、未知函数拒绝 |
| `RollupGrainEnumeratorTest` | row subtotal、column subtotal、grand total、交叉 grain 去重 |
| `RollupCacheTest` | cache key 构建、重复 key 拒绝、miss fail-closed |
| `SubtotalInjectorNonAdditiveTest` | SUM/MIN/MAX 内存 rollup、COUNT_DISTINCT 从 cache 注入、计算字段重算 |

### 2. 集成测试

基于 ecommerce SQLite 增加：

1. `COUNT_DISTINCT(customerId)` 的 row subtotal 正确性。
2. `COUNT_DISTINCT(customerId)` 的 grand total 正确性。
3. 反例断言：子品类 distinct count 之和不等于品类 distinct count。
4. `AVG(orderAmount)` 小计由辅助查询给出，不平均子节点 AVG。
5. `grossMargin = grossProfit / revenueAmount` 在小计行按父级 revenue/grossProfit 重算。
6. `rowSubtotals + columnSubtotals + grandTotal` 同时开启时 cache key 不冲突。
7. 不支持聚合类型参与 subtotal 时 fail-closed。
8. `Having/TopN` 后 subtotal 只统计可见成员，不把被过滤或截断的成员算回父级。
9. 输出 metric 只有 `grossMargin` 时，辅助查询自动补查 `grossProfit/revenueAmount`，最终结果不额外暴露 base metrics。

### 3. 回归测试

必须确保：

1. 纯 `SUM/COUNT` 的现有 64 个测试不受影响。
2. 不启用小计/总计时，不触发辅助查询。
3. 没有 non-additive metric 时，不触发辅助查询。
4. `properties` 后置贴合仍在 subtotal 后执行。

## 十一、 推荐实施拆分

### S8.3.0 叶子聚合修正

修正 `PivotPipeline.executePhase1(...)`：

1. metric 的 `GroupByItem.agg` 来自度量元数据，不再硬编码 `SUM`。
2. 对 `AVG/COUNT_DISTINCT/MIN/MAX` 增加叶子格集成测试。
3. calculatedFields 推断失败时，pivot subtotal/grandTotal 场景 fail-closed。

### S8.3.1 判定与计划

新增：

```text
MetricAdditivityAnalyzer
RollupMetricPlan
RollupStrategy
RollupGrain
RollupGrainEnumerator
```

只产出计划，不执行 SQL。计划中必须包含 `requiredBaseMetrics`、surviving domain 输入和正式/过渡 executor 标记。

### S8.3.2 Cache-aware SubtotalInjector

改造 `SubtotalInjector`：

```java
SubtotalInjector.apply(resultSet, rowFields, colFields, metrics, options, rollupPlan, rollupCache)
```

保留旧签名作为无 cache 兼容入口。

### S8.3.3 辅助查询执行

先实现可工作的查询执行路径，再做 SQL 合并优化：

1. 过渡版：按 `RollupGrain` 执行多个内部聚合请求。
2. 目标版：合并为 `UNION ALL`。

过渡版只能作为 correctness prototype；目标版完成后才允许标记 S8.3 完成。

### S8.3.4 集成验收

补齐 COUNT_DISTINCT、AVG、ratio calculatedFields 的真实 SQL 比对。

## 十二、 验收标准

S8.3 完成必须满足：

1. 任一 non-additive metric 出现在小计/总计场景时，不再使用子节点 SUM。
2. 辅助查询 grain 可追踪，debug extra 中能看到 rollup grain 数量和策略。
3. cache miss、重复 key、未知聚合类型都有明确错误。
4. 集成测试能证明 distinct count 和 avg subtotal 与人工 SQL 一致。
5. 纯 additive 场景性能和现有行为不退化。
6. `Having/TopN` 后的小计只统计可见成员域。
7. ratio calculatedFields 能通过隐式 base metrics 在父级 grain 上重算，且 base metrics 不污染最终输出。
8. 正式完成形态使用 `UNION ALL` 或等价批次合并，不以 N 次内部查询作为最终交付。
