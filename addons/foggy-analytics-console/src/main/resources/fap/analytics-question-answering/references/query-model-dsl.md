# Query-model DSL

Use `foggy.analytics.query-model.run@v1` for one exact model. Its arguments contain the
server-bound `namespace`, described `modelName`, exact `expectedModelRevision`, `mode`, and the
DSL object under `payload`.

## Construction rules

- Use `columns`, not `fields`.
- Use only fields and metrics returned by model describe.
- A measure returned by model describe is already a canonical semantic expression. Select its
  described name directly, for example `"amount"`; do not rewrite it as `sum(amount)` merely
  because its metadata says the aggregation type is SUM.
- Distinguish detail from aggregation before building the payload. For detail rows, rankings,
  highest/lowest records, or record lookup, select described dimensions and measures directly and
  omit `groupBy`. Add an aggregate expression and `groupBy` only when the user asks for a grouped
  or summarized result and that expression is supported by model metadata or engine validation.
- Never put authority, permission, datasource, raw SQL, hints, tenant or caller identity in the
  payload. The Java Runtime resolves current authority server-side.
- Every selected non-aggregate field in an aggregate query belongs in `groupBy`.
- Put source-row filters in `slice`; use `having` or a derived Compose query for aggregate-result
  filters.
- Use the described business date field for date ranges; do not invent SQL date functions.
- Keep exploratory detail queries bounded with an explicit `limit`.
- Send the identical payload through `validate` before `execute`.

Supported top-level payload fields are `columns`, `slice`, `having`, `groupBy`, `orderBy`,
`start`, `limit`, `returnTotal`, `distinct`, `calculatedFields`, `withSubtotals`, `timeWindow`,
`pivot`, `route`, `executable_plan`, and `executablePlan`. Nested shapes are governed by the
published Function schema and engine validation.

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

## Pivot

- Do not mix `pivot` with top-level `columns`.
- Do not mix `pivot` with `timeWindow` in one request.
- Use only described axes and metrics.
- Bound high-cardinality axes with filters or axis limits before execution.

Common invalid repairs include replacing raw `where` or SQL functions with `slice`, correcting a
field to the exact described name, selecting a canonical described measure directly instead of
wrapping it in another aggregate, moving a true aggregate filter to `having`, adding missing
grouping fields to a true aggregate query, or reducing `limit`. Never repair a denial by removing
authority or switching models.
