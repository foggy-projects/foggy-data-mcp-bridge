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
| 1 | Aggregate Join PostgreSQL evidence | blocked-by-local-env | `application-postgres.yml` and the compose PostgreSQL service both target `15432`, but `docker`, `podman`, and `psql` are not available in this shell, and local PostgreSQL ports `15432`/`5432` are closed. This is an environment block, not a failed engine result. |
| 2 | Aggregate Join target TMS-style representative fixture | verified-local-gate-defined | Added a local TMS-style order+site composite-key aggregate relation fixture and verified RHS source-key `WHERE` plus measure `HAVING` pushdown on SQLite and live MySQL 5.7. Registry promotion gate is defined in `foggy-model-registry` commit `4e97d73`, but no TMS package is published before real authority models and target DB evidence exist. |
| 3 | Complex predicate pushdown boundary hardening | verified-local | Added coverage for mixed OR staying outer-only and AND `in`/range being pushed to RHS `WHERE` / `HAVING` while retaining the outer filters. Aggregate relation pushdown now also records structured pushed/retained/refused diagnostics with stable reason codes and exposes them in semantic response `debug.extra` when present. Java MCP natural-language capture and the real `dataset.query_model` callback preserve these diagnostics when normalizing successful `SemanticQueryResponse` results. The diagnostics record/JSON/reason-code contract is pinned by unit coverage, and the aggregate join parity snapshot exporter is opt-in via `-Dfoggy.parity.snapshot=true`. |
| 4 | Tenant/access guard edge cases | maintained | Existing system-slice, accessBuilder, deniedColumns, and predefined calculated-field tests continue to guard the hardening boundary. No new engine code was required in this pass. |
| 5 | Data Viewer direct `extData` runtime filter negative cases | verified-local | Added fail-closed coverage for blank top-level `extData.orderId` and for nested `param.extData` being ignored by the direct endpoint. |
| 6 | Pivot SQL Server cascade oracle | blocked-by-local-env | `application-sqlserver.yml` and the compose SQL Server service both target `11433`, but `docker`, `podman`, and `sqlcmd` are not available in this shell, and local SQL Server ports `11433`/`1433` are closed. No SQL Server cascade enablement is claimed. |
| 7 | Pivot MySQL 5.7 live large-domain/cascade evidence | verified-live-refusal-and-large-domain | MySQL 5.7 is reachable on `127.0.0.1:13306`. Pivot live evidence now covers large-domain axis-domain TopN/start pushdown, domain transport planning, and rows-cascade fail-closed refusal with `PIVOT_CASCADE_SQL_REQUIRED`. No MySQL 5.7 cascade enablement is claimed. |

## Verification

| Command | Result |
|---|---|
| `command -v docker`, `command -v podman`, `command -v psql`, `command -v sqlcmd`, `command -v mysql`, `nc -z 127.0.0.1 15432`, `nc -z 127.0.0.1 5432`, `nc -z 127.0.0.1 11433`, `nc -z 127.0.0.1 1433`, `nc -z 127.0.0.1 13306` | Only `/usr/local/bin/mysql` exists. MySQL 5.7 port `13306` is reachable. PostgreSQL `15432`/`5432` and SQL Server `11433`/`1433` are closed. `docker`, `podman`, `psql`, and `sqlcmd` are unavailable in this shell. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationMixedOrSliceShouldStayOuterOnly+aggregateRelationAndInRangeSlicesShouldPushRightFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; new complex-predicate aggregate relation coverage, including structured `OR_CONDITION_OUTER_ONLY` retained diagnostics and pushed `where` / `having` diagnostics; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationPushdownRefusalShouldRecordReasonCodes+aggregateRelationOutputNullSliceShouldStayOuterWhere+aggregateRelationOutputNotNullSliceShouldStayOuterWhere' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; aggregate relation diagnostics refusal and null-check outer-only reason-code coverage; Tests run: 3, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#semanticResponseShouldExposeAggregateRelationDiagnostics' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; semantic response `debug.extra.aggregateRelationDiagnostics` exposes pushed `where` / `having` aggregate relation planner evidence; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#semanticResponseShouldExposeRetainedAndRefusedAggregateRelationDiagnostics' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; semantic response `debug.extra.aggregateRelationDiagnostics` exposes retained `OR_CONDITION_OUTER_ONLY` and refused `INVALID_RANGE_VALUE` diagnostics for AI-facing responses; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#tmsStyleAggregateRelationShouldPushCompositeKeyFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; local TMS-style order+site composite-key aggregate relation fixture verifies RHS `agg_src.order_id = ?`, `agg_src.store_key = ?`, `HAVING sum(agg_src.sales_amount) > ?`, and native aggregate parity; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=AggregateJoinQueryModelTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; full aggregate join SQLite regression after predicate-boundary, diagnostics reason-code, semantic debug.extra exposure, and TMS-style composite-key fixture coverage; Tests run: 52, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -P'!multi-db' -Dtest='DataViewerApiSmokeTest#directQueryRuntimeFilterFailsClosedWhenExtDataValueBlank+directQueryRuntimeFilterDoesNotReadNestedParamExtData' -Dsurefire.failIfNoSpecifiedTests=false test` | success; new direct Data Viewer negative-case coverage; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -P'!multi-db' -Dtest=DataViewerApiSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test` | success after creating the missing local Maven generated-test-sources directory; full Data Viewer smoke suite; Tests run: 6, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=docker -Dtest='AggregateJoinQueryModelTest#aggregateRelationShouldRunExplainWithPushedRightSideFilters+aggregateRelationAndInRangeSlicesShouldPushRightFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success on live MySQL 5.7; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. The aggregate relation `EXPLAIN` shows derived source `agg_src` using `uk_order_line`, `type=ref`, `rows=2`, and `Using where` for pushed filters. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=docker -Dtest='AggregateJoinQueryModelTest#tmsStyleAggregateRelationShouldPushCompositeKeyFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success on live MySQL 5.7; local TMS-style composite-key aggregate relation executed with derived RHS filters on `order_id` and `store_key`; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=docker -Dtest='AggregateJoinQueryModelTest#semanticResponseShouldExposeAggregateRelationDiagnostics' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success on live MySQL 5.7; semantic response `debug.extra.aggregateRelationDiagnostics` exposes aggregate relation pushed `where` / `having` planner evidence through the MySQL quoting and `limit 100` path; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-mcp -am -P'!multi-db' -Dtest='QueryExpertServiceRoutingCalibrationTest#captureQueryResult_shouldNormalizeRxSemanticQueryResponse' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; Java MCP natural-language capture preserves `SemanticQueryResponse.debug.extra.aggregateRelationDiagnostics` while retaining normalized query result fields; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-mcp -am -P'!multi-db' -Dtest='McpToolCallbackFactoryTest#queryModelSuccessRx_shouldCaptureDebugExtraDiagnostics' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; real `dataset.query_model` callback captures successful `RX<SemanticQueryResponse>` results and preserves `debug.extra.aggregateRelationDiagnostics` in `QueryExpertService.LAST_QUERY_RESULT`; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dtest='AggregateRelationDiagnosticContractTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; aggregate relation diagnostics contract pins record component order, JSON keys, pushed/retained/refused factory semantics, and the eight reason codes; Tests run: 3, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='JavaQueryModelAggregateJoinSnapshotTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; aggregate join parity snapshot exporter is skipped by default unless `foggy.parity.snapshot` is enabled; Tests run: 1, Failures: 0, Errors: 0, Skipped: 1. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dfoggy.parity.snapshot=true -Dtest='JavaQueryModelAggregateJoinSnapshotTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; explicit aggregate join parity export writes `target/parity/_querymodel_aggregate_join_snapshot.json`; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotCascadeGenerateSqlParityIntegrationTest#testMysql57RowsCascadeFailsClosedWithoutMemoryFallback' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; MySQL 5.7 live-refusal test is gated away on non-docker profiles; Tests run: 1, Failures: 0, Errors: 0, Skipped: 1. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=docker -Dtest='PivotCascadeGenerateSqlParityIntegrationTest#testMysql57RowsCascadeFailsClosedWithoutMemoryFallback' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success on live MySQL 5.7; rows two-level cascade with per-level TopN fails closed with `PIVOT_CASCADE_SQL_REQUIRED` and does not fall back to memory execution; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=docker -Dtest='PivotIntegrationTest#testLargeAxisDomainTopNPushdownBeforeDefaultLimit+testLargeAxisDomainStartPushdownBeyondDefaultLimit+testLargeAxisDomainConstraintUsesDomainTransportPlan' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success on live MySQL 5.7; large-domain axis-domain TopN/start pushdown and 501-tuple domain transport planning remain executable beyond the default 1000-row boundary; Tests run: 3, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dtest='DomainRelationRendererTest#testMysql57DerivedTableRenderer_ThresholdRefusal' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; MySQL 5.7 domain-relation renderer remains threshold-limited and refuses oversized derived-table rendering instead of emitting unsafe SQL; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. |

## Follow-Up Boundary

- PostgreSQL aggregate join `EXPLAIN` must be rerun with a prepared PostgreSQL
  service, `psql`, and a reachable local port. The local profile and compose
  service exist, but they were not runnable from this shell.
- SQL Server Pivot cascade oracle still needs a prepared SQL Server service,
  `sqlcmd`, and an explicit dialect oracle. The local profile and compose
  service exist, but they were not runnable from this shell. This pass does not
  change SQL Server support.
- MySQL 5.7 Pivot evidence now covers live large-domain axis-domain pushdown,
  domain transport planning, and live cascade fail-closed refusal. Enabling
  cascade on MySQL 5.7 remains out of scope unless a future non-window staged
  oracle exists.
- Target TMS model promotion gate is now recorded in
  `/Users/fengjianguang/foggy-projects/foggy-model-registry/docs/v1.0/P2-tms-aggregate-relation-promotion-gate.md`
  at registry commit `4e97d73`. It intentionally does not publish the local
  ecommerce-style test fixture as a registry bundle; publication waits for real
  authority TMS models and target database evidence.
