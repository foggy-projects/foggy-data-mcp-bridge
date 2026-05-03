# Compose Query DSL Manual

> **Status**: 8.x synchronized edition. Covers `timeWindow`, derived queries, Join, Union, and compiler-managed CTE reuse.
> **Style**: JSON-first DSL through `dsl({...})`, intended for AI generation and declarative analysis workflows.
> **Mirror**: [Chain API Manual](./api-manual.md)
> **Legacy reference**: [query-dsl.md](../tm-qm/query-dsl.md) covers basic single-query syntax and does not cover CTE reuse, derived plans, or time-window semantics.

## 1. When to Use Compose Query

Use Compose Query only when a single `dataset.query_model` request is not enough:

| Scenario | Use Compose Query? | Boundary |
|----------|-------------------|----------|
| Single-model filtering, grouping, aggregation, calculated fields | No | Use `dataset.query_model` |
| Single-model YoY, MoM, YTD, MTD, rolling windows | No | Use `payload.timeWindow` |
| Single-model pivot, subtotal, grand total, parentShare, baselineRatio | No | Use `payload.pivot` |
| Derived query over a previous aggregation | Yes | Use `dsl({ model: prevPlan, ... })` |
| Cross-model Join / Union | Yes | Build each side first, then call `.join()` or `.union()` |
| Return multiple independent results | Yes | Return named plans in the `plans` envelope |

Do not hand-write SQL or CTE text. CTE reuse is an internal compiler optimization.

## 2. Minimal Query

```javascript
const sales = dsl({
  model: "FactSalesQueryModel",
  columns: ["product$id", "product$caption", "sum(salesAmount) as totalSales"],
  slice: [
    { field: "salesDate$id", op: ">=", value: "2025-01-01" }
  ],
  groupBy: ["product$id"],
  orderBy: ["-totalSales"],
  limit: 100
});

return {
  plans: { top_sales: sales },
  metadata: { title: "Top sales by product" }
};
```

`dsl({...})` returns a query plan. It does not execute immediately. The host compiles and executes returned plans.

## 3. Top-Level Fields

| Field | Type | Required | Meaning |
|-------|------|:--------:|---------|
| `model` | string or plan | yes | QM model name, or a previous `QueryPlan` for derived queries |
| `columns` | array | no | Output columns, aggregations, and aliases |
| `slice` | array | no | Filter conditions; supports `$or` / `$and` nesting |
| `groupBy` | array | no | Grouping fields |
| `orderBy` | array | no | Sort rules, such as `"-totalSales"` |
| `calculatedFields` | array | no | Calculated field definitions |
| `timeWindow` | object | no | High-level time-window semantics |
| `limit` | number | no | Row limit |
| `start` | number | no | Pagination offset, default `0` |
| `returnTotal` | boolean | no | Whether to return total count |
| `distinct` | boolean | no | `SELECT DISTINCT`; mutually exclusive with `groupBy` and aggregations |

## 4. Field References

| Scenario | Form | Example |
|----------|------|---------|
| Fact attribute / measure | bare name | `"orderAmount"`, `"salesAmount"` |
| Aggregation | `AGG(field) AS alias` | `"SUM(amount) AS total"` |
| Dimension ID | `dim$id` | `"product$id"` |
| Dimension caption | `dim$caption` | `"product$caption"` |
| Dimension attribute | `dim$attr` | `"customer$customerType"` |
| Nested dimension | `dim.child$attr` | `"product.category$caption"` |

Column object form is also supported for explicit aggregation:

```javascript
dsl({
  model: "FactSalesQueryModel",
  columns: [
    "product$id",
    { field: "salesAmount", agg: "sum", as: "totalSales" },
    { field: "amount", agg: "avg", as: "avgPrice" }
  ],
  groupBy: ["product$id"]
});
```

Plain alias `{ field, as }` is intended for base fields. Dimension suffix fields containing `$` should be used directly instead of renamed through plain alias.

## 5. Filters, Sorting, and Pagination

```javascript
slice: [
  { field: "status", op: "=", value: "ACTIVE" },
  { $or: [
    { field: "amount", op: ">=", value: 1000 },
    { field: "customer$customerType", op: "=", value: "VIP" }
  ]}
],
orderBy: ["-amount", "orderNo"],
start: 0,
limit: 20
```

Supported operators include `=`, `!=`, `<>`, `>`, `>=`, `<`, `<=`, `in`, `not in`, `is null`, `is not null`, `[]`, `[)`, `()`, `(]`, `like`, `left_like`, `right_like`, and parent-child hierarchy operators such as `descendantsOf`.

## 6. Calculated Fields

Define calculated fields in top-level `calculatedFields`, then reference them like normal fields:

```javascript
dsl({
  model: "FactSalesQueryModel",
  calculatedFields: [
    { name: "profitRate", expression: "profit / sales * 100", agg: "SUM" },
    { name: "netAmount", expression: "salesAmount - returnAmount" }
  ],
  columns: ["product$id", "profitRate", "netAmount"],
  slice: [{ field: "profitRate", op: ">", value: 20 }],
  groupBy: ["product$id"]
});
```

In a `timeWindow` query, execution order is fixed:

1. Expand the time window from base metrics and `targetMetrics`.
2. Apply post-window scalar `calculatedFields`.

Post-window calculated fields may reference generated columns such as `salesAmount__prior`, `salesAmount__diff`, and `salesAmount__ratio`, but must not declare `agg`, `partitionBy`, `windowOrderBy`, or `windowFrame`.

## 7. Derived Queries

Use a previous plan as the `model`:

```javascript
const base = dsl({
  model: "FactSalesQueryModel",
  columns: ["product$id", "sum(salesAmount) as totalSales"],
  groupBy: ["product$id"]
});

const filtered = dsl({
  model: base,
  columns: ["product$id", "totalSales"],
  slice: [{ field: "totalSales", op: ">", value: 100000 }],
  orderBy: ["-totalSales"]
});
```

Derived queries can only reference columns projected by the previous plan. They cannot skip the previous plan and reference hidden base-model fields.

## 8. Join and Union

Join is exposed as a plan composition method:

```javascript
const salesByProduct = dsl({
  model: "FactSalesQueryModel",
  columns: ["product$id", "sum(salesAmount) as totalSales"],
  groupBy: ["product$id"]
});

const returnsByProduct = dsl({
  model: "FactReturnQueryModel",
  columns: ["product$id", "sum(returnAmount) as totalReturns"],
  groupBy: ["product$id"]
});

const joined = salesByProduct.join(returnsByProduct, "inner", [
  { left: "product$id", op: "=", right: "product$id" }
]);
```

Join boundaries:

- Supported types: `inner`, `left`, `right`, `full` where the SQL dialect supports them.
- Join conditions are equality conditions and may include multiple keys.
- Cross-datasource Join is not part of the public contract.
- Resolve same-name columns upstream with aliases.

Union is also a plan composition method:

```javascript
const allOrders = online.union(offline, { all: true });
```

Union aligns by the left-side schema. Branches must expose the same column count and compatible types.

## 9. Time Window Semantic Shortcut

```javascript
dsl({
  model: "FactSalesQueryModel",
  columns: ["salesDate$id", "salesAmount"],
  groupBy: ["salesDate$id"],
  timeWindow: {
    field: "salesDate$id",
    grain: "month",
    comparison: "yoy",
    value: ["2024-01-01", "2025-01-01"],
    targetMetrics: ["salesAmount"]
  }
});
```

| Field | Type | Required | Meaning |
|-------|------|:--------:|---------|
| `field` | string | yes | Time axis field, preferably a dimension `$id` marked `timeRole=business_date` |
| `grain` | enum | yes | `day`, `week`, `month`, `quarter`, `year` |
| `comparison` | enum | yes | `yoy`, `mom`, `wow`, `ytd`, `mtd`, `rolling_7d`, `rolling_30d`, `rolling_90d` |
| `value` | `[string, string]` | no | Current period `[start, end]`; both values must be valid dates or relative expressions |
| `range` | enum | no | `"[)"` by default; `"[)"` and `"[]"` are supported |
| `targetMetrics` | string[] | no | Metrics to expand; empty means all metrics |
| `rollingAggregator` | enum | no | `sum`, `avg`, `count`, `min`, `max`; default `sum` |

Compatibility matrix:

| comparison | day | week | month | quarter | year |
|------------|-----|------|-------|---------|------|
| `yoy` | no | yes | yes | yes | yes |
| `mom` | no | no | yes | no | no |
| `wow` | yes | yes | no | no | no |
| `ytd` | yes | yes | yes | yes | no |
| `mtd` | yes | no | no | no | no |
| `rolling_7d` | yes | no | no | no | no |
| `rolling_30d` | yes | no | no | no | no |
| `rolling_90d` | yes | yes | no | no | no |

Generated suffixes:

| Suffix | Meaning | comparison |
|--------|---------|------------|
| no suffix | current value | all |
| `__prior` | prior value | `yoy`, `mom`, `wow` |
| `__diff` | current minus prior | `yoy`, `mom`, `wow` |
| `__ratio` | growth ratio | `yoy`, `mom`, `wow` |
| `__ytd` | year-to-date value | `ytd` |
| `__mtd` | month-to-date value | `mtd` |
| `__rolling_{N}{unit}` | rolling value | rolling modes |

## 10. CTE Reuse

CTE is a compiler optimization, not user syntax. When the same `QueryPlan` is referenced by multiple downstream plans, the compiler may lift it into a common CTE on dialects that support `WITH ... AS (...)`; otherwise it falls back to derived subqueries.

```javascript
const productSales = dsl({
  model: "FactSalesQueryModel",
  columns: ["product$id", "sum(salesAmount) as totalSales"],
  groupBy: ["product$id"]
});

const highValue = dsl({
  model: productSales,
  columns: ["product$id", "totalSales"],
  slice: [{ field: "totalSales", op: ">", value: 100000 }]
});

const ranked = dsl({
  model: productSales,
  columns: ["product$id", "totalSales"],
  orderBy: ["-totalSales"],
  limit: 20
});
```

Public boundaries:

- Users do not assign CTE names.
- Top-level plan names are result-map names, not SQL CTE names.
- Recursive CTE is not supported.
- Arbitrary SQL text cannot be injected as a CTE.

## Maintenance

| Date | Change | Notes |
|------|--------|-------|
| 2026-05-03 | Added English synchronized page | Covers the 8.x public contract for timeWindow, derived queries, Join, Union, and CTE reuse |
