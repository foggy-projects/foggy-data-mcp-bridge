# Extension Tools

Foggy MCP supports adding additional tools through extension modules. These tools require extra dependencies and configuration to enable.

## Data Viewer

Data Viewer is a data browser extension that provides the `dataset.open_in_viewer` tool for generating shareable browser links for interactive browsing of large datasets.

### Requirements

Data Viewer requires the following dependencies:

1. **MongoDB** - For caching query context
2. **foggy-data-viewer module** - Included in `foggy-mcp-launcher`

### Configuration

Ensure MongoDB is running and configured:

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/foggy_test
      auto-index-creation: true

# Optional: Disable Data Viewer
foggy:
  data-viewer:
    enabled: true  # Default true
    base-url: http://localhost:7108/data-viewer
```

Start MongoDB (using demo docker):

```bash
cd foggy-dataset-demo/docker
docker-compose up -d mongo
```

### Verification

After starting the service, call `tools/list` to check if the tool is available:

```bash
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}'
```

If `dataset.open_in_viewer` appears in the list, the extension is enabled.

---

## dataset.open_in_viewer

Generate shareable browser links for interactive browsing of large datasets.

### Use Cases

**Suitable for:**
- Detail queries expecting large result sets (500+ rows)
- User requests for "all", "list", "export" type queries
- Interactive exploration needs (filter, sort, paginate)

**Not suitable (use dataset.query_model instead):**
- Aggregate queries with groupBy (small result sets)
- Queries explicitly limited to ≤100 rows
- AI needs to directly analyze the data

### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `model` | string | ✅ | Query model name |
| `payload` | object | ✅ | Query parameters (same format as `query_model`) |
| `payload.columns` | array | ✅ | Columns to display |
| `payload.slice` | array | ✅ | Filter conditions (at least one) |
| `payload.orderBy` | array | ❌ | Sort configuration |
| `payload.groupBy` | array | ❌ | Group/aggregate fields |
| `title` | string | ❌ | Title for the data view |

> **Important**: `payload.slice` must provide at least one filter condition to prevent unbounded queries.

### Example Call

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "dataset.open_in_viewer",
    "arguments": {
      "model": "FactOrderQueryModel",
      "payload": {
        "columns": ["orderNo", "customer$caption", "orderDate$caption", "totalAmount"],
        "slice": [
          {"field": "orderDate$id", "op": "[)", "value": ["20250101", "20250131"]}
        ],
        "orderBy": [
          {"field": "orderDate$id", "dir": "DESC"}
        ]
      },
      "title": "January 2025 Orders"
    }
  }
}
```

### Response

```json
{
  "viewerUrl": "http://localhost:7108/data-viewer/view/abc123",
  "queryId": "abc123",
  "expiresAt": "2025-01-06T10:00:00",
  "estimatedRowCount": 1500,
  "message": "Data viewer link created. The link expires at 2025-01-06T10:00:00. Users can browse, filter, sort, and export the data interactively."
}
```

| Field | Description |
|-------|-------------|
| `viewerUrl` | Browser access link |
| `queryId` | Query cache ID |
| `expiresAt` | Link expiration time |
| `estimatedRowCount` | Estimated row count (optional) |

### Permissions

| Endpoint | Available |
|----------|:---------:|
| `/mcp/admin/rpc` | ✅ |
| `/mcp/analyst/rpc` | ✅ |
| `/mcp/business/rpc` | ❌ |

### Comparison with query_model

| Feature | query_model | open_in_viewer |
|---------|-------------|----------------|
| Returns | Direct query results | Browser link |
| Suitable data size | Small (≤100 rows) | Large (500+ rows) |
| AI analysis | ✅ AI can analyze directly | ❌ User views in browser |
| Interactivity | ❌ Static results | ✅ Filter/sort/paginate/export |
| Filter required | Optional | Required (at least one) |

### Typical Workflow

```
User: "Export all orders from last month"

AI decides:
1. "All orders" → Potentially large dataset
2. User asks for "export" → Needs interactive browsing
3. Choose open_in_viewer over query_model

AI calls open_in_viewer:
→ Returns browser link
→ User can view, filter, export in browser
```

---

## Developing Custom Extensions

To develop custom MCP tool extensions, refer to the `foggy-data-viewer` module implementation:

### 1. Create Tool Class

Implement `McpTool` interface (from `foggy-mcp-spi`):

```java
public class MyCustomTool implements McpTool {

    @Override
    public String getName() {
        return "my.custom_tool";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        return Set.of(ToolCategory.QUERY);
    }

    @Override
    public Object execute(Map<String, Object> arguments,
                          ToolExecutionContext context) {
        // Tool logic
        return Map.of("success", true);
    }
}
```

### 2. Create Auto-Configuration Class

Use `@AutoConfiguration` to register tool Bean:

```java
@AutoConfiguration
@ConditionalOnProperty(prefix = "my.extension", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
public class MyExtensionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MyCustomTool myCustomTool() {
        return new MyCustomTool();
    }
}
```

### 3. Register Auto-Configuration

Add to `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.example.MyExtensionAutoConfiguration
```

### 4. Add Dependencies

Extension modules need to depend on `foggy-mcp-spi`:

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-mcp-spi</artifactId>
    <version>${foggy.version}</version>
</dependency>
```

Tools will be automatically discovered and registered by `McpToolDispatcher`.

## Next Steps

- [Tools Overview](./overview.md) - View all built-in tools
- [Query Tool](./query.md) - Learn query_model usage
