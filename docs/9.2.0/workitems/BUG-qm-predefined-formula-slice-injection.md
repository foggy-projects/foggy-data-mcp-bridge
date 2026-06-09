---
type: bug
bug_source: upstream-feedback
version: 9.2.0
ticket: BUG-qm-predefined-formula-slice-injection
severity: major
status: local-verified-awaiting-upstream
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
created_at: 2026-05-29
updated_at: 2026-06-06
owner_repo: foggy-data-mcp-bridge-wt-dev-compose
owner_module: foggy-dataset-model
---

# BUG: QM Predefined Formula Field Not Injected When Referenced By Slice

## Background

Upstream reported that a QM predefined `formula` field can be loaded into `QueryModelSupport.predefinedCalculatedFields`, but querying with the field only in backend `slice` fails:

```text
未能找到列[availablePieceCount]
```

Example field:

```js
{
  name: 'availablePieceCount',
  caption: '可配件数',
  type: 'DOUBLE',
  formula: 'GREATEST(COALESCE(number, 0) - COALESCE(plannedPieceCount, 0), 0)'
}
```

Example query shape:

```json
{
  "slice": [
    { "field": "availablePieceCount", "op": ">", "value": 0 }
  ]
}
```

## Reproduction

The issue is stable when:

- QM has a predefined formula field.
- Request omits `columns`, or the formula field is not referenced by `columns`.
- Request references that field from `slice`.

Before the fix:

- `InlineExpressionPreprocessStep` returned early when `columns` was empty, before injecting predefined calculated fields.
- `JdbcModelQueryEngine.injectPredefinedCalculatedFields()` only scanned `queryRequest.columns`.
- `buildSingleCondition()` could not resolve the field and raised `未能找到列[...]`.

## Expected vs Actual

Expected:

- QM predefined formula fields behave as model fields when referenced by query request clauses.
- At minimum, fields referenced by `slice` are injected into `queryRequest.calculatedFields` before condition SQL is built.

Actual before fix:

- Only `columns` references were reliably injected.
- `slice`-only requests failed during condition building.

## Impact Scope

- Affects Java QueryModel / JDBC query engine.
- Blocks backend filtering, paging, and total statistics for predefined formula fields when callers do not explicitly request the formula column.
- Relevant request locations include `slice`, `having`, `postSlice`, `orderBy`, `groupBy`, and `$field` right-side references.
- Does not change formula parsing semantics; it only changes when trusted QM predefined calculated fields are injected into the request.

## Test Strategy

Automation is required because the bug is stable, query-engine-level, and easy to regress.

Regression coverage:

- Unit-level preprocessing:
  - `InlineExpressionPreprocessStepTest#injectsPredefinedCalculatedFieldReferencedOnlyBySliceWithoutColumns`
  - `InlineExpressionPreprocessStepTest#injectsPredefinedCalculatedFieldReferencedByFieldReferenceValue`
- Integration-level direct engine path:
  - `AdvancedAnalyticsTest#testQmPredefinedFormulaFieldReferencedOnlyBySlice`
- Pivot follow-up coverage:
  - `PivotIntegrationTest#testQmPredefinedFormulaMetricInPivotFlat`
  - `PivotIntegrationTest#testQmPredefinedFormulaInPivotTopLevelSlice`
  - `PivotIntegrationTest#testQmPredefinedFormulaInPivotAxisHavingAndOrderBy`
  - `PivotIntegrationTest#testQmPredefinedFormulaMetricInPivotGrandTotal`

## Code Inventory

| Path | Change |
|---|---|
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/PredefinedCalculatedFieldInjector.java` | 新增共享注入器，统一扫描 request 引用并注入 QM predefined calculated fields |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/InlineExpressionPreprocessStep.java` | 将预定义字段注入提前到 `columns` 空判断前，并复用共享注入器 |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java` | 直连 `analysisQueryRequest` 路径复用共享注入器 |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/InlineExpressionPreprocessStepTest.java` | 新增 slice-only 与 `$field` 引用注入单测 |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/AdvancedAnalyticsTest.java` | 新增 direct engine slice-only 预定义公式字段集成测试 |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotIntegrationTest.java` | 新增 Pivot 高级场景覆盖测试，用于识别 semantic/pivot 链路剩余缺口 |

## Fix Checklist

- [x] 建立失败测试复现 `slice` 引用预定义 formula 字段不注入。
- [x] 抽出共享 `PredefinedCalculatedFieldInjector`，避免预处理 step 与 JDBC engine 注入逻辑分叉。
- [x] 扫描 `columns`、`slice`、`having`、`postSlice`、`orderBy`、`groupBy`。
- [x] 递归扫描 `$and` / `$or` 条件组。
- [x] 扫描条件右侧 `$field` 引用。
- [x] 保留同名用户自定义 calculatedField 被 QM 预定义字段替换时的 warning。
- [x] 定向回归测试通过。
- [x] 2026-05-29 曾运行根目录全量 `mvn test`；当时被既有 aggregate join fixture 数据断言失败阻断，未观察到本回归新增失败。
- [x] Pivot 顶层 `slice` 引用聚合型 QM formula 时，需避免将聚合表达式下推到 SQL `WHERE`。
- [x] Pivot grandTotal/subtotal rollup 分析需在进入 `MetricAdditivityAnalyzer` 前获得 QM 预定义 calculatedFields。
- [x] 当前 Java engine 源码本地复核通过。
- [x] 生成上游复测 handoff：`upstream-verification-handoff-20260606.md`。
- [ ] 等待上游用 `OrderStationStockProjectionQuery.availablePieceCount` 场景复测确认。

## Verification

2026-05-29 本地定向验证：

```bash
mvn -pl foggy-dataset-model "-Dtest=InlineExpressionPreprocessStepTest#injectsPredefinedCalculatedFieldReferencedOnlyBySliceWithoutColumns+injectsPredefinedCalculatedFieldReferencedByFieldReferenceValue,AdvancedAnalyticsTest#testQmPredefinedFormulaFieldReferencedOnlyBySlice" test
```

Result:

- `Tests run: 3, Failures: 0, Errors: 0`
- Generated SQL expands `profitRate` into its predefined formula in `where`.

2026-05-29 根目录全量验证：

```bash
mvn test
```

Result:

- Failed in existing `AggregateJoinQueryModelTest` fixture assertions.
- Failure reason: test data did not contain an order with `COMPLETED` sales detail.
- No failure was observed in the new predefined formula injection regression tests.

2026-05-29 Pivot follow-up 定向验证：

```bash
mvn -pl foggy-dataset-model "-Dtest=PivotIntegrationTest#testQmPredefinedFormulaMetricInPivotFlat+testQmPredefinedFormulaInPivotTopLevelSlice+testQmPredefinedFormulaInPivotAxisHavingAndOrderBy+testQmPredefinedFormulaMetricInPivotGrandTotal" test
```

Result:

- `Tests run: 4, Failures: 0, Errors: 2`
- Passed:
  - `testQmPredefinedFormulaMetricInPivotFlat`
  - `testQmPredefinedFormulaInPivotAxisHavingAndOrderBy`
- Failed:
  - `testQmPredefinedFormulaInPivotTopLevelSlice`: grouped Pivot query expands `profitRate` to `SUM(...)` expression but leaves it in SQL `WHERE`, causing SQLite `misuse of aggregate: SUM()`.
  - `testQmPredefinedFormulaMetricInPivotGrandTotal`: rollup additivity analysis sees `profitRate` as aggregation `NONE` because Pivot pre-analysis has not received the QM predefined calculated field.

2026-05-29 Pivot follow-up 修复后定向验证：

```bash
mvn -pl foggy-dataset-model "-Dtest=PivotIntegrationTest#testQmPredefinedFormulaMetricInPivotFlat+testQmPredefinedFormulaInPivotTopLevelSlice+testQmPredefinedFormulaInPivotAxisHavingAndOrderBy+testQmPredefinedFormulaMetricInPivotGrandTotal" test
```

Result:

- `Tests run: 4, Failures: 0, Errors: 0`
- Verified Pivot flat metric, top-level `slice`, axis `having/orderBy`, and `grandTotal` all receive QM predefined calculated fields before query analysis.

2026-05-29 原始回归 + Pivot 补测组合验证：

```bash
mvn -pl foggy-dataset-model "-Dtest=InlineExpressionPreprocessStepTest#injectsPredefinedCalculatedFieldReferencedOnlyBySliceWithoutColumns+injectsPredefinedCalculatedFieldReferencedByFieldReferenceValue,AdvancedAnalyticsTest#testQmPredefinedFormulaFieldReferencedOnlyBySlice,PivotIntegrationTest#testQmPredefinedFormulaMetricInPivotFlat+testQmPredefinedFormulaInPivotTopLevelSlice+testQmPredefinedFormulaInPivotAxisHavingAndOrderBy+testQmPredefinedFormulaMetricInPivotGrandTotal" test
```

Result:

- `Tests run: 7, Failures: 0, Errors: 0`
- Verified the original slice-only regression path and Pivot advanced paths pass together.

2026-06-06 当前源代码复核：

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite '-Dtest=AggregateJoinQueryModelTest,SemanticScaleFactorIntegrationTest,InlineExpressionPreprocessStepTest,AdvancedAnalyticsTest#testQmPredefinedFormulaFieldReferencedOnlyBySlice,PivotIntegrationTest#testQmPredefinedFormulaMetricInPivotFlat+testQmPredefinedFormulaInPivotTopLevelSlice+testQmPredefinedFormulaInPivotAxisHavingAndOrderBy+testQmPredefinedFormulaMetricInPivotGrandTotal' test
```

Result:

- `Tests run: 68, Failures: 0, Errors: 0, Skipped: 0`。
- 2026-05-29 记录的 aggregate join fixture 阻断已由 2026-06-06 MySQL ecommerce aggregate fixture 修复消除；本次组合验证同时覆盖 `AggregateJoinQueryModelTest`。

## References

- Upstream report: QM predefined formula field referenced only in `slice` raises `未能找到列[...]`.
- Related runtime stack: `JdbcModelQueryEngine.buildSingleCondition` -> `buildSlice` -> `analysisQueryRequest`.
- Upstream verification handoff: `upstream-verification-handoff-20260606.md`
