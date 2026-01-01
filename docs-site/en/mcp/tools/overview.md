# Tools Overview

Foggy MCP provides a series of data query and analysis tools for AI assistants to call.

## Tool List

| Tool | Description | Category |
|------|-------------|----------|
| [`dataset.get_metadata`](./metadata.md#get_metadata) | Get user-accessible model list | Metadata |
| [`dataset.describe_model_internal`](./metadata.md#describe_model_internal) | Get model field details | Metadata |
| [`dataset.query_model_v2`](./query.md) | Execute structured data query | Query |
| [`dataset_nl.query`](./nl-query.md) | Natural language data query | Natural Language |
| `chart.generate` | Generate data charts | Visualization |
| `dataset.export_with_chart` | Export data and charts | Export |

## Tool Categories

### Metadata Tools

Used to get meta-information about data models and fields:

- **get_metadata** - Get overview of all available models
- **describe_model_internal** - Get detailed field definitions of a single model

Suitable for understanding data structure before querying.

### Query Tools

Used to execute structured data queries:

- **query_model_v2** - Supports complex filtering, sorting, grouping, aggregation

Requires understanding of data models and query syntax, provides precise query control.

### Natural Language Tools

Used for intelligent data queries:

- **dataset_nl.query** - Describe query requirements in natural language

No technical knowledge needed, suitable for regular business users.

### Visualization Tools

Used to generate charts:

- **chart.generate** - Generate trend charts, bar charts, pie charts based on data

### Export Tools

Used to export data:

- **export_with_chart** - Export query results and charts

## Role Permissions

Different endpoints provide different tool sets:

### Permission Matrix

| Tool | Admin | Analyst | Business |
|------|:-----:|:-------:|:--------:|
| `dataset.get_metadata` | ✅ | ✅ | ❌ |
| `dataset.describe_model_internal` | ✅ | ✅ | ❌ |
| `dataset.query_model_v2` | ✅ | ✅ | ❌ |
| `chart.generate` | ✅ | ✅ | ❌ |
| `dataset.export_with_chart` | ✅ | ✅ | ❌ |
| `dataset_nl.query` | ✅ | ❌ | ✅ |

### Endpoint Description

| Endpoint | Role | Tool Scope |
|----------|------|------------|
| `/mcp/admin/rpc` | Admin | All tools |
| `/mcp/analyst/rpc` | Analyst | Metadata + Query + Visualization |
| `/mcp/business/rpc` | Business User | Natural language only |

## Typical Workflows

### Analyst Workflow

```
1. get_metadata          → Get available model list
2. describe_model_internal → View model field details
3. query_model_v2        → Execute data query
4. chart.generate        → Generate chart (optional)
```

### Business User Workflow

```
1. dataset_nl.query      → Describe requirements in natural language, done in one step
```

## Tool Call Examples

### MCP Protocol Call

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

### Response Format

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

## Error Handling

Tool calls may return the following errors:

| Error Code | Description |
|------------|-------------|
| `-32600` | Invalid request |
| `-32601` | Method not found |
| `-32602` | Invalid params |
| `-32603` | Internal error |

### Error Response Example

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "error": {
    "code": -32602,
    "message": "Missing required parameter: model"
  }
}
```

## Performance Tips

1. **Get metadata first**: Use `get_metadata` before querying to understand available models
2. **Use pagination**: Use `start` and `limit` parameters for large data queries
3. **Add filter conditions**: Avoid full table scans, use `slice` to filter data
4. **Select necessary fields**: Only query needed `columns` to reduce data transfer

## Next Steps

- [Metadata Tools](./metadata.md) - Get model and field information
- [Query Tools](./query.md) - Execute structured queries
- [Natural Language Query](./nl-query.md) - Intelligent data queries
