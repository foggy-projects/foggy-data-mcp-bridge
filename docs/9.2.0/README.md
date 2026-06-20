---
doc_role: version_followup_plan
doc_purpose: Track items intentionally deferred from 9.1.0 and new Java engine semantic-layer follow-up work in the 9.2.0 line.
version: 9.2.0
target: Java Engine 9.2.0 Follow-Up Roadmap
status: readiness-review
created_at: 2026-05-03
updated_at: 2026-06-14
---

# Foggy Java Engine 9.2.0 Follow-Ups

## Purpose

9.2.0 starts from the 9.1.0 accepted-with-risks boundary and also accepts new Java engine semantic-layer work that must remain governed, fail-closed, and queryModel lifecycle preserving.

The 9.1.0 rule still applies: if semantics or execution capability cannot be proven, the engine must refuse explicitly and guide the LLM to a safer query shape.

## Current Readiness Snapshot

9.2.0 is no longer only a proposed backlog. The aggregate-join slice has a formal accepted-with-risks record, and the follow-up hardening workitems have targeted local or upstream evidence.

This snapshot is not a final release signoff. It records the current readiness boundary for the Java engine workstream and separates local engine evidence from upstream TMS verification.

| Area | Current Status | Evidence / Record | Remaining Boundary |
|---|---|---|---|
| QueryModel aggregate join | accepted-with-risks | `acceptance/query-model-aggregate-join-acceptance.md` | Complex predicate boundary now has SQLite and MySQL 5.7 evidence for mixed OR and AND `in`/range cases, and Java exposes structured pushed/retained/refused diagnostics for aggregate relation pushdown decisions. Java parity snapshot producer now emits `querymodel-aggregate-join-3` with 29 cases for Python replay. Local TMS-style order+site composite-key fixture is verified and registry promotion gate is defined. PostgreSQL/SQL Server profiles and compose services exist, but local services are unavailable in this shell; broader DB EXPLAIN, real target TMS package publication, Python replay consumption, and ETL promotion remain follow-up. |
| Aggregate relation joined left key | local-verified | `workitems/BUG-aggregate-relation-joined-dimension-left-key.md` | Await upstream issue #84 consumer confirmation where applicable. |
| Aggregate relation RHS dimension filter | upstream-verified | `workitems/BUG-aggregate-relation-rhs-dimension-filter.md` | No extra engine action recorded. |
| Formula TM property missing column error | local-verified-awaiting-upstream | `workitems/BUG-formula-property-missing-column-error.md` | Needs upstream TMS issue #85 verification. |
| QM predefined formula slice injection | local-verified-awaiting-upstream | `workitems/BUG-qm-predefined-formula-slice-injection.md` | Needs upstream verification with `OrderStationStockProjectionQuery.availablePieceCount`. |
| QM predefined scalar formula outer aggregate | implemented | `workitems/OPT-qm-predefined-scalar-formula-outer-aggregate.md` | Keep aggregate/window predefined fields fail-closed. |
| QM v2 TableModel multi-alias | implemented | `workitems/OPT-qm-v2-tablemodel-multi-alias.md` | Continue using explicit aliases only; do not infer ambiguous joins. |
| Data Viewer direct extData runtime filter | current-source-verified | `workitems/BUG-data-viewer-direct-extdata-runtime-filter.md` | Negative-case validation log is expected; no production regression claimed. |
| Formula SQL logical operator extraction | implemented | `workitems/BUG-formula-sql-logical-operator-reference-extraction.md` | SQL `and` / `or` normalization is limited to SQL expression dialect and string-external tokens. |
| Declarative TM/QM field permissions | local-verified | `workitems/MODEL-field-permissions.md` | TM/QM `fieldPermissions` now resolve with runtime `fieldAccess` and `deniedColumns` into one effective allowlist for query enforcement and semantic metadata; predicates are controlled and fail closed. |
| Pivot output snapshot parity | local-verified | `workitems/PIVOT-python-output-snapshot-parity.md` | Java producer now covers 8 Python replay cases including flat/grid row subtotals + grand total; Python replay remains a cross-repo follow-up. |
| Pivot production telemetry diagnostics | local-verified-consumption-contract | `workitems/PIVOT-production-telemetry-diagnostics.md` | Successful Pivot responses now expose `debug.extra.pivotDiagnostics` for SQL pushdown, axis-domain selection, domain transport plan, final execution-path evidence, and outer-cache provider unavailable downgrade. Cascade refusals now emit `pivot.cascade.refused`; dashboard/log-query consumption examples are documented. |
| Pivot tree/cascade/subtotal semantic review | semantic-review-complete-fail-closed | `workitems/PIVOT-tree-cascade-subtotal-semantics.md` | Tree + cascade remains refused, and tree totals remain unsigned until parent/child ranking, visible/full-total, and oracle fixture gates are accepted. |
| Pivot outer cache design | e1b-provider-boundary-verified-default-off | `workitems/PIVOT-outer-cache-design.md` | E1a telemetry remains the default behavior; E1b response cache can be explicitly enabled for successful flat/grid no-warning responses, with permission isolation, runtime bundle fingerprint, deployment bundle/freshness tokens, replaceable cache provider, default-safe external provider downgrade when Redis/KV is unavailable, no-Redis startup safety, provider-unavailable response diagnostics, distributed provider contract descriptor/key layout, distributed adapter/payload codec skeleton, reusable provider contract tests, invalidation event/result contracts, local replay-window guard for event-bus consumers, replay deduplication and source-node self-loop semantics, reusable invalidation broadcaster contract tests, simulated two-node fan-out semantics including partial-failure reporting, namespace/model cleanup hook, default-off admin eviction endpoint with failure payload diagnostics, bundle-removal cleanup with broadcaster-failure isolation, hit/store/store-skipped/TTL, eviction, deep-copy, and concurrent-hit diagnostics locally verified. |
| Pivot outer cache Redis provider boundary | planned-contract | `workitems/PIVOT-outer-cache-redis-provider-boundary.md` | Future Redis/KV provider modules must stay outside the core engine dependency graph, avoid constructor/lifecycle/disabled-state network I/O, degrade provider operation failures through `PivotOuterCacheSafeProvider` by default, support explicit fail-fast mode, and pass provider/distributed contract gates before production promotion. |
| Pivot dialect cascade evidence | planned-env-gated | `workitems/PIVOT-dialect-cascade-evidence-plan.md` | SQL Server cascade parity is blocked by live service availability; MySQL 5.7 remains large-domain transport supported and cascade refused. |
| Compose script snapshot parity | local-verified | `workitems/COMPOSE-python-script-snapshot-parity.md` | Java producers now cover 13 compose script runtime cases plus 7 MCP tool error cases; Python replay passes for both generated fixtures. |
| Optional before-execute pipeline loop hook | loop-hook-infrastructure-local-verified | `workitems/OPT-loop-hook-pipeline-boundary.md` | `QueryExecutionStep` now has a default-off bounded before-execute loop hook with context stop/change/trace state and tests. Main pipeline remains one-way; after-execute remains single-pass; no business step, `PlanNormalizePipeline`, or PreAgg migration is enabled yet. |
| Odoo registry consumer gate | consumer-verified | `../9.1.0/workitems/P1-odoo-model-registry-promotion-20260606.md` | Registry promotion remains a cross-repo follow-up, not a 9.2 Java engine blocker. |

Readiness documents:

- `acceptance/version-readiness-snapshot.md` records the current 9.2.0 signoff view across workitems.
- `quality/query-model-hardening-followups-quality-and-coverage.md` consolidates non aggregate-join quality review and coverage evidence.
- `workitems/upstream-verification-handoff-20260606.md` provides the compact TMS verification packet for local-verified items.

## Candidate Scope

| ID | Area | 9.1.0 Boundary | 9.2.0 Goal | Functional Impact Until Resolved |
|---|---|---|---|---|
| QM-92-AJ1 | QueryModel aggregate join | TMS-style projections currently use `viewSql` or caller-side Compose/CTE to pre-aggregate 1:N detail before join | Add a narrow Java QueryModel aggregate join capability with right-side slice/accessBuilder pushdown, join-key grain validation, aggregate metadata inheritance, permission preservation, and dialect-safe SQL lowering | Stable business projections still risk row multiplication or black-box `viewSql` performance issues on older databases |
| PIVOT-92-D1 | Tree + cascade / tree subtotal semantics | Tree mode with cascade TopN is rejected | Record semantic review and keep fail-closed until oracle gates for ranking, descendant totals, visible totals, and tree diagnostics pass | Tree-shaped "top children per parent" reports must be rewritten as normal two-level Pivot cascade or rejected |
| PIVOT-92-E1 | Outer Pivot cache | Not enabled | Define cache key, invalidation, staged eligibility, and telemetry before any cached response is returned | Correctness unaffected; repeated expensive Pivot queries may run without outer cache acceleration |
| PIVOT-92-F1 | SQL Server cascade oracle | SQL Server cascade execution is refused | Add dialect-specific SQL oracle, CI profile, and parity/refusal evidence when a live SQL Server service is reachable | SQL Server users cannot run C2 cascade; they must simplify to single-level TopN or use a supported dialect |
| PIVOT-92-F2 | MySQL 5.7 live evidence | C2 cascade refused; Stage 5A transport remains threshold-limited/fail-closed | Keep MySQL 5.7 support as live large-domain transport evidence plus explicit cascade refusal; do not enable cascade without a non-window staged oracle | MySQL 5.7 cascade remains unavailable; large-domain axis-domain pushdown and 501-tuple domain transport planning have live evidence, while oversized renderer paths still fail closed |
| PIVOT-92-O1 | Production telemetry dashboards | Safe log markers exist; dashboards/log-query examples are not formalized | Standardize response-level Pivot diagnostics, add missing refusal markers, and document first dashboard/log-query consumption examples | AI/debug consumers can now read `debug.extra.pivotDiagnostics`; operational dashboards still need concrete backend packaging |
| QM-92-AJ2 | Aggregate join to ETL promotion | Runtime aggregate join provides structured development-stage semantics | Future/deferred: define evidence and metadata rules for promoting stable high-frequency aggregate joins to ETL / pre-aggregated / materialized models after the Java semantic path is stable | Stable TMS projections may keep relying on runtime aggregation longer than necessary on old databases |

## Required Review Order

0. QM-92-AJ1 can proceed independently of Pivot follow-ups because it targets queryModel join semantics, not Pivot DSL.
1. QM-92-AJ2 is explicitly out of the current Java engine delivery scope; it can follow aggregate join SQL-shape and legacy database evidence later as a modeling/optimization promotion rule.
2. PIVOT-92-F1/F2 dialect evidence can proceed independently because they should not change public DSL semantics; SQL Server remains env-gated and MySQL 5.7 remains cascade-refused.
3. PIVOT-92-O1 response diagnostics and consumption contract are now locally documented; concrete dashboard/log-query packaging can proceed without changing Pivot DSL semantics.
4. PIVOT-92-D1 semantic review is complete and keeps tree + cascade fail-closed until oracle fixtures define ranking and totals.
5. PIVOT-92-E1 E1b local response cache is operationally verified but remains default-off; runtime bundle fingerprint, provider SPI, default-safe provider-unavailable downgrade, reusable provider contract tests, distributed provider adapter/payload codec skeleton, invalidation broadcaster SPI, reusable fan-out contract tests, local replay-window guard, bundle-removal cleanup, and default-off admin eviction endpoint are now implemented. Promotion beyond local opt-in still needs a production distributed provider or external fan-out implementation plus cross-node coherency evidence.

## Guardrails

- No queryModel lifecycle bypass.
- No `viewSql` substitution for aggregate join acceptance; the engine must keep aggregate relation structure visible.
- Do not treat runtime aggregate join as the permanent performance answer for stable, high-frequency TMS projections; promote proven shapes to ETL / pre-aggregated / materialized models.
- No public Pivot DSL expansion without a separate accepted requirement.
- No free SQL / free CTE exposure to LLM or caller payloads.
- No memory fallback for cascade unless a future staged memory implementation has parity oracle coverage.
- No SQL Server or MySQL 5.7 cascade enablement without live or dialect-specific oracle evidence.
- No tree + cascade enablement until parent/child ranking, descendant aggregation, visible-domain totals, and subtotal behavior are explicitly specified.

## Acceptance Boundary

9.2.0 work must produce its own implementation plans, quality gates, coverage audits, and acceptance records. The 9.1.0 acceptance record only signs off the existing C2 v1 rows two-level cascade subset and fail-closed residual risks.

The current readiness boundary is:

- No 9.2.0 workitem remains in `ready-for-verification`.
- Two workitems are local-verified but still need upstream TMS confirmation.
- A compact upstream verification handoff is available at `workitems/upstream-verification-handoff-20260606.md`.
- Prepared MySQL dataset-model gate is recorded as passing, including Pivot large-domain live evidence and MySQL 5.7 cascade fail-closed refusal; PostgreSQL and SQL Server evidence still depend on external prepared services in this local environment, despite existing `application-postgres.yml`, `application-sqlserver.yml`, and compose service definitions.
- Aggregate join release readiness remains accepted-with-risks, not risk-free.

## Primary Workitems

- `workitems/query-model-aggregate-join.md` - Java engine initial cut, query-time RHS pushdown, structured accessBuilder join-key guard pushdown, aggregate metadata inheritance, local TMS-style composite-key fixture, SQLite evidence, MySQL 5.7 real database evidence, Java aggregate-join parity snapshot v3 producer, coverage audit, and accepted-with-risks signoff recorded.
- `workitems/java-engine-hardening-1-7-20260611.md` - 2026-06-11 Java engine hardening pass for aggregate join predicate boundaries, direct Data Viewer `extData` fail-closed cases, local dialect evidence boundaries, and Pivot MySQL 5.7 large-domain/cascade refusal evidence.
- `workitems/BUG-aggregate-relation-joined-dimension-left-key.md` - Follow-up fix for aggregate relation ON left keys that reference already-joined dimension fields or nested dimension paths; ensures ON dependencies join before the aggregate derived table.
- `workitems/BUG-aggregate-relation-rhs-dimension-filter.md` - Follow-up fix for aggregate relation RHS fixed filters that reference right-side dimension fields; ensures the derived table joins RHS dimensions before filtering and grouping.
- `workitems/BUG-formula-property-missing-column-error.md` - Follow-up fix for formula-backed TM property fields missing `column`; validation now reports the concrete field path and carrier-column rule instead of dumping the whole model object.
- `workitems/BUG-qm-predefined-formula-slice-injection.md` - Follow-up fix for QM predefined formula fields referenced outside `columns`; `slice` / `having` / `postSlice` / `orderBy` / `groupBy` / `$field` references now inject trusted predefined calculated fields before SQL condition building.
- `workitems/OPT-qm-predefined-scalar-formula-outer-aggregate.md` - Allow outer aggregation for row-level scalar QM predefined calculated fields while keeping aggregate/window predefined fields fail-closed.
- `workitems/OPT-qm-v2-tablemodel-multi-alias.md` - Support ordinary QM v2 `TableModel` explicit aliases so one TM can join multiple times with alias-qualified root fields in `on`, `columns`, `slice`, and `orderBy`.
- `workitems/BUG-data-viewer-direct-extdata-runtime-filter.md` - Data Viewer direct-query entrypoint now preserves top-level `extData` as runtime filter context for direct QM execution.
- `workitems/BUG-formula-sql-logical-operator-reference-extraction.md` - SQL-expression formulas using string-external `and` / `or` can be parsed for dependency extraction without rewriting quoted values.
- `workitems/MODEL-field-permissions.md` - Declarative TM/QM field visibility rules resolve with runtime `fieldAccess` and physical-column `deniedColumns`, preserving TM upper bounds, QM narrowing, existing query enforcement, and semantic metadata filtering.
- `workitems/PIVOT-python-output-snapshot-parity.md` - Java Pivot output snapshot producer for Python replay now covers flat/grid, grand total, and row subtotal + grand total cases.
- `workitems/PIVOT-production-telemetry-diagnostics.md` - Java Pivot responses now expose structured `pivotDiagnostics` for execution-path, large-domain transport evidence, and default-safe outer-cache provider-unavailable downgrade, with cascade refusal logging and first dashboard/log-query consumption examples recorded.
- `workitems/PIVOT-tree-cascade-subtotal-semantics.md` - Semantic review for tree + cascade and tree totals; keeps unsupported combinations fail-closed until explicit oracle gates pass.
- `workitems/PIVOT-outer-cache-design.md` - Staged outer Pivot cache design plus E1a default telemetry and E1b default-off cache evidence for permission isolation, runtime bundle/deployment freshness keying, replaceable cache provider, provider-unavailable safe downgrade for absent/unreachable Redis/KV by default, no-Redis startup safety, provider-unavailable response diagnostics, distributed provider contract descriptor/key layout, distributed adapter/payload codec skeleton, reusable provider contract tests, invalidation event/result contracts, local replay-window guard, replay deduplication and source-node self-loop semantics, invalidation broadcaster contract tests, simulated two-node fan-out semantics including partial-failure reporting, namespace/model cleanup hook, default-off admin eviction endpoint with failure payload diagnostics, bundle-removal cleanup with broadcaster-failure isolation, lookup/miss/refused/hit/store/store-skipped/TTL-expiry diagnostics, eviction, deep-copy, and concurrent-hit safety.
- `workitems/PIVOT-outer-cache-redis-provider-boundary.md` - Future Redis/KV provider-module acceptance boundary; keeps Redis out of the core engine dependency graph and requires lazy startup-safe operation-level failure handling.
- `workitems/PIVOT-dialect-cascade-evidence-plan.md` - Dialect evidence plan for SQL Server cascade parity and MySQL 5.7 large-domain transport plus cascade refusal.
- `workitems/COMPOSE-python-script-snapshot-parity.md` - Java compose script snapshot producers for Python replay now cover tool/runtime markers, base/derived/union/join preview SQL and execute rows envelopes, security refusal, resolver-null host misconfiguration, remote principal mismatch, remote missing binding, and the current boundary that legacy result-object methods are not part of `dataset.compose_script`.
- `workitems/OPT-loop-hook-pipeline-boundary.md` - Optional before-execute pipeline loop hook design boundary and first default-off `QueryExecutionStep` infrastructure slice; keeps the main query pipeline deterministic while allowing future bounded convergence-only consumers such as QueryPlan/CTE normalization.
