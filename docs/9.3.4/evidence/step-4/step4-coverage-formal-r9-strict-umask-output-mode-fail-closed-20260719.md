---
evidence_type: formal-failure-fail-closed
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-formal-r9
tested_commit: 34cd2452c1bbe793c0567ebe23179b290227ae3d
status: failed
decision: requires-new-diagnostic
failed_phase: coverage-report
recorded_at: 2026-07-19
---

# Step 4 formal-r9 strict-umask public-output failure

## Decision

"step4-coverage-20260719-formal-r9" ran against the genuinely fresh, non-shallow, clean Cfreeze
"34cd2452…". The outer formal wrapper deliberately used "umask 077". In "coverage-report", the
effective-POM receipt publisher observed its public output as "0600", rejected it with "E_OUTPUT", and
the runner ended "failed / exit_code=2".

This is a correct fail-closed result, not a test or coverage-floor regression. The run is permanently
"failed / excluded / non-reusable / non-candidate": it must not be resumed, combined with another run,
or supplied with retroactive success artifacts. It has no Step 4 coverage-gate, candidate, final, audit,
or release authority.

The repair is outside the tested Cfreeze formalization delta. The only legal replacement path is a new
clean, pushed Cdiag successor followed by fresh all-lane "diagnostic-r29"; that diagnostic must create a
new candidate, capsule, reviews, direct-child Cfreeze, and replacement fresh formal run.

## Completed boundary

- Formalization delta passed on the tested commit: direct single parent, "11" changed paths, and formal
  workflow state; source-before was "f9aabf18…3bc1".
- Unit passed: "681 execution + 55 structural / 4941 / F0 E0 S0"; Integration passed:
  "47 + 4 / 320 / F0 E0 S0" across six variants.
- Database matrix passed: five cells, "29 / 370 / F0 E0 S0"; external matrix passed:
  seven variants, "16 / 76 / F0 E0 S0"; Addon companion passed: "2 / 6 / F0 E0 S0".
- Step 3 required passed: "45 / 446 / F0 E0 S0"; report inventory passed:
  "773 execution + 59 structural / 5707 / F0 E0 S0", with Addon held outside the required union.
- The run-owned exec manifest was verified for "23" files and "48" sessions. This is only input
  provenance: aggregate exec/XML/report provenance and any coverage result remain absent.

"source_after", summary, aggregate exec/provenance, aggregate XML/HTML, coverage observation/gate,
candidate manifest, and final manifest are absent. The completed child lanes and inventory therefore do
not become a formal coverage conclusion.

## Failure and root cause

The normalized failure record is "E_OUTPUT: unexpected output mode: 0600"; it is intentionally a typed
summary, not a copied raw-log excerpt. The publisher supplied "0o644" when creating the temporary
receipt, but POSIX applies the process umask to that creation mode. Under the formal wrapper's "077", the
temporary inode became "0600"; hard-link publication retained that mode; the publisher's final public
receipt assertion ("0644") then rejected it and removed the failed publication.

The defect is confined to public-output portability in the effective-POM receipt publisher. It neither
lowers a threshold nor changes production code, Java test selection, report cardinality, class universe,
or a database/external lane.

## Cleanup and privacy boundary

- The wrapper recorded runner "rc=2", restore "rc=0", receipt inspection "rc=0", and its own failure
  outcome "rc=2"; all four pre-existing demo databases were restored to their prior healthy running state.
- Runner cleanup reported zero created container, volume, and network residue. No container identity,
  name, image, endpoint, or TSV is copied into this evidence.
- The source raw log and every excerpt are intentionally excluded. Success-stage sensitive receipts are
  absent, as required by the failed boundary.

## Minimal safe capsule

[formal-r9-strict-umask-failure-capsule/](formal-r9-strict-umask-failure-capsule/) contains one
identity-free normalized summary and a manifest. It contains no raw run log, container TSV, outer restore
document, or identity-bearing material. The summary binds only aggregate results and source artifact
hashes/sizes; all listed source artifacts remain outside the capsule.

| Source artifact role | SHA-256 |
|---|---|
| run status | "797bd871…3e1e" |
| formalization delta | "7893f8a9…52f1" |
| exec manifest | "e1d36317…0c5a" |
| report inventory | "7603bcaa…1171" |
| outer wrapper status | "728c197b…b5b2" |

## Recovery boundary

The remediation must preserve atomic no-overwrite and real-parent checks while making the public receipt
mode independent of the caller's umask: keep the staging inode private while writing, explicitly set it
to "0644" before link publication, fsync it, then assert the published regular file is exactly "0644".
A strict-"umask 077" probe must prove the receipt has the expected bytes and "0644" mode, with no
temporary residue or symlink acceptance.

The recovery work item is
[BUG-step4-effective-pom-public-output-mode-portability.md](../../workitems/BUG-step4-effective-pom-public-output-mode-portability.md).
Until its new Cdiag successor and fresh "diagnostic-r29" pass all gates, Step 5, coverage audit, Step 4
acceptance, and 9.3.4 signoff remain closed.
