---
acceptance_scope: feature
version: 9.3.4
target: step4-replacement-coverage-gate-signoff
status: signed-off
decision: accepted
signed_off_by: foggy-projects
signed_off_at: 2026-07-20
reviewed_by: independent fresh-clone evidence replay and foggy-projects designated signoff
blocking_items: []
follow_up_required: yes
evidence_count: 10
---

# Step 4 Replacement Coverage Gate Feature Acceptance

## Document Purpose

- intended_for: foggy-projects signoff owner / 9.3.4 root session / Step 5 owner / reviewer
- purpose: 对 approved Step 4 replacement authority 作出独立、证据驱动的 feature acceptance 决定。

## Background

- delivery_spec: `docs/9.3.4/workitems/FEATURE-step4-replacement-coverage-gate-signoff.md`
- target_outcome: 以 fresh formal-r11 为唯一 replacement formal authority，完成 Step 4 coverage gate
  feature signoff；不签收 9.3.4 version，不实施 Step 5–7、9.3.5 或 9.4.0。
- signoff_scope: Cfreeze `4b17dbe34ae17168d44377c19ea6637b4c37a200`，其 direct single parent 为
  Cdiag `93b3993e41d285300cb6968865da229319dad26d`。

## Acceptance Basis

- approved delivery spec and its bounded `Implementation Result` at `READY_FOR_SIGNOFF`;
- [formal-r11 evidence](../evidence/step-4/step4-coverage-formal-r11-pass-20260720.md) and canonical final
  artifact replay at the tested Cfreeze (`ARTIFACT VALID stage=final`);
- same-Cfreeze [Pivot companion](../evidence/step-4/step4-pivot-legacy-companion-r11-20260720.md);
- independent [final implementation quality](../quality/step4-formal-r11-final-implementation-quality-20260720.md);
- [replacement audit](../coverage/step4-replacement-coverage-audit-20260720.md) and its
  [35+1 scope](../coverage/step4-replacement-audit-scope-20260720.json);
- fresh detached-clone static replay of contract, frozen diagnostic, successor overlay and Step 6 CI-workflow
  validators; all passed;
- project-owner approval and designated signoff owner=`foggy-projects` recorded in the canonical delivery spec.

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | fresh formal sole authority, final replay, receipt=`0644`, cleanup zero | formal-r11 final artifact validates; required=`773+59/5707/F0E0S0`; receipt regular/non-link/non-empty `0644`; cleanup=`0/0/0` | formal-r11 record, independent verifier, topology/static replay | pass |
| AC-2 | same-Cfreeze Pivot supplemental branch proof outside formal totals | focused result=`1/F0E0S0`; source/report identity cross-checked; aggregate totals unchanged | Pivot companion and independent quality review | pass |
| AC-3 | final implementation quality with no blocking debt | `APPROVE`, B/H/M/L=`0/0/0/0`, mandatory fixes=`0` | final quality record | pass |
| AC-4 | 35-row audit plus independently blocking receipt gate | `31+4=35` covered, extra gate passed, critical/major gaps=`0/0` | audit scope JSON and audit record | pass |
| AC-5 | owner approves scoped contract and names signoff owner | direct owner authorization; owner=`foggy-projects` | canonical approval record | pass |
| AC-6 | independent signoff without downstream scope expansion | this acceptance record issued by designated owner; Step 5 only becomes ready/not-started | this record and canonical status writeback | pass |

## Implementation Quality

- scope and changed surface: reviewed Cfreeze is direct-child topology; all post-Cfreeze changes are evidence/
  governance documentation only. No production code, POM, runner, threshold, inventory, SPI, module-boundary
  or Step 5 implementation change entered this feature signoff.
- maintainability and duplication: no new runtime implementation; the canonical spec and this concise record
  reference existing evidence rather than copying raw runtime material.
- error handling and edge cases: receipt mode, fail-closed artifact binding, cleanup and historical-authority
  exclusion are covered by the formal evidence and replay.
- contract, data and compatibility: no API/config/data migration change; Unit MySQL classification debt remains
  explicitly deferred to 9.3.5 version acceptance.
- terminology and documentation: audit denominator remains 35 rows plus one separate blocking gate; it is not
  misrepresented as a 36-row audit.

## Evidence Matrix

| Item | Risk | Unit | Integration | E2E | Playwright | Manual | Evidence | Coverage |
|---|---|---|---|---|---|---|---|---|
| AC-1 | critical | formal required union actually ran | formal required integration/database/external lanes actually ran | N/A | N/A | independent artifact/topology replay | formal-r11 + verifier + static replays | covered |
| AC-2 | major | N/A | focused Failsafe companion actually ran | N/A | N/A | source/report identity review | Pivot companion + quality review | covered |
| AC-3 | critical | N/A | N/A | N/A | N/A | independent quality review | final quality record | covered |
| AC-4 | critical | N/A | N/A | N/A | N/A | workitem/evidence mapping review | audit scope JSON + audit record | covered |
| AC-5 | critical | N/A | N/A | N/A | N/A | owner authorization | canonical approval record | covered |
| AC-6 | critical | N/A | N/A | N/A | N/A | designated independent signoff | this acceptance record | covered |

## Failed Items

- none

## Risks / Follow-ups

- `DEBT-unit-mysql57-fixture-classification-migration` remains open and is due before 9.3.5 version acceptance;
  it is a preserved, explicit handoff rather than a Step 4 feature blocker.
- Step 5–7 remain governed by the separate `FEATURE-v934-release-authority-and-ci.md` contract. This decision
  changes Step 5 only to `ready / not-started`; it does not sign off 9.3.4 or open 9.3.5/9.4.0.

## Final Decision

- decision: `accepted`
- rationale: all six approved acceptance criteria have direct, current authority evidence; independent replay
  found no evidence or scope blocker, and no production surface changed after the tested Cfreeze.
- blocking_items: none
- follow_up_owner_and_due: Step 5 owner under `FEATURE-v934-release-authority-and-ci.md`; Unit MySQL debt owner
  before 9.3.5 version acceptance.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: foggy-projects
- signed_off_at: 2026-07-20
- acceptance_record: `docs/9.3.4/acceptance/step4-coverage-gate-replacement-acceptance-20260720.md`
- blocking_items: none
- follow_up_required: yes
