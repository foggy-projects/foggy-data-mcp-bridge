---
evidence_type: failed-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260717-diagnostic-r11
reported_launch_head: 141592ca9f4219d87a018774ee607b09a8e5a8a1
status: failed
failure_class: outer-runner-source-seal-binding-drift
decision: excluded-from-step4-exit
recorded_at: 2026-07-17
---

# Step 4 coverage diagnostic r11 outer source-seal fail-closed evidence

## Decision

调用方在启动前确认 worktree clean、`HEAD == origin/main`，两者均为
`141592ca9f4219d87a018774ee607b09a8e5a8a1`。该值只是已确认的 clean/pushed launch HEAD，
不是 run-owned Git seal：`step4-coverage-20260717-diagnostic-r11` 在建立 Git/source identity
前，于 `bootstrap-negative` 以 exit code `1` fail closed。

full coverage contract、successor overlay、sensitive-pattern bootstrap probe、authority 与
coverage contract negatives 已通过；lifecycle negative suite 随后检测到 outer runner 的
actual raw SHA-256 与其内嵌 frozen seal 不同，并发布 `E_SOURCE_SEAL`。r11 没有启动任何测试
lane，也没有生成 exec、coverage observation、sensitive-scan 或 summary。

结论为 `excluded-from-step4-exit / non-reusable`：其 bootstrap 前置绿色不能与后续 run 拼接，
也不能用于 threshold freeze、formal 或 Step 4 exit。

## Pre-run environment boundary

四个 repo demo DB 容器由 operator 在 evidence window 外停止；容器、volume 与 network 均未
删除，exact ID 保持不变。启动 r11 前四个 frozen port 均为 free：

| Container | Frozen port | Exact container ID |
|---|---:|---|
| `foggy-demo-mysql` | `13306` | `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166` |
| `foggy-demo-mysql8` | `13308` | `f603e63eaf82f0c2243bef89d68cfe4ff59b60caf93dc4dbf44ea6f183c7816a` |
| `foggy-demo-postgres` | `15432` | `a4d6a278b0b99ff6a427b1870f24e7ebef4958341af327053eae76d4eb681d07` |
| `foggy-demo-sqlserver` | `11433` | `7881fa53039f1251ddd7ae8c43fd3cff549174a2cbaf07b4247c57495b8bf1fd` |

该维护动作不是 runner 接管 ambient listener，也不属于 r11 run-owned resource。

## Bootstrap observations

同一个 outer invocation 在失败前观察到：

- full coverage contract validation：`passed`；workflow state=`diagnostic`，threshold=
  `diagnostic-pending`；
- successor overlay：`passed`；
- sensitive-pattern bootstrap probe：dangerous=`7/7`、safe=`3/3`、total=`10/10`，status=
  `passed`；该项只是内存 fixture probe，不是最终 run-root sensitive scan；
- authority：positive=`2`、negative=`14`，status=`passed`；
- coverage contract mutation negatives：`21/21`，status=`passed`；
- lifecycle suite 已执行 dynamic cases 并进入 outer raw source-seal 校验，但未发布最终
  `[v934-run-log-test] PASS`；因此不得把整个 lifecycle gate 记为通过；
- stable failure code=`E_SOURCE_SEAL`；expected frozen SHA-256=
  `02a920d91d1b8792cad47d65ce860352a8e9ecf39106f4489a714df01888dbaa`，actual outer
  SHA-256=`57f5da9a23c4973beef54a6bfd303c3dfd38fccb03a7bc2cadadbfaa3206f649`。

该 failure 不涉及测试结果：Unit、Integration、database、external、Addon 及其他测试 lane
均未启动，不得声称 r11 执行过测试 lane 或产生任何覆盖率结果。

## Root cause and fail-closed classification

r10 remediation 修改 outer runner 后，top `scripts/v934/step4/SHA256SUMS` 已刷新为 actual
SHA-256 `57f5da9a...`；但 `run_log_lifecycle_negative_test.sh` 的
`OUTER_RUNNER_SHA256` 仍为旧值 `02a920d9...`。该 nested constant 未被声明式 runtime
binding 或变更 checklist 覆盖，导致 top manifest 与前置 contract 可以通过，直到 lifecycle
source-seal check 才拒绝 drift。

runner 的行为正确：旧 seal 不得为新 bytes 背书。failure class=
`outer-runner-source-seal-binding-drift`，`product_regression=false`；问题属于 Step 4 test
governance/tooling binding 缺口。

## Immutable outer result and absence boundary

- outer：`failed / exit_code=1 / last_phase=bootstrap-negative`；
- run-owned `git_head`、`started_at`、`source_before_sha256`、`source_after_sha256`、
  `outer_marker_sha256`：全部为空；
- `toolchain_receipt_sha256=absent`、`summary_sha256=absent`；
- `summary.env`、`sensitive-scan.env`、`exec-manifest.json`、
  `coverage-observation.json`：全部 absent；
- run-owned Git/source inventory、lane marker/report、aggregate、threshold candidate 与 coverage
  gate：全部 absent；
- run root 中仅存在 `run-status.env`、`cleanup.env`、`run.log`、
  `negative/coverage-contract.json` 与 `negative/run-log-lifecycle.txt`；
- run-owned container/volume/network residue=`0/0/0`。

关键 artifact SHA-256：

| Artifact | SHA-256 |
|---|---|
| `run-status.env` | `c5e8cb6bfc32e867f3b261cd93b10cfcae7c7a6a18170c87e4812c62a4c221ca` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `run.log` | `8b8e80e62679995d0f41506113300902c2df974179d674c4b90c27535dcadf6b` |
| `negative/coverage-contract.json` | `f16ea4cef19c10855257c202aaffb734c1f26c4daa834f23ed363c26f77c1791` |
| `negative/run-log-lifecycle.txt` | `174155e8f90a28a9c27db0ffee2b7952d2b9e4f736b57781ca019185974d7e7a` |

Canonical ignored evidence root：

- `target/v934-step4-coverage/runs/step4-coverage-20260717-diagnostic-r11/`。

## Cleanup and external restoration

outer finalizer 证明 r11 run-owned container/volume/network=`0/0/0`。完整退出后，operator 在
evidence window 外按上表四个 exact ID 恢复 demo DB；复核结果为容器
`running/healthy=4/4`、frozen port listener=`4/4`。该外部恢复不属于 r11 cleanup receipt，
不得与 run-owned zero-residue 口径混写。

## Next gate

- [x] 封存 r11 bootstrap-negative failure、exact hash mismatch、absence boundary、cleanup 与
      external restoration；
- [x] 登记 `BUG-934-STEP4-OUTER-RUNNER-SOURCE-SEAL-BINDING-DRIFT`；
- [x] 更新 outer raw/executable-stream seals，保持双层 source-seal fail closed；
- [x] 增加 canonical positive=`1` 与 binding negatives=`6`，证明 exact binding；
- [x] 将 Unit/Integration/outer/library nested constants 纳入 early manifest/raw binding；
- [x] 完成完整 lifecycle suite、manifest、successor、contract/overlay focused 回归；
- [x] 完成正式实现质量复核并清零 B/H/M；两路 post-fix 与 docs/status review=`0/0/0/0`；
- [ ] commit/push 并证明 clean `HEAD == origin/main`；
- [ ] 使用全新 run ID `step4-coverage-20260717-diagnostic-r12` 执行 fresh all-lane
      diagnostic，不复用 r11 目录或前置绿色；
- [ ] 仅在 fresh diagnostic 成功后冻结 exact-observed threshold，再执行 fresh formal、最终质量、
      coverage evidence audit 与 acceptance。

当前 threshold=`diagnostic-pending`，`can_enter_coverage_audit=no`；r11 不可复用，Step 4 未
通过，Step 5、formal、coverage audit 与 acceptance 保持关闭。

## Post-failure remediation status（not r11 evidence）

以下结果发生在 r11 退出后，只说明针对该 failure 的 coded/focused remediation；不得回写成
r11 pass，也不得改变上文 immutable absence boundary：

- outer 在创建 run root、建立 Git/source seal 或启动 test lane 前调用 early binding preflight；
- Unit runner、Integration runner、outer runner、lifecycle library 四个独立 frozen constants
  均与 canonical raw bytes、top manifest exact rows 做三方 binding；成功 stable line=
  `V934_RUNNER_SEAL_BINDINGS status=passed count=4`；
- binding canonical positive=`1`；stable `E_SOURCE_SEAL_BINDING` negatives=`6`：
  outer+manifest refresh/nested stale、outer-runner-only drift、valid-64 nested constant-only
  wrong、missing constant、duplicate constant、invalid-format/63-char constant；
- outer CRLF raw mutation 与 executable no-op semantic mutation 分别触达 raw-byte 与
  executable-stream seal；
- current outer raw/executable-stream SHA-256=
  `90b4b979e55c17243644cce186767a4647ce79c85b431adcb415bddd18cc1cec` /
  `065211912aab5227125ef02f40e2965fce7ff5060df5c7b91a902c4ad4f34cae`；
- current lifecycle tool/top manifest SHA-256=
  `61bf7b990bdef6e0d75c53010644bcc6d1525a67119cd36c5f82eeb911e005fc` /
  `a9b105ce2f8f640dfa09863e797697bcf9892a7b0fa68b38f83b5bbd7435afb4`；
- safety review 的 preflight path-check/read TOCTOU Medium 已由 descriptor-bound strict read
  关闭：`O_NOFOLLOW`、`fstat`、fd read、post-`lstat` stable identity；stable error 仍为
  `E_SOURCE_SEAL_BINDING`；两路 post-fix implementation review 与独立 docs/status review
  最终 B/H/M/L=`0/0/0/0`；
- focused lifecycle suite=`PASS`，top manifest=`60/60`、successor manifest=`14/14`、coverage
  contract mutations=`21/21`、overlay negatives=`12/12`。

当前 remediation=`formal quality passed / commit pending`。仍须 commit/push、证明 clean
`HEAD == origin/main`，再从空 run root 执行 fresh
`step4-coverage-20260717-diagnostic-r12`；不得声称 r12 已运行。threshold freeze、formal
coverage run、coverage audit、acceptance、Step 4 exit 与 Step 5 均保持关闭。
