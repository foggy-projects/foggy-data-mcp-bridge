# dataset.query_model

执行数据模型查询，支持过滤、排序、分组聚合、计算字段。

> **Note**: 本工具适用于单模型查询。如果遇到单模型 DSL 无法解决的复杂查询（如跨模型 Join、Union、派生查询、或者需要返回多个 Plan 的场景），请使用 `dataset.compose_script` 工具。

## AI 能力选择

| 场景 | 使用 | 边界与退化 |
|---|---|---|
| 明细、过滤、排序、简单聚合 | `columns` / `slice` / `orderBy` | 字段不确定先用 `dataset.describe_model_internal` |
| 条件聚合 | `sum/avg/count(if(...)) as alias` | 不要生成 `sum_if`、`count_if`、SQL `case when` |
| 复杂表达式、窗口排名、显式 agg | `calculatedFields` | 简单 `sum(field)` 留在 `columns` |
| 同比、环比、YTD、MTD、rolling | `timeWindow` | 不要用 `CALCULATE`；不能和 `pivot` 同用，必要时拆成两个查询 |
| 交叉表、小计/总计、树形 rows、父级占比、列基准比 | `pivot` | 不要同时传 `columns`；普通分组退回 `columns`；跨模型退回 `dataset.compose_script` |
| 跨模型 Join / Union / 派生查询 | `dataset.compose_script` | 单个 `query_model` 不表达这些计划图 |

## 字段规则

**直接使用 `dataset.describe_model_internal` 返回的字段名**

| 字段类型 | 用法 |
|---|---|
| 维度 | `xxx$id`(查询/过滤), `xxx$caption`(展示) |
| 父子维度 | `xxx$hierarchy$id`(层级范围过滤), `xxx$hierarchy$caption`(层级汇总展示) |
| 属性/度量 | 直接使用字段名 |


只使用 describe 返回的完整字段名，不要自己拼接多跳字段，例如不要把 `move$invoiceUserId` 猜成 `move$invoiceUserId$caption`，也不要把关系字段简写成 `caption`。字段未暴露时，省略该列或改用暴露该字段的模型。

### 父子维度 (Parent-Child Dimension)
层级结构维度（如组织架构、公司层级）支持两种访问视角：
- **xxx$id / xxx$caption**: 精确匹配该节点（与普通维度相同）
- **xxx$hierarchy$id / xxx$hierarchy$caption**: 通过闭包表匹配节点及所有后代（层级汇总）

还可在 slice 中对 `xxx$id` 使用层级操作符进行细粒度查询（见操作符表）。

## 参数

### columns (普通查询必填；pivot 查询不要传)
声明要查询的列，支持普通字段或简单的内联聚合表达式（系统自动处理 groupBy）：
```json
["product$categoryName", "sum(salesAmount) as totalSales", "count(orderId) as orderCount"]
```
支持的聚合函数：`sum`、`avg`、`count`、`max`、`min`、`group_concat`、`countd`(去重计数)、`stddev_pop`、`stddev_samp`、`var_pop`、`var_samp`。

> **WARNING**:
> - 引擎可以推断部分 `groupBy`，但为了避免首轮 GROUP BY 错误，只要 `columns` 同时包含维度和聚合表达式/模型预定义聚合 measure，就必须显式传 `groupBy`。
> - `groupBy` 必须包含每个非聚合维度列；展示 `partner$caption` 时通常同时查询并分组 `partner$id` 和 `partner$caption`，例如 `columns: ["partner$id", "partner$caption", "arOverdueAmount"], groupBy: ["partner$id", "partner$caption"]`。
> - 使用 `partner$caption` 等维度分组时，`columns` 只放这些分组维度和聚合指标。不要为了解释或排查额外混入 `move$caption`、`moveName`、`lineCount` 等未分组明细字段；除非用户明确要求统计行数，否则不要添加 `lineCount`。
> - 如果模型说明提供 AR 业务指标（如 `arOverdueAmount`、`arOutstandingAmount`、`arOverdueCustomerCount`），优先直接作为 measure 使用；不要再包装 `sum(...)`，也不要同时加入不属于该分组口径的明细列。
> - `slice` 是语义过滤：明细/维度字段下推为 WHERE，预定义或已选聚合 measure（如 `{"field": "arOutstandingAmount", "op": ">", "value": 0}`）会由引擎提升为 HAVING。不要在同一个 `$or` / `$and` 逻辑组里混合明细字段和聚合 measure；如果主查询已经返回 0 或空结果，直接回答；复杂二阶段过滤使用 `dataset.compose_script` 在结果 plan 上 `.query({...})`。
> - `columns` 仅用于简单的单层聚合：`agg(field) as alias`。
> - **条件聚合** 统一使用 `sum/avg/count(if(条件, 满足时的值, 不满足时的值))` 写法，例如：`sum(if(state == "sale", amountTotal, 0)) as confirmed`。**绝对不要**生成 `count_if`、`sum_if` 之类的未定义函数，也绝对不要生成 SQL 风格的 `case when`。

### calculatedFields (可选)
如果计算逻辑比较复杂，必须放在 `calculatedFields` 中：
```json
[
  {"name": "netAmount", "expression": "salesAmount - discountAmount"},
  {"name": "salesRank", "expression": "RANK()", "partitionBy": ["product$categoryName"], "windowOrderBy": [{"field": "salesAmount", "dir": "desc"}]}
]
```

**边界判定：何时使用 calculatedFields？**
- 需要使用窗口函数（如 `RANK()`、移动平均，通过 `partitionBy`、`windowOrderBy` 配置）。
- 需要显式指定 `agg` 参数。
- 表达式中引用了其他的计算字段。
如果只是普通的 `sum(field)` 或 `sum(if(...))`，请直接写在 `columns` 中。

**跨当前分组占比：使用受限 `CALCULATE`**

- 全局占比：`SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(customer$customerType)), 0)`
- 组内占比：`ROUND(SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(product$categoryName)), 0), 4)`
- 同比、环比、累计、滚动不要用 `CALCULATE`，继续使用 `timeWindow`。
- 父级占比不要用 `ROLLUP_TO` 或 `REMOVE(childDim)`，使用 `pivot.metrics.parentShare`。
- 跨列首/末基准比较不要用 `CELL_AT` 或 `AXIS_MEMBER`，使用 `pivot.metrics.baselineRatio`。

限制：`CALCULATE` 只支持 `CALCULATE(SUM(metric), REMOVE(groupByDim...))`；`REMOVE` 只能移除当前 `groupBy` 中的维度；占比分母必须使用 `NULLIF(CALCULATE(...), 0)`。

### timeWindow (可选)
声明式时间窗口分析。遇到同比、环比、周同比、年初至今、月累计、滚动 7/30/90 天这类需求，优先使用 `timeWindow`，不要手写窗口 SQL。

`value` 可选；传入时必须是两个元素的数组 `[start, end]`，每个元素为合法日期或相对表达式。`rollingAggregator` 支持 `sum` / `avg` / `count` / `min` / `max`，不填默认 `sum`。

```json
{
  "timeWindow": {
    "field": "salesDate$id",
    "grain": "month",
    "comparison": "yoy",
    "targetMetrics": ["salesAmount"]
  }
}
```

派生列命名规则：
- 同环比：`{metric}__prior`、`{metric}__diff`、`{metric}__ratio`
- 累计：`{metric}__ytd`、`{metric}__mtd`
- 滚动：`{metric}__rolling_7d`、`{metric}__rolling_30d`、`{metric}__rolling_90d`

可在 `timeWindow` 结果列之上追加后置标量 `calculatedFields`：
```json
{
  "columns": ["salesDate$year", "salesDate$month", "salesAmount__ratio", "growthPercent"],
  "groupBy": ["salesDate$year", "salesDate$month"],
  "timeWindow": {
    "field": "salesDate$id",
    "grain": "month",
    "comparison": "yoy",
    "targetMetrics": ["salesAmount"]
  },
  "calculatedFields": [
    {"name": "growthPercent", "expression": "salesAmount__ratio * 100"}
  ]
}
```

限制：`targetMetrics` 不可引用 calculatedFields；后置 calculatedFields 不能设置 `agg` 或窗口字段。

### 日期分桶与 SQL 函数边界
普通“按月/按周/按年分组”不要在 `columns`、`groupBy`、`orderBy` 中生成 `DATE_TRUNC(...)`、`YEAR(...)`、`MONTH(...)` 等 SQL 函数字段，也不要把 `DATE_TRUNC` 当字段名。先调用 `dataset.describe_model_internal`，只使用返回的日期粒度字段（如 `salesDate$year`、`salesDate$month`、`salesDate$week`）进行展示、分组和排序。

如果模型没有暴露所需日期粒度字段，不要自造 SQL 函数；改用已有日期字段过滤、`timeWindow`，或说明当前模型未提供该粒度。同比、环比、YTD、MTD、rolling 继续使用 `timeWindow`。


### slice (可选)
数组形式的过滤条件。

**标准格式**：
```json
[
  {"field": "status", "op": "=", "value": "done"},
  {"field": "amount", "op": ">", "value": 100}
]
```

**等值简写格式**（仅限等值判断）：
```json
[{"status": "done"}]
```
等价于 `{"field": "status", "op": "=", "value": "done"}`。
> **WARNING**: 在使用 `$or` 嵌套逻辑时，强烈建议**全部使用标准格式**，以免结构混淆导致语法错误。

**逻辑组合**：
```json
[
  {"field": "orderStatus", "op": "=", "value": "COMPLETED"},
  {
    "$or": [
      {"field": "totalAmount", "op": ">=", "value": 1000},
      {"field": "customer$customerType", "op": "=", "value": "VIP"}
    ]
  }
]
```

**通用操作符**：
| 类型 | 操作符 |
|---|---|
| 等值 | `=`, `!=`, `<>` |
| 比较 | `>`, `>=`, `<`, `<=` |
| 模糊 | `like`, `left_like`, `right_like` |
| 集合 | `in`, `not in` |
| 空值 | `is null`, `is not null` (无需value) |
| 区间 | `[]`, `[)`, `()`, `(]` (value为[start,end]) |

**字段间比较**：
- `$field` 引用：`{"field": "a", "op": ">", "value": {"$field": "b"}}` → `WHERE a > b`
- `$expr` 表达式：`{"$expr": "salesAmount > costAmount * 1.2"}` → 支持算术运算


**层级操作符**（仅限父子维度的 `$id` 字段）：
| 类型 | 操作符 | 说明 |
|---|---|---|
| 后代 | `childrenOf` | 直接子节点 |
| 后代 | `descendantsOf` | 所有后代（不含自身） |
| 后代 | `selfAndDescendantsOf` | 自身 + 所有后代 |
| 祖先 | `ancestorsOf` | 所有祖先（不含自身） |
| 祖先 | `selfAndAncestorsOf` | 自身 + 所有祖先 |

可选 `maxDepth` 限制深度：
```json
{"field": "team$id", "op": "descendantsOf", "value": "T001", "maxDepth": 2}
```


### orderBy (可选)
排序规则。简写格式：`"field"`(升序)、`"field desc"`(降序)、`"-field"`(降序)。**必须使用 columns 中定义的别名**，如 `year` 而非 `YEAR(createdAt)`。

开启 `pivot` 时，顶层 `orderBy` 不是透视轴排序或 TopN 控制；不要生成 `payload.pivot` + 顶层 `orderBy` 的组合。需要轴内排序时，使用 `pivot.rows[*].orderBy` 或 `pivot.columns[*].orderBy`。
```json
["-totalSales", "orderId"]
```

### 其他控制参数
| 参数 | 类型 | 默认值 | 互斥/依赖关系 |
|---|---|---|---|
| `limit` | number | 无 | 普通查询分页大小；`pivot` 轴裁剪请使用 `pivot.rows[*].limit` / `pivot.columns[*].limit` |
| `start` | number | `0` | 偏移量 |
| `returnTotal` | boolean | `true` | 是否返回总行数 |
| `distinct` | boolean | `false` | 与 `groupBy` 和聚合函数互斥 |
| `withSubtotals` | boolean | `false` | 仅在有 `groupBy` 时生效（Rollup计算） |

## 结果使用纪律

- 查询返回的 `items`、`schema`、`pagination` 已足够回答题面时，直接最终回答。
- 不要为了确认空结果、0 值或已满足条件的主查询而重复查询全表、所有月份或所有状态；除非用户明确要求审计、排查数据质量或解释异常。
- 对口径明确的问题，按题目限定的期间、状态、对象回答，不主动扩展到其他期间或其他状态。

## Pivot 透视表查询

当用户需要行列交叉表、小计/总计、树形 rows 轴、父级占比或列基准比值时使用 `pivot`。

```json
{
  "pivot": {
    "rows": ["region$caption", "city$caption"],
    "columns": ["salesDate$month"],
    "metrics": [
      "salesAmount",
      {"name": "share", "type": "parentShare", "of": "salesAmount"},
      {"name": "index", "type": "baselineRatio", "of": "salesAmount", "axis": "columns", "baseline": "first"}
    ],
    "outputFormat": "grid",
    "options": {"rowSubtotals": true, "grandTotal": true}
  }
}
```

边界：
- `pivot` 与 `columns` 互斥；开启 `pivot` 时不要传 `columns`。
- `pivot` 与 `timeWindow` 互斥；同比/环比需求改用 `timeWindow`，或拆成两个查询。
- 顶层 `orderBy` / `limit` 不作为透视轴排序或 TopN 控制；需要排序或裁剪行/列成员时，写在对应 `pivot.rows[*]` / `pivot.columns[*]` 轴对象上。
- `hierarchyMode=tree` 仅支持 rows 轴和 `outputFormat=tree`，不能与 `crossjoin`、`rowSubtotals`、`columnSubtotals`、`grandTotal` 同用。
- `parentShare` 只支持 rows 相邻层级和可加原生度量，不支持 tree/cascade/having/orderBy/limit。
- `baselineRatio` 只支持 columns 轴 `baseline=first/last`，不支持 tree/cascade/having/orderBy/limit。
- 不要生成 `ROLLUP_TO`、`CELL_AT`、`AXIS_MEMBER`、`AXIS_REF` 或 `expr` 类型 pivot metric；超出边界时退回普通 pivot、普通聚合或明确说明不支持。

## 错误处理指南
如果在调用 `query_model` 时遇到报错，请按以下思路进行修复：
1. **字段不存在**：检查字段名是否写错。外键必须使用 `xxx$id` 或 `xxx$caption` 访问，不要直接用关联模型的自身名称。如果不确定，先调用 `dataset.describe_model_internal`。
2. **函数未定义**：如果是 `count_if` / `sum_if` 报错，请改为 `sum/avg/count(if(...))`。
3. **不支持在 columns 中使用复杂表达式**：将该带有计算逻辑的表达式（比如加减乘除、窗口函数等）移到 `calculatedFields` 中定义别名，再放入 `columns`。
4. **GROUP BY 错误**：移除未分组明细列，或把该普通字段加入 `groupBy`；预定义聚合 measure 与维度一起使用时，只保留分组维度和 measure。
5. **语法错误**：检查 JSON 结构是否闭合，特别是 `slice` 中的 `$or` 是否正确嵌套。
6. **Pivot 互斥或边界错误**：移除 `columns` 或 `timeWindow`；tree 错误时移除 `crossjoin`/`rowSubtotals`/`columnSubtotals`/`grandTotal`；派生指标错误时移除 `parentShare`/`baselineRatio` 或改为普通 pivot。
