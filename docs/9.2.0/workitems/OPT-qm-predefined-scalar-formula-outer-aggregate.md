---
type: optimization
bug_source: upstream-feedback
version: 9.2.0
ticket: OPT-qm-predefined-scalar-formula-outer-aggregate
severity: major
status: implemented
test_strategy: unit-test + integration-test
automation_decision: required
owner: foggy-dataset-model
created_at: 2026-06-05
updated_at: 2026-06-05
owner_repo: foggy-data-mcp-bridge-wt-dev-compose
owner_module: foggy-dataset-model
---

# OPT: Allow Outer Aggregation For QM Predefined Scalar Formula Fields

## Purpose

Record and track the Java query engine change that lets callers aggregate safe row-level QM predefined calculated fields, while preserving the existing double-aggregation refusal for predefined fields that already contain aggregate semantics.

## Background

TMS upstream needs a direct query summary over `SuggestionOrderInventoryDemandProjectionQuery`:

```json
{
  "columns": [
    "sum(suggestionAvailablePieceCount) as remainingPieceCount",
    "sum(suggestionAvailableWeight) as remainingWeight",
    "sum(suggestionAvailableVolume) as remainingVolume"
  ],
  "slice": [
    { "field": "useType", "op": "in", "value": [100, 101] },
    { "field": "number", "op": ">", "value": 0 },
    { "field": "suggestionAvailablePieceCount", "op": ">", "value": 0 }
  ],
  "extData": {
    "suggestionSheetId": 380
  }
}
```

Current engine behavior rejects the request:

```text
ILLEGAL_DOUBLE_AGGREGATION: Cannot wrap predefined calculated field 'suggestionAvailablePieceCount' in an aggregate function.
```

The referenced fields are row-level scalar formulas equivalent to `max(stock - occupied, 0)`. The required summary semantics are `SUM(row_formula)`, not `max(SUM(stock) - SUM(occupied), 0)`.

## Expected Behavior

- Outer aggregate functions may wrap QM predefined calculated fields when those fields are scalar row-level formulas.
- QM predefined calculated fields that already have explicit `agg` metadata or contain aggregate/window semantics must still be rejected when wrapped by another aggregate.
- The public direct query payload does not change for upstream callers.
- Generated SQL must aggregate the expanded row formula through the existing calculated-field pipeline.

## Non-Goals

- Do not expose raw SQL, CTE, or composeScript as a workaround.
- Do not make all predefined calculated fields aggregatable.
- Do not change formula parsing or function allow-list semantics beyond the aggregate safety decision.
- Do not change QueryModel aggregate join semantics.

## Code Inventory

| Path | Planned Change |
|---|---|
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/InlineExpressionPreprocessStep.java` | Replace the blanket predefined-field aggregate refusal with a scalar-safety check. |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/InlineExpressionPreprocessStepTest.java` | Cover allowed scalar predefined formula aggregation and still-rejected aggregate predefined formula wrapping. |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/AdvancedAnalyticsTest.java` | Add a direct query integration case that aggregates a predefined row-level formula and compares real query output. |

## Implementation Checklist

- [x] Classify predefined calculated fields as aggregate-unsafe when explicit `agg` exists or their formula AST contains aggregate functions.
- [x] Allow `sum(predefinedScalarFormula)` for row-level scalar predefined formulas.
- [x] Preserve `ILLEGAL_DOUBLE_AGGREGATION` for predefined aggregate formulas.
- [x] Add unit tests for both allowed and rejected paths.
- [x] Add or update integration coverage using a real query model path.
- [x] Run targeted tests and record results.
- [x] Prepare commit, push, and `main` merge handoff.

## Verification

Passed on 2026-06-05:

```powershell
mvn -pl foggy-dataset-model "-Dtest=InlineExpressionPreprocessStepTest,AdvancedAnalyticsTest" "-Dspring.profiles.active=sqlite" test
```

Result:

- `AdvancedAnalyticsTest`: 17 tests, 0 failures, 0 errors.
- `InlineExpressionPreprocessStepTest`: 5 tests, 0 failures, 0 errors.
- Total: 22 tests, 0 failures, 0 errors.

Full module regression also passed on 2026-06-05:

```powershell
mvn -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" test
```

Result from surefire reports:

- Total: 3026 tests, 0 failures, 0 errors, 1 skipped.

## Implementation Notes

- `InlineExpressionPreprocessStep` now rejects outer aggregation only when the referenced predefined calculated field is aggregate/window unsafe.
- `PredefinedCalculatedFieldInjector` now discovers predefined formula references inside aliased inline expressions such as `sum(profitRate) as totalProfitRate`.
- `SqlCalculatedFieldProcessor` keeps selected QM measure formulas aggregated in grouped contexts, but leaves injected intermediate scalar formulas row-level so `sum(profitRate)` lowers to `SUM(row_formula)`.

## Changed Files

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/InlineExpressionPreprocessStep.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/PredefinedCalculatedFieldInjector.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/expression/SqlCalculatedFieldProcessor.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/InlineExpressionPreprocessStepTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/AdvancedAnalyticsTest.java`

## Quality Check

Self-check only. The change is narrowly scoped to QM predefined calculated-field injection and calculated-field aggregation lowering, with targeted unit and real query-model integration coverage.

## Experience

N/A. Backend Java query engine behavior only; no frontend or UX surface changed.
