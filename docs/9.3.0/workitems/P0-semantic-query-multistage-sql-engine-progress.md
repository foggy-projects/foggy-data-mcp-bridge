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
- active_stage: Stage 1 - Planner Skeleton
- scope: introduced read-only stage planning diagnostics without changing default SQL rendering.
- next_stage: Stage 2 - Aggregate And Post-Aggregate Stage Builders
- non_goals_this_pass: no plan-driven renderer enablement, no Step loop planning, no public API contract change.

## Stage Progress

| Stage | Status | Notes |
|---|---|---|
| Stage 0 - Baseline And Regression Fence | completed | Existing issue #120 guard and targeted regression tests are present. |
| Stage 1 - Planner Skeleton | completed | Added planner DTOs, classifier, diagnostics, and `ModelResultContext.extData["queryStagePlan"]` metadata exposure. |
| Stage 2 - Aggregate And Post-Aggregate Stage Builders | pending | Requires stable `queryStagePlan` metadata first. |
| Stage 3 - Window And Result Stage Integration | pending | Requires Stage 2 renderer abstraction and dialect fallback. |
| Stage 4 - PreAgg, AggSql, Compose, And Dialect Hardening | pending | Requires planned final count stage metadata. |
| Stage 5 - Default Enablement And Cleanup | pending | Requires full dialect/test evidence and quality gates. |

## Development Progress

- Stage 1 completed.
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
  - ordered `stages`
  - per-stage `id`, `type`, `inputAliases`, `outputAliases`, `filterAliases`, `orderAliases`, `requiresSqlBoundary`
- Stage 1 does not change SQL rendering behavior; existing wrapper paths still render SQL.

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
- SQL Server status: pending.
- MySQL 5.7 derived fallback status: pending.
- release-level test: pending.

## Experience Progress

- N/A: backend query engine architecture only, no UI or manual interaction surface changed.

## Acceptance Criteria Tracking

| Criteria | Status | Evidence |
|---|---|---|
| No nested aggregate for issue #120 variants | partial | Existing regression tests pass before Stage 1. |
| Legal aggregate expressions remain single-stage where valid | partial | Existing regression tests pass before Stage 1. |
| Aggregate alias references visible only downstream | partial | Stage 1 tests assert `teamSales` as post-aggregate input and `salesShare` as downstream output/filter metadata. |
| Deterministic stage diagnostics | partial | Stage 1 tests assert stage order and `renderStrategy`; broader dialect coverage remains pending. |
| Existing single-stage behavior compatible | partial | Stage 1 tests assert single-stage aggregate stays `renderStrategy=single` and does not introduce CTE SQL. |
| MySQL 5.7 no unsupported CTE | pending | Stage 2/3 renderer work. |
| `returnTotal` final semantic row set | pending | Stage 4 work. |
| Step loop not used as planner | partial | Stage 1 uses dedicated `QueryStagePlanner`; renderer and execution steps still need later confirmation. |

## Execution Check-In

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
