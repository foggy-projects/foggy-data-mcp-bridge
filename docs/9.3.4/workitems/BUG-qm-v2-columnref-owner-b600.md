---
type: bug
bug_source: downstream-runtime-regression
version: 9.3.4
ticket: BUG-QM-V2-COLUMNREF-OWNER-B600
github_issue: https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/122
severity: major
status: fixed-awaiting-merge
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
owner_module: foggy-dataset-model
created_at: 2026-07-15
updated_at: 2026-07-15
---

# BUG: QM v2 ColumnRef owner / alias 在 stock-root join lowering 中丢失

## Background

QM v2 的 model-qualified `ColumnRef` 在 Query Model 解析和 dimension 自动展开时可能
退化为裸字段名。多模型、同名字段或显式 alias 共存时，后续 root-first 查找会绑定到错误
的 `TableModel`，SQL 因而输出逻辑 token、未限定字段或错误的维表 JOIN。

下游首次表现为 stock-root 默认列表 SQL B600；根因在 `foggy-dataset-model` lowering，
不是下游 TM/QM 模板或页面 DSL。

GitHub Issue: [#122](https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/122)。

## Reproduction

~~~bash
mvn -pl foggy-dataset-model \
  -Dtest=ColumnRefOwnerResolutionB600Test,DimensionAutoExpandTest,AdvancedAnalyticsTest#testQmV2SameTableModelMultipleAliases,AggregateJoinQueryModelTest#aggregateJoinResultShouldMatchNativeAggregate \
  test
~~~

修复前，显式订单 join / nested dimension 路径会生成类似 `orderId = sourceOrder$id`
或未限定同名字段的 SQL，SQLite 无法执行。

## Expected vs Actual

| 项目 | Expected | Actual before fix |
| --- | --- | --- |
| model-qualified ColumnRef | select、join、filter、order、access 均绑定创建它的具体 TableModel 实例 | 裸名称可被 root 或另一同名模型抢占 |
| 显式 alias | 仅解析到相同 runtime alias 的实例 | 同一 TM 的另一 alias 可能被错误采用 |
| dimension 自动展开 | properties/nested child 保留公开 alias 前缀并连接正确维表 | 属性/child 退化为裸路径或 JOIN 到错误实例 |

## Impact Scope

- QM v2 多模型 join，尤其 stock-root + 显式 order join。
- 同一 TM 的显式 alias/self-join。
- ColumnRef 参与 select、dimension、access、order 及 aggregate relation 的路径。
- 遗留 String ref 保持原有 root-first 契约，不由本修复改写。

## Test Strategy

1. 使用 SQLite `QueryFacade.queryModelResult` 真实执行 probe QM。
2. 对 SQL owner/alias 与实际返回行同时断言，不仅检查 SQL 文本。
3. 覆盖无显式 alias、显式 self-join、自动 properties、nested child、aggregate join。

## Code Inventory

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/ColumnRefResolver.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/QueryModelLoaderImpl.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/QueryModelSupport.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/proxy/AggregateRelationProxy.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/ColumnRefOwnerResolutionB600Test.java`

## Fix Checklist

- [x] 确认 ColumnRef 在 lowering 中丢失 owner / runtime alias。
- [x] 增加 owner-aware resolver，仅在创建 ColumnRef 的具体 TableModel 实例内解析。
- [x] 对显式 alias 禁止回退到同 TM 的其他实例。
- [x] 让 dimension expansion 的 properties/nested child 继承当前公开 alias 路径。
- [x] 修正 aggregate relation 显式 alias 标记。
- [x] 添加 SQLite 真执行 regression probes。
- [x] 推送修复分支 `fix/qm-v2-columnref-owner-b600`。
- [ ] 合并到 engine 主线并发布唯一 Maven 制品；下游 clean CI 重新构建后关闭。

## Verification

修复分支：

- `697f1a52 fix(query): retain column ref owner in qm v2`
- `c8639325 fix(query): retain alias in nested dimension expansion`

聚焦回归通过 16 项：

- `ColumnRefOwnerResolutionB600Test`：3
- `DimensionAutoExpandTest`：11
- `AdvancedAnalyticsTest#testQmV2SameTableModelMultipleAliases`：1
- `AggregateJoinQueryModelTest#aggregateJoinResultShouldMatchNativeAggregate`：1

## References

- GitHub Issue: [#122](https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/122)
- fix branch: [`fix/qm-v2-columnref-owner-b600`](https://github.com/foggy-projects/foggy-data-mcp-bridge/tree/fix/qm-v2-columnref-owner-b600)
- related historical alias regression:
  `docs/9.2.0/workitems/BUG-qm-v2-same-table-alias-aggregate-sql.md`
