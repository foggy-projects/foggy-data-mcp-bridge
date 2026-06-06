---
quality_scope: followup_bundle
quality_mode: consolidated-local-verification
version: 9.2.0
target: QueryModel Hardening Follow-Ups
status: reviewed-with-upstream-followups
decision: ready-for-version-readiness-with-followups
reviewed_by: Codex
reviewed_at: 2026-06-06
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

## Scope Boundary

This review intentionally does not re-sign the main aggregate-join feature. Aggregate join keeps its own quality, coverage, and acceptance records.

This review also does not claim upstream completion for workitems that only have local Java evidence. The two local-verified-awaiting-upstream items remain open until TMS confirms the real consumer scenarios.

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

### Data Viewer Direct Entrypoint Gate

Command:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-mcp-launcher -am -Dtest=DataViewerApiSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Result:

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

Coverage:

- `/data-viewer/api/query/direct/{qmModel}` propagates top-level `extData` into the direct QueryModel runtime filter context;
- the logged `aggregate relation runtime filter 值不能为空` belongs to an expected negative-case assertion and is not a positive-path regression.

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

## Known Gaps

- `BUG-formula-property-missing-column-error` still requires upstream TMS issue #85 verification.
- `BUG-qm-predefined-formula-slice-injection` still requires upstream verification with `OrderStationStockProjectionQuery.availablePieceCount`.
- Default full multi-db Maven execution is environment-dependent and should be rerun with MySQL/PostgreSQL services before release signoff.
- Aggregate join residual risks remain governed by `acceptance/query-model-aggregate-join-acceptance.md`.

## Decision

The non aggregate-join hardening bundle is ready to feed the 9.2.0 readiness snapshot with follow-ups. It should not be treated as final release acceptance until upstream TMS confirmations and the prepared-environment multi-db gate are complete.
