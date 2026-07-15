---
type: bug
bug_source: quality-review
version: 9.3.4
ticket: BUG-934-STEP2-REPORT-CROSS-RUN-SPLICE
severity: critical
status: closed
reproduction_status: confirmed-by-code-path
test_strategy: report-tool-negative-test
automation_decision: required
owner: test-infrastructure
---

# Step 2 report manifest 未绑定 outer run，可跨 run 拼接

## Problem

r7 report manifest 只封存 successor、variant、XML 与 marker mtime/hash；marker 内容没有
被解析，manifest 也没有绑定统一的 run id、Git HEAD、protected source hash。`finalize`
只检查 variant 唯一与 exact report set，因此可把多个 run 的局部 manifests 合并成
`status=passed`。

## Expected

- runner 在测试前生成唯一 `run-context.env`，至少绑定 run id、runner、Git HEAD、
  protected source hash、successor freeze/manifest。
- 每个 marker 必须声明同一 context hash、run id、runner 与 variant；collect 解析而非
  只比较 mtime。
- per-variant manifest 与 merged manifest 都封存 context；finalize 要求所有输入与命令
  指定 context 完全一致。
- `cross-run-splice` 负向探针必须得到稳定错误码。

## Fix Checklist

- [x] report schema 加入 run context identity。
- [x] collect 校验 marker/context 内容与哈希。
- [x] finalize 拒绝 cross-run manifests。
- [x] cross-run splice 负向探针通过。
- [x] Unit/Integration durable run status 与 final manifest 同轮封存。
- [x] r8e authority 通过。

## Evidence

- `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`
