---
doc_role: feature_acceptance
doc_purpose: Sign off the PIVOT-91-C1.1 LLM-safe Cascade Generate disambiguation gate for 9.1.0.
acceptance_scope: feature
version: 9.1.0
target: PIVOT-91-C1.1 Cascade Generate LLM-safe disambiguation
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex acceptance reviewer
signed_off_at: 2026-05-02
reviewed_by: Codex
blocking_items: []
follow_up_required: yes
evidence_count: 8
---

# Feature Acceptance

## Background

PIVOT-91-C1.1 is the LLM-safe semantic addendum for Stage 5B Cascade Generate and multi-level TopN. Its purpose is to remove ambiguity before PIVOT-91-C2 implementation by defining which cascade requests may execute, which requests must fail closed, which dialects are in the first implementation scope, and which SQL oracle cases must exist before implementation starts.

This acceptance covers documentation and semantic readiness only. It does not accept or authorize PIVOT-91-C2 production implementation.

## Acceptance Basis

- `docs/9.1.0/README.md`
- `docs/9.1.0/detailed_design/00_java_pivot_engine_roadmap.md`
- `docs/9.1.0/detailed_design/11_pivot_stage5b_cascade_generate_semantics.md`
- `docs/9.1.0/detailed_design/12_pivot_stage5b_cascade_generate_disambiguation.md`
- `docs/9.1.0/workitems/pivot-java-engine-9.1.0-followups.md`
- `docs/9.1.0/acceptance/version-phase-signoff.md`
- `docs/9.0.0.beta/acceptance/version-signoff.md`
- Local verification commands listed in this record.

## Checklist

- [x] C1.1 states the LLM-safe first principle: ambiguous or unproven cascade requests must fail closed with rewriteable errors.
- [x] C1.1 defines a narrow C2 v1 whitelist: rows two-level cascade, additive ranking/having metrics, explicit `orderBy`, staged SQL only, and supported dialects.
- [x] C1.1 explicitly rejects missing `orderBy`, non-additive ranking/having/totals, tree mode, cross-axis cascade, unsupported dialects, planner failure, and current memory fallback.
- [x] C1.1 reconciles the C1 memory fallback wording by superseding it for cascade requests.
- [x] C1.1 defines dialect v1 decisions for SQLite, MySQL 8, PostgreSQL, MySQL 5.7, and SQL Server.
- [x] C1.1 defines having-only cascade handling and keeps ambiguous dependent multi-level having fail-closed in C2 v1.
- [x] C1.1 maps all required SQL oracle cases to test type, dialect profile, expected behavior, and refusal error code where applicable.
- [x] Workitem tracking keeps PIVOT-91-C2 gated and requires explicit implementation approval.
- [x] No production Java code was changed for this design gate.

## Failed Items

No blocking failed items were found for the C1.1 design gate.

## Evidence

- `12_pivot_stage5b_cascade_generate_disambiguation.md` defines cascade detection, fixed semantic assertions, C2 v1 whitelist, required execution capability, deterministic tie-breaking, totals behavior, non-additive rejection, structured error categories, dialect matrix, having-only decision, and oracle-to-test matrix.
- `11_pivot_stage5b_cascade_generate_semantics.md` now distinguishes single-level memory fallback from cascade requests and states that C1.1 forbids current memory fallback for cascade.
- `workitems/pivot-java-engine-9.1.0-followups.md` records PIVOT-91-C1.1 as review-ready before this acceptance and keeps PIVOT-91-C2 gated.
- `README.md` and `00_java_pivot_engine_roadmap.md` keep Stage 5B implementation outside the RC phase and require explicit approval before C2.
- The C1.1 oracle-to-test matrix includes refusal evidence requirements for MySQL 5.7 and unsupported dialect capability.
- The C1.1 error categories are rewriteable by an LLM and include `PIVOT_CASCADE_ORDER_BY_REQUIRED`, `PIVOT_CASCADE_SQL_REQUIRED`, `PIVOT_CASCADE_NON_ADDITIVE_REJECTED`, `PIVOT_CASCADE_CROSS_AXIS_REJECTED`, `PIVOT_CASCADE_TREE_REJECTED`, and `PIVOT_CASCADE_SCOPE_UNSUPPORTED`.
- Verification commands for status, diff hygiene, and targeted content checks passed on 2026-05-02.
- No executable code was changed, so Maven tests were not required for this acceptance.

## Risks / Open Items

- C1.1 is documentation-only. PIVOT-91-C2 still needs production implementation, real SQL parity tests, and refusal tests.
- SQL Server is rejected/deferred in C2 v1 because no CI profile and no oracle coverage are established.
- Multi-level having-only cascade remains rejected in C2 v1 unless the request can be decomposed into independent single-level evaluations.
- Non-additive leaf-output metrics remain outside C2 v1 unless a separate oracle proves correctness.
- Three-level cascade and cross-axis cascade remain rejected in C2 v1.
- C2 must enforce the documented rewriteable error codes in code; this acceptance does not prove runtime behavior.

## Final Decision

PIVOT-91-C1.1 is signed off as `accepted-with-risks`.

The LLM-safe disambiguation gate is complete enough to be used as the semantic contract for future C2 implementation planning. PIVOT-91-C2 remains gated until implementation approval is explicit and the documented SQL oracle/refusal tests are implemented and passing.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/pivot-stage5b-c1.1-cascade-generate-disambiguation-acceptance.md`
- blocking_items: none
- follow_up_required: yes
