---
doc_role: focused-companion-evidence
doc_purpose: Bind current-source evidence for the Pivot legacy fallback branch that is intentionally outside the five-database formal variant.
version: 9.3.4
step: 4
status: passed
decision: companion-passed
tested_commit: f97483a0b87a82734d21888e7b5bea74b0c5fe55
recorded_at: 2026-07-18
---

# Step 4 Pivot Legacy Fallback Companion Evidence

## Scope

`PivotSqlParityIT#testPreAggHitWithSystemSliceAndLimitKeepsFinalParamOrder` 有两个受治理分支：

- formal-r4 五数据库 variant 均设置 `v934.expectedDatabase`，验证 V934 FULL fixture；
- legacy SQLite focused 不设置该属性，验证 `FactSalesPreAggQueryModel` 的显式
  snapshot-only fallback。

formal-r4 的五份 XML 均执行前一分支，因此本 companion 只补齐 legacy fallback 的
current-source 动态证据。它不加入 required `773+59/5707`、Addon `2/6`、`23 exec / 48
sessions` 或 aggregate coverage 统计，也不修改 formal-r4 artifact。

## Identity and Command

- HEAD / formal tested Cfreeze：
  `f97483a0b87a82734d21888e7b5bea74b0c5fe55`
- production/test worktree delta：`none`；执行时未提交变化仅在 `docs/9.3.4/**`
- current `PivotSqlParityIT.java` SHA-256：
  `18ebeedf8d79a84d5ba59ff991aeffeefea73cbb8aa51c6c10444ed64ba6d325`
- 该 SHA 与 Step 4 `SHA256SUMS`、formal-r4 source inventory 绑定的最终测试源码一致
- command：

```bash
mvn -q -pl foggy-dataset-model \
  -DskipUnitTests=true -DskipITs=false \
  -Dfailsafe.failIfNoTests=true \
  -Dfailsafe.failIfNoSpecifiedTests=true \
  '-Dit.test=PivotSqlParityIT#testPreAggHitWithSystemSliceAndLimitKeepsFinalParamOrder' \
  verify
```

## Result

- Maven exit code：`0`
- Failsafe XML：`tests=1 / failures=0 / errors=0 / skipped=0`
- raw XML：
  `foggy-dataset-model/target/failsafe-reports/TEST-com.foggyframework.dataset.db.model.engine.pivot.PivotSqlParityIT.xml`
  （size=`252655`，mtime=`2026-07-18 12:34:32.590634510 +0800`）
- XML SHA-256：
  `802933e63db781b2d0d039bf905ceaa77beff55680d6370df06cea1cbb9490dd`
- XML 没有 `v934.expectedDatabase` property，证明执行的是 legacy fallback，而不是再次
  执行 V934 variant。
- dynamic oracle 明确观察到：
  `daily_product_sales` 被选中，`hybridQuery=false`，SQL 从
  `preagg_daily_product_sales` 读取；raw `fact_sales` 不得冒充命中。
- 原有 system-slice 参数顺序、TopN 外层参数顺序与 native SQLite oracle 同时通过。

formal-r4 的五个 current-source V934 XML 均为 `23/F0E0S0`，对应 property 分别为
`sqlite`、`mysql57`、`mysql8`、`postgres15`、`sqlserver2022`；其中同名 testcase 均通过。
因此 companion 与 formal-r4 合并证明两个分支，而不是用 focused 结果替换 formal authority。

## Evidence Boundary

raw focused XML 位于 Maven `target`，后续运行可覆盖；本记录持久化 command、HEAD、源码 SHA、
XML SHA、exact testcase cardinality 与分支判别条件。其治理强度与 Step 3
MultiThread focused companion 相同，portable raw archive 仍归 Step 5。

本补证不改变 production、test、POM、runner、threshold、critical set、exclusion 或 machine
contract，不要求重跑 formal-r4。若未来上述源码 SHA 或分支条件变化，本 companion 自动失效，
必须重新执行。

## Conclusion

`companion-passed`。`BUG-934-STEP4-PIVOT-LEGACY-HYBRID-FIXTURE-DRIFT` 的 legacy
fallback 已在 formal-r4 同一 tested HEAD 的最终源码上取得 current-source automated evidence；
与 formal-r4 五数据库 V934 分支结合后，该 Major coverage gap 已关闭。
