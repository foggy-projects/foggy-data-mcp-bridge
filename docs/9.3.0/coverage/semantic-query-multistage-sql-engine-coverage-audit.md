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

This audit checks whether the 9.3.0 multi-stage SQL engine work item has enough test evidence to enter acceptance. The scope covers issue #120 protection, planner diagnostics, renderer dispatch, post-aggregate/window result-stage behavior, `returnTotal`, preAgg metadata, compose diagnostics, and dialect fallback behavior.

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
| Compose preserves stage diagnostics | `ComposePlannerCteWrapTest`, `ComposeSqlCompilerTest`, `ComposedDataSetResultIntegrationTest` | covered |
| Step loop is not used as planner | static implementation review | covered |
| SQL Server focused execution | not run | gap |
| True MySQL 5.7 server execution | not run | gap |

## Evidence Summary

- `mvn -pl foggy-dataset-model -DskipTests compile`: pass.
- `mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test`: pass, 24 tests.
- `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest,JdbcModelQueryEngineCteWrapTest test`: pass, 31 tests.
- `mvn -pl foggy-dataset-model -Dtest=ComposePlannerCteWrapTest,ComposeSqlCompilerTest,ComposedDataSetResultIntegrationTest test`: pass, 19 tests.
- `mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest,QueryRequestValidationStepTest,JdbcModelQueryEngineCteWrapTest,PreAggregationEdgeCaseTest test`: pass, 72 tests.
- `mvn -pl foggy-dataset-model -Dtest=OdooModelLoadingTest test`: pass, 38 tests.
- `mvn -pl foggy-dataset-model test`: pass, 3259 tests, 0 failures, 0 errors, 3 skipped.

## Gaps

- SQL Server profile was not executed in this checkpoint.
- True MySQL 5.7 server execution was not executed in this checkpoint.
- Stage-aware preAgg equivalence optimization is intentionally not covered because it remains future work; current tests cover safe skip behavior.

## Recommended Next Skills

- `foggy-acceptance-signoff`

## Conclusion

Conclusion: ready-with-gaps.

The core behavior has enough module-level and targeted regression evidence for accepted-with-risks signoff. The remaining gaps should be tracked as follow-up dialect evidence, not as blockers for the covered Stage 5 implementation.
