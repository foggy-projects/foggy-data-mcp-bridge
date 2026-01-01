# API 调用

本文档介绍如何直接调用 Foggy MCP 服务的 JSON-RPC API。

## 端点地址

| 端点 | 用途 |
|------|------|
| `POST /mcp/admin/rpc` | 管理员接口 |
| `POST /mcp/analyst/rpc` | 分析师接口 |
| `POST /mcp/business/rpc` | 业务用户接口 |

## 请求格式

所有请求使用 JSON-RPC 2.0 格式：

```json
{
  "jsonrpc": "2.0",
  "id": "唯一请求ID",
  "method": "方法名",
  "params": {}
}
```

### HTTP Headers

| Header | 说明 | 必填 |
|--------|------|:----:|
| `Content-Type` | `application/json` | ✅ |
| `Authorization` | 认证信息 | ❌ |
| `X-Trace-Id` | 会话追踪 ID | ❌ |

## 核心方法

### initialize

初始化 MCP 连接。

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

**响应：**
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

获取可用工具列表。

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

**响应：**
```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "result": {
    "tools": [
      {
        "name": "dataset.get_metadata",
        "description": "获取用户可访问的数据模型列表",
        "inputSchema": {
          "type": "object",
          "properties": {}
        }
      },
      {
        "name": "dataset.query_model_v2",
        "description": "执行数据查询",
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

调用指定工具。

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

**响应：**
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

心跳检测。

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

## 工具调用示例

### 获取元数据

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

### 获取模型详情

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

### 执行查询

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

### 自然语言查询

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
        "query": "查询本月销售额前10的客户"
      }
    }
  }'
```

## 错误处理

### 错误响应格式

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

### 错误码

| 错误码 | 说明 |
|--------|------|
| `-32700` | 解析错误（JSON 格式错误） |
| `-32600` | 无效请求 |
| `-32601` | 方法不存在 |
| `-32602` | 无效参数 |
| `-32603` | 内部错误 |

## 流式响应 (SSE)

对于长时间运行的查询，可以使用 SSE 端点获取实时进度：

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
        "query": "分析各地区销售趋势"
      }
    }
  }'
```

**响应流：**
```
event: progress
data: {"step": "analyzing", "message": "正在分析查询意图..."}

event: progress
data: {"step": "querying", "message": "正在执行查询..."}

event: complete
data: {"type": "result", "items": [...]}
```

## 编程语言示例

### JavaScript

```javascript
async function callMcpTool(toolName, args) {
  const response = await fetch('http://localhost:7108/mcp/analyst/rpc', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      jsonrpc: '2.0',
      id: Date.now().toString(),
      method: 'tools/call',
      params: {
        name: toolName,
        arguments: args
      }
    })
  });

  const result = await response.json();
  if (result.error) {
    throw new Error(result.error.message);
  }
  return JSON.parse(result.result.content[0].text);
}

// 使用示例
const metadata = await callMcpTool('dataset.get_metadata', {});
console.log(metadata.models);
```

### Python

```python
import requests
import json

def call_mcp_tool(tool_name, args=None):
    response = requests.post(
        'http://localhost:7108/mcp/analyst/rpc',
        headers={'Content-Type': 'application/json'},
        json={
            'jsonrpc': '2.0',
            'id': '1',
            'method': 'tools/call',
            'params': {
                'name': tool_name,
                'arguments': args or {}
            }
        }
    )
    result = response.json()
    if 'error' in result:
        raise Exception(result['error']['message'])
    return json.loads(result['result']['content'][0]['text'])

# 使用示例
metadata = call_mcp_tool('dataset.get_metadata')
print(metadata['models'])
```

### Java

```java
import java.net.http.*;
import java.net.URI;
import com.fasterxml.jackson.databind.ObjectMapper;

public class McpClient {
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public String callTool(String toolName, Map<String, Object> args) throws Exception {
        Map<String, Object> request = Map.of(
            "jsonrpc", "2.0",
            "id", "1",
            "method", "tools/call",
            "params", Map.of(
                "name", toolName,
                "arguments", args != null ? args : Map.of()
            )
        );

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:7108/mcp/analyst/rpc"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request)))
            .build();

        HttpResponse<String> response = client.send(httpRequest,
            HttpResponse.BodyHandlers.ofString());

        return response.body();
    }
}
```

## 下一步

- [Claude Desktop 集成](./claude-desktop.md) - 在 Claude 中使用
- [Cursor 集成](./cursor.md) - 在 Cursor 中使用
- [工具列表](../tools/overview.md) - 查看所有工具
