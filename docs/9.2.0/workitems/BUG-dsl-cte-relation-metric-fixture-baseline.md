---
type: bug
bug_source: regression-found
version: 9.2.0
ticket: BUG-dsl-cte-relation-metric-fixture-baseline
severity: minor
status: local-verified
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
created_at: 2026-06-20
updated_at: 2026-06-20
owner_module: foggy-dataset-model
---

# BUG: DSL_CTE Relation Metric Fixture Baseline Expects Old Category Label

## Background

Four DSL_CTE relation metric fixture assertions now receive `Align-Clothing`
where the test expects `服装配饰`. This appears related to shared Align fixture
data used by pivot alignment snapshots.

## Reproduction

Full-module baseline reproduced it when the pivot Align fixture leaked into
later shared database state:

```bash
mvn -pl foggy-dataset-model test
```

The focused relation metric fixture itself passes when isolated:

```bash
mvn -pl foggy-dataset-model -Dtest=DslCteRelationMetricFixtureIntegrationTest test
```

## Expected vs Actual

Expected:

- Manual baseline and bridge SQL return the same category label.

Actual:

- Affected tests expect `服装配饰`.
- Actual result is `Align-Clothing`.

## Impact Scope

- DSL_CTE relation metric fixture baseline expectations.
- Possibly ordering/bucket selection when Align fixture rows are present.

## Test Strategy

- Confirm whether `Align-Clothing` is now the correct deterministic top result.
- If yes, update baseline expectations and document fixture reason.
- If no, repair relation metric ordering/filtering logic.

## Code Inventory

- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/semantic/DslCteRelationMetricFixtureIntegrationTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/JavaPivotOutputSnapshotTest.java`

## Fix Checklist

- [x] Compare manual baseline SQL and bridge SQL output.
- [x] Decide whether this is expected fixture drift or engine logic regression.
- [x] Update baseline or repair logic.
- [x] Run the focused relation metric fixture test.

## Verification

- 2026-06-20: Reproduced by full-module test baseline.
- 2026-06-20: Fixed by the `JavaPivotOutputSnapshotTest` Align fixture cleanup guard.
- 2026-06-20: `mvn -pl foggy-dataset-model -Dtest=DslCteRelationMetricFixtureIntegrationTest test` passed, 9 tests.

## References

- `docs/9.2.0/workitems/BUG-model-module-test-baseline-20260620.md`
