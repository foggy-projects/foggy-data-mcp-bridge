---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-STEP4-PIVOT-LEGACY-HYBRID-FIXTURE-DRIFT
severity: major
status: closed
closed_at: 2026-07-18
closure_evidence: docs/9.3.4/acceptance/step4-coverage-gate-acceptance.md
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
---

# Step 4 Pivot legacy 参数顺序分支未声明 snapshot 语义

## Background

在修复 r2 暴露的 PreAgg L2 fixture 后，Step 4 对所有隐式 hybrid 测试做了主动扫描。
`PivotSqlParityIT#testPreAggHitWithSystemSliceAndLimitKeepsFinalParamOrder` 同时包含两个分支：

- 五数据库 authority 设置 `v934.expectedDatabase`，使用 FULL 的
  `V934PivotPreAggQueryModel`；
- 普通 SQLite focused 未设置该属性，回退到 INCREMENTAL 的
  `FactSalesPreAggQueryModel` 与全量 snapshot fixture。

legacy 分支没有设置 `ModelResultContext.QueryCacheConfig`，因此在生产默认 hybrid=true 后
继承 hybrid 语义，却仍要求必定命中预聚合。

## Reproduction

在当前源码上两次精确运行 SQLite legacy 单方法：

```bash
mvn -q -pl foggy-dataset-model \
  -DskipUnitTests=true -DskipITs=false \
  -Dfailsafe.failIfNoTests=true \
  -Dfailsafe.failIfNoSpecifiedTests=true \
  '-Dit.test=PivotSqlParityIT#testPreAggHitWithSystemSliceAndLimitKeepsFinalParamOrder' \
  verify
```

两次均为 `1 test / 1 failure / 0 errors / 0 skipped`，失败在
`preAgg should be applied before outer Pivot CTE wrapping`。路由链稳定为：

```text
daily_product_sales: skipped because watermark is null
monthly_category_sales: does not satisfy salesDate$id/day requirement
daily_customer_channel_sales: skipped because watermark is null
No pre-aggregation matched
```

RED 源码 SHA-256：
`1f2bf17a2fa20b864a975c5556bdc37d3490cfd4feb1b1412e99d62c26a4df88`。
最新模块 `target` XML 为 `1/F1E0S0`，但它会被后续运行覆盖，只作诊断证据。

## Root Cause

该方法验证的是“PreAgg + systemSlice 两个参数必须位于外层 TopN 参数之前”，不是 hybrid
UNION 行为。legacy SQLite 的 `05-preagg-data.sql` 是完整 snapshot；在默认 hybrid 切换前，
隐式配置等同 snapshot-only。默认切换后，INCREMENTAL pre-aggregation 没有已发布 watermark，
Matcher 正确 fail closed，测试前提却未同步。

五数据库 authority 没有暴露此缺陷，因为它总是设置 `v934.expectedDatabase` 并使用 FULL 的
`v934_daily_product_sales`。因此 required matrix 的绿色不能证明 legacy fallback 分支。

生产 hybrid 默认、null-watermark fail-closed 与五库 FULL fixture 均不应修改。

## Expected vs Actual

- Expected：legacy 分支显式选择 snapshot-only，精确命中
  `daily_product_sales/preagg_daily_product_sales`；五库分支继续使用 production 默认并精确
  命中 `v934_daily_product_sales/v934_preagg_daily_product_sales`；两者都验证参数顺序与
  native oracle。
- Actual：legacy 分支继承 hybrid=true、回退 raw，然后在 preAggApplied 断言处失败。

## Impact Scope

- 当前 r2/r3 required database runner不会直接触发 legacy 分支，但历史 focused 入口已失效，
  若不处理会留下未被权威矩阵覆盖的伪绿。
- 修改应限定为分支 fixture 声明和精确身份断言；testcase 仍为 23，五库 execution/report
  identity 不变。

## Test Strategy

`automation_decision=required`：

1. 只在 `v934.expectedDatabase` 为空的 legacy 分支设置
   `preAggEnabled=true, hybridQueryEnabled=false`；
2. 两个分支都断言 exact pre-aggregation name/table，禁止 generic `preagg_` 或 raw fact SQL；
3. legacy 单方法与带 `v934.expectedDatabase=sqlite` 的 V934 单方法均通过；
4. 五数据库 source successor/overlay 与 Step 4 SHA identity 必须级联，不改 report/testcase
   cardinality、skip、exclusion 或 threshold。

## Code Inventory

- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotSqlParityIT.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/ModelResultContext.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/preagg/PreAggregationMatcher.java`
- `foggy-dataset-demo/src/main/resources/foggy/templates/preagg_test/model/FactSalesPreAggModel.tm`
- `foggy-dataset-demo/src/main/resources/foggy/templates/v934_step3/model/V934PivotPreAggModel.tm`

## Fix Checklist

- [x] 主动扫描发现 required matrix 未覆盖的 legacy fallback。
- [x] legacy 单方法稳定复现两次 RED。
- [x] 显式冻结 legacy snapshot policy 与两分支 exact identity。
- [x] legacy/V934 focused 回归 GREEN。
- [x] 级联 database successor、Step 4 overlay 与 SHA 清单并完成静态正负例。
- [x] 从 clean/pushed HEAD 完成 fresh all-lane/formal，并以 current-source companion 补齐 legacy 分支。

## Verification

修复仅作用于测试分支 fixture：legacy 分支显式设置
`preAggEnabled=true, hybridQueryEnabled=false`；设置了 `v934.expectedDatabase` 的 V934 分支
没有覆盖 cache config，继续使用 production 默认 hybrid 策略。两个分支都改为断言 exact
pre-aggregation name/table，并显式拒绝 raw fact SQL。

- legacy SQLite 单方法：`1/F0E0S0`，命中
  `daily_product_sales/preagg_daily_product_sales`；
- `sqlite,v934-sqlite` 且 `v934.expectedDatabase=sqlite` 单方法：`1/F0E0S0`，命中
  `v934_daily_product_sales/v934_preagg_daily_product_sales`；
- 两条分支均保留 `systemSlice` 两个参数位于外层 TopN 参数之前的原始断言与 native oracle；
- 修复后测试源码 SHA-256：
  `5c6dcd3b4afba4d93a93c1af47cc4484a1dfc9976da92669bfbcc4529ede6155`。

focused 模块 `target` XML 会被后续 Maven 覆盖；最后一次 V934 SQLite XML 为
`1/F0E0S0`，SHA-256
`3ddddfa37674fe73536faba6d0d3cb35feceb38281c9502d68f4387e3df34a99`。
它只作为修复诊断；clean-HEAD r3 的 run-owned 五数据库 evidence 仍是阶段退出条件。

最终 identity/static verification：Pivot database source successor 已绑定上述最终源码；
coverage amendment 保持 `11 rows / 4 new + 7 changed`，declared amendments=`17`，top
manifest=`51/51`，successor manifest=`12/12`；对应 SHA-256=
`937666fc1926ec1c4764ebb50d4b4d4bdd1f1013f0d63cc77d9a1856fae153d2` /
`be9a2d553499f799d5dc81cee353397799ad3f01d2923c6aeccb82fdb9bd7548` /
`348ade918a5020b9b65b9fb93e4bb7034e73f197c8545c7cbbfeb3d34d044ac1` /
`6ac8a24dd983c1929f6d21430f57adca503893e69b368b37a08731f5a5355948`；coverage/view/
successor/DB negatives=`8/12/8/14` 全绿。

## References

- `docs/9.3.4/evidence/step-3/step3-pivot-preagg-method-diagnostic-20260715.md`
- `docs/9.3.4/evidence/step-3/step3-required-matrix-exit-20260716.md`
- `docs/9.3.4/workitems/BUG-step4-preagg-l2-hybrid-fixture-drift.md`
- `docs/9.3.4/contract/test-lane-evidence-contract.md`
