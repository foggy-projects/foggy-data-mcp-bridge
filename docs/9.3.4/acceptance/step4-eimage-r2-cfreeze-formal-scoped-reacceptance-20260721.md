---
acceptance_scope: bug
version: 9.3.4
target: BUG-STEP5-PACKAGE-IMAGE-EIMAGE-CLASSIFICATION
status: signed-off
decision: accepted-with-risks
signed_off_by: foggy-projects
signed_off_at: 2026-07-21
reviewed_by: codex-independent-signoff
blocking_items: []
follow_up_required: yes
evidence_count: 3
---

# E_IMAGE formal-r2 scoped reacceptance

## Acceptance Basis

- approved canonical delivery spec and scoped successor-overlay replan;
- direct-child Cfreeze plus fresh non-shallow formal;
- formal status `formal-passed / completed / exit=0`;
- independent frozen, candidate, final, inventory, Step4, successor overlay,
  and Step6 verification passed;
- owner signoff: foggy-projects, 2026-07-21.

## Contract Conformance

| Item | Result |
|---|---|
| Legacy nine-field receipt and E_IMAGE compatibility | pass |
| Three bounded E_IMAGE subphases and fail-closed fallback | pass |
| Cdiag → Cfreeze → formal evidence chain | pass |
| No API/SPI/POM/workflow/Dockerfile/pointer expansion | pass |

## Risks / Follow-ups

- P2: an underlying filesystem I/O or permission failure may leave a physical
  cleanup residual. The flow remains fail-closed and cannot publish a
  candidate or success record. Follow-up remains required before unrelated
  operational claims.

## Final Decision

- decision: accepted-with-risks
- rationale: all scoped acceptance criteria and independent formal evidence
  pass; the single residual risk is non-blocking and fail-closed.
- blocking_items: none
- follow_up_owner_and_due: foggy-projects / before operational cleanup claim

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: foggy-projects
- signed_off_at: 2026-07-21
- acceptance_record: docs/9.3.4/acceptance/step4-eimage-r2-cfreeze-formal-scoped-reacceptance-20260721.md
- blocking_items: none
- follow_up_required: yes
