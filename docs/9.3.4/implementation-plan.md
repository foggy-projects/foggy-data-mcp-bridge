---
doc_role: implementation-plan
doc_purpose: Define the strict Step 1-7 implementation and verification order for 9.3.4.
version: 9.3.4
status: in-progress
created_at: 2026-07-14
updated_at: 2026-07-14
---

# 9.3.4 Implementation Plan

## 文档作用

- doc_type: implementation-plan
- intended_for: project-root-session / build owner / CI owner / reviewer
- purpose: 固定每步输入、代码触点、验证、证据和下一步进入条件。

## Global Entry

- predecessor: 9.3.3 `signed-off / accepted-with-risks`
- predecessor authority: `20260714T084351Z-3271604`
- inherited minimum gate:
  `docs/9.3.3/preconditions/9.3.4-A-minimum-test-gate.md`=`passed`
- protected baseline: current dirty worktree must be inventoried before any rename/POM
  change；authority clean-commit requirement applies at Step 7, not by destructive cleanup。
- predecessor rule: v931–v933 raw evidence、exact FQCN/count 和 runners保持封存；
  9.3.4 用 reviewed migration manifest + successor lanes 证明 current-source 等价回归。
- rehearsal rule: Step 5 可在 protected dirty baseline 演练，只产 candidate；Step 6
  使用 review commit 接线 CI；只有 Step 7 exact clean commit run 是 final authority。
- execution rule: one root progress, strict Step 1→7；each exit must be recorded before
  next step starts。

## Step 1 — 契约与静态库存冻结

Inputs：9.3.3 signoff、root/module POM、all test sources、workflows/scripts、DB
compose、existing JaCoCo/release paths。

Work：

1. 生成 workspace `source-inventory.tsv`（source/reactor membership/kind）和 reactor
   `execution-inventory.tsv`（report FQCN/runner/lane/variant/db/infra/step/required）；
   用 discovery-only JUnit test plan、POM variants 和已有 fresh diagnostic XML 展开
   `@Nested`/dynamic report keys，不提前依赖 external fixture。
2. 人工分类 prefix `Test*`/`IT*`、33 个 candidate `*IntegrationTest` 与 7 个
   `*IT`；记录 helper、nested/dynamic reports 和 external preflight 规则。
3. 冻结 contract schema、skip=0 目标、五库 identity/sentinel、coverage critical
   set/XML verifier、evidence layout、release Docker same-JAR contract 和 stable
   required check name。
4. 建 expected-negative harness：orphan、overlap、duplicate、missing/zero、stale
   report，全部先证明会失败。
5. 生成 9.3.1–9.3.3 predecessor migration groups/edges：每个 historical criterion/
   owning node 映射 declared-cardinality successor execution keys；1:1 默认，split/
   merge 有 relation/rationale/reviewer；POM-only coverage module 对 reactor/main-JAR/
   Launcher count 的预期 delta 单独冻结。
6. 冻结 `rename-successor-plan.tsv`：把 33 个真实 `*IntegrationTest` 展开为 62 个
   report、74 个 old→new execution keys 和 50 个 predecessor edge joins；只规划
   `IntegrationTest→IT`，所有执行语义列保持不变。
7. 记录 worktree/source baseline；不运行 rename 或生产变更。

Verification：inventory generator + independent set-difference review；candidate count
只作 diagnostic，review 后写入 frozen manifest/hash。

Exit：contract=`confirmed`；workspace source 与 reactor execution manifests/hash
冻结；nested/variant execution key 唯一；orphan/overlap=0；optional/non-reactor 均有
owner/reason；external execution 有 exact Step 3 owner；migration group declared/
observed cardinality 一致、unmapped/edge-duplicate=0；negative probes 全生效。
pre-rename baseline immutable，rename plan/Step 2 successor parent-link 已冻结。
Progress 回写 Step 1。

Recorded result（2026-07-14）：`passed`。confirmed run=
`step1-candidate-r8-20260714`；证据见
`evidence/step-1/inventory-contract-freeze-20260714.md`。Step 2 entry=`ready`。

## Step 2 — Surefire/Failsafe 全量分层

Inputs：confirmed immutable Step 1 pre-rename inventory/contract + rename successor plan。

Work：

1. root pin Surefire/Failsafe config/version；Failsafe 绑定
   `integration-test/verify`，UT/IT skip property 分离，显式覆盖
   `IT*/*IT/*ITCase/*E2E/*E2ETest`，并从 Surefire 显式排除这些模式。
2. 严格按 rename plan 处理 `*IntegrationTest`：当前 33 个真 integration→`*IT`；
   更新 references/resources，最终 ambiguous count=0，不允许 plan 外 rename。
3. 移除/禁用 active-by-default multi-db Surefire repeated execution；DB 只由 Step 3
   lane owner执行。
4. 各 module 只保留必要 owning config；helper 0-test 不得掩盖 owning module。
5. 不覆盖 Step 1 baseline；在 `scripts/v934/successor/step2/` 生成 post-rename
   candidate，以 confirmed Step 1 manifest SHA + rename-plan SHA 做 parent link，校验
   source/report/key exact delta、执行语义列不变，并独立 review/confirm。同步 519
   predecessor nodes 到新 successor keys，50 个受影响 edges 不得丢失。
6. 实跑 all-reactor unit 和全部 hermetic Failsafe IT；将 fresh raw report execution
   keys 与 confirmed Step 2 successor inventory 的 `execution_step=2` subset 双向核对，
   校验 freshness、exact testcase count、
   overlap=0。DB/Redis/other external required IT
   只允许以 Step 2 successor exact manifest 标 `deferred-to-step3`，不得标 pass。

Exit：每个 Step 2 execution key 恰由一个 runner 执行；ambiguous/orphan/overlap/
duplicate=0；missing/zero/stale negative 全失败；unit/hermetic IT actual pass；
external deferred set exact 且 predecessor mapping 无丢项。Progress 回写 Step 2。

## Step 3 — 五数据库与外部集成 Required Matrix

Inputs：Failsafe-only DB suites + Step 2 exact external-deferred manifest。

Work：

1. 建同构 sentinel fixture 与 manifest；自动、幂等初始化五库。
2. pin approved image/version/digest；SQLite pin JDBC artifact/version/hash。
3. 扩展 unified preflight 到 MySQL 8 与 SQL Server；使用 driver-aware physical
   coordinate，核对 catalog/schema/sentinel。
4. 将 `MultiDatabaseQueryTest`/parity owner 迁为 Failsafe IT；QueryFacade 与
   independent native oracle 精确比 rows/columns/order/values。
5. DB-specific capability：支持走 positive；不支持走明确 refusal assertion。
6. 启动并核验 Redis/其他 required external fixture，执行 Step 2 deferred
   execution-key subset 中全部 suites；不允许仍有 deferred owner。
7. 每 lane 记录 raw XML、testcases、S0、infra identity、fixture before/after；
   wrong/unavailable/mutated negative 必须失败。本 Step 只证明 correctness，明确不
   承诺 JaCoCo exec；coverage agent/rerun 归 Step 4。

Exit：SQLite/MySQL57/MySQL8/PostgreSQL15/SQLServer2022 全部 required、fresh、
S0；Step 2 external deferred 全部 actual pass；identity/sentinel/fixture exact；
negative probes 全生效；required inventory execution gap=0。Progress 回写 Step 3。

## Step 4 — JaCoCo Unit+IT 聚合与关键类门

Inputs：stable runner split + five DB and all required external lanes。

Work：

1. root central JaCoCo 和独立 UT/IT argLine；每 lane 唯一 exec 文件且保留 root
   UTF-8/JVM args。
2. 用正式 agent 重新执行 all unit、hermetic/SQLite broad IT 和 Step 3 全部 external
   lanes；Step 2/3 无 agent 的 correctness reports 不得充当 coverage evidence。
3. create build-only aggregate reporter，只产 aggregate XML/HTML 和 per-module
   locator；另建 versioned verifier 解析 XML，fail-close expected module/package/
   class/counter/threshold。
4. 将 model owning-module 0.77/0.62 check 改为消费其 UT+IT merged exec；不对空
   aggregate reporter 使用 `jacoco:check`。
5. 首次跑 diagnostic coverage；审查 source/classes/session/exec provenance 后冻结
   aggregate baseline。
6. 对 contract critical set 计算 candidate 0.80/0.70 floor；不足补测试，不以
   exclusion/降阈值过门。
7. expected-negative：missing/empty exec/XML、missing expected class/package、wrong
   class/source SHA、zero counter、低门、阈值被私改、exclusion drift 必须失败。

Exit：reviewed frozen baseline、critical gates 和 exec manifest 全部 pass；
aggregate XML verifier + model merged-exec check实际生效；aggregate/per-module reports
可定位；canonical lane runners 此后默认一次执行即产唯一 exec，coverage verifier
不重跑 suite；negative probes 生效。Progress 回写 Step 4。

## Step 5 — 单一 Authority Runner Rehearsal 与 Immutable Candidate

Inputs：Steps 1–4 stable lanes。

Work：

1. 以 Batch 7 runner 的锁/fresh count/DB/source/JAR/two-layer hash 设计为基础，
   建 `verify-v934-release-gate.sh`；只复用设计，不要求/修改 v933 exact runner。
2. 严格串行/受控并行编排 inventory、unit、integration、five DB、migration-backed
   9.3.1–9.3.3 successor regression、package/Launcher，最后只做 coverage
   collect/check，不重复执行 suites。
3. 在 protected dirty baseline 下核对 source/worktree before/after 不漂移、DB
   fixture、report inventory；package JAR 与本 candidate source/classes绑定。不得
   为 clean 运行破坏性清理。
4. 生成 run root、inner/outer manifest、deterministic archive + digest；上传模型
   先在本地模拟 download-and-verify。
5. 用 runtime-only release Dockerfile 直接嵌入 candidate JAR，从 image 回读
   `/app/app.jar` SHA；source-building `-DskipTests` Dockerfile 不属于 release path。
6. expected-negative：external skip flag、tamper、stale/missing XML、skip drift、
   historical mapping gap、hash mismatch、failed candidate pointer、JAR/source/
   image mismatch。

Exit：单一 exact candidate 可独立复算；状态明确 diagnostic/candidate；failed run
不更新 candidate pointer，任何 candidate 都不更新 final authority pointer；evidence
archive/digest/download/image-JAR verify pass。Progress 回写 Step 5。

## Step 6 — PR/Main/Release CI 接线

Inputs：rehearsed authority commands/candidate artifacts。

Work：

1. 建 reusable workflow，jobs 分为 inventory+unit、SQLite broad integration、
   five-DB contract matrix、coverage collector/check、package/evidence 和 always-run
   aggregator；coverage job 只下载各 lane exec，不重复执行测试；
   两个 SQLite job 消费 confirmed Step 2 successor 中冻结的互斥 FQCN manifests，
   并保留其 Step 1 parent/rename-plan linkage。
2. 五库每个 matrix cell 上传带 db kind/SHA/run/attempt 的 lane artifact；collector
   断言 exact set/cardinality=`5`、XML/manifest freshness。aggregator 再读取 collector
   与其他 required job results，拒绝 failure/skipped/cancelled；check name 稳定。
3. PR/main 实际触发并保留 run evidence；配置/核对 branch protection required
   check。无权限时记录 external blocker，不宣称完成。
4. 收窄/替换 legacy pivot/model-lifecycle workflow authority，禁止重复执行或
   partial green 被误当 release gate。
5. release 在同一 workflow run 调用/依赖 full reusable gate，校验 tag SHA，下载已测
   JAR/evidence，复验 digest；GitHub release 直接发布该 JAR+archive+digests。
6. Docker job 下载同一 JAR，使用 runtime-only release Dockerfile COPY；push 前回读
   image `/app/app.jar` SHA；删除 release path 中 skip-external/skip-test/source rebuild。

Exit：PR/main stable required check 实际通过；缺任一 DB artifact、重复/wrong-kind
artifact 或任一 required job skipped/cancelled 时 aggregator expected-fail；release
dry-run 的 GitHub JAR 与 image embedded JAR 均等于 gate artifact，missing upload
error。Progress 回写 Step 6。

## Step 7 — Clean-Commit 权威回放与后置门

Inputs：Steps 1–6 completed, clean commit, required CI configured。

Work：

1. 在 exact clean commit 执行完整 v934 local/CI authority；不得调用旧 exact runner
   冒充 current pass，也不得拼接旧/candidate run。
2. 从 CI 下载 archive/JAR/image，独立复算 migration coverage、XML/count/skip/
   coverage/DB/source/JAR/image/hash。
3. 更新 requirement、contract、inventory、test evidence、progress 和 roadmap。
4. 严格执行 implementation self-check → formal quality gate → coverage audit →
   version acceptance。
5. 只有所有 critical gate 通过才标 9.3.4 signed-off，并把 9.3.5 标 ready；
   不实施 9.3.5/9.4.0。

Exit：五库/external、coverage、successor regression、package、JAR=image、immutable
evidence、required CI 全绿；无 blocker/high；9.3.4 version signoff完成。签收记录必须
保存 `tested_commit`；authority 后的纯文档回写不冒充新的测试 commit。

## Stop Conditions

- inventory 无法做到唯一 runner/lane；
- predecessor node 无法归入唯一 mapping group，或 successor edge set 不满足 declared
  cardinality；
- DB 无法提供可证明的 product/version/physical identity/sentinel；
- coverage 只能靠降阈值/扩大 exclusion 过门；
- CI 平台无法使 required aggregator 对 skipped/cancelled fail；
- release 无法复用已测 artifact；
- Docker image 无法证明嵌入同一 tested JAR；
- 修复需要 9.3.5 public API 或 9.4.0 production module boundary。

遇到 stop condition：先回写 progress/workitem；不越过当前 Step，不伪造绿色。
