---
evidence_type: formal-contract-invalid-fail-closed
version: 9.3.4
step: 4
run_id: step4-coverage-20260720-formal-r10
tested_commit: f6873f65d6c97114ce219c64c62462096e46085b
status: mechanically-passed-contract-invalid-excluded
decision: requires-new-cdiag
recorded_at: 2026-07-20
---

# Step 4 formal-r10 report-stage public-receipt contract invalidation

## Decision

`step4-coverage-20260720-formal-r10` retains its immutable mechanical run status of
`formal-passed`. Independent post-formal review found that its final public effective-POM receipt was a
regular, non-link file with mode `0600`, while the governed public contract requires exact mode `0644`.
That property is part of the published evidence, not cosmetic metadata.

The governing classification is therefore
`mechanically-passed / contract-invalid / non-authoritative / excluded-from-audit-and-acceptance`.
The immutable run-status is not rewritten, but r10 must not be used as a formal conclusion, coverage-audit
input, feature-acceptance input, or Step 5 authorization.

## Root cause and failed boundary

The original effective-POM publisher correctly made its own public receipt `0644`. A later report-stage
copy ran under the deliberate strict outer `umask 077`; the unqualified copy created a new report-stage
inode as `0600`. The pre-existing report provenance bound content SHA-256 and byte size, but not the
public mode. It therefore allowed a content-correct but contract-invalid final artifact: a false green.

This is a runner/provenance defect only. It does not change production Java, test selection, POM behavior,
coverage floors, critical policy, report cardinality, DB/external matrix policy, or public API.

## Observation-only boundary

The all-lane, source, cleanup, and high-water observations completed by r10 remain historical
observations only. They do not cure the public-receipt defect and cannot be combined with a later run.
The preceding r34 diagnostic, its candidate/reviews, and the Cfreeze tested by r10 likewise cannot be
reused after the repair changes the runner and provenance contract.

No raw log, raw exec material, container identity, or container TSV is promoted into this evidence.
Only the typed contract fact above is retained.

## Required replacement chain

The replacement is deliberately a new authority chain, not a rerun or amendment of r10:

1. Commit and push a clean Cdiag containing the report-stage mode repair, provenance mode binding,
   strict-umask regression, hash closure, and this boundary record.
2. Run a fresh all-lane diagnostic with a new ID, allocated as
   `step4-coverage-20260720-diagnostic-r35` after the clean/push gate.
3. Create fresh candidate, Git-safe closure, capsule, and independent reviews from r35 only.
4. Create a direct-child Cfreeze and run a fresh formal successor.
5. Complete replacement final implementation quality, the scoped `legacy 31 + supplemental 4` coverage
   audit with critical/major gaps `0/0`, then Step 4 feature acceptance.

Until every replacement step completes in order, `can_enter_step5=no`,
`can_enter_coverage_audit=no`, and `can_enter_acceptance=no`; 9.3.5 and 9.4.0 remain closed.
