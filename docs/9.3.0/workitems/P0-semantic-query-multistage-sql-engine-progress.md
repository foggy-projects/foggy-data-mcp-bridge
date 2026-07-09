---
type: progress
version: 9.3.0
ticket: P0-semantic-query-multistage-sql-engine
status: completed_with_risks
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
- active_stage: Stage 5 - Default Enablement And Cleanup
- scope: completed Stage 5 cleanup, P0-P2 preAgg follow-up hardening, full module regression, and quality/coverage/acceptance records.
- next_stage: resolve SQL Server profile connectivity, capture true MySQL 5.7 server execution, and broaden stage-aware preAgg equivalence only after additional proof.
- non_goals_this_pass: no Step loop planning, no public API contract change, no release-level SQL Server signoff.

## Stage Progress

| Stage | Status | Notes |
|---|---|---|
| Stage 0 - Baseline And Regression Fence | completed | Existing issue #120 guard and targeted regression tests are present. |
| Stage 1 - Planner Skeleton | completed | Added planner DTOs, classifier, diagnostics, and `ModelResultContext.extData["queryStagePlan"]` metadata exposure. |
| Stage 2 - Aggregate And Post-Aggregate Stage Builders | completed | Post-aggregate renderer consumes planner `renderStrategy`; final-stage count SQL metadata and multi-stage `aggSql` policy are exposed in diagnostics. |
| Stage 3 - Window And Result Stage Integration | completed | Window result-stage plans now fail closed when window functions or required CTE rendering are unsupported. |
| Stage 4 - PreAgg, AggSql, Compose, And Dialect Hardening | completed | Added `preAggOptimizationPolicy`, skipped unsafe preAgg paths for final-stage plans, and preserved stage diagnostics in `SqlGenerationResult`. |
| Stage 5 - Default Enablement And Cleanup | completed_with_risks | Planner owns remaining stage detection and renderer dispatch; full module tests pass; quality, coverage, and acceptance records are written. SQL Server profile was attempted but blocked by datasource connectivity; true MySQL 5.7 server evidence remains a follow-up risk. |

## Development Progress

- Stage 1 completed.
- Stage 2 checkpoint completed for post-aggregate SQL rendering.
- Stage 3 completed for window/result-stage dialect fail-closed behavior.
- Stage 4 completed for preAgg/Compose metadata hardening.
- Stage 5 completed for default enablement cleanup and release evidence.
- Implemented code touchpoints:
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/stage`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/query_execution/PreAggRewriteStep.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/JdbcQueryModelImpl.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/SqlGenerationResult.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/support/DslCteDslRequestMapper.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/JdbcModelQueryEngineCteWrapTest.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/odoo/OdooModelLoadingTest.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationEdgeCaseTest.java`
- Diagnostics metadata currently includes:
  - `version`
  - `renderStrategy`
  - `returnTotalStrategy`
  - `finalCountStageId`
  - `countSqlInput`
  - `aggSqlOptimizationPolicy`
  - `preAggOptimizationPolicy`
  - ordered `stages`
  - per-stage `id`, `type`, `inputAliases`, `outputAliases`, `filterAliases`, `orderAliases`, `requiresSqlBoundary`
  - `fallbacks`
  - `unsupported`
- `unsupported` currently records fail-closed planner reasons including:
  - `post-slice-result-stage-required`
  - `post-aggregate-window-mix-unsupported`
  - `window-functions-unsupported`
  - `window-derived-rendering-unsupported`
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
- Stage 3 window/result-stage hardening is implemented:
  - Dialects without window-function support fail closed with `WINDOW_RESULT_STAGE_WINDOW_FUNCTION_UNSUPPORTED`.
  - Window result-stage queries that would require unsupported derived rendering fail closed with `WINDOW_RESULT_STAGE_DERIVED_RENDERING_UNSUPPORTED`.
  - `unsupported` diagnostics record the stage-plan reason before the exception is thrown.
- Stage 4 preAgg/Compose hardening is implemented:
  - `preAggOptimizationPolicy=skip-final-stage-required` is emitted when a query must preserve final semantic SQL.
  - `PreAggRewriteStep` skips both main-query preAgg and returnTotal preAgg aggregate SQL for final-stage-required plans.
  - `SqlGenerationResult.diagnostics` carries the `ModelResultContext.extData` snapshot, including `queryStagePlan`, for compose consumers.
  - DSL CTE top-level limit wrapping preserves `SqlGenerationResult` diagnostics.
- P0-P2 follow-up preAgg restoration and fail-closed hardening is implemented:
  - `preAggOptimizationPolicy=return-total-equivalent-only` is emitted only when the plan requires final-stage SQL, `returnTotal` uses `final-stage-count`, and no post-aggregate/window result-stage filter is present.
  - Main-query preAgg remains skipped for final-stage-required plans, preserving final projection and calculated-field semantics.
  - ReturnTotal preAgg aggregate SQL is restored for the bounded equivalent path by rebuilding a preAgg-backed grouped rollup and counting that rollup result set.
  - Final-stage plans with post-aggregate/window result filters still skip both main and aggregate preAgg paths.
  - The final-stage equivalent builder fails closed when it cannot map the actual planned group fields/measures to preAgg columns, when a same-dimension property is not declared by the preAgg, when only part of the groupBy can be mapped, or when the matched preAgg path is hybrid.
  - Equivalent aggregate diagnostics distinguish `preAggAggregateUsed`, `preAggAggregateMode=final-stage-equivalent`, `preAggAggregateSkippedByStagePlan`, and `preAggAggregateSkipReason`.
- Stage 5 default enablement cleanup is implemented:
  - `QueryStagePlanner` owns window, post-aggregate, and postSlice feature detection.
  - `JdbcModelQueryEngine` dispatches renderers through `QueryStagePlan.requiresPostAggregateRenderer()` and `QueryStagePlan.requiresWindowResultRenderer()`.
  - PostSlice without a result-stage producer and postAggregate/window mixed plans fail closed through planner diagnostics.
  - Odoo post-aggregate SQL tests accept either CTE or derived-table stage shape according to dialect capability.

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
- Stage 3 targeted tests:
  - `mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test`
  - result: pass, 22 tests; the Maven execution also completed the project's configured `test-mysql` phase successfully.
- Stage 4 preAgg/window targeted tests:
  - `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest,JdbcModelQueryEngineCteWrapTest test`
  - result: pass, 31 tests.
- Stage 4 compose regression:
  - `mvn -pl foggy-dataset-model -Dtest=ComposePlannerCteWrapTest,ComposeSqlCompilerTest,ComposedDataSetResultIntegrationTest test`
  - result: pass, 19 tests.
- Stage 4 regression fence:
  - `mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest,QueryRequestValidationStepTest,JdbcModelQueryEngineCteWrapTest,PreAggregationEdgeCaseTest test`
  - result: pass, 72 tests; configured `test-mysql` execution completed successfully.
- Stage 5 compile:
  - `mvn -pl foggy-dataset-model -DskipTests compile`
  - result: pass.
- Stage 5 targeted renderer tests:
  - `mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test`
  - result: pass, 24 tests.
- Stage 5 preAgg/window targeted tests:
  - `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest,JdbcModelQueryEngineCteWrapTest test`
  - result: pass, 31 tests.
- P0-P2 follow-up preAgg strictness and stage evidence:
  - `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest test`
  - result: pass, 13 tests, 0 failures, 0 errors, 0 skipped.
  - `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest,JdbcModelQueryEngineCteWrapTest test`
  - result: pass, 37 tests, 0 failures, 0 errors, 0 skipped; the configured second surefire execution also passed 37 tests.
  - `mvn -pl foggy-dataset-model -P!multi-db -Dspring.profiles.active=docker -Dtest=JdbcModelQueryEngineCteWrapTest test`
  - result: pass, 24 tests, 0 failures, 0 errors, 1 skipped.
  - `mvn -pl foggy-dataset-model -P!multi-db -Dspring.profiles.active=sqlserver -Dtest=JdbcModelQueryEngineCteWrapTest test`
  - result: blocked by datasource connectivity. Active profile was `sqlserver`; JDBC failed during SQL Server pre-login with connection reset on `localhost:11433`. Maven reported 24 tests run, 0 failures, 6 errors, 1 skipped.
- Stage 5 compose regression:
  - `mvn -pl foggy-dataset-model -Dtest=ComposePlannerCteWrapTest,ComposeSqlCompilerTest,ComposedDataSetResultIntegrationTest test`
  - result: pass, 19 tests.
- Stage 5 regression fence:
  - `mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest,QueryRequestValidationStepTest,JdbcModelQueryEngineCteWrapTest,PreAggregationEdgeCaseTest test`
  - result: pass, 72 tests; configured `test-mysql` execution completed successfully.
- Stage 5 Odoo post-aggregate shape regression:
  - `mvn -pl foggy-dataset-model -Dtest=OdooModelLoadingTest test`
  - result: pass, 38 tests.
- Stage 5 release-level test:
  - `mvn -pl foggy-dataset-model test`
  - result: pass, 3259 tests, 0 failures, 0 errors, 3 skipped.
- SQL Server status: attempted_blocked; the focused profile reached `localhost:11433` but failed during JDBC pre-login connection reset before model loading.
- MySQL 5.7 derived fallback status: partial; no-CTE derived fallback is covered with a `NoCteSqliteDialect` fixture, MySQL dialect regression shape tests, and docker profile execution, but true MySQL 5.7 server execution evidence is still pending.
- release-level test: completed.

## Experience Progress

- N/A: backend query engine architecture only, no UI or manual interaction surface changed.

## Acceptance Criteria Tracking

| Criteria | Status | Evidence |
|---|---|---|
| No nested aggregate for issue #120 variants | completed | Regression fences and full module tests pass. |
| Legal aggregate expressions remain single-stage where valid | completed | Single-stage aggregate diagnostics and full module tests pass. |
| Aggregate alias references visible only downstream | completed | Stage diagnostics and post-aggregate rendering tests assert downstream ownership. |
| Deterministic stage diagnostics | completed | Tests assert stage order, render strategy, count SQL policy, preAgg policy, unsupported reasons, and compose diagnostics transport. |
| Existing single-stage behavior compatible | completed | Full module regression passed. |
| MySQL 5.7 no unsupported CTE | partial | Derived fallback, MySQL dialect shape, and docker profile execution are covered; true MySQL 5.7 server execution evidence remains pending. |
| `returnTotal` final semantic row set | completed_for_fixtures | Tests compare final-stage `aggSql` behavior for post-aggregate, window postSlice, derived fallback, preAgg skip paths, and bounded equivalent preAgg aggregate paths. SQL Server is attempted-blocked; true MySQL 5.7 baseline remains pending. |
| PreAgg respects stage boundaries | completed_with_bounded_optimization | Stage 4 and Stage 5 tests verify unsafe preAgg paths are skipped for final-stage plans; P0-P2 follow-up restores returnTotal preAgg only for no-result-filter equivalent plans and proves fail-closed behavior for hybrid/unmappable cases. |
| Step loop not used as planner | completed | Planning remains in `QueryStagePlanner`; execution Step hooks only consume metadata. |

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

### Stage 3 Checkpoint - 2026-07-09

- completed work summary:
  - Added stage-plan unsupported diagnostics and validation before window/result-stage rendering.
  - Added fail-closed behavior for dialects that do not support required window functions.
  - Added fail-closed behavior for window result-stage plans when CTE is unavailable and no derived renderer is supported.
  - Kept the existing CTE window renderer active for supported dialects while making unsupported combinations explicit.
- commit:
  - `cda7baf8 feat: fail closed unsupported window stages`
- touched code paths:
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/stage/QueryStagePlan.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/stage/QueryStagePlanner.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/JdbcModelQueryEngineCteWrapTest.java`
- self-check checklist:
  - window-function capability gate: completed.
  - no-CTE window/result-stage fail-closed gate: completed.
  - unsupported diagnostics available before exception: completed.
  - Step loop still not used as planner: completed.
  - true SQL Server and MySQL 5.7 execution evidence captured: pending.
- verification:
  - `mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test`: pass, 22 tests; configured `test-mysql` execution completed successfully.
- remaining risks / blockers:
  - Window derived-table rendering remains unsupported and intentionally fail-closed until a dedicated renderer is implemented.
  - SQL Server profile remained pending in this historical checkpoint.
- acceptance readiness: Stage 3 is complete at engine-fixture level; release-level dialect signoff remains pending.
- self-check conclusion: Stage 3 can close; proceed to Stage 4 preAgg/Compose hardening.

### Stage 4 Checkpoint - 2026-07-09

- completed work summary:
  - Added `preAggOptimizationPolicy` to `QueryStagePlan` diagnostics.
  - Changed `PreAggRewriteStep` to skip both main-query preAgg rewrite and returnTotal preAgg aggregate SQL when the plan requires final-stage preservation.
  - Added `preAggSkippedByStagePlan` and `preAggSkipReason` metadata to `ModelResultContext.extData`.
  - Added `SqlGenerationResult.diagnostics` and preserved it through DSL CTE top-level limit wrapping.
  - Added tests for stage diagnostics, compose metadata transport, and preAgg skip behavior.
- touched code paths:
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/stage/QueryStagePlan.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/query_execution/PreAggRewriteStep.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/SqlGenerationResult.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/JdbcQueryModelImpl.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/support/DslCteDslRequestMapper.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/JdbcModelQueryEngineCteWrapTest.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationEdgeCaseTest.java`
- self-check checklist:
  - stage metadata exposes preAgg optimization policy: completed.
  - unsafe preAgg paths are skipped for final-stage plans: completed.
  - compose can consume `queryStagePlan` from SQL generation result diagnostics: completed.
  - Step loop still not used as planner: completed.
  - stage-aware preAgg equivalence optimization for safe multi-stage cases: pending future optimization.
  - true SQL Server and MySQL 5.7 execution evidence captured: pending.
- verification:
  - `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest,JdbcModelQueryEngineCteWrapTest test`: pass, 30 tests.
  - `mvn -pl foggy-dataset-model -Dtest=ComposePlannerCteWrapTest,ComposeSqlCompilerTest,ComposedDataSetResultIntegrationTest test`: pass, 19 tests.
  - `mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest,QueryRequestValidationStepTest,JdbcModelQueryEngineCteWrapTest,PreAggregationEdgeCaseTest test`: pass, 71 tests.
- remaining risks / blockers:
  - Current Stage 4 chooses correctness over preAgg optimization for final-stage plans; a future stage-aware preAgg equivalence proof is needed before re-enabling those optimizations.
  - SQL Server profile remained pending in this historical checkpoint.
  - Real MySQL 5.7 execution evidence remains pending; current coverage uses a no-CTE dialect fixture.
- acceptance readiness: Stage 4 is complete for planner metadata consumption and correctness hardening, but not ready for release-level signoff until dialect evidence and quality gates are complete.
- self-check conclusion: Stage 4 can close; proceed to Stage 5 default enablement, cleanup, and release evidence.

### Stage 5 Checkpoint - 2026-07-09

- completed work summary:
  - Moved remaining window, post-aggregate, and postSlice feature detection into `QueryStagePlanner`.
  - Changed `JdbcModelQueryEngine` renderer dispatch to use `QueryStagePlan.requiresPostAggregateRenderer()` and `QueryStagePlan.requiresWindowResultRenderer()`.
  - Added planner-level unsupported diagnostics for postSlice without a result-stage producer and postAggregate/window mixed plans.
  - Updated Odoo post-aggregate SQL shape assertions to accept CTE or derived-table stages according to dialect capability.
  - Ran full module regression and wrote quality, coverage, and acceptance records under `docs/9.3.0`.
- touched code paths:
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/stage/QueryStagePlan.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/stage/QueryStagePlanner.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/JdbcModelQueryEngineCteWrapTest.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/odoo/OdooModelLoadingTest.java`
  - `docs/9.3.0/quality/semantic-query-multistage-sql-engine-implementation-quality.md`
  - `docs/9.3.0/coverage/semantic-query-multistage-sql-engine-coverage-audit.md`
  - `docs/9.3.0/acceptance/semantic-query-multistage-sql-engine-acceptance.md`
- self-check checklist:
  - planner owns default stage detection for covered paths: completed.
  - engine renderer dispatch consumes planner metadata: completed.
  - unsupported mixed/result-stage cases are diagnosable before fail-closed exception: completed.
  - Odoo/MySQL dialect shape assertions are compatible with derived fallback: completed.
  - full module regression completed: completed.
  - quality, coverage, and acceptance records written: completed.
  - Step loop still not used as planner: completed.
  - SQL Server profile execution evidence captured: pending follow-up.
  - true MySQL 5.7 server execution evidence captured: pending follow-up.
- verification:
  - `mvn -pl foggy-dataset-model -DskipTests compile`: pass.
  - `mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test`: pass, 24 tests.
  - `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest,JdbcModelQueryEngineCteWrapTest test`: pass, 31 tests.
  - `mvn -pl foggy-dataset-model -Dtest=ComposePlannerCteWrapTest,ComposeSqlCompilerTest,ComposedDataSetResultIntegrationTest test`: pass, 19 tests.
  - `mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest,QueryRequestValidationStepTest,JdbcModelQueryEngineCteWrapTest,PreAggregationEdgeCaseTest test`: pass, 72 tests; configured `test-mysql` execution completed successfully.
  - `mvn -pl foggy-dataset-model -Dtest=OdooModelLoadingTest test`: pass, 38 tests.
  - `mvn -pl foggy-dataset-model test`: pass, 3259 tests, 0 failures, 0 errors, 3 skipped.
- remaining risks / blockers:
  - SQL Server profile remained pending at this Stage 5 checkpoint; it was later attempted in the P0-P2 follow-up and blocked by datasource connectivity.
  - True MySQL 5.7 server execution evidence remains pending; current evidence covers no-CTE and MySQL dialect rendering behavior inside the module test profile.
  - Broad stage-aware preAgg equivalence optimization remains a future performance enhancement; current behavior intentionally preserves correctness by skipping unsafe final-stage preAgg paths.
- acceptance readiness: accepted-with-risks for 9.3.0 covered paths.
- self-check conclusion: Stage 5 is complete with documented dialect evidence gaps and is ready for commit/push.

### P0-P2 Follow-Up Checkpoint - 2026-07-09

- completed work summary:
  - Restored a bounded stage-aware preAgg path for `returnTotal` when the final stage adds only projection/derived values and does not add post-aggregate/window result filters.
  - Kept main-query preAgg disabled for final-stage-required plans so the visible result SQL continues to preserve stage semantics.
  - Added `return-total-equivalent-only` diagnostics to distinguish safe aggregate-only restoration from unsafe final-stage preAgg rewrites.
  - Built final-stage equivalent aggregate SQL from mapped preAgg dimensions/measures and fail closed when the mapping cannot be proven.
  - Tightened the final-stage preAgg proof to use actual JDBC group columns before request `groupBy`, avoiding `AutoGroupByStep` helper aliases from polluting the equivalence check.
  - Added negative coverage for hybrid preAgg, unmappable group fields, undeclared same-dimension properties, undeclared measures, and partially mappable groupBy lists.
  - Preserved the existing skip behavior for result-filter final-stage plans.
- touched code paths:
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/stage/QueryStagePlan.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/query_execution/PreAggRewriteStep.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/preagg/PreAggregationInterceptor.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/preagg/FinalStagePreAggAggregateSqlBuilder.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/preagg/PreAggQueryRewriter.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationEdgeCaseTest.java`
- self-check checklist:
  - unsafe result-filter preAgg skip remains protected: completed.
  - safe no-result-filter returnTotal equivalent preAgg is restored: completed for covered fixture.
  - final-stage equivalent SQL does not count raw preAgg physical rows directly: completed.
  - unsupported/unprovable preAgg equivalent cases fail closed: completed.
  - external docker profile evidence captured: completed for available docker profile.
  - true SQL Server and MySQL 5.7 execution evidence captured: SQL Server attempted-blocked; true MySQL 5.7 pending follow-up.
- verification:
  - `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest test`: pass, 13 tests, 0 failures, 0 errors, 0 skipped.
  - `mvn -pl foggy-dataset-model -Dtest=PreAggregationEdgeCaseTest,JdbcModelQueryEngineCteWrapTest test`: pass, 37 tests, 0 failures, 0 errors, 0 skipped; configured second surefire execution also passed 37 tests.
  - `mvn -pl foggy-dataset-model -P!multi-db -Dspring.profiles.active=docker -Dtest=JdbcModelQueryEngineCteWrapTest test`: pass, 24 tests, 0 failures, 0 errors, 1 skipped.
  - `mvn -pl foggy-dataset-model -P!multi-db -Dspring.profiles.active=sqlserver -Dtest=JdbcModelQueryEngineCteWrapTest test`: blocked by SQL Server pre-login connection reset on `localhost:11433`; Maven reported 24 tests run, 0 failures, 6 errors, 1 skipped.
- remaining risks / blockers:
  - The restored preAgg equivalence path is intentionally narrow; it does not cover final-stage filters, mixed post-aggregate/window plans, hybrid preAgg, or cases where preAgg group/measure mapping is not provable.
  - SQL Server profile cannot be signed off until the `localhost:11433` datasource accepts connections and model loading succeeds.
  - True MySQL 5.7 server execution evidence remains pending.
- acceptance readiness: ready-with-risks for the bounded P1 preAgg restoration.
- self-check conclusion: P0 correctness remains protected, P1 preAgg performance recovery is partially restored for the proven no-result-filter returnTotal path, and P2 fail-closed proof coverage now protects the risky preAgg mapping edges.
