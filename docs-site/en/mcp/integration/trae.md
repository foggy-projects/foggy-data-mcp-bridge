# Trae CN Integration

This document describes how to integrate Foggy MCP service with ByteDance's Trae IDE.

## About Trae

[Trae](https://www.trae.cn/) is an AI-native IDE from ByteDance, with built-in models like Deepseek and Doubao. MCP protocol support was added in v1.3.0.

## Prerequisites

- Trae CN v1.3.0 or higher
- Foggy MCP service running (see [Quick Start](../guide/quick-start.md))

## Configuration Methods

### Method 1: JSON Configuration (Recommended)

1. Open Trae IDE
2. Click **Settings icon** (top right) → **MCP**
3. Click **Raw Configuration (JSON)** button
4. Add the following to `mcp.json`:

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

### Method 2: MCP Market

If Foggy MCP is published to Trae MCP Market:

1. Click **Settings** → **MCP** → **Add**
2. Search for "foggy" or "dataset" in the market
3. Click add and configure

## Endpoint Selection

| Endpoint | Use Case | Description |
|----------|----------|-------------|
| `/mcp/analyst/rpc` | Data Analysis | Structured queries, recommended |
| `/mcp/admin/rpc` | Full Access | All tool permissions |
| `/mcp/business/rpc` | Simple Queries | Natural language only |

## Verify Configuration

After configuration, MCP service status shows in the MCP list:

- **Green**: Connection successful
- **Red**: Connection failed, click to view error

For issues, check logs:
- **Windows**: `C:\Users\<username>\AppData\Roaming\Trae CN\logs`
- **macOS**: `~/Library/Application Support/Trae CN/logs`

## Using with Agents

Trae's **Builder with MCP** is a built-in agent that automatically includes configured MCP services.

### Usage Steps

1. Open AI Chat panel
2. Select **Builder with MCP** agent
3. Enter natural language query:

```
Query sales data for the last week, grouped by product category
```

### Add to Custom Agent

You can add MCP service to custom agents:

1. Go to **Agent Settings**
2. Add `foggy-dataset` in **Tools** section
3. Agent will automatically call MCP tools

## Usage Examples

### Data Query

```
Query order statistics for the last 7 days
```

### Data Analysis

```
Analyze sales data, find top 10 categories by revenue
```

### Report Generation

```
Generate monthly sales comparison report by store
```

### Code Generation

```
Query user table structure, generate TypeScript interface
```

## Configuration with Authentication

If MCP service requires authentication:

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc",
      "headers": {
        "Authorization": "Bearer your-token"
      }
    }
  }
}
```

## Multi-Project Configuration

Configure multiple MCP services:

```json
{
  "mcpServers": {
    "sales-data": {
      "url": "http://sales-server:7108/mcp/analyst/rpc"
    },
    "inventory-data": {
      "url": "http://inventory-server:7108/mcp/analyst/rpc"
    }
  }
}
```

## Troubleshooting

### MCP Service Shows Red

1. Check if MCP service is running:
   ```bash
   curl http://localhost:7108/actuator/health
   ```

2. Verify endpoint path is correct (use `/rpc`)

3. Check Trae logs for detailed error

### Configuration Not Working

1. Ensure JSON format is correct
2. Restart Trae IDE
3. Verify URL is accessible

### Tool Call Failed

1. Check MCP service logs
2. Verify datasource connection
3. Try simpler query for testing

## Comparison with Other IDEs

| Feature | Trae CN | Cursor | Claude Desktop |
|---------|---------|--------|----------------|
| Free to Use | ✅ | Partially | Subscription |
| Built-in Models | Deepseek/Doubao | Claude | Claude |
| MCP Support | ✅ | ✅ | ✅ |
| Agents | ✅ Built-in | ✅ | ❌ |
| Code Integration | ✅ | ✅ | ❌ |

## References

- [Trae Website](https://www.trae.cn/)
- [Trae MCP Documentation](https://docs.trae.cn/ide/model-context-protocol)

## Next Steps

- [Cursor Integration](./cursor.md) - Use with Cursor
- [Claude Desktop Integration](./claude-desktop.md) - Use with Claude Desktop
- [Tools List](../tools/overview.md) - View all available tools
