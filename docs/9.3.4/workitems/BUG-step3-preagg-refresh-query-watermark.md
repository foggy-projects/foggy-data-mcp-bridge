---
type: bug
bug_source: acceptance-found
version: 9.3.4
ticket: BUG-934-S3-PREAGG-WATERMARK
severity: critical
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model-preagg/foggy-dataset-model
---

# PreAgg 刷新结果未进入查询 watermark 生命周期

## Background

9.3.4 Step 3 最终只读审计发现，Addon 刷新可以成功更新物化表，但成功结果中的
`newWatermark` 没有发布到查询 matcher 使用的 `PreAggregation.dataWatermark`。原有
SQLite/MySQL 5.7 生命周期用例只直接对比源表和物化表，没有证明刷新后查询重写链路
实际接管。

## Reproduction

1. 创建带 DATE watermark 的增量预聚合，初始 `dataWatermark=null`。
2. 调用 `PreAggRefreshService.refresh(...)` 完成全量或增量刷新。
3. 刷新返回成功，但 `preAgg.getDataWatermark()` 仍为 `null`。
4. `PreAggregationMatcher` 在 hybrid 开启时跳过该候选，查询继续走源表。
5. 若把当天误当作 inclusive watermark，刷新后再写入同日迟到数据，旧有
   `preAgg <= watermark / source > watermark` 两分支都会漏掉该行。

## Expected vs Actual

- 期望：事务提交后发布一个查询可见的 exclusive upper bound；hybrid 使用
  `preAgg < watermark / source >= watermark`；
  刷新后 matcher/rewriter 应接管，且同日迟到数据仍由源表分支返回。
- 实际：watermark 只存在于刷新结果或 scheduler 私有状态；查询侧看不到。简单回写
  当天又会把尚未闭合的当天 bucket 错判为完整。

## Impact Scope

- `foggy-dataset-model-preagg` 的 FULL/INCREMENTAL 刷新生命周期。
- `foggy-dataset-model` 的 PreAgg matcher/hybrid rewriter 可见性与数据完整性。
- 9.3.4 Step 3 Addon 伴随门及正式 45/446 总闸门；修复前不得签收 Step 3。

## Test Strategy

- 在 SQLite 和 MySQL 5.7 共用的 Addon lifecycle IT 中先补跨层断言：刷新成功后
  watermark 发布到运行时对象，matcher 产生 hybrid match，rewriter 生成精确
  `< safeWatermark / >= safeWatermark` 两分支。
- 在刷新之后插入当天迟到行，执行重写 SQL并与源表原生 GROUP BY 结果对比，证明
  当天 bucket 不会被错误关闭。
- 保留失败刷新不修改 watermark、物化数据不变的 fail-closed 断言。

## Code Inventory

- `addons/foggy-dataset-model-preagg/.../refresh/IncrementalRefreshStrategy.java`
- `addons/foggy-dataset-model-preagg/.../refresh/FullRefreshStrategy.java`
- `addons/foggy-dataset-model-preagg/.../refresh/PreAggRefreshService.java`
- `foggy-dataset-model/.../impl/preagg/PreAggregationImpl.java`
- `addons/foggy-dataset-model-preagg/.../lifecycle/PreAggAddonLifecycleIT.java`
- `scripts/v934/step3/preagg_addon_lifecycle_report_tool.py`

## Fix Checklist

- [x] 先建立可稳定复现的 refresh -> matcher/rewriter 失败断言。
- [x] 明确 DATE watermark 为物化历史的 exclusive upper bound。
- [x] FULL/INCREMENTAL 成功后返回首个未闭合 bucket 的 exclusive boundary。
- [x] 仅在刷新事务成功后把 watermark/刷新时间发布到运行时 PreAggregation。
- [x] 保证运行时查询线程能看到已发布状态。
- [x] 为 Addon XML 增加 run/variant 身份绑定，拒绝跨变体复制报告。
- [x] 重跑单元、Addon 2×3、数据库/外部矩阵与顶层并集闸门。

## Verification

正式同 HEAD 总闸门与资源残留证据见下方 closure 及 Step 3 exit evidence。

## 2026-07-16 Formal Closure

刷新/查询契约已固定为 exclusive boundary：materialized 分支 `< W`，source 分支
`>= W`。首个无 runtime watermark 的 incremental 请求回落 FULL；直接 incremental
缺 W fail closed。只有成功事务提交后才向 runtime `PreAggregation` 发布 W/refresh
time，失败刷新不发布；matcher/rewriter 拒绝 null、非 `LocalDate` 与 future W，同日
迟到数据仍由 source 分支返回。refresh service 以 runtime `PreAggregation` 为锁串行化
刷新；scheduler 另以 `taskInfo` 为锁覆盖 capture→service→mirror publication，并保持
固定 `taskInfo → preAgg` 嵌套顺序。查询只读取一次 runtime W。

正式 parent=`step3-required-20260716-final-r4`：Addon companion=`2/6/F0E0S0`、
database=`29/370/F0E0S0`、external=`16/76/F0E0S0`、required union=
`45/446/F0E0S0`，资源残留均为零。该 closure 只声明运行时发布：模型重载/进程重启后
`W=null`，首次刷新回落 FULL；没有 durable watermark persistence 或刷新后缓存立即
一致的声明。

## References

- `docs/9.3.4/evidence/step-3/step3-required-matrix-exit-20260716.md`
- `docs/9.3.4/acceptance-evidence-plan.md`
- `docs/9.3.4/test/test-ci-evidence-chain-test-plan.md`
- `docs/9.3.4/workitems/BUG-step3-preagg-addon-materialization-contract.md`
