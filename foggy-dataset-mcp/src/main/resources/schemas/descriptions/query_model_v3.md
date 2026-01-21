# dataset.query_model

执行数据模型查询，支持过滤、排序、分组聚合、计算字段，以及向量相似度检索。

## 字段规则

**直接使用 `description_model` 返回的字段名**

| 字段类型 | 用法 |
|---------|------|
| 维度 | `xxx$id`(查询/过滤), `xxx$caption`(展示) |
| 父子维度 | `xxx$hierarchy$id`(含子节点汇总) |
| 属性/度量 | 直接使用字段名 |
| 向量字段 | 仅支持 `similar`/`hybrid` 操作符 |

### 父子维度 (Parent-Child Dimension)
层级结构维度（如组织架构）额外支持 `$hierarchy$` 视角：
- **xxx$id**: 精确匹配该节点
- **xxx$hierarchy$id**: 匹配该节点及所有后代（用于层级汇总）
示例：`team$hierarchy$id = 'T001'` 查询总公司及所有子部门

## 参数

### columns (必填)
支持内联聚合表达式，系统自动处理groupBy：
```json
["product$categoryName", "sum(salesAmount) as totalSales", "count(orderId) as orderCount"]
```
聚合函数：`sum`、`avg`、`count`、`max`、`min`、`group_concat`

**重要**：
- 当使用聚合表达式后，系统自动推断 groupBy，通常无需手动指定
- columns 仅支持简单的 `agg(field) as alias`，复杂计算用 calculatedFields

### calculatedFields (可选)
需要指定agg或复杂表达式时使用：
```json
[{"name": "netAmount", "expression": "salesAmount - discountAmount", "agg": "SUM"}]
```

**支持的函数**（函数名不区分大小写，使用函数调用语法如 `YEAR(date)` 而非 SQL 语法 `EXTRACT(YEAR FROM date)`）：
| 类型 | 函数 |
|------|------|
| 日期 | `YEAR(date)`, `MONTH(date)`, `DAY(date)`, `DATE_FORMAT`, `STR_TO_DATE`, `DATE_ADD`, `DATE_SUB`, `DATEDIFF`, `TIMESTAMPDIFF` |
| 字符串 | `CONCAT`, `CONCAT_WS`, `SUBSTRING`, `LEFT`, `RIGHT`, `LPAD`, `RPAD`, `REPLACE`, `LOCATE` |
| 空值 | `COALESCE`, `IFNULL`, `NVL`, `NULLIF` |
| 条件 | `IF`, `CASE` |
| 类型 | `CAST`, `CONVERT` |

*常用数学函数如 ABS、ROUND、FLOOR、CEIL 等均支持*

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
| 层级 | `childrenOf`, `descendantsOf`, `selfAndDescendantsOf` |
| 向量 | `similar`, `hybrid` (向量检索) |

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

## 最佳实践
- 展示用`$caption`，查询用`$id`
- 简单聚合用内联表达式，复杂计算用calculatedFields
- orderBy/groupBy 使用 columns 中定义的别名（如 `year`）而非表达式
- 向量检索：`similar`可与普通过滤组合，`hybrid`用于语义+关键词混合搜索
