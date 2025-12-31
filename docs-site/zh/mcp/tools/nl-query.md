# 自然语言查询

`dataset_nl.query` 是面向普通用户的智能查询工具，支持用自然语言描述数据需求。

## 基本信息

- **工具名称**: `dataset_nl.query`
- **分类**: 自然语言
- **权限**: Admin, Business

## 核心特性

- **自然语言理解**：用中文描述查询需求，无需了解技术细节
- **智能图表生成**：自动生成趋势图、对比图、占比图
- **多步骤分析**：自动分解复杂问题，智能整合结果
- **上下文记忆**：支持连续对话和追问

## 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `query` | string | ✅ | 自然语言查询内容 |
| `session_id` | string | ❌ | 会话 ID，用于上下文关联 |
| `cursor` | string | ❌ | 分页游标 |
| `format` | string | ❌ | 输出格式：`table`/`json`/`summary` |
| `hints` | object | ❌ | 查询优化提示 |
| `stream` | boolean | ❌ | 启用流式响应（默认 true） |

### hints 参数

```json
{
  "hints": {
    "prefer_model": "FactSalesQueryModel",
    "prefer_fields": ["customerName", "totalAmount"],
    "time_range": "last_7_days"
  }
}
```

## 查询类型

### 明细查询

```
查询客户信息
找出北京地区的企业客户
显示最近10条订单
```

### 统计查询

```
按团队统计订单数量
计算各地区销售总额
统计每个品类的商品数
```

### 趋势分析

```
近一周的运单趋势
最近30天的销售变化
本月每日订单量
```

→ 自动生成折线图 (LINE)

### 对比分析

```
对比各网点的业绩
不同客户类型的订单量
各部门销售额对比
```

→ 自动生成柱状图 (BAR)

### 占比分析

```
各地区销售占比
客户类型分布
支付方式占比
```

→ 自动生成饼图 (PIE)

## 使用示例

### 基础查询

```json
{
  "query": "查询最近一周的销售数据"
}
```

### 带会话的查询

```json
{
  "query": "再按客户类型细分",
  "session_id": "session_123"
}
```

### 分页查询

```json
{
  "query": "查询客户列表",
  "cursor": "eyJvZmZzZXQiOjEwMH0="
}
```

### MCP 协议调用

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "dataset_nl.query",
    "arguments": {
      "query": "按商品分类统计本月销售额",
      "format": "table"
    }
  }
}
```

## 响应格式

### 成功响应

```json
{
  "type": "result",
  "items": [
    {"category": "电子产品", "totalSales": 125000},
    {"category": "服装", "totalSales": 89000}
  ],
  "total": 5,
  "summary": "查询到5个品类的销售数据，电子产品销售额最高",
  "exports": {
    "charts": [
      {
        "url": "https://chart.example.com/sales.png",
        "type": "BAR",
        "title": "本月各品类销售额对比",
        "format": "PNG",
        "width": 800,
        "height": 600,
        "expiresAt": "2025-01-08T12:00:00Z"
      }
    ]
  },
  "hasNext": false,
  "nextCursor": null
}
```

### 响应字段

| 字段 | 说明 |
|------|------|
| `type` | 响应类型：result / error / info |
| `items` | 数据记录数组 |
| `total` | 总记录数 |
| `summary` | 结果摘要 |
| `exports.charts` | 生成的图表数组 |
| `hasNext` | 是否有下一页 |
| `nextCursor` | 下一页游标 |

## 智能图表

系统根据查询类型自动选择图表：

| 查询类型 | 图表类型 |
|----------|----------|
| 趋势分析 | 折线图 (LINE) |
| 分类统计 | 柱状图 (BAR) |
| 占比分析 | 饼图 (PIE) |
| 相关性分析 | 散点图 (SCATTER) |

图表标题由 AI 自动生成，具有业务含义：
- "近一周的运单趋势" → "近一周运单趋势图"
- "按客户类型统计" → "客户类型分布图"

## 多步骤分析

系统可以自动分解复杂问题：

**示例：** "找出本月发货量前10的客户，分析他们的全年发货量"

1. 查询本月发货量前10的客户
2. 获取这些客户的全年订单数据
3. 生成综合分析报告和对比图表

## 上下文记忆

使用 `session_id` 实现连续对话：

```json
// 第一次查询
{"query": "查询销售数据", "session_id": "abc123"}

// 追问（系统记住了上下文）
{"query": "按地区细分", "session_id": "abc123"}

// 继续追问
{"query": "只看北京的", "session_id": "abc123"}
```

## 错误处理

| 错误码 | 说明 | 解决方案 |
|--------|------|----------|
| QUERY_PARSE_ERROR | 无法理解查询 | 使用更明确的描述 |
| MODEL_NOT_FOUND | 无法识别数据模型 | 在 hints 中指定 prefer_model |
| FIELD_NOT_FOUND | 字段不存在 | 检查字段名或用更通用的描述 |
| QUERY_TIMEOUT | 查询超时 | 缩小查询范围 |

## 最佳实践

### 查询语句

✅ 好的查询：
```
查询北京地区企业客户的订单信息
近一周各网点的开单趋势
按客户类型统计订单数量
```

❌ 不好的查询：
```
给我看看数据
查一下
统计一下
```

### 会话管理

- 首次查询可不提供 `session_id`
- 连续追问使用相同的 `session_id`
- 会话有效期通常为 30 分钟

### 性能优化

1. 使用 `session_id` 减少模型识别开销
2. 大数据量启用 `stream: true`
3. 使用 `cursor` 分页
4. 提供 `hints` 提高准确率

## 下一步

- [查询工具](./query.md) - 结构化查询
- [元数据工具](./metadata.md) - 获取模型信息
- [工具概述](./overview.md) - 返回工具列表
