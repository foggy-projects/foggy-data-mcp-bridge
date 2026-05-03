# Pivot

> **Status**: 9.0 / 9.1 synchronized edition.
> **Entry**: MCP `dataset.query_model` with `payload.pivot`.
> **Use cases**: row/column pivoting, cross-tab analysis, subtotals, grand totals, parent share, baseline ratios.

Pivot is a multi-dimensional analysis DSL for AI queries and frontend pivot tables. When Pivot is enabled, returned fields are described by `payload.pivot` instead of `payload.columns`.

## 1. Minimal Example

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "pivot": {
      "rows": ["region$caption"],
      "columns": ["salesDate$month"],
      "metrics": ["salesAmount"],
      "outputFormat": "grid"
    },
    "slice": [
      { "field": "salesDate$id", "op": "[)", "value": ["2025-01-01", "2026-01-01"] }
    ]
  }
}
```

## 2. Top-Level Mutual Exclusion

`payload.pivot` and normal column queries are different modes:

| Combination | Result |
|-------------|--------|
| `pivot` + `columns` | Not allowed; schema error |
| `pivot` + `timeWindow` | Not allowed; schema error |
| `pivot` + `slice` / `calculatedFields` | Allowed; filter/calculate first, then pivot |
| `pivot` + top-level `orderBy` / `limit` | Not used as pivot axis ordering or TopN; use axis-level controls |

Use [DSL timeWindow](./dsl-manual.md#_9-time-window-semantic-shortcut) for YoY, MoM, and rolling-window analysis. Use Pivot for row/column cross-tab analysis. Do not mix them in one request.

## 3. `pivot` Fields

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| `rows` | array | yes | Row-axis dimensions; each item can be a field name or axis object |
| `columns` | array | no | Column-axis dimensions; each item can be a field name or axis object |
| `metrics` | array | yes | Native metrics and whitelisted derived metrics |
| `properties` | array | no | Extra properties fetched with dimensions; does not change aggregation grain |
| `options` | object | no | Crossjoin completion, subtotals, grand totals |
| `layout` | object | no | Client layout hints |
| `outputFormat` | enum | no | `flat`, `tree`, or `grid`; default `flat` |

## 4. Axis Definition

Short form:

```json
{
  "rows": ["region$caption", "productCategory$caption"],
  "columns": ["salesDate$month"]
}
```

Object form for TopN, sorting, Having, or tree output:

```json
{
  "rows": [
    {
      "field": "region$caption",
      "limit": 10,
      "orderBy": ["salesAmount desc"],
      "having": [
        { "metric": "salesAmount", "op": ">", "value": 100000 }
      ]
    }
  ],
  "columns": [
    { "field": "salesDate$month", "limit": 12 }
  ]
}
```

Axis object fields:

| Field | Description |
|-------|-------------|
| `field` | Dimension field name, required |
| `hierarchyMode` | `flat` or `tree` |
| `expandDepth` | Tree expansion depth |
| `limit` | TopN limit for this axis level |
| `orderBy` | Axis-level sort rules |
| `having` | Post-aggregation filter items `{ metric, op, value }` |

## 5. Metrics and Derived Metrics

Native measures can be written as strings:

```json
{
  "metrics": ["salesAmount", "orderCount"]
}
```

The public 9.0 / 9.1 derived metric whitelist only includes `parentShare` and `baselineRatio`. Arbitrary `expr`, `ROLLUP_TO`, `CELL_AT`, `AXIS_MEMBER`, and similar cell navigation functions are not public.

### 5.1 `parentShare`

`parentShare` calculates the current row value as a share of its parent aggregate value. The public axis is limited to `rows`.

```json
{
  "rows": ["region$caption", "city$caption"],
  "metrics": [
    "salesAmount",
    {
      "name": "salesParentShare",
      "type": "parentShare",
      "of": "salesAmount",
      "axis": "rows",
      "level": "city$caption",
      "parentLevel": "region$caption"
    }
  ],
  "outputFormat": "flat"
}
```

Boundaries:

- `parentShare` only supports adjacent levels on the rows axis.
- `of` must reference a native additive measure in the same `metrics` array.
- It cannot be combined with `hierarchyMode=tree`, cascade TopN, or axis-level `having` / `orderBy` / `limit`.
- If the request exceeds the boundary, remove `parentShare` and return a normal pivot result; do not use `ROLLUP_TO`, `REMOVE(childDim)`, or invented `expr`.

### 5.2 `baselineRatio`

`baselineRatio` calculates the current column value relative to the first or last item on the columns axis.

```json
{
  "rows": ["region$caption"],
  "columns": ["salesDate$month"],
  "metrics": [
    "salesAmount",
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
```

Boundaries:

- `baselineRatio` only supports the columns axis.
- `baseline` must be `first` or `last`, and the columns axis must not be empty.
- `of` must reference a native additive measure.
- It cannot be combined with tree mode, cascade TopN, or axis-level `having` / `orderBy` / `limit`.
- If the request exceeds the boundary, remove `baselineRatio` and return a normal pivot result; do not use `CELL_AT`, `AXIS_MEMBER`, or coordinate indexing.

## 6. Subtotals, Grand Total, and Crossjoin Completion

```json
{
  "pivot": {
    "rows": ["region$caption"],
    "columns": ["salesDate$quarter"],
    "metrics": ["salesAmount"],
    "options": {
      "crossjoin": true,
      "rowSubtotals": true,
      "columnSubtotals": true,
      "grandTotal": true
    },
    "outputFormat": "grid"
  }
}
```

| Option | Description |
|--------|-------------|
| `crossjoin` | Complete the row/column member skeleton |
| `rowSubtotals` | Output row subtotals |
| `columnSubtotals` | Output column subtotals |
| `grandTotal` | Output grand total |

Large-domain transfer and cascade guardrails are implemented in 9.1, but very large dimension domains still fail closed. Narrow `slice`, add axis `limit`, or fall back to paginated detail queries.

## 7. Output Formats

| `outputFormat` | Description |
|----------------|-------------|
| `flat` | Flat records, easy for LLMs to explain |
| `tree` | Nested row-axis hierarchy |
| `grid` | Cross-tab grid for frontend tables |

Layout hint:

```json
{
  "layout": {
    "metricPlacement": "columns"
  }
}
```

`metricPlacement` supports `columns` or `rows`.

## 8. Governance and Limits

- Governance such as `systemSlice`, `fieldAccess`, and `deniedColumns` still applies.
- Pivot mode cannot be combined with `columns`.
- Pivot mode cannot be combined with `timeWindow`.
- Tree output currently focuses on the rows axis.
- Large domains, too-deep hierarchies, and non-pushdown TopN / Having use a fail-closed strategy.

## 9. Capability Selection

| User intent | Recommended capability |
|-------------|------------------------|
| "Show sales YoY by month" | `timeWindow` |
| "Build a cross-tab by region and month" | `pivot` |
| "Show each city share of its region" | `pivot.metrics.parentShare` |
| "Compare each month with the first month" | `pivot.metrics.baselineRatio` |
| "Aggregate, filter TopN, then merge with another result" | Compose Query derived / Join / Union |

## Maintenance

| Date | Change | Notes |
|------|--------|-------|
| 2026-05-03 | Added English synchronized page | Covers 9.0 Pivot DSL and 9.1 large-domain / cascade guardrails |
