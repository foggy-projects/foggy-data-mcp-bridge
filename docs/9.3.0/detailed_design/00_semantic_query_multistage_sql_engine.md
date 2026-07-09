---
doc_role: detailed_design
version: 9.3.0
status: planned
owner: foggy-dataset-model
created_at: 2026-07-09
updated_at: 2026-07-09
---

# Semantic Query Multi-Stage SQL Engine

## Document Purpose

- doc_type: detailed-design
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Define the long-term SQL stage planning architecture for the JDBC semantic query engine.

## Background

GitHub issue #120 exposed a structural weakness in the current query engine: aggregate aliases, physical model fields, inferred aggregation metadata, post-aggregate fields, and window/result-stage wrappers are all inferred through local flags while SQL is being generated.

The issue was not caused by SQL Server lacking support for `SUM(a) / SUM(b)`. SQL Server supports that expression. The invalid shape is nested aggregation such as `SUM(SUM(a))`, and the engine generated that shape because an inline aggregate alias that reused a model field name was later resolved as the aggregate alias instead of the underlying model column.

The short-term fix protects this case, but the long-term engine should make stage boundaries explicit.

## Architecture Decision

Introduce a deterministic `QueryStagePlanner` for JDBC query generation.

The planner must classify every selected field, calculated field, slice, having condition, order item, result-stage filter, and `returnTotal` query into explicit SQL stages before SQL rendering starts. Rendering then consumes a completed `QueryStagePlan`; it must not rediscover stage ownership by mutating `DbQueryRequestDef` while SQL is being emitted.

## Stage Model

The planner should produce a stage DAG, normally linear:

| Stage | Responsibility | Input Symbols | Output Symbols |
|---|---|---|---|
| `REQUEST_NORMALIZATION` | Inline expression parse, validation, predefined calculated field injection, permission-aware request normalization. Not a SQL stage. | request + model | normalized request + expression metadata |
| `ROW_STAGE` | Base table joins, row-level dimensions/properties, row-level calculated fields, row-level `slice`, access scripts, system slice. | model fields | row aliases and dependency columns |
| `AGGREGATE_STAGE` | Grouping, aggregate measures, aggregate calculated expressions that directly contain aggregate functions, aggregate `having`. | row symbols + aggregate functions | aggregate aliases |
| `POST_AGGREGATE_STAGE` | Expressions that depend on aggregate aliases, ratio-to-total, cumulative aggregate calculations, aggregate-alias filters. | aggregate aliases | post-aggregate aliases |
| `WINDOW_RESULT_STAGE` | Window functions over row, aggregate, or post-aggregate output; result-stage filters such as `postSlice`. | previous stage aliases | window/result aliases |
| `FINAL_STAGE` | Final projection, `orderBy`, pagination SQL, and `returnTotal` SQL selection. | final visible aliases | executable SQL + count SQL |

Not every query needs every stage. Simple non-aggregate and simple aggregate queries should remain single-stage when legal.

## Symbol Resolution Rules

- A stage can reference model fields and aliases from earlier stages only.
- A select alias produced in the current stage is not visible to expressions in the same stage.
- Inside aggregate function arguments, field references resolve to model columns or earlier-stage row symbols, not to aggregate aliases produced in the same aggregate stage.
- If a selected aggregate alias has the same name as a model field, both symbols must exist in different scopes. The model field is available to aggregate arguments; the aggregate alias is available only downstream.
- Inferred `agg` metadata is a classification hint, not an instruction to always wrap the rendered SQL fragment in another aggregate function.
- Expressions that reference aggregate aliases are automatically moved to `POST_AGGREGATE_STAGE` only when every referenced alias is produced by an earlier aggregate stage and the expression contains no same-stage aggregate function argument ambiguity.
- Window or result-stage expressions are automatically moved downstream only when all inputs are previous-stage symbols and the target dialect can render the required semantics.
- Ambiguous references must fail closed with a stage-aware error message unless the model field and stage alias are disambiguated by stage rules.

These rules preserve valid single-stage SQL such as:

```sql
SUM(profit_amount) / NULLIF(SUM(sales_amount), 0)
```

and prevent invalid same-stage nested SQL such as:

```sql
SUM(SUM(profit_amount))
```

## Planner Components

Recommended components:

- `ExpressionStageClassifier`: classifies expressions as row, aggregate, post-aggregate, window/result, or final-only.
- `StageSymbolTable`: records model fields and per-stage aliases with explicit visibility rules.
- `QueryStagePlan`: immutable or effectively immutable plan object containing ordered stages, output schema, bind ownership, and diagnostics.
- `QueryStagePlanner`: orchestrates classification, dependency validation, stage splitting, and compatibility checks.
- `AggregateStageBuilder`: builds the row/aggregate `JdbcQuery` representation from the normalized request.
- `PostAggregateStageBuilder`: builds derived/CTE SQL over aggregate aliases.
- `WindowResultStageBuilder`: builds window and result-stage filtering SQL over prior stage aliases.
- `FinalStageBuilder`: applies final projection, ordering, pagination integration, and `returnTotal` SQL selection.

Exact class names can change during implementation, but the ownership boundary should remain: planning happens before rendering; rendering consumes the plan.

## Current Code Integration

The current code has several useful pieces that should be preserved and reorganized:

- `InlineExpressionPreprocessStep` and `AutoGroupByStep` already act as request normalization. They should not become SQL stage planners.
- `QueryRequestValidationStep` remains the fail-closed validation layer for request shape and operator normalization.
- `SqlCalculatedFieldProcessor`, `CalculatedFieldService`, `SqlExpContext`, and SQL expression nodes should feed classifier metadata and stage-aware symbol resolution.
- `JdbcModelQueryEngine` currently owns single-pass, CTE wrapping, and post-aggregate wrapping. 9.3.0 should move stage decision logic out of that class and leave it as orchestration plus backwards-compatible accessors.
- `SimpleSqlJdbcQueryVisitor` remains the low-level SQL visitor for one relational stage.
- Existing `SqlGenerationResult.CteStage` and compose relation compiler behavior should be reused for structured CTE output instead of inventing another final SQL carrier.
- `SqlGenerationResult` should carry a diagnostics snapshot so compose consumers can inspect `queryStagePlan` without reparsing SQL.
- `PreAggRewriteStep`, `AggSqlOptimizer`, and `returnTotal` paths must receive stage metadata so they can optimize the correct stage rather than assuming `innerSqlWithoutOrder` always has the right shape.

## SQL Rendering Strategy

Use dialect-aware rendering:

- Dialects that support CTEs should use `WITH stage AS (...)` when multiple stages are needed.
- MySQL 5.7-compatible paths must use derived-table wrapping when CTE is unavailable. `FDialect.supportsCte()` is the renderer gate; a false value must never emit `WITH`.
- Window semantics must be gated independently through dialect capability. If the dialect cannot support a required window calculation and no approved fallback exists, planning must fail closed before SQL rendering.
- SQL Server must quote identifiers and must not emit nested aggregate expressions.
- Parameter order must be deterministic: stage parameters are concatenated in stage order, and final-stage filter parameters come after the stage they filter.
- `returnTotal=true` should count the semantically final row set unless the request explicitly needs aggregate-row count optimization.

## ReturnTotal Contract

`returnTotal` has a stage-aware meaning:

- The `total` count is computed from the final semantic row set after all row filters, aggregate `having`, post-aggregate filters, window/result-stage filters, and `postSlice`.
- Final `orderBy`, limit, and offset are excluded from the count query.
- For grouped aggregate queries, `total` means the number of final grouped rows after all planned filters, not the number of source fact rows.
- Additional aggregate summary values, if produced by the existing aggregate SQL path, must be computed over the same filtered semantic row set as `total`.
- `PreAggRewriteStep` and pre-aggregation aggregate SQL may replace the count query only when the planner marks the pre-aggregation path equivalent to the planned count stage.
- If post-aggregate or window/result-stage filters cannot be represented in the pre-aggregation path, pre-aggregation count optimization must be skipped for that request.

The planner should record `finalCountStageId` and `returnTotalStrategy` in diagnostics so tests can assert the semantic stage directly.

## Planner Diagnostics Contract

Expose stable internal debug metadata through `ModelResultContext.extData["queryStagePlan"]`. This is an engine diagnostics contract, not a public REST API contract, but tests and execution interceptors may rely on it inside the module.

Minimum shape:

```json
{
  "version": "v1",
  "enabled": true,
  "dialect": "mysql|mysql8|postgres|sqlserver|sqlite",
  "renderStrategy": "single|cte|derived",
  "finalCountStageId": "final",
  "returnTotalStrategy": "final-stage-count|preagg-equivalent|disabled",
  "countSqlInput": "final-stage-sql-without-order|disabled",
  "aggSqlOptimizationPolicy": "preserve-final-stage-sql|optimizer-allowed",
  "preAggOptimizationPolicy": "skip-final-stage-required|optimizer-allowed",
  "stages": [
    {
      "id": "agg",
      "type": "AGGREGATE_STAGE",
      "sqlAlias": "stage1",
      "inputAliases": ["product$categoryName"],
      "outputAliases": ["salesAmount", "profitAmount"],
      "filterAliases": [],
      "orderAliases": [],
      "requiresSqlBoundary": false,
      "parameterCount": 0
    }
  ],
  "fallbacks": ["mysql57-derived-table"],
  "unsupported": []
}
```

Required semantics:

- `stages` order is the execution/render order.
- `renderStrategy` reflects the selected SQL shape after dialect fallback.
- `filterAliases` records filters owned by that stage, including `slice`, `having`, `postSlice`, and result-stage filters.
- `countSqlInput` records which rendered SQL shape feeds `returnTotal`.
- `aggSqlOptimizationPolicy` records whether `AggSqlOptimizer` may rewrite total SQL or must preserve the final semantic stage.
- `preAggOptimizationPolicy` records whether pre-aggregation can be attempted. `skip-final-stage-required` means both main-query preAgg and preAgg aggregate SQL must be skipped unless a later stage-aware preAgg equivalence proof is implemented.
- `fallbacks` records dialect fallbacks actually used.
- `unsupported` records fail-closed decisions for diagnostics before an exception is thrown.
- `SqlGenerationResult.diagnostics` must preserve this metadata for compose query integration.
- Existing SQL debug output may remain, but planner tests should prefer this metadata where possible.

## Why Not A Loop Step

The existing `QueryExecutionStep` loop hook is useful for bounded execution-time stabilization. It is not the right mechanism for SQL stage planning.

Do not implement multi-stage SQL by repeatedly running lifecycle steps until the request stops changing. That approach hides ownership in plugin order, makes diagnostics unstable, and makes alias visibility depend on side effects.

The correct model is:

1. Normalize the request.
2. Classify expressions and filters once.
3. Produce a `QueryStagePlan`.
4. Render the plan.
5. Execute normal query execution steps.

If planning needs another pass, it should be an internal deterministic graph walk over expression dependencies, not a public Step loop.

## Rollout Plan

Stage A: Golden coverage before refactor

- Add regression tests for issue #120 and adjacent alias collision cases.
- Add tests for aggregate alias reference as downstream post-aggregate expression.
- Add tests for `having`, `postSlice`, window fields, `orderBy`, and `returnTotal` interactions.
- Add MySQL 5.7 fallback coverage for multi-stage requests that do not require native window functions.
- Add SQL Server focused coverage for identifier quoting, aggregate aliases, and stage wrapper SQL.
- Verify real SQL results against fixture data.

Stage B: Planner skeleton behind a feature flag

- Introduce the planner data structures and diagnostics without changing default rendering.
- Add unit tests for expression classification and symbol visibility.
- Add `queryStagePlan` debug metadata and tests that assert stage order, render strategy, aliases, filter ownership, and final count stage.

Stage C: Migrate existing multi-stage paths

- Move current CTE wrapping and post-aggregate wrapping into plan-driven builders.
- Replace direct `WITH` generation with dialect-gated CTE or derived-table rendering.
- Preserve single-stage rendering for simple queries.
- Keep current public request contract unchanged.

Stage D: Enable by default

- Enable the plan-driven renderer for the covered cases.
- Keep fallback only for explicitly unsupported legacy cases.
- Run focused multi-dialect, `returnTotal`, MySQL 5.7 fallback, SQL Server, and compose parity tests.

Stage E: Cleanup and hardening

- Remove duplicated stage detection from `JdbcModelQueryEngine`.
- Collapse ad hoc `hasWindowCf`, `hasPostAggregateCalculations`, and `postAggregateSlice` handling into planner diagnostics.
- Add quality and coverage audit records under `docs/9.3.0`.

## Acceptance Criteria

- Issue #120 and same-name aggregate alias cases never generate nested aggregates.
- `SUM(a) / SUM(b)` remains single-stage when the expression directly references model fields.
- Expressions over aggregate aliases are rendered in a downstream stage.
- Same-stage alias references are never interpreted by local side effect. Safe references are planned downstream; unsafe references fail closed.
- Window calculations and result-stage filters are stage-planned, not handled by independent ad hoc wrappers.
- `returnTotal` uses the correct semantic stage and remains compatible with pre-aggregation rewrite where applicable.
- MySQL 5.7 does not receive unsupported `WITH` SQL from multi-stage rendering.
- Planner diagnostics are exposed through `queryStagePlan` and are covered by tests.
- Compose Query CTE flattening still receives structured stage data.
- The default path remains compatible for existing single-stage requests.
- Real SQL execution tests pass for core fixture scenarios.

## Test Requirements

Minimum targeted commands for implementation stages:

```bash
mvn -pl foggy-dataset-model -Dtest=CalculatedFieldAggregationBugTest test
mvn -pl foggy-dataset-model -Dtest=JdbcModelQueryEngineCteWrapTest test
mvn -pl foggy-dataset-model -Dtest=CalculatedFieldServiceTest,CalculatedFieldServiceFormulaTest,CalculatedFieldServiceDialectTest,SqlCalculatedFieldProcessorWindowOrderTest test
mvn -pl foggy-dataset-model -Dtest=ComposedDataSetResultIntegrationTest,ComposePlannerCteWrapTest,ComposeSqlCompilerTest test
```

Release-level validation should also run:

```bash
mvn -pl foggy-dataset-model test
mvn -pl foggy-dataset-model -Dspring.profiles.active=sqlserver -Dtest=CalculatedFieldAggregationBugTest test
```

If SQL Server Docker is unavailable, the implementation progress record must mark SQL Server execution as `not-run` with the reason. It must not claim SQL Server verification from SQL string inspection alone.
