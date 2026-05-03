# Query Tool

`dataset.query_model` is the core structured query tool. It supports single-model filtering, sorting, grouping, aggregation, calculated fields, time-window analysis, and pivot analysis.

Use `dataset.compose_script` instead when the request needs cross-model Join / Union, derived queries based on a previous plan, or multiple returned plans.

## Basic Information

- **Tool Name**: `dataset.query_model`
- **Category**: Data Query
- **Permission**: Admin, Analyst

## Parameters

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `model` | string | yes | Query model name |
| `payload` | object | yes | Query parameters |
| `mode` | string | no | Execution mode: `execute` (default) or `validate` |

### payload Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `columns` | array | Returned columns; supports inline aggregations |
| `slice` | array | Filter conditions |
| `orderBy` | array | Sorting rules |
| `groupBy` | array | Grouping fields, usually inferred automatically |
| `calculatedFields` | array | Calculated field definitions |
| `timeWindow` | object | Time-window analysis for YoY, MoM, YTD, MTD, rolling windows |
| `pivot` | object | Multi-dimensional pivot analysis with row axes, column axes, metrics, subtotals, grand totals |
| `start` | number | Start row, zero-based |
| `limit` | number | Returned row count |
| `returnTotal` | boolean | Whether to return total row count |

> `pivot` is mutually exclusive with `columns`, and `pivot` is mutually exclusive with `timeWindow`. Use `pivot` for cross-tabs. Use `timeWindow` for YoY, MoM, and rolling-window analysis.

## Field Rules

Use field names returned by `dataset.describe_model_internal`.

### Dimension Fields

Dimension fields expose two common variants:

- `xxx$id`: use for exact matching and filtering
- `xxx$caption`: use for display labels

```json
{
  "columns": ["customer$caption", "salesDate$caption"],
  "slice": [
    {"field": "customer$id", "op": "=", "value": 1001}
  ]
}
```

### Parent-Child Dimensions

Hierarchical dimensions expose a `$hierarchy$` view:

- `xxx$id`: exact node match
- `xxx$hierarchy$id`: match the node and its descendants

```json
{
  "slice": [
    {"field": "team$hierarchy$id", "op": "=", "value": "T001"}
  ]
}
```

### Attributes and Measures

Use the returned field name directly:

```json
{
  "columns": ["orderNo", "totalAmount", "quantity"]
}
```

## Filters (`slice`)

### Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `=` | Equal | `{"field": "status", "op": "=", "value": 1}` |
| `!=`, `<>` | Not equal | `{"field": "status", "op": "!=", "value": 0}` |
| `>`, `>=`, `<`, `<=` | Comparison | `{"field": "amount", "op": ">", "value": 100}` |
| `like` | Fuzzy match | `{"field": "name", "op": "like", "value": "Acme"}` |
| `left_like` | Prefix match | `{"field": "code", "op": "left_like", "value": "ORD"}` |
| `right_like` | Suffix match | `{"field": "code", "op": "right_like", "value": "001"}` |
| `in` | In list | `{"field": "type", "op": "in", "value": [1, 2, 3]}` |
| `not in` | Not in list | `{"field": "type", "op": "not in", "value": [0]}` |
| `is null` | Is null | `{"field": "remark", "op": "is null"}` |
| `is not null` | Is not null | `{"field": "remark", "op": "is not null"}` |
| `[]`, `[)`, `()`, `(]` | Range | `{"field": "date", "op": "[)", "value": ["2025-01-01", "2026-01-01"]}` |

### Example

```json
{
  "slice": [
    {"field": "salesDate$id", "op": "[)", "value": ["20250101", "20251231"]},
    {"field": "customer$caption", "op": "like", "value": "Acme"},
    {"field": "customerType", "op": "in", "value": [10, 20, 30]},
    {"field": "remark", "op": "is not null"}
  ]
}
```

## Aggregation

### Inline Aggregations

For simple aggregations, put aggregate expressions directly in `columns`. The engine infers `groupBy` when possible.

```json
{
  "columns": [
    "product$categoryName",
    "sum(salesAmount) as totalSales",
    "count(orderId) as orderCount",
    "avg(unitPrice) as avgPrice"
  ]
}
```

Supported functions include `sum`, `avg`, `count`, `max`, `min`, `group_concat`, `countd`, `stddev_pop`, `stddev_samp`, `var_pop`, and `var_samp`.

Conditional aggregation must use `sum/avg/count(if(condition, valueWhenTrue, valueWhenFalse))`. Do not generate `sum_if`, `count_if`, or SQL `case when`.

### calculatedFields

Use `calculatedFields` for complex expressions, explicit `agg`, window ranking, or expressions that reference other calculated fields.

```json
{
  "calculatedFields": [
    {
      "name": "netAmount",
      "expression": "salesAmount - discountAmount",
      "agg": "SUM"
    }
  ],
  "columns": ["customer$caption", "netAmount"]
}
```

For share-of-total over the current grouping, use the restricted `CALCULATE` form:

```text
SUM(metric) / NULLIF(CALCULATE(SUM(metric), REMOVE(groupByDim)), 0)
```

`CALCULATE` is only for current-group denominator problems. Do not use it for YoY, MoM, cumulative, or rolling-window analysis.

## timeWindow

`timeWindow` expresses YoY, MoM, WoW, year-to-date, month-to-date, and rolling-window analysis. It automatically appends derived columns for each `targetMetrics` item.

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "columns": ["salesDate$id", "salesAmount"],
    "groupBy": ["salesDate$id"],
    "timeWindow": {
      "field": "salesDate$id",
      "grain": "month",
      "comparison": "yoy",
      "value": ["2025-01-01", "2026-01-01"],
      "targetMetrics": ["salesAmount"]
    }
  }
}
```

For `yoy`, `mom`, and `wow`, the engine appends:

- `salesAmount__prior`: prior-period value
- `salesAmount__diff`: difference
- `salesAmount__ratio`: growth ratio

For `ytd`, `mtd`, `rolling_7d`, `rolling_30d`, and `rolling_90d`, the engine appends the corresponding cumulative or rolling suffix. See [Compose Query DSL Manual](../../dataset-model/compose-query/dsl-manual.md#_9-time-window-semantic-shortcut) for the complete contract.

## Pivot

Use `pivot` for cross-tabs, row/column axes, subtotals, grand totals, parent share, and baseline ratios. Do not pass `columns` when `pivot` is enabled.

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "pivot": {
      "rows": ["region$caption"],
      "columns": ["salesDate$month"],
      "metrics": ["salesAmount"],
      "options": {
        "rowSubtotals": true,
        "columnSubtotals": true,
        "grandTotal": true
      },
      "outputFormat": "grid"
    },
    "slice": [
      { "field": "salesDate$id", "op": "[)", "value": ["2025-01-01", "2026-01-01"] }
    ]
  }
}
```

Pivot supports two whitelisted derived metrics:

```json
{
  "pivot": {
    "rows": ["region$caption", "city$caption"],
    "columns": ["salesDate$month"],
    "metrics": [
      "salesAmount",
      {
        "name": "salesParentShare",
        "type": "parentShare",
        "of": "salesAmount",
        "axis": "rows",
        "level": "city$caption",
        "parentLevel": "region$caption"
      },
      {
        "name": "salesVsFirstMonth",
        "type": "baselineRatio",
        "of": "salesAmount",
        "axis": "columns",
        "baseline": "first"
      }
    ],
    "outputFormat": "grid"
  }
}
```

`parentShare` is only for parent share on the rows axis. It cannot be combined with `hierarchyMode=tree`, cascade TopN, or axis-level `having` / `orderBy` / `limit`. `baselineRatio` is only for first/last baseline comparison on the columns axis and has the same tree/cascade/having/orderBy/limit restrictions. When a request exceeds these boundaries, remove the derived metric and return a normal pivot result; do not generate hidden functions. See [Pivot](../../dataset-model/compose-query/pivot.md).

## Sorting

```json
{
  "orderBy": [
    {"field": "totalSales", "dir": "DESC"},
    {"field": "customer$caption", "dir": "ASC"}
  ]
}
```

For aggregated queries, `orderBy` must reference fields or aliases visible in `columns`.

## Pagination

```json
{
  "start": 0,
  "limit": 30,
  "returnTotal": true
}
```

## Complete Examples

### Basic Query

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "columns": ["orderNo", "customer$caption", "salesDate$caption", "totalAmount"],
    "slice": [
      {"field": "salesDate$id", "op": "[)", "value": ["20250101", "20251231"]}
    ],
    "orderBy": [{"field": "salesDate$id", "dir": "DESC"}],
    "start": 0,
    "limit": 30
  }
}
```

### Aggregate Query

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "columns": [
      "salesDate$caption",
      "sum(totalAmount) as totalSales",
      "count(orderId) as orderCount"
    ],
    "slice": [
      {"field": "salesDate$id", "op": "[)", "value": ["20250101", "20251231"]}
    ],
    "orderBy": [{"field": "totalSales", "dir": "DESC"}],
    "start": 0,
    "limit": 50
  }
}
```

### MCP Protocol Call

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
        "columns": ["customer$caption", "sum(totalAmount) as total"],
        "limit": 10
      }
    }
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
      {"customer$caption": "Customer A", "total": 12500.00},
      {"customer$caption": "Customer B", "total": 8900.00}
    ],
    "total": 156,
    "start": 0,
    "limit": 10,
    "pagination": {
      "start": 0,
      "limit": 10,
      "returned": 10,
      "totalCount": 156,
      "hasMore": true,
      "rangeDescription": "Showing 1-10 of 156 records"
    }
  }
}
```

### Large Data Auto-Truncation

When an MCP query returns too much data, the system automatically truncates the result and provides links to the complete dataset, preventing large tables from consuming the LLM context window.

#### Trigger Conditions

- Query source: MCP tool calls only
- Data threshold: cell count (rows x columns) exceeds `10000` by default
- Truncated rows: keep the first `100` rows by default

#### Truncated Response Format

```json
{
  "success": true,
  "data": {
    "items": [
      // truncated to 100 rows
    ],
    "total": 50000,
    "truncationInfo": {
      "truncated": true,
      "originalRowCount": 50000,
      "truncatedRowCount": 100,
      "columnCount": 15,
      "cellCount": 750000,
      "message": "Large dataset (50000 rows x 15 columns = 750000 cells) has been truncated to 100 rows.",
      "viewerUrl": "http://localhost:8080/data-viewer/view/abc123def456",
      "apiUrl": "http://localhost:8080/data-viewer/api/query/abc123def456/data",
      "hint": "Use the links above for the full dataset, or use API pagination with start and limit."
    }
  }
}
```

## Best Practices

1. Use `$caption` for display and `$id` for filtering.
2. Use inline aggregation for simple metrics, such as `sum(amount) as total`.
3. Use `calculatedFields` for complex calculations.
4. Use pagination for large data: set a reasonable `limit`.
5. Add filters to avoid full scans.
6. Use `timeWindow` for YoY, MoM, and rolling analysis; do not hand-write date-window SQL.
7. Use `pivot` for cross-tabs; do not pass `columns` or `timeWindow` at the same time.
8. Use only whitelisted pivot derived metrics: `parentShare` and `baselineRatio`. Do not generate `ROLLUP_TO`, `CELL_AT`, or `AXIS_MEMBER`.

## Next Steps

- [Metadata Tools](./metadata.md) - Get model and field information
- [Compose Query DSL Manual](../../dataset-model/compose-query/dsl-manual.md) - timeWindow, CTE reuse, derived queries, Join, Union
- [Pivot](../../dataset-model/compose-query/pivot.md) - Pivot DSL
- [Natural Language Query](./nl-query.md) - Intelligent data queries
- [Tools Overview](./overview.md) - Return to tools list
