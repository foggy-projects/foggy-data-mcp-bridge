---
evidence_type: independent-threshold-candidate-review
version: 1
step: 4
run_id: step4-coverage-20260720-diagnostic-r42
tested_commit: dd2ccde8e97c4dfe88e9a06141280a1d747ac737
source_sha256: dc87cb0e44080fa7af7113674275ef36d449dca1a8b27ffc2a402a0387bf0d0c
candidate_path: docs/9.3.4/evidence/step-4/step4-coverage-20260720-diagnostic-r42-threshold-candidate-20260720.json
candidate_sha256: 0f0f4b45b7e932139d985c62657dd137d075748b17eb3a4bdb5b41216e00717e
reviewer: codex-independent-reviewer-a
reviewed_at: 2026-07-20T17:14:11Z
verdict: pass
B: 0
H: 0
M: 0
L: 0
---

# r42 threshold candidate independent review

Reviewer A independently recomputed the sealed diagnostic with the public
validator and recomputed the threshold candidate. Both checks passed and bind
the same r42 run, tested commit, and source seal.

The reviewed aggregate minima are exact observed counters: line
`54624/76830` and branch `26112/44870`. The candidate contains the frozen
twelve critical identities, with exact observed minima and only the approved
`NamespaceScope.branch` zero-total not-applicable exception. It remains
`review-required`; this review does not claim formal validation or version
signoff.
