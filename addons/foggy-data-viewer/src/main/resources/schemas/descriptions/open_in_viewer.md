# dataset.open_in_viewer

生成可分享的浏览器链接，用于交互式浏览大数据集。

## 使用场景

**适用：**
- 明细数据查询，预期返回大量行（500+）
- 用户要求"全部"、"列表"、"导出"类查询
- 需要交互式探索（过滤、排序、分页）

**不适用（改用 dataset.query_model）：**
- 带 groupBy 的聚合查询（结果集小）
- 明确限制 ≤100 行的查询
- AI 需要直接分析数据

## 参数格式

```
{
  "model": "模型名称",
  "payload": { /* 与 dataset.query_model 完全相同 */ },
  "title": "视图标题（可选）"
}
```

**payload 内容与 `dataset.query_model` 完全一致**，支持 columns、slice、orderBy、groupBy、calculatedFields。

**重要：payload.slice 必须提供至少一个过滤条件**，防止无界查询。

## 示例

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
{
  "viewerUrl": "http://example.com/data-viewer/view/abc123",
  "queryId": "abc123",
  "expiresAt": "2025-01-06T10:00:00",
  "estimatedRowCount": 1500
}
```
