---
doc_type: implementation-checkpoint
version: 9.3.4
ticket: BUG-STEP5-POSTRUN-STEP4-ARTIFACT-VERIFIER-INTEGRITY-REPLAN
stage: cdiag-static
status: passed
recorded_at: 2026-07-21
authority: cdiag-only
---

# Step 5 post-run Step 4 integrity closure — Cdiag static checkpoint

## Scope

- change: package mutable-work exits now converge through one terminal Step 4
  integrity closure; the existing release-artifact verifier is reused through
  a private helper.
- boundary: this checkpoint covers only the declared Step 4/5/6 tooling-hash
  closure and governed documentation. It does not authorize a Step 4 run,
  Step 5 rehearsal, promotion, replay, or later roadmap transition.

## Verified without Maven or Docker

- Python syntax, governed Bash syntax, whitespace checks, and the package
  synthetic negative matrix passed. The matrix contains 113 bounded cases.
- The synthetic coordinator starts at the real mutable-work latch and covers
  clean and faulted E_IMAGE paths, Step 4 artifact/tree/report/source/authority
  failures, cleanup and signal precedence, direct success, receipt publication
  rollback, fixed nine-field sidecar shape, raw-detail suppression, and
  bounded directory-replacement handling.
- The Step 4, Step 5, and Step 6 hash manifests passed their self-checks.
  Step 6 workflow validation and its static negative matrix also passed.
- Static runner control-flow confirms that a nonzero package result validates
  its bounded sidecar and returns before the later candidate-pointer phase.
  That no-pointer property belongs to the outer runner; it is not claimed as
  package-tool synthetic output.
- No Maven build, Docker action, database action, Step 4 diagnostic/formal
  execution, candidate publication, or final-authority publication occurred.

## Residual boundary

- Cleanup uses fd-relative scan/stat/unlink/fsync once an owned directory is
  opened. A replacement observed at the implemented identity checks becomes a
  bounded fail-closed outcome, and a scan-time replacement cannot have its
  foreign contents removed by that scan.
- This is not an adversarial filesystem lifetime lock or an atomic
  inode-bound directory-delete guarantee. In particular, an empty external
  replacement in the narrow final identity-recheck-to-directory-removal window
  may still be removed; `(device, inode)` tracking also has ordinary ABA
  limits. A portable stronger guarantee requires external exclusion, a trusted
  parent protocol, or a platform-specific mechanism, none of which is in this
  frozen work item.

## Next required gate

- Commit and independently review this clean Cdiag. Then create a fresh full
  Step 4 diagnostic and continue only through the approved diagnostic,
  Cfreeze, formal, quality/audit, and owner-reacceptance sequence. A new Step 5
  rehearsal still requires separate owner approval.
