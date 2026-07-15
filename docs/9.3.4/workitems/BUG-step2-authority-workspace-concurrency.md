---
type: bug
bug_source: quality-review
version: 9.3.4
ticket: BUG-934-STEP2-AUTHORITY-WORKSPACE-CONCURRENCY
severity: critical
status: closed
reproduction_status: confirmed-by-code-path
test_strategy: script-contract-and-negative-test
automation_decision: required
owner: test-infrastructure
---

# Step 2 authority 共享 workspace 缺少互斥与 publish CAS

## Problem

Unit、Integration 与 successor runner 都会清理或覆盖 24 模块共享的
`target/test-classes`、Surefire/Failsafe reports 或 canonical successor。r7 以前没有
workspace lock；并发运行可读取另一 run 的 fresh XML，successor 也存在 precheck 到
publish 的 TOCTOU，因而 run id 与实际产物来源并不具备排他性。

## Expected

- 三个 runner 在任何共享清理、编译、测试或 canonical publish 前获取同一 workspace
  authority exclusive lock；锁冲突必须在无共享写入的状态下 fail-closed。
- successor 在 publish 前对当前 canonical freeze、manifest、confirmed summary 再做
  compare-and-swap 校验，不能只依赖启动时 precheck。
- lock 路径、模式、获取时点和 publish CAS 进入 successor runner contract 与负向探针。

## Fix Checklist

- [x] 三个 runner 使用同一 exclusive lock。
- [x] successor publish 前执行 canonical provenance CAS。
- [x] successor 静态契约封存 lock/CAS。
- [x] lock/CAS 漂移负向探针通过。
- [x] r8e successor 双审确认。
- [x] r8e Unit/Integration authority 通过。

## Evidence

r7 Unit `step2-unit-r7-20260715` 自身运行结果为 `677 + 55 / 4890 / F0 E0 S0`，
但因本缺陷只作为诊断基线，不作为最终 Step 2 authority。

r8e successor 完成独立双审并发布；r8e Unit 与 Integration 在共享互斥锁下依次完成，
最终权威证据见 `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`。
