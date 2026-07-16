---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-STEP4-PREAGG-VALIDATION-WRONG-TABLE
severity: major
status: closed
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: foggy-dataset-model
---

# Step 4 预聚合正确性测试腐化错误表并允许 raw-vs-raw 伪绿

## Background

9.3.4 Step 4 首次从 clean committed/pushed HEAD 执行 all-lane coverage diagnostic 时，
Unit authority 在 `PreAggregationDataValidationTest#testDetectCorruptedPreAggData`
fail-closed。该测试声称通过修改预聚合表验证错误检测能力，但查询实际没有读取被修改的表。

同一类的三项“一致性对比”还只记录 `preAggHit/preAggName`，没有把实际命中作为断言；其中
两项已退化为 raw-vs-raw，仍会因结果相等而绿色，属于测试证据伪绿。

## Reproduction

首轮失败证据：

- committed/pushed HEAD：`bc100b0f63bd3ff62d1105611dae41741790aedd`；
- run：`step4-coverage-20260716-diagnostic-r1`；
- phase：`child-unit`；
- 全 Unit：`3115 tests / 1 failure / 0 errors / 0 skipped`；
- 独立单方法：`1/1`；独立整类：`9/1`，排除跨类顺序和 Spring context 污染。

失败日志给出完整因果链：

```text
UPDATE preagg_daily_product_sales ... + 1000
Skipping hybrid pre-aggregation 'daily_product_sales': watermark is null
Selected pre-aggregation 'monthly_category_sales'
SELECT ... FROM preagg_monthly_category_sales
预聚合查询总额=27633.42, 原始表查询总额=27633.42
```

同一 RED XML 还记录：

- 日期+商品维度：`preAggHit=false, preAggName=null`；
- 客户+渠道维度：`preAggHit=false, preAggName=null`；
- 两项都以 raw-vs-raw 的相等结果通过。

## Root Cause

`daily_product_sales` 是 INCREMENTAL/hybrid 预聚合。生产默认
`hybridQueryEnabled=true`，测试运行时没有有效 watermark，因此 Matcher 正确地对 daily
候选 fail-closed；当前查询的可用候选是 FULL refresh 的
`monthly_category_sales`。测试却固定修改 daily 表，腐化数据与实际 SQL 完全脱节。

另外，snapshot 一致性 fixture 没有显式关闭 hybrid，且辅助断言没有要求
`preAggHit=true` 或核对 `preAggName`，从而允许未命中预聚合时继续比较两次原始查询。

生产 Matcher、watermark fail-closed 语义和优先级均无缺陷，不应通过伪造 watermark 或放宽
matcher 修复该测试。

## Expected vs Actual

- Expected：腐化探针必须精确修改实际被查询的预聚合行，命中名称必须先于金额差断言得到
  验证，差额必须精确为 `1000.00`，finally 必须精确恢复一行。
- Actual：探针修改 daily、查询 monthly，导致差额为 0；另外两个一致性测试未命中任何
  预聚合仍绿色。

## Impact Scope

- 阻断 Step 4 首轮 all-lane diagnostic 的 Unit authority。
- 若只删除失败断言或强制 daily hybrid，可掩盖生产默认 fail-closed 路由，继续形成伪绿。
- 影响限定为测试 fixture 与断言；生产查询、Matcher、阈值和 coverage exclusion 不变。

## Test Strategy

`automation_decision=required`：

1. 默认 hybrid 腐化探针使用确定性 `(year_month, product_key)` 键，备份、修改并恢复
   `preagg_monthly_category_sales`，update/restore 均要求影响精确一行；
2. 在金额断言前要求 `preAggHit=true` 且
   `preAggName=monthly_category_sales`；
3. 三个 snapshot 一致性对比显式 `hybridQueryEnabled(false)`，分别要求命中
   `daily_product_sales`、`daily_product_sales`、
   `daily_customer_channel_sales`，禁止 raw-vs-raw；
4. `preAggEnabled` 开关的正向路径也要求命中 monthly，负向路径要求未命中；
5. 整类、Unit authority 和 fresh all-lane diagnostic 均不得新增 skip、exclusion 或阈值下调。

## Code Inventory

- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationDataValidationTest.java`
- `foggy-dataset-demo/src/main/resources/foggy/templates/preagg_test/model/FactSalesPreAggModel.tm`
- `foggy-dataset-model/src/test/resources/sqlite/04-preagg-schema.sql`
- `foggy-dataset-model/src/test/resources/sqlite/05-preagg-data.sql`

## Fix Checklist

- [x] 保留 r1 immutable failure run 与 Surefire RED 证据。
- [x] 单方法/整类复现并排除顺序污染。
- [x] 腐化探针改为实际命中的 monthly 表与确定性主键。
- [x] 补齐 hit/name、精确 update/restore 与禁止空 fixture 的断言。
- [x] 三项 snapshot 对比补齐显式模式和 expected pre-aggregation identity。
- [x] focused 整类回归 GREEN，且日志证明不是 raw-vs-raw。
- [ ] 在修复提交的 clean/pushed HEAD 上完成 Unit authority 与 r2 all-lane diagnostic。

## Verification

修复后的 focused `PreAggregationDataValidationTest`：

- `9 tests / 0 failures / 0 errors / 0 skipped`；
- snapshot 路由依次为 `daily_product_sales`、`daily_product_sales`、
  `daily_customer_channel_sales`；
- 默认 hybrid 正向路由为 `monthly_category_sales`；
- 腐化结果 `28633.42`，原始表结果 `27633.42`，差额 `1000.00`；
- 测试源码 SHA-256：
  `affb6e415c1770eae7c9ab6f3505ac67acfe03eac1461bd2a48addf3ee790623`；
- focused Surefire XML SHA-256：
  `ab15b49b0d40122c04fdb737218a0488a672b8833c424e313a222b9810aca9ee`。

本 BUG 的测试缺陷已关闭；clean-HEAD Unit/all-lane evidence 仍是 Step 4 阶段退出条件，不把
可覆盖的 module `target` XML 当作最终 immutable evidence。

## References

- `docs/9.3.4/implementation-plan.md`
- `docs/9.3.4/contract/test-lane-evidence-contract.md`
- `docs/9.3.4/progress/test-ci-evidence-chain-progress.md`
- `docs/9.3.4/test/test-ci-evidence-chain-test-plan.md`
