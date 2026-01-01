# API Usage

This document describes how to directly call Foggy MCP Service JSON-RPC API.

## Endpoint Addresses

| Endpoint | Purpose |
|----------|---------|
| `POST /mcp/admin/rpc` | Admin interface |
| `POST /mcp/analyst/rpc` | Analyst interface |
| `POST /mcp/business/rpc` | Business user interface |

## Request Format

All requests use JSON-RPC 2.0 format:

```json
{
  "jsonrpc": "2.0",
  "id": "unique-request-id",
  "method": "method-name",
  "params": {}
}
```

### HTTP Headers

| Header | Description | Required |
|--------|-------------|:--------:|
| `Content-Type` | `application/json` | ✅ |
| `Authorization` | Authentication info | ❌ |
| `X-Trace-Id` | Session trace ID | ❌ |

## Core Methods

### initialize

Initialize MCP connection.

```bash
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {
        "name": "my-client",
        "version": "1.0.0"
      }
    }
  }'
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {}
    },
    "serverInfo": {
      "name": "foggy-mcp-server",
      "version": "8.0.1"
    }
  }
}
```

### tools/list

Get available tools list.

```bash
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/list",
    "params": {}
  }'
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "result": {
    "tools": [
      {
        "name": "dataset.get_metadata",
        "description": "Get user-accessible data model list",
        "inputSchema": {
          "type": "object",
          "properties": {}
        }
      },
      {
        "name": "dataset.query_model_v2",
        "description": "Execute data query",
        "inputSchema": {
          "type": "object",
          "properties": {
            "model": {"type": "string"},
            "payload": {"type": "object"}
          },
          "required": ["model", "payload"]
        }
      }
    ]
  }
}
```

### tools/call

Call a specific tool.

```bash
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "3",
    "method": "tools/call",
    "params": {
      "name": "dataset.get_metadata",
      "arguments": {}
    }
  }'
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": "3",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"models\":[...]}"
      }
    ]
  }
}
```

### ping

Heartbeat check.

```bash
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "4",
    "method": "ping",
    "params": {}
  }'
```

## Tool Call Examples

### Get Metadata

```bash
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/call",
    "params": {
      "name": "dataset.get_metadata",
      "arguments": {}
    }
  }'
```

### Get Model Details

```bash
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/call",
    "params": {
      "name": "dataset.describe_model_internal",
      "arguments": {
        "model": "FactSalesQueryModel"
      }
    }
  }'
```

### Execute Query

```bash
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "3",
    "method": "tools/call",
    "params": {
      "name": "dataset.query_model_v2",
      "arguments": {
        "model": "FactSalesQueryModel",
        "payload": {
          "columns": ["customer$caption", "sum(totalAmount) as total"],
          "orderBy": [{"field": "total", "dir": "DESC"}],
          "limit": 10
        }
      }
    }
  }'
```

### Natural Language Query

```bash
curl -X POST http://localhost:7108/mcp/business/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "4",
    "method": "tools/call",
    "params": {
      "name": "dataset_nl.query",
      "arguments": {
        "query": "Query top 10 customers by sales this month"
      }
    }
  }'
```

## Error Handling

### Error Response Format

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

### Error Codes

| Error Code | Description |
|------------|-------------|
| `-32700` | Parse error (JSON format error) |
| `-32600` | Invalid request |
| `-32601` | Method not found |
| `-32602` | Invalid params |
| `-32603` | Internal error |

## Streaming Response (SSE)

For long-running queries, use SSE endpoint for real-time progress:

```bash
curl -X POST http://localhost:7108/mcp/analyst/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/call",
    "params": {
      "name": "dataset_nl.query",
      "arguments": {
        "query": "Analyze sales trends by region"
      }
    }
  }'
```

**Response Stream:**
```
event: progress
data: {"step": "analyzing", "message": "Analyzing query..."}

event: progress
data: {"step": "querying", "message": "Executing query..."}

event: complete
data: {"result": {...}}
```

## Next Steps

- [Claude Desktop Integration](./claude-desktop.md) - Configure Claude Desktop
- [Cursor Integration](./cursor.md) - Configure Cursor IDE
- [Tools List](../tools/overview.md) - View all available tools
