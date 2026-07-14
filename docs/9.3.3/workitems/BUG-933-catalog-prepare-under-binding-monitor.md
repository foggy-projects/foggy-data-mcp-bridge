---
type: bug
bug_source: regression-found
version: 9.3.3
ticket: BUG-933-CATALOG-PREPARE-MONITOR
severity: major
status: closed
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: model-lifecycle-catalog
---

# BUG Work Item

## Background

Batch 7 实现质量预审确认 `CatalogRefreshCoordinator.publish` 将 `CandidateScope.commit` 整体放在 datasource binding publication guard 内。candidate 校验、alias/map freeze、排序和依赖图校验因此占用 Runtime/MCP datasource mutation monitor，catalog 越大，configure/rebind/save/remove 的等待越长。

## Reproduction

1. 使用持有可观测 mutation monitor 的 `DatasourceBindingResolver.publishIfCurrent`。
2. candidate 中放入会在 snapshot validation 时检查 `Thread.holdsLock(mutationMonitor)` 的 QueryModel。
3. 修复前 validation 在 publication callback 内执行，确定性观察到 monitor 被持有。
4. 同时验证不能通过简单移锁牺牲 binding/source/base/store currentness 终检。

## Expected vs Actual

- Expected：immutable snapshot prepare/freeze 在 binding mutation monitor 外；guard 内只做 binding currentness、source/base/store final currentness 和 atomic swap。
- Actual：完整 snapshot prepare 与 swap 都在 guard callback 内，扩大 mutation monitor 临界区。

## Impact Scope

- 影响 Runtime registry save/remove/rebind 与 MCP datasource configure/remove 的可用性和尾延迟。
- 未发现死锁或越代发布，正确性风险低于 critical；但 O(catalog + dependency graph) 工作不应占用 datasource mutation monitor。
- 仅收窄 coordinator 生产发布链；兼容直发路径不扩大本工单声明。

## Test Strategy

- unit-test（required）：确定性 monitor ownership 断言，不依赖 wall-clock 阈值。
- prepare 后 binding stale 必须拒绝 publication。
- prepare 后 source revision 或 base/store 变化必须在最终提交再次拒绝。

## Code Inventory

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/catalog/CatalogSnapshotStore.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/refresh/CatalogRefreshCoordinator.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/lifecycle/catalog/CatalogSnapshotStoreTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/lifecycle/refresh/CatalogRefreshCoordinatorBehaviorTest.java`

## Fix Checklist

- [x] 以 monitor ownership 断言建立失败基线。
- [x] 增加 request-local `prepareCommit`，在 binding guard 外 validate/freeze/seal。
- [x] guard 内保留 binding/source/base/store currentness 与 atomic swap。
- [x] 补 prepare 后 binding/source/base stale 的 fail-closed 回归。
- [x] 纳入 Batch 7 aggregate authority。

## Verification

- RED（2026-07-14）：focused baseline 13 tests / 2 failures，证明 snapshot validation 持有 mutation monitor。
- GREEN（2026-07-14）：核心 store/refresh 两类 31/0/0/0；catalog/refresh 契约族 41/0/0/0。
- Batch 7 replacement authority（2026-07-14）：run `20260714T084351Z-3271604`
  的 SQLite lane 包含 `CatalogSnapshotStoreTest` 18 +
  `CatalogRefreshCoordinatorBehaviorTest` 13 = owning core 31 tests / 2 reports；
  连同 candidate/coordinator/JDBC completeness 契约族为 41 tests / 5 reports，
  全部 GREEN。全 run `3824/519/F0/E0/S3` exact allowlist；独立复核
  `NO BLOCKER`。旧 run `20260714T074009Z-3153871` 已 superseded。

## References

- `docs/9.3.3/requirement/P0-model-lifecycle-concurrency.md`
- `docs/9.3.3/implementation-plan.md`
- `docs/9.3.3/progress/model-lifecycle-concurrency-progress.md`
