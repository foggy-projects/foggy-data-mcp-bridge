---
type: bug
bug_source: quality-gate-found
version: 9.3.3
ticket: BUG-933-WATCHER-REGISTER-SCAN-RACE
severity: critical
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: fsscript-model-lifecycle
---

# BUG Work Item

## Background

9.3.3 正式 implementation quality gate 发现，
`FsscriptFileChangeHandler.registerDirectoryTree` 先用一次 `Files.walk` 冻结
目录/文件列表，随后才逐目录注册 WatchKey。运行期新子目录回调进入这段逻辑时，
模型文件可能恰好在扫描快照完成后、对应目录 WatchKey 注册前创建。

## Reproduction

1. 父目录已由 external bundle 的 source authority 监听。
2. 创建新子目录，触发 `onFileCreated(directory)`。
3. 在该子目录完成第一次 `Files.walk`、但尚未成功注册 WatchKey 的确定性夹点创建
   `.qm`/`.tm` 文件。
4. 文件不在第一次快照中；创建时目录尚无 WatchKey，因此也没有后续 create event。
5. 观察 committed source revision 与 lifecycle event：当前实现可能永久漏掉该文件。

## Expected vs Actual

- Expected：目录 listener 建立后必须对注册窗口做 post-registration reconciliation；
  任一受管源文件要么由稳定扫描发现，要么由 WatchService event 发现，并且只提交一次。
- Actual：单次 scan-before-register 留有不可观测窗口，运行期 source mutation 可能丢失，
  catalog 继续使用旧 source revision。

## Impact Scope

- 直接影响 `SOURCE-COMMIT`、`EVENT-CONVERGENCE`、atomic refresh 与新查询 admission。
- 违反 `BUG-933-NEW-MODEL-WATCHER` 已冻结的“新子目录竞态不能漏 source mutation”契约。
- 已通过的 Batch 7 run `20260714T074009Z-3153871` 没有确定性夹点覆盖，不能用于最终签收。

## Test Strategy

- 先加入确定性夹点测试，在 scan/register 窗口创建模型文件并保存 RED。
- 修复采用 register-first + post-registration rescan/reconciliation；不得依赖 sleep。
- file watcher 注册失败必须 fail closed，不能把文件误记为已受管。
- GREEN 同时断言 exact namespace、source revision/event exactly once、共享 root 与 remove cleanup 不退化。
- 修复后重跑 watcher focused suite，并从头重放 Batch 7 authority。

## Code Inventory

- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/loadder/FsscriptFileChangeHandler.java`
- `foggy-core/src/main/java/com/foggyframework/core/utils/file/WatchServiceFileTracer.java`
- `foggy-fsscript/src/test/java/com/foggyframework/bundle/dynamic/DynamicBundleLifecycleTest.java`
- `scripts/verify-v933-batch7-regression.sh`

## Fix Checklist

- [x] 建立无 sleep 的确定性 RED。
- [x] 目录先成功注册 WatchKey，再进行 post-registration source reconciliation。
- [x] 文件 listener 注册结果可验证，失败时 unknown-scope commit/admission fail closed。
- [x] 同一文件的 scan/event 竞态保持 exactly-once source commit。
- [x] focused watcher/core suites GREEN。
- [x] Batch 7 authority 从头重放并独立复核。

## Verification

- quality finding（2026-07-14）：formal gate=`needs-fix-before-audit`；禁止进入 coverage audit。
- RED（2026-07-14）：watcher 修复前，core authority-loss 选择集
  `3 tests / F3 / E0 / S0`，fsscript scan/authority 选择集
  `3 tests / F3 / E0 / S0`；失败点分别落在未发出 loss callback、注册窗口漏提交和
  file watcher 注册失败未 fail-closed。
- GREEN（2026-07-14 16:38 CST）：
  `WatchServiceFileTracerTest` `11/F0/E0/S0`，
  `FsscriptFileChangeHandlerAuthorityTest` `4/F0/E0/S0`，
  `DynamicBundleLifecycleTest` `5/F0/E0/S0`，
  `DynamicBundleManagementTest` `16/F0/E0/S0`；合计
  `36 tests / 4 reports / F0 / E0 / S0`。
- 实现采用最多 8 轮的 register-first fixed-point：任一轮发现扫描开始前尚未 watched
  的目录，只注册目录并丢弃该轮 source snapshot；只有下一轮所有目录均已受监听时
  才注册并发布 sources。持续增长超限提交 `RECONCILIATION_LIMIT_EXCEEDED`
  unknown scope，不把不稳定快照当成功。
- 新增第二轮夹点覆盖：reconciliation 才发现新 child，且 source 在该 snapshot 后、
  child WatchKey 前出现；必须再扫描一次并 exactly-once commit。
- authority（2026-07-14）：replacement run `20260714T084351Z-3271604`；
  watcher lane `36 tests / 4 reports / F0/E0/S0`，全 run
  `3824/519/F0/E0/S3`；独立复算 XML、manifests、source/container 与制品均
  `NO BLOCKER`。旧 run `20260714T074009Z-3153871` 保持 superseded。

## References

- `docs/9.3.3/requirement/P0-model-lifecycle-concurrency.md`
- `docs/9.3.3/contract/model-lifecycle-concurrency-contract.md`
- `docs/9.3.3/workitems/BUG-933-new-model-file-watcher-lifecycle-gap.md`
- `docs/9.3.3/evidence/batch-7/batch-7-regression-exit-20260714.md`
- `docs/9.3.3/evidence/batch-7/batch-7-regression-exit-20260714-r2.md`
