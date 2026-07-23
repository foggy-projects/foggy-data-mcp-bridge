---
doc_role: signoff_prerequisite_record
version: 9.3.4
scope: step4-feature-replacement-authority
status: satisfied-by-feature-acceptance
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
`docs/9.3.4/workitems/FEATURE-step4-replacement-coverage-gate-signoff.md` was approved via direct
project-owner request, completed its bounded evidence replay, and is now `READY_FOR_SIGNOFF`; `foggy-projects`
is the named signoff owner. The separate canonical 9.3.4 delivery spec
`docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md` remains `ULTRA_EXECUTING` and explicitly
scoped to Steps 5–7, not this completed Step 4 feature boundary.

This prerequisite is satisfied by the accepted Step 4 feature decision at
`acceptance/step4-coverage-gate-replacement-acceptance-20260720.md`. Step 5 is now `ready / not-started`;
9.3.5 and 9.4.0 remain closed until 9.3.4 version signoff.

## Required handoff

The designated owner applied the feature-signoff template without rerunning formal-r11. This record remains a
precondition/history record; the acceptance record above is the authoritative decision.
