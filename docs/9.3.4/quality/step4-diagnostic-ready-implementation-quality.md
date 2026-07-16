---
quality_scope: feature
quality_mode: pre-coverage-audit
version: 9.3.4
target: STEP4-DIAGNOSTIC-READY
status: reviewed
decision: pass
reviewed_by: Codex root session + independent code review
reviewed_at: 2026-07-17
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

## Superseding Main Quality Gate — r4 fail-closed / remediation ready for r5（2026-07-16）

本节 supersede 上一节的 r4 next action，但保留 pre-r4 结论及 r1–r4 执行历史。
r4 从调用方已确认 clean、committed、pushed 的 HEAD=
`ceea084ca25a9d679ba128e3f6bd50a63322c112` 启动，却在发布 run-owned Git/source seal
之前于 `source-before` fail closed；所有 lane、aggregate、threshold 与 summary 均 absent，
decision=`excluded-from-step4-exit`。失败证据与回归记录分别为：

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r4-fail-closed-20260716.md`；
- `docs/9.3.4/workitems/BUG-step4-source-inventory-filemode-false.md`。

r4 根因是旧 source validator 把 authoritative Git `100644/100755` mode 与
`core.fileMode=false` checkout 的 POSIX executable bit 强制等同；当前 worktree 有
`3,968` 个 tracked file，其中 `3,452` 个为 Git `100644`、worktree 带 executable bit。
修复继续精确绑定 HEAD/index path、Git mode、blob 与工作树内容，只解除错误的 executable
映射；工作树必须为 canonical regular file、当前 euid/egid、NSS 私有主组、single-link、
稳定 stat，且禁止 other-write/special-bit。Git 调用固定关闭 fsmonitor/untracked-cache，
同时独立读取并绑定普通 index flags 与持久 fsmonitor-valid flags；source TSV 字段显式改为
`git_mode`。outer 也会把 source-before/source-after 的失败 JSON 写回 run log。
source seal 清除 ambient/global Git clean 配置并显式复算 raw 与 CRLF-input 两个 candidate，
使真实 CRLF worktree 在 HEAD/index clean-equivalent 时通过；HEAD-fixed attributes 若声明
external clean filter，则在任何 worktree-aware Git hash/driver hook 执行前 fail closed，
negative 证明 hook 未执行。

### Final-byte verification

- Shell/Python 静态：相关 shell `bash -n=7/7`、Python `py_compile=12/12`、
  `git diff --check` 全通过；
- coverage contract：exact `23 exec / 48 sessions`、required
  `773 positive + 59 structural / 5,707 testcase`；contract mutation=`20/20`；
- source identity=`22/22`：包含 Git `100644 -> worktree 0775` 与
  `100755 -> 0644` positive，world-write、hardlink、special-bit、assume-unchanged、
  skip-worktree、fsmonitor-valid、hostile Git environment，以及 shared primary GID、
  foreign `gr_mem`、foreign tracked GID fail-closed；新增 tracked FIFO preflight fail-fast，
  以及 before/after raw stat identity 对 Git-clean-equivalent concurrent rewrite 的拒绝；
- XML/gate/freeze/frozen replay fast negative=`63/63`；successor overlay=
  positive + `12/12`；managed logger=`9 类 / 14 case`；authority parent=
  `2 positive + 14 negative`；Step 2 derived view=`12/12`；
- database successor=`7 variants / 5 cells / 29 reports / 370 nodes + 14/14`；
  external successor=`7 variants / 16 reports / 76 nodes / optional 1 + 12/12`；
- coverage contract diagnostic/formal 双态 SHA-256=
  `5f4b49fd161b4f381a4f8c2238583eb56f27b577973ff93ce0659d84cca75f1d` /
  `58c3479666d0b786ea0ad8327b72b05c9e006dfdb516eacce9098ea83ef4c405`；
- successor manifest=`12/12`，SHA-256=
  `751018ac7c2357cface77dd125c5edc757ad488a500a3c8d9eece0354767381a`；
  top manifest=`54/54`，SHA-256=
  `ebda814b1278f92cf1ba7dc202170e4a77cb7e1f4485e6cb1375d152592a76d0`；
  declared amendments 仍为 `17`，SHA-256=
  `1e4f15c9e403d454fe07404e45b1226eae94f70faa154433a8db39531a305b47`；
- coverage tool / contract-negative / XML tool SHA-256=
  `07a36a2be8edc0afc0ab1031b052c2208a4e32769c4cdb475a397f81e6121ac9` /
  `732d799619461a4b49c8e9bfbb0a3487b107c36110b9e55cd91a405352d0ddb0` /
  `b837314ac4166eeeab94124b53e4f776dcdf8095a3b3915e14e45b81d910d439`；
- overlay contract / overlay tool / outer SHA-256=
  `2d4fe0024caac33199e2ccf87289dd9a262302d3faabad6b038adadb2b2974cb` /
  `a16aadf9c4d540cda8b95d1fc1ded94cf420aa0cfe5a1653b8f90d4cb72e0f51` /
  `254c7603554787ca38d880ac607f7dd4a21ae89064674490858245f0824951c9`。

### Findings and risk decision

- resolved Major：source mode 语义不再误拒绝 `core.fileMode=false` 的 clean checkout，
  也没有通过批量 chmod、跳过 source seal、降低 threshold 或扩大 exclusion 换取绿色；
- resolved Medium：NSS 私有主组与文件 owner/group 的声明最初缺直接失败控制；新增与生产
  路径共用的 pure policy hooks，并以 shared primary GID、foreign explicit member、foreign
  tracked GID 三项 hermetic negative 关闭证据缺口；
- final review open Blocker/High/Medium/Low=`0/0/0/2`；
- accepted Low 1：持久 fsmonitor-valid 的只读探针依赖当前 Linux 上 canonical、root-owned、
  single-link `/usr/bin/echo`；探针缺失或属性漂移会 fail closed，不提供弱化 fallback；
- accepted Low 2：逐文件 descriptor/lstat/fstat/final-stat 与 source-before/source-after
  双 seal 不把同一 UID、可精确恢复 stat 的瞬时恶意替换纳入威胁模型；同 UID 被视为同一
  build authority。若未来要求隔离该主体，应迁移到只读 snapshot/独立 checkout，而不是在
  当前 validator 中声称软件轮询可彻底消除该窗口。

### Superseding decision

- decision: `ready-with-risks`；最终复核 B/H/M/L=`0/0/0/2`，两项 Low 均按上述边界
  accepted。当前状态边界仍只放行 amend/push 本轮 remediation，确认 clean
  `HEAD == origin/main` 后，以全新 run id
  `step4-coverage-20260716-diagnostic-r5` 启动唯一 fresh all-lane diagnostic；
- threshold: `diagnostic-pending`；
- can_enter_coverage_audit: `no`；
- follow_up_required: `yes`；r5 必须完整产生同一 run 的 all-lane、aggregate、critical
  counters 与 sealed observation，之后才可评审 exact thresholds、创建 direct-single-parent
  freeze commit 并运行 fresh formal；
- boundary: 本闸门不表示 r5 已运行、Step 4 已 passed，也不创建 exit evidence，不放行
  coverage audit、Step 5、9.3.5 或 acceptance。

## Superseding Main Quality Gate — r5 fail-closed / successor remediation ready for r6（2026-07-16）

r5 在 tested commit `a35b99cb08f42817d8e75c440f18910b6961841b` 完成 source seal、
Unit、Integration 与 Addon 后，database-state companion 因误选 frozen Step 3 authority
manifest 以 `E_AUTHORITY_MANIFEST` fail closed。database cells、external matrix、aggregate、
threshold、source-after 与 summary 未完成；r5 及其 partial lanes 均为
`excluded-from-step4-exit / non-reusable`。immutable evidence 与 BUG 分别为：

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r5-fail-closed-20260716.md`；
- `docs/9.3.4/workitems/BUG-step4-database-state-successor-authority-manifest.md`。

### Implementation closure

修复不改动 frozen Step 3 字节，而在 Step 4 successor 中增加两个薄适配器：state adapter
只替换 matrix contract 选择；required-report adapter 只把 frozen state verifier argv 精确
改写到 state adapter，非目标 verifier argv 不变。三个运行时消费者均已绑定：

1. database runner 选择 successor state adapter；
2. required runner 选择 successor required-report adapter；
3. Step 4 report inventory 复核同样选择 successor required-report adapter。

overlay 在每次 preflight 中精确检查上述三个 selector，真实执行 state validate、required
rewrite self-test 和 required contract validate。original frozen state control 继续因 predecessor
authority stale 而 fail closed，证明修复没有偷改 predecessor contract/tool。

### Final-byte verification

- coverage contract=`23 exec / 48 sessions`，required=`773 positive + 59 structural / 5,707`；
- contract mutation/source identity=`20/20 + 22/22`；
- XML/gate/freeze/frozen replay=`63/63`；authority parent=`2 positive + 14 negative`；
- managed logger=`14 case`；report inventory negative=`27/27`；overlay=`positive + 12/12`；
- successor database-state standalone static=`12/12`，manifest 绑定 database contract
  `553dabf2b4c266b531fb4ce36f4a498dce223b6449106274a3a2b103ccb775ea`，
  Docker residue=`0/0/0`；
- required adapter validate=`45 reports / 446 nodes / F0E0S0`，contract SHA-256=
  `893ac03231cb4f6fd8ae427c01aa3f9f04267c96e3945814b9b70a3445a58af5`；
- coverage contract diagnostic/formal SHA-256=
  `16677d3ae64a7d24aa5796e7c1bbb8ca5af347d6843878471a7e48bdc52c82af` /
  `d8e7efa775d021d42485f1ffa6cb51a98a3f3f6662b1793e6b06f69852d12463`；
- successor manifest=`14/14`，SHA-256=
  `9fa9ddb23aa36c48961e54393f1fe747bf5d0433645cb1a0529e607db4f211cb`；
  top manifest=`56/56`，SHA-256=
  `be8c4c9c1698674917f1115388d3e7b6a6078d698daf52cb4fa55916166460f9`；
- overlay contract/tool SHA-256=
  `cd691d3d91540dd6ddba0045648493d16feaf9ebf3175da3b9ad15b0e399aadd` /
  `4df218807847beb789dcf1ef748e13bf21f39da071e4bcf7337fe97b78f8c84a`；
- coverage tool / declared amendments SHA-256=
  `bf317dd09bb2f909773dba602ab00037acf112b835a166bfd64ef9709045179a` /
  `187aac883460b259cd002f6c12bb72d8d9824d1e4dd8f12a12959f6866bfccfe`；
- shell `bash -n`、Python `py_compile`、inner/outer manifests 与 `git diff --check` 全通过。

### Findings and decision

- Blocker/High/Medium/Low=`0/0/0/0`；
- 未发现仍使用 frozen required/state verifier 的 Step 4 runtime consumer；剩余 frozen 路径
  仅存在于 frozen Step 3 文件、parent authority/hash 清单与两个 adapter 的受控委托；
- adapter 未改变 Step 3 totals、negative semantics、coverage denominator、threshold floor、
  formal dual-state projection 或 formal commit allowlist；
- decision=`pass / ready-for-commit-and-fresh-r6`；仅放行 commit/push、clean source seal 与
  fresh r6，不表示 Step 4 passed；
- threshold=`diagnostic-pending`；`can_enter_coverage_audit=no`；Step 5、9.3.5、coverage
  audit 与 acceptance 继续关闭。

## Superseding Main Quality Gate — Unit fixture remediation r3（2026-07-17）

本节只审 Unit MySQL replacement、profile isolation、closed connection receipt 与 fresh r3
最终字节，不改写 r1–r7 或 Unit r1/r2 的 immutable failure。tested commit=
`50161a0a869430e353f3933d9bb00dda59d9c4b1`；run=
`step4-unit-fixture-quality-20260716-r3`。

### Verification

- source before=after=`3,982 files` /
  `1db06cc18bb86c288ffa79e7094e3b9e509d866ddc23b6c93bcaaa0422b99eb2`；
- 唯一 Surefire invocation=`681 positive + 55 structural = 736 raw / 4,941 testcase /
  F0E0S0`；736 reports 与 12 fixture artifacts 独立逐哈希复算 bad=`0`；
- fixture before=after，closed receipt scope=`unit-maven-invocation`，18/18 connections
  全部为 restricted `v934_unit`；fixture negatives=`36/36`、receipt schema=`4/4`、
  lifecycle=`5/5`；
- run-owned cleanup=`0/0/0` 且 evidence-window 结束时 port free；demo exact container 仅在
  window 外恢复为同一 ID `running/healthy`；
- coverage contract validate=`23 exec / 48 sessions / 773 positive + 59 structural / 5,707`；
  successor overlay PASS；top manifest=`60/60`、successor manifest=`14/14`；Shell/Python
  syntax 与 `git diff --check` PASS；
- datasource placeholder 只由 `foggy-dataset` test resource 消费；outer/callback 的双层
  environment guard 是受契约与负向探针覆盖的 defense-in-depth，不是重复的隐式配置面；
  callback 后的 provisioner `foggy` 控制连接明确位于 closed Maven observation window 外。

### Findings and closure

- implementation code、测试、fixture/connection/cleanup evidence：open
  Blocker/High/Medium/Low=`0/0/0/0`；
- 首轮正式复核唯一 Medium 是权威 progress、quality、BUG checklist 与 r3 evidence 尚未同步；
  本次 check-in 已同步相关权威文档并链接 immutable r3 record，该 finding 已关闭；
- `unit_mysql_fixture_tool.py` 虽集中承载该机器契约，但职责已拆分为 derive/callback/build/
  verify/negative/cleanup/lifecycle 子命令与小函数；未发现为追求绿色而降低 threshold、扩大
  exclusion、借用 ambient listener 或覆盖其他 profile 的路径。

### Decision

- decision=`pass / ready-for-commit-and-fresh-r8`；final B/H/M/L=`0/0/0/0`；
- 仅放行提交/push 本轮 r3 evidence 与文档 closure、证明 clean
  `HEAD == origin/main`，然后运行唯一 fresh r8 all-lane diagnostic；
- threshold=`diagnostic-pending`，`can_enter_coverage_audit=no`；
- 本闸门不表示 r8、aggregate/critical review、threshold freeze、fresh formal、coverage
  audit、Step 4 exit、Step 5、9.3.5 或 acceptance 已通过。

## Superseding Main Quality Gate — r8 lifecycle contract remediation（2026-07-17）

本节只复核 r8 暴露的 Unit lifecycle static contract drift、对应 expected-negative 与
authoritative failure/writeback；不把 r8 前置绿色、Unit r3 或任何历史 partial lane 拼接成
Step 4 exit。

### Failure boundary and implementation

- r8=`step4-coverage-20260716-diagnostic-r8` 从 reported clean/pushed HEAD
  `3a3dd21a9aa956f0bedfffcf648ebb1cac0b756a` 启动，在 run-owned Git/source identity 与所有
  lane 前因 validator 仍要求旧 Unit direct finalizer token，于 `bootstrap-negative` fail
  closed；r8 excluded/non-reusable；
- 修复将 Unit/Integration 拆成 executable physical/logical contracts，排除 full/inline
  comments、heredoc body 与跨行 quoted decoy；Unit fixture-aware wrapper、两 runner
  flush→green、disarm→PASS 使用 exact executable slices；
- trap allowlist 覆盖 EXIT/numeric-0、链式/转义/间接引用；关键 lifecycle function reference
  只能以 canonical command 出现，拒绝尾分号重复 install/disarm、early return、logical-or
  close bypass 与 function shadow；
- 最终 fail-closed 边界以 whole-runner raw-byte SHA-256 seal 绑定 Unit/Integration exact
  bytes；真实文件使用 `read_bytes()` 直接哈希，再 strict UTF-8 decode。false/subshell outer
  context、heredoc source、eval shadow 与 CRLF universal-newline normalization 均无法旁路。

### Verification and adversarial review

- focused lifecycle suite 发布唯一 PASS：原 dynamic=`9 类 / 14 case` 全保留；Unit
  shape/source-seal=`13/13 + 3/3`；Integration=`11/11 + 5/5`；
- source-seal negatives 实际覆盖 Unit false/subshell/CRLF，以及 Integration trap
  false/subshell、flush false、heredoc-source shadow 与 eval shadow；
- lifecycle script SHA-256=
  `8dcc679c2762ff8908b3bc26e8dfb0553a083eb75003dd80366fd82e78d8ed9b`；top manifest=
  `60/60` / `0c4e6c18f4af0a2c35418604ce80d20828ceb4c275375b7d12461b4683553a81`；successor=
  `14/14` / `acb580e92a72eb407f31f5d6f9a8139a3509f3a0bfbf58537922465f4086a112`；
  coverage contract、successor overlay、Shell syntax 与 `git diff --check` 均通过；
- 两路独立 review 曾依次发现 comment/dead-code decoy、numeric-0 trap、quoted heredoc、
  continuation、function shadow、outer false/subshell、source/eval 与 CRLF byte gap；每项均有
  可复现 mutation 与实现闭合，最终两路复核都为 B/H/M/L=`0/0/0/0`。

### Decision

- decision=`pass / ready-for-authoritative-writeback-and-fresh-r9`；final
  B/H/M/L=`0/0/0/0`；
- 只放行本轮 script/manifest、r8 immutable evidence、BUG 与权威文档的 commit/push，随后
  必须证明 clean `HEAD == origin/main`，再使用新 run ID 启动 fresh all-lane r9；
- threshold=`diagnostic-pending`，`can_enter_coverage_audit=no`；
- 本闸门不表示 r9、aggregate/critical review、threshold freeze、fresh formal、coverage
  audit、Step 4 exit、Step 5、9.3.5 或 acceptance 已通过。
