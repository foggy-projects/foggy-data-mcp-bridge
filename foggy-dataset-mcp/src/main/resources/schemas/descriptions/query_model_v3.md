# dataset.query_model

执行数据模型查询，支持过滤、排序、分组聚合、计算字段，以及向量相似度检索。

> **Note**: 本工具适用于单模型查询。如果遇到单模型 DSL 无法解决的复杂查询（如跨模型 Join、Union、派生查询、或者需要返回多个 Plan 的场景），请使用 `dataset.compose_script` 工具。

## 字段规则

**直接使用 `dataset.describe_model_internal` 返回的字段名**

| 字段类型 | 用法 |
|---|---|
| 维度 | `xxx$id`(查询/过滤), `xxx$caption`(展示) |
| 父子维度 | `xxx$hierarchy$id`(层级范围过滤), `xxx$hierarchy$caption`(层级汇总展示) |
| 属性/度量 | 直接使用字段名 |
| 向量字段 | 仅支持 `similar`/`hybrid` 操作符 |

### 父子维度 (Parent-Child Dimension)
层级结构维度（如组织架构、公司层级）支持两种访问视角：
- **xxx$id / xxx$caption**: 精确匹配该节点（与普通维度相同）
- **xxx$hierarchy$id / xxx$hierarchy$caption**: 通过闭包表匹配节点及所有后代（层级汇总）

还可在 slice 中对 `xxx$id` 使用层级操作符进行细粒度查询（见操作符表）。

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

当用户询问“各分类占总额占比”“各产品在客户类型内占比”这类跨当前分组的分母问题，在 `calculatedFields.expression` 中使用：
```text
SUM(metric) / NULLIF(CALCULATE(SUM(metric), REMOVE(groupByDim)), 0)
```

示例：全国/全局占比，移除当前唯一分组维度：
```json
{
  "columns": ["customer$customerType", "salesAmount", "totalShare"],
  "groupBy": ["customer$customerType"],
  "calculatedFields": [
    {
      "name": "totalShare",
      "expression": "SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(customer$customerType)), 0)"
    }
  ]
}
```

示例：父级/组内占比，保留未移除的 groupBy 作为分区：
```json
{
  "columns": ["customer$customerType", "product$categoryName", "salesAmount", "typeShare"],
  "groupBy": ["customer$customerType", "product$categoryName"],
  "calculatedFields": [
    {
      "name": "typeShare",
      "expression": "ROUND(SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(product$categoryName)), 0), 4)"
    }
  ]
}
```

限制：`CALCULATE` 只支持 `CALCULATE(SUM(metric), REMOVE(groupByDim...))`；`REMOVE` 只能移除当前 `groupBy` 中的维度；占比分母必须使用 `NULLIF(CALCULATE(...), 0)`；不要用 `CALCULATE` 做同比、环比、累计或滚动窗口，这些需求继续使用 `timeWindow`。

### timeWindow (可选)
声明式时间窗口分析。遇到同比、环比、周同比、年初至今、月累计、滚动 7/30/90 天这类需求，优先使用 `timeWindow`，不要手写窗口 SQL。

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

## Pivot 透视表查询 (Pivot)

当用户需要**交叉表、多层分组小计、树形层级展示**时，请使用 `pivot` 替代常规的 `columns` + `groupBy`。
> **注意边界**：何时使用 `pivot`？
> - 需要将某个维度的值展开为列（例如：按年份作为行，各产品分类作为列，展示销售额）。
> - 需要行级或列级**小计 (Subtotals)**。
> - 需要以**树形结构 (Tree)** 展现组织架构或父子层级。
> - 如果只是普通的列表查询或简单的分组聚合（无行列交叉、无小计），请继续使用常规 `query_model`；如果涉及跨表 Join，请退回 `compose_script`。

### Pivot 请求结构

```json
{
  "pivot": {
    "rows": [{"field": "region$caption"}],
    "columns": [{"field": "salesDate$year"}],
    "metrics": ["salesAmount", "profitRate"],
    "outputFormat": "grid",
    "options": {
      "rowSubtotals": true,
      "columnSubtotals": true,
      "grandTotal": true
    }
  }
}
```

- **rows / columns**: 行/列轴定义，可以仅传字段名。
  - 支持将具有父子关系的维度设为树形结构：`{"field": "org$caption", "hierarchyMode": "tree"}`（注意：`tree` 模式下仅限 `rows` 轴，不支持 `options.rowSubtotals` 与 `crossjoin`）。
- **metrics**: 度量字段列表。支持字符串（原生度量名）和对象（派生指标）混合。对象形式可声明派生指标（如 `parentShare`），字符串直接引用原生度量。
- **outputFormat**: 输出格式。支持 `flat`（平铺，默认）、`grid`（网格交叉表）、`tree`（树形层级嵌套）。
- **options**: 补充选项，支持小计、总计和 `crossjoin` 稀疏补全。

### 轴内分组截断 (Generate / Per-Group TopN)

在 `rows` 或 `columns` 中，支持对特定层级进行分组内 TopN 截断。**截断的分区键隐式为该字段前面的所有轴字段。**
```json
{
  "pivot": {
    "rows": [
      "product$categoryName",
      {
        "field": "product$subCategoryName",
        "orderBy": ["-salesAmount"],
        "limit": 3
      }
    ],
    "metrics": ["salesAmount"],
    "outputFormat": "flat"
  }
}
```
**说明**：上述请求的含义是：对每个 `categoryName`，仅保留其内部 `salesAmount` 排名 Top 3 的 `subCategoryName`。

### 父级占比 (parentShare)

当用户询问“子品类占大类的占比”“各省占大区比率”这类**同一轴内父子层级占比**时，使用 `pivot.metrics` 中的 `parentShare` 结构化声明：

```json
{
  "pivot": {
    "rows": [
      {"field": "product$categoryName"},
      {"field": "product$subCategoryName"}
    ],
    "metrics": [
      "salesAmount",
      {"name": "categoryShare", "type": "parentShare", "of": "salesAmount"}
    ],
    "outputFormat": "flat"
  }
}
```
**说明**：`categoryShare` = 子品类 salesAmount / 所在大类 salesAmount。引擎自动推断最内层两个相邻层级为子/父。

可显式消歧：
```json
{"name": "share", "type": "parentShare", "of": "salesAmount", "axis": "rows", "level": "subCategory", "parentLevel": "category"}
```

限制：
- parentShare 不支持与 `hierarchyMode=tree` 同时使用。
- `of` 必须引用同一 metrics 中的原生度量，且该度量必须为可加聚合（SUM/COUNT/MIN/MAX），不支持 AVG/COUNT_DISTINCT。
- `axis` 第一版仅支持 `rows`（默认），不支持 `columns`。
- 不支持 `expr` 类型的计算指标。如需行内计算，请使用 `calculatedFields`。

### 基准比较 (baselineRatio)

当用户询问“某个列指标相对于首列/末列的增长率或比较指数”这类**同一行跨列的基准比较**时，使用 `pivot.metrics` 中的 `baselineRatio` 结构化声明：

```json
{
  "pivot": {
    "rows": ["categoryName"],
    "columns": ["month"],
    "metrics": [
      "salesAmount",
      {"name": "salesIndex", "type": "baselineRatio", "of": "salesAmount", "axis": "columns", "baseline": "first"}
    ]
  }
}
```
**说明**：`salesIndex` = 当前单元格的 salesAmount / 该行 `month` 排序下第一列的 salesAmount。
- `baseline` 可选值为 `"first"` 或 `"last"`。
- 第一版 `baselineRatio` 的 `axis` 强制只能为 `"columns"`。且 `columns` 轴不能为空。
- `of` 必须引用可加的原生度量，不支持树形模式。

### 高级函数警告 (Fail-closed)
> **WARNING**:
> 以下计算函数在当前版本**未开放使用**，引擎将在运行时强制阻断：
> - `ROLLUP_TO`、`CELL_AT`、`AXIS_MEMBER`、`AXIS_REF`
> - 任意维度的 MDX `Generate`、跨轴集合生成
>
> 如果需要**全局占比**，使用受限 `CALCULATE(..., REMOVE(...))`。
> 如果需要**父级占比**（子品类占大类），使用 `pivot.metrics` 的 `parentShare`（见上方示例），不要尝试 `REMOVE(childDim)` 或 `ROLLUP_TO`。
> 如果需要**跨列首末基准比较**，使用 `pivot.metrics` 的 `baselineRatio`（见上方示例），不要尝试 `CELL_AT` 或坐标推导。
> 如果需要**同环比、累计**，使用 `timeWindow`，不要用 `CALCULATE` 模拟。

## 错误处理指南
如果在调用 `query_model` 时遇到报错，请按以下思路进行修复：
1. **字段不存在**：检查字段名是否写错。外键必须使用 `xxx$id` 或 `xxx$caption` 访问，不要直接用关联模型的自身名称。如果不确定，先调用 `dataset.describe_model_internal`。
2. **函数未定义**：如果是 `count_if` / `sum_if` 报错，请改为 `sum/avg/count(if(...))`。
3. **不支持在 columns 中使用复杂表达式**：将该带有计算逻辑的表达式（比如加减乘除、窗口函数等）移到 `calculatedFields` 中定义别名，再放入 `columns`。
4. **语法错误**：检查 JSON 结构是否闭合，特别是 `slice` 中的 `$or` 是否正确嵌套。
5. **Pivot 互斥错误**：如果使用 `pivot` 并报错 `tree hierarchy mode is not compatible`，请移除 `options.rowSubtotals`。
