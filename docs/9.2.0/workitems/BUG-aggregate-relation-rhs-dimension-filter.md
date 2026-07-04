---
type: bug
bug_source: upstream-feedback
version: 9.2.0
ticket: BUG-aggregate-relation-rhs-dimension-filter
severity: major
status: upstream-verified
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
created_at: 2026-05-27
updated_at: 2026-05-27
upstream_issue: foggy-projects/foggy-data-mcp-bridge#84
owner_repo: foggy-data-mcp-bridge-wt-dev-compose
owner_module: foggy-dataset-model
---

# BUG: Aggregate Relation RHS Fixed Filter 支持右侧维度字段

## Background

TMS 侧 `OrderStationStockProjectionQuery` 在 RHS aggregate relation 中需要过滤 `tms_plan_assignment` 关联的 `planSheet` 维度字段：

```js
pa.planSheet$planStatus
pa.planSheet$supersededByPlanSheetId
```

修复前引擎会把这些字段错误渲染为 RHS 根表列，例如 `agg_src.plan_status`、`agg_src.superseded_by_plan_sheet_id`。这些列实际属于右侧维表 `tms_plan_sheet`，因此完整计划单过滤只能临时下线，TMS 仅保留 RHS 根表条件 `pa.planAssignmentStatus = 'PLANNED'`。

## Reproduction

本地用 ecommerce fixture 复现同类形态：

```js
const fo = loadTableModel('FactOrderModel');
const sales = loadTableModel('FactSalesModel');

const fs = sales
    .filterEq(sales.orderStatus, 'COMPLETED')
    .filterEq(sales.product$categoryId, 'CAT001')
    .groupBy(sales.orderId)
    .as('fsByElectronicsOrder');

fo.leftJoin(fs).on(fo.orderId, fs.orderId)
```

修复前生成的 RHS aggregate derived table 缺少 `dim_product` join，并错误引用 RHS 根表：

```sql
from fact_sales agg_src
where agg_src.order_status = 'COMPLETED'
  and agg_src.category_id = 'CAT001'
  and agg_src.order_id = 'ORD20240101000001'
group by agg_src.order_id
```

## Expected Behavior

- RHS aggregate derived table 内部应按右侧 TM 的 JoinGraph 补齐维表 JOIN。
- RHS fixed filter 引用右侧维度字段时，应使用维表 alias 与物理列。
- groupBy、measure、RHS join key pushdown 引用右侧维度字段时，也应共享同一套 RHS source alias context。
- root 字段继续使用稳定 root alias `agg_src`。
- 暂不支持 RHS 维度路径中的自定义 `onBuilder`，遇到时 fail-closed 并给出明确提示。

## Fix

- `AggregateJoinTableModel` 新增 RHS source SQL context。
- 构建 aggregate derived table 前，收集 groupBy、fixed filter、measure、右侧 ON key 引用到的 RHS `QueryObject`。
- 使用 `sourceModel.getJoinGraph().getPath(targets)` 在 derived table 内部补齐 RHS 维度路径。
- `sourceColumnSql` 改为按字段所属 `QueryObject` 选择 alias；root 字段使用 `agg_src`，维度字段使用 `agg_<原alias>`。
- RHS join-key pushdown mapping 使用同一 alias context，避免未来 RHS join key 为维度字段时再次退化为 root alias。

## Test Strategy

新增集成测试：

- `AggregateJoinQueryModelTest#aggregateRelationRhsFixedFilterShouldSupportRightDimensionField`

测试断言：

- SQL 包含 `from fact_sales agg_src left join dim_product`。
- SQL 不包含错误的 `agg_src.category_id`。
- fixed filter 使用右侧维表物理列 `agg_d7.category_id = 'CAT001'`。
- SQL 在 SQLite 真实执行成功，聚合结果等于原生查询。

原生对照：

```sql
select sum(fs.sales_amount)
from fact_sales fs
join dim_product dp on fs.product_key = dp.product_key
where fs.order_id = ?
  and fs.order_status = 'COMPLETED'
  and dp.category_id = 'CAT001'
```

## Verification

2026-05-27 Java engine 本地验证已执行：

```bash
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' '-Dtest=AggregateJoinQueryModelTest#aggregateRelationRhsFixedFilterShouldSupportRightDimensionField'
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' -Dtest=AggregateJoinQueryModelTest
mvn install -pl foggy-dataset-demo -DskipTests
```

结果：

- Targeted RHS dimension filter regression: passed, 1 test.
- Aggregate join suite: passed, 18 tests.
- Demo bundle install: passed, tests skipped.

关键 SQL 证据：

```sql
from fact_sales agg_src
 left join dim_product agg_d7 on agg_src.product_key=agg_d7.product_key
where agg_src.order_status = 'COMPLETED'
  and agg_d7.category_id = 'CAT001'
  and agg_src.order_id = 'ORD20240101000001'
group by agg_src.order_id
```

## Upstream Verification

2026-05-27 TMS 在 `f72e4abf fix: support aggregate relation rhs dimension filters` 后复测通过。

TMS 已恢复 `OrderStationStockProjectionQuery` RHS 维度过滤：

```js
pa.planSheet$planStatus in ['CONFIRMED', 'LOCKED']
pa.planSheet$supersededByPlanSheetId = null
pa.planAssignmentStatus = 'PLANNED'
```

上游验证结果：

- `mvn -pl query-cloud-service "-Dtest=PlanningQueryModelTest,AggregateJoinQueryModelTest" test`: passed, 6 tests.
- 使用最新本地 `foggy-dataset-model` / `foggy-data-viewer` jars 重建 `query-cloud-service`: passed.
- 真实 TMS 查询选择 `plannedPieceCount` / `plannedWeight` / `plannedVolume`: returned `code=200`, `total=33`.
- 样本订单号覆盖 `1297`、`1129`、`1128`、`1127`、`1126`。
- TMS 不再需要移除 `planSheet` filters 的临时 fallback。
- TMS 保持 aggregate relation-first QM 形态，不使用 `viewSql` / raw SQL / CTE。

## Follow-Up

- TMS 已恢复 RHS `planSheet` 维度条件并完成真实查询验证；Java engine 侧本问题可收口。
- 如 TMS RHS 维度 join 使用自定义 `onBuilder`，需要单独补 RHS inner onBuilder alias rewriting；当前实现会 fail-closed。
