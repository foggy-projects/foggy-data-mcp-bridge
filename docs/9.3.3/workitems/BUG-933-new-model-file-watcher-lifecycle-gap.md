---
type: bug
bug_source: regression-found
version: 9.3.3
ticket: BUG-933-NEW-MODEL-WATCHER
severity: critical
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: fsscript-model-lifecycle
---

# BUG Work Item

## Background

Batch 7 实现质量预审发现，外部 bundle 在运行期新增 `.qm` 文件时没有进入 9.3.3 的 source commit 与 scoped refresh 权威链。旧 MCP resolver 仍保留目录监听代码，但 `init()` 不再注册监听，且回调只调用已经是 no-op 的 MCP cache invalidation。

## Reproduction

1. 以 `watch=true` 注册一个文件系统 external bundle。
2. 在 bundle 已启动后新建一个 `.qm` 文件（包括新建子目录后再创建文件的场景）。
3. 观察 `WatchServiceFileTracer` 的目录 listener、`CommittedSourceRevisionRegistry` 和 `FsscriptRemoveEvent`。
4. 当前没有 source-owner 目录 listener；不会提交 revision，也不会发布带 canonical namespace 的 lifecycle event。

自动化复现测试将在 `foggy-fsscript` 的 dynamic bundle lifecycle 测试中先形成稳定 RED，再实施修复。

## Expected vs Actual

- Expected：`watch=true` 的 external bundle 对运行期新增模型文件进行递归目录监听；文件创建先提交 source revision，再发布 `scopeKnown=true`、namespace 与 affected resource 完整的事件；bundle remove 后目录与文件 watcher 一并释放。
- Actual：只有已加载文件的 file watcher；新文件没有任何 source mutation，MCP 的残留目录回调即使触发也只执行 no-op。

## Impact Scope

- 影响 9.3.3 `EVENT-CONVERGENCE`、`SOURCE-COMMIT`、`REFRESH-SCOPE` critical criteria。
- 影响 9.3.2 dynamic model discovery 回归。
- 新 `.qm` 可能在进程重启前始终不可见；已有 catalog 也无法按 namespace 收敛。
- 删除/修改已加载文件的既有链路不等同于新增文件链路，不能作为替代证据。

## Test Strategy

- integration-test（required）：使用真实 `WatchService`、有界等待/事件同步，不使用固定 sleep。
- 覆盖 `watch=true`、新子目录、新 `.qm`、committed revision、known namespace、affected resource，以及 bundle remove 后 watcher 清理。
- 保留既有已加载文件修改/删除和 `watch=false` 行为。

## Code Inventory

- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/loadder/FsscriptFileChangeHandler.java`
- `foggy-fsscript/src/main/java/com/foggyframework/bundle/SystemBundlesContextImpl.java`
- `foggy-fsscript/src/test/java/com/foggyframework/bundle/dynamic/DynamicBundleLifecycleTest.java`
- `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/spi/impl/SemanticServiceResolverImpl.java`
- `foggy-core/src/main/java/com/foggyframework/core/utils/file/WatchServiceFileTracer.java`

## Fix Checklist

- [x] 先补稳定失败的新增文件回归测试并记录 RED。
- [x] 将目录 watcher 所有权收敛到 fsscript/source authority。
- [x] 对已有目录递归注册，并处理运行期新子目录。
- [x] 新模型文件先加入 file watcher，再进行 known-scope source commit/event。
- [x] external bundle remove 同时释放目录与文件 watcher。
- [x] 移除 MCP resolver 的失效目录监听残留，禁止双 authority。
- [x] focused GREEN 后执行 Batch 7 regression runner。

## Verification

- RED（2026-07-14，confirmed）：
  - command：`mvn -B -pl foggy-fsscript '-P!multi-db' -Dtest='DynamicBundleLifecycleTest#watchEnabledExternalBundleMustCommitExactSourceEventForNewQmInNewSubdirectory' test`
  - report：`foggy-fsscript/target/surefire-reports/TEST-com.foggyframework.bundle.dynamic.DynamicBundleLifecycleTest.xml`
  - result：1 test / 1 failure / 0 errors / 0 skipped；8 秒有界等待内未收到新 `.qm` 的 committed source event。
- GREEN（2026-07-14，focused）：
  - `DynamicBundleLifecycleTest`：5 tests / 0 failures / 0 errors / 0 skipped，覆盖新子目录竞态、exact known scope/revision、`watch=false`、共享 root 引用计数与 remove 清理。
  - `DynamicBundleManagementTest`：16/0/0/0，包含 watcher 注册失败后的 bundle registry 完整回滚。
  - `WatchServiceFileTracerTest`：8/0/0/0；底层 WatchKey 注册失败会回滚 listener，不再伪报成功。
  - compile：`foggy-fsscript` + `foggy-dataset-mcp` 及依赖 reactor 9/9 success。
- Batch 7 replacement authority（2026-07-14）：run
  `20260714T084351Z-3271604` 的 watcher/source management lane
  `36 tests / 4 reports / F0/E0/S0`；全 run `3824/519/F0/E0/S3` exact
  allowlist；独立复核 `NO BLOCKER`。

## References

- `docs/9.3.3/requirement/P0-model-lifecycle-concurrency.md`
- `docs/9.3.3/implementation-plan.md`
- `docs/9.3.3/evidence/batch-5/atomic-refresh-exit-20260714.md`
- `docs/9.3.3/evidence/batch-6/batch-6-exit-20260714.md`
