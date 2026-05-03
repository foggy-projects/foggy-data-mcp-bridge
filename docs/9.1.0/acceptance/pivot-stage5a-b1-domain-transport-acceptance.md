---
doc_role: feature_acceptance
doc_purpose: Sign off the PIVOT-91-B1 Stage 5A large-domain transport design for 9.1.0.
acceptance_scope: feature
version: 9.1.0
target: PIVOT-91-B1 Stage 5A DomainTransportPlan design
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex acceptance reviewer
signed_off_at: 2026-05-02
reviewed_by: Codex
blocking_items: []
follow_up_required: yes
evidence_count: 6
---

# Feature Acceptance

## Background

PIVOT-91-B1 is the design gate for Stage 5A large-domain transport. The accepted 9.0.0.beta boundary keeps non-additive subtotal/grandTotal aligned to the surviving TopN domain but fails closed when the surviving domain exceeds 500 tuples. The 9.1.0 roadmap requires an internal `DomainTransportPlan` and dialect-facing renderer design before any production implementation.

## Acceptance Basis

- `docs/9.1.0/README.md`
- `docs/9.1.0/detailed_design/00_java_pivot_engine_roadmap.md`
- `docs/9.1.0/detailed_design/10_pivot_stage5a_domain_transport_plan.md`
- `docs/9.1.0/workitems/pivot-java-engine-9.1.0-followups.md`
- `docs/9.0.0.beta/acceptance/version-signoff.md`
- `docs/9.0.0.beta/detailed_design/09_pivot_stage5_domain_transport_spike_plan.md`

## Checklist

- [x] Design preserves the public Pivot DSL and keeps Stage 5A production behavior off by default.
- [x] Design explicitly keeps queryModel lifecycle boundaries: permission, preAgg, physical column checks, params, sanitizer, and logging.
- [x] Design defines internal `DomainTransportPlan` and dialect-facing `DomainRelationRenderer` responsibilities.
- [x] Design covers SQLite, PostgreSQL, MySQL8, and MySQL5.7 strategy decisions.
- [x] Design covers tuple correlation, null-safe tuple matching, parameter ordering, and fail-closed refusal.
- [x] Design includes SQL oracle examples for single-domain, tuple-domain, NULL, TopN surviving domain, non-additive grand total, and refusal cases.
- [x] Workitems record B1 as review-ready/complete and keep B2 blocked/pending.
- [ ] B2-ready engineering details are fully quantified and locked.

## Failed Items

No blocking failed items were found for the B1 design gate. The unchecked item above is a B2 pre-implementation risk, not a B1 blocker.

## Evidence

- `git status --short --branch` showed the expected `9.1.0` branch with prior A1/A2 uncommitted work preserved.
- `git diff --check` reported only Windows line-ending warnings and no whitespace errors.
- `10_pivot_stage5a_domain_transport_plan.md` documents goals, non-goals, dialect matrix, null-safe matching, parameter ordering, data flow, migration strategy, and SQL oracle examples.
- `pivot-java-engine-9.1.0-followups.md` records B1 review-ready/complete and B2 blocked/pending.
- Current `SemanticQueryRequest` has no dedicated internal domain transport field, confirming B2 must avoid public DTO/DSL leakage when choosing the carrier.
- MySQL official documentation states the `VALUES ROW(...)` table value constructor is available from MySQL 8.0.19, so MySQL8 rendering must gate versions before using that syntax: https://dev.mysql.com/doc/refman/8.0/en/values.html

## Risks / Open Items

- MySQL8 syntax gate: the design's MySQL8 `VALUES ROW(...)` path is valid only for MySQL 8.0.19+. B2 must either detect minor version support or use derived-table fallback for earlier MySQL8 versions.
- Internal carrier choice is not frozen. B2 must specify whether the plan travels through `SemanticRequestContext`, `QueryContextV3`, `SqlGenerationResult`, or another internal execution context without exposing a public request field.
- MySQL5.7 derived-table support is accepted as a guarded fallback direction, but B2 must define exact tuple, parameter, SQL-length, and packet/parse-depth thresholds.
- Test plan is present as design coverage and SQL oracle examples, but B2 must convert it into real SQL data parity tests, not string-only assertions.
- Release status remains beta because Stage 5A is design-only and Stage 5B semantics are still open.

## Final Decision

PIVOT-91-B1 is signed off as `accepted-with-risks`. The design is sufficient to close the B1 design gate and preserve the 9.1.0 safety boundaries. It does not authorize immediate production enablement of Stage 5A. PIVOT-91-B2 remains pending until product/telemetry demand is confirmed and the open engineering details above are resolved.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/pivot-stage5a-b1-domain-transport-acceptance.md`
- blocking_items: none
- follow_up_required: yes
