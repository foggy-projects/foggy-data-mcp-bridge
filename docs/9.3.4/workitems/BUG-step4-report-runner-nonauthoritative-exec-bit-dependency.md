---
workitem_type: BUG
version: 9.3.4
step: 4
severity: blocker
status: fixed-awaiting-replacement-authority
owner: Step 4 coverage authority
discovered_by: step4-coverage-20260719-formal-r8
---

# Step 4 report runner depended on non-authoritative executable bits

## Problem

`coverage_report_runner.sh` directly executed two Python tools whose tracked Git mode is `100644`.
The long-lived main worktree uses `core.fileMode=false` and happened to expose executable worktree bits,
so diagnostic-r26 passed. A canonical fresh clone materialized the same files as non-executable and
formal-r8 stopped with rc=`126` after all test/report lanes had passed.

This is an authority portability blocker. Worktree permission drift that Git intentionally ignores cannot
be a release precondition, and a failed formal run cannot be repaired or resumed in place.

## Reproduction

- tested Cfreeze=`7c18019ed12d25c029de7e7e49caef77a79b2e67`；
- run=`step4-coverage-20260719-formal-r8`；phase=`coverage-report`；
- fresh-clone modes：`coverage_exec_tool.py=0664`、`coverage_tool.py=0664`；Git mode both=
  `100644`；
- direct `--help`=`126/126`；`python3 … --help`=`0/0`；
- direct command positions：report runner lines `275`、`286`、`589`；
- formal-r8 first failure：line `275` `coverage_exec_tool.py: Permission denied`。

## Fix

All three calls now use an explicit interpreter:

1. `python3 "$EXEC_TOOL" verify`；
2. `python3 "$CONTRACT_TOOL" validate-contract`；
3. `python3 "$EXEC_TOOL" verify-aggregate`。

The Python files intentionally remain Git `100644`; no chmod-only workaround is accepted. The Step 4
negative contract now covers all four Git `100644` Python tools and all seven interpreter dispatches:

- seals the exact runner raw bytes and the full 292-command logical executable stream；
- binds four exact target assignments and seven top-level interpreter calls；
- rejects raw/stream/semantic mutations (`44/44 / 43/43 / 33/33`); semantic probes explicitly disable
  both source seals, while 11 dynamic/heredoc probes include an inline-Python direct call that changes
  raw bytes without changing the shell command stream；
- reads authoritative Git modes and rejects `100755/120000/untracked/missing` (`4/4`)；
- copies all four tools as `0644`, recomputes the file mode, and proves direct execution is denied while
  `python3 --help` succeeds (`4/4`)；
- re-hashes the report runner before/after the probes。

The Step 4 manifest, diagnostic machine state and Step4→Step6 hash closure are updated. No production
source, Java test, POM, test selector, report cardinality, threshold floor or critical class changes.

## Verification and exit

- [x] formal-r8 capsule sealed and marked `failed / excluded / non-reusable / non-candidate`；
- [x] all three direct calls replaced, not only the observed first failure；
- [x] Python target/dispatch binding=`4/4 + 7/7`、raw/stream/semantic mutation=
  `44/44 / 43/43 / 33/33`、Git-mode mutation=`4/4`、non-executable smoke=`4/4`；
- [x] Step 4 manifest=`61/61`、Step 6 manifest=`16/16`；
- [x] machine state=`diagnostic-ready / diagnostic-pending`；
- [ ] new clean/pushed Cdiag；
- [ ] fresh diagnostic-r27、candidate/capsule/two reviews；
- [ ] direct-child Cfreeze and fresh formal-r9；
- [ ] post-formal quality、replacement coverage audit `31/31` and feature acceptance。

The implementation defect is fixed locally, but the workitem remains
`fixed-awaiting-replacement-authority` until the replacement formal and post-formal gates pass.
