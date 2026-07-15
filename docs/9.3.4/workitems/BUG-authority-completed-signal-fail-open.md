---
type: bug
bug_source: implementation-quality-gate
version: 9.3.4
ticket: BUG-934-AUTHORITY-COMPLETED-SIGNAL-FAIL-OPEN
severity: critical
status: closed
reproduction_status: confirmed-by-signal-probe
test_strategy: runner-contract-and-signal-probe
automation_decision: required
owner: test-infrastructure
---

# Authority completed 窗口收到信号时错误保留绿色状态

## Problem

Unit/Integration runner 只有 `EXIT` trap。主流程把 `PHASE` 设为 `completed` 后、
撤销 trap 前若收到 `TERM`，`EXIT` handler 可能读取到 `0` 并写出
`status=passed/exit_code=0`，但 shell 实际以 `143` 结束，形成伪绿色证据。

## Expected

- `INT/TERM/HUP` 必须分别映射为 `130/143/129`，再进入唯一一次 EXIT finalizer；
- finalizer 必须屏蔽信号重入，失败时删除候选 summary 并原子写失败状态；
- 成功路径必须先屏蔽信号、再撤销 EXIT trap，消除绿色状态窗口；
- 三类信号动态探针与 successor 静态/负向契约全部通过；
- 修复后的 successor、Unit、Integration authority 必须重新执行。

## Fix Checklist

- [x] TERM 同构探针稳定复现 `process=143` 与 `run-status=passed/0` 分裂。
- [x] shared helper 增加 signal mapping、非重入 finalizer 与有序 disarm。
- [x] Unit/Integration runner 统一使用 shared trap lifecycle。
- [x] INT/TERM/HUP 动态探针全部 fail-closed。
- [x] successor contract/negative probe 固定信号边界。
- [x] 新一代 successor 双审确认。
- [x] 新一代 Unit/Integration authority 通过。

## Evidence

- `scripts/v934/authority_runner_lib.sh`
- `scripts/verify-v934-unit.sh`
- `scripts/verify-v934-integration.sh`
- `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`
