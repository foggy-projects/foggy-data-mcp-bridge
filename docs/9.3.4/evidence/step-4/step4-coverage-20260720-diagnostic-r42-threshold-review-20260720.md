---
evidence_type: threshold-review
version: 1
step: 4
run_id: step4-coverage-20260720-diagnostic-r42
tested_commit: dd2ccde8e97c4dfe88e9a06141280a1d747ac737
source_sha256: dc87cb0e44080fa7af7113674275ef36d449dca1a8b27ffc2a402a0387bf0d0c
candidate_path: docs/9.3.4/evidence/step-4/step4-coverage-20260720-diagnostic-r42-threshold-candidate-20260720.json
candidate_sha256: 0f0f4b45b7e932139d985c62657dd137d075748b17eb3a4bdb5b41216e00717e
attestation_sha256: 8f2a2c0332f5cc7f12801b383e5441c5d9302b864f8cf976995853a69cb88556
capsule_archive_path: docs/9.3.4/evidence/step-4/step4-coverage-20260720-diagnostic-r42-portable-capsule.tar.gz
capsule_archive_sha256: 394a393224aabe31f5b9708bcc9c6874dfae23fb390291ec5d862c85123fc662
capsule_manifest_path: docs/9.3.4/evidence/step-4/step4-coverage-20260720-diagnostic-r42-portable-capsule.manifest.json
capsule_manifest_sha256: 3b275bbb7deaf1098f00e81f2999129132f4048f2bd1c301be154ada413c3682
independent_reviewers:
  - reviewer: codex-independent-reviewer-a
    path: docs/9.3.4/quality/step4-coverage-20260720-diagnostic-r42-candidate-independent-review-20260720.md
    sha256: 3170f6604cbb6bb048c50cd6f51c316a4366df57abbc25ff7d9bbeb21937916a
    verdict: pass
  - reviewer: codex-independent-reviewer-b
    path: docs/9.3.4/quality/step4-coverage-20260720-diagnostic-r42-capsule-independent-review-20260720.md
    sha256: e8bd62e4994769d8fe23740d623ee703687ca5101a05241ffa84fee525a87f46
    verdict: pass
reviewed_at: 2026-07-20T17:14:11Z
decision: confirm-observed-thresholds
status: reviewed-cfreeze-authorized
formal_status: pending
user_confirmation: not-asserted
B: 0
H: 0
M: 0
L: 0
---

# r42 threshold freeze review

Both independent reviews passed with no blocker, high, medium, or low
findings. Reviewer A independently recomputed the r42 diagnostic and the
threshold candidate. Reviewer B independently verified and materialized the
versioned Git-safe capsule. They bind the same run, Cdiag, source seal,
candidate, attestation, archive, and manifest listed above.

The reviewed aggregate minima are line `54624/76830` and branch
`26112/44870`; all twelve critical classes are at their exact observed
minimum and the only zero-total metric remains the explicit approved
not-applicable exception. The capsule retains only the hash-safe attestation
and JaCoCo XML members; raw execution bytes, runtime closure, and
unstructured output remain excluded.

This review authorizes exactly one direct, single-parent Cfreeze successor of
the tested Cdiag, containing only the prescribed Step 4/Step 6 formalization
delta and `docs/9.3.4/**`. Formal validation, Step 5 replay, version signoff,
and user acceptance remain pending.
