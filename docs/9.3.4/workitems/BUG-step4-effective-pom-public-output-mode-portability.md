---
workitem_type: BUG
version: 9.3.4
step: 4
severity: blocker
status: reopened-awaiting-replacement-authority
owner: Step 4 coverage authority
discovered_by: step4-coverage-20260719-formal-r9
---

# Step 4 effective-POM receipt depended on caller umask

## Problem

formal-r9 executed the report path under the deliberate outer "umask 077" policy. The effective-POM
receipt publisher requested "0644" in "os.open", but that is a requested creation mode rather than a
post-umask guarantee. The temporary inode consequently became "0600"; publication preserved that mode;
the public-receipt assertion correctly rejected it as "E_OUTPUT".

The fault is a portability and authority defect: release evidence must not depend on an ambient umask.
It is not valid to weaken the final "0644" assertion, run formal under a permissive umask, chmod a
completed run, or resume formal-r9.

The sealed failure record is
[step4-coverage-formal-r9-strict-umask-output-mode-fail-closed-20260719.md](../evidence/step-4/step4-coverage-formal-r9-strict-umask-output-mode-fail-closed-20260719.md).

## Required remediation

The effective-POM receipt publisher must retain its no-overwrite, no-symlink, real-parent, link-based
publication, rollback, and fsync guarantees while making the final public inode mode explicit:

1. Create and write the private staging inode under exclusive creation.
2. Before hard-link publication, explicitly apply "0644" to the same inode and fsync it.
3. Publish without replacing an existing path, fsync the parent directory, and require a regular final
   receipt with exact mode "0644".
4. On any failure, remove staging and any newly linked output; never retain a partially published
   receipt.

The strict-umask contract must execute the publisher with "umask 077" and verify all of the following:

- byte-exact decoded JSON payload;
- regular, non-symlink result at "0644";
- no leftover temporary output;
- unchanged refusal of overwrite and unsafe-parent cases.

The probe must be bound into the existing Step 4 negative/portability authority checks, so a future
removal of the explicit post-create mode correction cannot silently pass ordinary-worktree tests.

## Scope and non-goals

In scope are the effective-POM publisher and its governed portability check, plus the required Step 4 /
Step 6 hash closure and machine-state reset in the replacement Cdiag. Out of scope are production Java,
Java test selection, POM behavior, coverage floors, class universe, report cardinalities, DB/external
matrix policy, and existing Cfreeze artifacts.

The Cfreeze tested by formal-r9 is immutable historical evidence. Because this repair is outside its
formalization delta, it cannot be patched into that run or represented as a Cfreeze-only change.

## Exit criteria

- [x] formal-r9 is sealed "failed / excluded / non-reusable / non-candidate" with an identity-free
  minimal capsule;
- [x] an implementation baseline includes explicit public-mode correction and a strict-"umask 077"
  portability probe;
- [ ] create a new clean, pushed Cdiag successor that includes the repair, documentation, machine reset,
  and hash closure;
- [ ] run fresh all-lane "diagnostic-r29", create a new candidate/capsule, and complete independent
  reviews;
- [ ] create a direct-child Cfreeze and run a replacement fresh formal authority gate;
- [ ] complete post-formal quality, replacement coverage audit, Step 4 acceptance, and 9.3.4 signoff.

The checked-in recovery baseline is not diagnostic authority. Until the new clean, pushed Cdiag successor
and fresh "diagnostic-r29" complete, "can_enter_step5=no", "can_enter_coverage_audit=no", and
"can_enter_acceptance=no".

## formal-r10 report-stage false-green supersession（2026-07-20）

The publisher repair above was necessary but insufficient. formal-r10 mechanically completed after the
original publisher produced `0644`; a later report-stage copy under strict `umask 077` recreated the
public receipt as `0600`. Because final report provenance then bound only content SHA-256 and size, the
wrong mode was not detected in the mechanical formal path.

formal-r10 is consequently `mechanically-passed / contract-invalid / non-authoritative /
excluded-from-audit-and-acceptance`. Its run-status remains immutable and must not be rewritten, but it
cannot be used for candidate, Cfreeze, formal, audit, or feature-acceptance authority. The separate repair
is tracked by
[BUG-step4-report-stage-public-receipt-mode-binding.md](BUG-step4-report-stage-public-receipt-mode-binding.md).

The active recovery sequence supersedes the old r29 wording: clean/pushed replacement Cdiag → fresh
diagnostic-r35 → new candidate/Git-safe closure/capsule/dual review → direct-child Cfreeze → fresh formal
→ replacement final quality → `legacy 31 + supplemental 4` audit → Step 4 feature acceptance. No r34,
r10, or prior Cfreeze material may be reused.
