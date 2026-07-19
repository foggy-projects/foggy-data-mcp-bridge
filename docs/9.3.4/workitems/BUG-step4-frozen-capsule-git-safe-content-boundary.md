---
type: bug
bug_source: diagnostic-r29-git-safety-review
version: 9.3.4
ticket: BUG-934-STEP4-FROZEN-CAPSULE-GIT-SAFE-CONTENT-BOUNDARY
severity: blocker
status: open
reproduction_status: confirmed
test_strategy: git-safe-capsule-positive-and-negative
automation_decision: required
owner: step4-coverage-tooling
---

# Step 4 frozen capsule lacks a Git-safe content boundary

## Background

The completed r29 diagnostic produced valid local observation and candidate
facts, but its local capsule contains raw run log (`run.log`) and raw exec
content. Those content classes are excluded from Git. The capsule therefore
cannot be tracked or used to close the evidence chain for Cfreeze.

The defect is in the capsule publication boundary, not in r29's observed
coverage counters. A tool must make the allowed Git-safe closure explicit and
must reject forbidden raw content before publication. It is insufficient to
leave the local capsule untracked while documenting a candidate derived from
it.

## Expected vs actual

- Expected: a freezeable diagnostic produces only a Git-safe evidence closure,
  with the tool rejecting any forbidden raw run-log or raw-exec content before
  it can become a publication input.
- Actual: r29's local capsule includes those excluded content classes. The
  resulting diagnostic facts remain observable, but its candidate has no
  Git-safe closure and is non-freezable.

## Required fix and tests

1. Implement a tooling boundary that produces the permitted Git-safe capsule
   closure by construction and fail-closes when forbidden raw content is
   present.
2. Add persistent positive and negative coverage proving that allowed closure
   content is accepted and each forbidden content class is rejected.
3. Ensure generated documentation and Cfreeze inputs reference only the
   Git-safe closure; they must not assign a tracked identity to excluded local
   content.
4. Re-run the complete diagnostic authorization chain from a fresh source
   baseline. No r29 candidate, local capsule, local review, or historical r28
   precedent may be used as a substitute.

## Required successor sequence

The exact next path is:

`tool fix → new Cdiag → fresh diagnostic`

The new Cdiag must be clean and pushed. The fresh diagnostic must independently
produce new candidate facts and a separately reviewed Git-safe closure before
any Cfreeze is considered. Historical r28 high-water is informative only; it
does not waive this boundary. Formal-r9 remains failed and excluded.
