---
doc_role: workitem
doc_purpose: Track the 2026-06-11 Java engine hardening pass for items 1-7.
version: 9.2.0
target: Java Engine Hardening 1-7
status: partially-verified
priority: P0/P1
owner_module: foggy-dataset-model, foggy-mcp-launcher
created_at: 2026-06-11
updated_at: 2026-06-11
---

# Java Engine Hardening 1-7

## Scope

This workitem records the mainline Java engine hardening pass requested on
2026-06-11. It stays on the Java engine line and does not cover Python parity
or cross-language alignment.

## Item Status

| ID | Item | Status | Notes |
|---|---|---|---|
| 1 | Aggregate Join PostgreSQL evidence | blocked-by-local-env | `docker` and `psql` are not available in this shell, and local PostgreSQL port `15432` is closed. This is an environment block, not a failed engine result. |
| 2 | Aggregate Join target TMS-style representative fixture | verified-local | Added a local TMS-style order+site composite-key aggregate relation fixture and verified RHS source-key `WHERE` plus measure `HAVING` pushdown on SQLite and live MySQL 5.7. Real target TMS database evidence and model-registry promotion remain follow-up. |
| 3 | Complex predicate pushdown boundary hardening | verified-local | Added coverage for mixed OR staying outer-only and AND `in`/range being pushed to RHS `WHERE` / `HAVING` while retaining the outer filters. |
| 4 | Tenant/access guard edge cases | maintained | Existing system-slice, accessBuilder, deniedColumns, and predefined calculated-field tests continue to guard the hardening boundary. No new engine code was required in this pass. |
| 5 | Data Viewer direct `extData` runtime filter negative cases | verified-local | Added fail-closed coverage for blank top-level `extData.orderId` and for nested `param.extData` being ignored by the direct endpoint. |
| 6 | Pivot SQL Server cascade oracle | blocked-by-local-env | `docker` is unavailable and SQL Server port `11433` is closed. No SQL Server cascade enablement is claimed. |
| 7 | Pivot MySQL 5.7 live large-domain/cascade evidence | partial-by-related-live-evidence | MySQL 5.7 is reachable on `127.0.0.1:13306`. This pass collected live aggregate-join evidence there; Pivot cascade large-domain evidence remains a separate follow-up. |

## Verification

| Command | Result |
|---|---|
| `/usr/local/bin/mysql`, `nc -z 127.0.0.1 13306`, `nc -z 127.0.0.1 15432`, `nc -z 127.0.0.1 11433` | MySQL client exists and MySQL 5.7 port `13306` is reachable. PostgreSQL `15432` and SQL Server `11433` are closed. `docker` and `psql` are unavailable in this shell. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationMixedOrSliceShouldStayOuterOnly+aggregateRelationAndInRangeSlicesShouldPushRightFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; new complex-predicate aggregate relation coverage; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#tmsStyleAggregateRelationShouldPushCompositeKeyFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; local TMS-style order+site composite-key aggregate relation fixture verifies RHS `agg_src.order_id = ?`, `agg_src.store_key = ?`, `HAVING sum(agg_src.sales_amount) > ?`, and native aggregate parity; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=AggregateJoinQueryModelTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; full aggregate join SQLite regression after the new predicate-boundary and TMS-style composite-key fixture tests; Tests run: 50, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -P'!multi-db' -Dtest='DataViewerApiSmokeTest#directQueryRuntimeFilterFailsClosedWhenExtDataValueBlank+directQueryRuntimeFilterDoesNotReadNestedParamExtData' -Dsurefire.failIfNoSpecifiedTests=false test` | success; new direct Data Viewer negative-case coverage; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -P'!multi-db' -Dtest=DataViewerApiSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test` | success after creating the missing local Maven generated-test-sources directory; full Data Viewer smoke suite; Tests run: 6, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=docker -Dtest='AggregateJoinQueryModelTest#aggregateRelationShouldRunExplainWithPushedRightSideFilters+aggregateRelationAndInRangeSlicesShouldPushRightFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success on live MySQL 5.7; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. The aggregate relation `EXPLAIN` shows derived source `agg_src` using `uk_order_line`, `type=ref`, `rows=2`, and `Using where` for pushed filters. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=docker -Dtest='AggregateJoinQueryModelTest#tmsStyleAggregateRelationShouldPushCompositeKeyFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success on live MySQL 5.7; local TMS-style composite-key aggregate relation executed with derived RHS filters on `order_id` and `store_key`; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |

## Follow-Up Boundary

- PostgreSQL aggregate join `EXPLAIN` must be rerun with a prepared PostgreSQL
  service, `psql`, and a reachable local port.
- SQL Server Pivot cascade oracle still needs a prepared SQL Server service and
  an explicit dialect oracle. This pass does not change SQL Server support.
- MySQL 5.7 Pivot large-domain/cascade evidence remains separate from the
  aggregate-join live MySQL 5.7 evidence collected here.
- Target TMS model promotion should still happen through `foggy-model-registry`
  or a real target TMS database bundle after the representative local fixture is
  mapped to the production model names and grains.
