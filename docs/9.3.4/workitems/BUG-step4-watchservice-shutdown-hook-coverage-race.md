---
type: bug
bug_source: formal-gate-found
version: 9.3.4
ticket: BUG-934-STEP4-WATCHSERVICE-SHUTDOWN-HOOK-COVERAGE-RACE
severity: blocker
status: closed
reproduction_status: confirmed
product_regression: false
test_strategy: deterministic-isolated-lifecycle-unit-regression
automation_decision: required
owner: step4-coverage
---

# Step 4 WatchService shutdown hook 覆盖竞态

## Background

fresh formal `step4-coverage-20260717-formal-r1` 在 clean/pushed Cfreeze commit
`86d505810524383da6211bcc2a7965e9a4afb34e` 上完成全部测试与证据 lane，随后在
`formal-coverage-gate` 以 `E_FORMAL_LOW` fail closed。该 run 的 `summary.env`、
`coverage-gate.json`、candidate 与 final manifest 均未发布，run-status 为
`failed / exit 1`，cleanup=`0/0/0`、sensitive scan=`passed`。

immutable failure evidence：

- `docs/9.3.4/evidence/step-4/step4-coverage-formal-r1-watchservice-shutdown-race-fail-closed-20260717.md`。

## Expected vs Actual

- Expected：formal 必须在相同 source/class tree 上稳定达到 reviewed diagnostic 的 exact
  aggregate 与 critical thresholds；覆盖不得依赖 JVM 退出时 shutdown hook 的调度顺序。
- Actual：r13 aggregate=`54624/76830 line, 26109/44870 branch`，formal-r1=
  `54615/76830 line, 26106/44870 branch`；全部 `-9 line / -3 branch` 仅来自
  `WatchServiceFileTracer`。
- Actual：该类 line 从 `204/244` 降至 `195/244`，同时低于 reviewed exact threshold 和
  80% floor；branch 从 `98/128` 降至 `95/128`。

## Root Cause

`WatchServiceFileTracer.shutdown()` 没有确定性的显式测试入口，唯一执行入口是构造器注册的
`Runtime.addShutdownHook(new Thread(this::shutdown))`。JaCoCo 0.8.12 同样使用 JVM
shutdown hook 执行 dump-on-exit；JVM 对多个 shutdown hook 并发启动且不保证执行顺序。

r13 恰好在 tracer hook 完成后 dump，记录了 `processEvents()` 的退出和 `shutdown()` 清理；
formal-r1 则在 tracer 只执行到 `running=false / executor.shutdown()` 时已经 dump。逐 exec probe
比较证明差异只存在于 `jacoco-ut.exec`，其余 22 个 integration exec 完全一致；24 个 module
group 的 XML 逐类比较也只有该类变化。因此不能通过重复 formal 碰运气获得伪绿色。

## Fix Strategy

1. 在既有 `WatchServiceFileTracerTest#testWatchServiceAvailable` testcase 内反射 private
   constructor 创建隔离 tracer；不得关闭全局 singleton。
2. 显式 `shutdown()` 隔离实例并断言 `available true -> false`，同时断言全局 singleton 仍可用。
3. 不新增 testcase，保持 unit=`681/4941`、required=`773+59/5707` 冻结 cardinality。
4. 多次 fresh-fork focused test/JaCoCo 复核 shutdown probe 与 line/branch counters 稳定。
5. 测试源变更必须进入新的 diagnostic commit；canonical contract/threshold 恢复为
   `diagnostic-ready/diagnostic-pending`。不得直接重跑当前 Cfreeze 的 formal。
6. 新 diagnostic 通过后重新 candidate/review/Cfreeze，再运行 fresh formal。

## Regression Test Decision

`automation_decision=required`：现有测试用例必须在 JVM 退出前完成 isolated lifecycle；测试
报告节点总数保持 11。验证既覆盖显式 shutdown，也证明 singleton 未被污染；最终以新的
diagnostic 与 formal 两次 all-lane evidence 证明不再依赖 hook 顺序。

## Fix Checklist

- [x] formal-r1 失败 run、absence semantics、cleanup 与 sensitive scan 已确认。
- [x] aggregate、12 critical rows、source line、method 与 23 exec probe bitmap 已逐项对比。
- [x] 根因确认为 tracer/JaCoCo shutdown hook 顺序竞态，不是漏跑或 class-tree 漂移。
- [x] 选择既有 testcase 内的 isolated tracer 方案，避免污染 singleton 和 cardinality。
- [x] 实现并重复执行 focused regression：5/5 fresh forks=`177/245 probes`，目标 7 probes
  全命中、bitmap unique=`1`，Surefire=`11/F0E0S0`。
- [x] 恢复 b765 exact diagnostic machine state；contract/threshold/manifest=
  `15dae282...0b0b / 0df17a87...ff96 / cc356897...dc60`，manifest=`60/60`。
- [x] 完成新 Cdiag commit/push 与 clean identity：`322bb346cca19998a90d6d990505ef033f3a496a`。
- [x] fresh r14 diagnostic 与 threshold review 通过：WatchService=`204/244 line,99/128 branch`，
  candidate SHA-256=`9087774387f0bb4b177a1b5f2fe28a4102e0434afe7a5b316aa106511c9e6d55`。
- [x] direct-child Cfreeze=`1901a10138bac06a09b875c907b7aea6e2789b04` commit/push；
  fresh formal-r2 中 WatchService exact `204/244 line,99/128 branch`，关闭本 BUG 的稳定性验证。
- [ ] 9.3.4 最终 implementation quality、coverage audit 与 acceptance 通过（版本级后置门，
  当前由独立的 ListPreset branch-order BUG 阻塞）。

## References

- `foggy-core/src/main/java/com/foggyframework/core/utils/file/WatchServiceFileTracer.java`
- `foggy-core/src/test/java/com/foggyframework/core/utils/file/WatchServiceFileTracerTest.java`
- `scripts/v934/step4/coverage-thresholds.json`
- `scripts/verify-v934-step4-coverage.sh`
- `docs/9.3.4/evidence/step-4/step4-coverage-formal-r1-watchservice-shutdown-race-fail-closed-20260717.md`
