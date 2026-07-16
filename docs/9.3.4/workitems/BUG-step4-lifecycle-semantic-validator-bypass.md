---
type: bug
bug_source: quality-gate-found
version: 9.3.4
ticket: BUG-934-STEP4-LIFECYCLE-SEMANTIC-VALIDATOR-BYPASS
severity: blocker
status: in-progress
reproduction_status: confirmed
product_regression: false
test_strategy: lifecycle-semantic-shape-and-coded-mutation-regression
automation_decision: required
owner: step4-coverage-tooling
---

# Step 4 lifecycle 静态校验可被注释、死分支和动态 trap 拼接绕过

## Background

r9 fail-closed 后的实现质量复核重新审计
`scripts/v934/step4/run_log_lifecycle_negative_test.sh`。canonical Unit、Integration、outer
runner 与 shared library 均未被篡改，top SHA manifest 仍会拒绝直接 byte drift；但审计构造
的 shape-only mutation 证明若维护者同步更新 raw seal，若干语义 guard 可以伪绿。该缺陷
属于测试证据链本身，必须在 fresh r10 前闭合。

## Confirmed bypasses

1. outer/library critical ordering 使用 raw text 的 `in/index/rfind/count`：
   - 注释实际 authority FD close、保留同 token 的注释，仍被接受；
   - 把 `CHILD_SIGNAL_CRITICAL=true` 放入 `if false; then`，仍被接受；
   - library 中注释实际 logger authority close 或把 `v934_run_log_abort_open || true`
     放入死分支，也能满足 raw count/order。
2. `require_exact_traps()` 无法识别动态拼接 command word。在 canonical EXIT trap 后追加
   `t$''rap : EXIT`、`t$""rap : EXIT` 或 `t${EMPTY:-}rap : EXIT`，shape validator 接受；
   Bash 实际把它们解释为 `trap : EXIT`，会覆盖 Unit/Integration finalizer。
3. 现有 Unit `3` 个、Integration `5` 个所谓 source-seal negative 都先被 raw SHA guard
   拒绝；它们未断言稳定错误码。关闭 seal 后 7/8 shape mutation 被接受，仅 eval shadow
   被 canonical-reference guard 拒绝，因此原文档对“语义 guard 已覆盖 false/subshell/
   heredoc/CRLF”的声明过强。

修复前的基线 lifecycle suite 仍会发布 PASS，说明这是可复现的 test false-green gap，不是
产品 runtime regression。

## Expected

- 注释、heredoc body、quoted data 与不可达 control-flow context 不得满足 lifecycle
  executable contract；
- sensitive command 不得通过 shell expansion/quote concatenation 动态拼成 `trap` 等关键
  lifecycle invocation；
- raw-byte seal 与 semantic shape 是两个独立边界，负测必须分别触达并断言预期稳定错误码；
- CRLF 只作为 byte-seal mutation，不冒充 semantic shape coverage；
- outer、shared library、Unit 与 Integration 均有可独立复算的 executable-stream identity，
  不再以 raw substring count 充当最终语义证明。

## Fix Strategy

1. 为 shape failure 增加稳定 code；negative helper 必须断言 exact expected code。
2. semantic mutation 关闭 raw source seal，证明对应 executable/context/dynamic-command guard
   真正触达；byte-only mutation 单独断言 source-seal code。
3. 对 outer/library 建 executable physical/logical stream contract 与 exact semantic seal，
   删除 raw comment/dead-code 可满足的最终判定。
4. 拒绝 command-position dynamic spelling，使 `t$''rap`、`t$""rap`、
   `t${EMPTY:-}rap` 等 override fail closed。
5. 增加 outer/library commented-close、false-context flag、dead abort-open，以及 Unit/
   Integration dynamic trap、false/subshell、heredoc shadow 等 coded mutation。
6. 保留现有真实 dynamic cases、canonical runner/library bytes和 runtime behavior。

## Regression Test Decision

`automation_decision=required`。这是 mandatory bootstrap-negative gate 的伪绿色缺口；只做
人工说明或依赖 top manifest 会让后续合法维护时可能同时更新 seal 和危险语义。因此必须
先补失败 mutation，再修 validator，最后完整 lifecycle suite 唯一 PASS。

## Checklist

- [x] 独立审计复现 outer/library comment/dead-context bypass。
- [x] 独立审计复现三类 dynamic `trap` command-word bypass。
- [x] 证明原 8 个 source-seal negative 未触达声称的 semantic guard。
- [x] 实现 coded semantic/executable-stream fail-closed 校验。
- [x] 补齐并运行 exact-code mutation regression。
- [x] 完整 lifecycle dynamic/static suite 唯一 PASS：原动态 `9 类 / 14 case` 保留，
      Unit/Integration shape=`16/16 + 14/14`、semantic stream=`2/2 + 5/5`、raw source
      seal=`2/2`、outer/library=`3/3 + 3/3`。
- [x] 两路独立 lifecycle review 与全量正式质量最终 B/H/M/L=`0/0/0/0`。
- [x] 更新并验证 top=`60/60`、successor=`14/14` SHA manifest。
- [ ] commit/push，证明 clean `HEAD == origin/main`。
- [ ] 使用全新 run ID 完成 fresh all-lane diagnostic。
- [ ] fresh formal、最终质量、coverage audit 与 acceptance 后关闭本 BUG。

当前 Step 4/5、threshold freeze、formal、coverage audit 与 acceptance 全部保持关闭。

## References

- `scripts/v934/step4/run_log_lifecycle_negative_test.sh`
- `scripts/v934/step4/run_log_lifecycle_lib.sh`
- `scripts/verify-v934-unit.sh`
- `scripts/verify-v934-integration.sh`
- `scripts/verify-v934-step4-coverage.sh`
- `docs/9.3.4/quality/step4-diagnostic-ready-implementation-quality.md`
