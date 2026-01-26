# 查询工具

`dataset.query_model` 是核心数据查询工具，支持复杂的过滤、排序、分组和聚合操作。

## 基本信息

- **工具名称**: `dataset.query_model`
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
    "name": "dataset.query_model",
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

### 标准响应

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
    "limit": 10,
    "pagination": {
      "start": 0,
      "limit": 10,
      "returned": 10,
      "totalCount": 156,
      "hasMore": true,
      "rangeDescription": "显示第 1-10 条，共 156 条"
    }
  }
}
```

### 大数据自动截断

当 MCP 查询返回的数据量过大时（单元格数超过阈值），系统会**自动截断结果**并提供完整数据的访问链接，避免大量数据占用 LLM 的上下文窗口。

#### 触发条件

- 查询来源：仅对 MCP 工具调用生效
- 数据量阈值：**单元格数（行数 × 列数）超过 10000**（可配置）
- 截断行数：保留前 **100 行**返回给 LLM（可配置）

#### 截断响应格式

```json
{
  "success": true,
  "data": {
    "items": [
      // ... 截断后的 100 行数据
    ],
    "total": 50000,
    "truncationInfo": {
      "truncated": true,
      "originalRowCount": 50000,
      "truncatedRowCount": 100,
      "columnCount": 15,
      "cellCount": 750000,
      "message": "数据量较大（50000 行 × 15 列 = 750000 单元格），已自动截断为 100 行。",
      "viewerUrl": "http://localhost:8080/data-viewer/view/abc123def456",
      "apiUrl": "http://localhost:8080/data-viewer/api/query/abc123def456/data",
      "hint": "您可以访问上述链接查看完整数据,或通过 API 分页获取（参数：start, limit）"
    }
  }
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `truncated` | boolean | 是否发生了截断 |
| `originalRowCount` | number | 原始数据行数 |
| `truncatedRowCount` | number | 截断后保留的行数 |
| `columnCount` | number | 列数 |
| `cellCount` | number | 总单元格数 |
| `message` | string | 截断说明信息 |
| `viewerUrl` | string | 完整数据的浏览器查看链接 |
| `apiUrl` | string | 完整数据的 API 查询链接 |
| `hint` | string | 使用提示 |

#### LLM 处理建议

当检测到 `truncationInfo.truncated = true` 时，LLM 应该：

1. **告知用户数据被截断**：解释原因（数据量过大）
2. **展示样本数据**：使用截断后的 100 行作为样本
3. **提供访问链接**：引导用户通过链接查看完整数据
4. **说明分页选项**：如需编程访问，可使用 API 分页获取

示例回复：

> 查询返回了 50,000 行数据（共 750,000 个单元格），数据量较大。我这里展示前 100 行样本数据供您参考：
>
> [展示表格...]
>
> 完整数据请访问：[数据浏览器](http://localhost:8080/data-viewer/view/abc123def456)
>
> 如需通过 API 分页获取，可使用：`/data-viewer/api/query/abc123def456/data?start=0&limit=100`

#### 配置阈值

在 `application.yml` 中调整截断参数：

```yaml
foggy:
  data-viewer:
    thresholds:
      # 单元格阈值（行 × 列）
      cell-threshold-for-truncation: 10000
      # 截断后保留的行数
      truncated-row-limit: 100
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
