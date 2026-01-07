# dataset.query_model

执行数据模型查询，支持过滤、排序、分组聚合、计算字段。

## 字段规则

**直接使用 `description_model` 返回的字段名**

| 字段类型 | 用法 |
|---------|------|
| 维度 | `xxx$id`(查询/过滤), `xxx$caption`(展示) |
| 父子维度 | `xxx$hierarchy$id`(含子节点汇总) |
| 属性/度量 | 直接使用字段名 |

## 参数

### columns (必填)
支持内联聚合表达式，系统自动处理groupBy：
```json
["product$categoryName", "sum(salesAmount) as totalSales", "count(orderId) as orderCount"]
```
聚合函数：`sum`、`avg`、`count`、`max`、`min`

### calculatedFields (可选)
需要指定agg或复杂表达式时使用：
```json
[{"name": "netAmount", "expression": "salesAmount - discountAmount", "agg": "SUM"}]
```

### slice (可选)
过滤条件：
```json
[
  {"field": "customer$caption", "op": "like", "value": "张三"},
  {"field": "salesDate$id", "op": "[)", "value": ["20250101", "20251231"]},
  {"field": "customerLevel", "op": "is not null"}
]
```

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

### orderBy (可选)
```json
[{"field": "totalSales", "dir": "DESC"}]
```
注意：聚合时orderBy字段必须在columns中

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
    "orderBy": [{"field": "totalSales", "dir": "DESC"}],
    "limit": 50
  }
}
```

## 最佳实践
- 展示用`$caption`，查询用`$id`
- 简单聚合用内联表达式，复杂计算用calculatedFields
- 聚合时orderBy字段需在columns中
