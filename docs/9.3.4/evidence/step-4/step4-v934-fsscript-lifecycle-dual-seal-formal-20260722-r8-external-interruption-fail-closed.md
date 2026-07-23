---
doc_type: release-authority-evidence
version: 9.3.4
step: 4
status: FAILED_EXCLUDED
governance_date: 2026-07-22
run_id: step4-v934-fsscript-lifecycle-dual-seal-formal-20260722-r8
git_head: 5c57e537603004d47be936f74e92f873de5fe431
---

# Step 4 formal r8 external interruption fail-closed record

## Verdict

The sole governed formal invocation did not reach a terminal runner verdict. It was externally
terminated after the `sqlite-refresh` integration variant had produced its local zero-failure
report, but before the integration child, outer runner, coverage gate, cleanup finalizer, candidate
or capsule emitted their required terminal receipts.

This run is therefore **FAILED / EXCLUDED**. It is not formal authority, cannot be spliced with the
successful diagnostic, and must not be retried or reused under the current Cplan.

The governance date remains `2026-07-22`, matching the run context start at
`2026-07-22T17:53:06Z`. Local Asia/Shanghai wall-clock output crossed midnight and ended during
`2026-07-23`; that date transition is an observed runtime fact, not a change to the frozen run ID or
governance date.

## Frozen input and preflight

- Cfreeze / formal HEAD: `5c57e537603004d47be936f74e92f873de5fe431`.
- Direct parent Cdiag: `462d64acf0865f44582b2b1245a9b4c771aad4cd`.
- Run ID: `step4-v934-fsscript-lifecycle-dual-seal-formal-20260722-r8`.
- Run context source SHA-256:
  `21b9bddf5f5a120b8058ddaab5a9c0088ae2f72a9e67668d17a095bb391ca01a`.
- Coverage contract SHA-256:
  `5107aad524da7f309acc939c2e1698dfe10e88822749806fdbf441c8ae9412be`.
- The fresh repository was clean, non-shallow and detached at exact Cfreeze. Formal delta validation
  passed with the required direct-single-parent relationship and the six exact formalization paths.
- Runner seal bindings, lifecycle positive/negative suites, toolchain seals, Step2 derived view,
  report inventory and pre-run sensitive-pattern probes passed before Maven authority execution.

## Completed child evidence

The Unit child reached its canonical terminal receipt:

- status: `passed`;
- leader exit code: `0` and leader reaped;
- process-group residue: `0`;
- Surefire inventory: `682` positive executions, `55` structural reports, `4943` testcases;
- MySQL 5.7 fixture lifecycle: negative probes passed, residue `0/0/0`, port released.

Integration produced canonical `PASS collect` markers for these variants before interruption:

| Variant | Positive executions | Structural reports | Testcases |
|---|---:|---:|---:|
| caffeine-sqlite | 1 | 0 | 2 |
| hermetic | 1 | 0 | 3 |
| sqlite-broad | 42 | 4 | 308 |
| sqlite-harness | 1 | 0 | 1 |
| sqlite-lifecycle | 1 | 0 | 4 |

`sqlite-refresh` generated a Failsafe XML with `tests=2`, `failures=0`, `errors=0`, `skipped=0`
and a local report manifest marked passed. The outer run log did not emit its canonical
`PASS collect` marker, and no integration-complete receipt exists. These bytes are retained only as
interruption diagnostics and are not accepted as a completed variant or lane.

## Missing terminal authority

Post-interruption inspection found no live process matching the run ID, Maven, Surefire, Failsafe or
the Step4 formal runner. The run root contains no:

- integration-complete receipt;
- outer `summary.env` or `run-status` terminal receipt;
- source-after identity;
- final coverage/model/sensitive/cleanup verdict;
- formal threshold candidate or portable capsule.

No run-matching process remained at inspection time. The Unit-owned MySQL fixture had already
verified its own cleanup. This observation does not substitute for the missing outer cleanup
finalizer and is not promoted to a successful cleanup verdict.

## Scope and disposition

- No product, test, POM, coverage floor, exclusion, selector, fork, skip, test-order, API or SPI
  bytes changed during the interrupted formal invocation.
- The protected v9.3.5 workspace remains outside this clone and outside this record.
- The successful diagnostic and Cfreeze remain valid historical evidence, but they do not replace a
  complete fresh formal run.
- The r8 run ID and all partial lane/report bytes are permanently excluded from future authority.
- The canonical dual-seal successor Cplan is set to `NEEDS_REPLAN`. A future formal attempt requires
  a new approved successor contract, a new activation identity and a new run ID; no automatic retry
  is authorized by this record.
