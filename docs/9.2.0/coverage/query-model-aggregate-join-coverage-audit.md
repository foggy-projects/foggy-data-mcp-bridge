---
audit_scope: feature
audit_mode: pre-acceptance-check
version: 9.2.0
target: QueryModel Aggregate Join
status: reviewed-physical-permission-hardening
conclusion: ready-with-gaps
reviewed_by: Codex
reviewed_at: 2026-06-07
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
- Demo QMs: `OrderSalesAggregateJoinQueryModel.qm`, `OrderSalesAggregateRelationQueryModel.qm`, `OrderSalesAggregateRelationAccessQueryModel.qm`, `OrderSalesAggregateRelationGroupKeyAliasQueryModel.qm`

## Coverage Matrix

| Requirement | Coverage | Status | Evidence |
|---|---|---|---|
| AJ-REQ-01 prevent 1:N row multiplication | Integration query execution | covered | Aggregate join result is compared with native `fact_sales` aggregate SQL; left row is not multiplied by detail rows. |
| AJ-REQ-02 support controlled DSL contracts | SQL shape and execution tests | covered | `leftJoinAggregate` compatibility DSL and aggregate-relation-first DSL are both exercised through QM fixtures. |
| AJ-REQ-03 render fixed RHS filters inside aggregate relation | SQL shape and real execution | covered | `order_status = 'COMPLETED'` is rendered in the RHS derived relation before `GROUP BY`. |
| AJ-REQ-04 query-time RHS pushdown | SQL shape and explain evidence | covered-hardening | Left join-key filters and structured accessBuilder field-ref guards mirror into RHS source-key `WHERE`; equivalent visible left refs backed by the same physical SQL column are recognized; aggregate group-key filters push to RHS `WHERE`; aggregate measure filters push to RHS `HAVING`; explicit group-key aliases such as `salesOrderId` cover request-time slice paths without root-field collision; outer filters are retained. |
| AJ-REQ-04A aggregate group-key alias exposure | SQL shape and execution test | covered | `fsByOrder.orderId` is exposed as `salesOrderId`; request slice duplicates to RHS `agg_src.order_id = ?`, keeps outer `fsByOrder.orderId = ?`, and returns real data with the alias. |
| AJ-REQ-05 preserve system slice lifecycle | QueryFacade and accessBuilder fixture tests | covered-hardening | System slice lifecycle is covered; structured accessBuilder field-ref join-key pushdown is covered, including a system-slice tenant guard that bypasses user `fieldAccess` without leaking to returned columns. Source physical-column `deniedColumns` now maps aggregate relation outputs back to RHS source columns and fails closed, including direct and chained dynamic calculated fields that depend on aggregate outputs. Implicit tenant and raw SQL guard pushdown remain follow-up risks unless represented as safe structured join-key predicates. |
| AJ-REQ-06 invalid grain fails closed | Negative test | covered | Missing right join key in `groupBy` is rejected. |
| AJ-REQ-07 prove old database behavior | Live database execution | covered-initial | SQLite and live MySQL 5.7 profiles passed; MySQL `EXPLAIN` shows keyed access on `agg_src` for the pushed order predicate. |
| AJ-REQ-08 avoid ordinary join regression | Existing regression suite | covered | `MultiFactTableJoinTest` passed after the implementation. |
| AJ-REQ-09 aggregate output order/total | SQL shape, execution, and QueryFacade total tests | covered | Top-level `orderBy` on aggregate relation measure keeps RHS projection and renders on the relation output alias; QueryFacade `returnTotal` keeps aggregate relation SQL and returns filtered `total` / `totalData`. |
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
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationO615ProbeExpressJoinNoColumnsShouldResolveJoinPath+aggregateRelationO615TenantGuardShouldBypassFieldAccessWithoutLeaking' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; O615 explicit tenant guard backed by an equivalent left physical column is pushed into RHS aggregate `WHERE`, RHS groupBy retains the tenant key, and system-slice tenant guard bypasses user fieldAccess without leaking to returned columns; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=AggregateJoinQueryModelTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; full aggregate join sqlite regression after equivalent left join-key pushdown hardening; Tests run: 38, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationMeasureOrderByShouldRetainProjection+aggregateRelationReturnTotalShouldKeepAggregateRelationQuery' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; aggregate relation measure `orderBy` keeps RHS projection and executes, while QueryFacade `returnTotal` returns filtered total/totalData and keeps aggregate relation SQL; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=AggregateJoinQueryModelTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; full aggregate join sqlite regression after aggregate output `orderBy` and QueryFacade `returnTotal` coverage; Tests run: 40, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationGroupKeyAliasSliceShouldPushWhereThroughRequest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; request-time slice on aggregate relation group-key alias `salesOrderId` pushes into RHS source-key `WHERE`, keeps the outer relation filter, and executes against SQLite fixture data; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationO615ProbeExpressJoinNoColumnsShouldResolveJoinPath+aggregateRelationO615ProbeExpressJoinDimensionIdSliceShouldResolveJoinPath' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; alias-heavy O615 join-path regression remains valid after QM external field names are used for public columns and source columns drive owner resolution; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=AggregateJoinQueryModelTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; full aggregate join sqlite regression after group-key alias slice coverage; Tests run: 41, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=MultiFactTableJoinTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; ordinary multi-fact join regression after QM alias owner-resolution hardening; Tests run: 13, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationOutputFieldShouldFailClosedWhenDeniedPhysicalSourceColumn+aggregateRelationDeniedPhysicalUnreferencedSourceColumnShouldPass' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; aggregate relation output `salesAmount` is rejected when RHS source physical column `fact_sales.sales_amount` is denied, while an unreferenced denied source column does not over-block the query; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest,PhysicalColumnPermissionIntegrationTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; aggregate relation regression and ordinary physical-column permission integration tests pass together after RHS source physical-column mapping hardening; Tests run: 54, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationCalculatedFieldShouldFailClosedWhenDeniedPhysicalSourceColumn+aggregateRelationCalculatedFieldChainShouldFailClosedWhenDeniedPhysicalSourceColumn' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; dynamic calculated fields that directly or transitively reference aggregate relation output `salesAmount` fail closed when RHS source physical column `fact_sales.sales_amount` is denied; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest,PhysicalColumnPermissionIntegrationTest,JavaGovernanceSnapshotTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; full aggregate join SQLite regression, ordinary physical-column permission integration, and Java governance snapshot all pass after calculated-field deniedColumns coverage; Tests run: 57, Failures: 0, Errors: 0, Skipped: 0. |

## Gaps

- PostgreSQL and target TMS database `EXPLAIN` evidence is not available in this environment.
- OR / complex predicate groups are intentionally not pushed to the RHS aggregate relation.
- Relation-level default aggregate projection pruning is covered for structured references; raw SQL predicates intentionally disable pruning because alias usage is not inferred from raw SQL text.
- Aggregate relation group-key alias request slice is covered for the current relation-level DSL fixture; broader model-registry domain fixtures can reuse the same explicit `name`/`alias` pattern when root and RHS group keys share a field name.
- Aggregate relation output `orderBy` and QueryFacade `returnTotal` are covered against the current relation-level DSL fixture.
- Source physical-column `deniedColumns` is now covered for aggregate relation outputs and dynamic calculated fields that depend on those outputs. Implicit tenant and raw SQL guard pushdown remain follow-up risks. Explicit tenant guard pushdown is covered when the tenant is an aggregate relation join key/group key and the left guard ref resolves to an equivalent physical join-key column.
- Query-cloud/data-viewer `frontend-meta` propagation for aggregate relation fields remains an upstream metadata-chain check. Core QueryColumn caption/type coverage is now present.

## Recommended Next Skills

- `foggy-acceptance-signoff` for accepted-with-risks signoff.
- `foggy-bug-regression-workflow` if target TMS or PostgreSQL evidence exposes a dialect-specific optimizer or SQL rendering issue.

## Conclusion

Conclusion: ready-with-gaps.

The Java engine aggregate join cut has enough correctness, SQL-shape, SQLite, MySQL 5.7, and regression evidence to proceed to accepted-with-risks signoff. Remaining gaps are non-blocking for this delivery because they are documented as follow-up risks rather than hidden assumptions.
