---
type: bug
bug_source: acceptance-found
version: 9.3.4
ticket: BUG-934-CALCULATE-MVP-SQLITE-SKIP
severity: major
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
---

# CALCULATE unsupported-runtime 用例在 SQLite lane 必然 skip

## Background

9.3.4 Step 2 integration runner 的独立预审计发现，confirmed r3 将
`CalculateMvpIT` 归入 `sqlite-broad`，但
`calculateFailsClosedForRuntimeUnsupportedDatabase` 使用
`Assumptions.assumeFalse(supportsWindowFunctions())`。SQLite 支持窗口函数，因此该节点
在 Step 2 中必然 skip，与 authority 的零 skip 契约冲突。

## Reproduction

已有 v9.3.3 SQLite 运行的 fresh Failsafe XML 连续显示
`CalculateMvpIntegrationTest`/`CalculateMvpIT` 为 `Tests run: 14, Skipped: 1`。
Step 2 report verifier 对任何 positive suite 的 `skipped > 0` 稳定拒绝，即使 Maven
返回 0 也会以 `E_REPORT_OUTCOME` fail-closed。

## Expected vs Actual

- 期望：SQLite Step 2 中 47 个 Failsafe ownership 全部实际执行，零 skip。
- 实际：MySQL 5.7 负向 capability 用例依赖当前运行数据库，在 SQLite
  稳定跳过，既不提供负向证据，也会阻断权威报告。

## Impact Scope

- 仅修正测试 capability seam，不改生产 CALCULATE 逻辑。
- 保持方法名与 discovery node 不变，但源码内容变更必须触发 successor
  重生成、复核与确认。

## Test Strategy

1. 不放宽 report verifier 的零 skip 契约。
2. 使用真实 `FactSalesQueryModel` 的 spy，仅在 capability 边界提供
   MySQL 5.7 dialect 与 JDBC metadata。
3. 在 SQLite lane 中确定性断言 `CALCULATE_WINDOW_UNSUPPORTED`，保持原测试
   node 不变且不跳过。
4. 聚焦执行 `CalculateMvpIT`，要求零失败/零错误/零跳过；最终由
   Step 2 integration authority 的 exact-set/mtime 校验收口。

## Code Inventory

- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/CalculateMvpIT.java`
  - 以可控 MySQL 5.7 metadata 替代 runtime assumption。
  - 新增仅供该方法使用的 query-model analysis overload。

## Fix Checklist

- [x] 独立审计确认 SQLite 下必然 skip。
- [x] 保留 zero-skip fail-closed 报告契约。
- [x] 改为确定性 MySQL 5.7 capability seam。
- [x] focused `CalculateMvpIT` 零 skip GREEN。
- [x] Step 2 integration authority GREEN。

## Verification

先执行修正后的单方法，再执行完整 class：

```text
focused method: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
complete class: Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
```

同一 SQLite 运行下，原来稳定的 `Skipped: 1` 已消除。r4 successor 保持
14 个 discovery nodes 和原 Failsafe ownership，仅通过 hash-sealed test-source amendment
允许源码/test-classes 内容变化。最终关闭条件是 Step 2 integration
authority 的 47 个 positive executions 全部零 skip。

## References

- `scripts/verify-v934-integration.sh`
- `scripts/v934/step2_report_tool.py`
- `scripts/v934/successor/step2/execution-inventory.tsv`
- `target/v934-step2-successor/runs/step2-candidate-r4-20260714/summary.env`
- `target/v933-step2-report/runs/`
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`
