---
type: bug
bug_source: formal-gate-found
version: 9.3.4
ticket: BUG-934-STEP4-QUERY-MODEL-JOIN-GRAPH-DOUBLE-CHECK-COVERAGE-RACE
severity: blocker
status: closed
closed_at: 2026-07-18
closure_evidence: docs/9.3.4/acceptance/step4-coverage-gate-acceptance.md
reproduction_status: confirmed
product_regression: false
test_strategy: controlled-deterministic-concurrency-unit-regression
automation_decision: required
owner: step4-coverage
---

# Step 4 QueryModel JoinGraph double-check 覆盖并发竞态

## Background

fresh formal `step4-coverage-20260717-formal-r3` 在 clean/pushed Cfreeze commit
`a63c82c53ebaad1a1c22d78647fbda70b4bd6594` 上完成全部测试与证据 lane，随后在
`formal-coverage-gate` 以 `E_FORMAL_LOW` fail closed。immutable failure evidence：

- `docs/9.3.4/evidence/step-4/step4-coverage-formal-r3-query-model-join-graph-double-check-race-fail-closed-20260717.md`

该 run 的 success-only `summary.env`、`coverage-gate.json`、`candidate-manifest.json` 与
`final-manifest.json` 均未保留；source before/after SHA-256 同为
`7054bfc200f78ce2d5412aaf7016252d02a98c69960f3660fdb58196df93336c`，cleanup=`0/0/0`、
sensitive scan=`passed`，外层 wrapper 已成功恢复四个 demo database container。

## Expected vs Actual

- Expected：fresh formal 必须稳定达到 reviewed aggregate exact threshold：line=
  `54624/76830`、branch=`26111/44870`。
- Actual：line exact 命中；branch=`26110/44870`，只少一个 covered outcome。
- Actual：required inventory=`773+59/5707/F0E0S0`，Addon=`2/6`，exec/session/classes=
  `23/48/16946`，class universe=`24/2098`；全部 critical class 通过，below-floor=`0`。
- Actual：逐 class 对比只有 `QueryModelSupport` 变化；逐 method/line 对比只有
  `getMergedJoinGraph` 的 line 316 内层 DCL condition 变化：r16=`0 missed / 2 covered`，
  formal-r3=`1 missed / 1 covered`；method 总 branch 则为 `0/4 -> 1/3`（missed/covered）。

## Root Cause

`QueryModelSupport#getMergedJoinGraph` 使用 volatile field 与 double-checked locking。内层
`mergedJoinGraph != null` outcome 只有在至少两个线程都先观察到外层 null、随后由一个线程完成初始化、
另一个线程再取得 monitor 时才会覆盖。r16 的既有并发执行偶然形成了该交错；formal-r3 没有形成。

production source/class tree、测试库存、23 exec / 48 sessions 和全部 critical rows 均稳定，且 line
coverage 不变。因此这是 coverage oracle 依赖 incidental concurrent scheduling 的测试确定性缺陷，
不是 DCL 产品逻辑回归。

## Fix Strategy

1. 在既有 Unit testcase 内增加受控并发 regression，使用全新的 `QueryModelSupport` 实例；不得改动冻结
   数据库矩阵保护树或 testcase identity。
2. 让第一调用者进入 synchronized 初始化并阻塞在受控 `TableModel#getJoinGraph` build 窗口；启动第二
   调用者后，以 `ThreadMXBean` 确认其已通过外层 null check，并以第一调用者为 owner 阻塞在同一实例
   monitor，再释放 build。
3. 确保第一调用者构建并发布 `mergedJoinGraph`，第二调用者取得 monitor 时确定性观察 inner non-null；
   断言两者返回同一 graph、root 正确且 build count=`1`。
4. 复用或扩展既有 deterministic concurrency test support，所有等待必须有 timeout；`finally` 中取消未完成
   future，并断言 executor terminated，避免用 sleep/timing oracle 换一种方式制造波动。
5. 只改测试/测试支撑，不修改 production DCL，不降低 aggregate/critical threshold，不扩大 exclusion。
6. focused 多 fresh Maven/JVM/JaCoCo fork 验证 line 316 branch bitmap 稳定后，完整重走 Cdiag -> fresh
   diagnostic -> threshold review -> direct-child Cfreeze -> fresh formal。

## Regression Test Decision

`automation_decision=required`。这是 exact aggregate gate 的 blocker：不补确定性自动化，就无法区分真实
coverage regression 与线程调度波动，也无法让 formal authority 可复现。focused 结果只关闭修复方向，
不能替代 all-lane diagnostic 或 formal authority。

## Fix Checklist

- [x] formal-r3 failure、success-only artifact absence、source seal、cleanup、sensitive scan 与 DB restoration
  已确认。
- [x] required/Add-on 库存、exec/session、class universe 和全部 critical rows 已确认完整。
- [x] aggregate delta 已定位为 branch `-1`，line 无变化。
- [x] 唯一 class/method/source line 已定位到 `QueryModelSupport#getMergedJoinGraph` line 316。
- [x] 根因确认为 inner DCL false outcome 依赖 incidental concurrent scheduling。
- [x] 在既有
  `RuntimeNamedDataSourceResolverBindingTest#publicationGuardSerializesTheCallbackWithAConcurrentRebind`
  增加 controlled deterministic concurrency regression；无新增/改名 `@Test`，无 production 或
  threshold 变更；targeted=`1/F0E0S0`。
- [x] focused 5/5 fresh Maven/JVM fork PASS；`QueryModelSupport` class id=
  `d242dafe9de31249`、`34/629 probes`、packed bitmap unique=`1`；Runtime API module=
  `128/F0E0S0`，successor protected overlay 前后 PASS。
- [x] machine 恢复 `diagnostic-ready/diagnostic-pending`；formal-r3 recovery 时点的 exact
  hashes 只作为历史 pre-Cdiag snapshot，不再冒充 current identity。
- [x] pre-Cdiag formal implementation quality PASS，B/H/M/L=`0/0/0/0`；记录：
  `docs/9.3.4/quality/step4-formal-r3-recovery-implementation-quality.md`。
- [x] Cdiag `316a71f753827f8f34063b0eb0669271f696c5ee` 已 commit/push/clean，并被 fresh r17
  消耗；r17 因无关的 Unit MySQL final-server handoff infrastructure race 在 Maven/coverage 前
  fail closed，不能证明本 BUG 的 all-lane remediation。
- [x] handoff remediation pre-Cdiag formal quality=`PASS / 0/0/0/0`；记录：
  `docs/9.3.4/quality/step4-diagnostic-r17-recovery-implementation-quality.md`。
- [x] 形成 replacement Cdiag commit/push/clean identity。
- [x] fresh diagnostic 全 lane PASS，aggregate exact 不低于 `54624/76830 line, 26111/44870 branch`。
- [x] threshold candidate/review PASS，direct-child Cfreeze commit/push。
- [x] fresh formal PASS，随后完成最终 implementation quality、coverage audit 与 acceptance。

## Closure Scope

本 BUG 在 formal-r3 后保持 `in-progress`；后续 deterministic regression、fresh diagnostic、review、
formal-r4 与 feature acceptance 已全部通过，本 workitem 现已关闭。formal-r3 永久保留为 immutable
failed evidence，不复用、不修补；r17 同样永久 excluded，且不能把无关基础设施先失败解释为
QueryModel all-lane PASS 或 FAIL。

## References

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/QueryModelSupport.java`
- `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/RuntimeNamedDataSourceResolverBindingTest.java`
- `scripts/v934/step4/coverage-thresholds.json`
- `scripts/verify-v934-step4-coverage.sh`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r16-pass-20260717.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r16-threshold-review-20260717.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-formal-r3-query-model-join-graph-double-check-race-fail-closed-20260717.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r17-unit-mysql57-final-mysqld-handoff-fail-closed-20260717.md`
- `docs/9.3.4/workitems/BUG-step4-unit-mysql57-final-mysqld-handoff-readiness-race.md`
- `docs/9.3.4/quality/step4-diagnostic-r17-recovery-implementation-quality.md`
- `docs/9.3.4/quality/step4-formal-r3-recovery-implementation-quality.md`
