---
type: progress
version: 9.3.0
ticket: P0-semantic-query-multistage-sql-engine
status: in_progress
owner: foggy-dataset-model
updated_at: 2026-07-09
experience: N/A
---

# P0 Semantic Query Multi-Stage SQL Engine Progress

## Document Purpose

- doc_type: progress
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track implementation, testing, and readiness for the 9.3.0 multi-stage SQL engine work item.

## Current Execution Scope

- delivery_mode: single-root-delivery
- operation_mode: progress-update / execution-checkin
- active_stage: Stage 3 - Window And Result Stage Integration
- scope: completed Stage 2 post-aggregate rendering and final-stage `returnTotal`/count SQL alignment; next execution scope is window/result-stage migration.
- next_stage: Stage 3 - migrate window result-stage rendering to planner metadata and add dialect fail-closed behavior where needed.
- non_goals_this_pass: no Step loop planning, no public API contract change, no full window renderer migration, no release-level dialect signoff.

## Stage Progress

| Stage | Status | Notes |
|---|---|---|
| Stage 0 - Baseline And Regression Fence | completed | Existing issue #120 guard and targeted regression tests are present. |
| Stage 1 - Planner Skeleton | completed | Added planner DTOs, classifier, diagnostics, and `ModelResultContext.extData["queryStagePlan"]` metadata exposure. |
| Stage 2 - Aggregate And Post-Aggregate Stage Builders | completed | Post-aggregate renderer consumes planner `renderStrategy`; final-stage count SQL metadata and multi-stage `aggSql` policy are exposed in diagnostics. |
| Stage 3 - Window And Result Stage Integration | pending | Requires Stage 2 renderer abstraction and dialect fallback. |
| Stage 4 - PreAgg, AggSql, Compose, And Dialect Hardening | pending | Requires planned final count stage metadata. |
| Stage 5 - Default Enablement And Cleanup | pending | Requires full dialect/test evidence and quality gates. |

## Development Progress

- Stage 1 completed.
- Stage 2 checkpoint completed for post-aggregate SQL rendering.
- Implemented code touchpoints:
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/stage`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/JdbcModelQueryEngineCteWrapTest.java`
- Diagnostics metadata currently includes:
  - `version`
  - `renderStrategy`
  - `fallbackReason`
  - `returnTotalStrategy`
  - `finalCountStageId`
  - `countSqlInput`
  - `aggSqlOptimizationPolicy`
  - ordered `stages`
  - per-stage `id`, `type`, `inputAliases`, `outputAliases`, `filterAliases`, `orderAliases`, `requiresSqlBoundary`
- Stage 1 does not change SQL rendering behavior; existing wrapper paths still render SQL.
- Stage 2 post-aggregate rendering now uses stage-plan metadata:
  - `renderStrategy=cte` keeps the existing structured `WITH base_stage AS (...), post_stage AS (...)` rendering.
  - `renderStrategy=derived` renders nested derived tables instead of `WITH`.
  - derived fallback sets `cteWrapped=false` and exposes no `cteStages`.
  - CTE-based domain transport plus derived-table fallback currently fails closed with `DERIVED_STAGE_CTE_TRANSPORT_UNSUPPORTED`.
- Stage 2 final-stage count alignment is implemented:
  - `countSqlInput=final-stage-sql-without-order` means `returnTotal` counts the final semantic result set.
  - `countSqlInput=disabled` is emitted when `returnTotal=false`.
  - `aggSqlOptimizationPolicy=preserve-final-stage-sql` skips `AggSqlOptimizer` for stage-boundary queries so filters on post-aggregate/window results are preserved.
  - `aggSqlOptimizationPolicy=optimizer-allowed` remains for compatible single-stage queries.
- Window result-stage rendering still uses the existing CTE wrapper path; dialect capability enforcement for window functions is deferred to Stage 3.

## Testing Progress

- latest known targeted baseline:
  - `mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest,QueryRequestValidationStepTest test`
  - result before this stage: pass, 41 tests.
- Stage 1 compile:
  - `mvn -pl foggy-dataset-model -DskipTests compile`
  - result: pass.
- Stage 1 targeted tests:
  - `mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test`
  - result: pass, 18 tests.
- Stage 1 regression fence:
  - `mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest,QueryRequestValidationStepTest,JdbcModelQueryEngineCteWrapTest test`
  - result: pass, 59 tests.
- Stage 2 targeted tests:
  - `mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test`
  - result: pass, 19 tests.
- Stage 2 regression fence:
  - `mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest,QueryRequestValidationStepTest,JdbcModelQueryEngineCteWrapTest test`
  - result: pass, 60 tests.
- Stage 2 count-stage targeted tests:
  - `mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test`
  - result: pass, 20 tests.
- Stage 2 count-stage regression fence:
  - `mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest,QueryRequestValidationStepTest,JdbcModelQueryEngineCteWrapTest test`
  - result: pass, 61 tests; the Maven execution also completed the project's configured `test-mysql` phase successfully.
- SQL Server status: pending.
- MySQL 5.7 derived fallback status: partial; no-CTE derived fallback is covered with a `NoCteSqliteDialect` fixture, but true MySQL 5.7 execution evidence is still pending.
- release-level test: pending.

## Experience Progress

- N/A: backend query engine architecture only, no UI or manual interaction surface changed.

## Acceptance Criteria Tracking

| Criteria | Status | Evidence |
|---|---|---|
| No nested aggregate for issue #120 variants | partial | Existing regression tests pass before Stage 1. |
| Legal aggregate expressions remain single-stage where valid | partial | Existing regression tests pass before Stage 1. |
| Aggregate alias references visible only downstream | partial | Stage 1 tests assert `teamSales` as post-aggregate input and `salesShare` as downstream output/filter metadata; Stage 2 renders post-aggregate output downstream. |
| Deterministic stage diagnostics | partial | Stage 1 tests assert stage order and `renderStrategy`; Stage 2 tests assert renderer behavior, `countSqlInput`, and `aggSqlOptimizationPolicy` match the plan. |
| Existing single-stage behavior compatible | partial | Stage 1 tests assert single-stage aggregate stays `renderStrategy=single` and does not introduce CTE SQL. |
| MySQL 5.7 no unsupported CTE | partial | Stage 2 post-aggregate path can render derived tables when `supportsCte=false`; real MySQL 5.7 and window-path evidence remain pending. |
| `returnTotal` final semantic row set | partial | Stage 2 tests compare `aggSql` total with the rendered final-row SQL for window post-slice, post-aggregate CTE, and post-aggregate derived fallback paths; true SQL Server/MySQL 5.7 baselines remain pending. |
| Step loop not used as planner | partial | Stage 1 uses dedicated `QueryStagePlanner`; Stage 2 post-aggregate renderer reads the planner directly and does not introduce `QueryExecutionStep` planning. |

## Execution Check-In

### Stage 1 Checkpoint - 2026-07-09

- completed work summary:
  - Added `QueryStagePlan`, `QueryStagePlanner`, and `QueryStageType` as read-only planning metadata.
  - Attached stage diagnostics to `ModelResultContext.extData["queryStagePlan"]` during `JdbcModelQueryEngine.analysisQueryRequest`.
  - Added tests for single-pass aggregate, window result post-slice, and post-aggregate calculated-field stage metadata.
- touched code paths:
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/stage/QueryStagePlan.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/stage/QueryStagePlanner.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/stage/QueryStageType.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/JdbcModelQueryEngineCteWrapTest.java`
- self-check checklist:
  - requirement scope implemented as intended: completed.
  - non-goals preserved: completed.
  - code paths listed: completed.
  - basic self-review completed: completed.
  - test status recorded: completed.
  - docs/progress writeback completed: completed.
- remaining risks / blockers:
  - Stage 2 must connect existing aggregate/post-aggregate wrapper generation to the stage plan without SQL regression.
  - Stage 3 must make CTE versus derived-table rendering a dialect decision, especially for MySQL 5.7.
  - Stage 4 must define `returnTotal` count source from stage metadata and align preAgg/AggSql consumers.
- acceptance readiness: ready for Stage 1 review, not ready for release-level signoff.
- self-check conclusion: Stage 1 is complete and ready to proceed to Stage 2.

### Stage 2 Checkpoint - 2026-07-09

- completed work summary:
  - Changed `JdbcModelQueryEngine` post-aggregate SQL rendering to consume `QueryStagePlan` instead of relying only on local wrapper flags.
  - Added `QueryStagePlan` helper methods for stage lookup and render-strategy checks.
  - Added derived-table fallback for post-aggregate queries when the dialect does not support CTE.
  - Added a fail-closed guard for the currently unsupported combination of CTE-based domain transport and derived-table stage fallback.
  - Added tests that compare rendered SQL shape with `queryStagePlan.renderStrategy` and execute the no-CTE derived-table fallback.
- touched code paths:
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/stage/QueryStagePlan.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/JdbcModelQueryEngineCteWrapTest.java`
- self-check checklist:
  - post-aggregate renderer consumes planner metadata: completed.
  - CTE rendering path remains compatible: completed.
  - no-CTE derived fallback covered by test fixture: completed.
  - Step loop still not used as planner: completed.
  - returnTotal/count-stage semantics resolved: pending.
  - window result-stage renderer migrated: pending.
  - true MySQL 5.7 and SQL Server evidence captured: pending.
- verification:
  - `mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test`: pass, 19 tests.
  - `mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest,QueryRequestValidationStepTest,JdbcModelQueryEngineCteWrapTest test`: pass, 60 tests.
- remaining risks / blockers:
  - `returnTotal` still needs explicit final-count-stage metadata and SQL consumer alignment.
  - Window-function support is still partly diagnostic-only; Stage 3 must migrate the window result-stage renderer and make unsupported dialects fail closed.
  - MySQL 5.7 compatibility is not release-proven until a real MySQL 5.7 execution baseline is captured.
- acceptance readiness: ready for Stage 2 checkpoint review, not ready for release-level signoff.
- self-check conclusion: Stage 2 post-aggregate renderer checkpoint is complete; continue Stage 2 with `returnTotal` and count-stage semantics before broadening to Stage 3.

### Stage 2 Count-Stage Checkpoint - 2026-07-09

- completed work summary:
  - Added explicit final-stage count metadata to `QueryStagePlan` diagnostics through `countSqlInput` and `aggSqlOptimizationPolicy`.
  - Centralized `aggSql` construction behind the stage plan so stage-boundary queries preserve the final semantic SQL when building totals.
  - Kept `AggSqlOptimizer` enabled for compatible single-stage queries and disabled it for multi-stage boundary queries until a stage-aware optimizer is introduced.
  - Added tests proving post-aggregate, derived fallback, and window post-slice totals count the final rendered result set.
- touched code paths:
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/stage/QueryStagePlan.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/JdbcModelQueryEngineCteWrapTest.java`
- self-check checklist:
  - final count-stage metadata exposed: completed.
  - stage-boundary `returnTotal` preserves final result semantics: completed for covered fixtures.
  - single-stage aggregate optimization remains allowed: completed.
  - Step loop still not used as planner: completed.
  - stage-aware optimized count SQL restored for performance: pending Stage 4.
  - true MySQL 5.7 and SQL Server evidence captured: pending.
- verification:
  - `mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test`: pass, 20 tests.
  - `mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest,QueryRequestValidationStepTest,JdbcModelQueryEngineCteWrapTest test`: pass, 61 tests; configured `test-mysql` execution completed successfully.
- remaining risks / blockers:
  - Stage 3 still needs to migrate the window/result-stage renderer to consume planner metadata directly.
  - Stage 3 must make unsupported dialect/window combinations fail closed instead of relying on legacy wrapper behavior.
  - Stage 4 should reintroduce safe optimized count SQL for stage plans where the optimizer can prove semantic equivalence.
- acceptance readiness: Stage 2 is complete at engine-fixture level, but not ready for release-level signoff until Stage 3/4 dialect evidence is captured.
- self-check conclusion: Stage 2 can close; proceed to Stage 3 window/result-stage integration.
