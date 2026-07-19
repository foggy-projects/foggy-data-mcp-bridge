---
workitem_type: BUG
version: 9.3.4
step: 4
severity: blocker
status: fixed-verified-awaiting-fresh-formal
owner: Step 4 coverage tooling
discovered_by: step4-coverage-20260720-formal-r10-postformal-review
---

# Step 4 report-stage public receipt mode was not bound into provenance

## Problem

The original effective-POM publisher correctly produced a public `0644` receipt. The later report-stage
copy was a separate publication boundary. Under the required outer `umask 077`, it created a new final
report receipt with mode `0600`. The prior report provenance bound only SHA-256 and size, so it accepted
the wrong mode and allowed formal-r10 to appear mechanically successful.

This is distinct from the earlier publisher portability defect. The missing control is report-stage mode
enforcement plus exact-mode provenance binding.

## Implemented repair

- the coverage report runner explicitly applies and verifies `0644` after the report-stage copy and again
  immediately before final publication;
- report provenance records the receipt mode and its verifier rejects any regular receipt whose mode is not
  exact `0644`;
- XML/provenance negatives include a `0600` receipt rejection;
- the contract-negative suite mutates both runner enforcement call sites, the explicit normalization, and
  the exact-mode assertion, and runs the real helper against a report-stage copy under `umask 077`.

## Scope and non-goals

The work is limited to the Step 4 report runner, provenance consumers, their negative checks, associated
hash closure, machine reset, and documentation. It does not alter production Java, Java tests, POMs,
coverage thresholds, test/report cardinality, database behavior, or public API.

## Exit criteria

- [x] report-stage `0644` enforcement and exact-mode provenance binding implemented;
- [x] strict-umask report-stage-copy probe and mutation negatives implemented;
- [x] new clean, pushed Cdiag static closure completed;
- [x] fresh all-lane diagnostic-r35 plus candidate/capsule/reviews completed;
- [ ] direct-child Cfreeze and wholly fresh formal completed;
- [ ] replacement final quality, `legacy 31 + supplemental 4` audit, and Step 4 acceptance completed.

formal-r10 remains immutable and excluded; this work item does not authorize a retroactive repair,
rerun, audit, or acceptance of r10.

Fresh r35 proved the report-stage public receipt as regular/non-link exact `0644` under strict umask, and its
independent candidate/capsule reviews are `APPROVE / B/H/M/L=0/0/0/0`. This validates the repair but is not
a replacement formal authority; Cfreeze and formal must remain new-run evidence.
