---
type: bug
bug_source: quality-review
version: 9.3.4
ticket: BUG-934-STEP2-REPORT-DISCOVERY-CARDINALITY
severity: critical
status: closed
reproduction_status: confirmed-by-negative-fixture
test_strategy: report-tool-negative-test
automation_decision: required
owner: test-infrastructure
---

# Positive report 未约束冻结 discovery node 基数

## Problem

r7 verifier 对 positive XML 只要求 `tests > 0`。原 negative baseline 甚至把每份 positive
suite 统一伪造成一个 testcase 仍可通过 collect；例如 `sqlite-broad` 冻结 discovery
合计为 303 nodes，其中 `PivotIT=55`，严重截断仍可能被当成绿色。

## Expected

- report tool 从 hash-sealed `discovery-inventory.tsv` 按 source/report/owner 精确关联。
- `runtime_deferred_containers=0` 的 suite 要求实际 testcase nodes 与冻结 discovery nodes
  exact equal；存在 runtime-deferred container 时至少不得低于冻结静态 nodes。
- manifest 显式记录 discovered/deferred/cardinality policy；raw XML 复核时重复验证。
- `partial-testcase-cardinality` 负向探针必须 fail-closed。

## Fix Checklist

- [x] successor contract 加载并绑定 discovery inventory hash。
- [x] collect/manifest revalidation 实施 node cardinality。
- [x] negative baseline 使用每份 suite 的冻结 node 下界。
- [x] partial testcase 负向探针通过。
- [x] r8e Unit/Integration 实际节点数验证通过。

## Evidence

- `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`
