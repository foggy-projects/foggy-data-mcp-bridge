---
quality_scope: feature
quality_mode: pre-coverage-audit
version: 9.3.4
target: STEP4-DIAGNOSTIC-READY
status: reviewed
decision: ready-with-risks
reviewed_by: Codex root session
reviewed_at: 2026-07-16
follow_up_required: yes
---

# Implementation Quality Gate

## Background

本记录审查 9.3.4 Step 4 在启动首次全 lane coverage diagnostic 前的实现质量与
fail-closed 收口。检查对象是本地 `diagnostic-ready` baseline：单一 outer
orchestration、parent-linked coverage successor、run-owned exec/report provenance、
build-only aggregate reporter、model merged-exec check、toolchain receipt 与相应静态/
focused 回归。

本记录不是 coverage result，也不替代 `foggy-test-coverage-audit`。当前没有 clean
committed/pushed HEAD 上的 Docker/DB/external all-lane diagnostic、aggregate baseline
或 reviewed thresholds，因此不得据此声明 Step 4 `passed`、创建 Step 4 exit evidence、
进入 Step 5 或签收 9.3.4。

## Check Basis

- requirement：
  `docs/9.3.4/requirement/P0-test-ci-evidence-chain.md`；
- implementation plan：`docs/9.3.4/implementation-plan.md` 的 Step 4 work/exit 与
  diagnostic-ready superseding record；
- progress / execution check-in：
  `docs/9.3.4/progress/test-ci-evidence-chain-progress.md`；
- contracts and inventories：`scripts/v934/step4/coverage-contract.json`、
  `coverage-exec-ledger.tsv`、`coverage-report-amendment.tsv`、
  `coverage-thresholds.json` 与 `SHA256SUMS`；
- regression workitems：
  `docs/9.3.4/workitems/BUG-step4-preagg-unit-order-isolation.md`、
  `docs/9.3.4/workitems/BUG-step4-legacy-coverage-argline-fail-open.md`；
- static exact evidence：coverage execution=`23 exec / 48 sessions`；required report
  overlay=`773 positive + 59 structural / 5,707 testcase / F0E0S0`；Addon companion
  单列=`2/6`；Step 4 manifest=`49/49`，manifest SHA-256=
  `c735e8c1f7b74d72afe2d1d1872128d11a16acbd7373c750e59709624560106e`；
- fail-closed negatives：raw contract=`8/8`、effective POM=`4/4`、toolchain
  receipt=`5/5`、report inventory=`27/27`；
- focused regressions：PreAgg 单类=`22/22/F0E0S0`、三类聚合=
  `48/48/F0E0S0`；legacy coverage 修复后有 exec 且低于原门时正确失败；普通无
  coverage profile 与新 `v934-coverage` profile 双向回归通过。

## Changed Surface

- root build contract：`pom.xml` 中 Surefire/Failsafe late-bound argLine、
  `v934-coverage` profile、build-only reporter reactor ownership，以及 Central
  publishing 对 `foggy-coverage-report` 的显式排除；
- model build gate：`foggy-dataset-model/pom.xml` 中 legacy coverage 验证面与只消费
  merged exec 的 Step 4 model check；既有 threshold/exclusion 不下调；
- reporter module：`build-support/foggy-coverage-report/pom.xml`，只生成 aggregate
  XML/HTML，不含生产类，也不进入 Central release bundle；
- Step 4 authority：`scripts/verify-v934-step4-coverage.sh` 与
  `scripts/v934/step4/` 下 contract、ledger、successor、toolchain receipt、exec/XML/
  report/threshold verifier 和 negative tools；
- canonical lane instrumentation：Unit、Integration、database、external、Addon 与
  Step 3 parent runners接入 run-owned JaCoCo exec/session；
- focused test corrections：三个 snapshot-only PreAgg fixture 显式关闭 hybrid，
  Redis cross-JVM child session 注入；没有生产 API 或业务语义改动；
- documentation：9.3.4 requirement、plan、progress、contract、test、inventory、
  acceptance plan 与 roadmap 的 diagnostic-ready 边界回写。

## Quality Checklist

| Check | Result | Evidence |
|---|---|---|
| scope conformance | pass | 改动集中于 Step 4 build/test authority、coverage instrumentation、两个 workitem 与状态回写；无 9.3.5/9.4.0 API/module 治理扩张 |
| code hygiene | pass | 未以 skip、coverage exclusion、threshold 下调或生产重构换取绿色；临时 diagnostic 产物不属于发布 manifest |
| duplication and consolidation | pass-with-follow-up | safety-critical path 已收敛到共享 runner/receipt/exec/XML/report tools；各 canonical lane 仍保留领域 runner，由 single outer 统一调度 |
| complexity and abstraction | pass-with-risks | authority 工具链规模较大，但 contract、ledger、successor overlay 与 verifier 分层清晰；后续不得复制第二套 aggregate/receipt 逻辑 |
| error handling and edge cases | pass | missing/empty/tampered/noncanonical evidence、wrong count/hash/session/tool realm 与 report splice 均 fail closed；四组 negatives=`8/4/5/27` |
| readability and maintainability | pass-with-follow-up | exact inventories 和错误码让关键失败可定位；历史 Step 2 generator 与当前 derived view 的边界需继续保持显式 |
| critical logic documentation | pass | contract/workitems 解释 late evaluation、hybrid null-watermark、receipt replay、immutable parent 与 diagnostic-only 边界 |
| contract and compatibility | pass | Step 1 freeze 不改写；23 exec/48 sessions 与 773/59/5707 分层计数；Central 排除 reporter；legacy、普通、新 coverage 三态行为均被复核 |
| documentation and writeback | pass-for-diagnostic | requirement/plan/progress 已统一为 `in-progress / diagnostic-ready`；未创建或预签 Step 4 exit/acceptance |
| test alignment | pass-for-diagnostic | focused 22/22、48/48 与 coverage 三态直接命中改动面；静态 negatives 验证 fail-closed，但不冒充 all-lane runtime evidence |
| release readiness | not-ready | 本地 baseline 可提交/push 后用于 fresh diagnostic；coverage audit、Step 4 exit、Step 5 与版本签收均未放行 |

## Findings

Resolved before this decision：

1. Blocker — effective-POM/toolchain receipt 使用未全限定的 Maven Help goal 时，
   plugin prefix/version 解析可能随本机 repository metadata 漂移，无法证明同一工具链
   重放。调用已固定为
   `org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom`；receipt 同时绑定
   plugin artifact/hash，24 个 production module effective compiler 被精确复算，
   effective POM negatives=`4/4`。
2. Blocker — root Surefire/Failsafe 的 `${argLine}` early interpolation 让 legacy
   JaCoCo `prepare-agent` 只打印设置日志却不进入 test JVM；missing exec 后 report/check
   skip 仍可 `BUILD SUCCESS`。改为 `@{argLine}` late evaluation 后，legacy focused
   coverage 生成并实际消费 exec，低于既有门正确 `rc=1`；普通 profile=`rc0/no exec`，
   新 `v934-coverage`=`rc0/non-empty exec/exact session`。详见
   `BUG-step4-legacy-coverage-argline-fail-open.md`。
3. Major — report refresh 暴露三个 snapshot-only PreAgg fixture 没有显式关闭 hybrid，
   null watermark 的 production fail-closed 行为被误判为顺序污染。修复只调整 fixture，
   保持 production Matcher 语义；修后单类=`22/22`、三类聚合=`48/48`。详见
   `BUG-step4-preagg-unit-order-isolation.md`。
4. Major — build-only aggregate reporter 加入 reactor 后若被 Central publication 收集，
   会把证据模块误当发布组件。Central plugin 已显式
   `excludeArtifacts=foggy-coverage-report`；reporter 不含 production class，且 24 个
   production module 的发布语义不变。
5. Major — 只在起点记录 Java/Maven/ASM identity 不能覆盖长链运行中的环境漂移。
   run-owned toolchain receipt 绑定 Step 1 raw versions、compiler/JaCoCo/test ASM=
   `9.6/9.7/9.7.1`、Maven runtime/artifact hashes 与 24 个 effective compiler；outer
   runner 在 pre-compile seal、post-children、post-reporter、post-model 重放，reporter
   在生成前后独立重放，summary 前再次重放并复核初始 receipt hash。receipt tamper
   negatives=`5/5`。
6. Major — coverage exec 数、test report 数与 testcase 数若混为一个库存，可产生漏跑
   伪绿。contract/ledger/report inventory 已分别冻结 `23/48` 与
   `773/59/5707/F0E0S0`，Addon `2/6` 单列；local manifest exact=`49/49`。

当前未发现阻止提交 diagnostic-ready baseline 的开放实现 blocker。以上结论只覆盖静态
契约、focused regression 与诊断执行链实现，不覆盖尚未运行的 all-lane 结果。

## Risks / Follow-ups

1. High evidence risk — 尚未从 clean committed/pushed HEAD 运行 fresh all-lane
   diagnostic。Docker、five-DB、required external、Addon、23 exec/48 sessions 的运行时
   provenance，以及 aggregate/per-module/critical-class counters 尚无同一 run 的最终
   证据。提交并推送后必须先确认 `HEAD == origin/main` 且 worktree clean，再启动
   diagnostic。
2. High gate risk — `coverage-thresholds.json` 仍为 `diagnostic-pending`。必须审查
   all-lane aggregate baseline 和 model/critical-class counters 后才可冻结 confirmed
   thresholds；不得降低 Step 1 floor 或扩大 exclusion。完成前
   `can_enter_coverage_audit=no`。
3. Medium compatibility risk — historical Step 2 generator 永久表达 24 个 production
   module，当前 root 因 build-only reporter 为 25-project reactor；直接重跑历史 generator
   应 fail closed。Step 4 只能消费 immutable Step 2 parent，经
   `step2_report_view_tool.py` derived view 与 reviewed overlay 得到当前 536-source/report
   视图，不能放宽或改写历史 24-production 语义。
4. Medium regression risk — legacy argLine BUG 已由 versioned canonical guard 与
   focused 三态动态证据关闭；fresh all-lane diagnostic 仍需把该修复纳入同一 run 的
   exec/session/report provenance。BUG 关闭不能单独关闭 Step 4 coverage evidence。
5. Step 4 尚无 reviewed aggregate、coverage audit、exit evidence 或 acceptance；Step 5
   继续关闭，9.3.4 继续 `in-progress`，不得把本记录解释为版本签收。

## Recommended Next Skills

- 当前动作：提交并 push diagnostic-ready baseline，确认 clean HEAD identity 后运行
  fresh all-lane diagnostic；这一步仍属于 Step 4 实现/测试执行，不启动正式 coverage
  audit。
- `foggy-bug-regression-workflow`：若 diagnostic 暴露缺 exec/session、DB/external、
  aggregate 或 threshold 伪绿，先记录稳定复现和自动化决策，再修复并全链重跑。
- `foggy-implementation-quality-gate`：diagnostic 与 threshold review 引起代码/契约修订
  时，对最终 Step 4 实现重新复核。
- `foggy-test-coverage-audit`：仅在 clean all-lane diagnostic 完成、aggregate 人工 review、
  confirmed thresholds 与 required evidence 均齐备后启动。
- `foggy-acceptance-signoff`：coverage audit 放行后才做 Step 4 feature acceptance；不在本
  阶段创建 9.3.4 version acceptance。

## Decision

- decision: `ready-with-risks`；当前实现已达到“提交/push diagnostic-ready baseline，并
  从 clean committed HEAD 启动 fresh all-lane diagnostic”的质量门槛。
- decision boundary: 本决定不表示 Step 4 passed，不确认 aggregate thresholds，不创建
  exit evidence，不放行 Step 5 或任何 feature/version acceptance。
- can_enter_coverage_audit: no（待 clean all-lane diagnostic、aggregate review 与
  confirmed thresholds）。
- follow_up_required: yes。

## Post-review Update — Diagnostic r1 fail-closed（2026-07-16）

本节是首次 diagnostic 后的 superseding update；保留上文“只放行 clean-HEAD
diagnostic”的历史判断，不把上文改写成 r1 coverage review。该判断的窄边界已被实际执行：
clean/pushed HEAD=`bc100b0f63bd3ff62d1105611dae41741790aedd`，run=
`step4-coverage-20260716-diagnostic-r1`，outer 在 `child-unit` 以
`3115 tests / 1 failure / 0 errors / 0 skipped` fail closed，未进入 aggregate/report/
threshold。

r1 暴露 `PreAggregationDataValidationTest` 的测试 oracle 缺陷：腐化 daily 表时默认 hybrid
因 watermark=null 正确跳过 daily，SQL 实际命中 monthly；另有 raw-vs-raw 与
nullable/empty 伪绿。修复限定在测试 fixture/assertion，不改生产 Matcher、threshold 或
exclusion：corruption 必须命中/修改/恢复 `monthly_category_sales` 的精确一行且差额为
`1000.00`；三个 snapshot 对比必须依次命中 `daily_product_sales`、
`daily_product_sales`、`daily_customer_channel_sales`，空/缺失/null/非数值数据 fail
closed。focused class=`9/F0E0S0`，DataValidation+EdgeCase+Matcher+RequirementBuilder=
`57/F0E0S0`，source SHA-256=
`affb6e415c1770eae7c9ab6f3505ac67acfe03eac1461bd2a48addf3ee790623`。canonical record=
`docs/9.3.4/workitems/BUG-step4-preagg-validation-wrong-table.md`。

相应 successor 只声明测试 source amendment，不改变 report identity/cardinality：
`coverage-report-amendment.tsv` 从 `9 rows / 4 new + 5 changed` 更新为
`10 rows / 4 new + 6 changed`，SHA-256=
`5a1a07e2c47835fa244b90a06334341e13660a305d9eb7c74c64ee2f36a06504`；declared
amendments 从 `14` 更新为 `15`。required 总量仍为 `773/59/5707/F0E0S0`，
exec/session 仍为 `23/48`。Step 2 derived view negatives=`12/12`；successor overlay
negatives=`8/8`，新增 Redis 显式路径错绑负例。top manifest=`49/49`，SHA-256=
`c735e8c1f7b74d72afe2d1d1872128d11a16acbd7373c750e59709624560106e`。

Post-review decision：修复与 successor refresh 可提交/push，并在新的 clean/pushed HEAD
执行 r2；r1 和 focused module `target` XML 均不得冒充最终 evidence。Step 4 继续
`in-progress`，threshold=`diagnostic-pending`，`can_enter_coverage_audit=no`；完整 r2、
aggregate 人工 review、confirmed thresholds 与最终实现质量复核完成前，Step 5、9.3.5
和 acceptance 均保持关闭。
