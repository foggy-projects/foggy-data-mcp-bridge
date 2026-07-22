---
evidence_type: scoped-threshold-cfreeze-authorization
version: 1
step: 4
run_id: step4-postrun-integrity-state-reset-diagnostic-20260722-r1
reviewer: codex-governed-cfreeze-review
reviewed_at: 2026-07-21T18:18:34Z
decision: reviewed-cfreeze-authorized
authorization: single-direct-child-only
---

# Workflow-state reset diagnostic r1 — scoped threshold review

## Reviewed inputs

- Fresh diagnostic: passed and independently recomputable.
- Threshold candidate: recomputed and verified while remaining
  `review-required`.
- Git-safe capsule: sealed and structurally verified.
- Primary review and independent review: both passed with no blocking
  findings.

## Scoped authorization

The approved workflow-state-reset specification authorizes exactly one
direct-single-parent Cfreeze from the clean Cdiag descendant. That Cfreeze may
only project this reviewed candidate to the existing formal-ready/confirmed
machine state, refresh the governed Step 4 and Step 6 hash closure, and carry
the safe run-owned evidence under `docs/9.3.4/`.

This record does not authorize a formal run until that Cfreeze is committed,
pushed, topology-validated, and clean. It does not authorize Step 5, release,
version acceptance, 9.3.5, or 9.4.0.
