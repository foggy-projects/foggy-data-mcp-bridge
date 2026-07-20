---
evidence_type: threshold-review
version: 1
step: 4
run_id: step4-coverage-20260720-diagnostic-r40
tested_commit: 6b92451294f3a324ada156a9c088756d711c790c
source_sha256: caca16f6cd17e8fa2195dc2e355013d8da536cd0d0c123644b3ea14f18f5d5cb
candidate_path: docs/9.3.4/evidence/step-4/step4-coverage-20260720-diagnostic-r40-threshold-candidate-20260720.json
candidate_sha256: 28d42a09a5da68a326c2effc3fad95da0b1f3b3bd794dd1814fb505388e259cb
attestation_sha256: 9fbd58fa82cf8fee1a2cc65d2ad2133c9cef4e26d4eafd22edaa2c7ddb980c39
capsule_archive_path: docs/9.3.4/evidence/step-4/step4-coverage-20260720-diagnostic-r40-portable-capsule.tar.gz
capsule_archive_sha256: 6fa51912862c7787efeaa49e029af80821af04bb486f822c4d5088e795ce0637
capsule_manifest_path: docs/9.3.4/evidence/step-4/step4-coverage-20260720-diagnostic-r40-portable-capsule.manifest.json
capsule_manifest_sha256: a82409581842b6d69d7f58681f0d62717482cfaa5291f3b3bd0ecb4261c87eb1
independent_reviewers:
  - reviewer: codex-independent-reviewer-a
    path: docs/9.3.4/quality/step4-coverage-20260720-diagnostic-r40-candidate-independent-review-20260720.md
    sha256: a2e5309318c4eb1b38ea6ac48dbf8445aead68d383e6700e260714ed7e898f36
    verdict: pass
  - reviewer: codex-independent-reviewer-b
    path: docs/9.3.4/quality/step4-coverage-20260720-diagnostic-r40-capsule-independent-review-20260720.md
    sha256: 4b14d48c9f18ff664b78713738aaf71da0cf072087fd0d0cc21efa4270d56a1f
    verdict: pass
reviewed_at: 2026-07-20T10:16:03Z
decision: confirm-observed-thresholds
status: reviewed-cfreeze-authorized
formal_status: pending
user_confirmation: not-asserted
B: 0
H: 0
M: 0
L: 0
---

# r40 threshold freeze review

Both independent reviews passed with no blockers, high, medium, or low
findings. Reviewer A independently recomputed the r40 diagnostic and the
threshold candidate. Reviewer B independently verified, materialized, and
deterministically rebuilt the Git-safe capsule. They bind the same run,
Cdiag, source seal, candidate, attestation, archive, and manifest listed
above.

The reviewed aggregate minima are line `54624/76830` and branch
`26112/44870`; all twelve critical classes are at their exact observed
minimum and the only zero-total metric remains the explicit not-applicable
exception. The capsule retains only the hash-safe attestation and JaCoCo XML
members; raw execution bytes, runtime closure, and unstructured output remain
excluded.

This review authorizes exactly one direct, single-parent Cfreeze successor of
the tested Cdiag, containing only the prescribed Step 4/Step 6 formalization
delta and `docs/9.3.4/**`. Formal validation, Step 5 replay, version signoff,
and user acceptance remain pending.
