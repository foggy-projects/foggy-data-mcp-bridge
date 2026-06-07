---
quality_scope: feature
quality_mode: pre-coverage-audit
version: 9.2.0
target: QueryModel Aggregate Join
status: reviewed-physical-permission-hardening
decision: ready-for-acceptance-with-risks
reviewed_by: Codex
reviewed_at: 2026-06-07
follow_up_required: yes
---

# Implementation Quality Gate

## Background

This quality gate reviews the Java engine initial cut for QueryModel aggregate join. The implementation goal is to let a QM declare right-side pre-aggregation before a LEFT JOIN, avoiding 1:N detail row multiplication and avoiding model-authored `viewSql` as the acceptance path.

## Check Basis

- Workitem: `docs/9.2.0/workitems/query-model-aggregate-join.md`
- Engine DSL and parser changes in `foggy-dataset-model`
- Demo QM fixture in `foggy-dataset-demo`
- Targeted SQLite execution tests, live MySQL 5.7 execution-plan evidence, and normal join regression

## Changed Surface

| Path | Change |
|---|---|
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/proxy/AggregateJoinBuilder.java` | Added controlled aggregate join DSL builder. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/proxy/AggregateRelationProxy.java` | Added relation-level aggregate proxy for `filter*`, `groupBy`, and `as`. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/proxy/TableModelProxy.java` | Added `leftJoinAggregate` entrypoint and aggregate relation method dispatch. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query/JdbcQuery.java` | Added structured field-ref RHS aggregate pushdown for accessBuilder and other `JdbcQuery` conditions. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model/AggregateJoinTableModel.java` | Added synthetic right-side aggregate relation, SQL lowering, aggregate output metadata inheritance, and same-physical-column left join-key expansion for explicit and relation-level aggregate joins. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model/AggregateRelationOutputColumn.java` | Added generated column metadata contract for group keys, aggregate measures, source lineage, and pushdown hook. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model/AggregateRelationQueryObject.java` | Added aggregate relation query object pushdown lifecycle contract. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java` | Added AND-only conservative query-time RHS pushdown for aggregate relation fields and left join-key filters. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/QueryModelLoaderImpl.java` | Treats explicit `SelectColumnDef.name` as the QM external column identifier so aggregate relation group keys can be exposed under non-conflicting aliases. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/QueryModelSupport.java` | Hardened dimension owner-model resolution to prefer the underlying selected column / `QueryObject`, preserving join-path planning when QM fields use external aliases. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/PhysicalColumnMappingBuilder.java` | Maps aggregate relation output query fields back to RHS source physical columns so `deniedColumns` fail closed for aggregate outputs without over-blocking unrelated source columns. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/JdbcQueryModelBuilder.java` | Integrated aggregate join and aggregate relation parsing before normal join parsing. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query/OrderSalesAggregateJoinQueryModel.qm` | Added ecommerce aggregate join fixture. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query/OrderSalesAggregateRelationQueryModel.qm` | Added relation-level aggregate DSL fixture using TM default measure aggregations. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query/OrderSalesAggregateRelationAccessQueryModel.qm` | Added accessBuilder guard fixture to prove structured field-ref join-key pushdown into RHS aggregate source `WHERE`. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query/OrderSalesAggregateRelationGroupKeyAliasQueryModel.qm` | Added aggregate relation group-key alias fixture to cover request-time slices without root-field name collision. |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/AggregateJoinQueryModelTest.java` | Added SQL-shape, parity, no-match, relation-level default aggregation, fail-closed, metadata, query-time group-key/measure/join-key pushdown, and calculated-field source physical-permission tests. |

## Quality Checklist

| Check | Result | Notes |
|---|---|---|
| Scope containment | pass | Public surface is limited to QM authoring DSL (`leftJoinAggregate` and aggregate relation proxy); no MCP schema or caller raw SQL change. |
| Semantic validation | pass-initial | Enforces LEFT join, equality `on`, right join key covered by `groupBy`, non-empty groupBy/default measures, and simple aliases. |
| Query correctness evidence | pass-initial | SQLite and live MySQL 5.7 parity tests compare generated aggregate join with native aggregate SQL. |
| Regression coverage | pass | Existing `MultiFactTableJoinTest` passed after implementation. |
| Raw SQL exposure | pass-with-note | Generated SQL uses an internal derived-query carrier; model authors still provide structured DSL only. |
| Readability and locality | pass | New behavior is isolated to aggregate builder, synthetic table model, and QueryModel builder branch. |
| LLM analysis ergonomics | pass-initial | Relation-level DSL lets model authors publish ordinary QM fields backed by TM aggregation metadata, reducing LLM need to synthesize raw SQL/CTE. |
| Query-time RHS filter performance | pass-hardening-with-risk | AND-only request slices on aggregate relation output fields now duplicate into RHS `WHERE` or `HAVING`; left join-key filters and structured accessBuilder field-ref guards mirror into the RHS source key domain. The left join-key match expands through same-physical-column visible TM/QM fields, so a guard authored against `tenant$id` can still mirror when the aggregate join uses `tenantId`. Duplicated RHS fragments render with bind placeholders through the derived relation parameter channel. MySQL 5.7 `EXPLAIN` confirms keyed RHS source access for the selective order predicate. OR join-key and OR aggregate-measure slices are covered as outer-only behavior; remaining risks are expanding complex predicate pushdown and broader cross-dialect plan evidence. |
| Aggregate group-key alias exposure | pass-hardening | Explicit QM `name`/`alias` can expose an aggregate relation group key, allowing request-time `slice` pushdown without colliding with the root field name. Owner-model resolution now uses the selected source column first, so external aliases do not break dimension join-path planning. |
| Permission/system slice proof | pass-hardening-with-risk | System slice lifecycle through QueryFacade is covered. Structured accessBuilder field-ref join-key guard pushdown is covered, including equivalent left join-key refs and a system-slice tenant guard that bypasses user `fieldAccess` without leaking to output columns. Source physical-column `deniedColumns` now fail closed for aggregate relation outputs through QueryModel physical-column mapping; request-side dynamic calculated fields inherit that boundary through direct and chained dependency expansion, and QM predefined calculated fields inherit it through predefined formula physical-column mapping. Implicit tenant guards and raw SQL guard pushdown remain follow-up risks unless modeled as safe structured join keys. |
| Aggregate output order/total | pass-hardening | Top-level `orderBy` on an aggregate relation measure keeps the required RHS projection and renders against the relation output alias. QueryFacade `returnTotal` keeps the aggregate relation derived table in total SQL and returns filtered `total` / `totalData`. |
| Aggregate field metadata | pass-initial | Aggregate relation fields inherit TM caption, resolved output type, formatter, AI/deprecation metadata, and expose `extData.aggregateRelation` lineage including aggregation/source/semantic scale/unit metadata. |
| Dialect/old database proof | pass-initial-with-risk | SQLite and live MySQL 5.7 execution passed. PostgreSQL and the target TMS database were not available in this environment. |

## Findings

- No blocking implementation-quality issue was found in the initial Java cut.
- The relation-level DSL implementation matches the planned direction: `filter*` belongs inside the RHS aggregate relation, `groupBy` defines relation grain, and normal `leftJoin(...).on(...)` remains the join surface.
- The largest remaining correctness gap is scoped evidence, not code shape: system slice lifecycle, structured accessBuilder field-ref join-key pushdown, and aggregate relation source physical-column `deniedColumns` have coverage, including same-physical-column tenant guard refs. Implicit tenant guards and raw SQL guards remain follow-up risks unless the condition is represented as a safe structured join-key predicate.
- The main performance gap is now narrower: MySQL 5.7 `EXPLAIN` confirms the RHS derived aggregate source can use keyed access (`uk_order_line`, `type=ref`, `rows=10`, `Using where`) after left join-key pushdown for the tested selective predicate. PostgreSQL and target TMS database plans remain follow-up evidence.
- Query-time RHS pushdown deliberately preserves the outer QueryModel filter, so it improves optimizer visibility without changing LEFT JOIN no-match semantics.
- Aggregate relation group-key request slice is now covered through an explicit `salesOrderId` QM alias for `fsByOrder.orderId`; the SQL keeps both RHS source-key pushdown and the outer filter.
- QM external alias handling is now stricter about ownership: external `name` is used for the public QueryModel field, while dimension owner resolution follows the selected source column to avoid regressions in alias-heavy O615 paths.
- Aggregate relation runtime schema now carries business captions and type semantics from TM measures; the remaining schema risk is frontend-meta/query-cloud/data-viewer propagation, not the core QueryModel schema object.
- The previous default-measure projection-width gap is closed for tracked structured references: aggregate relation RHS projection is pruned to required group keys and referenced outputs. Raw SQL conditions intentionally disable this pruning because alias usage is not inferred from raw SQL text.
- Aggregate relation output `orderBy` and QueryFacade `returnTotal` are no longer incidental coverage: both have targeted SQL-shape and execution assertions against the relation-level DSL fixture.
- Request-side dynamic `calculatedFields` that reference aggregate relation outputs are covered for source physical-column `deniedColumns`, including a transitive calculated-field chain; QM predefined calculated fields are also covered through QueryModel physical-column mapping. No implementation change was needed for the request-side path because `FieldAccessPermissionStep` already expands calculated-field dependencies before checking denied QM fields.

## Follow-Ups

- Add more cross-domain tenant guard fixtures if future industry models use non-standard tenant grains; the O615 explicit tenant join-key fixture is covered for the current engine cut.
- Verify query-cloud/data-viewer `frontend-meta` propagation for aggregate relation fields after core schema metadata is available.
- Keep OR/complex predicate pushdown disabled unless a future boolean-normalization design proves semantic equivalence; current OR join-key and OR aggregate-measure boundaries are covered as outer-only regressions.
- Add PostgreSQL and target TMS database SQL/explain evidence when those services are available.
- Keep derived relation body-parameter ordering covered as more derived table carriers are added; aggregate relation now uses `QueryObject.getBodyParameters()` for RHS fixed/runtime filters and duplicated pushdown fragments.
- Keep relation-level projection pruning covered when new query-reference sources are added; structured references are pruned today, while raw SQL predicates retain full RHS projection by design.
- Keep ETL / pre-aggregated promotion out of this delivery; reopen it as a separate modeling/optimization work item after the runtime aggregate path is stable.

## Decision

Decision: ready-for-acceptance-with-risks.

The implementation is suitable for accepted-with-risks signoff for the Java engine cut. Remaining risks are explicitly scoped to PostgreSQL/target TMS explain evidence, future expansion of complex predicate pushdown beyond the covered OR outer-only boundary, implicit tenant/raw SQL guard boundaries, explicit tenant-key fixtures, and upstream frontend-meta propagation.
