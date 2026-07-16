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

## Post-review Update — Diagnostic r2 fail-closed / remediation ready for r3（2026-07-16）

本节 supersede 上一节的 next action，但保留 r1 quality boundary 作为历史判断。r2 从
clean/pushed HEAD=`0101a44a07784bf6b484d490c7fb508727fbab70` 启动，Unit=
`681 execution + 55 structural / 4,941 testcase / F0E0S0`；Integration=
`312/F1E0S0`，唯一失败为 `PreAggregationL2CacheIT`。outer 在
`child-integration` fail closed，summary absent，database/external/Addon/aggregate/
threshold 均未执行。failed evidence=
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r2-fail-closed-20260716.md`，decision=
`excluded-from-step4-exit`。

质量复核确认两项修复均限定在测试 fixture/assertion，不修改生产 Matcher、hybrid 默认、
threshold 或 exclusion：

- L2 测试显式选择 snapshot-only，断言 exact preAgg name/table、raw negative、lookup/write
  SQL+params key、二次 hit 与单次 write；focused=`1/F0E0S0`，与 `PreAggregationIT` 组合=
  `30/F0E0S0`，source SHA-256=
  `bb5d6884401579447382587861e197feac2884ab701065d7826fe7a676e0c313`；
- Pivot legacy fallback 两次稳定 RED；只在 legacy 分支关闭 hybrid，V934 FULL 分支保持
  production 默认，并用 exact relation token 防止 legacy 表名子串冒充 V934 表。legacy 与
  V934 SQLite focused 各 `1/F0E0S0`，source SHA-256=
  `5c6dcd3b4afba4d93a93c1af47cc4484a1dfc9976da92669bfbcc4529ede6155`。

身份级联已与最终源码一致：coverage amendment=`11 rows / 4 new + 7 changed`，SHA-256=
`937666fc1926ec1c4764ebb50d4b4d4bdd1f1013f0d63cc77d9a1856fae153d2`；declared
amendments=`17`，SHA-256=
`be9a2d553499f799d5dc81cee353397799ad3f01d2923c6aeccb82fdb9bd7548`；top manifest=
`51/51`，SHA-256=`348ade918a5020b9b65b9fb93e4bb7034e73f197c8545c7cbbfeb3d34d044ac1`；
successor manifest=`12/12`，SHA-256=
`6ac8a24dd983c1929f6d21430f57adca503893e69b368b37a08731f5a5355948`。positives
coverage=`773/59/5707`、Step 2=`724/59`、DB=`7/29/370`、overlay required=`45/446`、
Addon=`2/6`；negatives coverage/view/successor/DB=`8/12/8/14`，全部通过。

Post-review decision：`ready-with-risks`。当前未发现阻断提交/push 最终修复与 identity、并从
clean HEAD 执行 r3 的实现 blocker。该结论不表示 Step 4 passed；threshold 仍为
`diagnostic-pending`，`can_enter_coverage_audit=no`。只有 r3 完整 all-lane observation、
aggregate 人工 review、confirmed thresholds 和最终实现质量复核完成后，才可考虑 coverage
audit；Step 5、9.3.5 与 acceptance 继续关闭。

Non-blocker coverage-audit follow-up：现有证据分别覆盖 hybrid rewrite/matcher、snapshot L2
identity 与执行顺序，但尚无单一集成用例直接验证“带已发布 watermark 的 hybrid UNION SQL
及其参数作为 L2 cache key”。这不阻断 r3；coverage audit 应决定是否登记为后续测试增强，
不得为补该用例降低当前门槛或扩大 exclusion。

## Pre-r4 Implementation Review Candidate — Diagnostic r3 fail-closed / Cdiag converged（2026-07-16）

本节 supersede 上一节的 r3 next action，但不改写 r1/r2 已执行 quality
history。本节只登记主线程正式 quality gate 之前的检查候选面，不声称已再次
执行 `foggy-implementation-quality-gate`。

r3 从 clean/pushed HEAD=`e16693297239f2a861f3b93b3de60c1bb783bda0` 启动，
run=`step4-coverage-20260716-diagnostic-r3`。Unit=
`681 positive + 55 structural / 4,941 testcase / F0E0S0`，但 outer 在
`child-unit` leader 返回后检测到 live process-group member 并 fail closed。后续
Integration/database/external/Addon/aggregate/threshold 未执行，summary absent。该失败封存于
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md`；
root cause 与回归策略登记于
`docs/9.3.4/workitems/BUG-step4-child-run-log-tee-residue-race.md`。r3 Unit 结果不得
拼接成 Step 4 绿色。

### Candidate changed surface

- Unit/Integration 从未捕获的 `exec > >(tee ...)` 改为共享 managed FIFO
  logger；保存 logger PID，关闭写端、flush/reap 完成后才可发布 child green；
- `run_log_lifecycle_lib.sh` / `run_log_lifecycle_negative_test.sh` 定义 slow、
  nonzero、timeout、exit、clean-group 与 persistent-residue 边界；
- outer/launcher 的 child ready/completion receipt 绑定 PID/PGID/SID/starttime/boot-id，
  并要求 exact schema、mode/link/inode/hash 和 kill-before bytes-safe member snapshot；
- `coverage_xml_tool.py` / `coverage_xml_negative_tool.py` 对 child lifecycle、
  formalization delta、canonical gate/candidate/final/status path 进行 typed 复算；
- green `run-status.env` 被收紧为所有证据验证后的最后一次原子发布，
  禁止发绿后再执行可失败校验；
- formal 必须对 confirmed threshold 指向的真实 diagnostic run 执行
  frozen replay，从 diagnostic commit 取回历史 threshold/contract blob；
  `Cfreeze` 只允许一个 direct-single-parent child，拒绝 merge、multi-commit、
  shallow、replace-ref、graft 和 allowlist 外 delta。

### Main quality gate inspection objects

1. 确认 managed logger 在 success、ordinary failure、INT/TERM/HUP、logger timeout/
   nonzero 与持管道 descendant 路径都不留绿色 summary，也不吞原始 child
   exit code。
2. 确认 PID reuse、ready receipt 替换/硬链接/模式漂移、fake starttime 不会导致
   错杀或 fail-open，且 residue snapshot 能处理非 UTF-8 `comm/cmdline`。
3. 确认 diagnostic/formal 双态没有交叉产物；canonical path、typed child lifecycle、
   formalization delta 和 public final verification 的约束没有 alternate-path 绕过。
4. 确认 success status 确为 final publication；seal 失败后 summary/final absent，只可留
   failed status，不存在可观察的短暂伪绿。
5. 确认 formal 的 frozen diagnostic replay 不信任合成 run id，source/worktree clean 校验
   不受 assume-unchanged/skip-worktree、replace refs、grafts 或 shallow history 绕过，且
   direct-single-parent delta receipt 可独立重算。
6. 确认新文件、successor overlay 和 top `SHA256SUMS` 级联与实际字节一致，
   最终快测全绿后才允许 commit/push 与 fresh r4。

### Candidate decision

- decision: `pending-main-quality-gate`。Cdiag 实现面已收口，但主线程尚未对
  上述最终字节执行正式实现质量闸门，因而不继承 r2 的
  `ready-with-risks` 作为当前放行决定。
- next action: 主线程最终快测→正式质量闸门→identity/manifest 级联→
  commit/push→确认 clean `HEAD == origin/main`→fresh r4 all-lane diagnostic。
- threshold: `diagnostic-pending`；can_enter_coverage_audit: `no`。
- boundary: 不表示 r4 已运行、threshold 已 confirmed、coverage audit 已开始、
  Step 4 已 passed，也不放行 Step 5、9.3.5 或 acceptance。

## Superseding Main Quality Gate — Cdiag pre-r4（2026-07-16）

本节是对上述 candidate 的主线正式实现质量闸门，不改写 r1/r2/r3 历史。
检查对象是最终 Cdiag 字节：managed logger、child/process identity、Git 环境隔离、
source/context/manifest 四元绑定、exact 23 raw exec 重放、pending/formal 状态机与双层
identity manifest。

### Final-byte verification

- Shell/Python 静态：相关 shell `bash -n`、五个 Python 工具 `py_compile`、
  `git diff --check` 均通过；
- logger lifecycle：9 类 / 14 case 通过，精确为 slow=`2`、nonzero=`2`、
  timeout=`1`、PID reuse=`1`、early signal=`3`、capture failure=`1`、exit=`2`、
  clean group=`1`、persistent residue=`1`；
- canonical provenance：XML/gate/freeze/frozen-replay negatives=`63/63`，包含
  source-before/after/context SHA、manifest context/source/not-before/Git 四元组、expected
  Git-head splice 与 raw exec byte/mtime/missing/extra；
- Git identity：coverage contract mutations=`20/20`，真实 source Git identity=
  `7/7`；shallow、graft、replace 与 17-key hostile ambient control 全部 fail closed；
- parent/successor：authority=`2 positive + 14 negative`、Step 2 derived view=`12/12`、
  successor overlay=`12/12`、database=`14/14`、external=`12/12`；
- identity：top manifest=`54/54`，SHA-256=
  `589a7d67f35a0f09c7f1a026dbbf07e56dc89f099ca51291418cd1c6cc5fd077`；
  successor manifest=`12/12`，SHA-256=
  `961e50350cef1c7984c6ff6b4fd0b5716ac5bb87d42271a3478233258b30784f`；
  declared amendments=`17`，SHA-256=
  `1e4f15c9e403d454fe07404e45b1226eae94f70faa154433a8db39531a305b47`。

### Findings and risk decision

- resolved critical：所有安全边界的 Git 子进程改为 non-Git allowlist，outer 在首个
  Git/lock 之前清除 ambient `GIT_*`；子 launcher、frozen validator 与 overlay 不再
  看到不同 repository view；workitem=
  `docs/9.3.4/workitems/BUG-step4-git-environment-override-bypass.md`；
- resolved critical：XML live/frozen validation 不再只信 manifest 自述，必须从
  canonical run root strict-read source/context，并逐字节重放 exact retained 23 exec；
  workitem=`docs/9.3.4/workitems/BUG-step4-source-context-crosslink-gap.md`；
- resolved major：Unit/Integration 不再使用无主的 process-substitution `tee`；所有
  success/failure/signal 路径均先 close/reap logger，再允许 durable child status；
- open Blocker/High/Medium=`0/0/0`；
- accepted Low risk：diagnostic 与 formal 之间必须保留原 run 的 23 个 raw exec 原字节。
  任一缺失、mtime/owner/link/mode/inode/SHA 漂移都会 fail closed；这是有意的
  evidence-retention 约束，不是可降级的 fallback。

### Superseding decision

- decision: `ready-with-risks`；放行边界仅为提交/push 最终 Cdiag、确认 clean
  `HEAD == origin/main`，然后执行唯一 fresh r4 all-lane diagnostic；
- threshold: `diagnostic-pending`；
- can_enter_coverage_audit: `no`；
- follow_up_required: `yes`；fresh r4 完整通过后才可评审 exact observed
  thresholds、生成 direct-single-parent `Cfreeze` 并运行 fresh formal；
- boundary: 本闸门不表示 Step 4 passed，不创建 exit evidence，不启动
  coverage audit、Step 5、9.3.5 或 acceptance。
