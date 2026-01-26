# Query Tools

`dataset.query_model` is the core tool for executing structured data queries.

## Basic Information

- **Tool Name**: `dataset.query_model`
- **Category**: Query
- **Permission**: Admin, Analyst

## Parameters

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `model` | string | ✅ | Query model name |
| `payload` | object | ✅ | Query parameters |

### payload Structure

```json
{
  "columns": ["field1", "field2"],
  "slice": [...],
  "orderBy": [...],
  "groupBy": [...],
  "start": 0,
  "limit": 100
}
```

## Query Parameters

### columns - Select Fields

```json
{
  "columns": [
    "customer$caption",           // Dimension display value
    "customer$id",                // Dimension ID
    "orderDate",                  // Attribute field
    "sum(totalAmount) as total",  // Aggregate expression
    "count(*) as count"           // Count
  ]
}
```

### slice - Filter Conditions

```json
{
  "slice": [
    {"field": "orderStatus", "op": "=", "value": "COMPLETED"},
    {"field": "totalAmount", "op": ">", "value": 1000},
    {"field": "category$id", "op": "in", "value": [1, 2, 3]},
    {"field": "orderDate", "op": "[)", "value": ["2024-01-01", "2024-07-01"]}
  ]
}
```

### Operators

| Operator | Description | Example Value |
|----------|-------------|---------------|
| `=` | Equal | `"COMPLETED"` |
| `!=` | Not equal | `"CANCELLED"` |
| `>` | Greater than | `100` |
| `>=` | Greater than or equal | `100` |
| `<` | Less than | `1000` |
| `<=` | Less than or equal | `1000` |
| `in` | In list | `["A", "B", "C"]` |
| `not in` | Not in list | `["X", "Y"]` |
| `like` | Fuzzy match | Auto adds `%` on both sides |
| `left_like` | Left fuzzy | Auto adds `%` on left |
| `right_like` | Right fuzzy | Auto adds `%` on right |
| `is null` | Is null | No value needed |
| `is not null` | Not null | No value needed |
| `[]` | Closed interval | `[100, 500]` |
| `[)` | Left-closed right-open | `["2024-01-01", "2024-07-01"]` |

### orderBy - Sorting

```json
{
  "orderBy": [
    {"field": "totalAmount", "dir": "DESC"},
    {"field": "orderDate", "dir": "ASC"}
  ]
}
```

### groupBy - Grouping

```json
{
  "groupBy": [
    {"field": "customer$id"},
    {"field": "category$id"}
  ]
}
```

### Pagination

```json
{
  "start": 0,    // Start position
  "limit": 100   // Return count
}
```

## Complete Example

### Basic Query

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
        "columns": ["customer$caption", "orderDate", "totalAmount"],
        "slice": [
          {"field": "orderStatus", "op": "=", "value": "COMPLETED"}
        ],
        "orderBy": [{"field": "orderDate", "dir": "DESC"}],
        "limit": 10
      }
    }
  }
}
```

### Aggregate Query

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "columns": [
      "customer$caption",
      "sum(totalAmount) as total",
      "count(*) as orderCount"
    ],
    "groupBy": [{"field": "customer$id"}],
    "orderBy": [{"field": "total", "dir": "DESC"}],
    "limit": 10
  }
}
```

### Multi-condition Query

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "columns": ["category$caption", "sum(totalAmount) as total"],
    "slice": [
      {"field": "orderDate", "op": "[)", "value": ["2024-01-01", "2024-07-01"]},
      {"field": "region$id", "op": "in", "value": [1, 2, 3]},
      {"field": "totalAmount", "op": ">", "value": 100}
    ],
    "groupBy": [{"field": "category$id"}],
    "orderBy": [{"field": "total", "dir": "DESC"}]
  }
}
```

## Response Format

### Standard Response

```json
{
  "success": true,
  "data": {
    "items": [
      {"customer$caption": "Customer A", "total": 125000, "orderCount": 45},
      {"customer$caption": "Customer B", "total": 89000, "orderCount": 32}
    ],
    "total": 150,
    "start": 0,
    "limit": 10,
    "pagination": {
      "start": 0,
      "limit": 10,
      "returned": 10,
      "totalCount": 150,
      "hasMore": true,
      "rangeDescription": "Showing 1-10 of 150 records"
    }
  }
}
```

### Large Data Auto-Truncation

When an MCP query returns excessive data (cells exceed threshold), the system **automatically truncates the result** and provides access links to the complete data, preventing large datasets from consuming the LLM's context window.

#### Trigger Conditions

- Query Source: Only applies to MCP tool calls
- Data Threshold: **Cell count (rows × columns) exceeds 10,000** (configurable)
- Truncation Limit: Returns first **100 rows** to LLM (configurable)

#### Truncated Response Format

```json
{
  "success": true,
  "data": {
    "items": [
      // ... truncated to 100 rows
    ],
    "total": 50000,
    "truncationInfo": {
      "truncated": true,
      "originalRowCount": 50000,
      "truncatedRowCount": 100,
      "columnCount": 15,
      "cellCount": 750000,
      "message": "Large dataset (50000 rows × 15 columns = 750000 cells) has been truncated to 100 rows.",
      "viewerUrl": "http://localhost:8080/data-viewer/view/abc123def456",
      "apiUrl": "http://localhost:8080/data-viewer/api/query/abc123def456/data",
      "hint": "You can access the complete data via the links above, or use API pagination (params: start, limit)"
    }
  }
}
```

#### Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| `truncated` | boolean | Whether data was truncated |
| `originalRowCount` | number | Original row count |
| `truncatedRowCount` | number | Rows kept after truncation |
| `columnCount` | number | Number of columns |
| `cellCount` | number | Total cell count |
| `message` | string | Truncation explanation |
| `viewerUrl` | string | Browser link to view complete data |
| `apiUrl` | string | API endpoint for complete data |
| `hint` | string | Usage instructions |

#### LLM Handling Guidelines

When `truncationInfo.truncated = true` is detected, the LLM should:

1. **Inform the user** about data truncation and the reason (large dataset)
2. **Show sample data**: Use the truncated 100 rows as a sample
3. **Provide access links**: Guide users to view complete data via links
4. **Explain pagination**: For programmatic access, mention API pagination

Example response:

> The query returned 50,000 rows (750,000 cells total), which is quite large. Here's a sample of the first 100 rows:
>
> [Display table...]
>
> To view the complete data, please visit: [Data Viewer](http://localhost:8080/data-viewer/view/abc123def456)
>
> For API pagination, use: `/data-viewer/api/query/abc123def456/data?start=0&limit=100`

#### Configuration

Adjust truncation parameters in `application.yml`:

```yaml
foggy:
  data-viewer:
    thresholds:
      # Cell threshold (rows × columns)
      cell-threshold-for-truncation: 10000
      # Rows to keep after truncation
      truncated-row-limit: 100
```

## Notes

1. **Use get_metadata first**: Understand available models before querying
2. **Use describe_model_internal**: Get field details of target model
3. **Distinguish $id and $caption**: Use `$id` for filtering, `$caption` for display
4. **Add limit**: Avoid returning too much data
5. **Use appropriate operators**: Choose correct operators for filtering

## Next Steps

- [Metadata Tools](./metadata.md) - Get model and field info
- [Natural Language Query](./nl-query.md) - Intelligent queries
- [Tools Overview](./overview.md) - Return to tools list
