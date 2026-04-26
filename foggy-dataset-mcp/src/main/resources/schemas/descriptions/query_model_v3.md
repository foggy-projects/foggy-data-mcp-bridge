# dataset.query_model

执行数据模型查询，支持过滤、排序、分组聚合、计算字段，以及向量相似度检索。

## 字段规则

**直接使用 `description_model` 返回的字段名**

| 字段类型 | 用法 |
|---------|------|
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
支持内联聚合表达式，系统自动处理groupBy：
```json
["product$categoryName", "sum(salesAmount) as totalSales", "count(orderId) as orderCount"]
```
聚合函数：`sum`、`avg`、`count`、`max`、`min`、`group_concat`、`countd`(去重计数)、`stddev_pop`、`stddev_samp`、`var_pop`、`var_samp`

**重要**：
- 当使用聚合表达式后，系统自动推断 groupBy，通常无需手动指定
- columns 仅支持简单的 `agg(field) as alias`，复杂计算用 calculatedFields
- 不要生成 SQL 风格 `case when ... then ... else ... end`
- 不要生成 `count_if`、`sum_if`、`avg_if` 这类未定义函数
- 条件聚合统一改写为 `sum/avg/count(if(...))`

### calculatedFields (可选)
需要指定agg或复杂表达式时使用：
```json
[{"name": "netAmount", "expression": "salesAmount - discountAmount", "agg": "SUM"}]
```

**窗口函数**：通过 `partitionBy`、`windowOrderBy`、`windowFrame` 实现排名、移动平均等分析：
```json
[
  {"name": "salesRank", "expression": "RANK()", "partitionBy": ["product$categoryName"], "windowOrderBy": [{"field": "salesAmount", "dir": "desc"}]},
  {"name": "ma7", "expression": "AVG(salesAmount)", "partitionBy": ["product$caption"], "windowOrderBy": [{"field": "salesDate$caption", "dir": "asc"}], "windowFrame": "ROWS BETWEEN 6 PRECEDING AND CURRENT ROW"}
]
```
窗口函数：`ROW_NUMBER`、`RANK`、`DENSE_RANK`、`NTILE`、`LAG`、`LEAD`、`FIRST_VALUE`、`LAST_VALUE`

**支持的函数**（函数名不区分大小写，使用函数调用语法如 `YEAR(date)` 而非 SQL 语法 `EXTRACT(YEAR FROM date)`）：
| 类型 | 函数 |
|------|------|
| 日期 | `YEAR(date)`, `MONTH(date)`, `DAY(date)`, `DATE_FORMAT`, `STR_TO_DATE`, `DATE_ADD`, `DATE_SUB`, `DATEDIFF`, `TIMESTAMPDIFF` |
| 字符串 | `CONCAT`, `CONCAT_WS`, `SUBSTRING`, `LEFT`, `RIGHT`, `LPAD`, `RPAD`, `REPLACE`, `LOCATE` |
| 空值 | `COALESCE`, `IFNULL`, `NVL`, `NULLIF` |
| 条件 | `IF`, `CASE` |
| 类型 | `CAST`, `CONVERT` |
| 统计 | `STDDEV_POP`, `STDDEV_SAMP`, `VAR_POP`, `VAR_SAMP` (SQLite 不支持) |

*常用数学函数如 ABS、ROUND、FLOOR、CEIL 等均支持*

### timeWindow (可选)
声明式时间窗口分析。遇到同比、环比、周同比、年初至今、月累计、滚动 7/30/90 天这类需求，优先使用 `timeWindow`，不要手写窗口 SQL。

```json
{
  "columns": ["salesDate$id", "salesAmount", "salesAmount__rolling_7d"],
  "groupBy": ["salesDate$id"],
  "timeWindow": {
    "field": "salesDate$id",
    "grain": "day",
    "comparison": "rolling_7d",
    "targetMetrics": ["salesAmount"]
  }
}
```

字段说明：
- `field`：业务时间字段，优先选择 `timeRole=business_date` 的维度 id 字段，如 `salesDate$id`
- `grain`：`day` / `week` / `month` / `quarter` / `year`
- `comparison`：`yoy` / `mom` / `wow` / `ytd` / `mtd` / `rolling_7d` / `rolling_30d` / `rolling_90d`
- `targetMetrics`：需要派生时间窗口列的度量字段，如 `["salesAmount"]`
- `rollingAggregator`：rolling 场景可选，`sum` / `avg` / `count`，默认 `sum`

派生列命名：
- 同环比：`{metric}__prior`、`{metric}__diff`、`{metric}__ratio`
- 累计：`{metric}__ytd`、`{metric}__mtd`
- 滚动：`{metric}__rolling_7d`、`{metric}__rolling_30d`、`{metric}__rolling_90d`

**条件聚合推荐写法**：
- 条件计数：`sum(if(stage$caption == 'Won', 1, 0)) as wonCount`
- 条件求和：`sum(if(state == 'sale', amountTotal, 0)) as confirmedAmount`
- 条件均值：`avg(if(stage$caption == 'Won', amountTotal, null)) as avgWonAmount`
- 条件计数（只统计命中行）：`count(if(stage$caption == 'Won', 1, null)) as wonOrderCount`

**条件表达式规则**：
- 相等判断用 `==`，不要写 SQL 风格 `=`
- 多个条件用 `&&` / `||`
- `avg(if(...))` 的 else 分支通常应为 `null`
- `count(if(...))` 的 else 分支通常应为 `null`
- 对外写法使用 `if(...)`，内部会归一化并降级到 SQL `CASE WHEN`

### slice (可选)
过滤条件（数组内条件默认 AND 连接）：
```json
[
  {"field": "customer$caption", "op": "like", "value": "张三"},
  {"field": "salesDate$id", "op": "[)", "value": ["20250101", "20251231"]},
  {"field": "customerLevel", "op": "is not null"}
]
```

**等值条件简写**：`{ "fieldName": value }` 等价于 `{ "field": "fieldName", "op": "=", "value": value }`
```json
[
  {"orderStatus": "COMPLETED"},
  {"customer$customerType": "VIP"}
]
```

**逻辑组合**：使用 `$or` / `$and` 显式组合条件：
```json
[
  {"orderStatus": "COMPLETED"},
  {
    "$or": [
      {"field": "totalAmount", "op": ">=", "value": 1000},
      {"customer$customerType": "VIP"}
    ]
  }
]
```
生成 SQL：`WHERE order_status = 'COMPLETED' AND (total_amount >= 1000 OR customer_type = 'VIP')`

**操作符**：
| 类型 | 操作符 |
|------|--------|
| 等值 | `=`, `!=`, `<>` |
| 比较 | `>`, `>=`, `<`, `<=` |
| 模糊 | `like`, `left_like`, `right_like` |
| 集合 | `in`, `not in` |
| 空值 | `is null`, `is not null` (无需value) |
| 区间 | `[]`, `[)`, `()`, `(]` (value为[start,end]) |
| 层级(后代) | `childrenOf`(直接子节点), `descendantsOf`(所有后代,不含自身), `selfAndDescendantsOf`(自身+所有后代) |
| 层级(祖先) | `selfAndAncestorsOf`(自身+所有祖先), `ancestorsOf`(所有祖先,不含自身) |
| 向量 | `similar`, `hybrid` (向量检索) |

层级操作符用于父子维度，作用于 `xxx$id` 字段，可选 `maxDepth` 限制深度：
```json
{"field": "team$id", "op": "selfAndDescendantsOf", "value": "T001"}
{"field": "company$id", "op": "selfAndAncestorsOf", "value": 3}
{"field": "team$id", "op": "descendantsOf", "value": "T001", "maxDepth": 2}
```

**字段间比较**：
- `$field` 引用：`{"field": "a", "op": ">", "value": {"$field": "b"}}` → `WHERE a > b`
- `$expr` 表达式：`{"$expr": "salesAmount > costAmount * 1.2"}` → 支持算术运算

### 向量检索

**仅向量字段（type=VECTOR）支持以下操作符**，普通字段不可使用。

#### similar - 相似度搜索
```json
{
  "field": "embedding",
  "op": "similar",
  "value": {
    "text": "销售额分析",
    "topK": 10,
    "minScore": 0.6,
    "groupBy": "category",
    "radius": 0.3
  }
}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `text` | string | 搜索文本（自动转向量）|
| `vector` | float[] | 直接传向量（与text二选一）|
| `topK` | int | 返回条数，默认10 |
| `minScore` | float | 最低相似度(0-1) |
| `groupBy` | string | 按字段分组去重 |
| `radius` | float | 范围搜索最低分数 |

#### hybrid - 混合搜索
向量相似度 + 关键词过滤的组合搜索：
```json
{
  "field": "embedding",
  "op": "hybrid",
  "value": {
    "text": "销售分析",
    "keyword": "报告",
    "topK": 10,
    "vectorWeight": 0.7,
    "keywordWeight": 0.3
  }
}
```

返回结果包含 `_score` 字段表示相似度(0-1)。

### orderBy (可选)
排序规则，支持多种格式：
```json
[{"field": "totalSales", "dir": "desc"}]
```

**简写格式**：
| 格式 | 说明 |
|------|------|
| `"field"` | 默认升序 |
| `"field desc"` | 降序 |
| `"-field"` | 降序（负号前缀）|

```json
["-totalSales", "orderId"]
```

**使用 columns 中定义的别名**，如 `year` 而非 `YEAR(createdAt)`

### distinct (可选)
设为 `true` 返回去重结果（`SELECT DISTINCT`），适用于"列出所有…"类查询。
与聚合查询（groupBy）互斥，有 groupBy 时自动忽略 distinct。
```json
{"columns": ["customer$caption", "customer$customerType"], "distinct": true}
```

### withSubtotals (可选)
设为 `true` 对 groupBy 聚合结果追加分组小计行和总计行。通过 `_rowType` 字段标记行类型：
- `data` — 原始数据行
- `subtotal` — 分组小计行（按第一维度分组）
- `grandTotal` — 总计行

仅对包含 groupBy 的聚合查询生效。多维度时按第一维度做小计；单维度时仅追加总计行。
```json
{
  "columns": ["department$caption", "month$caption", "sum(amount) as total"],
  "withSubtotals": true
}
```

### 分页
- `start`: 起始行(从0开始)
- `limit`: 每页记录数
- `returnTotal`: false可提升性能

## 示例

**聚合查询**：
```json
{
  "model": "TmsOrderModel",
  "payload": {
    "columns": ["salesDate$caption", "sum(totalAmount) as totalSales"],
    "orderBy": ["-totalSales"],
    "limit": 50
  }
}
```

**向量相似度检索**：
```json
{
  "model": "DocumentSearchModel",
  "payload": {
    "columns": ["docId", "title", "content", "_score"],
    "slice": [
      {"field": "embedding", "op": "similar", "value": {"text": "销售业绩", "topK": 10}},
      {"category": "report"}
    ],
    "limit": 10
  }
}
```

**按年月统计**：
```json
{
  "model": "ProductModel",
  "payload": {
    "columns": ["YEAR(createdAt) as year", "MONTH(createdAt) as month", "count(productKey) as cnt"],
    "orderBy": ["year", "month"],
    "limit": 100
  }
}
```

**去重查询（DISTINCT）**：
```json
{
  "model": "TmsCustomerModel",
  "payload": {
    "columns": ["customer$caption", "customer$customerType"],
    "distinct": true,
    "limit": 100
  }
}
```

**分组小计与总计**：
```json
{
  "model": "TmsOrderModel",
  "payload": {
    "columns": ["department$caption", "MONTH(salesDate) as month", "sum(totalAmount) as total"],
    "withSubtotals": true,
    "limit": 200
  }
}
```

## 最佳实践
- 展示用`$caption`，查询用`$id`
- 简单聚合用内联表达式，复杂计算用calculatedFields
- orderBy/groupBy 使用 columns 中定义的别名（如 `year`）而非表达式
- 向量检索：`similar`可与普通过滤组合，`hybrid`用于语义+关键词混合搜索
- `distinct` 与 `groupBy` 互斥：去重用 distinct，聚合用 groupBy
- `withSubtotals` 追加的行通过 `_rowType` 字段区分，前端可据此渲染样式
