---
doc_type: release-authority-evidence
version: 9.3.4
step: 4
status: FAILED_EXCLUDED
governance_date: 2026-07-23
run_id: step4-v934-fsscript-lifecycle-dual-seal-formal-recovery-20260723-r9
git_head: 5c57e537603004d47be936f74e92f873de5fe431
activation_sha: ccde9e6cccf8c410d1c06f0998ee4ce7cb295e2c
---

# Step 4 formal recovery r9 external interruption fail-closed record

## Verdict

The one fresh formal invocation authorized by the recovery Cplan did not reach the outer runner's
terminal verdict. Unit and Integration completed, the Step3 successor overlay passed, and the Addon
companion completed `2/6` with zero failures, errors or skips. The terminal-managed execution was
then externally interrupted while the Step3 required orchestrator was entering
`child-database-matrix`.

The Step3 orchestrator wrote a terminal failed receipt with `exit_code=1`,
`last_phase=child-database-matrix`, no `source_after_sha256` and no final report manifest. The outer
Step4 runner emitted no source-after, summary, run-status, final coverage, cleanup, candidate or
capsule receipt. This run is therefore **FAILED / EXCLUDED**, is not formal authority, and cannot be
combined with r8 or any completed child output.

The recovery contract identifies this invocation as the second expensive formal attempt and
explicitly forbids an automatic third attempt. The recovery work item is therefore
`NEEDS_REPLAN`; Step5, Step6 and Step7 remain blocked.

## Governed identity and preflight

- Recovery Cplan: `61328fc709d804b3231fd0872c250bb82305758d`.
- Clean/pushed activation: `ccde9e6cccf8c410d1c06f0998ee4ce7cb295e2c`.
- Immutable Cfreeze / execution HEAD: `5c57e537603004d47be936f74e92f873de5fe431`.
- Direct parent Cdiag: `462d64acf0865f44582b2b1245a9b4c771aad4cd`.
- Run ID: `step4-v934-fsscript-lifecycle-dual-seal-formal-recovery-20260723-r9`.
- Run started at `2026-07-23T01:32:32Z` in a fresh, clean, non-shallow clone detached at exact
  Cfreeze.
- Run-context source SHA-256:
  `21b9bddf5f5a120b8058ddaab5a9c0088ae2f72a9e67668d17a095bb391ca01a`.
- Coverage contract SHA-256:
  `5107aad524da7f309acc939c2e1698dfe10e88822749806fdbf441c8ae9412be`.
- Formal delta validation passed with the required direct-single-parent topology and six exact
  formalization paths. Source, contract, threshold, Step4 manifest and Step6 seal digests matched
  the frozen record before execution.
- The r8 runtime root was not used as execution input. This invocation used a new clone, run root
  and run ID and started all lanes from zero.

## Completed but excluded child observations

These facts explain how far the invocation progressed. They do not constitute a reusable formal
result.

| Child / lane | Observation | Terminal child receipt |
|---|---|---|
| Unit | `682` positive, `55` structural, `4943` testcases; zero failures/errors/skips | PASS, leader reaped, process-group residue `0` |
| Integration | `47` positive, `4` structural, `320` testcases; zero failures/errors/skips | PASS, leader reaped, process-group residue `0` |
| Step3 successor overlay | `45` reports / `446` testcases; zero failures/errors/skips | overlay validation PASS |
| Addon companion | `2` reports / `6` testcases; zero failures/errors/skips | PASS, resource residue `0/0/0` |

Relevant receipt SHA-256 values:

- outer partial `run.log`:
  `f40ea6d79fe97c5dff6dec11f61c67b732dd60353d63daee18ff517facbd6c05`;
- Unit complete:
  `4f1ee8769dbd85a2ea8ef957328f842b38817c1898f0b17b82bc3c85dd27ef1b`;
- Integration complete:
  `2b060514b222b78194863afd9cf177676ce3d0186f5619e8f3394a271994bdb1`;
- Addon summary:
  `75a54dc38ed66013d894e286b96499b464d002ac0fcf4faac9816e53b5b77b5e`;
- Addon run-status:
  `1842153b5f6a540655b093e217c41deebc779f9309c95cdca8cd32de29ca12bf`;
- Step3 failed run-status:
  `a068c4c5d346f5d314c3558f3f9d1bbffaef68d9e8dbcfdf49d53e164a8c1e7a`;
- Step3 cleanup:
  `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1`.

The Step3 orchestrator did not produce `summary.env`, `source-after.tsv` or its final report
manifest. It recorded `status=failed`, `exit_code=1` and `last_phase=child-database-matrix` at
`2026-07-23T01:58:09Z`. No database-matrix completion can be claimed.

## Missing outer authority and residue inspection

The Step4 run root has no:

- `source-after.tsv`;
- outer `summary.env` or terminal `run-status`;
- final aggregate coverage and critical-floor verdict;
- model or sensitive-pattern terminal verdict;
- outer cleanup finalizer receipt;
- formal threshold candidate or portable capsule.

Post-interruption inspection found no process matching this run ID or the Step4/Step3 runner, no
run-labeled container, volume or network, and port `13306` was free. Unit and Addon child cleanup
receipts reported zero owned residue, and the Step3 failure trap reported container/volume/network
residue `0/0/0`. These observations do not replace the missing outer cleanup finalizer.

## Scope and disposition

- No product, test, POM, coverage floor, exclusion, selector, fork, skip, test order, API or SPI byte
  was changed by the invocation.
- The protected original workspace remains at
  `9743f97d9d935d5e26311b78c158755bca51f17a`; its `docs/9.3.5` user changes remain untouched and
  are not included in this record.
- No product failure verdict is made: all completed child reports had zero failures/errors/skips,
  but the formal authority chain is incomplete.
- r8 and r9 runtime bytes are permanently excluded from future authority and must not be spliced,
  resumed or promoted.
- `can_enter_step5=no / can_enter_step6=no / can_enter_step7=no`.
- Any further formal attempt requires a new explicitly approved successor plan, a new activation,
  a new run ID and a fresh execution strategy that addresses terminal-session survivability. No
  automatic retry is authorized.
