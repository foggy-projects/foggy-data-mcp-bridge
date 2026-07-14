---
type: bug
bug_source: regression-found
version: 9.3.3
ticket: BUG-933-RUNTIME-MODEL-CONTROLLER-FULL-SUITE
severity: critical
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: runtime-model-api
---

# BUG Work Item

## Background

Batch 7 authority run `20260714T070421Z-3070417` 的最终 package lane 因跳测参数与
根 POM 的 `skipUnitTests` 配置不一致而意外执行了 Runtime 全量测试。该意外执行
暴露 `RuntimeCapabilitiesControllerEnabledTest` 48 个用例中的 3 个红灯。虽然
package 只应作为编译/装配证据，这三个已知回归不能被忽略。

## Reproduction

失败报告：

```text
foggy-runtime-api/target/surefire-reports/
  TEST-com.foggyframework.runtime.api.RuntimeCapabilitiesControllerEnabledTest.xml
```

失败用例：

1. `shouldReturnModelValidateFailedWithDiagnosticsWhenValidationFails`：line 240，diagnostics 数组读取得到 null。
2. `shouldRefreshRequestedModelsThroughRuntimeEnvelope`：line 1747，期望 refresh result 成功但实际为 false。
3. `shouldReturnModelRefreshFailedWhenRequestedModelFails`：line 1775，期望 1 个失败项但实际为 0。

意外执行汇总：124 tests / 2 failures / 1 error / 0 skipped，Runtime 模块导致 reactor
`BUILD FAILURE`。权威 run fail-closed 停止，未更新 `latest-run-id`。

## Expected vs Actual

- Expected：Runtime model validate/refresh 的 legacy envelope、additive lifecycle 字段和 typed/sanitized error 在真实 Controller 全量回归中一致；package lane 使用 `skipUnitTests=true`，不把测试误记为装配证据。
- Actual：三个既有 Controller 用例与当前响应不一致；同时 package runner 的 `-DskipTests` 被根 POM 显式 `skipUnitTests=false` 配置覆盖，意外执行测试。

## Impact Scope

- 直接阻断 Batch 7 `API-COMPAT`、`REGRESSION` 和 root package exit。
- 可能影响 `/runtime/models/validate`、`/runtime/models/refresh` 旧消费者的 JSON 兼容。
- 前序 Batch 7 lanes（API focused、watcher、binding、REAL-QUERY、SQLite、三库、9.3.1、9.3.2）已绿，但本 run 整体只能作为 failed diagnostic。

## Test Strategy

- 对三个失败用例逐项确认当前 JSON 与 service result，禁止只改期望绕过产品错误。
- focused RED/GREEN 执行完整 `RuntimeCapabilitiesControllerEnabledTest`（48 tests）。
- 将该 owning suite 纳入 Batch 7 API regression inventory，避免 focused 4-suite 清单漏掉 Controller 全路径。
- package lane 同时设置 `skipUnitTests=true` 和 `skipTests`，并继续断言零 `Running` 行。
- 修复后从头重放 Batch 7 authority；不得拼接 failed run 的前序 lanes。

## Code Inventory

- `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/RuntimeCapabilitiesControllerEnabledTest.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/controller/RuntimeModelsController.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/RuntimeApiResponseFactory.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/RuntimeModelOperations.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/dto/RuntimeLifecycleSanitizer.java`
- `scripts/verify-v933-batch7-regression.sh`

## Fix Checklist

- [x] 保存 failed authority run、三个失败与未更新 latest 的证据。
- [x] 确认三个失败的共同根因及兼容契约。
- [x] 先形成稳定 focused RED，再实施最小修复。
- [x] 完整 Controller owning suite GREEN（48/0/0/0）。
- [x] Batch 7 API inventory 纳入该 suite 并冻结新总数。
- [x] package lane 使用项目真实跳测属性，且 authority 证明零测试执行。
- [x] Batch 7 authority 全量重放 GREEN。

## Verification

- RED（2026-07-14，confirmed）：
  - run：`target/v933-batch7-regression/runs/20260714T070421Z-3070417/`。
  - package log：`lanes/root-package-artifacts/maven.log`。
  - `RuntimeCapabilitiesControllerEnabledTest`：48 tests / 2 failures / 1 error / 0 skipped。
  - run status：failed at `12-root-package-artifacts`；`latest_run_id_updated=false`。
- 根因（2026-07-14）：
  - validate 与 refresh failure 将原有结构化
    `diagnostics.attributes.validation/refresh` 替换为 lifecycle-only 属性，违反
    Runtime DTO 仅 additive 的冻结契约；
  - Controller 测试仍桩旧 `QueryModelLoader` clear/warmup 路径，而当前生产入口已正确改由
    `CatalogRefreshCoordinator` 原子刷新；
  - package lane 只传 `skipTests`，被根 POM 的 `skipUnitTests=false` 插值覆盖。
- GREEN（2026-07-14，focused）：原始三个失败方法 3/0/0/0。
- GREEN（2026-07-14，完整与 supporting）：
  - `RuntimeCapabilitiesControllerEnabledTest`：48/0/0/0；
  - `RuntimeModelRefreshLifecycleTest`：4/0/0/0；
  - `RuntimeModelValidationIsolationTest`：2/0/0/0；
  - `RuntimeModelsControllerCompatibilityTest`：2/0/0/0；
  - 合计 56/0/0/0，`BUILD SUCCESS`。
- 修复：保留 coordinator 原子刷新与 no-clear/no-warmup；使用基础
  `Map/List/String/Number/null` 恢复经过 sanitizer 的 legacy validation/refresh
  结构，并保留新增 lifecycleCode/lifecycle；sanitizer 保持 nested null，不把旧 nullability
  改写为空字符串。
- runner：API inventory 已冻结 58 tests / 5 reports；package 同时传
  `-DskipUnitTests=true -DskipTests`。
- Batch 7 replacement authority（2026-07-14）：run
  `20260714T084351Z-3271604`；API lane
  `62 tests / 6 reports / F0/E0/S0`；package lane 25-module success 且零
  `Running ...Test`；全 run `3824/519/F0/E0/S3` exact allowlist；独立复核
  `NO BLOCKER`。旧 run `20260714T074009Z-3153871` 已 superseded。

## References

- `docs/9.3.3/requirement/P0-model-lifecycle-concurrency.md`
- `docs/9.3.3/implementation-plan.md`
- `docs/9.3.3/evidence/batch-5/atomic-refresh-exit-20260714.md`
