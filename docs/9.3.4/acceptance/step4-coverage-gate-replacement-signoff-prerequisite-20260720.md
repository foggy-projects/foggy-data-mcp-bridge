---
doc_role: signoff_prerequisite_record
version: 9.3.4
scope: step4-feature-replacement-authority
status: approved-awaiting-ready-for-signoff
recorded_at: 2026-07-20
---

# Step 4 replacement acceptance signoff prerequisite

## Evidence readiness

The replacement evidence package is complete:

- fresh formal-r11 is `formal-passed`, independently replayed, and proves exact public receipt mode=`0644`;
- same-Cfreeze Pivot supplemental evidence passed;
- independent final implementation quality is `APPROVE / B/H/M/L=0/0/0/0`;
- replacement coverage audit is approved with `35/35 covered`, separate report-stage gate=`passed`, and
  critical/major evidence gaps=`0/0`.

## Approval resolved / remaining readiness gate

The delivery-signoff procedure requires a Step 4-scoped canonical delivery spec at `READY_FOR_SIGNOFF` before
an official feature acceptance decision can be made. The Step 4 candidate at
`docs/9.3.4/workitems/FEATURE-step4-replacement-coverage-gate-signoff.md` is now `APPROVED` via direct
project-owner request, with `foggy-projects` as the named signoff owner. It still needs the approved-contract
evidence replay and `Implementation Result` required to reach `READY_FOR_SIGNOFF`. The separate canonical 9.3.4 delivery spec
`docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md` remains `ULTRA_EXECUTING` and explicitly
scoped to Steps 5–7, not this completed Step 4 feature boundary.

Therefore this record does **not** mark Step 4 accepted, does not change Step 5 to ready, and does not open
9.3.5 or 9.4.0. It preserves the completed evidence and names the one missing authority input.

## Required handoff

The approved Step 4-scoped canonical delivery spec now requires its bounded implementation/evidence replay.
Only after it reaches `READY_FOR_SIGNOFF` may `foggy-projects` apply the feature-signoff template against the
evidence package without rerunning formal-r11.
