---
evidence_type: formal-failure-fail-closed
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-formal-r5
tested_commit: bf860be778dcb86f40c7cba7718a0e75e58d6a36
status: failed
decision: requires-new-diagnostic
failed_phase: child-unit
recorded_at: 2026-07-19
---

# Step 4 formal r5 fresh-clone portability failure

## Decision

`step4-coverage-20260719-formal-r5` 在真正全新、非 shallow、clean clone 上按预期
fail-closed。Cfreeze topology、frozen r21 replay、formal contract、toolchain、negative probes 和
production class universe 均先通过；Unit lane 随后因两个测试要求仓库外兄弟目录
`foggy-data-mcp-bridge-python` 而失败。该 run 永久记录为 failed，不得补写、拼接或提升为
candidate/final。

修复必须删除 Unit snapshot producer 对相邻仓库的直接写入，使每个 Java producer 只发布到本
module 的 `target/parity/**`。由于修复路径不属于 formalization allowlist，必须形成新的 Cdiag、
重跑完整 diagnostic、重新 review/freeze threshold，再启动新的 fresh formal；不得在 r5 上续跑。

## Run identity

- fresh clone `HEAD=bf860be778dcb86f40c7cba7718a0e75e58d6a36`；唯一 parent=
  `5121a9c7fe35120c7864de8554e99188f5d1dc87`；remote branch 与 HEAD exact；
- repository=`non-shallow / replace=0 / grafts empty / clean`；
- run window=`2026-07-18T16:57:45Z` 至 `2026-07-18T17:08:52Z`；
- `last_phase=child-unit / exit_code=1 / status=failed`；
- source-before=`5bc928be11cef2f98d88d5e1632d30e8ef6ce9021c941ffb781ee6f9868cb004`；
  source-after absent，符合 early-failure 状态；
- formalization delta=`direct-single-parent / 12 allowed paths / passed`；
- production class universe=`24 modules / 2,098 classes / passed`；
- Unit terminal summary=`511 tests / 0 failures / 2 errors / 0 skipped` in
  `foggy-dataset-mcp`；
- runner cleanup=`containers 0 / volumes 0 / networks 0 / port_free true`；
- outer restore=`runner_rc=1 / restore_rc=0`，四个原 ID demo DB container 均恢复
  `running / healthy`。

## Artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `100376904952129bb86a923a64b39766cd33d25326d1458410f2933a2d45c31b` |
| `run.log` | `cb5e2c1a3ddedcbbe3c26f4f09379979ca4103dbe10531662821c2a8043d8d54` |
| `run-context.json` | `720344b6565e402cf729639e90be0c6584924ecb8a408d634685ed39b69d3aca` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `formalization-delta.json` | `2ec9f58bdc7fc0d6081502964767e5a9e241c307bd5937b9733c2643b3b894d2` |
| toolchain receipt | `aa69b0157b7a68a2df462c07ca506994c10ddbf9562d9ba66bf3515a440bd2af` |
| compose-tool-error XML | `bb03debb5f774155a0bde2202170311d0fa81dae480751ed1707e7c95fdb8e48` |
| compose-tool-error TXT | `43bfa0a04dfbfd75555434d271e3aa27352dbb5e0d8933cdbab958adaeaaf690` |
| domain-neutral-runner XML | `ca1a3020c9525e59c7a2e3be6a63b8b62e24fd54b97fb4f56584bdf0c9fc2ea1` |
| domain-neutral-runner TXT | `a6e8e6500d2ea379b939c9b444e54bce038bb23291be109c60352d9d3563485d` |

## Failure proof and root cause

Both failing reports contain one typed `IllegalStateException` and no assertion failure：

```text
JavaComposeScriptToolErrorSnapshotTest.shouldProduceComposeScriptToolErrorSnapshot
  Unable to locate foggy-data-mcp-bridge-python from <fresh-clone>/foggy-dataset-mcp

JavaDomainQuestionNeutralRunnerSnapshotTest.shouldProduceDomainQuestionNeutralRunnerSnapshot
  Unable to locate foggy-data-mcp-bridge-python from <fresh-clone>/foggy-dataset-mcp
```

Read-only inventory found ten snapshot producers containing the external repository literal。Seven
producers unconditionally create/write a sibling tree，the two mcp producers require a sibling
`pyproject.toml`，and one time-window producer conditionally writes depending on test order。This makes
the same Unit suite environment- and order-dependent and permits writes outside the sealed repository。

The local `target/parity/**` artifacts already exist as the correct Java-owned publication boundary。
The recovery therefore removes all cross-repository writes，retains local artifacts and adds a Unit
preflight that freezes `14` local snapshot producers with `0` external writers。

## Boundary

- r5 is not a coverage regression and produced no coverage gate/candidate/final；
- no threshold may be lowered and no sibling repository may be cloned/copied merely to turn r5 green；
- Cfreeze `bf860be7…` remains immutable historical evidence，but is superseded for execution by the next
  diagnostic lineage；
- Step 4 remains in progress；coverage audit、acceptance、Step 4 exit and release remain closed。
