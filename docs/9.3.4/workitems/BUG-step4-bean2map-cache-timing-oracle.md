---
type: bug
bug_source: diagnostic-found
version: 9.3.4
ticket: BUG-934-STEP4-BEAN2MAP-CACHE-TIMING-ORACLE
severity: blocker
status: closed
post_gate_confirmed_at: 2026-07-18
post_gate_evidence: docs/9.3.4/acceptance/step4-coverage-gate-acceptance.md
reproduction_status: confirmed
product_regression: false
test_strategy: deterministic-behavior-regression
automation_decision: required
owner: step4-unit-authority
---

# Step 4 Bean2Map cache 单次计时 oracle 波动

## Background

fresh diagnostic `step4-coverage-20260717-diagnostic-r15` 在 clean/pushed Cdiag
`9270d2d4e58684226aeb15eff55b027e6aa4a7eb` 上进入完整 Unit replacement 后，
`Bean2MapUtilsTest#testCachingMechanism` 以单次纳秒计时断言失败。outer 在 `child-unit`
fail closed，source-after、report inventory、exec manifest、aggregate、observation、summary 与
sensitive-scan receipt 均未发布；run-owned cleanup=`0/0/0`。编排会话在 evidence window 外
另行恢复并观察四个 exact demo DB 为原 ID、running/healthy；该观察不是 r15 runner-owned
immutable evidence。

immutable failure evidence：

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r15-bean2map-timing-oracle-fail-closed-20260717.md`。

## Expected vs Actual

- Expected：Surefire correctness authority 不得以 JVM/JIT/GC/OS/JaCoCo 调度敏感的单次墙钟
  采样判定缓存是否正确。
- Actual：原测试要求 `secondCallTime <= firstCallTime * 3`；r15 观测 first=
  `26,839ns`、second=`304,859ns`、third=`8,517ns`，阈值=`80,517ns`，因此
  `23 tests / 1 failure / 0 errors / 0 skipped`。
- Actual：同一测试类中的 `testPerformanceWithManyObjects` 还以 `1000 次 < 1000ms` 作为
  Surefire 门，属于同类潜在环境噪声风险。

## Root Cause

`Bean2MapUtils.PROPERTY_CACHE` 是静态 `ConcurrentHashMap`，只通过 `computeIfAbsent` 写入。
r15 的 `testCachingMechanism` 执行前，同一 `SourceBean` / `TargetBean` class 已被多个测试调用，
其中 `testPerformanceWithManyObjects` 又复制 1000 次。因此所谓第一次、第二次、第三次实际
全部是 cache hit，原测试没有建立 cold->warm 边界。

两个单次纳秒采样的倍率只反映调度/JIT/GC/safepoint/JaCoCo 插桩噪声；third 立即回落到
`8,517ns` 也不符合持续缓存失效。该断言既没有证明 cache entry identity，也不是业务
correctness 或稳定性能 SLA，属于 test oracle 缺陷，不是 product regression。

## Fix Strategy

1. 保留既有 `testCachingMechanism` testcase 名称与节点数，不引入反射、test seam 或生产 API。
2. 使用三个同类、不同 source 实例连续复制；每个实例使用不同 `name/age`，分别断言三个 target
   保留各次调用时的值，证明 metadata cache 不得缓存实例数据。
3. 删除单次 `nanoTime`、println 与倍率断言；缓存性能若需 SLA，后续应使用独立 JMH multi-fork
   benchmark/telemetry，不进入 Surefire correctness 门。
4. 同步将既有 `testPerformanceWithManyObjects` 从墙钟性能断言改为 1000 次批量 correctness：
   保留循环和首末结果断言，删除 `currentTimeMillis`、输出与 `<1000ms` threshold。
5. 不修改 production、public API、POM、runner、coverage floor/critical/exclusion；不增加或
   删除 testcase。
6. 通过 10 个 fresh JVM focused forks、完整 23-test class 与完整 27-test module 验证，再形成
   新 Cdiag 并从头运行 fresh diagnostic。

## Regression Test Decision

`automation_decision=required`：r15 已在 clean all-lane authority 中复现。修复必须保留自动化
correctness，而不是简单删除测试或扩大计时容忍；同时移除同源的 1000ms 潜在时序门，避免
下一代 run 再因机器负载产生伪红/碰运气绿色。

## Fix Checklist

- [x] r15 run/status/absence/cleanup 已确认；四 demo DB restoration 仅作为 evidence-window 外
  编排观察记录，不归因于 r15 runner-owned evidence。
- [x] failure method、source line、first/second/third 与 exact threshold 已从 sealed run.log 复核。
- [x] 根因确认为已预热 cache 上的单次计时噪声，不是生产缓存失效。
- [x] `testCachingMechanism` 改为三个同类、不同 source 实例的 deterministic behavior oracle。
- [x] 邻接 1000-copy test 移除墙钟门，保留批量 correctness。
- [x] focused 10/10 fresh JVM=`1/F0E0S0`；class=`23/F0E0S0`；module=`27/F0E0S0`。
- [x] 完成正式 pre-Cdiag implementation quality，B/H/M/L=`0/0/0/0`。
- [x] 完成一次 Cdiag commit/push 与 clean identity：
  `f863c672029d5d1e5a4903df74cf6cba22a04a85`。
- [x] fresh r16 diagnostic sealed PASS：`773+59/5707/F0E0S0`、exec/session/class IDs=
  `23/48/16948`、aggregate=`54624/76830 line,26111/44870 branch`；Bean2MapUtils 关键回归
  coverage 无退化。
- [x] candidate/review 已通过：candidate=
  `2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919`、review=
  `88b99e76e5584d3cd17bcdcffd138f1fe6655ce0b7795d3430d2f15a018c8fb3`，
  B/H/M/L=`0/0/0/1`。
- [x] Cfreeze machine worktree 已为 threshold `confirmed` / contract `formal-ready`：
  `ca6a25c66fbbe9a595adde74f1b7589bd3829b93edebfd5b11dc394ab8d088c8` /
  `6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`。
- [x] direct-child Cfreeze commit/push 与 fresh formal 通过；review 的 Low 必须由 fresh formal
  复现 aggregate，不得降低 threshold。
- [x] 最终 implementation quality、coverage audit 与 acceptance 通过。

## Closure Scope

本 BUG 按 implementation regression closed / diagnostic verified 关闭：r16 已在完整 Unit 与
all-lane diagnostic 中证明 deterministic behavior oracle 稳定，且 Bean2MapUtils 关键回归
coverage 无退化。
该 implementation closure 当时不等于 fresh formal 或 Step 4 accepted；后续 formal-r4、最终质量、
coverage audit 与 feature acceptance 已完成。9.3.4 version gate 仍由 Steps 5–7 承接。

## References

- `foggy-bean-copy/src/main/java/com/foggyframework/bean/copy/utils/Bean2MapUtils.java`
- `foggy-bean-copy/src/test/java/com/foggyframework/bean/copy/utils/Bean2MapUtilsTest.java`
- `scripts/verify-v934-unit.sh`
- `scripts/verify-v934-step4-coverage.sh`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r15-bean2map-timing-oracle-fail-closed-20260717.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r16-pass-20260717.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r16-threshold-review-20260717.md`
