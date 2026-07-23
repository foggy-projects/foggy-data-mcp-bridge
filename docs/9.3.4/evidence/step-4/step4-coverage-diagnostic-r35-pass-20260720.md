---
evidence_type: successful-diagnostic-portable-capsule
version: 9.3.4
step: 4
run_id: step4-coverage-20260720-diagnostic-r35
tested_commit: 93b3993e41d285300cb6968865da229319dad26d
status: diagnostic-observed
decision: threshold-candidate-reviewed-cfreeze-authorized
candidate_status: review-complete
capsule_status: sealed
review_evidence_sha256: 12e3cb12e3519fd9aebd5e05aed56279d48ec4e80e0df671aa2ecc484ae7154f
recorded_at: 2026-07-20
---

# Step 4 coverage diagnostic r35 pass

## Decision

Fresh r35 completed from clean, pushed Cdiag `93b3993e41d285300cb6968865da229319dad26d` as
`diagnostic-observed / completed / exit 0`. Public diagnostic recomputation, threshold-candidate
verification, and both independent reviews passed. r35 is the only reviewed diagnostic source authorized
for one direct-single-parent Cfreeze.

This is not a formal run, coverage audit, Step 4 feature acceptance, Step 5 authorization, 9.3.5
acceptance, or 9.4.0 authorization.

## Sealed diagnostic result

- Required evidence is `773+59 structural / 5707 testcase / F0E0S0`; Unit=`681+55 / 4941`,
  Integration=`47+4 / 320`, and Step 3 required=`45 / 446`.
- Addon companion is `2 / 6 / F0E0S0`, excluded from the required union. Execution provenance is
  `23` files / `48` sessions; source before/after is byte-identical and cleanup=`0/0/0`.
- Aggregate counters are line=`54624/76830`, branch=`26112/44870`, and
  complexity=`17659/35571`; all exactly meet the governed high-water.
- The critical policy is `12` classes / `23` applicable metrics / `1` structural N/A /
  below-floor=`0`.
- The final report public effective-POM receipt is exact `0644`; report provenance records and verifies
  that mode. This is a fresh r35 result, not a reuse of formal-r10.

## Candidate and Git-safe closure

The immutable threshold candidate is
`step4-coverage-diagnostic-r35-threshold-candidate-20260720.json`, SHA-256
`b3a842bac72dd23401edb5a36946ac51cc28793961fee7b1738bdd6d6fdf647e`.

The Git-safe portable capsule archive and manifest SHA-256 values are respectively
`e3d8d6fab1539c6122a44e6954995366712e006fa014cb8b126078f057479d2e` and
`5267d4b255455b36eccc830e759871bf45983a30d7cf3c03d11e67ab3233f18a`.
It retains only the diagnostic attestation and JaCoCo XML; raw logs, execution bytes, runtime closure,
and container/process/host material are excluded.

Reviewer A and Reviewer B each returned `APPROVE / B0 H0 M0 L0`, mandatory actions=`0`. The review
record is `step4-coverage-diagnostic-r35-threshold-review-20260720.md`.

## Only legal next gate

Create one Cfreeze whose only parent is r35's tested Cdiag. Its non-document delta is limited to the
frozen Step 4/Step 6 formalization paths. Then push that Cfreeze and run a genuinely fresh formal
successor; r35 execution and raw XML are not formal evidence.
