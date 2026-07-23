---
workitem_type: BUG
version: 9.3.4
step: 4
severity: blocker
status: fixed-awaiting-replacement-authority
owner: Step 4 coverage authority
discovered_by: step4-coverage-20260719-formal-r6
---

# Step 4 fsmonitor-valid negative fixture nondeterminism

## Problem

The synthetic fsmonitor-valid source-identity probe used a newline-terminated response for a Git
fsmonitor v2 hook. Git v2 requires a NUL-terminated token and NUL-separated paths. The malformed
response made the first `ls-files -f` observation timing-dependent even though the valid bit remained
persisted, so a correct fresh formal run could fail before testing production code.

This is an authority-fixture blocker because a mandatory negative gate must be deterministic. It is not
a reason to weaken the source-identity policy or reuse the failed run.

## Reproduction

- formal-r6 failed at `bootstrap-negative` with `fsmonitor-valid fixture flag differs`;
- same synthetic index could read `H` once and `h` immediately afterward while index bytes and
  `flags=0x200000` remained unchanged;
- independent old-hook stress observed `996/1000 h` and `4/1000 H`;
- main and fresh-clone real indexes both had ordinary `4066 × H` entries.

## Fix

`coverage_contract_negative_tool.py` now emits a protocol-v2 response with fixed
`token\0` and no changed paths. The probe still must prove:

1. the fixture itself reads exact `h tracked.txt\0`;
2. the production source-hash validator rejects it with rc=2 and
   `fsmonitor index flags must be ordinary H`;
3. repository-local fsmonitor/untracked-cache configuration and hooks remain disabled.

No production path, threshold, critical set, report inventory, test lane or source-hash acceptance rule
changed.

## Verification and exit

- compliant-hook focused stress=`1000/1000` in independent reproduction;
- local focused stress=`100 iterations / 200 cases / passed`;
- five independent full negative-tool processes=`5/5 passed`；each contains contract mutation=
  `27/27`、source/Git identity=`22/22`、threshold/frozen replay=`12/12`；
- Step 4 manifest=`61/61`、Step 6 manifest=`16/16`、full diagnostic contract、successor overlay and
  CI workflow validator all PASS；
- machine state=`diagnostic-ready / diagnostic-pending`。

The implementation defect is fixed, but this workitem remains `fixed-awaiting-replacement-authority`
until a clean/pushed Cdiag, fresh diagnostic, new Cfreeze and fresh formal all pass. formal-r6 remains
immutable failed evidence.
