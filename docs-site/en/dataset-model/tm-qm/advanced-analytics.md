# Advanced Analytics

## Overview

foggy-dataset-model supports three levels of analytics capabilities:

| Level | Capability | Examples |
|-------|-----------|----------|
| 1 | Extended Aggregation | COUNT(DISTINCT), STDDEV, VARIANCE |
| 2 | Window Functions | RANK, LAG/LEAD, Moving Average |
| 3 | Composite Patterns | Z-Score, YoY/MoM, RFM, Pareto |

---

## Level 1: Extended Aggregation

### COUNT(DISTINCT) — Distinct Count

```json
{
  "calculatedFields": [
    {"name": "uv", "expression": "COUNTD(customerId)"}
  ],
  "columns": ["product$caption", "uv"],
  "groupBy": ["product$caption"]
}
```

Aliases: `COUNTD()` and `COUNT_DISTINCT()` are equivalent.

### Statistical Functions

| Function | Description | SQLite |
|----------|-------------|--------|
| `STDDEV_POP(x)` | Population standard deviation | Not supported |
| `STDDEV_SAMP(x)` | Sample standard deviation | Not supported |
| `VAR_POP(x)` | Population variance | Not supported |
| `VAR_SAMP(x)` | Sample variance | Not supported |

SQL Server automatically translates to `STDEVP`/`STDEV`/`VARP`/`VAR`.

```json
{
  "calculatedFields": [
    {"name": "salesStd", "expression": "STDDEV_POP(salesAmount)"}
  ],
  "columns": ["product$caption", "salesStd"],
  "groupBy": ["product$caption"]
}
```

### TM Definition

The TM `aggregation` field supports the new types:

```javascript
measures: [
  { column: 'customer_id', name: 'uniqueCustomers', caption: 'Unique Customers',
    type: 'INTEGER', aggregation: 'COUNT_DISTINCT' },
  { column: 'sales_amount', name: 'salesStddev', caption: 'Sales Std Dev',
    type: 'NUMBER', aggregation: 'STDDEV_POP' }
]
```

---

## Level 2: Window Functions

### Supported Window Functions

`ROW_NUMBER`, `RANK`, `DENSE_RANK`, `NTILE`, `LAG`, `LEAD`, `FIRST_VALUE`, `LAST_VALUE`

Aggregate functions (`SUM`/`AVG`/`COUNT`/`MAX`/`MIN`) become window functions when `partitionBy` is specified.

### Structured Definition (Recommended)

```json
{
  "calculatedFields": [
    {
      "name": "salesRank",
      "expression": "RANK()",
      "partitionBy": ["product$categoryName"],
      "windowOrderBy": [{"field": "salesAmount", "dir": "desc"}]
    },
    {
      "name": "ma7",
      "expression": "AVG(salesAmount)",
      "partitionBy": ["product$caption"],
      "windowOrderBy": [{"field": "salesDate$caption", "dir": "asc"}],
      "windowFrame": "ROWS BETWEEN 6 PRECEDING AND CURRENT ROW"
    }
  ],
  "columns": ["product$caption", "salesDate$caption", "salesAmount", "salesRank", "ma7"]
}
```

### Window Function Fields

| Field | Type | Description |
|-------|------|-------------|
| `partitionBy` | `string[]` | PARTITION BY field list |
| `windowOrderBy` | `{field, dir}[]` | ORDER BY sort definition |
| `windowFrame` | `string` | Window frame, e.g. `ROWS BETWEEN 6 PRECEDING AND CURRENT ROW` |

### QM Predefined Window Fields

Define window calculated fields in QM using `formula` + `partitionBy`:

```javascript
columnGroups: [{
  caption: 'Advanced Analytics',
  items: [
    { name: 'profitRate', caption: 'Profit Rate (%)',
      formula: 'profitAmount / salesAmount * 100', type: 'NUMBER' },
    { name: 'salesRank', caption: 'Sales Rank',
      formula: 'RANK()',
      partitionBy: ['product$categoryName'],
      windowOrderBy: [{ field: 'salesAmount', dir: 'desc' }],
      type: 'INTEGER' },
    { name: 'ma7', caption: '7-Day Moving Average',
      formula: 'AVG(salesAmount)',
      partitionBy: ['product$caption'],
      windowOrderBy: [{ field: 'salesDate$caption', dir: 'asc' }],
      windowFrame: 'ROWS BETWEEN 6 PRECEDING AND CURRENT ROW',
      type: 'NUMBER' }
  ]
}]
```

Query by referencing field names directly:

```json
{"columns": ["product$caption", "salesDate$caption", "salesAmount", "salesRank", "ma7"]}
```

### Where to Define

| Scenario | Recommended | Reason |
|----------|------------|--------|
| COUNT_DISTINCT / STDDEV aggregation | TM `aggregation` | Consistent with existing measures |
| Window functions (RANK/LAG/MA) | QM `formula` + `partitionBy` | Uses semantic field names, cross-dimension references |
| Complex calculated fields (profit rate) | QM `formula` | References model field names |

---

## Level 3: Composite Analysis Patterns

The following patterns combine Level 1-2 capabilities.

### Z-Score Outlier Detection

```json
{
  "calculatedFields": [
    {"name": "avgSales", "expression": "AVG(salesAmount)",
     "partitionBy": [], "windowOrderBy": []},
    {"name": "stdSales", "expression": "STDDEV_POP(salesAmount)",
     "partitionBy": [], "windowOrderBy": []},
    {"name": "zScore", "expression": "(salesAmount - avgSales) / stdSales"}
  ],
  "columns": ["product$caption", "salesAmount", "zScore"]
}
```

Data points with Z-Score > 2 or < -2 are considered outliers.

### Year over Year (YoY)

```json
{
  "calculatedFields": [
    {"name": "lastYearSales", "expression": "LAG(salesAmount, 12)",
     "partitionBy": ["product$caption"],
     "windowOrderBy": [{"field": "salesDate$caption", "dir": "asc"}]},
    {"name": "yoyRate", "expression": "(salesAmount - lastYearSales) / lastYearSales * 100"}
  ],
  "columns": ["product$caption", "salesDate$caption", "salesAmount", "yoyRate"]
}
```

### Month over Month (MoM)

```json
{
  "calculatedFields": [
    {"name": "lastMonthSales", "expression": "LAG(salesAmount, 1)",
     "partitionBy": ["product$caption"],
     "windowOrderBy": [{"field": "salesDate$caption", "dir": "asc"}]},
    {"name": "momRate", "expression": "(salesAmount - lastMonthSales) / lastMonthSales * 100"}
  ],
  "columns": ["product$caption", "salesDate$caption", "salesAmount", "momRate"]
}
```

### Top N Leaderboard

```json
{
  "calculatedFields": [
    {"name": "salesRank", "expression": "DENSE_RANK()",
     "partitionBy": ["salesDate$caption"],
     "windowOrderBy": [{"field": "salesAmount", "dir": "desc"}]}
  ],
  "columns": ["salesDate$caption", "product$caption", "salesAmount", "salesRank"],
  "filters": [{"field": "salesRank", "op": "<=", "value": 10}]
}
```

### Moving Average (7-day / 30-day)

```json
{
  "calculatedFields": [
    {"name": "ma7", "expression": "AVG(salesAmount)",
     "partitionBy": ["product$caption"],
     "windowOrderBy": [{"field": "salesDate$caption", "dir": "asc"}],
     "windowFrame": "ROWS BETWEEN 6 PRECEDING AND CURRENT ROW"},
    {"name": "ma30", "expression": "AVG(salesAmount)",
     "partitionBy": ["product$caption"],
     "windowOrderBy": [{"field": "salesDate$caption", "dir": "asc"}],
     "windowFrame": "ROWS BETWEEN 29 PRECEDING AND CURRENT ROW"}
  ],
  "columns": ["product$caption", "salesDate$caption", "salesAmount", "ma7", "ma30"]
}
```

### Pareto ABC Analysis

```json
{
  "calculatedFields": [
    {"name": "totalSales", "expression": "SUM(salesAmount)"},
    {"name": "cumSales", "expression": "SUM(totalSales)",
     "partitionBy": [],
     "windowOrderBy": [{"field": "totalSales", "dir": "desc"}],
     "windowFrame": "ROWS UNBOUNDED PRECEDING"},
    {"name": "grandTotal", "expression": "SUM(totalSales)",
     "partitionBy": [], "windowOrderBy": []},
    {"name": "cumPct", "expression": "cumSales / grandTotal * 100"}
  ],
  "columns": ["product$caption", "totalSales", "cumPct"],
  "groupBy": ["product$caption"]
}
```

cumPct <= 80 → Class A, 80-95 → Class B, >95 → Class C.

### RFM Customer Segmentation

```json
{
  "calculatedFields": [
    {"name": "rScore", "expression": "NTILE(5)",
     "partitionBy": [],
     "windowOrderBy": [{"field": "lastOrderDate", "dir": "desc"}]},
    {"name": "fScore", "expression": "NTILE(5)",
     "partitionBy": [],
     "windowOrderBy": [{"field": "orderCount", "dir": "asc"}]},
    {"name": "mScore", "expression": "NTILE(5)",
     "partitionBy": [],
     "windowOrderBy": [{"field": "totalSpend", "dir": "asc"}]}
  ],
  "columns": ["customer$caption", "rScore", "fScore", "mScore"]
}
```

---

## Database Compatibility

| Capability | MySQL | PostgreSQL | SQL Server | SQLite |
|-----------|-------|-----------|------------|--------|
| COUNT(DISTINCT) | 5.7+ | 12+ | 2012+ | 3.30+ |
| STDDEV/VARIANCE | 5.7+ | 12+ | 2012+ | Not supported |
| Window Functions | 8.0+ | 8.4+ | 2012+ | 3.25+ |
