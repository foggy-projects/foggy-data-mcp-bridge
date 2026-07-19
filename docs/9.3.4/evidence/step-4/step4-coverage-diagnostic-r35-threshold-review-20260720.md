---
evidence_type: threshold-review
version: 9.3.4
step: 4
run_id: step4-coverage-20260720-diagnostic-r35
tested_commit: 93b3993e41d285300cb6968865da229319dad26d
candidate_sha256: b3a842bac72dd23401edb5a36946ac51cc28793961fee7b1738bdd6d6fdf647e
reviewer: "Codex /root + Reviewer A + Reviewer B"
independent_reviewers:
  - "/root/r35_result_audit (Reviewer A)"
  - "/root/r35_capsule_review (Reviewer B)"
reviewed_at: 2026-07-20
decision: confirm-observed-thresholds
status: reviewed-cfreeze-authorized
formal_status: pending
user_confirmation: not-asserted
B_H_M_L: "0/0/0/0"
---

# Step 4 r35 threshold review

## Decision

`step4-coverage-20260720-diagnostic-r35` is approved as the only reviewed diagnostic source for the
next Step 4 Cfreeze. It ran from fresh, non-shallow, clean Cdiag
`93b3993e41d285300cb6968865da229319dad26d` and completed as
`diagnostic-observed / completed / exit 0`.

Reviewer A independently recomputed the diagnostic and threshold candidate. Reviewer B independently
verified, rebuilt, and materialized the Git-safe capsule. Both returned `APPROVE / B/H/M/L=0/0/0/0`,
mandatory actions=`0`.

This is not a formal run, coverage audit, feature acceptance, Step 5 authorization, 9.3.5 acceptance,
or 9.4.0 authorization.

## Candidate recomputation

- Public `validate-diagnostic-run` and `verify-threshold-candidate` passed for r35.
- Required evidence is `773+59 structural / 5707 testcase / F0E0S0`; Unit=`681+55 / 4941`,
  Integration=`47+4 / 320`, Step 3 required=`45 / 446`, and excluded Addon companion=`2 / 6`.
- Execution provenance is `23` files / `48` sessions. Source before/after is byte-identical and
  runner-owned cleanup closed at `0/0/0`.
- Aggregate high-water is line=`54624/76830`, branch=`26112/44870`, and complexity=`17659/35571`.
  The candidate freezes exact observed line and branch values; complexity remains a non-lowerable
  reviewed high-water constraint.
- The critical policy remains `12` classes / `23` applicable metrics / `1` approved structural N/A /
  below-floor=`0`.
- The final public effective-POM receipt is a regular non-link at exact `0644`, and report provenance
  records and verifies that mode. r10 is not reused: it remains contract-invalid historical evidence.

## Git-safe capsule review

The canonical capsule archive SHA-256 is
`e3d8d6fab1539c6122a44e6954995366712e006fa014cb8b126078f057479d2e`; its manifest SHA-256 is
`5267d4b255455b36eccc830e759871bf45983a30d7cf3c03d11e67ab3233f18a`.

Reviewer B rebuilt the capsule in an independent temporary directory. The archive, manifest, and member
list were byte-identical to canonical output; materialization yielded only:

- `evidence/diagnostic-attestation.json`
- `evidence/jacoco.xml`

No raw log, execution bytes, runtime closure, container/process identity, or historical r34/r10 material
is retained.

## Cfreeze authorization boundary

This review authorizes exactly one direct-single-parent Cfreeze from
`93b3993e41d285300cb6968865da229319dad26d`. Its non-document delta may only project the r35 candidate
into the frozen Step 4/Step 6 formalization paths. A genuinely fresh formal run and all post-formal gates
remain mandatory.
