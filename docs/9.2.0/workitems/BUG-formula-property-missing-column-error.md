---
type: bug
bug_source: upstream-feedback
version: 9.2.0
ticket: BUG-formula-property-missing-column-error
severity: minor
status: local-verified-awaiting-upstream
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
created_at: 2026-05-27
updated_at: 2026-06-13
upstream_issue: foggy-projects/foggy-data-mcp-bridge#85
owner_repo: foggy-data-mcp-bridge-wt-dev-compose
owner_module: foggy-dataset-model
---

# BUG: Formula Property Missing Column Error Message

## Background

TMS `OrderSettlementCandidateQuery` 使用 `FactOrderSettlementModel` 中的 formula-backed JSON 属性字段。字段缺少 `column` 时，TM 校验失败，但错误只输出完整模型对象：

```text
属性的column不能为空,模型：DbObjectSupport(name=FactOrderSettlementModel, caption=运单结算摘要, ...)
```

TMS 已通过给 formula-backed JSON 字段补 carrier column（如 `column: 'cost_settlement'`）绕过问题，但模型作者仍需要 Java engine 直接定位到具体字段。

## Reproduction

新增 ecommerce invalid fixture：

```js
{
    name: 'salesAmountFormulaLeafYuan',
    caption: '销售金额公式属性',
    type: 'MONEY',
    semanticScaleFactor: 100,
    formulaDef: {
        value: 'alias.sales_amount + 2'
    }
}
```

加载 `FactSalesFormulaPropertyMissingColumnInvalidModel` 可稳定复现。

## Expected vs Actual

Expected:

- 错误包含具体字段路径，例如 `FactSalesFormulaPropertyMissingColumnInvalidModel.salesAmountFormulaLeafYuan column不能为空`。
- 对 `formulaDef` / `dialectFormulaDef` 属性字段，错误明确说明当前仍必须声明 carrier column。

Actual before fix:

- 错误只包含模型对象 dump，无法直接定位缺失 `column` 的字段。

## Impact Scope

- 影响 TM / QM 开发阶段的模型校验体验。
- 不改变 query SQL 生成语义。
- 不放开 formula-only property；属性字段仍需要 carrier column，用于字段元数据、权限和物理列绑定。
- formula-only measure 已有单独规则，不在本次问题范围。

## Test Strategy

新增集成测试：

- `SemanticScaleFactorIntegrationTest#formulaPropertyMissingColumn_reportsFieldPathAndCarrierColumnRule`

断言：

- 异常包含模型与字段路径。
- 异常包含 `formulaDef/dialectFormulaDef`。
- 异常包含 `carrier column`。

## Code Inventory

| Path | Change |
|---|---|
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/loader/TableModelLoaderManagerImpl.java` | 在 loader 层基于 `DbPropertyDef` / dimension context 做 property column contract 校验，输出字段级路径和 formula carrier-column 规则 |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/property/DbPropertyImpl.java` | 更新兜底 `column` 校验，避免继续输出完整模型对象 |
| `foggy-dataset-model/src/test/resources/foggy/templates/ecommerce/model/FactSalesFormulaPropertyMissingColumnInvalidModel.tm` | 新增 invalid fixture |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/SemanticScaleFactorIntegrationTest.java` | 新增回归测试 |

## Fix Checklist

- [x] 记录 #85 为 9.2.0 bug workitem。
- [x] loader 层补字段级 `column` 缺失错误。
- [x] formula-backed property 缺 `column` 时补 carrier-column 规则提示。
- [x] `DbPropertyImpl` 兜底错误去掉模型对象 dump。
- [x] 新增 invalid fixture 和集成测试。
- [x] 当前 Java engine 源码本地复核通过。
- [x] 生成上游复测 handoff：`upstream-verification-handoff-20260606.md`。
- [ ] 等待 TMS 侧用 #85 场景复测确认。

## Verification

2026-05-27 Java engine 本地验证已执行：

```bash
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' '-Dtest=SemanticScaleFactorIntegrationTest#formulaPropertyMissingColumn_reportsFieldPathAndCarrierColumnRule'
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' '-Dtest=SemanticScaleFactorIntegrationTest'
```

结果：

- Targeted missing-column diagnostic regression: passed, 1 test.
- Semantic scale / formula integration suite: passed, 27 tests.

关键错误信息：

```text
FactSalesFormulaPropertyMissingColumnInvalidModel.salesAmountFormulaLeafYuan column不能为空；formulaDef/dialectFormulaDef 字段必须声明 carrier column，用于字段元数据、权限和物理列绑定
```

2026-06-06 当前源代码复核已执行：

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite '-Dtest=AggregateJoinQueryModelTest,SemanticScaleFactorIntegrationTest,InlineExpressionPreprocessStepTest,AdvancedAnalyticsTest#testQmPredefinedFormulaFieldReferencedOnlyBySlice,PivotIntegrationTest#testQmPredefinedFormulaMetricInPivotFlat+testQmPredefinedFormulaInPivotTopLevelSlice+testQmPredefinedFormulaInPivotAxisHavingAndOrderBy+testQmPredefinedFormulaMetricInPivotGrandTotal' test
```

结果：

- 组合验证通过；`Tests run: 68, Failures: 0, Errors: 0, Skipped: 0`。
- `SemanticScaleFactorIntegrationTest` 覆盖 formula-backed property missing-column 诊断路径。

2026-06-13 当前源码复核已执行：

```bash
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' '-Dtest=SemanticScaleFactorIntegrationTest#formulaPropertyMissingColumn_reportsFieldPathAndCarrierColumnRule,InlineExpressionPreprocessStepTest#injectsPredefinedCalculatedFieldReferencedOnlyBySliceWithoutColumns+injectsPredefinedCalculatedFieldReferencedByFieldReferenceValue,AdvancedAnalyticsTest#testQmPredefinedFormulaFieldReferencedOnlyBySlice,AggregateJoinQueryModelTest#aggregateRelationOnLeftKeyShouldSupportJoinedDimensionField+aggregateRelationOnLeftKeyShouldSupportNestedDimensionPath+aggregateRelationRhsFixedFilterShouldSupportRightDimensionField'
```

结果：

- 组合验证通过；`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`。
- `foggy-projects/foggy-data-mcp-bridge#85` 仍未收到 TMS pass/fail 回执，因此状态保持 `local-verified-awaiting-upstream`。

2026-06-18 upstream audit:

- `gh issue view 85 -R foggy-projects/foggy-data-mcp-bridge` still reports
  #85 as `OPEN`, updated at `2026-06-13T04:41:07Z`.
- Local workspace still has no TMS/query-cloud-service checkout, so this repo
  cannot replace the required real TMS pass/fail confirmation.
- Keep this workitem as `local-verified-awaiting-upstream`.

## References

- GitHub issue: `foggy-projects/foggy-data-mcp-bridge#85`
- Related TMS commit: `c9fbe9a5 feat: stabilize order settlement subject fields`
- Upstream verification handoff: `upstream-verification-handoff-20260606.md`
