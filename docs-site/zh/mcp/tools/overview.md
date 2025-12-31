# 工具概述

Foggy MCP 提供一系列数据查询和分析工具，供 AI 助手调用。

## 工具列表

| 工具 | 说明 | 分类 |
|------|------|------|
| [`dataset.get_metadata`](./metadata.md#get_metadata) | 获取用户可访问的模型列表 | 元数据 |
| [`dataset.describe_model_internal`](./metadata.md#describe_model_internal) | 获取模型详细字段信息 | 元数据 |
| [`dataset.query_model_v2`](./query.md) | 执行结构化数据查询 | 查询 |
| [`dataset_nl.query`](./nl-query.md) | 自然语言数据查询 | 自然语言 |
| `chart.generate` | 生成数据图表 | 可视化 |
| `dataset.export_with_chart` | 导出数据和图表 | 导出 |

## 工具分类

### 元数据工具

用于获取数据模型和字段的元信息：

- **get_metadata** - 获取所有可用模型的概览
- **describe_model_internal** - 获取单个模型的详细字段定义

适合在查询前了解数据结构。

### 查询工具

用于执行结构化数据查询：

- **query_model_v2** - 支持复杂过滤、排序、分组、聚合

需要了解数据模型和查询语法，提供精确的查询控制。

### 自然语言工具

用于智能数据查询：

- **dataset_nl.query** - 用自然语言描述查询需求

无需了解技术细节，适合普通业务用户。

### 可视化工具

用于生成图表：

- **chart.generate** - 根据数据生成趋势图、柱状图、饼图等

### 导出工具

用于导出数据：

- **export_with_chart** - 导出查询结果和图表

## 角色权限

不同端点提供不同的工具集：

### 权限矩阵

| 工具 | Admin | Analyst | Business |
|------|:-----:|:-------:|:--------:|
| `dataset.get_metadata` | ✅ | ✅ | ❌ |
| `dataset.describe_model_internal` | ✅ | ✅ | ❌ |
| `dataset.query_model_v2` | ✅ | ✅ | ❌ |
| `chart.generate` | ✅ | ✅ | ❌ |
| `dataset.export_with_chart` | ✅ | ✅ | ❌ |
| `dataset_nl.query` | ✅ | ❌ | ✅ |

### 端点说明

| 端点 | 角色 | 工具范围 |
|------|------|----------|
| `/mcp/admin/rpc` | 管理员 | 全部工具 |
| `/mcp/analyst/rpc` | 分析师 | 元数据 + 查询 + 可视化 |
| `/mcp/business/rpc` | 业务用户 | 仅自然语言 |

## 典型工作流

### 分析师工作流

```
1. get_metadata          → 获取可用模型列表
2. describe_model_internal → 查看模型字段详情
3. query_model_v2        → 执行数据查询
4. chart.generate        → 生成图表（可选）
```

### 业务用户工作流

```
1. dataset_nl.query      → 自然语言描述需求，一步完成
```

## 工具调用示例

### MCP 协议调用

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "dataset.get_metadata",
    "arguments": {}
  }
}
```

### 响应格式

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{...}"
      }
    ]
  }
}
```

## 错误处理

工具调用可能返回以下错误：

| 错误码 | 说明 |
|--------|------|
| `-32600` | 无效请求 |
| `-32601` | 方法不存在 |
| `-32602` | 无效参数 |
| `-32603` | 内部错误 |

### 错误响应示例

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "error": {
    "code": -32602,
    "message": "缺少必要参数: model"
  }
}
```

## 性能建议

1. **先获取元数据**：查询前使用 `get_metadata` 了解可用模型
2. **使用分页**：大数据量查询时使用 `start` 和 `limit` 参数
3. **添加过滤条件**：避免全表扫描，使用 `slice` 过滤数据
4. **选择必要字段**：只查询需要的 `columns`，减少数据传输

## 下一步

- [元数据工具](./metadata.md) - 获取模型和字段信息
- [查询工具](./query.md) - 执行结构化查询
- [自然语言查询](./nl-query.md) - 智能数据查询
