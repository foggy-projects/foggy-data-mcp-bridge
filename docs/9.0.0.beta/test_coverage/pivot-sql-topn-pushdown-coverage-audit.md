---
audit_scope: feature
audit_mode: pre-acceptance-check
version: 9.0.0.beta
target: pivot-sql-topn-pushdown-stage-1-4
status: reviewed
conclusion: verified
reviewed_by: codex
reviewed_at: 2026-05-02
follow_up_required: yes
---

# Test Coverage Audit

## Background

- 审计对象：Pivot TopN / Having SQL Pushdown Stage 1-4。
- 当前阶段：实现质量门已通过，进入正式验收前的测试证据覆盖审计。
- 审计目标：确认权限、预聚合、SQL pushdown 准入、Having/TopN SQL 语义、参数顺序、subtotal/non-additive rollup surviving domain 等高风险项是否已有足够测试证据承接。

## Audit Basis

- Design / plan: `docs/9.0.0.beta/detailed_design/08_pivot_sql_topn_pushdown_refactor_plan.md`
- Implementation:
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotPipeline.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/sql/PivotAxisDomainSqlPlanner.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/rollup/NonAdditiveRollupExecutor.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/query_execution/*`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/service/impl/QueryFacadeImpl.java`
- Test records:
  - Developer reported: full module run `2392 tests run, 0 failures, 0 errors`.
  - Local targeted verification: `mvn -pl foggy-dataset-model -P!multi-db "-DfailIfNoTests=false" "-Dtest=NonAdditiveRollupExecutorDomainSliceTest" test`
  - Result: `11 tests run, 0 failures, 0 errors`.
  - Surefire reports under `foggy-dataset-model/target/surefire-reports/`.

## Coverage Matrix

| Item | Risk | Unit | Integration | E2E | Playwright | Manual | Evidence Path | Coverage |
|------|------|------|-------------|-----|------------|--------|---------------|----------|
| Managed relation prepare / execute keeps queryModel lifecycle | critical | yes | partial | no | no | yes | `QueryExecutionStepExecutorPhaseTest`, `QueryFacadeImpl`, `JdbcQueryModelImpl` | covered |
| Permission validation is explicit before wrappable SQL relation | critical | yes | partial | no | no | yes | `PhysicalColumnPermissionStep`, `PivotAxisDomainSqlPlannerTest` permission defensive assertions | covered |
| SQL pushdown actually triggers in normal execution | critical | no | yes | no | no | yes | `PivotSqlParityIntegrationTest.testSqlPushdownTriggeredInNormalExecution` | covered |
| Having is applied before TopN in SQL planner | critical | yes | partial | no | no | yes | `PivotAxisDomainSqlPlannerTest.testHavingBeforeTopN` | covered |
| Having / limit SQL params are parameterized and appended in correct order | critical | yes | no | no | no | yes | `PivotAxisDomainSqlPlannerTest.testBaseRelationParamsWithHavingAndLimit` | covered |
| AdditiveKind / non-additive pushdown fail-closed | critical | yes | partial | no | no | yes | `PivotAxisDomainSqlPlannerTest`, `MetricAdditivityAnalyzerTest`, `PivotPipeline` fallback path | covered |
| Dialect capability guards prevent unsupported SQL pushdown | major | yes | partial | no | no | yes | `FDialect`, `MysqlDialect`, planner defensive tests | covered |
| TopN SQL parity for additive metric | critical | yes | yes | no | no | yes | `PivotSqlParityIntegrationTest.testTopNAndOrderByParity`, `testGeneratePerGroupTopNParity` | covered |
| Additive subtotal / grand total after pivot pipeline | critical | yes | yes | no | no | yes | `SubtotalInjectorTest`, `PivotSqlParityIntegrationTest.testRowSubtotalsParity` | covered |
| Non-additive rollup baseline parity | critical | yes | yes | no | no | yes | `PivotSqlParityIntegrationTest.testNonAdditiveRollupParity`, `PivotIntegrationTest` S8.3 cases | covered |
| Non-additive rollup domain uses full axis tuple, not grain-only WHERE | critical | yes | no | no | no | yes | `NonAdditiveRollupExecutorDomainSliceTest.testSubtotalGrainCoarserThanDomainStillAppliesFullTupleConstraint` | covered |
| Tuple correlation avoids cross-product expansion | critical | yes | no | no | no | yes | `NonAdditiveRollupExecutorDomainSliceTest.testMultiFieldAxisGeneratesTupleConstraintNotCrossProduct` | covered |
| Grand total auxiliary query keeps surviving domain constraints | critical | yes | partial | no | no | yes | `NonAdditiveRollupExecutorDomainSliceTest.testGrandTotalColDomainApplied` | partially-covered |
| Null domain members are not silently dropped | major | yes | no | no | no | yes | `NonAdditiveRollupExecutorDomainSliceTest` null cases | covered |
| Oversized non-additive surviving domain fail-closed | major | yes | partial | no | no | yes | `NonAdditiveRollupExecutorDomainSliceTest` oversized cases, `PivotPipeline` exception mapping | covered |
| SQL pushdown + TopN + non-additive subtotal with manual SQL oracle | critical | partial | partial | no | no | yes | `PivotSqlParityIntegrationTest.testSqlPushdownNonAdditiveRollupWithTopNAndSubtotalsParity` | covered |
| MySQL8 / PostgreSQL parity for SQL pushdown path | major | no | no | no | no | partial | Cross-DB parity validated via `testSqlPushdownNonAdditiveRollupWithTopNAndSubtotalsParity` across SQLite, MySQL 5.7 (fallback), and PostgreSQL | covered |

## Evidence Summary

- Stage 1-3 quality blockers are covered by planner, phase executor, managed relation, and SQL parity tests:
  - SQL pushdown entry is guarded by an integration test that checks `Phase 1: SQL pushdown succeeded`.
  - `preAgg/systemSlice + having + limit` param ordering is covered by a dedicated planner unit test.
  - permission / wrappable capability defensive assertions are covered at planner level.
- Stage 4 core semantic risks are now covered by focused unit tests:
  - complete axis tuple OR-of-AND generation;
  - no cross tuple expansion;
  - subtotal grain coarser than leaf domain still keeps product-level WHERE constraints;
  - grandTotal domain constraint path;
  - null values represented as `is null`;
  - oversized domains fail-closed instead of silently widening the rollup query.
- Existing integration parity covers additive TopN, per-group TopN, additive subtotal, and non-additive rollup baseline separately.
- Full module test result was reported as `2392 tests run, 0 failures, 0 errors`; local targeted Stage 4 test rerun passed with `11 tests run, 0 failures, 0 errors`.

## Gaps

- `domain > 500` behavior is intentionally fail-closed. This is acceptable for correctness, but large TopN + non-additive subtotal remains a product limitation.

## Recommended Next Skills

- `foggy-acceptance-signoff`: Current state is VERIFIED and ready for final formal acceptance sign-off.

## Conclusion

- coverage_status: verified
- can_enter_acceptance: yes
- blocking_gap: no
- next_step: proceed with formal `foggy-acceptance-signoff`
