---
type: bug
bug_source: upstream-feedback
version: 9.2.0
ticket: BUG-aggregate-relation-joined-dimension-left-key
severity: major
status: upstream-verified
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
created_at: 2026-05-27
updated_at: 2026-06-13
upstream_issue: foggy-projects/foggy-data-mcp-bridge#84
owner_repo: foggy-data-mcp-bridge-wt-dev-compose
owner_module: foggy-dataset-model
---

# BUG: Aggregate Relation ON 左键支持已 join 维度字段和维度路径

## Background

TMS 侧 `ReceiptHeaderModel` 当前没有暴露根表 `srcId`，业务源单号来自已 join 维度字段 `stockHouse$srcId`。模型作者期望使用：

```js
rh.leftJoin(demandAgg).on(rh.stockHouse$srcId, demandAgg.srcId)
```

这样可以保持 TM/QM 语义建模，不需要为了 aggregate relation 临时把根表物理字段 `src_id` 暴露成 `srcId`。

## Reproduction

本地用 ecommerce fixture 复现同类形态。单层维度字段：

```js
const fo = loadTableModel('FactOrderModel');
const stores = loadTableModel('StoreAggregateSourceModel');

const storeAgg = stores
    .filterEq(stores.status, 'ACTIVE')
    .groupBy(stores.storeId)
    .as('storeAggByBusinessId');

fo.leftJoin(storeAgg).on(fo.store$storeId, storeAgg.storeId)
```

修复前主干已经能把 `fo.store$storeId` 解析成维表列 `d3.store_id`，但没有把 `dim_store d3` 加入 FROM/JOIN 列表，导致 SQL 出现未声明别名：

```sql
left join (...) storeAggByBusinessId
  on d3.store_id = storeAggByBusinessId.storeId
```

这与上游报告的 `t1.stockHouse$srcId` 旧症状同属一类问题：aggregate relation 自定义 ON 条件依赖的左侧语义字段没有完整转成可执行 join plan。

补齐验证嵌套维度路径：

```js
const fs = loadTableModel('FactSalesNestedDimModel');
const categories = loadTableModel('CategoryAggregateSourceModel');

const categoryAgg = categories
    .filterEq(categories.status, 'ACTIVE')
    .groupBy(categories.categoryId)
    .as('categoryAggByBusinessId');

fs.leftJoin(categoryAgg).on(fs.product.category$categoryId, categoryAgg.categoryId)
```

该场景要求引擎根据 ON 左侧 `product.category$categoryId` 自动补齐 `fact_sales_nested -> dim_product_nested -> dim_category_nested` join path，再 join RHS aggregate derived table。

## Expected Behavior

- validate 继续通过。
- 运行时 SQL ON 使用展开后的物理列表达式，不出现 `t1.<fieldAlias>` 或 `<dimension>$<property>`。
- 如果 ON 左键来自已 join 维度字段，SQL 必须先 join 该维表，再 join RHS aggregate derived table。
- 如果 ON 左键来自嵌套维度路径，SQL 必须按路径顺序补齐所有依赖维表，再 join RHS aggregate derived table。
- root 字段 ON 与维度字段 ON 都可执行。
- RHS pushdown 仍基于 aggregate relation 的安全 join-key mapping，不通过 raw SQL 猜测。

## Fix

- `JoinCondition` 统一通过 QueryModel 解析 ON 字段，候选名覆盖 `fullRef`、`aliasRef`、`columnName`，并用 `DbColumn.getDeclare(null, runtimeAlias)` 渲染物理表达式。
- `JoinBuilderFunction` 暴露 ON 条件引用到的 `QueryObject` 列表。
- `JdbcQuery.JdbcFrom` 在加入带 `JoinBuilderFunction` 的 edge 前，先补齐 ON 条件依赖的 join graph path。
- 保持 aggregate relation RHS pushdown 逻辑不变，避免把 ON SQL 字符串当作 pushdown 来源。

## Test Strategy

新增集成测试：

- `AggregateJoinQueryModelTest#aggregateRelationOnLeftKeyShouldSupportJoinedDimensionField`
- `AggregateJoinQueryModelTest#aggregateRelationOnLeftKeyShouldSupportNestedDimensionPath`

测试断言：

- 单层维度字段 SQL 包含 `left join dim_store`，不包含 `store$storeId`，ON 条件使用 `d3.store_id = storeAggByBusinessId.storeId`。
- 嵌套维度路径 SQL 按顺序包含 `left join dim_product_nested`、`left join dim_category_nested`、`left join (select ...)`，不包含 `product.category$categoryId` 或 `product_category$categoryId`。
- 嵌套维度路径 ON 条件使用二级品类维表物理列 `d3.category_id = categoryAggByBusinessId.categoryId`。
- SQL 在 SQLite 真实执行成功，结果等于原生查询。

单层维度字段原生对照：

```sql
select ds.area_sqm
from fact_order fo
join dim_store ds on fo.store_key = ds.store_key
where fo.order_id = ?
  and ds.status = 'ACTIVE'
```

嵌套维度路径原生对照：

```sql
select dp.product_id productId, dc.category_level categoryLevel
from dim_product_nested dp
join dim_category_nested dc on dp.category_key = dc.category_key
where dc.status = 'ACTIVE'
```

## Verification

2026-05-27 已执行：

```bash
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' '-Dtest=AggregateJoinQueryModelTest#aggregateRelationOnLeftKeyShouldSupportJoinedDimensionField'
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' '-Dtest=AggregateJoinQueryModelTest#aggregateRelationOnLeftKeyShouldSupportNestedDimensionPath'
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' -Dtest=AggregateJoinQueryModelTest
```

结果：

- Single-level targeted regression: passed, 1 test.
- Nested-path targeted regression: passed, 1 test.
- Aggregate join suite: passed, 17 tests.

单层维度字段关键 SQL 证据：

```sql
from fact_order t1
 left join dim_store d3 on t1.store_key=d3.store_key
 left join (
   select agg_src.store_id storeId,
          sum(agg_src.area_sqm) areaSqm
   from dim_store agg_src
   where agg_src.status = 'ACTIVE'
   group by agg_src.store_id
 ) storeAggByBusinessId on d3.store_id = storeAggByBusinessId.storeId
```

嵌套维度路径关键 SQL 证据：

```sql
from fact_sales_nested t1
 left join dim_product_nested d2 on t1.product_key=d2.product_key
 left join dim_category_nested d3 on d2.category_key=d3.category_key
 left join (
   select agg_src.category_id categoryId,
          sum(agg_src.category_level) categoryLevel
   from dim_category_nested agg_src
   where agg_src.status = 'ACTIVE'
   group by agg_src.category_id
) categoryAggByBusinessId on d3.category_id = categoryAggByBusinessId.categoryId
```

2026-06-06 当前源代码复核已执行：

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite '-Dtest=AggregateJoinQueryModelTest,SemanticScaleFactorIntegrationTest,InlineExpressionPreprocessStepTest,AdvancedAnalyticsTest#testQmPredefinedFormulaFieldReferencedOnlyBySlice,PivotIntegrationTest#testQmPredefinedFormulaMetricInPivotFlat+testQmPredefinedFormulaInPivotTopLevelSlice+testQmPredefinedFormulaInPivotAxisHavingAndOrderBy+testQmPredefinedFormulaMetricInPivotGrandTotal' test
```

结果：

- 组合验证通过；`Tests run: 68, Failures: 0, Errors: 0, Skipped: 0`。
- 覆盖当前 aggregate relation left-key / RHS dimension filter / predefined formula / pivot formula 相关回归组合。

2026-06-13 当前源码与上游反馈复核：

```bash
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' '-Dtest=SemanticScaleFactorIntegrationTest#formulaPropertyMissingColumn_reportsFieldPathAndCarrierColumnRule,InlineExpressionPreprocessStepTest#injectsPredefinedCalculatedFieldReferencedOnlyBySliceWithoutColumns+injectsPredefinedCalculatedFieldReferencedByFieldReferenceValue,AdvancedAnalyticsTest#testQmPredefinedFormulaFieldReferencedOnlyBySlice,AggregateJoinQueryModelTest#aggregateRelationOnLeftKeyShouldSupportJoinedDimensionField+aggregateRelationOnLeftKeyShouldSupportNestedDimensionPath+aggregateRelationRhsFixedFilterShouldSupportRightDimensionField'
```

结果：

- 组合验证通过；`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`。
- 覆盖 #84 左侧已 join 维度字段、嵌套维度路径、RHS dimension fixed filter，以及 #85 / predefined formula slice-only 相关回归。
- `foggy-projects/foggy-data-mcp-bridge#84` 上游反馈已确认 `OrderStationStockProjectionQuery` 真实 TMS 查询返回 `code=200`，`total=33`，并且 ON 条件包含左侧维度路径展开后的物理字段。
- #84 当前仍保留一个边界：RHS dimension join with custom `onBuilder` 属于后续增强范围，不影响本 workitem 的左侧维度路径 aggregate ON 验收。

## Follow-Up

- TMS 可在 bridge 升级后移除为了绕过该问题临时暴露的根表 `srcId` 字段。
- 单层维度字段与嵌套维度路径 ON 已有 ecommerce fixture 覆盖；后续若出现三层以上路径或跨多个左侧路径组合，可按同一模式补更深 fixture。
