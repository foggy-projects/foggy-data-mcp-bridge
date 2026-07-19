---
review_type: implementation-quality
version: 9.3.4
step: 4
scope: formal-r9 strict-umask effective-POM receipt publication recovery
status: passed
decision: ready-for-new-Cdiag
reviewed_at: 2026-07-19
---

# Step 4 formal-r9 effective-POM output-mode recovery implementation quality

## Decision

formal-r9 is immutable `failed / excluded / non-reusable / non-candidate`:
it stopped in `coverage-report` with `E_OUTPUT: unexpected output mode: 0600`.
The recovery keeps the public-receipt contract at exact `0644`; it does not
relax the assertion or make the outer authority environment less restrictive.

The implementation-quality decision is `PASS / B/H/M/L=0/0/0/0`, mandatory
findings=`0`, and authorizes only a new clean/pushed Cdiag followed by one
fresh all-lane diagnostic-r29. It does not authorize a candidate, Cfreeze,
formal success, Step 5, coverage audit, feature acceptance, 9.3.5, or 9.4.0.

## Review basis

- r9 tested Cfreeze=`34cd2452c1bbe793c0567ebe23179b290227ae3d`; its outer wrapper
  used `umask 077`, while the effective-POM publication path required a final
  `0644` receipt. The failure boundary is preserved under
  `docs/9.3.4/evidence/step-4/`; no raw log, raw exec, container identity, or
  partial report is promoted as authority.
- `reporter_effective_pom_tool.py` now creates its staging inode as `0600`,
  writes and fsyncs it, then uses the same descriptor to set `0644` and fsync
  before no-replace link publication. Thus a restrictive caller umask cannot
  alter the public artifact contract, and content is not made public while it
  is still being written.
- `coverage_contract_negative_tool.py` directly imports the real publisher,
  executes it under `umask 077`, and rejects any non-regular, symlinked,
  non-`0644`, or content-different receipt. This is a persistent no-container
  regression gate, not a one-off wrapper workaround.
- Because both changed tools are outside the prior formalization allowlist,
  the machine state was reset exactly to `diagnostic-ready / diagnostic-pending`.
  Threshold bytes are the immutable pending predecessor
  `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96`;
  r28/r9 observations, exec, XML, candidate, and final artifacts remain
  unavailable as successor input.

## Verification summary

| Check | Result |
|---|---|
| strict-umask direct publisher probe | `PASS`, public receipt mode=`0644` |
| strict-umask effective-POM run | input POM=`0600`; receipt + negative receipt=`0644`; 4 semantic negative cases passed |
| persistent contract-negative probe | passed under `umask 077` using the actual publisher |
| Step 4 manifest | `77c9ad05f8c39624e55a57057f4661fa598c2aa0403852ce6c33cd1c5ed89ff8`, `61/61` |
| Step 6 manifest | `effa63a2f5281517bf6896502b8323b5950f8aec3dab22ec219a8183f09d0e1a`, `16/16` |
| contract / overlay / CI workflow | all passed in diagnostic workflow state |
| changed surface | two Step 4 tools, pending-state reset, Step 4→Step 6 hash cascade; no production/test/POM/API/cardinality/floor/critical change |

## Authorization boundary

The next authority source must be the clean/pushed Cdiag successor that
contains this recovery record. It must run a fresh `diagnostic-r29` under the
same strict outer `umask 077` condition, then independently establish new
observation, high-water comparison, candidate, capsule, dual review, and one
direct-child Cfreeze before a new formal run is permitted.

No partial r9 evidence may be resumed, copied into a success artifact, or
combined with r29. Any output-mode, source, high-water, negative, cleanup, or
outer-restore failure remains fail-closed.
