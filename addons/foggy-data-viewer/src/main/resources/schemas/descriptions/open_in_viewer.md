# dataset.open_in_viewer

生成可分享的浏览器链接，用于交互式浏览大数据集。

## 使用场景

| 适用 | 不适用（用query_model） |
|------|------------------------|
| 明细查询，预期500+行 | 带groupBy的聚合查询 |
| "全部"、"列表"、"导出"类查询 | 限制≤100行的查询 |
| 需要交互式探索 | AI需直接分析数据 |

## 参数

payload格式与`dataset.query_model`完全一致，支持columns、slice、orderBy、groupBy、calculatedFields。

**重要：slice必须提供至少一个过滤条件**

```json
{
  "model": "FactOrderQueryModel",
  "payload": {
    "columns": ["orderNo", "customer$caption", "orderDate$caption", "totalAmount"],
    "slice": [{"field": "orderDate$id", "op": "[)", "value": ["20250101", "20250131"]}],
    "orderBy": [{"field": "orderDate$id", "dir": "DESC"}]
  },
  "title": "2025年1月订单"
}
```

## 返回值

```json
{"viewerUrl": "http://.../view/abc123", "queryId": "abc123", "expiresAt": "2025-01-06T10:00:00", "estimatedRowCount": 1500}
```
