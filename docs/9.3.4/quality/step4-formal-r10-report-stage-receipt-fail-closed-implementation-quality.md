---
review_type: implementation-quality
version: 9.3.4
step: 4
scope: formal-r10 final report-stage public effective-POM receipt
status: rejected
decision: rejected-requires-new-cdiag
reviewed_at: 2026-07-20
---

# Step 4 formal-r10 report-stage receipt implementation-quality review

## Decision

The review of formal-r10 is `REJECTED / B/H/M/L=1/0/0/0`, mandatory findings=`1`.
The blocker is the final public effective-POM receipt at mode `0600` instead of exact `0644`. The
mechanical formal pass does not override that contract. r10 is excluded from audit, acceptance, Step 5,
9.3.5, and 9.4.0 authority.

## Mandatory finding

| Severity | Finding | Required disposition |
|---|---|---|
| Blocker | Report-stage copy can create the final public receipt under strict `umask 077` as `0600`, while provenance verifies only bytes and size. | Do not reuse r10. Bind exact `0644` mode at the runner and provenance layers, prove it under strict umask, then rebuild authority from a new Cdiag. |

## Repair boundary

The repair must preserve private staging and require a regular, non-link, non-empty public receipt. The
runner must explicitly normalize and assert `0644` after report-stage copying and immediately before
publication. Report provenance must carry and independently verify the same exact mode. A real
strict-umask report-stage-copy probe and mutations of both enforcement call sites, the normalization, and
the mode assertion must fail closed.

The report-directory rename does not change an inode mode; the two runner checks therefore cover both the
copy boundary and the pre-publication boundary. The next full diagnostic remains the end-to-end proof.

## Authorization boundary

This review records the r10 rejection; it is not a replacement formal-quality pass. A new Cdiag is
eligible only after its static contract, mutation, XML/provenance, overlay, and Step 4-to-Step 6 hash
closures pass. It must then produce a fresh all-lane diagnostic, candidate/reviews, direct-child Cfreeze,
and fresh formal before final quality can be re-opened.

No raw logs, raw exec artifacts, or container details are incorporated into this review.
