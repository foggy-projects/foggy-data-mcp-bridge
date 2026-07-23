---
evidence_type: successful-diagnostic-portable-capsule
version: 9.3.4
step: 4
run_id: step4-coverage-20260720-diagnostic-r34
tested_commit: 72175735a8409116a42afba4d69e5f7ec9fb50fe
status: diagnostic-observed
decision: threshold-candidate-reviewed-cfreeze-authorized
candidate_status: review-complete
capsule_status: sealed
review_evidence_sha256: 4df76ed31fb48f3e2f48597ece1936e4e75c7dabe51b2df9a386d461870acd8e
recorded_at: 2026-07-20
---

# Step 4 coverage diagnostic r34 pass

## Decision

Fresh r34 completed from the clean, pushed Cdiag
`72175735a8409116a42afba4d69e5f7ec9fb50fe`. Its status is
`diagnostic-observed / completed / exit 0`. Public diagnostic recomputation,
immutable threshold-candidate verification, and both independent reviews
passed. r34 is the only reviewed diagnostic source authorized for a
direct-single-parent Cfreeze.

This is not a formal run, acceptance artifact, coverage audit, Step 4 feature
acceptance, or 9.3.4 version signoff. Steps 5–7, 9.3.5, and 9.4.0 remain
closed until the Cfreeze, fresh formal run, and post-formal gates complete.

## Sealed diagnostic result

- Source before/after is byte-identical; all required lanes completed and
  runner-owned cleanup is `0/0/0`.
- Required evidence is `773+59 structural / 5707 testcase / F0E0S0`.
  Unit=`681+55 / 4941`, Integration=`47+4 / 320`, and Step 3 required=`45 /
  446` (database=`29 / 370`, external=`16 / 76`).
- Addon companion evidence is `2 / 6 / F0E0S0`, excluded from the required
  union. Execution provenance is `23` files / `48` sessions.
- Aggregate counters are instruction=`252725/352456`, line=`54624/76830`,
  branch=`26112/44870`, method=`9068/12701`, class=`1503/1716`, and
  complexity=`17659/35571`.
- The critical policy is `12` classes / `23` applicable metrics / `1`
  structural N/A / below-floor=`0`; the model external gate passed.

The aggregate line, branch, and complexity values meet the governed r34
high-water exactly. The candidate freezes line and branch as exact observed
thresholds; complexity remains a reviewed diagnostic high-water constraint and
may not be lowered by later formalization.

## Candidate and Git-safe closure

The immutable threshold candidate is
`step4-coverage-diagnostic-r34-threshold-candidate-20260720.json`, SHA-256
`67161164713cdb520cc57d4b2cfa6c5f6d43bf6753e5964c9b1514e1317b08bc`.
It was independently recomputed and contains no historical or noncanonical
run material.

The Git-safe portable capsule is bound to the same r34 attestation and JaCoCo
XML. Its archive SHA-256 is
`9cce912b59822ebef734a37e3d61a847f06e9983fb50dd7a67a668c1493a1cc7` and
its manifest SHA-256 is
`a7477cab81887b482dc61dd083dd2359d6eea5c165d31be265020aa8e4e6a1c1`.
It contains only `evidence/diagnostic-attestation.json` and
`evidence/jacoco.xml`; raw execution, logs, runtime closure, and
container/process/host metadata are excluded by construction.

Reviewer A (candidate/high-water) and Reviewer B (capsule/replay) each
returned `APPROVE / B0 H0 M0 L0`, mandatory actions=`0`. The finalized review
record is `step4-coverage-diagnostic-r34-threshold-review-20260720.md`,
SHA-256=`4df76ed31fb48f3e2f48597ece1936e4e75c7dabe51b2df9a386d461870acd8e`.

## Only legal next gate

Create one Cfreeze whose only parent is r34's tested Cdiag and whose
non-document delta is limited to the frozen Step 4/Step 6 formalization paths.
Then push that Cfreeze and perform a genuinely fresh formal run. r34's run
tree, execution bytes, and raw XML must not be reused as formal evidence.
