---
doc_role: version_followup_plan
doc_purpose: Track items intentionally deferred from 9.1.0 and new Java engine semantic-layer follow-up work in the 9.2.0 line.
version: 9.2.0
target: Java Engine 9.2.0 Follow-Up Roadmap
status: proposed
created_at: 2026-05-03
updated_at: 2026-06-05
---

# Foggy Java Engine 9.2.0 Follow-Ups

## Purpose

9.2.0 starts from the 9.1.0 accepted-with-risks boundary and also accepts new Java engine semantic-layer work that must remain governed, fail-closed, and queryModel lifecycle preserving.

The 9.1.0 rule still applies: if semantics or execution capability cannot be proven, the engine must refuse explicitly and guide the LLM to a safer query shape.

## Candidate Scope

| ID | Area | 9.1.0 Boundary | 9.2.0 Goal | Functional Impact Until Resolved |
|---|---|---|---|---|
| QM-92-AJ1 | QueryModel aggregate join | TMS-style projections currently use `viewSql` or caller-side Compose/CTE to pre-aggregate 1:N detail before join | Add a narrow Java QueryModel aggregate join capability with right-side slice/accessBuilder pushdown, join-key grain validation, aggregate metadata inheritance, permission preservation, and dialect-safe SQL lowering | Stable business projections still risk row multiplication or black-box `viewSql` performance issues on older databases |
| PIVOT-92-D1 | Tree + cascade / tree subtotal semantics | Tree mode with cascade TopN is rejected | Define whether parent/child ranking, descendants, visible nodes, and totals can be made deterministic | Tree-shaped "top children per parent" reports must be rewritten as normal two-level Pivot cascade or rejected |
| PIVOT-92-E1 | Outer Pivot cache | Not enabled | Define cache key, invalidation, managed relation phase, and telemetry | Correctness unaffected; repeated expensive Pivot queries may run without outer cache acceleration |
| PIVOT-92-F1 | SQL Server cascade oracle | SQL Server cascade execution is refused | Add dialect-specific SQL oracle, CI profile, and parity/refusal evidence | SQL Server users cannot run C2 cascade; they must simplify to single-level TopN or use a supported dialect |
| PIVOT-92-F2 | MySQL 5.7 live evidence | C2 cascade refused; Stage 5A transport remains threshold-limited/fail-closed | Decide whether live MySQL 5.7 remains in support scope and record live parity/refusal evidence | MySQL 5.7 cascade remains unavailable; large-domain transport may refuse beyond safe renderer thresholds |
| PIVOT-92-O1 | Production telemetry dashboards | Safe log markers exist; dashboards/log-query examples are not formalized | Add operational dashboard or log-query examples for transport refusal, tuple/parameter distribution, and cascade refusal rates | Operational visibility is manual log review rather than standardized dashboarding |
| QM-92-AJ2 | Aggregate join to ETL promotion | Runtime aggregate join provides structured development-stage semantics | Future/deferred: define evidence and metadata rules for promoting stable high-frequency aggregate joins to ETL / pre-aggregated / materialized models after the Java semantic path is stable | Stable TMS projections may keep relying on runtime aggregation longer than necessary on old databases |

## Required Review Order

0. QM-92-AJ1 can proceed independently of Pivot follow-ups because it targets queryModel join semantics, not Pivot DSL.
1. QM-92-AJ2 is explicitly out of the current Java engine delivery scope; it can follow aggregate join SQL-shape and legacy database evidence later as a modeling/optimization promotion rule.
2. PIVOT-92-F1/F2 dialect evidence can proceed independently because they should not change public DSL semantics.
3. PIVOT-92-O1 can proceed after production telemetry format is stable.
4. PIVOT-92-D1 must start with semantic review before any Java implementation. Tree + cascade must remain fail-closed unless the review produces unambiguous oracle cases.
5. PIVOT-92-E1 should wait until production telemetry identifies repeated expensive Pivot queries worth caching.

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

## Primary Workitems

- `workitems/query-model-aggregate-join.md` - Java engine initial cut, query-time RHS pushdown, structured accessBuilder join-key guard pushdown, aggregate metadata inheritance, SQLite evidence, MySQL 5.7 real database evidence, coverage audit, and accepted-with-risks signoff recorded.
- `workitems/BUG-aggregate-relation-joined-dimension-left-key.md` - Follow-up fix for aggregate relation ON left keys that reference already-joined dimension fields or nested dimension paths; ensures ON dependencies join before the aggregate derived table.
- `workitems/BUG-aggregate-relation-rhs-dimension-filter.md` - Follow-up fix for aggregate relation RHS fixed filters that reference right-side dimension fields; ensures the derived table joins RHS dimensions before filtering and grouping.
- `workitems/BUG-formula-property-missing-column-error.md` - Follow-up fix for formula-backed TM property fields missing `column`; validation now reports the concrete field path and carrier-column rule instead of dumping the whole model object.
- `workitems/BUG-qm-predefined-formula-slice-injection.md` - Follow-up fix for QM predefined formula fields referenced outside `columns`; `slice` / `having` / `postSlice` / `orderBy` / `groupBy` / `$field` references now inject trusted predefined calculated fields before SQL condition building.
- `workitems/OPT-qm-predefined-scalar-formula-outer-aggregate.md` - Allow outer aggregation for row-level scalar QM predefined calculated fields while keeping aggregate/window predefined fields fail-closed.
- `workitems/OPT-qm-v2-tablemodel-multi-alias.md` - Support ordinary QM v2 `TableModel` explicit aliases so one TM can join multiple times with alias-qualified root fields in `on`, `columns`, `slice`, and `orderBy`.
