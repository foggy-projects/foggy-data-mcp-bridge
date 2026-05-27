---
quality_scope: feature
quality_mode: pre-coverage-audit
version: 9.2.0
target: QueryModel Aggregate Join
status: reviewed-real-db-evidence
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
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model/AggregateJoinTableModel.java` | Added synthetic right-side aggregate relation and SQL lowering for explicit and relation-level aggregate joins. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model/AggregateRelationOutputColumn.java` | Added generated column metadata contract for group keys, aggregate measures, source lineage, and pushdown hook. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model/AggregateRelationQueryObject.java` | Added aggregate relation query object pushdown lifecycle contract. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java` | Added AND-only conservative query-time RHS pushdown for aggregate relation fields and left join-key filters. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/JdbcQueryModelBuilder.java` | Integrated aggregate join and aggregate relation parsing before normal join parsing. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query/OrderSalesAggregateJoinQueryModel.qm` | Added ecommerce aggregate join fixture. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query/OrderSalesAggregateRelationQueryModel.qm` | Added relation-level aggregate DSL fixture using TM default measure aggregations. |
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
| Query-time RHS filter performance | pass-initial-with-risk | AND-only request slices on aggregate relation output fields now duplicate into RHS `WHERE` or `HAVING`; left join-key filters mirror into the RHS source key domain. MySQL 5.7 `EXPLAIN` confirms keyed RHS source access for the selective order predicate. Remaining risks are literal-rendered duplicated fragments and OR/complex predicates. |
| Permission/system slice proof | partial-risk | System slice lifecycle through QueryFacade is covered. Dedicated field-permission/accessBuilder RHS aggregate pushdown coverage remains a follow-up risk. |
| Dialect/old database proof | pass-initial-with-risk | SQLite and live MySQL 5.7 execution passed. PostgreSQL and the target TMS database were not available in this environment. |

## Findings

- No blocking implementation-quality issue was found in the initial Java cut.
- The relation-level DSL implementation matches the planned direction: `filter*` belongs inside the RHS aggregate relation, `groupBy` defines relation grain, and normal `leftJoin(...).on(...)` remains the join surface.
- The largest remaining correctness gap is scoped evidence, not code shape: system slice lifecycle has coverage, while field-permission/accessBuilder-specific RHS aggregate pushdown coverage is deferred as an explicit risk.
- The main performance gap is now narrower: MySQL 5.7 `EXPLAIN` confirms the RHS derived aggregate source can use keyed access (`uk_order_line`, `type=ref`, `rows=10`, `Using where`) after left join-key pushdown for the tested selective predicate. PostgreSQL and target TMS database plans remain follow-up evidence.
- Query-time RHS pushdown deliberately preserves the outer QueryModel filter, so it improves optimizer visibility without changing LEFT JOIN no-match semantics.
- The main efficiency gap in the new default-measure path is projection width: all supported source TM measures are generated in the first cut, even when a QM selects only a subset. This is acceptable for the initial semantic cut but should be pruned before wide production models rely on it heavily.

## Follow-Ups

- Add field-permission/accessBuilder-specific RHS aggregate relation tests.
- Add direct fixture coverage for request-time slice on aggregate relation group keys once alias exposure can avoid root-field collisions cleanly.
- Add OR/complex predicate analysis before expanding query-time RHS pushdown beyond AND-only conditions.
- Add `orderBy` on aggregate output and `returnTotal` focused assertions if these become acceptance requirements rather than incidental coverage.
- Add PostgreSQL and target TMS database SQL/explain evidence when those services are available.
- Consider a parameter-carrying derived relation so RHS fixed filters do not need to render literals.
- Prune relation-level default aggregate outputs to only referenced QM fields after the field dependency path is available.
- Keep ETL / pre-aggregated promotion out of this delivery; reopen it as a separate modeling/optimization work item after the runtime aggregate path is stable.

## Decision

Decision: ready-for-acceptance-with-risks.

The implementation is suitable for accepted-with-risks signoff for the Java engine cut. Remaining risks are explicitly scoped to PostgreSQL/target TMS explain evidence, parameter-carrying derived relations, OR/complex predicate pushdown, projection pruning, and field-permission/accessBuilder-specific RHS coverage.
