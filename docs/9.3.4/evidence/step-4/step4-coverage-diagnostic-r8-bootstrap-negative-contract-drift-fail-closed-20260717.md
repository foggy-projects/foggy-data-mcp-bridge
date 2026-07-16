---
evidence_type: failed-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260716-diagnostic-r8
reported_launch_head: 3a3dd21a9aa956f0bedfffcf648ebb1cac0b756a
status: failed
failure_class: bootstrap-negative-static-contract-drift
decision: excluded-from-step4-exit
recorded_at: 2026-07-17
---

# Step 4 coverage diagnostic r8 bootstrap-negative contract drift fail-closed evidence

## Decision

调用方在启动前确认 worktree clean、`HEAD == origin/main`，两者均为
`3a3dd21a9aa956f0bedfffcf648ebb1cac0b756a`。该值只是已确认的 launch HEAD，不是
run-owned Git seal：`step4-coverage-20260716-diagnostic-r8` 在创建 Git/source seal 前，于
`bootstrap-negative` 以 exit code `1` fail closed。

失败前 full coverage contract、successor overlay、authority positive/negative 与 coverage
contract negatives=`20/20` 均通过。run-log lifecycle 动态 cases 已执行到静态 shape
检查；静态检查仍要求 Unit runner 直接包含旧 token
`v934_run_log_exit_trap "$?" v934_record_run_status`，但当前 Unit runner 为保证 MySQL
fixture cleanup，使用 `v934_unit_exit_trap` wrapper，并在 wrapper 内以保存后的
`exit_code` 委托 shared finalizer。静态契约没有同步该合法结构，因而错误拒绝当前 Unit
lifecycle。

r8 没有建立 run-owned Git/source identity，也没有启动任何 test lane、JaCoCo exec、
aggregate、critical-class gate、threshold observation 或 summary。该 run 明确为
`excluded-from-step4-exit / non-reusable`；其前置绿色不得与其他 run 拼接。

## Pre-run environment boundary

四个 repo demo DB 容器由 operator 在 evidence window 外停止；容器、volume 与 network
均未删除，exact ID 保持不变。启动 r8 前四个 frozen port 均为 free：

| Container | Frozen port | Exact container ID |
|---|---:|---|
| `foggy-demo-mysql` | `13306` | `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166` |
| `foggy-demo-mysql8` | `13308` | `f603e63eaf82f0c2243bef89d68cfe4ff59b60caf93dc4dbf44ea6f183c7816a` |
| `foggy-demo-postgres` | `15432` | `a4d6a278b0b99ff6a427b1870f24e7ebef4958341af327053eae76d4eb681d07` |
| `foggy-demo-sqlserver` | `11433` | `7881fa53039f1251ddd7ae8c43fd3cff549174a2cbaf07b4247c57495b8bf1fd` |

该维护动作不是 runner 接管 ambient listener，也不属于 r8 run-owned resource。

## Bootstrap observations

失败前按同一 outer invocation 观察到：

- full contract validation：`passed`；execution contract=`23 exec / 48 sessions`；
- successor overlay：`passed`；
- authority：positive=`2`、negative=`14`，status=`passed`；
- coverage contract mutation negatives：`20/20`，status=`passed`；
- run-log lifecycle 动态部分已完成 slow flush、logger nonzero、timeout、PID reuse、
  early signal、capture failure、exit、clean group 与 persistent residue cases，并进入随后
  的 static shape validation；
- lifecycle suite 未发布最终 `[v934-run-log-test] PASS`，因此不得把动态 case 完成写成
  完整 lifecycle negative gate 通过；
- static failure：

```text
scripts/verify-v934-unit.sh: missing lifecycle token 'v934_run_log_exit_trap "$?" v934_record_run_status'
```

当前 Unit runner 的实际 lifecycle 是：

1. `trap 'v934_unit_exit_trap "$?"' EXIT` 保存原退出码；
2. wrapper 先执行 lifecycle fixture fallback cleanup；
3. wrapper 再执行 main fixture fallback cleanup；
4. 最后调用
   `v934_run_log_exit_trap "$exit_code" v934_record_run_status` 发布 fail-closed status。

旧静态检查把 Unit 与 Integration 当成同一种直接 EXIT trap shape；它验证的是已过期的
文本 token，而不是 Unit fixture-aware cleanup 的等价且更强语义。

## Immutable outer result

- outer：`failed / exit_code=1 / last_phase=bootstrap-negative`；
- `run-status.env`：`git_head`、`started_at`、`source_before_sha256`、
  `source_after_sha256`、`outer_marker_sha256` 均为空；
- `toolchain_receipt_sha256=absent`、`summary_sha256=absent`；
- `run-context.json`、source-before/source-after inventory、run-owned `git-head.txt`、
  lane markers/reports、aggregate、threshold candidate 与 `summary.env`：全部 absent；
- Unit、Integration、Addon、database、external：均未启动；
- run-owned container/volume/network residue=`0/0/0`。

关键 artifact SHA-256：

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `ad4c50c64a7d121fb76dc1b7dc910d94f0e1efd6990ef5bc48975aa90d5fcb75` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `run.log` | `c231987f46a6d5e5cc29285295cd3d1e4f0e3f5e2c34921643fa977edfed3033` |
| `negative/coverage-contract.json` | `412011aeb5c27bf4e971f172a0ea159e4bbc1146ea0411baa0c43a1addad4943` |
| `negative/run-log-lifecycle.txt` | `174155e8f90a28a9c27db0ffee2b7952d2b9e4f736b57781ca019185974d7e7a` |

Canonical ignored evidence root：

- `target/v934-step4-coverage/runs/step4-coverage-20260716-diagnostic-r8/`。

## Cleanup and external restoration

outer finalizer 证明 r8 run-owned container/volume/network=`0/0/0`。r8 完整退出后，operator
在 evidence window 外按上表四个 exact ID 恢复 demo DB；复核四个容器均为
`running/healthy`，四个 frozen port listener 恢复。该外部恢复不属于 r8 cleanup receipt，
不得与 run-owned zero-residue 口径混写。

## Classification and next gate

failure class=`bootstrap-negative-static-contract-drift`，`product_regression=false`。runner
正确 fail closed；缺陷位于 Unit lifecycle 静态 shape 契约未随 fixture-aware wrapper
演进。BUG 记录：

- `docs/9.3.4/workitems/BUG-step4-unit-lifecycle-static-contract-drift.md`。

下一 gate：

- [x] 封存 r8 bootstrap-negative failure、artifact hashes、absence boundary 与 cleanup；
- [x] 登记 lifecycle static contract drift BUG；
- [x] 修复 Unit/Integration 分类型的 lifecycle static contract；
- [x] 补齐 Unit shape/source-seal `13/13 + 3/3` 与 Integration `11/11 + 5/5`
      expected-negative，并以 fail-closed `replace_exact` 保证 mutation anchor 精确命中；
- [x] focused lifecycle/static 唯一 PASS；动态 `9 类 / 14 case` 保留；runner raw-byte seal
      拒绝 false/subshell、heredoc source/eval 与 CRLF drift；
- [x] 两路独立正式实现质量复核 B/H/M/L=`0/0/0/0`；tool SHA-256=
      `8dcc679c2762ff8908b3bc26e8dfb0553a083eb75003dd80366fd82e78d8ed9b`，top
      `60/60` / `0c4e6c18f4af0a2c35418604ce80d20828ceb4c275375b7d12461b4683553a81`，
      successor `14/14`；
- [ ] commit/push，证明 clean `HEAD == origin/main`；
- [ ] 使用新 run ID r9 执行 fresh all-lane diagnostic，不复用 r8 目录或前置绿色。

当前 threshold=`diagnostic-pending`，`can_enter_coverage_audit=no`；Step 4 未通过，Step 5、
formal、coverage audit 与 acceptance 保持关闭。
