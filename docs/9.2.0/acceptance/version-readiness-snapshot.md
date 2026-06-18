---
doc_role: version_readiness_snapshot
doc_purpose: Summarize the 9.2.0 Java engine workitem signoff state without replacing feature-level acceptance records.
version: 9.2.0
target: Java Engine 9.2.0 Readiness Snapshot
status: readiness-with-upstream-followups
decision: ready-for-next-hardening-cycle
created_at: 2026-06-06
updated_at: 2026-06-18
---

# Java Engine 9.2.0 Readiness Snapshot

## Scope

This document is a version-level readiness view. It does not replace the formal aggregate-join acceptance record and does not claim a final 9.2.0 release signoff.

The goal is to keep the current 9.2.0 state clear before the next engine-hardening cycle:

- aggregate join has been accepted with explicit residual risks;
- follow-up QueryModel hardening work has targeted Java evidence;
- local-only fixes are separated from upstream TMS confirmation;
- a compact upstream verification handoff exists for the two local-only consumer checks;
- environment-dependent full multi-db gates are represented only where prepared-service evidence exists.

## Signoff Matrix

| Workitem | Current Status | Signoff Meaning | Evidence |
|---|---|---|---|
| `workitems/query-model-aggregate-join.md` | accepted-with-risks | Feature slice is usable inside the documented boundary; residual risks remain active. | `acceptance/query-model-aggregate-join-acceptance.md`, `quality/query-model-aggregate-join-implementation-quality.md` |
| `workitems/BUG-aggregate-relation-rhs-dimension-filter.md` | upstream-verified | Java fix has upstream confirmation for RHS dimension fixed-filter shape. | Workitem evidence plus prior upstream feedback. |
| `workitems/BUG-aggregate-relation-joined-dimension-left-key.md` | upstream-verified | Java regression is covered locally and upstream #84 confirmed the real TMS left-dimension aggregate ON path. | Targeted dataset-model integration runs on 2026-06-06 and 2026-06-13; upstream #84 feedback. |
| `workitems/BUG-formula-property-missing-column-error.md` | local-verified-awaiting-upstream | Engine error message is verified locally; TMS issue #85 still needs consumer confirmation. | Targeted dataset-model integration run on 2026-06-06. |
| `workitems/BUG-qm-predefined-formula-slice-injection.md` | local-verified-awaiting-upstream | Engine injection path is verified locally; upstream `OrderStationStockProjectionQuery.availablePieceCount` still needs confirmation. | Targeted dataset-model integration run on 2026-06-06. |
| `workitems/OPT-qm-predefined-scalar-formula-outer-aggregate.md` | implemented | Row-level scalar predefined formulas can be outer-aggregated; aggregate/window formulas still fail closed. | Unit and integration evidence recorded in the workitem. |
| `workitems/OPT-qm-v2-tablemodel-multi-alias.md` | implemented | Explicit ordinary `TableModel` aliases are supported across joins, projections, filters, and ordering. | Unit and integration evidence recorded in the workitem. |
| `workitems/BUG-postgres-full-gate-regressions.md` | closed | PostgreSQL full-gate regressions in aggregate relation self-alias projection pruning, CTE running-sum fixture boundary, and YoY oracle limits are fixed and covered by the full prepared PostgreSQL gate. | Targeted PostgreSQL recheck plus full dataset-model PostgreSQL gate on 2026-06-18. |
| `workitems/BUG-data-viewer-direct-extdata-runtime-filter.md` | current-source-verified | Current source preserves direct-query `extData` runtime context; the expected negative-case validation log does not indicate a regression. | `DataViewerApiSmokeTest` run on 2026-06-06. |
| `workitems/BUG-formula-sql-logical-operator-reference-extraction.md` | implemented | SQL-expression formulas using string-external `and` / `or` can be parsed for dependency extraction without rewriting quoted values. | Targeted unit regression plus prepared MySQL gate on 2026-06-06. |
| `workitems/MODEL-field-permissions.md` | local-verified | Declarative TM/QM field visibility rules are resolved together with runtime `fieldAccess` and physical-column `deniedColumns` for query enforcement and semantic metadata. | Targeted dataset-model unit and integration gate on 2026-06-13. |
| `workitems/COMPOSE-python-script-snapshot-parity.md` | local-verified | Java compose script snapshot producers cover runtime markers, base/derived/union/join preview SQL and execute rows envelopes, security refusal, and MCP tool error payloads for Python replay. | `JavaComposeScriptSnapshotTest`, `JavaComposeScriptToolErrorSnapshotTest`, and Python replay tests run on 2026-06-07. |
| `workitems/PIVOT-outer-cache-design.md` | e1b-provider-cross-process-local-verified-default-off | Pivot outer-cache E1b remains default-off, but Java engine/addon cache provider/invalidation contracts are locally verified through default-off, live Redis, auto-configuration, and cross-JVM evidence. | Workitem evidence plus addon live Redis runs on 2026-06-18. |
| `workitems/PIVOT-outer-cache-redis-provider-boundary.md` | redis-provider-cross-process-local-verified | Redis stays outside core; addon provider and invalidation primitive are locally verified, including cross-JVM Pub/Sub delivery. Production rollout, race-window, and subscription-loss recovery evidence remain outside this signoff. | Workitem evidence plus live Redis cross-process run on 2026-06-18. |

## Gate Snapshot

| Gate | Result | Notes |
|---|---|---|
| Java QueryModel targeted regression gate | pass | `Tests run: 68, Failures: 0, Errors: 0, Skipped: 0`. |
| 2026-06-13 targeted upstream-risk recheck | pass | #85 diagnostic, predefined formula slice-only injection, #84 left-side dimension ON, nested dimension path, and RHS dimension filter passed together: `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`. |
| 2026-06-13 MySQL aggregate join EXPLAIN gate | pass | Live MySQL profile passed 6 aggregate relation tests; EXPLAIN showed RHS derived `agg_src` using `uk_order_line`, `type=ref`, `rows=2`, `Using where` for pushed filters. |
| 2026-06-18 PostgreSQL aggregate join targeted gate | pass | Local Docker `postgres:15-alpine` container `foggy-demo-postgres` on `127.0.0.1:15432`; aggregate relation EXPLAIN/pushdown, composite-key, joined-dimension left-key, nested dimension path, and RHS dimension fixed-filter subset passed: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`. |
| 2026-06-13 Java metadata contract gate | pass | Aggregate relation diagnostics and V3 JSON metadata aggregate-field exposure passed: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`. |
| Data Viewer direct-query smoke gate | pass | `DataViewerApiSmokeTest` passed; expected negative-case validation log observed. |
| Odoo registry consumer gate | pass | `addons/foggy-odoo-bridge-java` reactor test passed and is recorded under 9.1.0 Odoo registry promotion evidence. |
| Compose script snapshot producer gate | pass | `JavaComposeScriptSnapshotTest` passed and regenerated the tracked Python runtime fixture with 13 cases; `JavaComposeScriptToolErrorSnapshotTest` passed and regenerated the tracked MCP tool error fixture with 7 cases. Python replay passed for both fixtures. |
| Prepared MySQL dataset-model gate | pass | After refreshing local `foggy-dataset` and `foggy-dataset-demo` artifacts, `mvn -pl foggy-dataset-model surefire:test@test-mysql -Dsurefire.failIfNoSpecifiedTests=false` passed: `Tests run: 3040, Failures: 0, Errors: 0, Skipped: 51`. |
| 2026-06-18 Pivot outer-cache Redis live addon gate | pass | Local `redis:7-alpine` on `127.0.0.1:16379`; provider, Pub/Sub invalidation, auto-configuration, and independent publisher/listener JVM delivery passed together: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`. |
| 2026-06-18 PostgreSQL regression recheck | pass | Self-alias aggregate relation projection, CTE running-sum post-slice, monthly YoY, and quarterly YoY regressions passed together: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`. |
| PostgreSQL full prepared gate | pass | After refreshing local artifacts and compiling tests, `mvn -pl foggy-dataset-model test-compile surefire:test@test-postgres -Dsurefire.failIfNoSpecifiedTests=false` passed: `Tests run: 3162, Failures: 0, Errors: 0, Skipped: 3`. |
| Default full multi-db gate | partially claimed | MySQL and PostgreSQL full prepared-service evidence are recorded. SQL Server remains environment-gated before any SQL Server release claim. |

## Decision

9.2.0 is ready for the next hardening cycle with known follow-ups. It is not yet a risk-free release signoff because upstream TMS confirmation and SQL Server environment-backed evidence remain open.

Recommended next actions:

- send or re-send `workitems/upstream-verification-handoff-20260606.md` to upstream and collect confirmation for TMS issue #85 and `OrderStationStockProjectionQuery.availablePieceCount`; 2026-06-13 audit found #85 still open, no standalone `availablePieceCount` issue, and no local TMS/query-cloud-service checkout in this workspace;
- keep aggregate-join residual risks in the aggregate-join acceptance record until separately closed;
- treat the Pivot Redis line as engine-local closed for now; production Redis rollout, durable replay/fan-out, subscription-loss recovery, and race-window evidence belong to deployment/ops hardening rather than the next Java engine loop;
- decide whether SQL Server prepared-service evidence is required before the next release tag, because MySQL and PostgreSQL full gates now have local prepared-service evidence.
