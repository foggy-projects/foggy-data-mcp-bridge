---
quality_scope: followup_bundle
quality_mode: consolidated-local-verification
version: 9.2.0
target: QueryModel Hardening Follow-Ups
status: reviewed-with-upstream-followups
decision: ready-for-version-readiness-with-followups
reviewed_by: Codex
reviewed_at: 2026-06-11
follow_up_required: yes
---

# QueryModel Hardening Follow-Ups Quality And Coverage

## Purpose

This document consolidates quality and coverage evidence for the 9.2.0 hardening workitems that sit around, but do not replace, the main aggregate-join acceptance.

Covered workitems:

- aggregate relation joined-dimension left key;
- aggregate relation RHS dimension fixed filter;
- formula-backed TM property missing-column error;
- QM predefined formula reference injection outside `columns`;
- outer aggregation for safe row-level scalar predefined formulas;
- QM v2 ordinary `TableModel` multi-alias support;
- Data Viewer direct-query `extData` runtime filter propagation.
- SQL-style logical operator normalization for formula dependency extraction.

## Scope Boundary

This review intentionally does not re-sign the main aggregate-join feature. Aggregate join keeps its own quality, coverage, and acceptance records.

This review also does not claim upstream completion for workitems that only have local Java evidence. The two local-verified-awaiting-upstream items remain open until TMS confirms the real consumer scenarios.

The upstream verification packet is recorded at `../workitems/upstream-verification-handoff-20260606.md`.

## Workitem Readiness Matrix

| Workitem | Status | Engine Surface | Evidence | Remaining Action |
|---|---|---|---|---|
| `BUG-aggregate-relation-joined-dimension-left-key` | local-verified | Aggregate relation dependency planning for ON left fields. | Targeted Java integration evidence. | Upstream confirmation when TMS exercises issue #84 consumer shape. |
| `BUG-aggregate-relation-rhs-dimension-filter` | upstream-verified | RHS aggregate derived-table dimension joins before filter/grouping. | Workitem and upstream evidence. | None recorded. |
| `BUG-formula-property-missing-column-error` | local-verified-awaiting-upstream | TM validation error reporting. | Targeted Java integration evidence. | TMS issue #85 confirmation. |
| `BUG-qm-predefined-formula-slice-injection` | local-verified-awaiting-upstream | Trusted predefined calculated field injection for `slice`, `having`, `postSlice`, `orderBy`, `groupBy`, and `$field`. | Targeted Java integration evidence. | Upstream `OrderStationStockProjectionQuery.availablePieceCount` confirmation. |
| `OPT-qm-predefined-scalar-formula-outer-aggregate` | implemented | Final projection aggregation over row-level scalar predefined formulas. | Unit and integration evidence. | Preserve fail-closed behavior for aggregate/window formulas. |
| `OPT-qm-v2-tablemodel-multi-alias` | implemented | Explicit aliases for repeated ordinary `TableModel` use. | Unit and integration evidence. | Do not introduce implicit alias inference. |
| `BUG-data-viewer-direct-extdata-runtime-filter` | current-source-verified | Data Viewer direct endpoint parameter conversion. | Launcher smoke test evidence. | Keep expected negative-case validation log documented. |
| `BUG-formula-sql-logical-operator-reference-extraction` | implemented | SQL-expression formula compiler preprocessing for reference extraction. | Targeted unit evidence and MySQL gate evidence. | Keep normalization limited to SQL expression dialect and outside quoted strings. |

## Quality Review

| Review Area | Result | Notes |
|---|---|---|
| Scope containment | pass | Changes stay inside QueryModel planning, formula metadata handling, TM validation, and Data Viewer direct API parameter conversion. |
| QueryModel lifecycle preservation | pass | Workitems keep model structure visible and do not introduce raw SQL, caller CTEs, or lifecycle bypasses. |
| Fail-closed behavior | pass | Unsupported aggregate/window predefined formula outer aggregation remains rejected. Missing runtime filter negative cases still fail explicitly. |
| Regression posture | pass-with-followup | Local Java tests cover the engine paths. Two workitems still need upstream TMS confirmation. |
| Documentation traceability | pass | Each behavior change has a workitem with status, evidence, and owner surface. |

## Coverage Evidence

### Dataset-Model Targeted Gate

Command:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite '-Dtest=AggregateJoinQueryModelTest,SemanticScaleFactorIntegrationTest,InlineExpressionPreprocessStepTest,AdvancedAnalyticsTest#testQmPredefinedFormulaFieldReferencedOnlyBySlice,PivotIntegrationTest#testQmPredefinedFormulaMetricInPivotFlat+testQmPredefinedFormulaInPivotTopLevelSlice+testQmPredefinedFormulaInPivotAxisHavingAndOrderBy+testQmPredefinedFormulaMetricInPivotGrandTotal' test
```

Result:

```text
Tests run: 68, Failures: 0, Errors: 0, Skipped: 0
```

Coverage:

- aggregate relation joined-dimension left-key dependency ordering;
- formula property validation error clarity;
- predefined formula slice/top-level reference injection;
- predefined scalar formula outer aggregation behavior;
- regression coverage around semantic scale and aggregate join paths touched by the same engine area.

### Formula SQL Logical Operator Regression

Command:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='DialectTest$SqlServerDialectTest#testGeneratePagingSqlWithDistinctAndNoOrderBy,CalculatedFieldServiceTest#extractRefs_ifFunctionWithSqlAndConditions+extractRefs_sqlLogicalOperatorsIgnoreStringLiterals' test
```

Result:

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

Coverage:

- SQL Server DISTINCT pagination assertion after refreshing the current `foggy-dataset` local artifact;
- SQL-style `and` formula reference extraction;
- `and` / `or` normalization outside strings while preserving quoted literal values.

### Data Viewer Direct Entrypoint Gate

Command:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -Dtest=DataViewerApiSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Result:

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```

Coverage:

- `/data-viewer/api/query/direct/{qmModel}` propagates top-level `extData` into the direct QueryModel runtime filter context;
- blank top-level `extData` values fail closed with the aggregate relation runtime-filter validation error;
- nested `param.extData` is intentionally ignored by the direct endpoint and fails closed when no top-level direct runtime context is present;
- the logged `aggregate relation runtime filter 值不能为空` belongs to an expected negative-case assertion and is not a positive-path regression.

### Java Engine Hardening 1-7 Supplement

Command:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#aggregateRelationMixedOrSliceShouldStayOuterOnly+aggregateRelationAndInRangeSlicesShouldPushRightFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='AggregateJoinQueryModelTest#tmsStyleAggregateRelationShouldPushCompositeKeyFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest=AggregateJoinQueryModelTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=docker -Dtest='AggregateJoinQueryModelTest#aggregateRelationShouldRunExplainWithPushedRightSideFilters+aggregateRelationAndInRangeSlicesShouldPushRightFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=docker -Dtest='AggregateJoinQueryModelTest#tmsStyleAggregateRelationShouldPushCompositeKeyFilters' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dspring.profiles.active=postgres -Dtest='AggregateJoinQueryModelTest#aggregateRelationShouldRunExplainWithPushedRightSideFilters+aggregateRelationAndInRangeSlicesShouldPushRightFilters+tmsStyleAggregateRelationShouldPushCompositeKeyFilters+aggregateRelationOnLeftKeyShouldSupportJoinedDimensionField+aggregateRelationOnLeftKeyShouldSupportNestedDimensionPath+aggregateRelationRhsFixedFilterShouldSupportRightDimensionField' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
```

Result:

```text
Targeted SQLite predicate-boundary tests: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
Targeted SQLite TMS-style composite-key fixture: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
Full AggregateJoinQueryModelTest SQLite suite: Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
MySQL 5.7 live aggregate-join gate: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
MySQL 5.7 live TMS-style composite-key fixture: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
PostgreSQL 15 targeted aggregate-join gate: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```

Coverage:

- mixed join-key/measure OR request slices stay outer-only and are not copied into RHS `WHERE` / `HAVING`;
- AND `in`/range request slices duplicate safely to RHS source-key `WHERE` and aggregate-measure `HAVING` while retaining the outer filters;
- local TMS-style order+site composite-key fixture verifies RHS `order_id` and `store_key` pushdown plus aggregate-measure `HAVING` while matching native aggregate SQL;
- live MySQL 5.7 `EXPLAIN` shows derived source `agg_src` using `uk_order_line`, `type=ref`, `rows=2`, and `Using where` for pushed filters;
- live PostgreSQL `postgres:15-alpine` targeted evidence now covers aggregate relation EXPLAIN/pushdown, composite-key pushdown, joined-dimension left-key, nested dimension path, and RHS dimension fixed-filter cases;
- SQL Server remains environment-blocked locally because no SQL Server service is reachable on `11433` / `1433` and `sqlcmd` is unavailable; this blocks only SQL Server-specific prepared-service or cascade claims.

### Odoo Registry Consumer Gate

Command:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl addons/foggy-odoo-bridge-java -am -P'!multi-db' test
```

Result:

```text
Reactor build success across 10 modules.
```

Coverage:

- confirms the current Java workspace still compiles and tests with the Odoo bridge consumer module after the QueryModel hardening changes;
- detailed ownership remains in `docs/9.1.0/workitems/P1-odoo-model-registry-promotion-20260606.md`.

### Prepared MySQL Dataset-Model Gate

Prerequisite refresh:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn install -pl foggy-dataset -DskipTests
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn install -pl foggy-dataset-demo -DskipTests
```

Command:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model surefire:test@test-mysql -Dsurefire.failIfNoSpecifiedTests=false
```

Result:

```text
Tests run: 3040, Failures: 0, Errors: 0, Skipped: 51
```

Environment note:

- MySQL was reachable on `127.0.0.1:13306`.
- Supplemental 2026-06-18 PostgreSQL targeted aggregate-join evidence passed against local Docker `postgres:15-alpine` on `127.0.0.1:15432`.

### Prepared PostgreSQL Dataset-Model Gate

Command:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model test-compile surefire:test@test-postgres -Dsurefire.failIfNoSpecifiedTests=false
```

Result:

```text
Tests run: 3162, Failures: 0, Errors: 0, Skipped: 3
```

Regression notes:

- Initial full PostgreSQL run reproduced three failures, tracked and closed in `../workitems/BUG-postgres-full-gate-regressions.md`.
- Targeted PostgreSQL recheck passed for aggregate relation self-alias projection pruning, CTE running-sum post-slice, monthly YoY, and quarterly YoY: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.
- Full PostgreSQL prepared-service gate now passes against the local PostgreSQL service after the fixes.

## Known Gaps

- `BUG-formula-property-missing-column-error` still requires upstream TMS issue #85 verification.
- `BUG-qm-predefined-formula-slice-injection` still requires upstream verification with `OrderStationStockProjectionQuery.availablePieceCount`.
- The handoff packet for both checks is available at `../workitems/upstream-verification-handoff-20260606.md`.
- Aggregate join residual risks remain governed by `acceptance/query-model-aggregate-join-acceptance.md`.

## Decision

The non aggregate-join hardening bundle is ready to feed the 9.2.0 readiness snapshot with follow-ups. It should not be treated as risk-free final release acceptance until upstream TMS confirmations are complete. SQL Server prepared-service evidence remains required before any SQL Server-specific support claim, not before the default MySQL/PostgreSQL/SQLite engine boundary.
