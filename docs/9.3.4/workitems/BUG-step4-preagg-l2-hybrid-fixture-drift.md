---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-STEP4-PREAGG-L2-HYBRID-FIXTURE-DRIFT
severity: major
status: in-progress
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
---

# Step 4 PreAgg L2 集成测试未声明 snapshot 语义并复用过期绿色证据

## Background

Step 4 r2 从修复后的 clean committed/pushed HEAD 执行 all-lane coverage diagnostic。Unit
authority 已完整通过，但 Failsafe `sqlite-broad` 在
`PreAggregationL2CacheIT#shouldUsePostPreAggregationIdentityForL2LookupAndWrite` fail
closed。该测试验证 L2 lookup/write 必须使用预聚合改写后的最终 SQL 与参数身份，但 fixture
没有显式声明 snapshot-only 或 hybrid 语义。

Step 3 曾保存该类 `1/F0E0S0` 的 focused diagnostic；该日志早于
`ModelResultContext.QueryCacheConfig.hybridQueryEnabled` 默认值切换为 `true`。默认值变更后
没有在同一源码/运行时快照上重跑这项 focused 证据，因此旧绿色不能证明当前语义。

## Reproduction

r2 immutable failure：

- committed/pushed HEAD：`0101a44a07784bf6b484d490c7fb508727fbab70`；
- run：`step4-coverage-20260716-diagnostic-r2`；
- outer phase：`child-integration`；child phase：`variant-sqlite-broad`；
- Unit authority：`681 execution + 55 structural / 4,941 testcase / F0E0S0`；
- Integration 已执行：caffeine=`2/F0E0S0`、hermetic=`3/F0E0S0`、
  sqlite-broad=`307/F1E0S0`，合计 `312/F1E0S0`；
- failing report：`PreAggregationL2CacheIT=1 test / 1 failure / 0 errors / 0 skipped`；
- failure：`首轮查询必须实际命中预聚合 ==> expected: <true> but was: <false>`；
- outer summary absent，database/external/addon/aggregate/threshold 均未运行；
- cleanup：container/volume/network residue=`0/0/0`，worktree 保持 clean。

r2 immutable artifact SHA-256：

- outer run status：`e20643fe6bc8c24d1ca6c6a9979cc706bf67e8fa7f5df715b36b1671d5a584c7`；
- outer run log：`16670b8c01a3fc399ad0a6e14a1f0815085533e75f4958f0c14272352a275784`；
- toolchain receipt：`a8c9aeccfecfa684b9aa99e56d24c115d9530b56908feaa3f3711c8ad1d96248`；
- Unit summary：`9227f74aa266bdda3f58a146417805fc282bc81ca4a2efe5e47bd195db334f0a`；
- Integration run status：`ec1a2fcaa00458e5b998a30d323ab46ce85f4970737fcac813f0c6de2a4c6096`。

日志给出完整路由链：

```text
Query requirement: dimensions=[salesDate, product], granularity salesDate=DAY
Skipping hybrid pre-aggregation 'daily_product_sales': watermark is null
Pre-aggregation 'monthly_category_sales' does not satisfy requirements
Skipping hybrid pre-aggregation 'daily_customer_channel_sales': watermark is null
No pre-aggregation matched
L2 cache MISS for model=FactSalesPreAggQueryModel
SELECT ... FROM fact_sales ...
L2 cache WRITE for model=FactSalesPreAggQueryModel
```

旧 focused 日志
`target/v934-step3-preagg-l2-r5/console.log` 的 SHA-256 为
`031e85e836c015881afadc6f8b9b300962a169e7797d1a68fdf35d849ff1ede9`，其中 Matcher
以 `hybridQuery=false` 命中 `daily_product_sales`。该日志生成于 2026-07-15
13:36；默认 hybrid 变更提交 `612bebe6` 生成于 2026-07-16 03:32，因此它是历史诊断而非
当前 HEAD 的有效回归证据。

独立 clean-HEAD 复现排除了 JaCoCo、顺序和 Spring context 污染：无 JaCoCo 的单方法与独立
JVM 整类均为 `1/F1E0S0`，三次都失败在同一断言并给出相同路由链。

## Root Cause

生产默认启用 hybrid，以保证增量预聚合只在存在已发布 exclusive watermark 时合并物化历史与
raw tail。当前 SQLite fixture 没有发布 watermark，所以 Matcher 对 INCREMENTAL daily
候选 fail closed 是正确行为。monthly 候选的月粒度不能满足日+商品查询，也应拒绝。

L2 测试只需要证明“预聚合改写先于 L2 lookup/write，且两轮使用同一最终身份”，并不验证
hybrid UNION 语义；它却依赖旧的隐式 snapshot 默认值。生产默认切换后，测试既没有显式关闭
hybrid，也没有建立/验证 watermark，导致测试前提失效。

本缺陷还暴露证据链问题：focused 绿色若早于其依赖的生产默认变更，不能在未重跑的情况下继续
作为当前证据引用。

## Expected vs Actual

- Expected：测试显式选择其要证明的 snapshot-only 语义，先断言命中
  `daily_product_sales`，再证明 lookup/write SQL、参数和 key 使用同一 post-rewrite 身份，
  第二轮实际命中 L2。
- Actual：测试隐式继承新的 hybrid 默认值；无 watermark 时正确回退 raw，却仍断言
  `preAggHit=true`。

## Impact Scope

- 阻断 Step 4 r2 的 Failsafe `sqlite-broad` authority。
- r2 未进入 database、required external、Addon、aggregate 或 threshold review，不得拼接
  Unit 绿色宣称 Step 4 通过。
- 影响限定为测试 fixture/证据时序；当前没有证据表明生产 Matcher、L2 identity 或 hybrid
  fail-closed 语义存在缺陷。

## Test Strategy

`automation_decision=required`：

1. L2 fixture 显式设置 `hybridQueryEnabled(false)`，不得改变生产默认；
2. 首轮先断言 `preAggHit=true` 且 `preAggName=daily_product_sales`，再断言 L2 miss、
   lookup/write SQL 含精确预聚合表名且 key 相同；
3. 第二轮再次断言同一 pre-aggregation identity、L2 hit、结果相同且不重复 write；
4. 独立方法/整类、相关 PreAgg cache/route 组合与 fresh all-lane r3 均必须通过；
5. 不新增 skip/exclusion，不伪造 watermark，不回退生产 hybrid 默认值，不下调 coverage threshold。

## Code Inventory

- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationL2CacheIT.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/ModelResultContext.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/query_execution/PreAggRewriteStep.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/preagg/PreAggregationMatcher.java`
- `foggy-dataset-demo/src/main/resources/foggy/templates/preagg_test/model/FactSalesPreAggModel.tm`

## Fix Checklist

- [x] 保留 r2 immutable failure run、Unit GREEN 和 Failsafe RED 证据。
- [x] 确认默认 hybrid 变更与旧 focused 绿色的时间顺序。
- [x] 从日志确认 null-watermark fail-closed、raw SQL 与 L2 miss/write 路径。
- [x] 完成独立复现并排除顺序/context/instrumentation 污染。
- [x] 显式冻结 snapshot-only fixture 与 exact pre-aggregation identity。
- [x] focused/组合回归 GREEN。
- [x] 级联 Step 4 successor/SHA 契约并完成静态正负例。
- [ ] 从修复提交的 clean/pushed HEAD 完成 r3 all-lane diagnostic。

## Verification

修复后 focused Failsafe：

- `PreAggregationL2CacheIT=1/F0E0S0`；
- 首轮日志：`daily_product_sales`、`hybridQuery=false`、L2 miss/write，SQL 为
  `FROM preagg_daily_product_sales`；
- 第二轮日志：同一 `daily_product_sales` 路由与最终 SQL identity，L2 hit，不执行 raw SQL；
- `PreAggregationIT + PreAggregationL2CacheIT=30/F0E0S0`，未用 snapshot fixture 替代
  既有 29 项 matcher/rewriter/hybrid 覆盖；
- 修复后测试源码 SHA-256：
  `bb5d6884401579447382587861e197feac2884ab701065d7826fe7a676e0c313`。

最终 identity/static verification：coverage amendment=`11 rows / 4 new + 7 changed`，SHA-256=
`937666fc1926ec1c4764ebb50d4b4d4bdd1f1013f0d63cc77d9a1856fae153d2`；declared
amendments=`17`，SHA-256=
`be9a2d553499f799d5dc81cee353397799ad3f01d2923c6aeccb82fdb9bd7548`；top manifest=
`51/51`，SHA-256=`348ade918a5020b9b65b9fb93e4bb7034e73f197c8545c7cbbfeb3d34d044ac1`；
successor manifest=`12/12`，SHA-256=
`6ac8a24dd983c1929f6d21430f57adca503893e69b368b37a08731f5a5355948`；coverage/view/
successor/DB negatives=`8/12/8/14` 全绿。

focused 模块 `target` XML 会被后续 Maven 覆盖，仅作为修复诊断；clean-HEAD r3 与最终
run-owned evidence 仍是 Step 4 阶段退出条件。

## References

- `docs/9.3.4/implementation-plan.md`
- `docs/9.3.4/contract/test-lane-evidence-contract.md`
- `docs/9.3.4/progress/test-ci-evidence-chain-progress.md`
- `docs/9.3.4/evidence/step-3/step3-pivot-preagg-method-diagnostic-20260715.md`
- `docs/9.3.4/workitems/BUG-step4-preagg-validation-wrong-table.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r2-fail-closed-20260716.md`
- `docs/9.3.4/workitems/BUG-step4-pivot-legacy-hybrid-fixture-drift.md`
