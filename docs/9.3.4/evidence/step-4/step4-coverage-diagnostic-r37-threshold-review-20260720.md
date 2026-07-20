---
evidence_type: threshold-review
version: 9.3.4
step: 4
run_id: step4-coverage-20260720-diagnostic-r37
tested_commit: 9743f97d9d935d5e26311b78c158755bca51f17a
candidate_sha256: f8b8cac460966759466c5787c46bf5f093f1b11ec50791f1db93f99ea6d7633d
reviewer: "Codex /root + Reviewer A + Reviewer B"
independent_reviewers:
  - "/root/r37_candidate_review (Reviewer A)"
  - "/root/r37_capsule_review (Reviewer B)"
reviewed_at: 2026-07-20T05:45:31Z
decision: confirm-observed-thresholds
status: reviewed-cfreeze-authorized
formal_status: pending
user_confirmation: not-asserted
B_H_M_L: "0/0/0/0"
---

# Step 4 r37 threshold review

## Decision

`step4-coverage-20260720-diagnostic-r37` is the only reviewed diagnostic source
for the next Step 4 Cfreeze. It ran from clean Cdiag
`9743f97d9d935d5e26311b78c158755bca51f17a` and completed as
`diagnostic-observed / completed / exit 0` with identical before/after source
seals.

Reviewer A independently recomputed the diagnostic and threshold candidate.
Reviewer B independently verified the Git-safe attestation and capsule, then
materialized and deterministically rebuilt it outside the repository. Both
returned `PASS / B/H/M/L=0/0/0/0`; mandatory actions=`0`.

This review is not a formal run, feature acceptance, Step 5 authorization,
9.3.5 acceptance, or 9.4.0 authorization.

## Candidate recomputation

- `validate-diagnostic-run` and `verify-threshold-candidate` passed for r37.
- Required evidence is `773+59 structural / 5707 testcase / F0E0S0`; Unit and
  Integration completed before Step 3 required, and the Addon companion is
  `2 / 6`.
- Execution provenance is `23` files / `48` sessions. Runner-owned cleanup
  closed with zero container, volume, and network residue.
- Aggregate observed/reviewed thresholds are line=`54624/76830` and
  branch=`26112/44870`; complexity is the reviewed non-lowerable high-water
  `17659/35571`.
- The critical policy is `12` classes / `23` applicable metrics / `1` approved
  structural N/A / below-floor=`0`.
- The candidate's pending predecessor is exactly
  `scripts/v934/step4/coverage-thresholds.json` SHA-256
  `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96`.

## Git-safe capsule review

The canonical capsule archive SHA-256 is
`209386ac9035183ccaa9c031f3d76047d66bb70bde07132e535c674f393a9c6b`; its
manifest SHA-256 is
`e9a69df14fd95a7dd15599dc1a72bd706026d67d12ec2ac333034e0126318e33`.
The safe diagnostic attestation SHA-256 is
`617e3191469404e9606a59755706919db562b9553c7b9aa764e370dd36675be3`.

The capsule contains only:

- `evidence/diagnostic-attestation.json`
- `evidence/jacoco.xml`

No execution bytes, runtime closure, unstructured output, process identity, or
historical diagnostic material is retained.

## Cfreeze authorization boundary

This review authorizes exactly one direct-single-parent Cfreeze from
`9743f97d9d935d5e26311b78c158755bca51f17a`. Its non-document delta may only
project the r37 candidate into the six governed Step 4/Step 6 formalization
paths. A fresh formal run and all post-formal gates remain mandatory.
