---
doc_role: bug
doc_purpose: Record and close GitHub issue #118 for raw measure-only query validation.
version: v3.0
ticket: BUG-github-118-raw-measure-selection-validation
github_issue: https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/118
bug_source: user-report
severity: major
status: fixed-verified
owner: runtime-query-validation
created_at: 2026-07-08
updated_at: 2026-07-24
---

# BUG: Raw Measure Selection Without Aggregate Validation

## Summary

GitHub issue #118 reported that payloads selecting only measure fields without
`groupBy` or explicit aggregate expressions validate and execute as detail
queries. This can return plausible first-row values when the user intended a
metric summary.

## Reproduction

Issue payload shape:

```json
{
  "limit": 1,
  "columns": [
    "saleLineCount",
    "totalIncludingTax",
    "totalExcludingTax",
    "profit",
    "quantity"
  ],
  "slice": [
    { "field": "invoiceDate$id", "op": "[)", "value": ["2016-01-01", "2016-06-01"] }
  ]
}
```

The affected query model declares the selected fields as measures. With no
`groupBy` and no explicit expressions such as `sum(totalIncludingTax) as
totalIncludingTax`, the runtime generated a detail SQL query and returned a
single row instead of a summary.

## Expected Behavior

Validation should fail or warn before execution when all selected columns are
raw measure fields and the request has no `groupBy` or explicit aggregate
expressions. The diagnostic should tell callers to either:

- use explicit aggregates such as `sum(amount) as amount`; or
- include a detail dimension or id column when a detail query is intended.

## Fix

- Added semantic validation warnings for measure-only raw column selection
  without `groupBy`.
- Kept detail-query behavior valid when a non-measure anchor column is selected,
  for example `orderId` plus `amount`.
- Kept explicit aggregate expressions valid without `groupBy`.
- Mapped the validation warning through Runtime API as
  `AMBIGUOUS_MEASURE_SELECTION` on `query.validate`, with a repair suggestion
  and `safeToAutoRepair=true`.

## Regression Coverage

| Command | Result | Notes |
|---|---|---|
| `mvn -pl foggy-dataset-model -Dtest=SemanticQueryServiceV3ValidatePipelineTest test` | passed | Covers raw measure warning, detail-anchor opt-out, and explicit aggregate opt-out. |
| `mvn -pl foggy-runtime-api -Dtest=RuntimeCapabilitiesControllerEnabledTest test` | passed | Covers Runtime API mapping from validation warning to query error envelope. |

Current-main re-verification on 2026-07-24:

- `SemanticQueryServiceV3ValidatePipelineTest`: `4/F0E0S0`
- `RuntimeCapabilitiesControllerEnabledTest`: `48/F0E0S0`
- GitHub issue #118: closed

## Residual Risk

This fix intentionally warns/fails validation instead of changing execution to
auto-aggregate raw measure fields. That avoids a semantic behavior change in the
query engine, but callers still need to repair payloads by emitting explicit
aggregate expressions.
