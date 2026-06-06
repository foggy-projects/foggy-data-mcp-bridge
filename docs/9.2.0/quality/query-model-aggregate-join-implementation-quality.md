---
quality_scope: feature
quality_mode: pre-coverage-audit
version: 9.2.0
target: QueryModel Aggregate Join
status: reviewed-tms-feedback-hardening
decision: ready-for-acceptance-with-risks
reviewed_by: Codex
reviewed_at: 2026-05-27
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
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model/AggregateJoinTableModel.java` | Added synthetic right-side aggregate relation, SQL lowering, and aggregate output metadata inheritance for explicit and relation-level aggregate joins. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model/AggregateRelationOutputColumn.java` | Added generated column metadata contract for group keys, aggregate measures, source lineage, and pushdown hook. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model/AggregateRelationQueryObject.java` | Added aggregate relation query object pushdown lifecycle contract. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java` | Added AND-only conservative query-time RHS pushdown for aggregate relation fields and left join-key filters. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/JdbcQueryModelBuilder.java` | Integrated aggregate join and aggregate relation parsing before normal join parsing. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query/OrderSalesAggregateJoinQueryModel.qm` | Added ecommerce aggregate join fixture. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query/OrderSalesAggregateRelationQueryModel.qm` | Added relation-level aggregate DSL fixture using TM default measure aggregations. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query/OrderSalesAggregateRelationAccessQueryModel.qm` | Added accessBuilder guard fixture to prove structured field-ref join-key pushdown into RHS aggregate source `WHERE`. |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/AggregateJoinQueryModelTest.java` | Added SQL-shape, parity, no-match, relation-level default aggregation, fail-closed, metadata, and query-time group-key/measure/join-key pushdown tests. |

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
| Query-time RHS filter performance | pass-initial-with-risk | AND-only request slices on aggregate relation output fields now duplicate into RHS `WHERE` or `HAVING`; left join-key filters and structured accessBuilder field-ref guards mirror into the RHS source key domain. Duplicated RHS fragments now render with bind placeholders through the derived relation parameter channel. MySQL 5.7 `EXPLAIN` confirms keyed RHS source access for the selective order predicate. Remaining risks are OR/complex predicates and broader cross-dialect plan evidence. |
| Permission/system slice proof | pass-initial-with-risk | System slice lifecycle through QueryFacade is covered. Structured accessBuilder field-ref join-key guard pushdown is covered. Field-permission, implicit tenant guards, and raw SQL guard pushdown remain follow-up risks unless modeled as safe structured join keys. |
| Aggregate field metadata | pass-initial | Aggregate relation fields inherit TM caption, resolved output type, formatter, AI/deprecation metadata, and expose `extData.aggregateRelation` lineage including aggregation/source/semantic scale/unit metadata. |
| Dialect/old database proof | pass-initial-with-risk | SQLite and live MySQL 5.7 execution passed. PostgreSQL and the target TMS database were not available in this environment. |

## Findings

- No blocking implementation-quality issue was found in the initial Java cut.
- The relation-level DSL implementation matches the planned direction: `filter*` belongs inside the RHS aggregate relation, `groupBy` defines relation grain, and normal `leftJoin(...).on(...)` remains the join surface.
- The largest remaining correctness gap is scoped evidence, not code shape: system slice lifecycle and structured accessBuilder field-ref join-key pushdown have coverage; field-permission, implicit tenant guards, and raw SQL guards remain follow-up risks unless the condition is represented as a safe structured join-key predicate.
- The main performance gap is now narrower: MySQL 5.7 `EXPLAIN` confirms the RHS derived aggregate source can use keyed access (`uk_order_line`, `type=ref`, `rows=10`, `Using where`) after left join-key pushdown for the tested selective predicate. PostgreSQL and target TMS database plans remain follow-up evidence.
- Query-time RHS pushdown deliberately preserves the outer QueryModel filter, so it improves optimizer visibility without changing LEFT JOIN no-match semantics.
- Aggregate relation runtime schema now carries business captions and type semantics from TM measures; the remaining schema risk is frontend-meta/query-cloud/data-viewer propagation, not the core QueryModel schema object.
- The previous default-measure projection-width gap is closed for tracked structured references: aggregate relation RHS projection is pruned to required group keys and referenced outputs. Raw SQL conditions intentionally disable this pruning because alias usage is not inferred from raw SQL text.

## Follow-Ups

- Add field-permission-specific RHS aggregate relation tests once representative permission rules are available.
- Add explicit tenant guard tests in a fixture where tenant is declared as an aggregate relation join key and group key.
- Add direct fixture coverage for request-time slice on aggregate relation group keys once alias exposure can avoid root-field collisions cleanly.
- Verify query-cloud/data-viewer `frontend-meta` propagation for aggregate relation fields after core schema metadata is available.
- Add OR/complex predicate analysis before expanding query-time RHS pushdown beyond AND-only conditions.
- Add `orderBy` on aggregate output and `returnTotal` focused assertions if these become acceptance requirements rather than incidental coverage.
- Add PostgreSQL and target TMS database SQL/explain evidence when those services are available.
- Keep derived relation body-parameter ordering covered as more derived table carriers are added; aggregate relation now uses `QueryObject.getBodyParameters()` for RHS fixed/runtime filters and duplicated pushdown fragments.
- Keep relation-level projection pruning covered when new query-reference sources are added; structured references are pruned today, while raw SQL predicates retain full RHS projection by design.
- Keep ETL / pre-aggregated promotion out of this delivery; reopen it as a separate modeling/optimization work item after the runtime aggregate path is stable.

## Decision

Decision: ready-for-acceptance-with-risks.

The implementation is suitable for accepted-with-risks signoff for the Java engine cut. Remaining risks are explicitly scoped to PostgreSQL/target TMS explain evidence, OR/complex predicate pushdown, field-permission-specific RHS coverage, explicit tenant-key fixtures, and upstream frontend-meta propagation.
