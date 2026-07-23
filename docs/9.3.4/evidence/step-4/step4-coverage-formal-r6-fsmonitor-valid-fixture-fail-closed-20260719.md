---
evidence_type: formal-failure-fail-closed
version: 9.3.4
step: 4
run_id: step4-coverage-20260719-formal-r6
tested_commit: 14931b681f0e4203c1108264f9c1f3b4cfb514db
status: failed
decision: requires-new-diagnostic
failed_phase: bootstrap-negative
recorded_at: 2026-07-19
---

# Step 4 formal-r6 fsmonitor-valid fixture failure

## Decision

`step4-coverage-20260719-formal-r6` 在真正 fresh、non-shallow、clean clone 的
Cfreeze `14931b68…` 上于 mandatory `bootstrap-negative` 阶段 fail closed。该 run 永久保持
`failed / excluded / non-reusable`；不得重跑同 ID、补写后续 artifact、拼接 r22 或提升为 formal
authority。

formal delta、frozen diagnostic r22 replay、full formal contract 与 successor overlay 均先通过；
失败发生在 source seal、toolchain receipt 和所有测试 lane 启动之前。因此它不是生产代码、覆盖率或
fresh clone source identity 回归。

## Run identity and absence boundary

- fresh clone `HEAD=14931b681f0e4203c1108264f9c1f3b4cfb514db`，唯一 parent=
  `cfb64d629b06f03a078ebae06580918d4c8df301`；branch 与 remote exact；
- repository=`clean / non-shallow / replace refs 0 / grafts empty`；
- `finished_at=2026-07-18T19:25:17Z`、`last_phase=bootstrap-negative`、`exit_code=1`；
- formalization delta=`direct-single-parent / exact 12 changed paths / passed`；
- frozen r22=`ancestor verified / confirmed threshold 700e0bdf… / passed`；
- full contract=`formal / confirmed / 61 manifest entries / passed`；
- successor overlay=`3 parents / 4 contracts / 35 amendments / required 45/446 / Addon 2/6 / passed`；
- `git_head`、`started_at`、source-before/after、outer marker 均为空，toolchain receipt 与 summary
  均 `absent`，符合 bootstrap early-failure 契约；
- runner cleanup=`containers 0 / volumes 0 / networks 0 / port_free true`；outer restore=
  `runner_rc=1 / restore_rc=0`，原四个 demo DB container 均恢复同一 ID 且 healthy。

原始 run root=
`/tmp/v934-formal-r6.rw3Ssn/repo/target/v934-step4-coverage/runs/step4-coverage-20260719-formal-r6`。
最小可持久复核副本位于 `formal-r6-failure-capsule/`；manifest SHA-256=
`969beebbaf47db053f5829f43f24a0782e6f6983079407aee56b6160ce0c21de`，entries=`5`，
total size=`10412`。其中 outer restore receipt 绑定：

| DB | Exact container ID | Port | Post-restore state |
|---|---|---:|---|
| MySQL 5.7 | `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166` | 13306 | running / healthy |
| MySQL 8 | `f603e63eaf82f0c2243bef89d68cfe4ff59b60caf93dc4dbf44ea6f183c7816a` | 13308 | running / healthy |
| PostgreSQL 15 | `a4d6a278b0b99ff6a427b1870f24e7ebef4958341af327053eae76d4eb681d07` | 15432 | running / healthy |
| SQL Server 2022 | `7881fa53039f1251ddd7ae8c43fd3cff549174a2cbaf07b4247c57495b8bf1fd` | 11433 | running / healthy |

## Artifact identities

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `462eb651d46415ae417bc8a20a9b33acc5254bc4df1e0c9f7aafad8c6de13e0b` |
| `run.log` | `f250635d6389c5280dfddbb2e8bb06430284c98d83cec7c5202667389e528195` |
| `formalization-delta.json` | `c1d84e8762dd6b4c57bec62a82a9094edb878c01c540c7930dc4d3da9d38955b` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `outer-restore.json` | `0124a05738d9e2c1b53ce86e5f2f4b4740e155facf535fcd3d6c2e849ce04229` |
| capsule `manifest.json` | `969beebbaf47db053f5829f43f24a0782e6f6983079407aee56b6160ce0c21de` |

Success-only artifact boundary：`run-context.json`、`toolchain-receipt.json`、所有 child XML/exec、
coverage observation/gate、summary、candidate/final manifest 均未生成。

## Root cause

失败断言为：

```text
coverage_contract_negative_tool.py: source_hash_fsmonitor_probes
fsmonitor-valid fixture flag differs
```

负例夹具向 Git fsmonitor hook v2 返回 `0\n`。v2 协议要求新 token 以 NUL 结尾，并继续使用
NUL 分隔 changed paths。旧换行响应使同一个 synthetic index 的首个 `git ls-files -f -z`
偶发把已持久化的 `CE_FSMONITOR_VALID` 位显示为大写 `H`；紧接第二次读取恢复为小写 `h`，
`.git/index` 哈希与 debug `flags=0x200000` 均不变。

独立矩阵对旧实现复现 `996 h / 4 H`；改为固定 `token\0` 后为 `1000/1000 h`。主仓与 fresh
clone 的真实 index 均为 `4066 × H`，不存在 fsmonitor-valid、assume-unchanged 或 skip-worktree
entry。本次差异属于 synthetic fixture protocol/timing oracle，不属于 clone 内容差异。

## Recovery boundary

最小修复只把 synthetic hook 改为合规 NUL-token；保留精确 `h tracked.txt\0` 前置断言和真实
validator 必须以 rc=2 拒绝 `fsmonitor-valid` 的 fail-closed 断言，不接受 `H`、不重试、不跳过。

该工具不在 r22 Cfreeze formalization allowlist，故必须重置为
`diagnostic-ready / diagnostic-pending`，形成新 Cdiag，完成 fresh diagnostic、全新 candidate、
portable capsule、双审、direct-child Cfreeze 和 fresh formal-r7。r22/Cfreeze/r6 均只保留历史证据。
