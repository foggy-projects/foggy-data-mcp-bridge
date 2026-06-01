---
doc_role: version_overview
doc_purpose: Track Java Pivot Engine follow-up capabilities deferred from 9.0.0.beta into 9.1.0.
version: 9.1.0
target: Java Pivot Engine Follow-Up Roadmap
status: release-candidate signed-off-with-risks
created_at: 2026-05-02
---

# Foggy Pivot Engine 9.1.0 Roadmap

## Purpose

9.1.0 collects Java Pivot Engine capabilities that are intentionally not enabled in 9.0.0.beta, but are candidates for future support.

The 9.0.0.beta line remains the release-stability baseline. Items listed here must not be treated as 9.0.0 blockers unless a release owner explicitly changes the scope.

## Candidate Scope

| Area | 9.0.0.beta Status | 9.1.0 Direction |
|---|---|---|
| Stage 5A large-domain transport | `domain > 500` fail-closed for non-additive subtotal/grandTotal | Production-wired internal `DomainTransportPlan` / `DomainRelationRenderer` without changing Pivot DSL |
| Stage 5B cascade Generate | deferred / known limitation | C2 v1 rows two-level cascade implemented and signed off with fail-closed residual risks |
| Tree advanced semantics | rows-only tree, no `tree + crossjoin`, no `tree + subtotals` | Deferred to 9.2.0 as a separate tree/cascade feasibility track |
| Outer Pivot cache | not enabled for SQL pushdown first version | Deferred to 9.2.0 as a separate cache feasibility track |
| Observability | benchmark evidence exists, telemetry not formalized | Add pushdown/fallback/domain-limit metrics and release dashboards |
| Release hardening | scripts exist, CI not fully wired to external DB profiles | Wire release readiness into CI with SQLite / MySQL8 / PostgreSQL gates |

## Out of Scope by Default

- Public `CELL_AT` / `AXIS_MEMBER` DSL.
- Public `ROLLUP_TO` function strings.
- Arbitrary MDX-compatible set algebra.
- Bypassing queryModel for faster Pivot SQL generation.

These remain rejected or research-only unless a separate product requirement reopens them.

## Primary Planning Documents

- `detailed_design/00_java_pivot_engine_roadmap.md`
- `detailed_design/12_pivot_stage5b_cascade_generate_disambiguation.md`
- `detailed_design/13_pivot_stage5b_c2_implementation_plan.md`
- `detailed_design/14_llm_query_tool_capability_matrix.md`
- `workitems/pivot-java-engine-9.1.0-followups.md`
- `quality/pivot-stage5b-c2-cascade-generate-implementation-quality.md`
- `test_coverage/pivot-stage5b-c2-cascade-generate-coverage-audit.md`
- `acceptance/pivot-stage5b-c2-cascade-generate-acceptance.md`
- `beta-release-notes.md`
- `acceptance/rc-release-checklist.md`
- `acceptance/pivot-stage5b-c2-release-owner-review.md`
- `operations/stage5a-domain-transport-observability.md`
- `workitems/query-doc-contract-hardening-small-iteration.md`
- `workitems/LLM-routing-replan-guard-java-mcp-20260527.md`
- `workitems/P2-field-comparison-predicate-fixture-20260531.md`
- `workitems/P2-query-trace-orderby-limit-observability-20260601.md`
- `workitems/P2-order-sales-team-semantic-fixture-20260601.md`

Upstream 9.0.0.beta references:

- `../9.0.0.beta/acceptance/version-signoff.md`
- `../9.0.0.beta/acceptance/pivot-stage5a-domain-transport-spike-report.md`
- `../9.0.0.beta/detailed_design/09_pivot_stage5_domain_transport_spike_plan.md`

## Current Decision

9.1.0 has completed release hardening, telemetry, Stage 5A design, Stage 5A production transport, Stage 5B semantic design, scoped Stage 5B C2 implementation, and LLM query tool routing guidance.

The current release-candidate tag for the C2-inclusive closeout is `v9.1.0-rc.2`.

The remaining unsupported or unproven items are not 9.1.0 blockers. They are either explicit fail-closed runtime boundaries or 9.2.0 follow-up tracks:

1. SQL Server cascade and conservative MySQL / MySQL 5.7 cascade remain refused until accepted dialect oracle coverage exists.
2. MySQL 5.7 live large-domain parity remains a 9.2.0 evidence follow-up; current behavior stays threshold-limited and fail-closed.
3. Tree + cascade / advanced tree subtotal semantics move to 9.2.0.
4. Outer Pivot cache feasibility moves to 9.2.0.
5. Production telemetry dashboard/log-query examples move to 9.2.0 operational hardening.

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-03
- acceptance_record: `acceptance/version-signoff.md`
- blocking_items: none
- follow_up_required: yes
