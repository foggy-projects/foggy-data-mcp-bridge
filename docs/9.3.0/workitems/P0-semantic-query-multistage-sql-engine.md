---
type: architecture-hardening
version: 9.3.0
ticket: P0-semantic-query-multistage-sql-engine
priority: P0
status: completed_with_risks
owner: foggy-dataset-model
source: github-issue-120-followup
test_strategy: integration-test
automation_decision: required
experience: N/A
---

# P0 Semantic Query Multi-Stage SQL Engine

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track the 9.3.0 long-term implementation plan for explicit multi-stage SQL planning in the semantic query engine.

## Background

The semantic layer engine is a foundation component for downstream company projects. It must be stable, maintainable, and predictable across dialects and query shapes.

GitHub issue #120 showed that the current query engine can generate illegal nested aggregate SQL when an inline aggregate alias reuses the same name as a model field. SQL Server support was not the root cause. SQL Server supports `SUM(a) / SUM(b)`. The engine problem was alias and aggregation stage confusion that could produce `SUM(SUM(a))`.

The immediate regression fix is useful and should remain, but 9.3.0 should address the architectural cause.

## Target Outcome

Build a deterministic multi-stage SQL planning architecture for JDBC query generation.

The engine should explicitly plan row, aggregate, post-aggregate, window/result, and final stages before rendering SQL. Each stage should have a clear symbol table and ownership boundary. SQL generation should consume the plan instead of repeatedly inferring stage ownership from calculated-field flags during rendering.

## Relation To Version Goal

This work is the P0 architecture item for 9.3.0. It supports the version goal of making the semantic query engine a stable shared base for internal projects.

## Constraints

- Preserve public `query_model` request compatibility unless an existing behavior is invalid SQL or already fail-closed.
- Preserve single-stage SQL for legal simple queries.
- Do not introduce a Step loop as the primary planner.
- Reuse existing dialect, expression, visitor, and structured CTE infrastructure where possible.
- Treat MySQL 5.7 as a first-class compatibility target. Planner-driven multi-stage SQL must not emit `WITH` when `FDialect.supportsCte()` is false.
- Treat `returnTotal` as a stage-aware contract: total row count is computed after all semantic filters in the final planned row set and before final `orderBy` and pagination.
- Expose planner diagnostics through stable metadata before enabling plan-driven rendering by default.
- Follow the repository rule that query-chain changes must include real SQL data comparison tests.
- Do not change UI, REST response envelope, MCP endpoint contracts, or model file syntax in this work item.

## Non-Goals

- No general-purpose SQL optimizer.
- No cross-datasource query execution.
- No recursive CTE support.
- No new public DSL syntax unless a later work item explicitly scopes it.
- No broad refactor of compose_script runtime suspension or pipeline loop hooks.

## Root Cause Summary

Issue #120 was caused by two design gaps:

- Alias visibility was not stage-aware. An aggregate alias could shadow a physical model field with the same name while compiling another aggregate expression.
- Aggregation metadata had double duty. It was used both as group metadata and as an instruction to wrap an expression, which made already-aggregate fragments vulnerable to double wrapping.

The immediate fix added guards in expression resolution and aggregate wrapping. The long-term fix is to make alias scope and aggregate stage ownership explicit.

## Architecture Decision

Implement a planner-first SQL generation flow:

1. Normalize and validate the request.
2. Classify every expression, filter, order item, and calculated field into a stage.
3. Build a `QueryStagePlan` with stage-local symbol tables.
4. Render each planned stage with dialect-aware builders.
5. Execute existing query execution steps against the rendered SQL.

The planner should be deterministic: the same request, model, dialect, and feature flags must produce the same stage plan or the same fail-closed error.

## Hard Decisions From Review

- CTE fallback is not a late hardening task. Stage 2 and Stage 3 must render derived-table fallback for dialects without CTE support before those stages can be considered complete.
- Same-stage alias references follow one rule: automatically split only when the expression can be safely evaluated from earlier-stage aliases in a downstream stage; otherwise fail closed with a stage-aware error.
- `returnTotal` counts the final semantic row set after row `slice`, aggregate `having`, post-aggregate filters, window/result-stage filters, and `postSlice`, excluding only final ordering and pagination.
- Pre-aggregation aggregate SQL may replace `returnTotal` only when it targets the same planned count stage. If post-aggregate or window/result filters cannot be represented by the pre-aggregation path, the pre-aggregation count optimization must be bypassed.
- Planner metadata is part of the internal engine contract for 9.3.0 tests and execution interceptors.

## Module Responsibility

| Module | Responsibility | Can Start Now | Dependencies |
|---|---|---|---|
| root workspace docs | Track version goal, work item, detailed design, progress, quality, coverage, and acceptance evidence. | yes | none |
| `foggy-dataset-model` | Own stage planning, expression classification, symbol table, query engine orchestration, regression and integration tests. | yes | existing module `pom.xml` and `CLAUDE.md` constraints |
| `foggy-dataset` | Provide dialect capability checks only if existing `FDialect` APIs are insufficient. | after planner design confirms a gap | avoid broad dialect rewrite |
| compose query compiler | Consume structured stage output and preserve CTE flattening/parity. | after planner skeleton | `SqlGenerationResult.CteStage` compatibility |
| pre-aggregation/cache execution steps | Adapt to stage metadata for `returnTotal` and optimized aggregate SQL. | after stage plan metadata exists | planner stage output |

## Code Inventory

```yaml
code_inventory:
  - repo: foggy-data-mcp-bridge
    path: docs/9.3.0/README.md
    role: version tracking entry
    expected_change: create
    notes: created in this planning task

  - repo: foggy-data-mcp-bridge
    path: docs/9.3.0/detailed_design/00_semantic_query_multistage_sql_engine.md
    role: architecture design
    expected_change: create
    notes: created in this planning task

  - repo: foggy-data-mcp-bridge
    path: docs/9.3.0/workitems/P0-semantic-query-multistage-sql-engine.md
    role: requirement, implementation plan, progress template
    expected_change: create
    notes: created in this planning task

  - repo: foggy-data-mcp-bridge
    path: foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java
    role: current orchestration and ad hoc single/CTE/post-aggregate SQL generation
    expected_change: update
    notes: should delegate stage decisions to a planner instead of owning all stage checks directly

  - repo: foggy-data-mcp-bridge
    path: foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/expression
    role: calculated field compile, SQL fragment metadata, expression dependency extraction
    expected_change: update
    notes: should expose stage classification inputs and stage-aware symbol resolution

  - repo: foggy-data-mcp-bridge
    path: foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query
    role: low-level JDBC query model and SQL visitor
    expected_change: update
    notes: visitor should render one relational stage; avoid embedding planner logic here

  - repo: foggy-data-mcp-bridge
    path: foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/stage
    role: planned package for QueryStagePlanner, QueryStagePlan, stage symbol table, stage builders
    expected_change: create
    notes: module placement validated against root CLAUDE.md and foggy-dataset-model/pom.xml; no new module dependency required; include diagnostics DTO or serializable debug map

  - repo: foggy-data-mcp-bridge
    path: foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter
    role: request validation and normalization steps
    expected_change: update
    notes: keep as lifecycle normalization; do not turn Step chain into the SQL planner

  - repo: foggy-data-mcp-bridge
    path: foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/query_execution
    role: SQL execution interceptors, pre-aggregation rewrite, optional bounded loop hooks
    expected_change: update
    notes: may consume stage metadata; must not become multi-stage SQL planner

  - repo: foggy-data-mcp-bridge
    path: foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce
    role: real SQL fixture regression tests
    expected_change: update
    notes: add issue #120, alias collision, post-aggregate, window/result-stage, returnTotal, MySQL derived fallback, and SQL Server coverage

  - repo: foggy-data-mcp-bridge
    path: foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/compose
    role: compose query integration and CTE compatibility coverage
    expected_change: update
    notes: verify structured CTE output remains compatible
```

Module placement validation:

- Root `CLAUDE.md` identifies `foggy-dataset-model` as the core TM/QM engine module.
- `foggy-dataset-model/pom.xml` already depends on `foggy-dataset` and test fixtures; a new internal `engine/stage` package does not introduce a module cycle.
- No implementation logic should be placed in launcher, demo, or MCP endpoint modules for this work item.

## Implementation Plan

### Stage 0: Baseline And Regression Fence

- Keep the immediate issue #120 fix in place.
- Add or preserve tests that prove same-name aggregate aliases do not generate nested aggregates.
- Add negative and positive tests for stage visibility:
  - direct `sum(profitAmount) / sum(salesAmount)` remains legal in aggregate stage;
  - `profitAmount / salesAmount` over aggregate aliases requires post-aggregate stage;
  - same-stage aggregate alias reference is auto-split only when all references are previous-stage aliases and the expression is safe for downstream evaluation;
  - unsafe same-stage alias references fail closed with a stage-aware error.

Completion evidence:

- Targeted tests pass.
- Generated SQL and `aggSql` contain no nested aggregate in covered cases.
- Real SQL result values are compared with native SQL baselines.

### Stage 1: Planner Skeleton

- Add stage enums, symbol table, stage plan, and diagnostics.
- Classify expressions without changing default SQL generation.
- Expose planned stage debug metadata in `ModelResultContext.extData` or query debug output.
- Add unit tests for classification and alias visibility.

Completion evidence:

- Planner classification tests pass.
- Existing query tests remain unchanged.
- Planner diagnostics explain why each expression belongs to a stage and are exposed through the `queryStagePlan` debug metadata contract.

### Stage 2: Aggregate And Post-Aggregate Stage Builders

- Move post-aggregate decision logic from `JdbcModelQueryEngine` into plan-driven builders.
- Render aggregate-stage aliases into a derived/CTE stage selected by dialect capability.
- Implement MySQL 5.7 derived-table fallback before enabling the post-aggregate builder for that dialect.
- Render expressions that depend on aggregate aliases downstream.
- Preserve current `postAggregateCalculations` behavior.
- Define `returnTotal` count stage for aggregate and post-aggregate requests.

Completion evidence:

- Post-aggregate tests pass with real SQL execution.
- `postSlice` over post-aggregate aliases filters the result stage only.
- `returnTotal` counts the expected final semantic row set.
- MySQL 5.7 path does not emit `WITH` for post-aggregate multi-stage SQL.

### Stage 3: Window And Result Stage Integration

- Migrate current window CTE wrapping into the same planner.
- Support final result-stage filters after window columns.
- Keep window and post-aggregate mixed cases fail-closed until explicitly supported, or define a deterministic stage sequence if support is added.
- Implement dialect capability checks for CTE and window functions before rendering. If a dialect cannot support the required window semantics through native window functions or an approved fallback, fail closed with a dialect-specific stage error.

Completion evidence:

- Window CTE tests pass.
- MySQL 5.7 path either uses a documented non-CTE fallback for supported result-stage cases or fails closed before invalid SQL is emitted.
- Existing unsupported mixed cases produce clear stage-aware errors.
- Compose CTE flattening continues to work.

### Stage 4: PreAgg, AggSql, Compose, And Dialect Hardening

- Ensure `AggSqlOptimizer`, `PreAggRewriteStep`, and pre-aggregation aggregate SQL consume the correct planned stage.
- Verify SQL Server, PostgreSQL, MySQL, SQLite identifier quoting and CTE/derived table behavior.
- Preserve structured `SqlGenerationResult.CteStage` for compose query integration.
- Preserve `queryStagePlan` diagnostics in `SqlGenerationResult` so compose consumers can read stage metadata directly.
- Add `preAggOptimizationPolicy`; skip pre-aggregation when the final semantic stage must be preserved, except for explicitly proven `return-total-equivalent-only` aggregate SQL.
- Verify `queryStagePlan` metadata remains available after pre-aggregation/cache execution steps.

Completion evidence:

- Targeted compose, pre-aggregation, and dialect tests pass.
- Pre-aggregation does not rewrite main SQL for post-aggregate/window result-stage queries that require final-stage preservation. ReturnTotal preAgg aggregate SQL is allowed only for the proven `return-total-equivalent-only` path and must fail closed otherwise.
- SQL Server profile is run where available.
- If any dialect remains not-run, the reason is recorded in progress.

### Stage 5: Default Enablement And Cleanup

- Enable plan-driven renderer for covered cases by default.
- Remove duplicated ad hoc stage checks from `JdbcModelQueryEngine`.
- Keep explicit fallback for unsupported legacy shapes only if documented.
- Run quality, coverage, and acceptance checks.

Completion evidence:

- `mvn -pl foggy-dataset-model test` passes or all failures are fixed before completion.
- Implementation quality gate is recorded under `docs/9.3.0/quality`.
- Coverage audit is recorded under `docs/9.3.0/coverage`.
- Acceptance signoff is recorded under `docs/9.3.0/acceptance`.

## Acceptance Criteria

- No nested aggregate is generated for issue #120 or same-name alias variants.
- Legal aggregate expressions remain legal and do not require unnecessary wrapping.
- Aggregate alias references are available only in downstream stages.
- Unsafe same-stage alias references fail closed; safe aggregate-alias expressions are automatically split only into a downstream stage.
- Stage plans are deterministic and diagnosable.
- `queryStagePlan` debug metadata exposes stage order, render strategy, stage aliases, output aliases, filter ownership, final count stage, and fallback/unsupported feature decisions.
- Existing single-stage query behavior remains compatible.
- Window, post-aggregate, result-stage filters, orderBy, pagination, and returnTotal are planned through one architecture.
- MySQL 5.7 multi-stage requests do not emit unsupported CTE SQL. Unsupported window semantics fail closed unless a tested fallback exists.
- `returnTotal` counts the final semantic row set after all planned filters and before ordering/pagination; pre-aggregation count SQL is used only when equivalent.
- Query execution Step loop hooks are not used as the SQL planning mechanism.
- Real SQL integration tests cover the critical behavior.

## Required Verification

Targeted implementation checks:

```bash
mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest test
mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test
mvn -pl foggy-dataset-model -Dtest=CalculatedFieldServiceTest,CalculatedFieldServiceFormulaTest,CalculatedFieldServiceDialectTest,SqlCalculatedFieldProcessorWindowOrderTest test
mvn -pl foggy-dataset-model -Dtest=ComposedDataSetResultIntegrationTest,ComposePlannerCteWrapTest,ComposeSqlCompilerTest test
```

Release-level check:

```bash
mvn -pl foggy-dataset-model test
```

SQL Server focused check when Docker/profile is available:

```bash
mvn -pl foggy-dataset-model -Dspring.profiles.active=sqlserver -Dtest=CalculatedFieldAggregationBugTest test
```

Additional evidence required before acceptance:

- issue #120 real SQL result comparison against native SQL baseline.
- `returnTotal` real SQL baseline for aggregate, post-aggregate filter, window/result filter, and `postSlice`.
- MySQL 5.7 derived-table fallback test proving no `WITH` is emitted when `supportsCte()` is false.
- SQL Server dialect execution or documented `not-run` reason plus SQL rendering evidence for identifier quoting, aggregate aliases, and stage wrappers.
- Planner metadata assertions against `queryStagePlan`, not only SQL string assertions.

## Progress Tracking

- development: completed_with_risks. Stage 1 through Stage 5 are implemented for the covered JDBC semantic query paths.
- testing: completed. Full `foggy-dataset-model` module regression passed with 3259 tests, 0 failures, 0 errors, and 3 skipped.
- experience: N/A, backend query engine architecture only.
- quality: ready-with-risks. See `../quality/semantic-query-multistage-sql-engine-implementation-quality.md`.
- coverage: ready-with-gaps. See `../coverage/semantic-query-multistage-sql-engine-coverage-audit.md`.
- acceptance readiness: accepted-with-risks. See `../acceptance/semantic-query-multistage-sql-engine-acceptance.md`.
- remaining risks: SQL Server profile was not executed; true MySQL 5.7 server execution evidence remains pending.

## Implementation Self-Check Template

Before marking any stage complete, update this section or a companion progress record with:

- scope conformance: pending.
- non-goals preserved: pending.
- changed code paths listed: pending.
- tests executed and results: pending.
- SQL Server status: pending.
- MySQL 5.7 derived fallback status: pending.
- `returnTotal` semantic stage status: pending.
- planner metadata assertion status: pending.
- real SQL data comparison status: pending.
- fallback or unsupported cases documented: pending.
- self-check conclusion: pending.

## Review And Audit Workflow

- After a stage implementation finishes, run a lightweight implementation self-check and update progress.
- Because this is a core shared engine path, formal `foggy-implementation-quality-gate` is required before coverage audit.
- Run `foggy-test-coverage-audit` after the quality gate.
- Run `foggy-acceptance-signoff` after coverage evidence is complete.
