---
type: bug
bug_source: regression-found
version: 9.3.3
ticket: BUG-933-MULTI-BINDING-RETIRE
severity: critical
status: closed
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: runtime-datasource-lifecycle
---

# BUG Work Item

## Background

Batch 7 实现质量预审发现，datasource registry 已提交新 generation 后，`ManagedDataSourcePoolManager` 会逐个 retire 同一 backend 的 named/namespace bindings。若某个 active binding 的 drain deadline 调度被 scheduler 拒绝，异常会中断循环，后续旧 binding 仍保持 current/open。

## Reproduction

1. 为同一 backend 建立至少两个 current binding，并各自持有 active lease。
2. 使用首次 `schedule()` 即抛出 `RejectedExecutionException` 的测试 scheduler。
3. 调用 `onRecordSaved` 触发 generation 变化与多 binding retire。
4. 当前实现从首个 `retireBinding` 抛出，后续旧 binding 未 retire，仍可通过旧 handle borrow。

## Expected vs Actual

- Expected：deadline 调度失败必须立即 hard revoke 当前 binding，并继续 fail-close 所有受影响 binding；registry mutation 返回后任何旧 handle 都不能再借出连接。
- Actual：调度异常向外传播并提前终止 retire 循环，形成已提交 registry generation 与仍可借用旧 backend 的分裂状态。

## Impact Scope

- 直接影响 9.3.3 `BINDING-REVOKE`、`DS-GENERATION` 与 fail-closed critical criteria。
- 影响 record save/remove 下同 backend 的 named 与 namespace binding。
- 正常 graceful drain 不受影响；缺陷仅在 scheduler failure，但该异常路径必须 fail-closed。

## Test Strategy

- unit-test（required）：fake pool + rejecting scheduler，构造多 binding active leases。
- 先证明当前实现抛异常且至少一个旧 binding 仍可 borrow，再修复为不抛、先关闭全部旧 binding admission；调度失败的 binding hard revoke，其他 binding 保持有界 drain，所有旧 handle 均拒绝新 borrow。
- 复跑完整 `RuntimeDatasourceBindingLifecycleTest` 与相关 manager tests。

## Code Inventory

- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/ManagedDataSourcePoolManager.java`
- `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/RuntimeDatasourceBindingLifecycleTest.java`

## Fix Checklist

- [x] 先补稳定失败测试并记录 RED。
- [x] 两阶段 retire 先关闭全部 affected binding admission，再执行可失败的 deadline schedule。
- [x] deadline schedule failure 仅 hard revoke 当前 binding，并继续处理其他 bounded-drain binding。
- [x] 验证所有旧 handle 均 fail-closed；rejected binding 的 held lease 立即关闭。
- [x] 复跑 focused 与 Batch 7 regression runner。

## Verification

- RED（2026-07-14，confirmed）：
  - command：`mvn -B -pl foggy-runtime-api -am '-P!multi-db' -Dtest='RuntimeDatasourceBindingLifecycleTest#rejectedDrainDeadlineRevokesEveryAffectedBindingWithoutFailingTheCommit' -Dsurefire.failIfNoSpecifiedTests=false test`
  - result：1 test / 1 failure / 0 errors / 0 skipped；`RejectedExecutionException` 从首个 schedule 穿透并中断批量 retire。
- GREEN（2026-07-14，focused）：
  - 同一单方法：1/0/0/0。
  - 完整 `RuntimeDatasourceBindingLifecycleTest`：11/0/0/0；named + tenant A/B 的 admission 先统一关闭，rejected slot hard revoke，其他 slot bounded drain 后关闭。
  - report：`foggy-runtime-api/target/surefire-reports/TEST-com.foggyframework.runtime.api.service.RuntimeDatasourceBindingLifecycleTest.xml`。
- Batch 7 replacement authority（2026-07-14）：run
  `20260714T084351Z-3271604` 的 binding publication/retire lane
  `16 tests / 2 reports / F0/E0/S0`；全 run `3824/519/F0/E0/S3` exact
  allowlist；独立复核 `NO BLOCKER`。

## References

- `docs/9.3.3/requirement/P0-model-lifecycle-concurrency.md`
- `docs/9.3.3/implementation-plan.md`
- `docs/9.3.3/evidence/batch-3/catalog-binding-exit-20260713.md`
- `docs/9.3.3/evidence/batch-6/batch-6-exit-20260714.md`
