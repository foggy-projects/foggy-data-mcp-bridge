---
doc_role: version_readiness_snapshot
doc_purpose: Summarize the 9.2.0 Java engine workitem signoff state without replacing feature-level acceptance records.
version: 9.2.0
target: Java Engine 9.2.0 Readiness Snapshot
status: readiness-with-upstream-followups
decision: ready-for-next-hardening-cycle
created_at: 2026-06-06
updated_at: 2026-06-13
---

# Java Engine 9.2.0 Readiness Snapshot

## Scope

This document is a version-level readiness view. It does not replace the formal aggregate-join acceptance record and does not claim a final 9.2.0 release signoff.

The goal is to keep the current 9.2.0 state clear before the next engine-hardening cycle:

- aggregate join has been accepted with explicit residual risks;
- follow-up QueryModel hardening work has targeted Java evidence;
- local-only fixes are separated from upstream TMS confirmation;
- a compact upstream verification handoff exists for the two local-only consumer checks;
- environment-dependent full multi-db gates are not represented as completed.

## Signoff Matrix

| Workitem | Current Status | Signoff Meaning | Evidence |
|---|---|---|---|
| `workitems/query-model-aggregate-join.md` | accepted-with-risks | Feature slice is usable inside the documented boundary; residual risks remain active. | `acceptance/query-model-aggregate-join-acceptance.md`, `quality/query-model-aggregate-join-implementation-quality.md` |
| `workitems/BUG-aggregate-relation-rhs-dimension-filter.md` | upstream-verified | Java fix has upstream confirmation for RHS dimension fixed-filter shape. | Workitem evidence plus prior upstream feedback. |
| `workitems/BUG-aggregate-relation-joined-dimension-left-key.md` | local-verified | Java regression is covered locally; upstream issue #84 confirmation is still useful if TMS exercises the exact consumer model. | Targeted dataset-model integration run on 2026-06-06. |
| `workitems/BUG-formula-property-missing-column-error.md` | local-verified-awaiting-upstream | Engine error message is verified locally; TMS issue #85 still needs consumer confirmation. | Targeted dataset-model integration run on 2026-06-06. |
| `workitems/BUG-qm-predefined-formula-slice-injection.md` | local-verified-awaiting-upstream | Engine injection path is verified locally; upstream `OrderStationStockProjectionQuery.availablePieceCount` still needs confirmation. | Targeted dataset-model integration run on 2026-06-06. |
| `workitems/OPT-qm-predefined-scalar-formula-outer-aggregate.md` | implemented | Row-level scalar predefined formulas can be outer-aggregated; aggregate/window formulas still fail closed. | Unit and integration evidence recorded in the workitem. |
| `workitems/OPT-qm-v2-tablemodel-multi-alias.md` | implemented | Explicit ordinary `TableModel` aliases are supported across joins, projections, filters, and ordering. | Unit and integration evidence recorded in the workitem. |
| `workitems/BUG-data-viewer-direct-extdata-runtime-filter.md` | current-source-verified | Current source preserves direct-query `extData` runtime context; the expected negative-case validation log does not indicate a regression. | `DataViewerApiSmokeTest` run on 2026-06-06. |
| `workitems/BUG-formula-sql-logical-operator-reference-extraction.md` | implemented | SQL-expression formulas using string-external `and` / `or` can be parsed for dependency extraction without rewriting quoted values. | Targeted unit regression plus prepared MySQL gate on 2026-06-06. |
| `workitems/MODEL-field-permissions.md` | local-verified | Declarative TM/QM field visibility rules are resolved together with runtime `fieldAccess` and physical-column `deniedColumns` for query enforcement and semantic metadata. | Targeted dataset-model unit and integration gate on 2026-06-13. |
| `workitems/COMPOSE-python-script-snapshot-parity.md` | local-verified | Java compose script snapshot producers cover runtime markers, base/derived/union/join preview SQL and execute rows envelopes, security refusal, and MCP tool error payloads for Python replay. | `JavaComposeScriptSnapshotTest`, `JavaComposeScriptToolErrorSnapshotTest`, and Python replay tests run on 2026-06-07. |

## Gate Snapshot

| Gate | Result | Notes |
|---|---|---|
| Java QueryModel targeted regression gate | pass | `Tests run: 68, Failures: 0, Errors: 0, Skipped: 0`. |
| Data Viewer direct-query smoke gate | pass | `DataViewerApiSmokeTest` passed; expected negative-case validation log observed. |
| Odoo registry consumer gate | pass | `addons/foggy-odoo-bridge-java` reactor test passed and is recorded under 9.1.0 Odoo registry promotion evidence. |
| Compose script snapshot producer gate | pass | `JavaComposeScriptSnapshotTest` passed and regenerated the tracked Python runtime fixture with 13 cases; `JavaComposeScriptToolErrorSnapshotTest` passed and regenerated the tracked MCP tool error fixture with 7 cases. Python replay passed for both fixtures. |
| Prepared MySQL dataset-model gate | pass | After refreshing local `foggy-dataset` and `foggy-dataset-demo` artifacts, `mvn -pl foggy-dataset-model surefire:test@test-mysql -Dsurefire.failIfNoSpecifiedTests=false` passed: `Tests run: 3040, Failures: 0, Errors: 0, Skipped: 51`. |
| PostgreSQL prepared gate | blocked-by-environment | `docker` / `podman` unavailable locally and `127.0.0.1:15432` is not accepting connections. |
| Default full multi-db gate | partially claimed | MySQL evidence is now recorded; PostgreSQL still requires a prepared service before release signoff. |

## Decision

9.2.0 is ready for the next hardening cycle with known follow-ups. It is not yet a risk-free release signoff because upstream TMS confirmation and full environment-backed multi-db evidence remain open.

Recommended next actions:

- send or re-send `workitems/upstream-verification-handoff-20260606.md` to upstream and collect confirmation for TMS issue #85 and `OrderStationStockProjectionQuery.availablePieceCount`; 2026-06-13 audit found #85 still open and no standalone `availablePieceCount` issue;
- keep aggregate-join residual risks in the aggregate-join acceptance record until separately closed;
- run the PostgreSQL Maven gate in an environment with a prepared PostgreSQL service before a release tag.
