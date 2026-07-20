---
doc_type: bug-remediation
version: 9.3.4
ticket: step4-addon-context-mtime-publication-order
status: ACCEPTED
parent_feature: step4-replacement-coverage-gate-signoff
authorization: ongoing approved 9.3.4 release-authority scope
canonical: true
revalidation_status: accepted-for-step5-rehearsal
opened_at: 2026-07-20
---

# Step 4 Addon child-context publication order

## Problem

在 WSL 文件时间短暂回退时，已验证的 Step 3 parent marker 可能比随后发布的 Addon child
`run-context.json` 具有更晚的 `mtime_ns`。Step 3 的 anti-splice guard 因而正确地以
`E_CROSS_RUN_SPLICE` 拒绝该 run；不得删除、放宽或在 consumer 端绕过该 guard。

## Approved Remediation Boundary

- 仅修改已声明为 Step 4 successor amendment 的
  `scripts/verify-v934-preagg-addon-lifecycle.sh`。
- 仅在 inherited authority 已验证 canonical parent path、regular/non-link identity、SHA-256 与
  schema 后，为 child context 的临时 inode 设置不早于 parent marker 的时间下界；随后 fsync、
  原子发布、重读 child bytes，并再次核验 parent identity/SHA。
- standalone 模式不接收 parent provenance；冻结的 `scripts/v934/step3/**`、其 manifest 和
  `E_CROSS_RUN_SPLICE` consumer rule 均不得修改。
- 不改变 production API、coverage threshold、report cardinality、fixture ownership 或 release
  authority scope。

## Required Revalidation

该修复改变了正式 runner bytes，故 formal-r11 与其 feature acceptance 仅保留为历史证据，不能
覆盖新 runner。必须完成新的 clean Cdiag → fresh diagnostic → candidate/independent review →
direct-single-parent Cfreeze → fresh formal → owner reacceptance → fresh Step 5 rehearsal/portable replay
链路后，才可恢复完整 downstream authority。r38 owner acceptance 只恢复 Step 5 的 fresh-entry
authority，不预先打开 Step 6、Step 7、9.3.5 或 9.4.0。

## Verification Obligations

- normal parent/child publication remains valid;
- forced parent-later mtime path preserves `child_mtime >= parent_mtime` before atomic publication;
- malformed, hash-mismatched, symlinked, or publication-raced parent evidence fails closed;
- existing stale-report and downstream cross-run-splice negatives remain rejecting;
- successor, Step 4, and Step 6 hash closures validate before any diagnostic run.

## Non-Goals

- no historical runtime artifact reuse or cross-run evidence splice;
- no Step 5–7, 9.3.5, or 9.4.0 production implementation advancement until revalidation closes.

## Implementation Result

- implementation_summary: the declared Addon successor runner now authenticates a canonical inherited parent,
  clamps only the temporary child timestamp to the authenticated parent lower bound, fsyncs, atomically
  publishes, rereads the child, and rechecks the parent identity/digest. The frozen Step 3 consumer and
  E_CROSS_RUN_SPLICE rule are unchanged.
- changed_surface: Cdiag 9743f97d9d935d5e26311b78c158755bca51f17a changes the declared
  scripts/verify-v934-preagg-addon-lifecycle.sh successor amendment and its governed hash closure. Cfreeze
  62361688d838ba0a73348900502924decfbeeb68 freezes the r37 thresholds/contracts and is its sole direct child.
- tested_authority: fresh formal-r38 on that Cfreeze is formal-passed / completed / exit=0. It preserves
  required=773+59/5707/F0E0S0, Addon=2/6/F0E0S0, 23 exec / 48 sessions, high-water
  line/branch/complexity=54624/76830 / 26112/44870 / 17659/35571, public receipt mode 0644, and cleanup 0/0/0.
- evidence: formal result, same-Cfreeze Pivot companion, independent final quality, and the r38 35+1
  replacement audit are linked in the Signoff Readiness section below. They contain only safe summaries and
  reproducible digests.
- deviations: none. r11 formal and its acceptance remain immutable historical evidence, not authority for the
  changed runner bytes.
- residual_risks: a fresh Step 5 rehearsal and portable replay remain required after a new owner decision;
  the unrelated Unit MySQL classification debt remains due before 9.3.5 version acceptance.

## Verification Evidence

| Obligation | Current evidence | Result |
|---|---|---|
| normal inherited parent/child publication | formal-r38 structured parent/child binding and final artifact | passed |
| parent-later mtime clamp preserves child_mtime >= parent_mtime | reviewed clamp branch plus r38 structured mtime invariant | passed |
| malformed, hash-mismatched, symlinked, or publication-raced parent fails closed | independent implementation-quality review of authenticated-parent and post-publication recheck paths | passed |
| stale-report and downstream cross-run-splice negatives remain rejecting | frozen Step 3 consumer unchanged; formal static closure and successor validation | passed |
| successor, Step 4, and Step 6 hash closures validate | formal post-run overlay, manifest, contract, and CI workflow replay | passed |

## Signoff Readiness

- readiness: ACCEPTED
- owner_decision: explicit foggy-projects re-signoff accepted for Cfreeze
  62361688d838ba0a73348900502924decfbeeb68.
- formal_evidence:
  [formal-r38](../evidence/step-4/step4-coverage-formal-r38-pass-20260720.md)
- supplemental_evidence:
  [Pivot r38](../evidence/step-4/step4-pivot-legacy-companion-r38-20260720.md)
- quality_evidence:
  [r38 implementation quality](../quality/step4-formal-r38-addon-context-final-implementation-quality-20260720.md)
- coverage_evidence:
  [r38 replacement audit](../coverage/step4-replacement-coverage-audit-r38-20260720.md)
- prerequisite:
  [owner-signoff prerequisite](../acceptance/step4-addon-context-revalidation-signoff-prerequisite-20260720.md)
- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: foggy-projects
- signed_off_at: 2026-07-20
- acceptance_record:
  ../acceptance/step4-addon-context-revalidation-acceptance-20260720.md
- downstream_authority: restored for fresh Step 5 rehearsal only; Steps 6-7, 9.3.5, and 9.4.0 remain closed.
