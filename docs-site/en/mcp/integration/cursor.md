# Cursor Integration

This document describes how to integrate Foggy MCP Service with Cursor IDE.

## Configuration Methods

Cursor supports adding MCP servers through settings interface or configuration files.

### Method 1: Settings Interface (Recommended)

1. Open Cursor
2. Go to **Settings** → **Features** → **MCP Servers**
3. Click **Add Server**
4. Fill in configuration:
   - **Name**: `foggy-dataset`
   - **URL**: `http://localhost:7108/mcp/analyst/rpc`

### Method 2: Configuration File

Edit Cursor configuration file:

- **macOS**: `~/Library/Application Support/Cursor/User/settings.json`
- **Windows**: `%APPDATA%\Cursor\User\settings.json`
- **Linux**: `~/.config/Cursor/User/settings.json`

Add the following configuration:

```json
{
  "mcp.servers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

## Endpoint Selection

Choose endpoints based on use case:

| Endpoint | Use Case | Description |
|----------|----------|-------------|
| `/mcp/analyst/rpc` | Development & Debug | Structured queries, precise control |
| `/mcp/admin/rpc` | Full Access | Includes all tools |
| `/mcp/business/rpc` | Simple Queries | Natural language only |

## Use Cases

### Database Query Assistance

During development, have Claude help with:

```
Query order statistics for the last 7 days from orders table
```

### Data Analysis

```
Analyze sales data, find top 10 categories by sales amount
```

### Generate Reports

```
Generate this month's sales comparison chart across stores
```

## Usage in Code

Cursor's AI assistant can call MCP tools during code editing:

```javascript
// Have Claude query data and generate code
// Input: "Query user table field structure, generate TypeScript interface"

// Claude will call dataset.describe_model_internal to get metadata
// Then generate code like:

interface User {
  id: number;
  username: string;
  email: string;
  createdAt: Date;
}
```

## Configuration with Authentication

If MCP service requires authentication:

```json
{
  "mcp.servers": {
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

Configure different MCP services for different projects:

```json
{
  "mcp.servers": {
    "sales-data": {
      "url": "http://sales-server:7108/mcp/analyst/rpc"
    },
    "inventory-data": {
      "url": "http://inventory-server:7108/mcp/analyst/rpc"
    }
  }
}
```

## Verify Configuration

After configuration:

1. Restart Cursor
2. Open AI Chat (Cmd/Ctrl + L)
3. Input: `List available data models`
4. Confirm Claude can call `dataset.get_metadata` tool

## Troubleshooting

### MCP Tools Not Available

1. Check if MCP service is running:
   ```bash
   curl http://localhost:7108/actuator/health
   ```

2. Check Cursor logs:
   - **macOS**: `~/Library/Logs/Cursor/`
   - **Windows**: `%APPDATA%\Cursor\logs\`

### Configuration Not Taking Effect

1. Ensure JSON format is correct
2. Completely quit and restart Cursor
3. Check URL includes full path (including `/rpc`)

### Connection Timeout

If queries timeout, it may be:
- MCP service responding slowly, check service logs
- Network connection issues
- Query data volume too large, try adding filter conditions

## Comparison with Claude Desktop

| Feature | Cursor | Claude Desktop |
|---------|--------|----------------|
| Code Integration | ✅ Deep Integration | ❌ Standalone App |
| Context Awareness | ✅ Current File/Project | ❌ Conversation Only |
| MCP Support | ✅ | ✅ |
| Configuration Complexity | Low | Low |

## Next Steps

- [Claude Desktop Integration](./claude-desktop.md) - Use in Claude Desktop
- [API Usage](./api.md) - Direct MCP API calls
- [Tools List](../tools/overview.md) - View all available tools
