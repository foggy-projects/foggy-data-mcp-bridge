---
audit_scope: feature
audit_mode: pre-acceptance-check
version: 9.2.0
target: QueryModel Aggregate Join
status: reviewed-tms-feedback-hardening
conclusion: ready-with-gaps
reviewed_by: Codex
reviewed_at: 2026-05-27
follow_up_required: yes
---

# Test Coverage Audit

## Background

This audit reviews the Java QueryModel aggregate join implementation before signoff. The feature lets the engine aggregate a 1:N right-side relation before joining it to a left relation, while keeping the aggregate relation structured and visible to the QueryModel lifecycle.

ETL / pre-aggregation promotion is not part of this delivery. It remains a future optimization path after the runtime semantic path is stable.

## Audit Basis

- Workitem: `docs/9.2.0/workitems/query-model-aggregate-join.md`
- Quality gate: `docs/9.2.0/quality/query-model-aggregate-join-implementation-quality.md`
- Core test: `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/AggregateJoinQueryModelTest.java`
- Regression test: `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/MultiFactTableJoinTest.java`
- Demo QMs: `OrderSalesAggregateJoinQueryModel.qm`, `OrderSalesAggregateRelationQueryModel.qm`, `OrderSalesAggregateRelationAccessQueryModel.qm`

## Coverage Matrix

| Requirement | Coverage | Status | Evidence |
|---|---|---|---|
| AJ-REQ-01 prevent 1:N row multiplication | Integration query execution | covered | Aggregate join result is compared with native `fact_sales` aggregate SQL; left row is not multiplied by detail rows. |
| AJ-REQ-02 support controlled DSL contracts | SQL shape and execution tests | covered | `leftJoinAggregate` compatibility DSL and aggregate-relation-first DSL are both exercised through QM fixtures. |
| AJ-REQ-03 render fixed RHS filters inside aggregate relation | SQL shape and real execution | covered | `order_status = 'COMPLETED'` is rendered in the RHS derived relation before `GROUP BY`. |
| AJ-REQ-04 query-time RHS pushdown | SQL shape and explain evidence | covered-initial | Left join-key filters and structured accessBuilder field-ref guards mirror into RHS source-key `WHERE`; aggregate group-key filters push to RHS `WHERE`; aggregate measure filters push to RHS `HAVING`; outer filters are retained. |
| AJ-REQ-05 preserve system slice lifecycle | QueryFacade and accessBuilder fixture tests | covered-initial | System slice lifecycle is covered; structured accessBuilder field-ref join-key pushdown is covered. Field-permission, implicit tenant, and raw SQL guard pushdown remain follow-up risks unless represented as safe structured join-key predicates. |
| AJ-REQ-06 invalid grain fails closed | Negative test | covered | Missing right join key in `groupBy` is rejected. |
| AJ-REQ-07 prove old database behavior | Live database execution | covered-initial | SQLite and live MySQL 5.7 profiles passed; MySQL `EXPLAIN` shows keyed access on `agg_src` for the pushed order predicate. |
| AJ-REQ-08 avoid ordinary join regression | Existing regression suite | covered | `MultiFactTableJoinTest` passed after the implementation. |
| AJ-NG-ETL keep ETL out of current scope | Documentation | covered | Workitem records ETL promotion as deferred/out of scope for this Java engine cut. |

## Evidence Summary

| Evidence | Result |
|---|---|
| `mvn install -pl foggy-dataset-demo -DskipTests` | success; demo bundle installed for model test resource loading. |
| `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db -Dtest=AggregateJoinQueryModelTest` | success; Tests run: 15, Failures: 0, Errors: 0, Skipped: 0. |
| SQLite explain log | RHS aggregate source uses indexed order key access for the selective order predicate. |
| `mvn test -pl foggy-dataset-model -Dspring.profiles.active=docker -P!multi-db -Dtest=AggregateJoinQueryModelTest` | success on live MySQL 5.7; Tests run: 15, Failures: 0, Errors: 0, Skipped: 0. |
| MySQL 5.7 explain log | Derived aggregate source `agg_src` uses `uk_order_line`, `type=ref`, `rows=10`, `Using where` for pushed `order_id`. |
| `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db -Dtest=MultiFactTableJoinTest` | success; Tests run: 13, Failures: 0, Errors: 0, Skipped: 0. |
| Local service availability check | Docker command unavailable; MySQL 5.7 port `13306` reachable; PostgreSQL `15432` and MySQL 8 `13308` closed. |

## Gaps

- PostgreSQL and target TMS database `EXPLAIN` evidence is not available in this environment.
- RHS duplicate pushdown fragments are literal-rendered because the current derived relation carrier has no parameter channel.
- OR / complex predicate groups are intentionally not pushed to the RHS aggregate relation.
- Relation-level default aggregates currently render all supported source TM measures; projection pruning remains a follow-up.
- Field-permission, implicit tenant, and raw SQL guard pushdown remain follow-up risks. Tenant pushdown should be covered by a fixture where tenant is an explicit aggregate relation join key and group key.
- Query-cloud/data-viewer `frontend-meta` propagation for aggregate relation fields remains an upstream metadata-chain check. Core QueryColumn caption/type coverage is now present.

## Recommended Next Skills

- `foggy-acceptance-signoff` for accepted-with-risks signoff.
- `foggy-bug-regression-workflow` if target TMS or PostgreSQL evidence exposes a dialect-specific optimizer or SQL rendering issue.

## Conclusion

Conclusion: ready-with-gaps.

The Java engine aggregate join cut has enough correctness, SQL-shape, SQLite, MySQL 5.7, and regression evidence to proceed to accepted-with-risks signoff. Remaining gaps are non-blocking for this delivery because they are documented as follow-up risks rather than hidden assumptions.
