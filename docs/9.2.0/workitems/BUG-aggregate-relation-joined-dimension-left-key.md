---
type: bug
bug_source: upstream-feedback
version: 9.2.0
ticket: BUG-aggregate-relation-joined-dimension-left-key
severity: major
status: ready-for-verification
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

# BUG: Aggregate Relation ON 左键支持已 join 维度字段

## Background

TMS 侧 `ReceiptHeaderModel` 当前没有暴露根表 `srcId`，业务源单号来自已 join 维度字段 `stockHouse$srcId`。模型作者期望使用：

```js
rh.leftJoin(demandAgg).on(rh.stockHouse$srcId, demandAgg.srcId)
```

这样可以保持 TM/QM 语义建模，不需要为了 aggregate relation 临时把根表物理字段 `src_id` 暴露成 `srcId`。

## Reproduction

本地用 ecommerce fixture 复现同类形态：

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

## Expected Behavior

- validate 继续通过。
- 运行时 SQL ON 使用展开后的物理列表达式，不出现 `t1.<fieldAlias>` 或 `<dimension>$<property>`。
- 如果 ON 左键来自已 join 维度字段，SQL 必须先 join 该维表，再 join RHS aggregate derived table。
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

测试断言：

- SQL 包含 `left join dim_store`。
- SQL 不包含 `store$storeId`。
- ON 条件使用 `d3.store_id = storeAggByBusinessId.storeId`。
- SQL 在 SQLite 真实执行成功，结果等于原生查询：

```sql
select ds.area_sqm
from fact_order fo
join dim_store ds on fo.store_key = ds.store_key
where fo.order_id = ?
  and ds.status = 'ACTIVE'
```

## Verification

2026-05-27 已执行：

```bash
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' '-Dtest=AggregateJoinQueryModelTest#aggregateRelationOnLeftKeyShouldSupportJoinedDimensionField'
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' -Dtest=AggregateJoinQueryModelTest
```

结果：

- Targeted regression: passed, 1 test.
- Aggregate join suite: passed, 16 tests.

关键 SQL 证据：

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

## Follow-Up

- TMS 可在 bridge 升级后移除为了绕过该问题临时暴露的根表 `srcId` 字段。
- 若后续出现嵌套维度路径 ON，例如 `a.b$c`，需补同类 fixture，确认 aliasRef/path join graph 仍可覆盖。
