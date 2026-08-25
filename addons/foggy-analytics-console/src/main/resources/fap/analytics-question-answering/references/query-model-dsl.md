# Query-model DSL

Use `foggy.analytics.query-model.run@v2` for one model. Its arguments contain the server-bound
`namespace`, described `modelName`, `mode`, and the DSL object under `payload`. The provider
resolves the current valid model once per invocation and keeps that catalog resolution fixed
through validation or execution.

## Construction rules

- Use `columns`, not `fields`.
- Use only fields and metrics returned by model describe.
- Distinguish detail from aggregation before building the payload. For detail rows, rankings,
  highest/lowest records, or record lookup, select described dimensions and measures directly and
  omit `groupBy`. When the user asks for a total or grouped summary, apply the aggregation shown by
  describe to the exact measure field, for example `sum(amount) as totalAmount`. Count a described
  non-null identifier, for example `count(orderId) as orderCount`; do not invent `count(*)` for the
  standard DSL. Never double-aggregate an already aggregated calculated expression.
- Never put authority, permission, datasource, raw SQL, hints, tenant or caller identity in the
  payload. The Java Runtime resolves current authority server-side.
- Every selected non-aggregate field in an aggregate query belongs in `groupBy`.
- Put source-row filters in `slice`; use `having` or a derived Compose query for aggregate-result
  filters.
- Use the described business date field for date ranges; do not invent SQL date functions.
- Keep exploratory detail queries bounded with an explicit `limit`.
- Send the identical payload through `validate` before `execute`.
- Treat a repairable validation result as an instruction to correct and revalidate the payload.
  Use `violations[].messageKey`; do not execute the rejected payload or stop after the first
  repairable parameter failure.

Supported top-level payload fields are `columns`, `slice`, `having`, `groupBy`, `orderBy`,
`start`, `limit`, `returnTotal`, `distinct`, `calculatedFields`, `withSubtotals`, `timeWindow`,
`pivot`, `route`, `executable_plan`, and `executablePlan`. Standard DSL nested shapes are governed
by the published Function schema and engine validation. `executable_plan` is an open schema object:
its allowed controlled recipes are documented below, while graph-order and expression-signature
rules are enforced dynamically by Runtime validation.

The published `foggy.analytics.query-model.run@v2` input schema is the machine-readable contract
for the standard DSL properties. Do not infer a standard property that is absent from it. For
controlled CTE, use only the documented recipe below. In particular:

- `groupBy` accepts a field string or `{ "field": "...", "agg": "..." }`; strings are the
  preferred public shorthand.
- `orderBy` accepts `"field"`, `"-field"`, `"field desc"`, or an object containing `field`,
  `dir`, and optional `nullFirst` / `nullLast`.
- A standard `slice` / `having` item is `{field, op, value}`. Use `$or` / `$and` arrays for
  explicit Boolean groups, `{ "$expr": "a > b * 1.2" }` for governed field comparison, and
  `maxDepth` only with a described parent/child hierarchy operator. The legacy `{fieldName:
  value}` equality shorthand is accepted for compatibility but must not be generated.
- Common filter operators are `=`, `!=`, `<>`, `===`, `>`, `>=`, `<`, `<=`, `like`, `not like`,
  `left_like`, `right_like`, `in`, `not in`, `bit_in`, `is null`, `is not null`, `[]`, `[)`, `()`,
  `(]`, and the described hierarchy/vector operators. The published schema deliberately accepts a
  non-blank operator string because the Runtime formula registry is authoritative. A field
  reference value is `{ "$field": "other" }`.
- `calculatedFields` items require `name` and `expression`; optional fields are `agg`,
  `partitionBy`, `windowOrderBy`, and `windowFrame`. Allowed `agg` values come from the Function
  schema. Expressions are Foggy expression DSL, not database SQL.

## Detail query

```json
{
  "columns": ["stationId", "stationName", "stationCode"],
  "slice": [
    {"field": "stationCode", "op": "in", "value": ["T011250", "T011264"]}
  ],
  "orderBy": ["stationCode"],
  "limit": 50
}
```

For example, if describe returns `orderId` and the measure `amount`, “top three orders by amount”
is a detail ranking, not a grouped aggregation:

```json
{
  "columns": ["orderId", "amount"],
  "orderBy": ["-amount"],
  "limit": 3,
  "returnTotal": true
}
```

## Grouped aggregation

```json
{
  "columns": [
    "customer$id",
    "customer$caption",
    "sum(orderAmount) as totalAmount"
  ],
  "groupBy": ["customer$id", "customer$caption"],
  "orderBy": ["-totalAmount"],
  "limit": 20,
  "returnTotal": true
}
```

## Overall total and row count

An overall total and record count does not require `DSL_CTE`, `route`, `executable_plan`, or
`groupBy`:

```json
{
  "columns": [
    "sum(amount) as totalAmount",
    "count(orderId) as orderCount"
  ],
  "limit": 1
}
```

## Date range

Use a half-open range for a closed calendar period:

```json
{
  "columns": ["sum(orderAmount) as totalAmount"],
  "slice": [
    {
      "field": "businessDate$id",
      "op": "[)",
      "value": ["2026-05-01", "2026-06-01"]
    }
  ],
  "limit": 1
}
```

## Time window

Use `timeWindow` only for an explicit comparison, accumulated or rolling metric. Include the
time axis and base metric as well as the derived metric.

```json
{
  "columns": ["businessDate$id", "rpValue", "rpValue__mtd"],
  "groupBy": ["businessDate$id"],
  "timeWindow": {
    "field": "businessDate$id",
    "grain": "day",
    "comparison": "mtd",
    "targetMetrics": ["rpValue"]
  }
}
```

`grain` is one of `day`, `week`, `month`, `quarter`, `year`. `comparison` is one of `yoy`,
`mom`, `wow`, `ytd`, `mtd`, `rolling_7d`, `rolling_30d`, `rolling_90d`. Optional `value` must
contain exactly two date/relative-bound strings; `range` is `[)` or `[]`; `rollingAggregator` is
`sum`, `avg`, `count`, `min`, or `max`. Derived names are `{metric}__prior`, `__diff`, `__ratio`,
`__ytd`, `__mtd`, or the selected `__rolling_*` suffix. `targetMetrics` cannot name a
`calculatedFields` result.

## Controlled DSL_CTE

Use `route: "DSL_CTE"` plus `executable_plan.cte_plan.stages` only for a signed, controlled
single-model recipe such as row-level SLA duration/hit flags followed by aggregation and a
NULL-safe result-stage rate. Do not place raw SQL, arbitrary database functions, identity, policy,
datasource, or routing values in the plan.

Stage references are strict:

- the first stage must declare `input: {"model": "<the exact described model>"}` and must not use
  `inputs`;
- every later `inputs` entry must equal the `name` of a stage declared earlier in the array;
- `source`, `input`, a table name, or a model name is not an implicit stage and must never appear
  in `inputs` unless an earlier stage has exactly that name;
- `DSL_CTE_STAGE_REFERENCE_INVALID` means the stage graph is invalid. Repair the references and
  call `mode=validate` again; do not execute the rejected plan.

The currently documented SLA signatures are:

- `hours_between(createdAt, firstResponseAt|resolvedAt)` for natural-hour duration;
- `firstResponseAt is not null and firstResponseHours <= N` or a documented
  `priority_threshold(priority, P1=..., P2=..., P3=...)` hit rule;
- `firstResponseAt is null and createdAt < '<cutoff>'` or a documented `hours_between` overdue
  rule;
- `sum(flag)`, `sum(case when flag then 1 else 0 end)`, `count(*)`, subtraction for an explicitly
  named miss count, and `hitCount / ticketCount` for a NULL-safe rate.

Business-hours, contract-calendar, paused-time, held-time, or customer-wait exclusions are not
signed. Ask for the missing calendar/policy semantics and stop; never invent functions for them.

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

## Pivot

- Do not mix `pivot` with top-level `columns`.
- Do not mix `pivot` with `timeWindow` in one request.
- Use only described axes and metrics.
- Bound high-cardinality axes with filters or axis limits before execution.

`pivot.rows` and `pivot.columns` accept a field string or an axis object with `field`, optional
`orderBy`, `limit`, `having`, `hierarchyMode`, and `expandDepth`. Tree hierarchy belongs only on
the rows axis and requires `outputFormat=tree`; it cannot be combined with crossjoin or totals.
Axis `having` items are `{metric, op, value}` and apply after aggregation, before TopN.

`pivot.metrics` accepts described native measure strings and two controlled derived objects:

```json
[
  "salesAmount",
  {"name": "categoryShare", "type": "parentShare", "of": "salesAmount", "axis": "rows"},
  {"name": "salesIndex", "type": "baselineRatio", "of": "salesAmount", "axis": "columns", "baseline": "first"}
]
```

`parentShare` is limited to adjacent rows levels and an additive native `of` measure.
`baselineRatio` requires a non-empty columns axis and `baseline=first|last`. Neither derived
metric may participate in axis having/orderBy/limit or tree/cascade combinations. Do not replace
them with `ROLLUP_TO`, `CELL_AT`, `AXIS_MEMBER`, `AXIS_REF`, coordinate lookup, or an `expr`.

`pivot.options` contains `crossjoin`, `rowSubtotals`, `columnSubtotals`, and `grandTotal`.
`columnSubtotals` is currently unsupported and must be omitted. Ordinary pivot supports
`grandTotal`; two-level rows cascade can support `rowSubtotals`/`grandTotal` for additive
SUM/COUNT metrics. `metricPlacement` is `columns` or `rows`; `outputFormat` is `flat`, `tree`, or
`grid`.

Common invalid repairs include replacing raw `where` or SQL functions with `slice`, correcting a
field to the exact described name, selecting a described measure directly for detail/ranking,
applying its advertised aggregation only for totals or grouped summaries, moving a true aggregate
filter to `having`, adding missing grouping fields to a true aggregate query, or reducing `limit`.
Never repair a denial by removing authority or switching models.
