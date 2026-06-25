---
type: optimization
source_type: module-review
version: 9.2.1
ticket: P1-fsscript-runtime-stability-resource-boundary
priority: P1
severity: major
status: signed-off
owner: foggy-fsscript
owner_module: foggy-fsscript
automation_decision: required
created_at: 2026-06-25
updated_at: 2026-06-25
---

# P1: FSScript 运行时稳定性与资源边界优化

## 文档作用

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: 记录 foggy-fsscript 模块自身的运行时稳定性、资源路径边界、动态卸载清理和打包兼容性优化范围。

## Background

对 `foggy-fsscript` 模块 review 后，除管理接口暴露与鉴权策略外，模块自身仍存在若干运行时风险：

- classpath 资源路径识别依赖 `ClassPathResource#getFile()`，在 fat jar 或嵌套 jar 部署下可能失败。
- bundle、fsscript 缓存和资源索引存在普通 `ArrayList` / `HashMap` 与非同步读写混用。
- 动态 bundle 卸载时未完整清理 file watcher、root loader cache 和依赖缓存。
- FSScript import 相对路径未约束在 bundle root 内，影响 bundle 封装边界和发布可迁移性。
- 非文件资源缓存 key 使用 URL path，可能丢失 scheme、host 或 jar 上下文。
- 加载失败处理存在 `catch(Throwable)`、递归重试和 `printStackTrace()`，定位成本高。

## Problem Statement

当前实现能覆盖常规本地开发和测试场景，但在以下场景存在稳定性隐患：

- Spring Boot fat jar / nested jar 部署读取内置模型或脚本资源。
- 运行中动态添加、移除、重载外部 bundle。
- 多线程同时执行脚本加载、资源查询和 bundle 列表查询。
- bundle 内脚本使用相对 import，迁移后依赖到 bundle 根目录外的资源。
- 非本地文件资源作为 bundle root 或脚本资源时发生缓存 key 冲突。

## Target Outcome

- classpath、jar、file、普通 URL 资源都能生成稳定且唯一的 resource identity。
- bundle root 内资源解析保持边界清晰，默认拒绝逃逸 bundle root 的相对 import。
- 动态 bundle 的 add/remove/list/load 并发路径具备一致性，不依赖未同步集合读写。
- bundle 卸载时清理对应 watcher、脚本缓存和依赖索引。
- 加载失败路径不吞掉关键异常，不使用递归重试掩盖根因。
- 现有 FSScript 语法和公开 Java API 尽量保持兼容。

## Scope

### In Scope

- `foggy-fsscript` 资源路径与缓存 key 规则。
- `BundleImpl`、`ExternalFileBundle`、`SystemBundlesContextImpl` 的动态 bundle 读写一致性。
- `RootFsscriptLoader`、`FsscriptFileChangeHandler` 的缓存和 watcher 生命周期。
- `ResourceFsscriptClosureDefinitionSpace` 与 `DefaultResourceFinder` 相关的相对 import 边界。
- 加载失败日志和异常处理收敛。
- 单元测试与必要的集成测试补充。

### Out Of Scope

- `/api/bundles` 管理接口是否暴露、鉴权策略、外部控制面治理。
- FSScript 沙箱能力、`@bean` / `java:` / 反射调用权限模型。
- QueryModel、Pivot、Compose DSL 语义变更。
- 前端或用户体验页面改动。

## Touched Code Areas

- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/closure/file/ResourceFsscriptClosureDefinitionSpace.java`
- `foggy-core/src/main/java/com/foggyframework/core/utils/resource/DefaultResourceFinder.java`
- `foggy-fsscript/src/main/java/com/foggyframework/bundle/BundleImpl.java`
- `foggy-fsscript/src/main/java/com/foggyframework/bundle/external/ExternalFileBundle.java`
- `foggy-fsscript/src/main/java/com/foggyframework/bundle/SystemBundlesContextImpl.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/loadder/RootFsscriptLoader.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/loadder/FsscriptFileChangeHandler.java`
- `foggy-core/src/main/java/com/foggyframework/core/utils/file/WatchServiceFileTracer.java`
- `foggy-fsscript/src/test/java/**`

## Task Split / Ownership

| Step | Task | Owner Module | Status |
|---|---|---|---|
| 1 | 设计统一 resource identity 规则，覆盖 file、classpath、jar、URL 资源 | `foggy-fsscript` | done |
| 2 | 为相对 import 增加 bundle root containment 校验和负例测试 | `foggy-fsscript` / `foggy-core` | done |
| 3 | 替换或保护动态 bundle 与 fsscript cache 的并发集合访问 | `foggy-fsscript` | done |
| 4 | 在 bundle remove 路径清理 watcher、root loader cache 和依赖缓存 | `foggy-fsscript` / `foggy-core` | done |
| 5 | 收敛加载失败处理，移除 `printStackTrace()` 和无界递归重试 | `foggy-fsscript` | done |
| 6 | 补齐测试证据并更新必要模块手册 | `foggy-fsscript` | done |

## Acceptance Criteria

- fat jar / nested jar 场景下，classpath FSScript 资源不依赖 `Resource#getFile()` 才能生成缓存 key。
- 不同 scheme、host、jar entry 或 classpath location 的资源不会因相同 URL path 共用缓存。
- 相对 import 使用 `../`、绝对路径、Windows drive path 或跨 root 解析时明确拒绝，并有测试覆盖。
- 动态 add/remove/list/load 并发测试不出现 `ConcurrentModificationException`、脏缓存或丢失 bundle 定义。
- remove external bundle 后，相关 watcher 和 fsscript cache 不再保留可触发的旧资源条目。
- 加载失败保留原始异常上下文，不通过递归重试造成重复日志或堆栈风险。
- `mvn -pl foggy-fsscript test` 通过。

## Test Strategy

- Add unit tests for resource identity generation:
  - file resource canonical path.
  - classpath resource inside test classes.
  - jar-like or URL resource identity with scheme and host retained.
- Add negative tests for import containment:
  - `../outside.fsscript`
  - absolute filesystem path.
  - Windows drive style path where applicable.
  - `file:` location when resolving from a bundle script.
- Add concurrency tests around:
  - add/remove/list external bundle.
  - load script while bundle is removed.
  - root loader cache read/write under repeated reload.
- Add lifecycle tests for watcher cleanup where feasible.
- Re-run `mvn -pl foggy-fsscript test`.

## Constraints / Non-Goals

- Do not change public FSScript syntax unless a compatibility note is added and accepted.
- Do not change management endpoint exposure in this workitem.
- Do not introduce a new external dependency unless the existing JDK/Spring resource APIs are insufficient.
- Do not move long-term manuals into `docs/9.2.1`; version docs should link to module docs when needed.

## Required Review / Audit / Acceptance Workflow

- Implementation self-check is required after code changes.
- `foggy-implementation-quality-gate` is recommended before test coverage audit because this touches runtime caches and resource loading.
- `foggy-test-coverage-audit` is required before acceptance if concurrency or resource boundary behavior changes.
- `foggy-acceptance-signoff` should sign off this workitem after tests and review evidence are recorded.

## Progress Tracking

### Development Progress

| Item | Status | Notes |
|---|---|---|
| Workitem recorded | done | Initial review risks captured for 9.2.1. |
| First red tests added | done | Added expected-behavior tests for resource identity, import containment, and dynamic bundle cache cleanup. |
| Resource identity implementation | done | File resources use canonical file path; non-file resources use full URL identity, covering jar/classpath and URL origin differences. |
| Import containment implementation | done | Relative imports resolved to file resources are checked against the owning bundle root. |
| Concurrent cache and bundle registry hardening | done | `SystemBundlesContextImpl` now protects bundle registry and definitions with a read-write lock, returns bundle list snapshots, and `RootFsscriptLoader` protects cache reads/writes with synchronized snapshots. Bundle resource name indexes use `ConcurrentHashMap`. |
| Bundle remove watcher/cache cleanup | done | External bundle removal now clears bundle resource index, `RootFsscriptLoader` cache entries, and file watcher entries under the removed bundle root. |
| Error handling cleanup | done | Primary runtime paths no longer catch `Throwable`; `Error` propagation is restored for script eval, bean import, script import, count loops, try/catch expression handling, expression utility evaluation, and conversion helper paths. All `printStackTrace()` references were removed from `foggy-fsscript/src/main/java`; bundle IO/debug failures now use structured logger output. |

### Testing Progress

| Test Area | Status | Evidence |
|---|---|---|
| Existing baseline | pass | `mvn -pl foggy-fsscript test`, 346 tests passed during review. |
| Resource identity tests | pass | `ResourceFsscriptClosureDefinitionSpaceTest` covers jar classpath identity without `Resource#getFile()` and URL host/scheme identity. |
| Import containment negative tests | pass | `FsscriptImportBoundaryTest` covers within-root relative import and rejects `../../outside.fsscript`. |
| Bundle remove cache cleanup test | pass | `DynamicBundleLifecycleTest#removeExternalBundleShouldClearLoadedFsscriptCache` verifies root loader cache removal after external bundle removal. |
| Concurrent add/remove/list tests | pass | `DynamicBundleManagementTest` covers `getBundleList()` snapshot behavior and concurrent `listExternalBundles()` while removing an external bundle. |
| Watcher cleanup tests | pass | `DynamicBundleLifecycleTest#removeExternalBundleShouldUnwatchLoadedFsscriptFiles` verifies watcher entries are removed after external bundle removal. |
| Loading failure handling tests | pass | `BundleLoadFailureTest` covers stale cached path reload and serious cached-loader error propagation for `BundleImpl` and `ExternalFileBundle`. |
| Runtime failure handling tests | pass | `FsscriptRuntimeFailureTest` and `ImportBeanExpFailureTest` cover serious `Error` propagation instead of wrapping it as `RuntimeException`. |

### Red Test Evidence

Command:

```bash
mvn -pl foggy-fsscript "-Dtest=ResourceFsscriptClosureDefinitionSpaceTest,FsscriptImportBoundaryTest,DynamicBundleLifecycleTest" test
```

Result on 2026-06-25:

- Tests run: 5
- Failures: 4
- Errors: 0
- Skipped: 0

Expected pre-fix failures:

- `ResourceFsscriptClosureDefinitionSpaceTest#classpathResourceInsideJarShouldNotRequireFile`
- `ResourceFsscriptClosureDefinitionSpaceTest#urlResourceIdentityShouldIncludeSchemeAndHost`
- `FsscriptImportBoundaryTest#relativeImportShouldNotEscapeBundleRoot`
- `DynamicBundleLifecycleTest#removeExternalBundleShouldClearLoadedFsscriptCache`

### First Batch Fix Evidence

Changed paths:

- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/closure/file/ResourceFsscriptClosureDefinitionSpace.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/loadder/RootFsscriptLoader.java`
- `foggy-fsscript/src/main/java/com/foggyframework/bundle/SystemBundlesContextImpl.java`
- `foggy-fsscript/src/test/java/com/foggyframework/fsscript/closure/file/ResourceFsscriptClosureDefinitionSpaceTest.java`
- `foggy-fsscript/src/test/java/com/foggyframework/fsscript/closure/file/FsscriptImportBoundaryTest.java`
- `foggy-fsscript/src/test/java/com/foggyframework/bundle/dynamic/DynamicBundleLifecycleTest.java`

Verification on 2026-06-25:

```bash
mvn -pl foggy-fsscript "-Dtest=ResourceFsscriptClosureDefinitionSpaceTest,FsscriptImportBoundaryTest,DynamicBundleLifecycleTest" test
```

- Tests run: 5
- Failures: 0
- Errors: 0
- Skipped: 0

```bash
mvn -pl foggy-fsscript "-Dtest=DynamicBundleManagementTest,DynamicBundleLifecycleTest" test
```

- Tests run: 14
- Failures: 0
- Errors: 0
- Skipped: 0

```bash
mvn -pl foggy-fsscript test
```

- Tests run: 351
- Failures: 0
- Errors: 0
- Skipped: 0

### Second Batch Red Test Evidence

Command:

```bash
mvn -pl foggy-fsscript "-Dtest=DynamicBundleManagementTest" test
```

Result on 2026-06-25 before the concurrency fix:

- Tests run: 15
- Failures: 2
- Errors: 0
- Skipped: 0

Expected pre-fix failures:

- `DynamicBundleManagementTest#testGetBundleListReturnsSnapshot`
- `DynamicBundleManagementTest#testListExternalBundlesDuringRemoveShouldNotFailFast`

### Second Batch Fix Evidence

Changed paths:

- `foggy-fsscript/src/main/java/com/foggyframework/bundle/SystemBundlesContextImpl.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/loadder/RootFsscriptLoader.java`
- `foggy-fsscript/src/main/java/com/foggyframework/bundle/BundleImpl.java`
- `foggy-fsscript/src/main/java/com/foggyframework/bundle/external/ExternalFileBundle.java`
- `foggy-fsscript/src/test/java/com/foggyframework/bundle/dynamic/DynamicBundleManagementTest.java`

Implementation notes:

- `SystemBundlesContextImpl` protects bundle list and bundle definition mutations with a `ReentrantReadWriteLock`.
- `getBundleList()` returns a snapshot so callers cannot mutate the internal registry accidentally.
- `listExternalBundles()` works on a bundle snapshot to avoid fail-fast iteration during remove.
- `RootFsscriptLoader` synchronizes cache reads and returns snapshots for external inspection.
- `BundleImpl` and `ExternalFileBundle` use `ConcurrentHashMap` for filename-to-path resource indexes.

Verification on 2026-06-25:

```bash
mvn -pl foggy-fsscript "-Dtest=DynamicBundleManagementTest" test
```

- Tests run: 15
- Failures: 0
- Errors: 0
- Skipped: 0

```bash
mvn -pl foggy-fsscript "-Dtest=DynamicBundleManagementTest,NamespaceBundleTest,RootFsscriptLoaderTest,DynamicBundleLifecycleTest" test
```

- Tests run: 23
- Failures: 0
- Errors: 0
- Skipped: 0

```bash
mvn -pl foggy-fsscript test
```

- Tests run: 353
- Failures: 0
- Errors: 0
- Skipped: 0

```bash
mvn -pl foggy-dataset-model,foggy-dataset-mcp -am -DskipTests compile
```

- Result: build success
- Purpose: compile-smoke downstream `SystemBundlesContext#getBundleList()` consumers after snapshot behavior was introduced.

### Third Batch Red Test Evidence

Command:

```bash
mvn -pl foggy-fsscript "-Dtest=BundleLoadFailureTest,DynamicBundleLifecycleTest" test
```

Result on 2026-06-25 before the watcher and loading-failure fixes:

- Tests run: 6
- Failures: 5
- Errors: 0
- Skipped: 0

Expected pre-fix failures:

- `BundleLoadFailureTest#bundleImplShouldReloadWhenCachedPathNoLongerHasLoadedScript`
- `BundleLoadFailureTest#externalFileBundleShouldReloadWhenCachedPathNoLongerHasLoadedScript`
- `BundleLoadFailureTest#bundleImplShouldNotSwallowSeriousLoaderErrorsFromCachedPath`
- `BundleLoadFailureTest#externalFileBundleShouldNotSwallowSeriousLoaderErrorsFromCachedPath`
- `DynamicBundleLifecycleTest#removeExternalBundleShouldUnwatchLoadedFsscriptFiles`

### Third Batch Fix Evidence

Changed paths:

- `foggy-core/src/main/java/com/foggyframework/core/utils/file/FileTracer.java`
- `foggy-core/src/main/java/com/foggyframework/core/utils/file/WatchServiceFileTracer.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/loadder/FsscriptFileChangeHandler.java`
- `foggy-fsscript/src/main/java/com/foggyframework/bundle/SystemBundlesContextImpl.java`
- `foggy-fsscript/src/main/java/com/foggyframework/bundle/BundleImpl.java`
- `foggy-fsscript/src/main/java/com/foggyframework/bundle/external/ExternalFileBundle.java`
- `foggy-fsscript/src/test/java/com/foggyframework/bundle/BundleLoadFailureTest.java`
- `foggy-fsscript/src/test/java/com/foggyframework/bundle/dynamic/DynamicBundleLifecycleTest.java`

Implementation notes:

- `WatchServiceFileTracer` and legacy `FileTracer` now expose root-scoped removal APIs for watcher/listener cleanup.
- `FsscriptFileChangeHandler` removes watched files under a removed external bundle root; it keeps a reflective fallback so `foggy-fsscript` remains compatible when compiled against an older installed `foggy-core`.
- `SystemBundlesContextImpl#removeBundle` now clears file watchers in addition to bundle indexes and root-loader cache.
- `BundleImpl` and `ExternalFileBundle` now evict stale cached script paths and reload through bundle resources once, without recursive retry or `printStackTrace()`.
- Serious cached-loader errors are no longer caught as `Throwable`; they propagate to preserve failure context.

Verification on 2026-06-25:

```bash
mvn -pl foggy-fsscript "-Dtest=BundleLoadFailureTest,DynamicBundleLifecycleTest" test
```

- Tests run: 6
- Failures: 0
- Errors: 0
- Skipped: 0

```bash
mvn -pl foggy-core test
```

- Tests run: 91
- Failures: 0
- Errors: 0
- Skipped: 0

```bash
mvn -pl foggy-fsscript test
```

- Tests run: 358
- Failures: 0
- Errors: 0
- Skipped: 0

```bash
mvn -pl foggy-dataset-model,foggy-dataset-mcp -am -DskipTests compile
```

- Result: build success
- Purpose: compile-smoke downstream modules after `foggy-core` watcher APIs and `foggy-fsscript` bundle lifecycle behavior changed.

### Fourth Batch Red Test Evidence

Command:

```bash
mvn -pl foggy-fsscript "-Dtest=FsscriptRuntimeFailureTest,ImportBeanExpFailureTest" test
```

Result on 2026-06-25 before the broad runtime exception cleanup:

- Tests run: 2
- Failures: 2
- Errors: 0
- Skipped: 0

Expected pre-fix failures:

- `FsscriptRuntimeFailureTest#evalShouldNotWrapSeriousErrors`
- `ImportBeanExpFailureTest#beanMethodInvocationShouldNotWrapSeriousErrors`

### Fourth Batch Fix Evidence

Changed paths:

- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/support/FsscriptImpl.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/exp/ImportBeanExp.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/exp/ImportFsscriptExp.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/exp/NCountExp.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/exp/TryCatch.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/utils/ExpUtils.java`
- `foggy-fsscript/src/main/java/com/foggyframework/conversion/ConversionUtils.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/DefaultExpEvaluator.java`
- `foggy-fsscript/src/main/java/com/foggyframework/bundle/BundleImpl.java`
- `foggy-fsscript/src/main/java/com/foggyframework/bundle/loader/BundleLoader.java`
- `foggy-fsscript/src/main/java/com/foggyframework/bundle/SystemBundlesContextImpl.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/parser/ElExpScanner.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/parser/Examples.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/parser/ExpScanner.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/fun/SleepFunDef.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/exp/UnresolvedFunCall.java`
- `foggy-fsscript/src/test/java/com/foggyframework/fsscript/support/FsscriptRuntimeFailureTest.java`
- `foggy-fsscript/src/test/java/com/foggyframework/fsscript/exp/ImportBeanExpFailureTest.java`

Implementation notes:

- `FsscriptImpl`, `ImportFsscriptExp`, `NCountExp`, `TryCatch`, `ExpUtils`, and `ConversionUtils` catch runtime failures instead of broad `Throwable`, so serious JVM or assertion errors are not hidden by runtime wrapping.
- `ImportBeanExp` unwraps `InvocationTargetException`; target `Error` values propagate directly, while target runtime failures keep the existing runtime-exception conversion behavior.
- `SystemBundlesContextImpl#startReg` no longer catches broad `Throwable` around bundle loading.
- `BundleImpl`, `BundleLoader`, `DefaultExpEvaluator`, and parser/debug paths now use logger or runtime conversion instead of direct `printStackTrace()`.
- Comment-only `printStackTrace()` examples were removed from main source so source scans stay actionable.

Verification on 2026-06-25:

```bash
mvn -pl foggy-fsscript "-Dtest=FsscriptRuntimeFailureTest,ImportBeanExpFailureTest" test
```

- Tests run: 2
- Failures: 0
- Errors: 0
- Skipped: 0

```bash
mvn -pl foggy-fsscript "-Dtest=FsscriptRuntimeFailureTest,ImportBeanExpFailureTest,FsscriptImplTest,ImportBeanExpTest" test
```

- Tests run: 7
- Failures: 0
- Errors: 0
- Skipped: 0

```bash
mvn -pl foggy-fsscript test
```

- Tests run: 360
- Failures: 0
- Errors: 0
- Skipped: 0

```bash
rg -n "catch\s*\(\s*Throwable|printStackTrace\s*\(" foggy-fsscript/src/main/java
```

- Result: no matches

```bash
git diff --check
```

- Result: no whitespace errors; only existing LF/CRLF working-copy warnings.

```bash
mvn -pl foggy-dataset-model,foggy-dataset-mcp -am -DskipTests compile
```

- Result: build success
- Purpose: compile-smoke downstream modules after runtime exception propagation and conversion helper behavior changed.

### Experience Progress

experience: N/A

Reason: This workitem is backend/runtime only and does not add or change UI pages, forms, dialogs, buttons, or frontend data display behavior.

## Execution Checklist

- [x] Confirm resource identity compatibility rule before implementation.
- [x] Add failing tests for classpath/jar identity and import containment before or with fixes.
- [x] Replace unsafe collection access with a clear concurrency model.
- [x] Add bundle unload cleanup path and tests.
- [x] Add watcher cleanup and stale cached-load regression tests.
- [x] Remove recursive stale-cache reload handling from primary bundle load paths.
- [x] Remove broad `catch(Throwable)` and direct `printStackTrace()` usage from `foggy-fsscript/src/main/java`.
- [x] Run source scan for `catch(Throwable)` and `printStackTrace()`.
- [x] Update `foggy-fsscript/docs` only if public behavior or deployment guidance changes. No long-term module manual change was required because this work preserves public syntax/API and only records versioned runtime hardening here.
- [x] Run `mvn -pl foggy-core test`.
- [x] Run `mvn -pl foggy-fsscript test`.
- [x] Run downstream compile smoke for dataset consumers.
- [x] Record execution check-in with changed paths, test evidence, remaining risk, and acceptance readiness.

## Acceptance Readiness

status: signed-off

Reason: 第一批资源边界、第二批并发集合访问、第三批 watcher 生命周期与主 bundle 加载失败路径、第四批历史 `catch(Throwable)` / `printStackTrace()` 清理均已实现并通过测试。实现质量检查、测试覆盖审计和正式签收均已完成；安全管理接口暴露与鉴权策略仍按 out-of-scope 独立评估。

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-25
- acceptance_record: docs/9.2.1/acceptance/P1-fsscript-runtime-stability-resource-boundary-acceptance.md
- blocking_items: none
- follow_up_required: yes
