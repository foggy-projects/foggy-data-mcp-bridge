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
| 类型 | 操作符 | 适用范围 |
|------|--------|----------|
| 等值 | `=`, `!=`, `<>` | 全部 |
| 比较 | `>`, `>=`, `<`, `<=` | 全部 |
| 模糊 | `like`, `left_like`, `right_like` | 全部 |
| 集合 | `in`, `not in` | 全部 |
| 空值 | `is null`, `is not null` (无需value) | 全部 |
| 区间 | `[]`, `[)`, `()`, `(]` (value为[start,end]) | 全部 |
| 层级 | `childrenOf`, `descendantsOf`, `selfAndDescendantsOf` | 全部 |
| 向量相似 | `similar` (语义相似度检索) | 仅向量模型 |

**向量查询说明**：
- `similar` 操作符仅适用于向量数据库模型（模型名通常包含 `Vector`）
- 用于语义相似度检索，value 为查询文本
- 示例：`{"field": "content", "op": "similar", "value": "销售数据分析"}`

### orderBy (可选)
```json
[{"field": "totalSales", "dir": "DESC"}]
```
注意：聚合或计算字段使用了聚合函数时orderBy字段必须在columns中

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

**向量相似度检索**：
```json
{
  "model": "DocumentSearchModel",
  "payload": {
    "columns": ["docId", "title", "content", "_score"],
    "slice": [
      {"field": "embedding", "op": "similar", "value": {"text": "销售业绩", "topK": 10}},
      {"field": "category", "op": "=", "value": "report"}
    ],
    "limit": 10
  }
}
```

## 最佳实践
- 展示用`$caption`，查询用`$id`
- 简单聚合用内联表达式，复杂计算用calculatedFields
- 聚合或计算字段使用了聚合函数时orderBy字段必须在columns中
- 向量模型使用`similar`操作符进行语义检索，关系数据库不支持此操作符
