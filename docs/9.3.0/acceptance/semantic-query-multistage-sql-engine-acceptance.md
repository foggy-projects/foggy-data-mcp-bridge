---
acceptance_scope: feature
version: 9.3.0
target: semantic-query-multistage-sql-engine
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex
signed_off_at: 2026-07-09
reviewed_by: Codex
blocking_items: []
follow_up_required: yes
evidence_count: 7
doc_role: feature_acceptance
doc_purpose: Record Stage 5 acceptance for the 9.3.0 semantic query multi-stage SQL engine.
---

# Feature Acceptance

## Background

This acceptance record covers the 9.3.0 multi-stage SQL engine work item after Stage 5 cleanup. The implementation makes SQL stage planning explicit for covered JDBC semantic query paths and records diagnostics for renderer selection, fallback, final count stage, preAgg policy, and fail-closed cases.

## Acceptance Basis

- Work item: `docs/9.3.0/workitems/P0-semantic-query-multistage-sql-engine.md`
- Detailed design: `docs/9.3.0/detailed_design/00_semantic_query_multistage_sql_engine.md`
- Progress record: `docs/9.3.0/workitems/P0-semantic-query-multistage-sql-engine-progress.md`
- Quality gate: `docs/9.3.0/quality/semantic-query-multistage-sql-engine-implementation-quality.md`
- Coverage audit: `docs/9.3.0/coverage/semantic-query-multistage-sql-engine-coverage-audit.md`

## Checklist

| Item | Status | Notes |
|---|---|---|
| Issue #120 root guard remains protected | accepted | Regression fences pass. |
| Planner-first stage metadata exists and is consumed | accepted | `QueryStagePlanner` and `QueryStagePlan` drive covered renderer decisions. |
| Post-aggregate and window/result-stage paths are deterministic | accepted | Supported paths render through stage plan; unsupported mixed paths fail closed. |
| `returnTotal` final-stage semantics are protected | accepted | Covered fixtures preserve final-stage SQL for total count. |
| PreAgg does not rewrite unsafe final-stage queries | accepted | Skip metadata and tests are present. |
| Compose diagnostics are preserved | accepted | `SqlGenerationResult.diagnostics` coverage is present. |
| Full module regression passes | accepted | 3259 tests, 0 failures, 0 errors, 3 skipped. |
| SQL Server execution evidence | accepted_with_risk | SQL Server profile was not run. |
| True MySQL 5.7 execution evidence | accepted_with_risk | No real MySQL 5.7 server run was captured. |

## Evidence

- `mvn -pl foggy-dataset-model -DskipTests compile`: pass.
- `mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test`: pass, 24 tests.
- `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest,JdbcModelQueryEngineCteWrapTest test`: pass, 31 tests.
- `mvn -pl foggy-dataset-model -Dtest=ComposePlannerCteWrapTest,ComposeSqlCompilerTest,ComposedDataSetResultIntegrationTest test`: pass, 19 tests.
- `mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest,QueryRequestValidationStepTest,JdbcModelQueryEngineCteWrapTest,PreAggregationEdgeCaseTest test`: pass, 72 tests.
- `mvn -pl foggy-dataset-model -Dtest=OdooModelLoadingTest test`: pass, 38 tests.
- `mvn -pl foggy-dataset-model test`: pass, 3259 tests, 0 failures, 0 errors, 3 skipped.

## Risks / Open Items

- Run SQL Server focused execution when the SQL Server profile and datasource are available.
- Run true MySQL 5.7 server execution for derived fallback and final-stage `returnTotal` behavior.
- Consider future stage-aware preAgg equivalence optimization after correctness remains stable.

## Failed Items

No blocking failed acceptance items.

## Final Decision

Decision: accepted-with-risks.

The covered Stage 5 implementation is accepted. The remaining SQL Server and true MySQL 5.7 evidence gaps are follow-up risks and must not be reported as completed verification until those profiles are actually executed.

## Signoff Marker

Signed off on 2026-07-09 by Codex for the 9.3.0 semantic query multi-stage SQL engine Stage 5 scope.
