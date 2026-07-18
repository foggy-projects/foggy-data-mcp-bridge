---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-STEP4-PREAGG-UNIT-ORDER-ISOLATION
severity: major
status: closed
post_gate_confirmed_at: 2026-07-18
post_gate_evidence: docs/9.3.4/acceptance/step4-coverage-gate-acceptance.md
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: foggy-dataset-model
---

# Step 4 预聚合单测 snapshot/hybrid fixture 契约漂移

## Background

9.3.4 Step 4 在刷新 Step 2 后新增/变更报告基线时，把三个已有/新增预聚合测试类放入
同一个 Surefire JVM 执行。首轮误以为结果依赖执行顺序；缩小到单类和单方法后仍可复现，
最终确认是三个 snapshot-only fixture 没有显式关闭 hybrid query。

## Reproduction

在 2026-07-16 的 Step 4 instrumentation bootstrap worktree 执行：

```bash
mvn -q -pl foggy-dataset-model \
  -Dtest=PreAggQueryRequirementBuilderTest,PreAggregationMatcherTest,PreAggregationEdgeCaseTest \
  -DskipITs=true test
```

结果稳定证据的首轮为 `48 tests / 3 failures / 0 errors / 0 skipped`，失败均位于
`PreAggregationEdgeCaseTest`：

- `testAggregationQueryWithDimensionAndMeasures_ShouldUsePreAgg`：期望
  `daily_product_sales`，实际为 `monthly_category_sales`；
- `testQueryWithDateRangeSlice_ResultConsistency`：期望命中预聚合，实际未命中；
- `testQueryWithSlices_ResultConsistency`：期望 `daily_product_sales`，实际为
  `monthly_category_sales`。

## Root Cause

`daily_product_sales` 是 INCREMENTAL 预聚合，三个测试 fixture 没有运行时 watermark，且
`QueryCacheConfig.hybridQueryEnabled` 默认为 `true`。9.3.4 加固后的
`PreAggregationMatcher` 必须对 `watermark=null` fail-closed，因此日志明确记录：

```text
Skipping hybrid pre-aggregation 'daily_product_sales': watermark is null
```

单独运行 `PreAggregationEdgeCaseTest` 仍为 `22/3`，单独运行第一个失败方法仍为 `1/1`，
排除了测试顺序、Spring context cache 和其它两个测试类的污染。生产 Matcher 行为正确；
漂移发生在测试 fixture 对 snapshot/hybrid 模式的表达。

## Expected vs Actual

- 期望：同一模块的测试类以任意合法 Surefire 顺序和同 JVM 聚合执行时结果一致，全部
  `F0/E0/S0`。
- 实际：聚合选择器改变 `PreAggregationEdgeCaseTest` 所见的预聚合候选或选择结果，产生
  三个失败；单类绿色不足以证明全量 Unit lane 可重复。

## Impact Scope

- 阻断 Step 4 的 all-lane diagnostic baseline 和阈值 review。
- 暴露 Step 2 后新增/变更单测未被同 HEAD 全量 authority 重新证明的风险。
- 生产 fail-closed 语义未放宽；影响限定为三个测试请求未显式声明 snapshot-only。

## Test Strategy

`automation_decision=required`。现有单类与聚合命令是稳定、低成本的回归入口；修复不固定
测试顺序，也不为增量预聚合伪造共享 watermark。三个“应命中 daily snapshot”的请求显式
设置 `.hybridQueryEnabled(false)`，同时保留
`PreAggregationMatcherTest#uninitializedHybridWatermarkDoesNotMatch` 对 null/future/foreign
watermark 的 fail-closed 保护。

1. `PreAggregationEdgeCaseTest` 单类 GREEN；
2. 上述三类聚合选择器 GREEN；
3. Step 4 Unit authority 在同一 JVM/fork 契约下 GREEN；
4. 无新增 skip、无 coverage exclusion、无阈值下调。

## Code Inventory

- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationEdgeCaseTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/preagg/PreAggregationMatcherTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/preagg/PreAggQueryRequirementBuilderTest.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/preagg/`

## Fix Checklist

- [x] Step 4 聚合单测捕获 RED。
- [x] 用单类/单方法复现并排除测试顺序污染。
- [x] 定位默认 hybrid 与 null watermark 的 fixture 契约漂移。
- [x] 三个 snapshot-only fixture 显式关闭 hybrid；生产 Matcher 不变。
- [x] focused 单类与聚合回归 GREEN。
- [x] Unit authority 与 coverage exec 证据 GREEN。
- [x] 回写根因、测试结果、源码 SHA 与当前缺口。

## Verification

修复后：

- `PreAggregationEdgeCaseTest`：`22/22, F0/E0/S0`；
- `PreAggQueryRequirementBuilderTest,PreAggregationMatcherTest,PreAggregationEdgeCaseTest`：
  `48/48, F0/E0/S0`；
- 测试源码 SHA-256：
  `ef08f3f7ea2f1956376635eb93f0dd7f629db177816f8dffd22634bf2192252e`；
- focused 单类 XML SHA-256：
  `a9728a8512e62740d7adfe55f701372c5ca2269b049748f566ae22bcc9976aec`。

本 BUG 的 fixture 缺陷已关闭；Step 4 Unit authority/coverage 证据仍是阶段退出条件，不把
可覆盖的 module `target` XML 当作最终 immutable evidence。

## References

- `docs/9.3.4/implementation-plan.md`
- `docs/9.3.4/contract/test-lane-evidence-contract.md`
- `docs/9.3.4/progress/test-ci-evidence-chain-progress.md`
