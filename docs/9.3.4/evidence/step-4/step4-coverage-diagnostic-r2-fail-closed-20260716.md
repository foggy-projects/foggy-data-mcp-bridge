---
evidence_type: failed-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260716-diagnostic-r2
tested_commit: 0101a44a07784bf6b484d490c7fb508727fbab70
status: failed
decision: excluded-from-step4-exit
recorded_at: 2026-07-16
---

# Step 4 coverage diagnostic r2 fail-closed evidence

## Decision

`step4-coverage-20260716-diagnostic-r2` 是从 clean committed/pushed HEAD
`0101a44a07784bf6b484d490c7fb508727fbab70` 启动的完整 all-lane diagnostic attempt。
Unit authority 全量通过；Failsafe 在 `sqlite-broad` 唯一失败后，outer runner 于
`child-integration` fail closed。database、required external、Addon、aggregate、model
merged-exec gate 和 threshold observation 均未执行，因此本 run 明确排除在 Step 4 exit
evidence 之外，不能与 r1 或 focused 绿色拼接。

## Immutable result

- outer status：`failed / exit_code=1 / last_phase=child-integration`；
- source seal：before=
  `428ede2bab82483ed97c857ea73e16b35f9e86ea750f94cded26bc3df9d13079`，失败后未发布
  source-after；
- Unit：`681 positive execution + 55 structural = 736 raw reports`，
  `4,941 testcase / F0E0S0`；
- Integration 已执行部分：caffeine SQLite=`2/F0E0S0`、hermetic=`3/F0E0S0`、
  sqlite-broad=`307/F1E0S0`，合计 `312/F1E0S0`；
- 唯一失败：
  `PreAggregationL2CacheIT#shouldUsePostPreAggregationIdentityForL2LookupAndWrite`，
  `expected preAggHit=true, actual=false`；
- cleanup：container/volume/network residue=`0/0/0`；
- outer `summary.env`、acceptance candidate、aggregate observation 均不存在。

## Artifact identity

- outer `run-status.env` SHA-256：
  `e20643fe6bc8c24d1ca6c6a9979cc706bf67e8fa7f5df715b36b1671d5a584c7`；
- outer `run.log` SHA-256：
  `16670b8c01a3fc399ad0a6e14a1f0815085533e75f4958f0c14272352a275784`；
- toolchain receipt SHA-256：
  `a8c9aeccfecfa684b9aa99e56d24c115d9530b56908feaa3f3711c8ad1d96248`；
- Unit `summary.env` SHA-256：
  `9227f74aa266bdda3f58a146417805fc282bc81ca4a2efe5e47bd195db334f0a`；
- Integration `run-status.env` SHA-256：
  `ec1a2fcaa00458e5b998a30d323ab46ce85f4970737fcac813f0c6de2a4c6096`。

原始 ignored artifacts 位于：

- `target/v934-step4-coverage/runs/step4-coverage-20260716-diagnostic-r2/`；
- `target/v934-step2-unit/runs/step4-coverage-20260716-diagnostic-r2/`；
- `target/v934-step2-integration/runs/step4-coverage-20260716-diagnostic-r2/`。

## Root cause and repair boundary

L2 用例验证的是“PreAgg rewrite 先于 L2 lookup/write，且两轮共享 post-rewrite SQL、参数
和 cache key identity”，不是 hybrid UNION。fixture 未显式冻结 snapshot-only 语义；生产
默认 hybrid=true 后，INCREMENTAL daily pre-aggregation 因 watermark=null 正确 fail
closed，测试却继续要求命中。修复只在测试 fixture 设置
`hybridQueryEnabled(false)`，并收紧 exact name/table、raw SQL negative、miss/write/hit 与
禁止重复 write；不改生产 Matcher、hybrid 默认、threshold 或 exclusion。

focused 修复证据：`PreAggregationL2CacheIT=1/F0E0S0`；
`PreAggregationIT + PreAggregationL2CacheIT=30/F0E0S0`。对应 workitem：
`docs/9.3.4/workitems/BUG-step4-preagg-l2-hybrid-fixture-drift.md`。

同一轮主动 hybrid fixture 扫描还发现 required matrix 未覆盖的 Pivot legacy fallback
漂移；该问题必须与 L2 修复一并进入新的 clean/pushed HEAD diagnostic，不能因 r2 未执行到
database lane 而忽略。对应 workitem：
`docs/9.3.4/workitems/BUG-step4-pivot-legacy-hybrid-fixture-drift.md`。

## Next gate

- [x] 完成 L2、Pivot source successor 与完整 Step 4 SHA identity 级联；
- [x] 静态 positives/negatives 和 r3 前实现质量复核通过；
- [ ] 提交、推送并证明 `HEAD == origin/main`、worktree clean；
- [ ] 从该 HEAD 启动唯一新 r3 all-lane diagnostic；
- [ ] 只有 r3 完整 observation、人工 threshold review 和 confirmed formal replay 后，才可
   评估 Step 4 exit、coverage audit 或 Step 5 entry。
