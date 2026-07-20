---
acceptance_scope: bug
version: 9.3.4
target: step4-addon-context-mtime-publication-order
status: signed-off
decision: accepted
signed_off_by: foggy-projects
signed_off_at: 2026-07-20
reviewed_by: independent r38 quality, coverage, documentation, final-signoff audit, and exact-Cfreeze artifact replays
blocking_items: []
follow_up_required: yes
evidence_count: 11
---

# Step 4 Addon Context Publication-Order Revalidation Acceptance

## Document Purpose

- intended_for: foggy-projects signoff owner / 9.3.4 root session / Step 5 owner / reviewer
- purpose: accept the changed Addon successor-runner remediation without reviving historical r11 authority or
  expanding the decision beyond Step 5 rehearsal readiness.

## Background

- delivery_spec:
  docs/9.3.4/workitems/BUG-step4-addon-context-mtime-publication-order.md
- target_outcome: under WSL timestamp inversion, preserve the frozen Step 3 anti-splice rule while publishing
  an authenticated Addon child context whose mtime is no earlier than its parent marker.
- signoff_scope: Cdiag 9743f97d9d935d5e26311b78c158755bca51f17a, its sole direct-child Cfreeze
  62361688d838ba0a73348900502924decfbeeb68, and formal run
  step4-coverage-20260720-formal-r38.

## Acceptance Basis

- canonical BUG was READY_FOR_SIGNOFF with the implementation result and five verification obligations;
- user explicitly approved this r38 revalidation and designated foggy-projects as signoff owner;
- the changed Addon runner and governed successor/Step 4/Step 6 hash closure are bound by Cdiag/Cfreeze;
- [formal-r38 evidence](../evidence/step-4/step4-coverage-formal-r38-pass-20260720.md) is the sole formal
  authority; r11 evidence/acceptance remain historical only for changed runner bytes;
- [same-Cfreeze Pivot companion](../evidence/step-4/step4-pivot-legacy-companion-r38-20260720.md),
  [independent quality](../quality/step4-formal-r38-addon-context-final-implementation-quality-20260720.md),
  and [r38 35+1 audit](../coverage/step4-replacement-coverage-audit-r38-20260720.md) all pass;
- final artifact replay was rerun in an exact clean Cfreeze worktree, returning ARTIFACT VALID stage=final;
  post-formal documentation commit 4be8f04663d1796a103f4864a8bb073addc0ce1e was deliberately excluded
  from that Cfreeze authority replay;
- exact-Cfreeze static replays passed: coverage contract, frozen diagnostic, successor overlay, Step 4/Step 6
  SHA manifests, and Step 6 CI workflow validation.

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| VC-1 | normal inherited publication remains valid | authenticated parent/child binding and formal final artifact remain valid | formal-r38 structured metadata | pass |
| VC-2 | parent-later path publishes child_mtime >= parent_mtime | temporary child timestamp is clamped only after canonical parent authentication | runner review plus r38 mtime invariant | pass |
| VC-3 | malformed, hash-mismatched, symlinked, or raced parent fails closed | pre-publication checks and post-publication parent recheck remain mandatory | independent r38 implementation quality | pass |
| VC-4 | stale-report and cross-run-splice negatives remain rejecting | frozen Step 3 consumer and E_CROSS_RUN_SPLICE are unchanged | successor/static closure plus quality review | pass |
| VC-5 | successor, Step 4, and Step 6 hash closures validate | all governed bindings/manifests and workflow contract validate | exact-Cfreeze static replays | pass |
| VC-6 | historic authority is not silently restored | r11 retained as history; new authority is r38 only | r38 evidence, audit, and this record | pass |
| VC-7 | owner approves the bounded BUG scope | explicit foggy-projects approval is recorded | user approval and canonical writeback | pass |

## Implementation Quality

- scope and changed surface: only the declared Addon successor-runner amendment and its governed closure changed
  in Cdiag; the Cfreeze formalization is a direct child. Post-formal commits are documentation/acceptance
  records only. No production API, POM, database policy, threshold, inventory, or module boundary changed.
- maintainability and duplication: authenticated-parent verification, timestamp clamp, atomic publication, and
  post-publication recheck remain in the single declared runner path; no consumer bypass or duplicate rule was
  introduced.
- error handling and edge cases: malformed/changed parent material rejects fail-closed; standalone remains
  parentless; the frozen consumer remains the cross-run authority boundary.
- contract, data and compatibility: no API/event/config/data migration change. Existing report counts,
  thresholds, run-owned fixture semantics, and public receipt mode remain stable.
- terminology and documentation: r38 records separate the 35+1 coverage denominator from the independent
  Addon BUG control, preserve r11 history, and state the limited downstream opening consistently.

## Evidence Matrix

| Item | Risk | Unit | Integration | E2E | Playwright | Manual | Evidence | Coverage |
|---|---|---|---|---|---|---|---|---|
| VC-1/VC-2 | critical | inherited lifecycle and mtime invariant | formal required lanes actually ran | N/A | N/A | exact-Cfreeze artifact replay | formal-r38 + quality | covered |
| VC-3/VC-4 | critical | fail-closed branch/static negative closure | successor/Step 3 boundary preserved | N/A | N/A | independent implementation review | quality + overlay/static replay | covered |
| VC-5 | critical | N/A | formal contract/manifests/workflow validation | N/A | N/A | exact-Cfreeze rerun | static replay receipts | covered |
| VC-6 | critical | N/A | N/A | N/A | N/A | historical-authority review | r38 formal/audit/docs review | covered |
| Pivot supplemental | major | N/A | focused Failsafe 1/F0E0S0 | N/A | N/A | source/report identity review | r38 Pivot companion | covered |
| 35+1 audit | critical | N/A | N/A | N/A | N/A | workitem/evidence mapping | r38 scope and audit | covered |
| VC-7 | critical | N/A | N/A | N/A | N/A | explicit owner approval | this record | covered |

## Failed Items

- none

## Risks / Follow-ups

- Step 5 must still run a new fresh rehearsal and portable replay; no historical formal/run root is reused.
- Steps 6-7, 9.3.5, and 9.4.0 remain closed until their own dependency gates complete.
- DEBT-unit-mysql57-fixture-classification-migration remains open and is due before 9.3.5 version acceptance.

## Final Decision

- decision: accepted
- rationale: every canonical BUG verification obligation has current r38 evidence, exact-Cfreeze artifact replay
  and governed static closure pass, the independent reviews report B/H/M/L=0/0/0/0, and the named owner
  explicitly approved the bounded revalidation.
- blocking_items: none
- follow_up_owner_and_due: Step 5 owner must execute a fresh rehearsal under
  FEATURE-v934-release-authority-and-ci.md before Steps 6-7; the classification-debt owner must close Gate 0
  before 9.3.5 version acceptance.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: foggy-projects
- signed_off_at: 2026-07-20
- acceptance_record:
  docs/9.3.4/acceptance/step4-addon-context-revalidation-acceptance-20260720.md
- blocking_items: none
- follow_up_required: yes
