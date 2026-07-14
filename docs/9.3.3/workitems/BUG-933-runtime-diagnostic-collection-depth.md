---
type: bug
bug_source: quality-gate-found
version: 9.3.3
ticket: BUG-933-RUNTIME-DIAGNOSTIC-COLLECTION-DEPTH
severity: major
status: closed
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: runtime-model-api
---

# BUG Work Item

## Background

`RuntimeLifecycleSanitizer` 对嵌套 Map 在 depth 5 截断，但 Collection 分支没有
统一 depth guard。由 List/Collection 组成的极深 hostile diagnostics 会递归到 JVM
栈上限，而不是稳定截断为安全公开结构。

## Expected vs Actual

- Expected：Map 与 Collection 共享统一 composite depth 上限，输入深度、宽度和
  文本长度都受约束；脱敏过程不能因 hostile diagnostics 栈溢出。
- Actual：纯 Collection 链绕过 Map 的 depth cutoff。

## Impact Scope

- 影响 Runtime public error envelope 的防御性稳定性。
- 不改变成功路径或 lifecycle authority；与 Batch 7 Runtime API lane 一并重放。

## Test Strategy

- 建立深层 List/Collection deterministic RED，禁止只断言不抛异常。
- 使用同一 composite depth 常量截断 Map/Collection；保留 nested null 与基础类型。
- 直接单测 safe depth、beyond-limit、宽度限制与 legacy validation nullability。

## Code Inventory

- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/dto/RuntimeLifecycleSanitizer.java`
- `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/dto/RuntimeLifecycleSanitizerTest.java`

## Fix Checklist

- [x] 保存 deterministic RED。
- [x] Map/Collection 使用统一 composite depth bound。
- [x] nested null、primitive、sanitization 兼容不退化。
- [x] focused Runtime sanitizer/API suites GREEN。
- [x] fresh Batch 7 authority。

## Verification

- source proof（2026-07-14）：formal quality finding=medium；本轮选择修复而非携带风险。
- RED（2026-07-14 16:24 CST）：
  - 命令：`mvn -pl foggy-runtime-api -DskipTests=false -DskipUnitTests=false -Dtest=RuntimeLifecycleSanitizerTest test`
  - 结果：`Tests run: 4, Failures: 2, Errors: 0, Skipped: 0`。
  - `deeplyNestedPureCollectionsStopAtUnifiedCompositeDepth` 以
    `StackOverflowError` 失败；`mapsAndCollectionsShareOneCompositeDepthBudget`
    以第 5 层仍保留 `must-be-truncated` 失败。nested null/primitive 与宽度
    兼容用例在 RED 阶段通过。
- GREEN focused（2026-07-14 16:25 CST）：同一命令得到
  `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`。
- GREEN compatibility/Controller（2026-07-14 16:25 CST）：
  - 命令：`mvn -pl foggy-runtime-api -DskipTests=false -DskipUnitTests=false -Dtest=RuntimeLifecycleSanitizerTest,RuntimeLifecycleSafetyContractTest,RuntimeModelsControllerCompatibilityTest,RuntimeCapabilitiesControllerEnabledTest,RuntimeCapabilitiesControllerDisabledTest test`
  - 结果：`Tests run: 58, Failures: 0, Errors: 0, Skipped: 0`；其中 sanitizer
    `4`、safety contract `3`、enabled Controller `48`、disabled Controller `1`、
    Runtime Models compatibility `2`。
- 实现：root diagnostics Map 从 depth `0` 起计数；Map 与 Collection 共用
  `MAX_COMPOSITE_DEPTH=5` 和 `MAX_COMPOSITE_WIDTH=100`，到界分别返回空 Map
  或空 List；安全基础值及 nested null 保持原样。
- hygiene：repository `git diff --check` 通过；三个新增/未跟踪目标文件分别以
  `git diff --no-index --check /dev/null <file>` 校验，无 whitespace error 输出。
- authority（2026-07-14）：replacement run `20260714T084351Z-3271604`；
  API lane `62 tests / 6 reports / F0/E0/S0`，全 run
  `3824/519/F0/E0/S3`，独立 raw-XML/manifest audit `NO BLOCKER`。

## References

- `docs/9.3.3/workitems/BUG-933-runtime-model-controller-full-suite-regression.md`
- `docs/9.3.3/requirement/P0-model-lifecycle-concurrency.md`
