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
evidence_count: 10
doc_role: feature_acceptance
doc_purpose: Record Stage 5 acceptance for the 9.3.0 semantic query multi-stage SQL engine.
---

# Feature Acceptance

## Background

This acceptance record covers the 9.3.0 multi-stage SQL engine work item after Stage 5 cleanup. The implementation makes SQL stage planning explicit for covered JDBC semantic query paths and records diagnostics for renderer selection, fallback, final count stage, preAgg policy, bounded preAgg `returnTotal` equivalence, and fail-closed cases.

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
| PreAgg does not rewrite unsafe final-stage queries | accepted | Skip metadata and tests are present for result-filter final-stage plans. |
| Bounded preAgg returnTotal equivalence | accepted_with_scope_limit | No-result-filter final-stage plans can use equivalent preAgg aggregate SQL; hybrid/unprovable mapping and predicate-proof cases fail closed; broader final-stage preAgg rewrites remain out of scope. |
| Compose diagnostics are preserved | accepted | `SqlGenerationResult.diagnostics` coverage is present. |
| Full module regression passes | accepted | 3259 tests, 0 failures, 0 errors, 3 skipped. |
| SQL Server execution evidence | blocked_with_risk | SQL Server profile was attempted but failed during JDBC pre-login connection reset on `localhost:11433`. |
| True MySQL 5.7 execution evidence | accepted_with_risk | Docker profile execution passed, but no confirmed MySQL 5.7 server run was captured. |

## Evidence

- `mvn -pl foggy-dataset-model -DskipTests compile`: pass.
- `mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test`: pass, 24 tests.
- `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest#testPostAggregateWithoutResultFilterUsesEquivalentPreAggForReturnTotal test`: pass, 1 test.
- `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest test`: pass, 17 tests, 0 failures, 0 errors, 0 skipped.
- `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest,JdbcModelQueryEngineCteWrapTest test`: pass, 41 tests, 0 failures, 0 errors, 0 skipped; configured second surefire execution also passed 41 tests.
- `mvn -pl foggy-dataset-model -P!multi-db -Dspring.profiles.active=docker -Dtest=JdbcModelQueryEngineCteWrapTest test`: pass, 24 tests, 0 failures, 0 errors, 1 skipped.
- `mvn -pl foggy-dataset-model -P!multi-db -Dspring.profiles.active=sqlserver -Dtest=JdbcModelQueryEngineCteWrapTest test`: blocked by SQL Server pre-login connection reset on `localhost:11433`; Maven reported 24 tests run, 0 failures, 6 errors, 1 skipped.
- `mvn -pl foggy-dataset-model -Dtest=ComposePlannerCteWrapTest,ComposeSqlCompilerTest,ComposedDataSetResultIntegrationTest test`: pass, 19 tests.
- `mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest,QueryRequestValidationStepTest,JdbcModelQueryEngineCteWrapTest,PreAggregationEdgeCaseTest test`: pass, 72 tests.
- `mvn -pl foggy-dataset-model -Dtest=OdooModelLoadingTest test`: pass, 38 tests.
- `mvn -pl foggy-dataset-model test`: pass, 3259 tests, 0 failures, 0 errors, 3 skipped.

## Risks / Open Items

- Fix or reprovision SQL Server profile connectivity, then rerun focused execution.
- Run true MySQL 5.7 server execution for derived fallback and final-stage `returnTotal` behavior.
- Broaden stage-aware preAgg equivalence only after adding proof and tests for final-stage filters, mixed stages, non-trivial preAgg mappings, and row-level measure predicate semantics.

## Failed Items

No blocking implementation acceptance items. SQL Server dialect execution remains blocked by environment connectivity and is tracked as a follow-up risk.

## Final Decision

Decision: accepted-with-risks.

The covered Stage 5 plus P0-P2 follow-up implementation is accepted. SQL Server remains blocked at datasource connectivity, and true MySQL 5.7 evidence remains pending; neither should be reported as completed dialect signoff until those profiles execute successfully.

## Signoff Marker

Signed off on 2026-07-09 by Codex for the 9.3.0 semantic query multi-stage SQL engine Stage 5 scope.
