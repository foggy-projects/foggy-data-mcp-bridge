---
evidence_type: failed-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260716-diagnostic-r3
tested_commit: e16693297239f2a861f3b93b3de60c1bb783bda0
status: failed
decision: excluded-from-step4-exit
recorded_at: 2026-07-16
---

# Step 4 coverage diagnostic r3 fail-closed evidence

## Decision

`step4-coverage-20260716-diagnostic-r3` 从 clean committed/pushed HEAD
`e16693297239f2a861f3b93b3de60c1bb783bda0` 启动。契约、successor overlay、工具链、
Step 2 report view 与 fresh class universe 校验均通过；Unit authority 完整通过。outer 随后
在 `child-unit` 检测到 live process-group member 并 fail closed。

该 run 没有执行 Integration、database、required external、Addon、aggregate、model gate
或 threshold observation，`summary.env` 不存在。因此 r3 明确排除在 Step 4 exit、threshold
freeze、coverage audit 与验收证据之外；Unit 绿色不得与 r1/r2/focused 结果拼接。

## Immutable result

- outer：`failed / exit_code=1 / last_phase=child-unit`；
- tested source seal：
  `d06072c74b4e853b2076cccf26b80e0d7201cb6214872ade7d3be8271ea5756a`；
- fresh class universe：`24 modules / 2,098 classes`；
- Step 2 run-owned view：`728 positive / 59 structural / 5,261 testcase`；
- Unit authority：`681 positive / 55 structural / 4,941 testcase / F0E0S0`；
- failure：Unit child PASS 后 outer 报
  `child returned with live process-group residue: unit`；
- outer summary、coverage observation、acceptance candidate 均 absent；
- cleanup：container/volume/network=`0/0/0`；
- finalizer 后当前不存在 r3 Maven、Surefire、Java、tee 或 Docker run-owned residue。

## Artifact identity

- outer `run-status.env`：
  `7ccd7408c87b8d8be0f1983361117173f3545d6b4f689a74ddc5518b9a6225f2`；
- outer `run.log`：
  `fc494fdfe04b7b4e5ea241716e4cecbea0f5678cc7a53e71361e815e655f258e`；
- outer `cleanup.env`：
  `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1`；
- `toolchain-receipt.json`：
  `c4fc147c65defe3170d0ddb1ab5598b73b3674c639e12ff7421cebfe93a419a9`；
- `class-universe.json`：
  `4c12d9f864387b78ad332aaee6d388f07f22c96c62af86bacbf8c12e492246f5`；
- outer `run-context.json`：
  `e2db62d3998bae40de33e9e0cba09e41e61212f9bf3a1f0415dc42562eee3c85`；
- Unit `run-status.env`：
  `d8eaded1ddca36b033e7a2fef0f5c7d5b529f48ba0c3e63c44af71cefceeca56`；
- Unit `summary.env`：
  `ef4cc02aba55415430ac8c1e8866b81e3fb05b7de0a015c9fb2e81a0bcf1c2b5`；
- Unit final report manifest：
  `54fc121851f9d087f61ad39b334b711b2f3b3bf6eb74c21fdb3905a9e4d8dcd4`。

原始 ignored evidence 位于：

- `target/v934-step4-coverage/runs/step4-coverage-20260716-diagnostic-r3/`；
- `target/v934-step2-unit/runs/step4-coverage-20260716-diagnostic-r3/`。

## Root cause boundary

Unit/Integration 使用未捕获、未 wait 的 Bash `exec > >(tee -a run.log)`。child launcher
通过 `setsid()` 建立独立 PGID，日志 `tee` 继承该 PGID；child shell 完成并返回 0 后，logger
仍可能短暂排空。outer 只 wait leader，再立即探测整个 PGID，因而命中 logger teardown
窗口。受控复现已证明显式 close/write-end + wait logger 可消除残留。

r3 没有在 TERM 前保存 group member receipt，现有 artifact 不能恢复当时的确切 PID。不得把
受控复现 PID 写成 r3 PID，也不得在缺少证据时声称已排除所有其他 descendant。修复同时要求
加入 ready handshake 与 kill-before member snapshot，补齐这一证据缺口。

## Next gate

- [ ] managed logger 与 process-group receipt 回归通过；
- [ ] pending-safe generic freeze/formal 工具能力完成，但 formal 保持禁用；
- [ ] identity/static/quality gate 通过并从新 commit push；
- [ ] fresh all-lane diagnostic 完整产出 observation；
- [ ] 只有 reviewed exact thresholds、独立 freeze commit 与 fresh formal replay 通过后，才可
  进入 coverage audit 或 Step 4 exit 判定。
