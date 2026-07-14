---
type: bug
bug_source: quality-gate-found
version: 9.3.3
ticket: BUG-933-WATCHER-AUTHORITY-LOSS
severity: critical
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: fsscript-model-lifecycle
---

# BUG Work Item

## Background

正式 implementation quality gate 确认 `WatchServiceFileTracer` 在事件
`OVERFLOW` 时只记录日志，在 `WatchKey.reset()` 返回 false 时只移除 core map；
`FsscriptFileChangeHandler` 对目录删除也不建立 source-authority-loss 语义。

## Reproduction

1. 对 watch-enabled external bundle 注册递归目录 authority。
2. 触发指定目录的 `OVERFLOW`、invalid WatchKey 或已监听子目录删除。
3. 当前 core tracer 不向 source handler 发出 authority-lost callback。
4. source revision 不推进、unknown-scope event 不发布、catalog admission 不阻断。

## Expected vs Actual

- Expected：任何无法证明目录事件完整性的状态都必须产生明确 authority-loss
  信号；source owner 提交 unknown-scope mutation，使可能受影响 catalog fail closed。
- Actual：仅 log/remove/no-op，旧 catalog 可无限期保持 ACTIVE。

## Impact Scope

- 直接阻断 `SOURCE-COMMIT`、`EVENT-CONVERGENCE` 与 fail-closed version signoff。
- 与 `BUG-933-WATCHER-REGISTER-SCAN-RACE` 共用 watcher/source authority 修复面。

## Test Strategy

- core 层确定性模拟 overflow、invalid key 与 watched-directory deletion。
- fsscript 层断言每次 authority loss 形成 unknown-scope committed revision/event，
  重复信号幂等，shared root 的其他有效注册不被误删。
- callback/API 必须 additive；现有 `DirectoryChangeListener` 实现无需修改即可编译。
- 不使用 sleep；focused GREEN 后从头重放 Batch 7 authority。

## Code Inventory

- `foggy-core/src/main/java/com/foggyframework/core/utils/file/DirectoryChangeListener.java`
- `foggy-core/src/main/java/com/foggyframework/core/utils/file/WatchServiceFileTracer.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/loadder/FsscriptFileChangeHandler.java`
- corresponding core/fsscript tests

## Fix Checklist

- [x] 保存 authority-loss RED。
- [x] OVERFLOW 发出 additive loss signal。
- [x] invalid WatchKey 发出 loss signal 并安全清理。
- [x] watched-directory deletion 发出 loss signal 并清理子树。
- [x] source owner unknown-scope commit/admission fail closed，重复信号幂等。
- [x] focused GREEN。
- [x] fresh Batch 7 authority。

## Verification

- source proof（2026-07-14）：formal gate=`needs-fix-before-audit`。
- RED（2026-07-14）：core authority-loss 选择集
  `3 tests / F3 / E0 / S0`，fsscript scan/authority 选择集
  `3 tests / F3 / E0 / S0`。
- GREEN（2026-07-14 16:38 CST）：core + fsscript owning suites 合计
  `36 tests / 4 reports / F0 / E0 / S0`；其中 core `11`、authority `4`、
  lifecycle `5`、management `16`。
- `DirectoryChangeListener.onWatchAuthorityLost` 是 additive default method；
  `WatchServiceFileTracer` 在 cleanup 前捕获 listener，在 cleanup 后发出一次 loss signal，
  并按 authority epoch 对重复信号去重。
- fsscript source owner 对 loss root 强制撤销失效子树 watcher、提交 unknown-scope
  committed revision；共享 root 的其他有效注册保持可用。
- authority（2026-07-14）：replacement run `20260714T084351Z-3271604`；
  watcher lane `36 tests / 4 reports / F0/E0/S0`，全 run
  `3824/519/F0/E0/S3`；独立 authority audit `NO BLOCKER`。

## References

- `docs/9.3.3/workitems/BUG-933-watcher-register-scan-race.md`
- `docs/9.3.3/requirement/P0-model-lifecycle-concurrency.md`
- `docs/9.3.3/contract/model-lifecycle-concurrency-contract.md`
