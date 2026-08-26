---
name: analytics-question-answering
description: Answer Analytics Console questions with the complete governed Foggy query-model DSL and restricted Compose/CTE tools.
---

# Analytics question answering

Work only in the Namespace frozen by the current Analytics Console Task. The available
Functions are read-only semantic operations; their presence is not permission to access another
Namespace, inject identity or policy fields, or bypass a model.

## Workflow

1. If no model is known, call `foggy.analytics.model-dependencies.list@v3` for the bound
   Namespace. Never claim that models cannot be listed before trying this Function.
2. Call `foggy.analytics.semantic-models.describe@v2` with the selected model. The provider
   resolves the current valid model for each invocation. Use only fields, measures, captions,
   dates and relationships in that description.
   For a detail/ranking question, select and order by the described measure name directly and do
   not add aggregation or `groupBy`. When the user explicitly asks for a total, count, grouped
   contribution or other summary, use the aggregation advertised by describe, for example
   `sum(amount) as totalAmount`, and count a described identifier such as
   `count(orderId) as orderCount`. Never invent a field or aggregate function absent from describe.
3. Prefer `foggy.analytics.query-model.run@v2` for one-model detail, filters, grouping,
   aggregation, calculated fields, time windows, pivot, subtotal or controlled DSL_CTE plans.
   Its published input schema is the complete machine-readable contract for the standard DSL and
   is requested as INLINE delivery. If the Runtime reports `effectiveDelivery=ON_DEMAND` (or
   `delivery=ON_DEMAND`) because of a prompt budget, call
   `describe_business_function` with the `INPUT` or `FULL` view before invoking it. Do not guess
   nested `slice`, `having`, `groupBy`, `orderBy`, `timeWindow` or `pivot` shapes from a summary.
   The `executable_plan` object is intentionally governed by the controlled recipe below plus
   Runtime validation.
   A total/count or ordinary grouped summary is standard DSL: use `columns` and, only when needed,
   `groupBy`; omit `route` and `executable_plan`. Choose `DSL_CTE` only for a documented staged
   recipe that cannot be expressed by the standard fields. In a DSL_CTE plan, the first stage uses
   `input: {"model": "<exact described model>"}`; later `inputs` values may reference only prior
   stage names. Never invent an implicit `source` stage.
   Call it with `mode=validate` first and then `mode=execute` with the same payload when
   validation succeeds. If validation returns repairable violations, use each stable
   `messageKey`, correct the payload, and validate again. Never execute a payload that failed
   validation or end the turn after the first repairable parameter error.
4. Use `foggy.analytics.compose.run@v1` only when one query-model request is insufficient:
   cross-model joins, union, a derived query over prior output, or multiple named result plans.
   Validate first; use preview when SQL/plan evidence is sufficient; execute only when rows are
   needed.
5. Answer only from Function evidence. Preserve totals, truncation, `hasMore`, warnings and
   validation failures. Ask a concise clarification when model metadata cannot disambiguate the
   request.

The smaller `foggy.analytics.semantic-queries.execute@v2` remains available for its typed
subset, but do not fall back to it merely because the full DSL requires validation.

## Query-model DSL contract

Call `query-model.run@v2` with only `namespace`, `modelName`, `mode`, and `payload`. The provider
resolves the current valid model once and keeps the same CatalogResolution throughout that
invocation.

The Function input schema is authoritative for every standard nested shape. Its payload fields
are `columns`, `slice`, `having`, `groupBy`, `orderBy`, `start`, `limit`, `returnTotal`,
`distinct`, `calculatedFields`, `withSubtotals`, `timeWindow`, `pivot`, `route`,
`executable_plan`, and the compatibility spelling `executablePlan`.

- `columns` is an array of described field names or aggregate expressions. A record lookup or
  ranking is a detail query: select a described measure such as `amount` directly, omit
  `groupBy`, and sort it. For a total or grouped summary, apply the aggregation advertised by
  describe, such as `sum(amount) as totalAmount`. Count a described non-null identifier, such as
  `count(orderId) as orderCount`; do not invent `count(*)` in standard DSL.
- `slice` filters source rows; `having` filters an aggregate result. A normal item is
  `{ "field": "...", "op": "...", "value": ... }`. Explicit Boolean groups use `$or` or
  `$and` arrays; governed field comparisons use `{ "$expr": "a > b * 1.2" }`; a field-reference
  value is `{ "$field": "otherField" }`. `maxDepth` is only for a described hierarchy operator.
  Common operators are `=`, `!=`, `<>`, `===`, `>`, `>=`, `<`, `<=`, `like`, `not like`,
  `left_like`, `right_like`, `in`, `not in`, `is null`, `is not null`, `[]`, `[)`, `()`, `(]`,
  and described hierarchy/vector operators. Do not generate the legacy `{fieldName: value}`
  shorthand.
- `groupBy` accepts a field string or `{ "field": "...", "agg": "..." }`. Its aggregate enum is
  `MAX`, `MIN`, `SUM`, `AVG`, `COUNT`, `COUNT_DISTINCT`, `STDDEV_POP`, `STDDEV_SAMP`, `VAR_POP`,
  `VAR_SAMP`, or `PK`. Every selected non-aggregate field in an aggregate query belongs in
  `groupBy`.
- `orderBy` accepts `"field"`, `"-field"`, `"field desc"`, or an object with `field`, `dir`, and
  optional `nullFirst` / `nullLast`. Use `start`, `limit`, and `returnTotal` for ordinary result
  paging. `distinct` is for unique detail values and is mutually exclusive with `groupBy`.
- A `calculatedFields` item requires `name` and `expression`; it may use `agg`, `partitionBy`,
  `windowOrderBy: [{field, dir}]`, and `windowFrame`. Expressions are Foggy expression DSL, not
  database SQL. Use `timeWindow`, not a hand-written window calculation, for period comparison,
  accumulation, or rolling analysis.
- `timeWindow` requires `field`, `grain`, and `comparison`. Grain is `day`, `week`, `month`,
  `quarter`, or `year`; comparison is `yoy`, `mom`, `wow`, `ytd`, `mtd`, `rolling_7d`,
  `rolling_30d`, or `rolling_90d`. Optional `value` is exactly two date/relative-bound strings;
  `range` is `[)` or `[]`; `targetMetrics` names described base measures; and
  `rollingAggregator` is `sum`, `avg`, `count`, `min`, or `max`.
- `pivot` cannot coexist with top-level `columns` or `timeWindow`. It requires `rows` and
  `metrics`; optional members are `columns`, `properties`, `options`, `layout`, and
  `outputFormat`. Axis entries are field strings or objects with `field`, `hierarchyMode`,
  `expandDepth`, `limit`, `orderBy`, and `having: [{metric, op, value}]`. Derived metrics are only
  `parentShare` on the rows axis and `baselineRatio` on the columns axis. Do not invent `expr`,
  `ROLLUP_TO`, `CELL_AT`, `AXIS_MEMBER`, or `AXIS_REF`. Options are `crossjoin`, `rowSubtotals`,
  `columnSubtotals`, and `grandTotal`; `columnSubtotals` is currently unsupported. Bound
  high-cardinality axes before execution.

A detail ranking and an overall total/count look like this:

```json
{"columns":["orderId","amount"],"orderBy":["-amount"],"limit":3,"returnTotal":true}
```

```json
{"columns":["sum(amount) as totalAmount","count(orderId) as orderCount"],"limit":1}
```

A grouped calendar-period query uses a half-open date range:

```json
{
  "columns": ["customer$ageGroup", "sum(payAmount) as totalPayAmount"],
  "groupBy": ["customer$ageGroup"],
  "slice": [
    {"field": "orderDate$id", "op": "[)", "value": ["20240101", "20240401"]}
  ],
  "orderBy": ["-totalPayAmount"],
  "limit": 10
}
```

## Controlled DSL_CTE

An ordinary total, count, filter, ranking, or grouped summary must omit `route` and
`executable_plan`. Use `route: "DSL_CTE"` only for the controlled single-model recipe that needs
row-level SLA duration/hit flags followed by aggregation and a NULL-safe result-stage rate.

The stage graph is strict:

- The first stage has `input: {"model": "<exact described model>"}` and no `inputs`.
- Every later `inputs` entry must name a stage declared earlier in the array. There is no implicit
  `source`, `input`, table-name, or model-name stage.
- `DSL_CTE_STAGE_REFERENCE_INVALID` is repairable only by correcting the graph and validating
  again.
- Allowed SLA expressions are `hours_between(createdAt, firstResponseAt|resolvedAt)`, documented
  threshold comparisons or `priority_threshold(...)`, documented overdue rules, `sum(flag)`,
  `sum(case when flag then 1 else 0 end)`, `count(*)`, subtraction for a named miss count, and
  `hitCount / ticketCount` for a NULL-safe rate. Business-calendar, pause/hold/customer-wait
  semantics are not signed; ask for them rather than inventing a function.

```json
{
  "route": "DSL_CTE",
  "executable_plan": {
    "cte_plan": {
      "stages": [
        {
          "name": "ticket_scope",
          "type": "derive",
          "input": {"model": "ServiceTicketQueryModel"},
          "filters": [{"field": "createdAt", "op": "[)", "value": ["2026-05-01", "2026-06-01"]}],
          "derived": [
            {"name": "firstResponseHours", "expr": "hours_between(createdAt, firstResponseAt)"},
            {"name": "slaHit", "expr": "firstResponseAt is not null and firstResponseHours <= 48"}
          ]
        },
        {
          "name": "team_sla",
          "type": "aggregate",
          "inputs": ["ticket_scope"],
          "groupBy": ["team$caption"],
          "metrics": [
            {"name": "ticketCount", "expr": "count(*)"},
            {"name": "slaHitCount", "expr": "sum(slaHit)"}
          ]
        },
        {
          "name": "team_sla_rate",
          "type": "derive",
          "inputs": ["team_sla"],
          "derived": [{"name": "slaAchievementRate", "expr": "slaHitCount / ticketCount"}]
        }
      ],
      "output": ["team$caption", "ticketCount", "slaHitCount", "slaAchievementRate"]
    }
  }
}
```

## Restricted Compose

Use `compose.run@v1` only for cross-model joins, compatible unions, a derived query over an
earlier plan, or multiple named outputs. Describe every model first. A base plan is `dsl({...})`;
a derived plan is `plan.query({...})`; composition uses `.join(...)` or `.union(...)`. Return an
envelope such as `return { plans: result };` and never call `.execute()` directly.

```fsscript
const customers = dsl({
  model: "CustomerQM",
  columns: ["id as customer_id", "name as customer_name"]
});
const orders = dsl({
  model: "OrderQM",
  columns: ["customerId as order_customer_id", "sum(amount) as total_amount"],
  groupBy: ["customerId"]
});
const joined = customers.join(orders, "left", [
  { left: "customer_id", op: "=", right: "order_customer_id" }
]);
return { plans: joined.query({
  columns: ["customer_id", "customer_name", "total_amount"],
  orderBy: ["-total_amount"],
  limit: 20
}) };
```

Supported join types are `inner`, `left`, `right`, and `full`; conditions are AND-only and may
reference only exposed columns. Union inputs must expose compatible columns; use
`online.union(offline, { all: true })`. A Compose base `dsl({...})` supports `model`, `columns`,
`slice`, `having`, `groupBy`, `orderBy`, `start`, `limit`, `distinct`, `calculatedFields`, and
`timeWindow`. Keep a single-model time window in `query-model.run`; use it in Compose only when its
output feeds a derived query, join/union, or multiple outputs. Prefer lower-case snake_case aliases
for join keys and later sort fields.

## Closed boundaries

Never use raw SQL, standalone FSScript, filesystem, network, Java/Python host access, private
Runtime endpoints, credentials, datasource selectors, tenant/employee/organization identifiers,
authority objects, security filters, or mutation Functions. Do not create or modify Report,
Dashboard, Bundle, model, or business data. If the semantic surface cannot express a request,
state that limitation instead of weakening these boundaries.
