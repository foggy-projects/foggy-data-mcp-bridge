---
type: bug
bug_source: regression-found
version: 9.2.0
ticket: BUG-model-module-test-baseline-20260620
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

# BUG: foggy-dataset-model Full Module Test Baseline Is Not Green

## Background

While verifying the DSL_CTE cross-model structured CTE fix, the focused
acceptance tests passed, but the full module test command still failed. These
failures are outside the fixed DSL_CTE cross-model wrapper path and should be
tracked separately so later DSL/CTE work can use a clear baseline.

## Reproduction

Run:

```bash
mvn -pl foggy-dataset-model test
```

## Expected vs Actual

Expected:

- Full module test suite passes, or expected failures are explicitly quarantined
  and documented.

Original actual:

- Full module suite fails in several unrelated areas.

Confirmed failing groups from the original `foggy-dataset-model/target/surefire-reports`:

- `AdvancedAnalyticsTest.testQmV2SameTableModelMultipleAliases`
  - SQLite error: `no such column: salesAmount`.
  - Scope: QM v2 same-table alias / aggregate join SQL projection.
- `JavaSemanticScaleSnapshotTest.shouldProduceSemanticScaleSnapshot`
  - Snapshot mismatch: expected `[1000]`, actual `[1000.0]`.
  - Scope: semantic scale snapshot formatting/parity.
- `PreAggregationDataValidationTest`
  - 9 consistency failures, including original total `28033.42` vs preagg
    total `27633.42`, and quantity `15` vs `11`.
  - Scope: pre-aggregation data or fixture consistency.
- `PreAggregationEdgeCaseTest.testQueryWithDateRangeSlice_ResultConsistency`
  - Date range consistency failure: original `28033.42`, preagg `27633.42`.
  - Scope: pre-aggregation date range consistency.
- `PreAggregationIntegrationTest.testFormulaSemanticMeasurePreAggResultMatchesNativeSql`
  - Native SQL includes Align rows for years 2098/2099, preagg result omits
    them.
  - Scope: pre-aggregation formula materialization fixture coverage.
- `DslCteRelationMetricFixtureIntegrationTest`
  - 4 baseline assertion failures:
    - `relationCaseLabelSameStageAliasDagSqlMatchesManualBaseline`
    - `relationCaseLabelBridgeSqlMatchesManualBaseline`
    - `relationMetricRatioOrderByBridgeSqlMatchesManualBaseline`
    - `relationOrderedNumericBucketBridgeSqlMatchesManualBaseline`
  - Expected `服装配饰`, actual `Align-Clothing`.
  - Scope: relation metric fixture baseline/data expectation.
- `AggregateJoinQueryModelTest.aggregateRelationGroupKeyAliasSliceShouldPushWhereThroughRequest`
  - Late full-gate failure after the first repair batch.
  - Scope: dialect quote normalization in SQL shape assertion.
- `JdbcModelQueryEngineCteWrapTest.testRunningSumPostSliceExecutesAndMatchesHandWrittenSql`
  - Late full-gate failure after the first repair batch.
  - Scope: hard-coded running-sum postSlice threshold became invalid when
    repeated sqlite executions reused shared fixture state.

No `SemanticServiceV3Test` failure remains in the latest full-module run after
the validate-pipeline behavior was aligned and its legacy expectations were
updated.

## Impact Scope

- Originally blocked treating `mvn -pl foggy-dataset-model test` as a clean
  quality gate.
- Can obscure regressions from DSL/CTE refactoring if not tracked separately.
- Does not invalidate the focused DSL_CTE acceptance verification because
  `DslCteAcceptanceSampleTest` passed under default/mysql/postgres executions.

## Test Strategy

Each failure group should get its own follow-up work item before repair:

- QM alias / aggregate join: integration test already exists and reproduces.
- Semantic scale snapshot: snapshot/parity test already exists and reproduces.
- Pre-aggregation consistency: integration tests already reproduce, but fixture
  setup should be audited before code changes.
- Relation metric fixture baseline: integration tests already reproduce, but
  fixture data and expected baseline should be checked before code changes.

## Code Inventory

- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/AdvancedAnalyticsTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/parity/JavaSemanticScaleSnapshotTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationDataValidationTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationEdgeCaseTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationIntegrationTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/semantic/DslCteRelationMetricFixtureIntegrationTest.java`

## Fix Checklist

- [x] Record current failing baseline.
- [x] Split failing groups into dedicated work items before repair.
- [x] Confirm whether fixture data changed intentionally.
- [x] Repair or update expected baselines with focused tests.
- [x] Restore full `mvn -pl foggy-dataset-model test` as a green gate.

## Verification

- 2026-06-20: Reproduced during full module verification after DSL_CTE focused
  tests passed.
- 2026-06-20: Re-ran `mvn -pl foggy-dataset-model test` after the
  validate-pipeline test expectation update. Full module suite is still not
  green, but failures are limited to the baseline groups above:
  `AdvancedAnalyticsTest`, `JavaSemanticScaleSnapshotTest`,
  `PreAggregationDataValidationTest`, `PreAggregationEdgeCaseTest`,
  `PreAggregationIntegrationTest`, and
  `DslCteRelationMetricFixtureIntegrationTest`.
- 2026-06-20: Split and repaired the original failure groups. Also repaired two
  late full-gate issues:
  - `AggregateJoinQueryModelTest` SQL assertion now normalizes dialect quotes.
  - `JdbcModelQueryEngineCteWrapTest` derives the running-sum postSlice
    threshold from the current fixture, preserving the hand-written SQL parity
    assertion under repeated sqlite executions.
- 2026-06-20: `mvn -pl foggy-dataset-model test` passed.

## References

- `docs/9.2.0/workitems/BUG-dsl-cte-cross-model-cte-stages.md`
- `docs/9.2.0/workitems/BUG-qm-v2-same-table-alias-aggregate-sql.md`
- `docs/9.2.0/workitems/BUG-preagg-data-consistency-baseline.md`
- `docs/9.2.0/workitems/BUG-dsl-cte-relation-metric-fixture-baseline.md`
- `docs/9.2.0/workitems/BUG-semantic-scale-snapshot-format.md`
- `docs/model-engine-dsl-cte-tm-qm-review-20260620.md`
