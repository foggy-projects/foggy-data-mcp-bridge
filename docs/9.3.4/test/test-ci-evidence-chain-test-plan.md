---
doc_role: test-plan
doc_purpose: Define positive, negative and authority verification for the 9.3.4 test and CI evidence chain.
version: 9.3.4
status: in-progress
result: in-progress
step1_result: passed
step2_result: passed
step3_result: in-progress
created_at: 2026-07-14
updated_at: 2026-07-15
---

# 9.3.4 Test and Evidence Plan

## 文档作用

- doc_type: test-plan
- intended_for: test/build/CI implementers and evidence reviewers
- purpose: 把 requirement criteria 映射到可执行 positive、expected-negative 和
  authority evidence；本文件不预填任何通过结果。

## Evidence Rules

- 每个 positive suite 必须有 owning module、FQCN、runner、lane、fresh XML、exact
  testcase count 和 commit/source marker。
- expected-negative 必须验证 wrapper/aggregator 非零退出和稳定错误原因；失败输入
  不得污染后续 positive reports。
- 只有同一 clean commit 的 Step 7 exact authority 可用于签收；diagnostic、failed、
  superseded 或多个 run 拼接结果全部排除。
- v931–v933 exact runner/raw evidence 只作 historical baseline；current-source 通过
  reviewed migration map + v934 successor reports 验证，旧 runner 不要求重放。
- compile/package success 不是测试通过；workflow YAML 存在不是 branch protection
  或 remote required check 通过。

## Step 1 — Inventory / Contract

Positive：

- 扫描 workspace candidates 与 active root reactor modules，输出
  `source-inventory.tsv`；用 discovery-only JUnit test plan、POM variants 和 available
  diagnostic XML 展开 `execution-inventory.tsv` 的 report FQCN/runner/lane/variant/
  db/infra/step，不执行 Step 3 external fixtures。
- 人工 review prefix-only `Test*`/`IT*`、`*IntegrationTest`、`*IT`、nested/dynamic/
  report-producing suites；helper/non-executable source 也保留 owner/reason。
- workspace source↔source inventory 双向差集为 0；non-reactor source 有显式
  disposition。每个 executable reactor source 至少一个 execution row；helper/
  generator 为 0 rows 且有 owner/reason。
- 65 个 diagnostic `@Nested` source 的 `Outer$Nested` expected reports 逐行 review；
  DB/provider/profile variants 以 execution key 区分，全表 key 唯一。
- predecessor migration group 声明 relation 与 old/successor cardinality；observed
  distinct nodes exact，所有 predecessor 都被覆盖、successor key 均存在于 execution
  inventory、edge tuple duplicate=0；mapping edges 不参与测试总数求和。
- historical package inventory 与新 POM-only reporter delta 有 reviewed mapping；
  reporter 不产 main JAR、不进入 Launcher，successor reactor/JAR set 单独冻结。
- `rename-successor-plan.tsv` exact 覆盖 33 sources、62 reports、74 old/new execution
  keys 和 50 predecessor edges；target 只做 `IntegrationTest→IT`，执行语义列不变、
  无碰撞，plan SHA 纳入 confirmed summary。
- contract freeze 记录 manifest SHA、tool/source SHA、reviewer、decision。

Expected-negative：orphan source、未声明 non-reactor source、nested report missing/
unexpected、execution key/edge duplicate、同 key 双 runner、missing owner、optional
无 reason、stale manifest、zero candidate owning module、migration cardinality mismatch/
unmapped、tampered rename successor/policy 均被拒绝。

## Step 2 — Runner Split

Positive：

- root/reactor unit command只由 Surefire执行 unit；all integration/E2E 只由
  Failsafe `integration-test/verify` 执行。
- Step 1 pre-rename baseline immutable；post-rename inventory 写入独立 successor
  目录，parent manifest/rename-plan SHA exact，approved delta 双向差集精确且再次独立
  confirm；Step 1 baseline validator 变 stale 不能被当作 post-rename 失败或被覆盖。
- final `*IntegrationTest` ambiguous source count=`0`；Step 2 raw report execution
  keys 与 confirmed Step 2 successor positive `execution_step=2` subset 完全一致，
  不要求尚未执行的 Step 3 subset。59 个 reviewed outer zero-test containers 进入独立
  structural inventory，不进入 positive execution/test totals。
- fresh XML exact set 等于 positive + structural expected reports；positive suite tests
  大于 0，structural suite tests/testcase/F/E/S 全为 0，且 suite tests 总和等于 testcase
  nodes；同一 FQCN 不同时出现在 Surefire/Failsafe 或 positive/structural 两类。
- SQLite broad integration 与 five-DB parity SQLite 子 lane 的 execution-key subsets
  双向求交为空；相同 `(report_fqcn, sqlite, lane)` execution count 不大于 1。
- all unit + hermetic IT actual pass；DB/Redis/other external required suites 只以
  confirmed Step 2 successor exact manifest 标 `deferred-to-step3`，且 owner/preflight
  唯一。
- renamed FQCN 与 predecessor typed migration map 双向一致；50 个受影响 edges 经
  plan 确定性改写，480 execution refs + 39 structural refs 覆盖全部 519 historical
  nodes，无 criterion 丢失。

Expected-negative：指定 owning test 不存在、0 report、旧 XML、duplicate FQCN、
Surefire 执行 IT、Failsafe 执行 unit、helper module 放宽掩盖 owner 0 tests、缺 parent
link、plan 外 rename、语义列漂移、structural missing/nonzero/无 positive sibling、
typed predecessor ref 漂移或覆盖 Step 1 baseline 均失败。这里的 0 report 指 positive
execution 为 0；reviewed structural report 只能按独立 strict-zero contract 通过。

## Step 3 — Required Database / External Matrix

| Lane | Required positive evidence | Required negative evidence |
|---|---|---|
| SQLite | JDBC artifact/version/hash、physical file/memory coordinate、sentinel、Failsafe parity/capability XML、S0 | wrong artifact/coordinate/sentinel、mutated fixture、missing XML |
| MySQL 5.7 | pinned image/digest、product/version/host/port/catalog、sentinel、QueryFacade/native parity、S0 | unavailable、wrong MySQL major/catalog/sentinel、fixture drift |
| MySQL 8 | pinned image/digest、product/version/host/port/catalog、sentinel、QueryFacade/native parity、S0 | unavailable、accidentally hitting 5.7、wrong catalog/sentinel |
| PostgreSQL 15 | pinned image/digest、product/version/host/port/catalog/schema、sentinel、parity、S0 | wrong major/schema/sentinel、fixture drift |
| SQL Server 2022 | pinned image/digest、product/version、driver-aware host/port/catalog/schema、sentinel、dialect-native oracle、S0 | generic URL misparse、wrong edition/version/catalog/sentinel |

所有库使用同构 sentinel manifest 和幂等 fixture init。支持能力走 positive；不支持
能力必须返回被精确断言的 refusal，不产生 `<skipped>`。五库 required skip 总数为 0。
v934 fixture/profile 必须与 v933 共用 demo 初始化隔离；authority 使用 fresh/run-scoped
storage，禁止把长期 named volume 当作初始状态证明。Pivot pre-aggregation 用例必须真实
执行 rewritten relation/planned SQL 并与 native oracle 对比，单纯断言 SQL 字符串包含
`preagg_` 不计为证据。
此外，Step 2 deferred 中 45 个 required DB/Redis/other external execution 必须全部
actual pass 并清零 required gap；1 个 optional LLM execution 保留 reviewed
disposition。raw report keys 与 confirmed Step 2 successor `execution_step=3` required
subset exact。Step 2/3 required execution key 并集等于该 successor generation 全部 required execution
inventory、交集为空。此 Step 只验证 correctness/identity/
report，不要求或接受 coverage exec。

Required external contract 精确冻结为：Redis `2 reports / 3 testcase`、Mongo/DataViewer
`4/30`、MCP/MySQL57 `8/23`、Vector `2/20`，合计 `7 variants / 16 reports / 76 testcase`；
optional LLM 独立为 `1/1`。collector 除 missing/extra/duplicate/F/E/S/stale 外，还必须拒绝
flaky/rerun outcome、raw-report run-context splice、wrong selector/marker 与 cross-run manifest。

当前 committed Redis、Mongo/DataViewer 与 MySQL57 subset candidates 分别为 `2/3`、`4/30`
与 `8/23`，合计关闭 external `14 reports / 56 testcase / F0/E0/S0`；三条 lane 的 12/12
report/manifest negatives、sensitive probes 和 real INT/TERM/HUP `130/143/129` cleanup 均通过。
三个 final manifest 都是 `complete=false`，且来自不同 run，不能拼接为 full authority，也
不能代替 remaining Vector `2/20`。wrong image/version、dirty state 和 forced cleanup failure
仍是 Step 3 resource-negative backlog。

## Step 4 — Coverage

Positive：

- 接好 agent 后重新执行 all unit、hermetic/SQLite integration 和 Step 3 全 external
  lanes；每 lane 写独立 exec，manifest 绑定
  JaCoCo version、commit、source/classes hash、lane/session。
- build-only aggregator 只产生 aggregate XML/HTML；versioned verifier 精确校验
  expected module/package/class、counter totals 和 reviewed thresholds。
- model 既有 LINE 0.77 / BRANCH 0.62 对 UT+IT merged exec 的 owning-module check
  不下降；reactor baseline 经人工 review 后冻结；critical set 每类达到最终门。
- 同一 source/class 对 coverage report、tested JAR 和 authority manifest 一致。
- Step 4 后 canonical lanes 一次执行即产 exec；coverage collect/check 在 Step 5–7
  只消费 upstream exec，测试 FQCN execution ledger 不出现 coverage-induced duplicate。

Expected-negative：missing/empty/truncated exec/XML、missing expected class/package、
错误 commit/class/source hash、zero counter、低于门槛、阈值被私降、exclusion
漂移、同名 exec 并发覆盖、空 reporter `jacoco:check`、只有 unit 或只有 IT exec 均失败。

## Step 5 — Authority Rehearsal / Immutable Candidate

Positive：

- single runner 串联 inventory/migration、unit、integration、五库、coverage、
  9.3.1–9.3.3 successor regressions、package 和 Launcher audit。
- run root 包含 environment/source/inventory/lane/skip/DB/coverage/regression/JAR
  manifests、raw XML、inner/outer `SHA256SUMS`、deterministic archive/digest。
- protected dirty source/worktree before/after 不漂移；DB fixtures、reports、JAR/
  nested JAR 保持契约；runtime-only image 内 `/app/app.jar` SHA 与 candidate JAR
  exact；archive 上传模型经过 download-and-verify。
- run 明确标 candidate/diagnostic，只写 candidate pointer，不写 final authority pointer。

Expected-negative：`--skip-external-db`/skipTests/skipITs、missing/stale XML、skip
drift、migration gap、report/JAR/image/source hash mismatch、tampered archive、missing
artifact、failed run更新 candidate pointer、candidate 更新 final pointer、credential
scan hit 均失败。

## Step 6 — CI / Release

Positive：

- PR/main reusable workflow 实际运行所有 required jobs；always-run aggregator 名称
  固定为 reviewed stable check，并确认 branch rule 实际引用。
- artifact 名含 commit/run/attempt；package/evidence job 下载并复验所有 upstream
  evidence。
- five-DB cells 各自产带 db kind/SHA/run/attempt 的 artifact；collector 只接受
  exact set/cardinality=5 和 fresh manifests/XML。
- release dry-run 校验 tag SHA，下载同一已测 Launcher JAR/archive/digests；GitHub
  asset直接使用该 JAR，runtime-only Dockerfile COPY 后回读 image JAR SHA，均不重建。

Expected-negative：任一 required job `failure`、`skipped`、`cancelled`；artifact
missing/tampered/duplicate/wrong-kind 或 cardinality!=5；tag SHA mismatch；release
使用 `-DskipTests`、source-building Dockerfile 或生成/嵌入不同 JAR；
legacy partial workflow 冒充 required authority，全部使 aggregator/release 失败。

## Step 7 — Final Authority and Gate Order

在 exact clean commit 完整回放 v934 successor authority，并由独立 reviewer 从
migration map、原始 XML、DB manifests、JaCoCo XML、JAR/image/archive digests 和
CI job states 复算，不只读取 summary。

Final acceptance 至少验证：

- inventory orphan/overlap/duplicate/ambiguous=`0`；
- Surefire/Failsafe 所有 owning lane fresh、exact、required S0；
- 五库 identity/sentinel/parity/capability refusal 全通过；
- aggregate XML verifier、model merged-exec 与 critical coverage checks 全通过；
- 9.3.1–9.3.3 successor mapping/regression、package、Launcher、JAR=image
  same-artifact release 全通过；
- five-DB artifacts exact set/cardinality=5，且每 cell XML/manifest fresh；
- CI required aggregator 的 success 与 failure/skipped/cancelled negative 均有实际证据；
- inner/outer/archive digest、下载复验、sensitive scan 全通过；
- self-check → quality gate → coverage audit → version acceptance 顺序无跳门。

## Requirement Mapping

| Requirement | Primary steps | Minimum evidence |
|---|---:|---|
| INVENTORY | 1/2 | frozen source/execution manifests + migration/hash + subset set proofs + negatives |
| RUNNER-SPLIT / NO-ZERO | 2/3 | unit/hermetic IT + external-deferred/actual union + exact XML + negatives |
| DB-IDENTITY / DB-PARITY / SKIP-ZERO | 3 | five DB manifests/XML/native oracle + unavailable/wrong/skip negatives |
| COVERAGE-AGG / COVERAGE-CRITICAL | 4 | all-lane exec + aggregate XML verifier + model merged check + negatives |
| AUTHORITY / EVIDENCE-IMMUTABLE | 5/7 | candidate rehearsal + clean exact run + two-layer/archive digest + recomputation |
| CI-REQUIRED | 6/7 | exact five-cell artifacts + actual PR/main state negatives + branch-rule evidence |
| RELEASE-SAME-ARTIFACT | 6/7 | tag/source/JAR/image linkage + download/reverify + rebuild-negative |
| REGRESSION-931-933 | 1/5/7 | historical→successor mapping + current-source exact lane summaries/raw XML |
| POST-GATES | 7 | timestamped ordered gate records and version signoff |

## Current Result

- result: `in-progress`
- step1_result: `passed`
- step2_result: `passed`
- confirmed run: `step1-candidate-r8-20260714`
- Step 1：532 workspace sources、820 discovery rows、829 execution keys、519
  predecessor nodes/edges；28/28 expected-negative probes 精确通过。
- Step 2 confirmed successor=`step2-candidate-r8e-20260715`；current required split=
  `724 Step 2 positive + 46 Step 3 deferred + 59 structural`。
- Surefire `677/55/732` reports、`4,890` testcase；Failsafe `47/4/51` reports、
  `315` testcase；combined `5,205`，F/E/S=`0/0/0`，report negatives 各 `20/20`。
- INT/TERM/HUP 实际信号探针为 `130/143/129` 且失败 summary absent；r8d 及其 runner
  evidence 已 superseded，不参与当前结果。
- Step 3 database runner/collector candidate contract=
  `5 cells / 7 variants / 29 reports / 370 testcase`；authority inputs=`66/66`，
  report/final-bundle negatives=`14/14`。
- 最终冻结字节下的真实 run-owned SQLite=`5 reports / 50 tests / F0/E0/S0`，fixture 与
  JDBC JAR before/after hash 一致且 cleanup residue=`0`。完整 runner 在当前主机因冻结
  MySQL57 port 被长期诊断容器占用而 fail closed，未执行正向 lane、未创建 run resource。
- 这些 candidates 是 run-local 且 not authority：四外库 fresh replay、16 个 required
  external 中 remaining Vector `2/20`、DB/resource-state negatives 和 portable archive
  contract 仍未完成。Committed Redis、Mongo 与 MySQL subsets 已分别达到 `2/3`、`4/30`、
  `8/23`，不得与 SQLite 或彼此跨 run 拼成 full authority。
- evidence：
  `docs/9.3.4/evidence/step-1/inventory-contract-freeze-20260714.md`；
  `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`；
  `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`；
  `docs/9.3.4/evidence/step-3/step3-database-matrix-runner-candidate-20260715.md`；
  `docs/9.3.4/evidence/step-3/step3-external-redis-runner-candidate-20260715.md`；
  `docs/9.3.4/evidence/step-3/step3-external-mongo-runner-candidate-20260715.md`；
  `docs/9.3.4/evidence/step-3/step3-external-mysql-runner-candidate-20260715.md`。
- next executable action: 推进 Vector `2/20`，同时补 Step 3 DB/resource-state negatives；在 clean
  host 对同一 commit 完成四外库 remaining fresh `24/320` replay；optional LLM 维持 reviewed
  disposition。
