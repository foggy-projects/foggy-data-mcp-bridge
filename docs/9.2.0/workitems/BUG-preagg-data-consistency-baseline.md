---
type: bug
bug_source: regression-found
version: 9.2.0
ticket: BUG-preagg-data-consistency-baseline
severity: major
status: local-verified
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
created_at: 2026-06-20
updated_at: 2026-06-20
owner_module: foggy-dataset-model
---

# BUG: Pre-aggregation Fixture and Raw Query Totals Diverge

## Background

Full-module verification shows multiple pre-aggregation consistency failures.
Raw fact totals include rows that pre-aggregation fixtures do not cover.

## Reproduction

Full-module baseline reproduced it during:

```bash
mvn -pl foggy-dataset-model test
```

Focused pre-aggregation tests pass in isolation:

```bash
mvn -pl foggy-dataset-model "-Dtest=PreAggregationDataValidationTest,PreAggregationEdgeCaseTest,PreAggregationIntegrationTest" test
```

## Expected vs Actual

Expected:

- Pre-aggregation totals and row sets match native/raw fact SQL.

Actual:

- Raw sales total is `28033.42`, pre-aggregation total is `27633.42`.
- Raw quantity is `15`, pre-aggregation quantity is `11`.
- Formula materialization expected rows for `Align Phone`, `Align Laptop`, and
  `Align Coat`; the pre-aggregation result omits them.

## Impact Scope

- Pre-aggregation correctness checks.
- Fixture data added by pivot/align tests when shared database state is reused.
- Confidence in pre-aggregation rewrite parity.

## Test Strategy

- First determine whether the failure is fixture contamination or rewrite logic.
- Add fixture-level guard if shared test data is leaking into preagg suites.
- Then update fixture setup or preagg generation logic with focused regression.

## Code Inventory

- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationDataValidationTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationEdgeCaseTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationIntegrationTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/JavaPivotOutputSnapshotTest.java`

## Fix Checklist

- [x] Identify which fixture inserts the Align rows.
- [x] Decide whether preagg tests must isolate or include those rows.
- [x] Add a regression guard for the chosen fixture contract.
- [x] Repair fixture or preagg data setup.
- [x] Run the focused preagg tests.

## Verification

- 2026-06-20: Reproduced by full-module test baseline.
- 2026-06-20: Focused pre-aggregation group passed in isolation, confirming the rewrite logic was not the direct failing point.
- 2026-06-20: Added a `JavaPivotOutputSnapshotTest` fixture cleanup guard and `@AfterEach` cleanup for the Align rows.
- 2026-06-20: `mvn -pl foggy-dataset-model "-Dtest=JavaPivotOutputSnapshotTest,PreAggregationDataValidationTest,PreAggregationEdgeCaseTest,PreAggregationIntegrationTest" test` passed.

## References

- `docs/9.2.0/workitems/BUG-model-module-test-baseline-20260620.md`
