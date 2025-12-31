# 查询工具

`dataset.query_model_v2` 是核心数据查询工具，支持复杂的过滤、排序、分组和聚合操作。

## 基本信息

- **工具名称**: `dataset.query_model_v2`
- **分类**: 数据查询
- **权限**: Admin, Analyst

## 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `model` | string | ✅ | 查询模型名称 |
| `payload` | object | ✅ | 查询参数对象 |
| `mode` | string | ❌ | 执行模式：`execute`（默认）或 `explain` |

### payload 参数详解

| 参数 | 类型 | 说明 |
|------|------|------|
| `columns` | array | 返回的列，支持内联聚合 |
| `slice` | array | 过滤条件 |
| `orderBy` | array | 排序规则 |
| `groupBy` | array | 分组字段（通常自动处理） |
| `calculatedFields` | array | 计算字段定义 |
| `start` | number | 起始行号（从 0 开始） |
| `limit` | number | 返回记录数 |
| `returnTotal` | boolean | 是否返回总数 |

## 字段使用规则

### 维度字段

维度字段返回两个变体：
- `xxx$id` - 用于精确查询和过滤
- `xxx$caption` - 用于展示名称

```json
{
  "columns": ["customer$caption", "salesDate$caption"],
  "slice": [
    {"field": "customer$id", "op": "=", "value": 1001}
  ]
}
```

### 父子维度

层级结构维度支持 `$hierarchy$` 视角：
- `xxx$id` - 精确匹配该节点
- `xxx$hierarchy$id` - 匹配该节点及所有后代

```json
{
  "slice": [
    {"field": "team$hierarchy$id", "op": "=", "value": "T001"}
  ]
}
```

### 属性和度量字段

直接使用返回的字段名：

```json
{
  "columns": ["orderNo", "totalAmount", "quantity"]
}
```

## 过滤条件 (slice)

### 支持的操作符

| 操作符 | 说明 | 示例 |
|--------|------|------|
| `=` | 等于 | `{"field": "status", "op": "=", "value": 1}` |
| `!=`, `<>` | 不等于 | `{"field": "status", "op": "!=", "value": 0}` |
| `>`, `>=`, `<`, `<=` | 比较 | `{"field": "amount", "op": ">", "value": 100}` |
| `like` | 模糊匹配 | `{"field": "name", "op": "like", "value": "张"}` |
| `left_like` | 前缀匹配 | `{"field": "code", "op": "left_like", "value": "ORD"}` |
| `in` | 包含 | `{"field": "type", "op": "in", "value": [1, 2, 3]}` |
| `not in` | 不包含 | `{"field": "type", "op": "not in", "value": [0]}` |
| `is null` | 为空 | `{"field": "remark", "op": "is null"}` |
| `is not null` | 不为空 | `{"field": "remark", "op": "is not null"}` |
| `[]`, `[)`, `()`, `(]` | 区间 | `{"field": "date", "op": "[)", "value": ["2025-01-01", "2025-12-31"]}` |

### 过滤示例

```json
{
  "slice": [
    {"field": "salesDate$id", "op": "[)", "value": ["20250101", "20251231"]},
    {"field": "customer$caption", "op": "like", "value": "张三"},
    {"field": "customerType", "op": "in", "value": [10, 20, 30]},
    {"field": "remark", "op": "is not null"}
  ]
}
```

## 聚合查询

### 内联聚合表达式（推荐）

在 `columns` 中直接写聚合，系统自动处理 `groupBy`：

```json
{
  "columns": [
    "product$categoryName",
    "sum(salesAmount) as totalSales",
    "count(orderId) as orderCount",
    "avg(unitPrice) as avgPrice"
  ]
}
```

支持的聚合函数：`sum`, `avg`, `count`, `max`, `min`

### 计算字段 (calculatedFields)

需要复杂表达式或指定 `agg` 时使用：

```json
{
  "calculatedFields": [
    {
      "name": "netAmount",
      "expression": "salesAmount - discountAmount",
      "agg": "SUM"
    },
    {
      "name": "taxAmount",
      "expression": "salesAmount * 0.13",
      "agg": "SUM"
    }
  ],
  "columns": ["customer$caption", "netAmount", "taxAmount"]
}
```

## 排序 (orderBy)

```json
{
  "orderBy": [
    {"field": "totalSales", "dir": "DESC"},
    {"field": "customer$caption", "dir": "ASC"}
  ]
}
```

> 注意：聚合查询时，`orderBy` 字段必须在 `columns` 中出现。

## 分页

```json
{
  "start": 0,
  "limit": 30,
  "returnTotal": true
}
```

## 完整示例

### 基础查询

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "columns": ["orderNo", "customer$caption", "salesDate$caption", "totalAmount"],
    "slice": [
      {"field": "salesDate$id", "op": "[)", "value": ["20250101", "20251231"]}
    ],
    "orderBy": [{"field": "salesDate$id", "dir": "DESC"}],
    "start": 0,
    "limit": 30
  }
}
```

### 聚合查询

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "columns": [
      "salesDate$caption",
      "sum(totalAmount) as totalSales",
      "count(orderId) as orderCount"
    ],
    "slice": [
      {"field": "salesDate$id", "op": "[)", "value": ["20250101", "20251231"]}
    ],
    "orderBy": [{"field": "totalSales", "dir": "DESC"}],
    "start": 0,
    "limit": 50
  }
}
```

### MCP 协议调用

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "dataset.query_model_v2",
    "arguments": {
      "model": "FactSalesQueryModel",
      "payload": {
        "columns": ["customer$caption", "sum(totalAmount) as total"],
        "limit": 10
      }
    }
  }
}
```

## 响应格式

```json
{
  "success": true,
  "data": {
    "items": [
      {"customer$caption": "张三", "total": 12500.00},
      {"customer$caption": "李四", "total": 8900.00}
    ],
    "total": 156,
    "start": 0,
    "limit": 10
  }
}
```

## 最佳实践

1. **展示用 `$caption`，过滤用 `$id`**
2. **简单聚合用内联表达式**：`sum(amount) as total`
3. **复杂计算用 `calculatedFields`**
4. **大数据量使用分页**：设置合理的 `limit`
5. **添加过滤条件**：避免全表扫描

## 下一步

- [元数据工具](./metadata.md) - 获取模型字段信息
- [自然语言查询](./nl-query.md) - 智能数据查询
- [工具概述](./overview.md) - 返回工具列表
