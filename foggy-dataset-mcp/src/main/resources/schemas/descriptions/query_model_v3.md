# dataset.query_model

执行数据模型查询，支持过滤、排序、分组聚合、计算字段，以及向量相似度检索。

## 字段规则

**直接使用 `description_model` 返回的字段名**

| 字段类型 | 用法 |
|---------|------|
| 维度 | `xxx$id`(查询/过滤), `xxx$caption`(展示) |
| 父子维度 | `xxx$hierarchy$id`(含子节点汇总) |
| 属性/度量 | 直接使用字段名 |
| 向量字段 | 配合 `similar` 操作符使用 |

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
| 类型 | 操作符 |
|------|--------|
| 等值 | `=`, `!=`, `<>` |
| 比较 | `>`, `>=`, `<`, `<=` |
| 模糊 | `like`, `left_like`, `right_like` |
| 集合 | `in`, `not in` |
| 空值 | `is null`, `is not null` (无需value) |
| 区间 | `[]`, `[)`, `()`, `(]` (value为[start,end]) |
| 层级 | `childrenOf`, `descendantsOf`, `selfAndDescendantsOf` |
| 向量 | `similar` (向量相似度检索) |

### 向量相似度检索 (similar)

用于向量模型的语义相似度搜索。当模型包含向量字段（类型为 `VECTOR`）时，可使用 `similar` 操作符进行相似度检索。

**参数说明**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `text` | string | 是* | 搜索文本（自动转换为向量）|
| `vector` | float[] | 是* | 直接传入向量（与text二选一）|
| `topK` | int | 否 | 返回最相似的N条记录，默认10 |
| `minScore` | float | 否 | 最低相似度阈值（0-1），过滤低于此分数的结果 |

**使用示例**：
```json
{
  "field": "embedding",
  "op": "similar",
  "value": {
    "text": "销售额趋势分析",
    "topK": 10,
    "minScore": 0.7
  }
}
```

**场景映射**：
| 用户需求 | 生成的参数 |
|---------|-----------|
| "找10条最相关的" | `{ "text": "...", "topK": 10 }` |
| "找所有相关度大于0.8的" | `{ "text": "...", "minScore": 0.8, "topK": 100 }` |
| "找5条最相关的，但要足够相关" | `{ "text": "...", "topK": 5, "minScore": 0.7 }` |

**返回结果**：
向量搜索结果会包含 `_score` 字段，表示相似度得分（0-1，越高越相似）。

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
      {
        "field": "embedding",
        "op": "similar",
        "value": {
          "text": "如何提升销售业绩",
          "topK": 10,
          "minScore": 0.6
        }
      },
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
- 向量检索时，`similar` 条件可以与普通过滤条件组合使用（先向量召回，再过滤）
- 向量检索结果按相似度降序排列，`_score` 字段表示相似度
