# dataset.query_model

执行数据模型查询，支持过滤、排序、分组聚合、计算字段。

> **Note**: 本工具适用于单模型查询。如果遇到单模型 DSL 无法解决的复杂查询（如跨模型 Join、Union、派生查询、或者需要返回多个 Plan 的场景），请使用 `dataset.compose_script` 工具。

## 字段规则

**直接使用 `dataset.describe_model_internal` 返回的字段名**

| 字段类型 | 用法 |
|---|---|
| 维度 | `xxx$id`(查询/过滤), `xxx$caption`(展示) |

| 属性/度量 | 直接使用字段名 |


## 参数

### columns (必填)
声明要查询的列，支持普通字段或简单的内联聚合表达式（系统自动处理 groupBy）：
```json
["product$categoryName", "sum(salesAmount) as totalSales", "count(orderId) as orderCount"]
```
支持的聚合函数：`sum`、`avg`、`count`、`max`、`min`、`group_concat`、`countd`(去重计数)、`stddev_pop`、`stddev_samp`、`var_pop`、`var_samp`。

> **WARNING**:
> - 当使用聚合表达式后，系统自动推断 groupBy，通常无需手动指定。
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

限制：`CALCULATE` 只支持 `CALCULATE(SUM(metric), REMOVE(groupByDim...))`；`REMOVE` 只能移除当前 `groupBy` 中的维度；占比分母必须使用 `NULLIF(CALCULATE(...), 0)`。

### timeWindow (可选)
声明式时间窗口分析。同比、环比、周同比、年初至今、月累计、滚动 7/30/90 天优先使用 `timeWindow`。

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

派生列：`{metric}__prior`、`{metric}__diff`、`{metric}__ratio`、`{metric}__ytd`、`{metric}__mtd`、`{metric}__rolling_7d`。

timeWindow 结果列可再接后置标量 `calculatedFields`：
```json
{
  "columns": ["salesDate$year", "salesDate$month", "salesAmount__ratio", "growthPercent"],
  "groupBy": ["salesDate$year", "salesDate$month"],
  "timeWindow": {"field": "salesDate$id", "grain": "month", "comparison": "yoy", "targetMetrics": ["salesAmount"]},
  "calculatedFields": [{"name": "growthPercent", "expression": "salesAmount__ratio * 100"}]
}
```

限制：`targetMetrics` 不可引用 calculatedFields；后置 calculatedFields 不能设置 `agg` 或窗口字段。


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



### orderBy (可选)
排序规则。简写格式：`"field"`(升序)、`"field desc"`(降序)、`"-field"`(降序)。**必须使用 columns 中定义的别名**，如 `year` 而非 `YEAR(createdAt)`。
```json
["-totalSales", "orderId"]
```

### 其他控制参数
| 参数 | 类型 | 默认值 | 互斥/依赖关系 |
|---|---|---|---|
| `limit` | number | 无 | 分页大小 |
| `start` | number | `0` | 偏移量 |
| `returnTotal` | boolean | `true` | 是否返回总行数 |
| `distinct` | boolean | `false` | 与 `groupBy` 和聚合函数互斥 |
| `withSubtotals` | boolean | `false` | 仅在有 `groupBy` 时生效（Rollup计算） |

## 错误处理指南
如果在调用 `query_model` 时遇到报错，请按以下思路进行修复：
1. **字段不存在**：检查字段名是否写错。外键必须使用 `xxx$id` 或 `xxx$caption` 访问，不要直接用关联模型的自身名称。如果不确定，先调用 `dataset.describe_model_internal`。
2. **函数未定义**：如果是 `count_if` / `sum_if` 报错，请改为 `sum/avg/count(if(...))`。
3. **不支持在 columns 中使用复杂表达式**：将该带有计算逻辑的表达式（比如加减乘除、窗口函数等）移到 `calculatedFields` 中定义别名，再放入 `columns`。
4. **语法错误**：检查 JSON 结构是否闭合，特别是 `slice` 中的 `$or` 是否正确嵌套。
