---
type: bug
bug_source: code-review
version: 9.3.4
ticket: BUG-934-STEP3-PREAGG-ADDON-MATERIALIZATION-CONTRACT
severity: critical
status: closed
reproduction_status: confirmed
test_strategy: unit-and-integration-test
automation_decision: required
owner: addons/foggy-dataset-model-preagg
discovered_at: 2026-07-15
---

# PreAgg Addon DDL/refresh 未复用物化列契约

## Background

Step 3 的 Pivot query diagnostic 已将 model query matcher、main/final-stage rewriter 和
hybrid read path 收紧为明确物化列契约：命名约定、维度存在和时间粒度不能单独证明
物理列存在。独立复审确认 Addon 的 DDL/refresh 生成链仍保留另一套旧推导逻辑，不能把
query-path 的绿色结果扩大为预聚合全生命周期正确性。

## Confirmed Reproduction

静态可达链：

- `PreAggAutoConfiguration` 装配 `PreAggRefreshService`；
- refresh service 与 Controller 可调用
  `addons/foggy-dataset-model-preagg/**/ddl/PreAggSqlBuilder.java`；
- DDL builder 直接拼接 `dimName_propName`，refresh 聚合使用 `MAX(propName)`，忽略
  `dimensionPropertyColumnNames`；
- incremental refresh 把 semantic watermark `salesDate$id` 截成 `salesDate`，并把同一
  推导值同时用于物化表 DELETE 和源表 WHERE；典型真实列 `date_key` 因此会被错写。

该问题不依赖数据库故障或缺 fixture；只要配置显式映射与命名约定不同，或 watermark
使用 Foggy semantic field，即可生成错误或语义不等价 SQL。

## Expected vs Actual

- expected：DDL、full/incremental refresh 与 query rewriter 消费同一个明确物化列契约；
  semantic source 字段、source physical column、materialized physical column 三者分别
  解析，任一无法证明时 fail closed。
- actual：Addon 独立猜测列名并复用错误的 watermark token；query read path 已修复，
  生成/刷新路径仍可能创建错误 schema 或对错误列执行 DELETE/WHERE。

## Impact

- 自动生成的预聚合表可能与 TM 声明及 query rewriter 不一致；
- incremental refresh 可能直接失败，或在更危险的同名列场景读写错误数据范围；
- 当前五库 Pivot method diagnostic 只能证明隔离 fixture 上的 read/query path，不能证明
  Addon 自动建表和刷新生命周期。

## Required Fix Checklist

- [x] 提取 Addon-owned physical-column contract，并让 query/Addon 消费一致的 explicit
      mapping data；watermark roles 通过 shared resolver 统一
- [x] DDL 对所有 dimension property 使用明确 materialized mapping，未知字段拒绝生成
- [x] measure DDL/refresh 只使用声明的 physical measure column 与兼容 aggregation
- [x] 将 semantic watermark、source physical watermark、materialized physical watermark
      分开解析；禁止字符串截断和跨侧复用
- [x] semantic `$id` 要求 source foreign key 与 materialized id mapping 均非空
- [x] 为 explicit mapping、caption/id/time bucket、裸物理 watermark 与未知 mapping 增加
      正向/负向单测
- [x] 在至少 SQLite + 一个外部数据库真实执行 create/full refresh/incremental refresh/
      query parity，并证明错误配置 fail closed
- [x] 将 Addon lifecycle execution 纳入 Step 3 exact report collector

## Current Batch Boundary

`step3-pivot-preagg-method-diagnostic-20260715.md` 不包含 Addon DDL/refresh。当前批次允许
以 query-path diagnostic 合入，但 Step 3 exit 和“预聚合全生命周期物理契约统一”声明
均被本 BUG 阻止。

## 2026-07-15 Candidate Review Check-in

首个 Addon resolver/DDL builder candidate 已补出 explicit mapping、source/materialized
watermark 与 fail-closed 单测，但独立代码复核确认其尚不可合入：

- `COUNT(*)` 物化不应要求同名 source measure，目标列也不能建成 DECIMAL；
- formula/`semanticScaleFactor` measure 必须消费 model-rendered expression，不能把
  semantic measure name 当物理列；
- SQLite 的 `DEFAULT datetime('now')` 不能直接作为当前 DDL default 执行；
- 当前 refresh API 传入 `LocalDate`，而内置增量模型的 `salesDate$id`/`returnDate$id`
  实际是整数 `date_key`。candidate 正确拒绝类型错配，却会使现有模型全部不可刷新；
  必须迁移到显式 DATE watermark 或设计 typed date-key codec；
- 内置 monthly/daily-customer-channel 模型缺少所有 grain 的 explicit mapping，且
  `category_name`/`categoryName`、`channel_type`/`channelType` 尚未按既有命名规则归一化，
  candidate 会重复解析或直接 fail。

这些 finding 是阻止本 candidate 合入的高风险兼容缺口，不影响数据库 query selector 的
`29/370/S0` diagnostic。当前 workitem 提升为 `in-progress`，但 Required Fix Checklist
保持未完成；只有内置 TM 迁移、真实 SQLite + 外库 lifecycle 和 exact collector 同时绿色
后才能关闭。

## 2026-07-16 Formal Closure

Addon `PreAggPhysicalColumnContract` 已统一物化列 explicit mapping，query 消费一致的
mapping data；共享 strict-DAY `PreAggWatermarkResolver` 统一 semantic/source/
materialized watermark roles。DDL/refresh 对 COUNT、formula、caption/id/time bucket、
未知映射与 watermark 类型采用一致的 fail-closed 契约。正式父运行的 required companion 在 SQLite
和 MySQL 5.7 分别执行 create/full/incremental/query parity，合计
`2 reports / 6 testcase / F0E0S0`，report negatives=`4/4`，资源残留=`0/0/0`。

Addon companion 由父 run `step3-required-20260716-final-r4`、同一 tested commit 与
source seal 绑定，但按冻结契约明确不计入 required union `45/446`。证据见
`docs/9.3.4/evidence/step-3/step3-required-matrix-exit-20260716.md`。本 workitem 的
SQLite+外库生命周期范围关闭；不把该结果扩大为五库、Controller 或 Cache 全生命周期。

## References

- `docs/9.3.4/progress/test-ci-evidence-chain-progress.md`
- `docs/9.3.4/workitems/BUG-step3-database-contract-gaps.md`
- `addons/foggy-dataset-model-preagg/src/main/java/**/ddl/PreAggSqlBuilder.java`
- `addons/foggy-dataset-model-preagg/src/main/java/**/service/PreAggRefreshService.java`
