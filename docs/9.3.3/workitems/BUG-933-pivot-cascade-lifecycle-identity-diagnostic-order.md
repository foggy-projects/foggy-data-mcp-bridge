---
type: bug
bug_source: regression-found
version: 9.3.3
ticket: BUG-933-PIVOT-DIAGNOSTIC-ORDER
severity: major
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: pivot-lifecycle-cache
---

# BUG Work Item

## Background

Batch 7 SQLite 全量回归发现，`PivotCascadeGenerateSqlParityIntegrationTest#testRowsTwoLevelCascadeSubset`
在 9.3.3 lifecycle identity fail-closed 接入后变红。查询结果和级联 SQL parity 仍完成，但
`pivot.cache.refused.reason` 从用例原先断言的 `cascade_shape` 变成
`lifecycle_identity_incomplete`。

## Reproduction

1. 使用 SQLite profile 执行 `foggy-dataset-model` 全量测试。
2. 运行两层 rows TopN cascade parity 场景。
3. 读取响应中的 `pivot.cache.refused` diagnostic。
4. 当前用例期望 `cascade_shape`，实际先报告 `lifecycle_identity_incomplete`。

复现命令：

```bash
mvn -B -pl foggy-dataset-model '-P!multi-db' \
  -Dspring.profiles.active=sqlite -DskipITs=true test \
  -l target/v933-batch7-diagnostics/sqlite-full/maven.log
```

复现结果：3448 tests / 1 failure / 0 errors / 3 skipped；470 个 fresh Surefire XML。

## Expected vs Actual

- Expected：拒绝原因优先级必须有稳定契约；测试 fixture 应提供完整 lifecycle identity，或用例应明确断言 identity fail-closed 的优先级，不能因历史隐式身份缺口产生偶然结果。
- Actual：级联 SQL parity 用例依赖旧的 request-shape diagnostic，但其 JDBC 模型解析得到不完整 binding identity；9.3.3 逻辑优先返回 lifecycle refusal，导致全量回归失败。

## Impact Scope

- 阻断 Batch 7 SQLite 全量权威证据及最终签收。
- 不影响本次观察到的查询结果或 cascade SQL parity，但暴露 diagnostic 契约与测试 fixture 未对齐。
- 可能掩盖真实的 datasource binding identity 缺口；在确认前不得只机械改断言。

## Test Strategy

- 先保留 SQLite 全量 RED 作为回归证据。
- 检查真实 `CatalogResolution` 的 catalog/source/binding identity，判定 fixture 是否缺少受管 binding。
- focused GREEN 必须同时证明：cascade 请求仍 no cache lookup/store、identity 状态与 refusal reason 符合稳定优先级、SQL parity 不退化。
- 修复后重跑 SQLite 全量并保持唯一允许的 3 个 skip。

## Code Inventory

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotPipeline.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheTelemetry.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheStrongIdentity.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotCascadeGenerateSqlParityIntegrationTest.java`

## Fix Checklist

- [x] 保存 Batch 7 SQLite 全量 RED 与 fresh report 计数。
- [x] 确认 lifecycle identity 不完整的根因与 diagnostic 优先级契约。
- [x] 补充或修正 focused 回归断言。
- [x] focused GREEN。
- [x] SQLite 全量 GREEN，且 skip allowlist 精确为 3。
- [x] Batch 7 authority runner 回写。

## Verification

- RED（2026-07-14，confirmed）：
  - report：`foggy-dataset-model/target/surefire-reports/TEST-com.foggyframework.dataset.db.model.engine.pivot.PivotCascadeGenerateSqlParityIntegrationTest.xml`
  - failure：line 100，expected `cascade_shape` but was `lifecycle_identity_incomplete`。
  - aggregate：3448/1/0/3，470 fresh reports。
- GREEN（2026-07-14，focused）：
  - `PivotOuterCacheTelemetryTest`：4/0/0/0；新增 request-shape reason 优先、identity 独立 fail-closed 的直接契约。
  - `PivotCascadeGenerateSqlParityIntegrationTest#testRowsTwoLevelCascadeSubset`：1/0/0/0；同时证明 `cascade_shape`、identity=`incomplete`、无 cache lookup 与 SQL parity。
  - aggregate：5/0/0/0，`BUILD SUCCESS`。
- GREEN（2026-07-14，SQLite full diagnostic）：
  - command：`mvn -B -pl foggy-dataset-model '-P!multi-db' -Dspring.profiles.active=sqlite -DskipITs=true test`。
  - log：`target/v933-batch7-diagnostics/sqlite-full-green/maven.log`。
  - fresh aggregate：3449 tests / 470 reports / 0 failures / 0 errors / 3 skipped；`BUILD SUCCESS`。
  - exact skips：`CalculateMvpIntegrationTest#calculateFailsClosedForRuntimeUnsupportedDatabase`、`PivotCascadeGenerateSqlParityIntegrationTest#testMysql57RowsCascadeFailsClosedWithoutMemoryFallback`、`JavaQueryModelAggregateJoinSnapshotTest#shouldProduceSnapshot`。
- Batch 7 replacement authority（2026-07-14）：run
  `20260714T084351Z-3271604` 的 SQLite lane
  `3449 tests / 470 reports / F0/E0/S3`；skip 与冻结 allowlist 逐项一致；
  全 run 独立复核 `NO BLOCKER`。

## References

- `docs/9.3.3/requirement/P0-model-lifecycle-concurrency.md`
- `docs/9.3.3/test/model-lifecycle-concurrency-test-plan.md`
- `docs/9.3.3/evidence/batch-6/pivot-cache-generation-green-20260714.md`
