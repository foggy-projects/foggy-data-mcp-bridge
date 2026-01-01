# Claude Desktop Integration

This document details how to integrate Foggy MCP Service with Claude Desktop.

## Configuration File Location

Claude Desktop MCP configuration file is located at:

| OS | Configuration File Path |
|----|------------------------|
| macOS | `~/Library/Application Support/Claude/claude_desktop_config.json` |
| Windows | `%APPDATA%\Claude\claude_desktop_config.json` |
| Linux | `~/.config/Claude/claude_desktop_config.json` |

## Basic Configuration

### Minimal Configuration

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

### Full Configuration Example

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc",
      "headers": {
        "Authorization": "Bearer your-token-here"
      }
    }
  }
}
```

## Endpoint Selection

Choose different endpoints based on use case:

| Endpoint | Target Users | Available Tools |
|----------|--------------|-----------------|
| `/mcp/analyst/rpc` | Data Analysts | Metadata, Query, Charts (Recommended) |
| `/mcp/admin/rpc` | Administrators | All tools |
| `/mcp/business/rpc` | Business Users | Natural language query only |

### Analyst Endpoint (Recommended)

Suitable for professional users who need precise query control:

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

### Business Endpoint

Suitable for non-technical users who only need to describe requirements in natural language:

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/business/rpc"
    }
  }
}
```

## Apply Configuration

1. Save configuration file
2. Completely quit Claude Desktop (not just close window)
   - macOS: Right-click Dock icon → Quit
   - Windows: Right-click system tray icon → Exit
3. Restart Claude Desktop

## Verify Connection

After starting Claude Desktop, verify with:

1. Type in chat: `What data models are available?`
2. Claude should call the `dataset.get_metadata` tool and return model list

If Claude cannot recognize MCP tools, check:
- Configuration file JSON format is correct
- MCP service is running
- URL is accessible

## Usage Examples

### Query Metadata

```
List all available data models and fields
```

### Execute Query

```
Query sales data for the last week, summarize amount by product category
```

### Generate Charts

```
Generate sales trend chart for the last 30 days
```

## Multi-Service Configuration

You can configure multiple MCP services simultaneously:

```json
{
  "mcpServers": {
    "foggy-sales": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    },
    "foggy-inventory": {
      "url": "http://inventory-server:7108/mcp/analyst/rpc"
    }
  }
}
```

## Remote Server Configuration

If MCP service is deployed on a remote server:

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "https://mcp.example.com/mcp/analyst/rpc",
      "headers": {
        "Authorization": "Bearer your-api-token"
      }
    }
  }
}
```

## Troubleshooting

### Configuration Not Taking Effect

1. Confirm configuration file path is correct
2. Use JSON validation tool to check format
3. Completely quit and restart Claude Desktop

### Connection Failed

```bash
# Test if service is accessible
curl http://localhost:7108/actuator/health

# Test MCP endpoint
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"ping","params":{}}'
```

### Tool Call Failed

View MCP service logs:

```bash
# Docker environment
docker-compose logs -f mcp

# Local development
tail -f logs/mcp.log
```

## Next Steps

- [Cursor Integration](./cursor.md) - Use in Cursor
- [API Usage](./api.md) - Direct MCP API calls
- [Tools List](../tools/overview.md) - View all available tools
