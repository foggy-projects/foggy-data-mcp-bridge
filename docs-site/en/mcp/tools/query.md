# Query Tools

`dataset.query_model_v2` is the core tool for executing structured data queries.

## Basic Information

- **Tool Name**: `dataset.query_model_v2`
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
    "name": "dataset.query_model_v2",
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

```json
{
  "success": true,
  "data": {
    "items": [
      {"customer$caption": "Customer A", "total": 125000, "orderCount": 45},
      {"customer$caption": "Customer B", "total": 89000, "orderCount": 32}
    ],
    "total": 150,
    "pageSize": 10,
    "page": 1
  }
}
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
