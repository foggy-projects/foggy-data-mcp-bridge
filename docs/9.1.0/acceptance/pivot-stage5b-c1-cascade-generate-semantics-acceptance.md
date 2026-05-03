---
doc_role: feature_acceptance
doc_purpose: Sign off the PIVOT-91-C1 Cascade Generate and multi-level TopN semantic design for 9.1.0.
acceptance_scope: feature
version: 9.1.0
target: PIVOT-91-C1 Cascade Generate semantic design
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

PIVOT-91-C1 is the semantic design gate for Stage 5B Cascade Generate and multi-level TopN. Its purpose is to freeze ranking grain, Having order, tie-breaking, uneven-domain behavior, subtotal/grandTotal rules, and rejection boundaries before any C2 implementation starts.

## Acceptance Basis

- `docs/9.1.0/detailed_design/00_java_pivot_engine_roadmap.md`
- `docs/9.1.0/detailed_design/11_pivot_stage5b_cascade_generate_semantics.md`
- `docs/9.1.0/workitems/pivot-java-engine-9.1.0-followups.md`
- `docs/9.0.0.beta/acceptance/version-signoff.md`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/sql/PivotTopNSqlPlanner.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/sql/PivotAxisDomainSqlPlanner.java`

## Checklist

- [x] Semantic decision is explicit and conservative.
- [x] Ranking grain is defined as current-level aggregate after parent-domain filtering.
- [x] Having is defined to run before TopN at the same level.
- [x] Tie-breaking is deterministic and now avoids relying on dialect default NULL ordering.
- [x] Uneven domain, CrossJoin, subtotal/grandTotal, and non-additive limitations are documented.
- [x] SQL oracle examples cover baseline, two-level TopN, Having-before-TopN, deterministic ties, uneven domain, surviving-domain totals, and non-additive gated cases.
- [x] Mismatch cases explain why old/global inner ranking behavior is unsafe.
- [x] C2 remains gated and no production Java behavior was changed.

## Failed Items

No blocking failed items were found for the C1 design gate.

## Evidence

- `11_pivot_stage5b_cascade_generate_semantics.md` defines goals, non-goals, terminology, ranking grain, Having order, tie-breaking, row/column behavior, subtotal rules, rejected shapes, SQL oracle, mismatch cases, and C2 gates.
- The design now uses explicit NULL bucket ordering in TopN tie-break examples.
- SQL oracle examples use staged CTEs with parent-domain filtering before child-level ranking.
- SQL oracle examples use null-safe join predicates for surviving-domain filtering.
- `workitems/pivot-java-engine-9.1.0-followups.md` records C1 completion and keeps C2 gated.
- `git diff --check` reported only Windows line-ending warnings and no whitespace errors.

## Risks / Open Items

- C1 is design-only. No C2 production code, query planner implementation, or SQL parity test was added.
- Product still needs to explicitly accept the chosen current-level aggregate semantics if business users expect subtree/descendant ranking.
- C2 must add real SQL data parity tests for staged CTE behavior across supported dialects.
- Dialects without window-function support remain fail-closed or fallback-only; C2 must not silently downgrade ambiguous multi-level semantics.
- Release recommendation remains beta because Stage 5B is not implemented.

## Final Decision

PIVOT-91-C1 is signed off as `accepted-with-risks`. The semantic contract is strong enough to guide C2 planning, but it does not authorize production implementation until product/architecture confirms the chosen semantics and the required SQL parity tests are planned.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/pivot-stage5b-c1-cascade-generate-semantics-acceptance.md`
- blocking_items: none
- follow_up_required: yes
