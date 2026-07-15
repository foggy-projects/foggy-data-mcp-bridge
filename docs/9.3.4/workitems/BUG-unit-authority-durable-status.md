---
type: bug
bug_source: quality-review
version: 9.3.4
ticket: BUG-934-UNIT-AUTHORITY-DURABLE-STATUS
severity: major
status: closed
reproduction_status: confirmed-by-code-path
test_strategy: runner-contract-and-signal-probe
automation_decision: required
owner: test-infrastructure
---

# Unit authority 缺少 durable run log/status

## Problem

r7 Unit runner 没有 Integration 已具备的 EXIT trap。若 report collect/finalize 后的
negative、source guard 或 summary 阶段失败，run root 可能留下局部 `status=passed`
manifest，却没有持久化的 outer-run failed 状态。

## Expected

- Unit 在 run root/trap 初始化后持续写 `run.log`，EXIT 时写入 run id、HEAD、起止时间、
  last phase、原始 exit code 与 passed/failed。
- 只有 `exit_code=0 && last_phase=completed` 可标记 passed，trap 必须保留原退出码。
- final report 发布尽量放在 negative 与 source-after 之后；最终 evidence 同时验证
  summary、final manifest 与 durable run status。

## Fix Checklist

- [x] Unit 加入与 Integration 对称的 durable log/status trap。
- [x] finalization 移到 negative/source guard 后。
- [x] runner contract 与负向探针封存该行为。
- [x] r8e Unit `completed/0/passed`。

## Evidence

- `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`
