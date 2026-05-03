---
doc_role: version_followup_plan
doc_purpose: Track items intentionally deferred from 9.1.0 into the 9.2.0 Pivot Engine follow-up line.
version: 9.2.0
target: Java Pivot Engine Follow-Up Roadmap
status: proposed
created_at: 2026-05-03
---

# Foggy Pivot Engine 9.2.0 Follow-Ups

## Purpose

9.2.0 starts from the 9.1.0 accepted-with-risks boundary. Items listed here are not 9.1.0 blockers. They are either fail-closed runtime boundaries, missing dialect evidence, or separate feasibility tracks that need their own semantic review before implementation.

The 9.1.0 rule still applies: if semantics or execution capability cannot be proven, the engine must refuse explicitly and guide the LLM to a safer query shape.

## Candidate Scope

| ID | Area | 9.1.0 Boundary | 9.2.0 Goal | Functional Impact Until Resolved |
|---|---|---|---|---|
| PIVOT-92-D1 | Tree + cascade / tree subtotal semantics | Tree mode with cascade TopN is rejected | Define whether parent/child ranking, descendants, visible nodes, and totals can be made deterministic | Tree-shaped "top children per parent" reports must be rewritten as normal two-level Pivot cascade or rejected |
| PIVOT-92-E1 | Outer Pivot cache | Not enabled | Define cache key, invalidation, managed relation phase, and telemetry | Correctness unaffected; repeated expensive Pivot queries may run without outer cache acceleration |
| PIVOT-92-F1 | SQL Server cascade oracle | SQL Server cascade execution is refused | Add dialect-specific SQL oracle, CI profile, and parity/refusal evidence | SQL Server users cannot run C2 cascade; they must simplify to single-level TopN or use a supported dialect |
| PIVOT-92-F2 | MySQL 5.7 live evidence | C2 cascade refused; Stage 5A transport remains threshold-limited/fail-closed | Decide whether live MySQL 5.7 remains in support scope and record live parity/refusal evidence | MySQL 5.7 cascade remains unavailable; large-domain transport may refuse beyond safe renderer thresholds |
| PIVOT-92-O1 | Production telemetry dashboards | Safe log markers exist; dashboards/log-query examples are not formalized | Add operational dashboard or log-query examples for transport refusal, tuple/parameter distribution, and cascade refusal rates | Operational visibility is manual log review rather than standardized dashboarding |

## Required Review Order

1. PIVOT-92-F1/F2 dialect evidence can proceed independently because they should not change public DSL semantics.
2. PIVOT-92-O1 can proceed after production telemetry format is stable.
3. PIVOT-92-D1 must start with semantic review before any Java implementation. Tree + cascade must remain fail-closed unless the review produces unambiguous oracle cases.
4. PIVOT-92-E1 should wait until production telemetry identifies repeated expensive Pivot queries worth caching.

## Guardrails

- No queryModel lifecycle bypass.
- No public Pivot DSL expansion without a separate accepted requirement.
- No memory fallback for cascade unless a future staged memory implementation has parity oracle coverage.
- No SQL Server or MySQL 5.7 cascade enablement without live or dialect-specific oracle evidence.
- No tree + cascade enablement until parent/child ranking, descendant aggregation, visible-domain totals, and subtotal behavior are explicitly specified.

## Acceptance Boundary

9.2.0 work must produce its own implementation plans, quality gates, coverage audits, and acceptance records. The 9.1.0 acceptance record only signs off the existing C2 v1 rows two-level cascade subset and fail-closed residual risks.
