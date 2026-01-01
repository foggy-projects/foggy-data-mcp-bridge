# Metadata Tools

Metadata tools are used to get data model and field definition information, essential tools for understanding data structure before querying.

## get_metadata

Get all data models accessible by the user.

### Basic Information

- **Tool Name**: `dataset.get_metadata`
- **Category**: Metadata
- **Permission**: Admin, Analyst

### Parameters

This tool requires no parameters.

### Call Example

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
  "success": true,
  "data": {
    "models": [
      {
        "name": "FactSalesQueryModel",
        "caption": "Sales Analysis",
        "description": "Sales order detail data"
      },
      {
        "name": "FactOrderQueryModel",
        "caption": "Order Analysis",
        "description": "Order information query"
      }
    ],
    "fieldIndex": {
      "customerName": ["FactSalesQueryModel", "FactOrderQueryModel"],
      "totalAmount": ["FactSalesQueryModel"]
    },
    "dictionaries": {
      "customerType": {
        "10": "Individual",
        "20": "Enterprise",
        "30": "VIP"
      }
    }
  }
}
```

### Response Fields

| Field | Description |
|-------|-------------|
| `models` | Available model list |
| `fieldIndex` | Field semantic index (which models contain the field) |
| `dictionaries` | Dictionary appendix (field value mappings) |

### Use Cases

- User asks "What data models are available"
- Need to understand data scope before querying
- Explore field relationships across different models

---

## describe_model_internal

Get detailed field definitions for a specified model.

### Basic Information

- **Tool Name**: `dataset.describe_model_internal`
- **Category**: Metadata
- **Permission**: Admin, Analyst

### Parameters

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `model` | string | ✅ | Model name |

### Call Example

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "dataset.describe_model_internal",
    "arguments": {
      "model": "FactSalesQueryModel"
    }
  }
}
```

### Response Format

```json
{
  "success": true,
  "data": {
    "model": "FactSalesQueryModel",
    "caption": "Sales Analysis",
    "description": "Sales order detail data analysis",
    "fields": [
      {
        "name": "customer$id",
        "type": "DIMENSION",
        "caption": "Customer ID",
        "description": "For precise query"
      },
      {
        "name": "customer$caption",
        "type": "DIMENSION",
        "caption": "Customer Name",
        "description": "For display"
      },
      {
        "name": "totalAmount",
        "type": "MEASURE",
        "caption": "Sales Amount",
        "dataType": "MONEY",
        "aggregation": "SUM"
      },
      {
        "name": "orderStatus",
        "type": "ATTRIBUTE",
        "caption": "Order Status",
        "dictionary": "orderStatus"
      }
    ],
    "dictionaries": {
      "orderStatus": {
        "1": "Pending Payment",
        "2": "Paid",
        "3": "Shipped",
        "4": "Completed",
        "5": "Cancelled"
      }
    }
  }
}
```

### Field Types

| Type | Description | Usage |
|------|-------------|-------|
| `DIMENSION` | Dimension | Grouping, filtering, has `$id` and `$caption` variants |
| `MEASURE` | Measure | Aggregate calculations (SUM, AVG, COUNT, etc.) |
| `ATTRIBUTE` | Attribute | Regular field, use directly |

### Dimension Field Variants

Dimension fields return two variants:

| Variant | Description | Use Case |
|---------|-------------|----------|
| `xxx$id` | Returns ID value | Precise filtering, relationship queries |
| `xxx$caption` | Returns display name | Display to users |

**Example:**
```json
{
  "columns": ["customer$caption"],  // Display customer name
  "slice": [
    {"field": "customer$id", "op": "=", "value": 1001}  // Filter by ID
  ]
}
```

### Parent-Child Dimensions

Hierarchical dimensions additionally support `$hierarchy$` perspective:

| Variant | Description |
|---------|-------------|
| `team$id` | Exact match this node |
| `team$hierarchy$id` | Match this node and all descendants |

**Example:** Query headquarters and all sub-departments
```json
{
  "slice": [
    {"field": "team$hierarchy$id", "op": "=", "value": "T001"}
  ]
}
```

### Use Cases

- Confirm field names and types before querying
- Understand optional values for dictionary fields
- Confirm dimension `$id` / `$caption` usage

---

## Typical Workflow

```
1. get_metadata
   → Get all available model list

2. describe_model_internal
   → Select target model, get detailed fields

3. query_model_v2
   → Execute query using correct field names
```

## Next Steps

- [Query Tools](./query.md) - Execute data queries
- [Natural Language Query](./nl-query.md) - Intelligent queries
- [Tools Overview](./overview.md) - Return to tools list
