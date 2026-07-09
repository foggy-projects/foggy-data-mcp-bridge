---
audit_scope: feature
audit_mode: pre-acceptance-check
version: 9.3.0
target: semantic-query-multistage-sql-engine
status: reviewed
conclusion: ready-with-gaps
reviewed_by: Codex
reviewed_at: 2026-07-09
follow_up_required: yes
---

# Test Coverage Audit

## Background

This audit checks whether the 9.3.0 multi-stage SQL engine work item has enough test evidence to enter acceptance. The scope covers issue #120 protection, planner diagnostics, renderer dispatch, post-aggregate/window result-stage behavior, `returnTotal`, preAgg metadata, bounded preAgg `returnTotal` equivalence, strict preAgg fail-closed mapping proof, compose diagnostics, and dialect fallback behavior.

## Audit Basis

- `docs/9.3.0/workitems/P0-semantic-query-multistage-sql-engine.md`
- `docs/9.3.0/workitems/P0-semantic-query-multistage-sql-engine-progress.md`
- `docs/9.3.0/quality/semantic-query-multistage-sql-engine-implementation-quality.md`
- Maven test outputs recorded in the progress document.

## Coverage Matrix

| Requirement / Risk | Evidence | Status |
|---|---|---|
| Issue #120 and same-name aggregate alias do not generate nested aggregate SQL | `CalculatedFieldAggregationBugTest`, regression fence, full module test | covered |
| Legal direct aggregate expressions remain valid | `JdbcModelQueryEngineCteWrapTest`, regression fence, full module test | covered |
| Aggregate alias references are downstream-stage only | `JdbcModelQueryEngineCteWrapTest`, Odoo post-aggregate tests | covered |
| Planner diagnostics are deterministic | `JdbcModelQueryEngineCteWrapTest`, compose diagnostics tests | covered |
| Window result-stage fail-closed behavior | `JdbcModelQueryEngineCteWrapTest` | covered |
| PostSlice without result-stage producer fails closed | `JdbcModelQueryEngineCteWrapTest` | covered |
| PostAggregate/window mixed plans fail closed | `JdbcModelQueryEngineCteWrapTest` | covered |
| MySQL no-CTE post-aggregate derived fallback | no-CTE fixture, MySQL dialect shape coverage, Odoo shape tests | partially_covered |
| `returnTotal` counts final semantic row set | `JdbcModelQueryEngineCteWrapTest`, `PreAggregationEdgeCaseTest` | covered_for_fixtures |
| PreAgg respects final-stage boundaries | `PreAggregationEdgeCaseTest` | covered |
| Bounded preAgg `returnTotal` equivalence without result-stage filters | `PreAggregationEdgeCaseTest#testPostAggregateWithoutResultFilterUsesEquivalentPreAggForReturnTotal` | covered_for_fixture |
| Bounded preAgg equivalence fails closed for hybrid or unprovable mappings | `PreAggregationEdgeCaseTest` orders 23-27 | covered |
| Compose preserves stage diagnostics | `ComposePlannerCteWrapTest`, `ComposeSqlCompilerTest`, `ComposedDataSetResultIntegrationTest` | covered |
| Step loop is not used as planner | static implementation review | covered |
| SQL Server focused execution | attempted SQL Server profile; blocked by JDBC pre-login connection reset on `localhost:11433` | blocked |
| Docker profile execution | `JdbcModelQueryEngineCteWrapTest` with `spring.profiles.active=docker` | covered |
| True MySQL 5.7 server execution | not run against a confirmed MySQL 5.7 server | gap |

## Evidence Summary

- `mvn -pl foggy-dataset-model -DskipTests compile`: pass.
- `mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test`: pass, 24 tests.
- `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest test`: pass, 13 tests, 0 failures, 0 errors, 0 skipped.
- `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest,JdbcModelQueryEngineCteWrapTest test`: pass, 37 tests, 0 failures, 0 errors, 0 skipped; configured second surefire execution also passed 37 tests.
- `mvn -pl foggy-dataset-model -P!multi-db -Dspring.profiles.active=docker -Dtest=JdbcModelQueryEngineCteWrapTest test`: pass, 24 tests, 0 failures, 0 errors, 1 skipped.
- `mvn -pl foggy-dataset-model -P!multi-db -Dspring.profiles.active=sqlserver -Dtest=JdbcModelQueryEngineCteWrapTest test`: blocked by SQL Server pre-login connection reset on `localhost:11433`; Maven reported 24 tests run, 0 failures, 6 errors, 1 skipped.
- `mvn -pl foggy-dataset-model -Dtest=ComposePlannerCteWrapTest,ComposeSqlCompilerTest,ComposedDataSetResultIntegrationTest test`: pass, 19 tests.
- `mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest,QueryRequestValidationStepTest,JdbcModelQueryEngineCteWrapTest,PreAggregationEdgeCaseTest test`: pass, 72 tests.
- `mvn -pl foggy-dataset-model -Dtest=OdooModelLoadingTest test`: pass, 38 tests.
- `mvn -pl foggy-dataset-model test`: pass, 3259 tests, 0 failures, 0 errors, 3 skipped.

## Gaps

- SQL Server profile was executed far enough to prove the current blocker is datasource connectivity, not an assertion failure; it still needs a successful run before dialect signoff.
- True MySQL 5.7 server execution remains pending in this checkpoint.
- Stage-aware preAgg equivalence is covered only for the no-result-filter `returnTotal` fixture. Final-stage filters and mixed post-aggregate/window plans remain intentionally out of scope; hybrid and unprovable preAgg mappings are now covered as fail-closed cases.

## Recommended Next Skills

- `foggy-acceptance-signoff`

## Conclusion

Conclusion: ready-with-gaps.

The core behavior has enough module-level and targeted regression evidence for accepted-with-risks signoff. The remaining gaps should be tracked as follow-up dialect evidence, not as blockers for the covered Stage 5 implementation.
