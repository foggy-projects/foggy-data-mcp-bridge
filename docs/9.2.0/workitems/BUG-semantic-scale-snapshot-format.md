---
type: bug
bug_source: regression-found
version: 9.2.0
ticket: BUG-semantic-scale-snapshot-format
severity: minor
status: local-verified
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: foggy-dataset-model
created_at: 2026-06-20
updated_at: 2026-06-20
owner_module: foggy-dataset-model
---

# BUG: Semantic Scale Snapshot Parameter Format Drift

## Background

Java semantic scale parity snapshot now reports a parameter as `1000.0`, while
the snapshot expectation is `1000`.

## Reproduction

Run:

```bash
mvn -pl foggy-dataset-model -Dtest=JavaSemanticScaleSnapshotTest test
```

## Expected vs Actual

Expected:

- Snapshot parameter values keep the expected parity format.

Actual:

- Expected `[1000]`, actual `[1000.0]`.

## Impact Scope

- Java/Python semantic scale snapshot parity.
- Parameter normalization around semantic scale filters.

## Test Strategy

- Determine whether decimal output is semantically correct.
- If the engine now always uses decimal for scaled numeric comparisons, update
  the snapshot with rationale.
- If integer preservation is required, normalize parameter output before
  snapshot comparison.

## Code Inventory

- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/parity/JavaSemanticScaleSnapshotTest.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/SemanticScaleSqlSupport.java`

## Fix Checklist

- [x] Inspect generated SQL and params for the failing snapshot case.
- [x] Decide snapshot update vs normalization repair.
- [x] Apply the smallest safe change.
- [x] Run the focused snapshot test.

## Verification

- 2026-06-20: Reproduced by full-module test baseline.
- 2026-06-20: Focused `JavaSemanticScaleSnapshotTest` reproduced `[1000]` vs `[1000.0]` in `formulaPropertySqlCase`.
- 2026-06-20: Kept engine behavior and normalized numeric parameter comparison in the snapshot test, so equivalent numeric values do not fail on representation alone.
- 2026-06-20: `mvn -pl foggy-dataset-model -Dtest=JavaSemanticScaleSnapshotTest test` passed.

## References

- `docs/9.2.0/workitems/BUG-model-module-test-baseline-20260620.md`
