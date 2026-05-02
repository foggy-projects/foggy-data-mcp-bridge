---
doc_role: feature_acceptance
doc_purpose: Formal acceptance record for Pivot SQL TopN/Having pushdown Stage 1-4 in Foggy Pivot 9.0.0.beta.
acceptance_scope: feature
version: 9.0.0.beta
target: Pivot SQL TopN/Having Pushdown Stage 1-4
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex acceptance reviewer
signed_off_at: 2026-05-02
reviewed_by: Codex
blocking_items: []
follow_up_required: yes
evidence_count: 12
---

# Feature Acceptance

## Background

Pivot 9.0.0.beta originally performed axis `having` / `limit` / TopN truncation in memory after queryModel execution. This caused avoidable large intermediate result sets and made `limit` ineffective for controlling database-side scan and transfer volume.

This delivery moves eligible Pivot axis-domain filtering into SQL while preserving the queryModel lifecycle: permission validation, pre-aggregation rewrite, physical column checks, dialect capability checks, and parameter binding remain inside the managed queryModel path. Unsupported or unsafe cases fail closed to the existing memory path.

## Acceptance Basis

- `docs/9.0.0.beta/detailed_design/08_pivot_sql_topn_pushdown_refactor_plan.md`
- `docs/9.0.0.beta/test_coverage/pivot-sql-topn-pushdown-coverage-audit.md`
- `docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-performance-baseline.md`
- `docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-performance-baseline-acceptance.md`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotPipeline.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/sql/PivotAxisDomainSqlPlanner.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/rollup/NonAdditiveRollupExecutor.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/query_execution/ManagedSqlRelation.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/service/QueryFacade.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotSqlParityIntegrationTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/sql/PivotAxisDomainSqlPlannerTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/rollup/NonAdditiveRollupExecutorDomainSliceTest.java`

## Checklist

- [x] SQL pushdown is gated by capability checks: managed query facade available, non-tree mode, no `parentShare` / `baselineRatio`, and at least one axis requiring `having` or `limit`.
- [x] Pushdown does not bypass queryModel; managed relation prepare/execute keeps permission validation, pre-aggregation rewrite, physical column validation, and base SQL generation in the queryModel pipeline.
- [x] `ManagedSqlRelation` exposes defensive capability metadata for wrappability, permission validation, pre-aggregation status, dialect support, base parameters, and metric additivity.
- [x] `PivotAxisDomainSqlPlanner` handles `having` before TopN and parameterizes both having values and limit values.
- [x] Planner uses queryModel-provided metric metadata / aggregation resolution instead of hard-coding `SUM`.
- [x] Unsupported cases fail closed through `PivotPushdownUnsupportedException` or dialect `UnsupportedOperationException` and return to the memory engine.
- [x] MySQL 5.7 capability refusal no longer crashes the pipeline; it falls back to memory execution.
- [x] Stage 4 non-additive rollup domain bounding preserves tuple correlation with OR-of-AND slices, supports null values, covers grandTotal, and fails closed on oversized domains.
- [x] SQL pushdown + TopN + `COUNT_DISTINCT` + subtotals/grandTotal is verified against an independent SQL oracle using null-safe `EXISTS`.
- [x] SQLite, MySQL 5.7 fallback, and PostgreSQL parity evidence is recorded.

## Evidence

- Local acceptance rerun on 2026-05-02:

```bash
mvn -pl foggy-dataset-model -P!multi-db "-DfailIfNoTests=false" "-Dtest=PivotSqlParityIntegrationTest#testSqlPushdownNonAdditiveRollupWithTopNAndSubtotalsParity+testSqlPushdownTriggeredInNormalExecution,PivotAxisDomainSqlPlannerTest#testBaseRelationParamsWithHavingAndLimit,NonAdditiveRollupExecutorDomainSliceTest" test
```

Result: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`.

- `PivotSqlParityIntegrationTest.testSqlPushdownTriggeredInNormalExecution` asserts the normal execution path logs `Phase 1: SQL pushdown succeeded`.
- `PivotSqlParityIntegrationTest.testSqlPushdownNonAdditiveRollupWithTopNAndSubtotalsParity` verifies SQL pushdown active + TopN by `salesAmount` + non-additive `uniqueCustomers` + row subtotals + grandTotal.
- The complex parity oracle uses a null-safe `EXISTS` domain filter, matching Stage 4 `IS NULL` semantics and avoiding silent tuple loss.
- `PivotAxisDomainSqlPlannerTest.testBaseRelationParamsWithHavingAndLimit` verifies parameter order: base relation parameters first, then having value, then limit value.
- `NonAdditiveRollupExecutorDomainSliceTest` covers tuple correlation, null handling, grandTotal filtering, oversized domain fail-closed behavior, and empty-domain boundaries.
- Earlier Stage 4 targeted validation recorded `11 tests run, 0 failures, 0 errors` for `NonAdditiveRollupExecutorDomainSliceTest`.
- Full reported regression after Stage 4 semantic correction: `2392 tests run, 0 failures, 0 errors`.
- Reported cross-database verification covers SQLite default execution, MySQL 5.7 fallback execution, and PostgreSQL CTE/window execution.
- `pivot-sql-topn-pushdown-coverage-audit.md` is marked `conclusion: verified` and records the remaining boundary as a known product limitation, not a correctness gap.
- Performance baseline follow-up records SQLite / MySQL 8.0 / PostgreSQL benchmark sampling and accepts that SQL pushdown is not expected to beat memory truncation on small 10k-row data for every scenario.
- Large-domain benchmark evidence records fail-closed behavior at `600 > 500`, supporting the decision to defer Stage 5 domain transport for 9.0.0.beta.

## Failed Items

No blocking failed acceptance item remains.

## Risks / Open Items

- `domain > 500` for non-additive subtotal/grandTotal is intentionally fail-closed through `NonAdditiveRollupDomainTooLargeException`. This prevents silent wrong results. The performance baseline confirms Stage 5 large-domain transport is not a 9.0.0.beta blocker; it should be revisited only with telemetry or customer evidence.
- MySQL 5.7 does not receive SQL pushdown because required CTE/window capabilities are unavailable. This is accepted as guarded fallback behavior, not feature parity at the SQL-shape level.
- `walkthrough.md` and `task.md` were mentioned in the handoff, but they were not present under `docs/9.0.0.beta` during this signoff pass, so they are not used as formal evidence here.

## Final Decision

Accepted with non-blocking risks. Pivot SQL TopN/Having pushdown Stage 1-4 satisfies the architecture, safety, fallback, Stage 4 non-additive rollup alignment, and SQLite/MySQL 5.7/PostgreSQL verification requirements.

The only remaining item is capacity-oriented, not correctness-oriented: oversized non-additive rollup domains fail closed and should be treated as a future scalability enhancement if product usage requires larger bounded domains.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-acceptance.md
- blocking_items: none
- follow_up_required: yes
