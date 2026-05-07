# dataset.query_model

执行单模型查询，支持过滤、排序、分组聚合、计算字段、时间窗口、pivot 透视表和向量相似度检索。

> 本工具只处理单模型。跨模型 Join、Union、派生查询或一次返回多个 Plan 时，使用 `dataset.compose_script`。

## AI 能力路由与退化策略

| 用户意图 | 首选能力 | 不要这样做 | 超出边界后如何退化 |
|---|---|---|---|
| 明细列表、过滤、排序、简单聚合 | `columns` + `slice` + `orderBy` | 不要为普通 `sum(field)` 创建 `calculatedFields` | 字段不确定时先调用 `dataset.describe_model_internal` |
| 条件聚合 | `columns` 内 `sum/avg/count(if(...)) as alias` | 不要生成 `sum_if`、`count_if`、SQL `case when` | 改成 `if(条件, 值, 0/null)` 形式 |
| 复杂标量表达式、窗口排名、显式 agg | `calculatedFields` | 不要把复杂表达式直接塞进 `columns` | 先定义计算字段别名，再在 `columns` 中引用 |
| 同比、环比、周环比、YTD、MTD、rolling 7/30/90 | `timeWindow` | 不要用 `CALCULATE`、手写 SQL 窗口或多段日期拼接 | 如果还要透视表，拆成独立查询；本工具不支持 `pivot + timeWindow` |
| 行列交叉表、小计/总计、树形 rows 轴 | `pivot` | 不要同时传 `pivot` 和 `columns` | 简单分组退回普通 `columns`；跨模型分析退回 `dataset.compose_script` |
| 子级占父级比例 | `pivot.metrics[].type = "parentShare"` | 不要生成 `ROLLUP_TO` 或 `REMOVE(childDim)` 假装父级导航 | 仅 rows 相邻层级和可加度量；遇到 tree/cascade/不可加度量时去掉派生指标或说明当前不支持 |
| 当前列相对首列/末列基准 | `pivot.metrics[].type = "baselineRatio"` | 不要生成 `CELL_AT`、`AXIS_MEMBER` 或坐标索引 | 仅 columns 轴 `baseline=first/last`；遇到 tree/cascade 时去掉派生指标或说明当前不支持 |
| 跨模型 Join、Union、派生查询、多 Plan 返回 | `dataset.compose_script` | 不要用单个 `query_model` 硬拼 | 用 SemanticDSL `prevPlan.query({...})`、`.join()`、`.union()` |

## 字段规则

直接使用 `dataset.describe_model_internal` 返回的字段名。

| 字段类型 | 用法 |
|---|---|
| 维度 | `xxx$id`(查询/过滤), `xxx$caption`(展示) |
| 父子维度 | `xxx$hierarchy$id`(层级范围过滤), `xxx$hierarchy$caption`(层级汇总展示) |
| 属性/度量 | 直接使用字段名 |
| 向量字段 | 仅支持 `similar`/`hybrid` 操作符 |

字段名是闭集：只使用 describe 返回的完整字段名。模型没返回就不要拼、不要推导、不要自己拼接多跳字段，例如不要把 `move$invoiceUserId` 猜成 `move$invoiceUserId$caption`，也不要把关系字段简写成 `caption`。遇到 `invoiceDate` 这类普通属性时，不要追加 `$year` / `$month`；只有 describe 明确返回 `invoiceDate$year` / `invoiceDate$month` 时才可使用。字段未暴露时，省略该列、改用暴露该字段的模型，或说明当前模型未提供该粒度。

父子维度还可在 `xxx$id` 上使用 `childrenOf`、`descendantsOf`、`selfAndDescendantsOf`、`ancestorsOf`、`selfAndAncestorsOf` 等层级操作符；需要限制深度时加 `maxDepth`。

## 参数

### columns (普通查询必填；pivot 查询不要传)

声明要查询的列，支持普通字段或简单内联聚合表达式：
```json
["product$categoryName", "sum(salesAmount) as totalSales", "count(orderId) as orderCount"]
```

支持聚合函数：`sum`、`avg`、`count`、`max`、`min`、`group_concat`、`countd`、`stddev_pop`、`stddev_samp`、`var_pop`、`var_samp`。

规则：
- 引擎可以推断部分 `groupBy`，但为了避免首轮 GROUP BY 错误，只要 `columns` 同时包含维度和聚合表达式/模型预定义聚合 measure，就必须显式传 `groupBy`。
- `groupBy` 必须包含每个非聚合维度列；展示 `partner$caption` 时通常同时查询并分组 `partner$id` 和 `partner$caption`，例如 `columns: ["partner$id", "partner$caption", "arOverdueAmount"], groupBy: ["partner$id", "partner$caption"]`。
- 使用 `partner$caption` 等维度分组时，`columns` 只放这些分组维度和聚合指标。不要为了解释或排查额外混入 `move$caption`、`moveName`、`lineCount` 等未分组明细字段；除非用户明确要求统计行数，否则不要添加 `lineCount`。
- 如果模型说明提供 AR 业务指标（如 `arOverdueAmount`、`arOutstandingAmount`、`arOverdueCustomerCount`），优先直接作为 measure 使用；不要再包装 `sum(...)`，也不要同时加入不属于该分组口径的明细列。
- `slice` 是语义过滤：明细/维度字段下推为 WHERE，预定义或已选聚合 measure（如 `{"field": "arOutstandingAmount", "op": ">", "value": 0}`）会由引擎提升为 HAVING。不要在同一个 `$or` / `$and` 逻辑组里混合明细字段和聚合 measure；复杂二阶段结果过滤使用 `dataset.compose_script` 的 plan `.query({...})`。如果主查询已经返回 0 或空结果，直接回答。
- `columns` 只放简单单层聚合：`agg(field) as alias`。
- 条件聚合统一写成 `sum/avg/count(if(条件, 满足时的值, 不满足时的值))`，不要生成 `count_if`、`sum_if` 或 SQL `case when`。

### calculatedFields (可选)

复杂计算放在 `calculatedFields` 中，再在 `columns` 引用别名：
```json
[
  {"name": "netAmount", "expression": "salesAmount - discountAmount"},
  {"name": "salesRank", "expression": "RANK()", "partitionBy": ["product$categoryName"], "windowOrderBy": [{"field": "salesAmount", "dir": "desc"}]}
]
```

使用边界：
- 需要窗口函数、`partitionBy`、`windowOrderBy`。
- 需要显式指定 `agg` 参数。
- 表达式引用其他计算字段。
- 普通 `sum(field)` 或 `sum(if(...))` 仍写在 `columns`。
- `calculatedFields.expression` 使用 Foggy 表达式 DSL，不是数据库 SQL。不要生成 SQL 方言函数名或语句片段，例如 `DATEDIFF(...)`、`DATE_TRUNC(...)`、`YEAR(...)`、`MONTH(...)`、`CASE WHEN`；如果本文档没有明确列出某个函数，就不要猜函数名。
- 对“超过 N 天 / older than N days / overdue more than N days”这类过滤，优先在调用工具前计算截止日期，然后用已暴露的日期字段做 `slice`。例如当前日期为 2026-05-06，“逾期超过 30 天”表达为 `{"field": "dateMaturity", "op": "<", "value": "2026-04-06"}`。除非用户明确要求展示天数且已确认支持的表达式语法，否则不要创建 `overdueDays`、`DATEDIFF(...)` 或日期差 calculatedFields。

跨当前分组占比使用受限 `CALCULATE`：
```text
SUM(metric) / NULLIF(CALCULATE(SUM(metric), REMOVE(groupByDim)), 0)
```

示例：按客户类型计算全国占比。
```json
{
  "columns": ["customer$customerType", "salesAmount", "totalShare"],
  "groupBy": ["customer$customerType"],
  "calculatedFields": [
    {"name": "totalShare", "expression": "SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(customer$customerType)), 0)"}
  ]
}
```

限制：`CALCULATE` 只支持 `CALCULATE(SUM(metric), REMOVE(groupByDim...))`；`REMOVE` 只能移除当前 `groupBy` 中的维度；占比分母必须使用 `NULLIF(CALCULATE(...), 0)`；不要用 `CALCULATE` 做同比、环比、累计或滚动窗口。父级占比使用 `pivot.metrics.parentShare`，跨列首/末基准比较使用 `pivot.metrics.baselineRatio`。

### timeWindow (可选)

声明式时间窗口分析。同比、环比、周同比、YTD、MTD、rolling 7/30/90 天优先用 `timeWindow`，不要手写窗口 SQL。

`value` 可选；传入时必须是两个元素的数组 `[start, end]`，每个元素为合法日期或相对表达式。`rollingAggregator` 支持 `sum` / `avg` / `count` / `min` / `max`，不填默认 `sum`。

```json
{
  "columns": ["salesDate$year", "salesAmount", "salesAmount__ratio"],
  "groupBy": ["salesDate$year"],
  "timeWindow": {
    "field": "salesDate$id",
    "grain": "year",
    "comparison": "yoy",
    "targetMetrics": ["salesAmount"]
  }
}
```

派生列命名：
- 同环比：`{metric}__prior`、`{metric}__diff`、`{metric}__ratio`
- 累计：`{metric}__ytd`、`{metric}__mtd`
- 滚动：`{metric}__rolling_7d`、`{metric}__rolling_30d`、`{metric}__rolling_90d`

限制：`targetMetrics` 不可引用 calculatedFields；可在 `timeWindow` 结果列之上追加后置标量 `calculatedFields`，但不能设置 `agg`、`partitionBy`、`windowOrderBy`、`windowFrame`。

### 日期分桶与 SQL 函数边界

普通“按月/按周/按年分组”不要在 `columns`、`groupBy`、`orderBy` 中生成 `DATE_TRUNC(...)`、`YEAR(...)`、`MONTH(...)` 等 SQL 函数字段，也不要把 `DATE_TRUNC` 当字段名。先调用 `dataset.describe_model_internal`，只使用返回的日期粒度字段（如 `salesDate$year`、`salesDate$month`、`salesDate$week`）进行展示、分组和排序。不要把普通日期属性自行映射成 `$year` / `$month`；例如只返回 `invoiceDate` 时，不要生成 `invoiceDate$year`。

如果模型没有暴露所需日期粒度字段，不要自造 SQL 函数；改用已有日期字段过滤、`timeWindow`，或说明当前模型未提供该粒度。同比、环比、YTD、MTD、rolling 继续使用 `timeWindow`。

普通日期差过滤也不要自造 SQL 函数。对“最近 N 天”“超过 N 天未处理”“逾期超过 N 天”等条件，先算出绝对日期边界，再在 `slice` 中比较已暴露日期字段；只有在用户明确要求输出日期差数值、且工具文档明确支持对应 Foggy 表达式时，才使用 `calculatedFields`。

### slice (可选)

数组形式过滤条件，建议统一使用标准格式：
```json
[
  {"field": "status", "op": "=", "value": "done"},
  {"field": "amount", "op": ">", "value": 100},
  {"$or": [
    {"field": "totalAmount", "op": ">=", "value": 1000},
    {"field": "customer$customerType", "op": "=", "value": "VIP"}
  ]}
]
```

等值简写仅限简单等值：`[{"status": "done"}]`。使用 `$or` 嵌套逻辑时不要混用简写。

| 类型 | 操作符 |
|---|---|
| 等值 | `=`, `!=`, `<>` |
| 比较 | `>`, `>=`, `<`, `<=` |
| 模糊 | `like`, `left_like`, `right_like` |
| 集合 | `in`, `not in` |
| 空值 | `is null`, `is not null` (无需 value) |
| 区间 | `[]`, `[)`, `()`, `(]` (value 为 `[start,end]`) |
| 字段/表达式 | `{"value": {"$field": "b"}}`, `{"$expr": "salesAmount > costAmount * 1.2"}` |
| 父子层级 | `childrenOf`, `descendantsOf`, `selfAndDescendantsOf`, `ancestorsOf`, `selfAndAncestorsOf` |

### orderBy (可选)

排序格式：`"field"`(升序)、`"field desc"`(降序)、`"-field"`(降序)。必须使用 `columns` 中定义的别名，如 `year` 而非 `YEAR(createdAt)`。

开启 `pivot` 时，顶层 `orderBy` 不是透视轴排序或 TopN 控制；需要轴内排序时使用 `pivot.rows[*].orderBy` 或 `pivot.columns[*].orderBy`。

### 其他控制参数

| 参数 | 默认值 | 说明 |
|---|---|---|
| `limit` | 无 | 普通查询分页大小；pivot 轴裁剪用 `pivot.rows[*].limit` / `pivot.columns[*].limit` |
| `start` | `0` | 偏移量 |
| `returnTotal` | `true` | 是否返回总行数 |
| `distinct` | `false` | 与 `groupBy` 和聚合函数互斥 |

## 结果使用纪律

- 查询返回的 `items`、`schema`、`pagination` 已足够回答题面时，直接最终回答。
- 不要为了确认空结果、0 值或已满足条件的主查询而重复查询全表、所有月份或所有状态；除非用户明确要求审计、排查数据质量或解释异常。
- 对口径明确的问题，按题目限定的期间、状态、对象回答，不主动扩展到其他期间或其他状态。

## Pivot 透视表查询 (Pivot)

当用户需要交叉表、多层分组小计、树形层级展示、父级占比、列基准比较时，使用 `pivot` 替代常规 `columns` + `groupBy`。

> **硬边界**：
> - `pivot` 与 `columns` 互斥。开启 `pivot` 时不要传 `columns`。
> - `pivot` 与 `timeWindow` 互斥。同比/环比/YTD/rolling 使用 `timeWindow`；行列透视使用 `pivot`。用户同时要求时，拆成两个查询或先回答当前无法在一个请求里同时表达。
> - 普通列表或简单分组聚合不要用 `pivot`。
> - 跨模型 Join / Union / 派生查询不要用 `pivot` 硬拼，退回 `dataset.compose_script`。
> - 顶层 `orderBy` / `limit` 不作为透视轴排序或 TopN 控制；需要排序或裁剪行/列成员时，写在对应 `pivot.rows[*]` / `pivot.columns[*]` 轴对象上。

### Pivot 请求结构

#### 普通 pivot（支持 grandTotal；rowSubtotals 静默忽略；columnSubtotals 不支持）

```json
{
  "pivot": {
    "rows": [{"field": "region$caption"}],
    "columns": [{"field": "salesDate$year"}],
    "metrics": ["salesAmount", "profitRate"],
    "outputFormat": "grid",
    "options": {"grandTotal": true}
  }
}
```

> 普通 pivot 能力边界：
> - `grandTotal: true` — 支持，在结果末尾追加全列汇总行（度量必须是可加 SUM/COUNT 聚合）。
> - `rowSubtotals: true` — 静默忽略（单层行轴小计无实际意义）。
> - `columnSubtotals: true` — 拒绝并报错，任何情况下都不支持，移除后重试。

#### 二层 cascade pivot（支持 rowSubtotals / grandTotal）

```json
{
  "pivot": {
    "rows": [
      {"field": "salesTeam$caption", "orderBy": ["-salesAmount"], "limit": 3},
      {"field": "salesperson$caption", "orderBy": ["-salesAmount"], "limit": 5}
    ],
    "metrics": ["salesAmount"],
    "outputFormat": "flat",
    "options": {"rowSubtotals": true, "grandTotal": true}
  }
}
```

> `rowSubtotals` 和 `grandTotal` 在 rows 轴恰好两层 cascade（两个都带 `limit`）时支持，度量必须是可加聚合（SUM / COUNT）。`columnSubtotals` 始终不支持。

- `rows` / `columns`：行列轴，可传字段名或对象。树形层级仅支持 rows 轴：`{"field": "org$caption", "hierarchyMode": "tree"}`，且不能与 `crossjoin`、小计、总计组合。
- `metrics`：原生度量名，或受控派生指标对象。对象形式当前只支持 `parentShare` 和 `baselineRatio`；不支持 `expr`。
- `outputFormat`：`flat`(默认)、`grid`、`tree`。
- `options`：小计、总计和 `crossjoin` 稀疏补全。

### 轴内分组截断

在 `rows` 或 `columns` 中可对特定层级做分组内 TopN，分区键隐式为该字段前面的所有轴字段：
```json
{
  "pivot": {
    "rows": ["product$categoryName", {"field": "product$subCategoryName", "orderBy": ["-salesAmount"], "limit": 3}],
    "metrics": ["salesAmount"],
    "outputFormat": "flat"
  }
}
```

### 父级占比 (parentShare)

同一 rows 轴内相邻层级的“子级占父级”比例，使用 `parentShare`：
```json
{
  "pivot": {
    "rows": [{"field": "product$categoryName"}, {"field": "product$subCategoryName"}],
    "metrics": ["salesAmount", {"name": "categoryShare", "type": "parentShare", "of": "salesAmount"}],
    "outputFormat": "flat"
  }
}
```

可显式消歧：`{"name": "share", "type": "parentShare", "of": "salesAmount", "axis": "rows", "level": "subCategory", "parentLevel": "category"}`。

限制：只支持 rows 轴相邻层级；`of` 必须引用同一 metrics 中的原生可加度量；不支持 `hierarchyMode=tree`、cascade TopN，也不能参与 `having` / `orderBy` / `limit`。超出边界时移除该派生指标或说明不支持，不要改用 `ROLLUP_TO`、`REMOVE(childDim)` 或自造 `expr`。

### 基准比较 (baselineRatio)

同一行跨 columns 轴相对首列/末列的基准比较，使用 `baselineRatio`：
```json
{
  "pivot": {
    "rows": ["categoryName"],
    "columns": ["month"],
    "metrics": ["salesAmount", {"name": "salesIndex", "type": "baselineRatio", "of": "salesAmount", "axis": "columns", "baseline": "first"}]
  }
}
```

限制：`baseline` 只能是 `"first"` 或 `"last"`；`axis` 只能是 `"columns"` 且 columns 轴不能为空；`of` 必须引用可加原生度量；不支持树形模式、cascade TopN，也不能参与 `having` / `orderBy` / `limit`。超出边界时移除该派生指标或说明不支持，不要改用 `CELL_AT`、`AXIS_MEMBER` 或坐标索引。

### 高级函数警告 (Fail-closed)

当前版本未开放以下函数，运行时会阻断：`ROLLUP_TO`、`CELL_AT`、`AXIS_MEMBER`、`AXIS_REF`，以及任意维度的 MDX `Generate`、跨轴集合生成。

需要全局占比时用受限 `CALCULATE(..., REMOVE(...))`；需要父级占比时用 `pivot.metrics.parentShare`；需要跨列首末基准比较时用 `pivot.metrics.baselineRatio`；需要同环比或累计时用 `timeWindow`。如果用户要求 `pivot + timeWindow`、任意 MDX 集合代数、多层跨轴坐标或三层级联 TopN，说明当前公开 DSL 不支持，不要生成隐藏函数。

## 错误处理指南

1. 字段不存在：外键使用 `xxx$id` 或 `xxx$caption`，不确定时调用 `dataset.describe_model_internal`。
2. 函数未定义：`count_if` / `sum_if` 改为 `sum/avg/count(if(...))`。
3. columns 复杂表达式：移到 `calculatedFields` 中定义别名，再放入 `columns`。
4. GROUP BY 错误：移除未分组明细列，或把该普通字段加入 `groupBy`；预定义聚合 measure 与维度一起使用时，只保留分组维度和 measure。
5. slice 语法错误：检查 `$or` 嵌套，复杂逻辑统一使用标准格式。
6. Pivot 互斥错误：`pivot` 不能与 `columns` 或 `timeWindow` 同时出现。
7. Pivot tree 错误：`hierarchyMode=tree` 仅支持 rows 轴和 `outputFormat=tree`，不能与 `crossjoin`、小计、总计同用。
8. Pivot 派生指标错误：`parentShare` / `baselineRatio` 不能与 tree/cascade 混用，也不能参与 having/orderBy/limit。
9. Pivot 小计/总计被拒绝：`columnSubtotals` 在任何情况下都不支持，移除后重试。普通 pivot 的 `rowSubtotals` 会静默忽略；`grandTotal` 在普通 pivot 和二层 cascade 下均支持（度量须为可加聚合）。
10. Pivot 域值过大：收窄 `slice`、减少轴层级、增加轴 `limit`，或改为普通分页明细查询。
