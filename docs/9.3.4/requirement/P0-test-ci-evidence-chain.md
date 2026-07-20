---
doc_role: workitem
doc_purpose: Define the 9.3.4 test runner, database, coverage, CI and immutable release evidence requirements.
version: 9.3.4
priority: P0
status: in-progress
acceptance_status: not-started
created_at: 2026-07-14
updated_at: 2026-07-20
---

# P0 测试与 CI 证据链

## 文档作用

- doc_type: requirement
- intended_for: project-root-session / build owner / CI owner / signoff owner
- purpose: 冻结 9.3.4 的目标、边界、fail-closed 规则和版本完成门。

## 背景

9.3.3 已用严格 wrapper 证明 lifecycle authority，但全仓仍存在结构性伪绿色：

- root 仅全局排除 `*IT/*E2E`，Failsafe 只在少数专用 profile 中启用；当前 workspace
  互斥静态命名候选为 unit-pattern 492、`*IntegrationTest` 33、`*IT` 7，共
  532 个 source files；其中两个 benchmark WIP 不在 active root reactor，reactor
  diagnostic=`490+33+7=530`。另有 65 个 source 含 `@Nested`，source 数不等于
  report/execution 数；尚无两层 inventory authority。
- `multi-db` active-by-default 以 Surefire 重复跑默认/MySQL/PostgreSQL，SQL
  Server execution 被注释，MySQL 8 未纳入统一 required contract。
- JaCoCo 只在 model profile 配置 bundle LINE 0.77 / BRANCH 0.62 和单类门，
  没有 reactor unit+IT aggregate 或五库 exec provenance。
- PR/release 仍有 `failIfNoSpecifiedTests=false`、`--skip-external-db`、
  `-DskipTests` 重建和 artifact missing=`warn` 通道。
- release JAR 不是完整 required gate 同一 commit/evidence chain 验证的产物。

上述源码数量是 2026-07-14 只读盘点候选，不是测试执行结果；Step 1 必须重新
生成并 review 后冻结 authority inventory。

## 目标

1. Surefire 只运行 unit；Failsafe 只在 `integration-test/verify` 运行 IT/E2E；
   source inventory 与 execution-key inventory 分离，每个实际 report/DB variant 有
   唯一 module、runner、lane 和 required/optional 归属。
2. required DB matrix 固定为 SQLite、MySQL 5.7、MySQL 8、PostgreSQL 15、
   SQL Server 2022，全部验证产品/版本/物理 identity 和同构 sentinel fixture。
3. missing/zero/duplicate/orphan/stale report、DB unavailable/wrong identity、
   skip drift、coverage missing/low、matrix skipped/cancelled 全部 fail closed。
4. 在 runner/infra 稳定后，重新带 JaCoCo agent 执行 unit + SQLite integration +
   五库/external contract lanes；冻结经审阅 baseline，并对 9.3.1–9.3.3
   lifecycle/isolation 关键类设独立门。
5. 建立单一 release authority runner，绑定 source SHA、测试 XML、数据库、
   coverage、package/JAR 和两层 checksum，输出不可变 evidence archive。
6. PR/main/release 共用 stable required aggregator；任一 required job
   failure/skipped/cancelled 都使 aggregator 失败。
7. release 下载并发布同一 gate 已验证 JAR/evidence；GitHub asset 与 Docker image
   内 `/app/app.jar` SHA 完全一致，不再跳测重新 package 或在 Dockerfile 内重建。

## 约束

- 保护 9.3.1–9.3.3 当前成果；禁止 reset/checkout/clean 或覆盖式还原。
- Step 1–7 严格顺序；首次 count/coverage 发现只能生成 diagnostic candidate，
  review 后才能冻结，runner 不得自动接受当前值。
- 9.3.1–9.3.3 exact run、FQCN、report/count 和 evidence 均为封存历史；Step 2
  重命名后必须用 reviewed predecessor migration manifest 建 successor regression，
  不修改旧 runner，也不要求旧 Batch 7 exact command 在新 source 原样通过。
- Step 2 只把 unit 与无外部依赖 IT 实跑作为 exit；DB/Redis 等 required suite
  必须有唯一 owner/preflight/earliest-step=`3`，并在 Step 3 全部实际执行。Step 3
  不预产 coverage exec；所有 required lanes 在 Step 4 接好 agent 后重新执行。
- 上述 DB/external → Step 3 归属仍是默认通则。r7 暴露的 Unit 隐式 MySQL 依赖只允许
  一个 `9.3.4-only` 例外：Step 4 以 pinned、run-owned MySQL 5.7 替换执行完整 frozen
  Unit lane，而不是把该例外推广到新的 execution key、其他 runner 或后续版本。机器权威为
  `scripts/v934/step4/unit-mysql57-fixture-contract.json`；其中列出的 6 个已知
  execution key / 11 个 testcase node 是 r7 已确认清单，不代表已穷尽证明其余 Unit
  suite 绝无数据库访问。fixture authority 因此作用于完整 `681 positive + 55 structural /
  4,941 testcase` 的唯一 Maven invocation。
- Step 2 confirmed inventory 的 execution identity 与 cardinality 仅按历史结构保留；r7 后
  Step 2 Unit 绿色不得再作为 correctness evidence。9.3.4 的 Unit correctness 必须由同一
  fresh Step 4 全 lane replacement run 重新证明，失败、partial 或跨 run 结果不能补写
  Step 2。分类迁移债务记录于
  `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`，并必须在
  9.3.5 版本验收前关闭。
- helper reactor module 可无指定测试，但 owning module 必须产生 fresh、exact
  FQCN/count XML；workflow authority 不直接暴露
  `surefire.failIfNoSpecifiedTests=false`。
- required release lanes 最终 skip budget 为 0。数据库能力不支持必须以明确的
  positive fail-closed assertion 通过，不能 assumption skip；非执行型 generator
  应移出 required inventory。
- 现有 model coverage 0.77/0.62 只能保持或提高；任何 threshold/exclusion 变化
  必须有 workitem、review 和 evidence，不得为过门静默下调。
- JaCoCo UT/IT 使用独立 argLine 和 exec 文件，不能覆盖 root UTF-8 argLine；
  合并前核对 commit/source/class/tool identity。aggregate XML/关键类门由版本化
  fail-closed verifier 检查，不能让无 production class 的 reporter 空 check 通过。
- DB evidence 不写 credential 或含密码 JDBC URL；日志/manifest/archive 先做
  sensitive scan。
- Step 5 只演练完整 runner 并产 immutable candidate，允许 dirty local baseline，
  只能标 diagnostic/candidate 且不得更新 final authority pointer；Step 7 authority
  只接受 exact clean commit。
- `latest-run-id` 仅便利指针；signoff 必须引用 exact run id + root/archive digest。
- 9.3.4-A 历史状态只引用，不复制或重写其 consumer-side authority。

## 非目标

- 不重复五次运行整个 3000+ SQLite suite；unit 跑一次，SQLite 承担广覆盖
  integration，五库重复固定 preflight/parity/capability contract。
- 不修改或重释 9.3.1–9.3.3 历史 raw evidence 来适配新测试名；只新增有审阅哈希的
  successor mapping/current-source evidence。
- 不为提高 coverage 做无关生产重构。
- 不引入 9.3.5 QueryFacade/phase/public API 改造。
- 不做 9.4.0 生产模块拆分、SPI v2、BackendProvider 或 Addon TCK。
- 不把 branch protection 的外部配置假装成仅靠仓库 YAML 已完成；必须保留实际
  required check/规则证据，环境无权限时标 blocked 而非绿色。

## External Contract Boundary

- workspace source inventory、reactor execution-key inventory、lane summary、skip
  manifest、coverage summary 和 evidence root manifest 是 9.3.4 的发布契约；字段/
  路径变更需兼容或版本化。
- required aggregator check name 在 PR/main/release 保持稳定，建议
  `required / test-ci-evidence-chain`。
- evidence run key 至少包含 commit SHA、workflow run id、attempt；artifact 名
  不可覆盖同名 run。
- five-DB matrix 每个 cell 必须产出带 db kind/SHA/run/attempt 的独立 lane artifact；
  collector 精确断言 `{sqlite,mysql57,mysql8,postgres15,sqlserver2022}` 和
  cardinality=`5`，不能只信 matrix job 聚合后的 `needs.result`。
- release asset 至少包含已测 Launcher JAR、evidence archive、archive digest 和
  root manifest digest；Docker image 必须直接 COPY 该 JAR并回读核对 SHA。

## 验收标准

| ID | 验收标准 | 严重级别 |
|---|---|---|
| INVENTORY | workspace source 与 reactor execution 两层 manifest 完整；nested/variant key 唯一；orphan/overlap/duplicate=0 | critical |
| RUNNER-SPLIT | Surefire 只跑 unit，Failsafe 只跑 IT/E2E；`*IntegrationTest` 双命名清零 | critical |
| NO-ZERO | missing class、0 owning tests/report、stale report 均 fail | critical |
| DB-IDENTITY | 五库 product/version/URL coordinate/catalog/schema/sentinel 精确 | critical |
| DB-PARITY | 五库 required QueryFacade/native rows/columns/order/values parity 或明确 capability refusal | critical |
| SKIP-ZERO | required lane skip=0；skip/assumption 漂移 fail | critical |
| COVERAGE-AGG | Step 4 重跑的 unit+IT+五库/external exec provenance 可追；aggregate XML verifier 与 module checks 生效 | critical |
| COVERAGE-CRITICAL | lifecycle/isolation 关键类独立门；missing exec/低门/私降阈值 fail | critical |
| AUTHORITY | 单一 runner 串联 inventory/test/DB/coverage/regression/package，source/DB/report/JAR/image 不漂移 | critical |
| EVIDENCE-IMMUTABLE | inner/outer/archive digest 可复验；tamper/missing artifact fail | critical |
| CI-REQUIRED | PR/main stable aggregator 实际出现；五库 artifact exact set=5；required job failure/skipped/cancelled 均 fail | critical |
| RELEASE-SAME-ARTIFACT | GitHub asset 与 Docker `/app/app.jar` 使用同一已测 JAR SHA，不跳测重建 | critical |
| REGRESSION-931-933 | historical criteria/FQCN 全部映射到 9.3.4 successor lanes，current source 等价回归全绿 | critical |
| POST-GATES | progress/self-check→quality→coverage→version acceptance 顺序闭环 | major |

## 完成定义

- 所有 critical 项有实际 local/CI evidence；关键项缺口不能降级为
  accepted-with-risks。
- 五库、coverage、successor regression、package、Docker embedded-JAR、archive
  download-and-verify 在同一 clean commit authority 全绿。
- expected-negative 至少覆盖 inventory、runner、DB、skip、coverage、CI state、
  evidence tamper 和 release skip flag。
- README、requirement、contract、progress、test、quality、coverage、acceptance、
  roadmap 与 root testing guidance 回写同一签收状态。

## Current Progress

- version status：`in-progress`；Steps 1–3=`passed`；Step 4=
  `in-progress / r27 high-water recovery / Cdiag→fresh-r28`；
- historical Step 4 authority/formal-r4/r24/Cfreeze 保留；fresh formal-r7 因仓外 CALCULATE catalog
  依赖 fail closed 并永久 excluded；r25 保持
  `pre-remediation / superseded / non-candidate`；修复后的 Cdiag `4fe86929…`、isolated r4、fresh
  diagnostic-r26 与 direct-child Cfreeze 已通过。formal-r8 完成全部 child/report inventory 后因 Git
  `100644` Python 工具被直接执行而 rc126，永久 failed/excluded；三处 interpreter 修复、runner
  raw/逻辑命令流双封印、覆盖四工具/七调用的 semantic/Git-mode dispatch gate 与 machine reset 已完成，尚不是 replacement formal、Step 4 feature acceptance 或
  version acceptance；
- Step 5–7=`hold / execution closed`，version acceptance=`not-started`，9.3.5=`queued`；
- Unit MySQL classification DEBT 继续 open，最终 9.3.4 临时放行由 Step 7 决定。

### Historical Step 4 diagnostic ledger

- r18 snapshot：Step 4=
  `in-progress / diagnostic-r18 PASS / threshold candidate not authorized / Pivot remediation
  verified / pre-Cdiag quality PASS / replacement Cdiag pending`（当时不是 `passed`）；
- Step 2 confirmed successor：`step2-candidate-r8e-20260715`，
  `724 positive + 59 structural` 已由 Surefire/Failsafe exact 覆盖，testcase=`5,205`，
  F/E/S=`0/0/0`；其 identity/cardinality 作为 Step 2 历史结构保留，Unit lane correctness
  已由 r7 判定必须交给 fresh Step 4 replacement evidence，不再复用旧绿色；
- Step 3 formal parent=`step3-required-20260716-final-r4`，tested commit=
  `ce3d70c391c7b8bd8046fe66dde0ad568d66601e`；five-DB=`29/370/F0E0S0`，
  required external=`16/76/F0E0S0`，exact union=`45/446`，gap/overlap/extra=
  `0/0/0`；DB state=`18/18`、Redis state=`4/4`，Addon companion=`2/6/F0E0S0`
  且不计入 union；1 个 optional LLM=`reviewed-optional-excluded`；
- Step 3 implementation quality、test evidence coverage 与 feature acceptance 已按序
  完成；feature decision=`accepted`，但 version acceptance 仍为 `not-started`；
- r8d authority 因 signal fail-open 作废；r8e 已以 INT/TERM/HUP=`130/143/129`
  动态探针证明 durable fail closed；
- Step 4 冻结结构仍为 exact `23 exec / 48 sessions`、required overlay=
  `773 positive + 59 structural / 5,707 testcase / F0E0S0`、Addon companion=`2/6`；
  formal-r3 在完成该全量结构后以 aggregate branch `26110/44870 < 26111/44870`
  fail closed，line exact `54624/76830`；唯一差异是
  `QueryModelSupport#getMergedJoinGraph` line 316 inner DCL outcome；
- 早期 diagnostic-ready snapshot 的 contract/XML=`21/21`、`68/68` 与旧 tool hashes 只保留为
  historical bootstrap evidence，不是当前 machine identity。r16 candidate SHA-256=
  `2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919` 已两路独立复算；
  review SHA-256=`88b99e76e5584d3cd17bcdcffd138f1fe6655ce0b7795d3430d2f15a018c8fb3`，
  B/H/M/L=`0/0/0/1`。该 r16 candidate 已被 formal-r3 failure supersede；formal-r3 recovery
  时点的 contract/threshold/manifest hashes 也只作为 historical pre-Cdiag snapshot。当前 machine
  状态=`diagnostic-ready/diagnostic-pending`，latest exact identity 由当前 manifests 机器校验，
  不再引用旧 `cc356…` 作为 current；
- r17 handoff recovery quality=`PASS / 0/0/0/0` 只作为历史前因；r18 已完整复现 full Unit 与
  required lanes，但 high-water guard 拒绝 candidate。当前 Pivot deterministic remediation 的
  pre-Cdiag formal implementation quality 已 `PASS / 0/0/0/0`，record=
  `docs/9.3.4/quality/step4-diagnostic-r18-pivot-null-axis-implementation-quality.md`；当前只授权
  replacement Cdiag commit/push/clean 与 fresh r19，不授权 Cfreeze、post-formal final quality、
  coverage audit 或 acceptance；
- run-owned Unit fixture 的 restricted credential、schema/tamper、atomic publisher、profile
  isolation 与 lifecycle requirements 保持生效；r16 已在单一 fresh run 中完成全部 required
  replacement evidence，不复用或拼接 r15 partial artifacts；
- QueryModel remediation Cdiag `316a71f753827f8f34063b0eb0669271f696c5ee` 已 commit/push/clean，
  但 historical r17 在 Unit MySQL57 第三个 lifecycle probe、callback ready 前因 final-server handoff race
  fail closed；canonical lifecycle、正常 fixture、Unit XML/exec 与后续 evidence absent。r17 永久
  excluded，cleanup JSON 不得当作 lifecycle/Unit PASS，也不能证明 QueryModel all-lane remediation；
- runtime RED 已证明 stock ping 在 PID1=`docker-entrypoi` 时可 premature healthy；authority
  MySQL57/8 已改为 final PID1=`mysqld` + ping，且两库 runtime GREEN。旧修复字节 lifecycle=
  `15/15`；penultimate diagnostics 字节 `5/5` receipt=`159fbe80…`，随后 latest callback
  diagnostics 又有加固，final current bytes r2=`5/5` / receipt=
  `e3bad41ad9ec634c1702ffe20f4c0ddbff3050a227956e2ba9054a11f6b606c7`，successful logs absent、
  demo exact restore=`runner_rc=0 / restore_rc=0` 且 healthy/listening。该时点 full Unit 必须由
  后续 clean/pushed replacement Cdiag 的 fresh diagnostic 证明；此项已由 r18 的
  `681+55/4941/F0E0S0` 完成，但 r18 未满足 aggregate high-water；
- clean/pushed HEAD `bc100b0f63bd3ff62d1105611dae41741790aedd` 的 diagnostic r1
  `step4-coverage-20260716-diagnostic-r1` 在 `child-unit` 以
  `3115 tests / 1 failure / 0 errors / 0 skipped` fail closed。根因是
  `PreAggregationDataValidationTest` 腐化 daily 表但实际命中 monthly，并允许
  raw-vs-raw/nullable-empty 伪绿；修复 focused=`9/F0E0S0`、组合=`57/F0E0S0`，
  source SHA-256=
  `affb6e415c1770eae7c9ab6f3505ac67acfe03eac1461bd2a48addf3ee790623`，见
  `docs/9.3.4/workitems/BUG-step4-preagg-validation-wrong-table.md`；
- diagnostic r2 从 clean/pushed HEAD `0101a44a07784bf6b484d490c7fb508727fbab70`
  启动：Unit=`681 execution + 55 structural / 4,941 testcase / F0E0S0`；Integration=
  `312/F1E0S0`，唯一失败为 `PreAggregationL2CacheIT`，outer 在
  `child-integration` fail closed，summary absent，后续 database/external/Addon/
  aggregate/threshold 未执行；failed evidence 见
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r2-fail-closed-20260716.md`；
- L2 fixture 修复后 focused=`1/F0E0S0`、与 `PreAggregationIT` 组合=`30/F0E0S0`，
  source SHA-256=`bb5d6884401579447382587861e197feac2884ab701065d7826fe7a676e0c313`；
  主动发现的 Pivot legacy fixture 两次稳定 RED，修复后 legacy/V934 SQLite 各
  `1/F0E0S0`，source SHA-256=
  `5c6dcd3b4afba4d93a93c1af47cc4484a1dfc9976da92669bfbcc4529ede6155`；两项均只改测试
  fixture/assertion，不改生产 hybrid 默认、Matcher、threshold 或 exclusion；
- diagnostic r3 从 clean/pushed `e16693297239f2a861f3b93b3de60c1bb783bda0` 启动，
  Unit=`4,941/F0E0S0`；child PASS 后因 Unit/Integration 共用的未托管日志 `tee` 没有
  close/wait，outer 以 live process-group residue fail closed。r3 summary/observation absent，
  failed evidence 见
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md`；
- diagnostic r4 的 reported launch head=
  `ceea084ca25a9d679ba128e3f6bd50a63322c112`；outer 在 `source-before` 以 exit code `2`
  fail closed，run-owned Git/source seal、所有测试 lane、aggregate、threshold 与 summary 均
  absent，decision=`excluded-from-step4-exit`。根因是 `core.fileMode=false` 的 clean checkout
  被错误要求 worktree executable bit 与 Git mode 完全一致；
- source-policy remediation 已实现并静态通过 contract=`20/20`、source identity=`22/22`、
  XML=`63/63`、overlay=`12/12` 与新 identity manifests；tracked FIFO preflight fail-fast
  及 before/after raw stat identity 对 Git-clean-equivalent concurrent rewrite 的拒绝均有直接
  回归；这些不是 all-lane evidence。
- source seal 清除 ambient/global Git clean 配置并显式复算 raw 与 CRLF-input 两个
  candidate，使真实 CRLF worktree 在 HEAD/index clean-equivalent 时通过；HEAD-fixed
  attributes 若声明 external clean filter，则在任何 worktree-aware Git hash/driver hook
  执行前 fail closed，negative 证明 hook 未执行；
- diagnostic r5 在 clean/pushed commit `a35b99cb08f42817d8e75c440f18910b6961841b`
  上建立 run-owned source seal；Unit=`681+55/4,941/F0E0S0`、Integration=
  `47+4/320/F0E0S0`、Addon=`2/6/F0E0S0` 后，database-state companion 因选择
  frozen Step 3 authority manifest 而以 `E_AUTHORITY_MANIFEST` fail closed。database
  cells、external、aggregate、threshold 与 summary 均未完成，r5 与其 partial
  lanes 明确 excluded/non-reusable；failed evidence=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r5-fail-closed-20260716.md`，BUG=
  `docs/9.3.4/workitems/BUG-step4-database-state-successor-authority-manifest.md`；
- database-state/required-report successor adapters 已静态通过；r6 在 clean/pushed commit
  `eb10d9c10a73f379db9ce4fa3d05ff340b489fd4` 建立 `3,974 files` / SHA-256=
  `3a4322e8442646c58ed522c0d4fb52071b3219cc1c2f204c209299bd8acc1cff` source seal，完成
  Unit=`681+55/4,941/F0E0S0`、Integration=`47+4/320/F0E0S0`、Addon=
  `2/6/F0E0S0` 后，因 repo demo container `foggy-demo-mysql` 占用 frozen port `13306`
  而以 `E_DYNAMIC_PRECONDITION` fail closed。该 failure 是 environment precondition，
  不是产品回归；database cells、external、aggregate、threshold、source-after 与 summary
  absent，r6 与 partial lanes 均 excluded/non-reusable。evidence=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r6-environment-fail-closed-20260716.md`；
  environment blocker=
  `docs/9.3.4/workitems/BLOCKER-step4-r6-mysql57-port-occupation.md`；
- r7 在 repo demo DB 容器已停止、四个 frozen ports 均无 listener 的 clean/pushed HEAD
  `528a0a541d90ef77d577e1816b392d33168cb558` 上启动；source-before=`3,976 files` /
  SHA-256=`b3fc04ee0d16a7a81f5e9697b10b5edeaafec0f59cd5dbec1e65625381c3fe43`。
  Unit 因 6 suites / 11 errors 在 `child-unit` fail closed，证明既有绿色隐式依赖 ambient
  `127.0.0.1:13306` MySQL/schema；r7 与不完整 Unit exec 均 excluded/non-reusable；
- Unit fixture quality r2=`step4-unit-fixture-quality-20260716-r2` 在 commit
  `a603f839a98d99b2d7beb8379f76b4d85539328c`、source-before=`3,981 files` /
  `087d074f3497aff3fe305806f82b4d62ff41cdd4d3b26556e58f034138b14c2c`
  上先通过 lifecycle `5/5`，随后完整 Maven invocation 因全局
  `spring.datasource.*` 覆盖 `foggy-dataset-model` 的 SQLite profile 而以
  `3,115 tests / 631 errors` fail closed；直接根因为 `org.sqlite.JDBC` 与
  `jdbc:mysql://127.0.0.1:13306/...` mismatch。r2 excluded/non-reusable；
- 修复必须由 Unit authority 同步创建 pinned、run-owned MySQL 5.7、固定最小 schema，保持
  唯一 Maven invocation、`681+55/4,941` 与全局 `23 exec / 48 sessions`，并在 Unit、outer、
  Step 3 边界证明 cleanup=`0/0/0`、`13306=free`。当前修复通过
  `foggy-dataset/src/test/resources/application.yml` 的
  `V934_UNIT_MYSQL57_URL/USERNAME/PASSWORD` 三个 placeholder 只适配该模块默认/隐藏
  MySQL consumer；其他 profile 保留自身 datasource。outer/callback 必须拒绝
  underscore/dotted/hyphen Spring/custom key 与 `@argfile`、`VMOptionsFile`、
  `javaagent/agentlib/agentpath` 间接注入；adapter consumer inventory 必须使用 scrubbed
  Git environment、`HEAD` tree 与 no-replace object。当前 contract static=`20/20`、
  Unit fixture negatives=`36/36`、真实 lifecycle=`5/5`、report inventory=`30/30`。
  这些静态结果是 fresh Unit replacement 的前置证据，不是 Step 4 exit evidence；
- fresh Unit fixture quality r3=`step4-unit-fixture-quality-20260716-r3` 已在 commit
  `50161a0a869430e353f3933d9bb00dda59d9c4b1`、source before=after=`3,982 files` /
  `1db06cc18bb86c288ffa79e7094e3b9e509d866ddc23b6c93bcaaa0422b99eb2`
  上通过。唯一 Surefire invocation=`681 positive + 55 structural = 736 raw reports /
  4,941 testcase / F0E0S0`；fixture negatives=`36/36`、receipt schema/tamper=`4/4`，
  closed receipt 的 `18/18` connections 全部为 `v934_unit`；真实 lifecycle=`5/5`，
  run-owned container/volume/network cleanup=`0/0/0` 且 port free。evidence window 外按
  exact ID `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166`
  恢复 demo MySQL 为 `running/healthy`。record=
  `docs/9.3.4/evidence/step-4/step4-unit-fixture-quality-r3-pass-20260717.md`；
- r3 通过 Unit remediation subgate，formal remediation quality B/H/M/L=`0/0/0/0`；r8
  在建立 run-owned identity 与 lane 前因 stale Unit direct-trap lifecycle shape 于
  `bootstrap-negative` fail closed，所有 lane/aggregate/threshold/summary absent，r8=
  excluded/non-reusable；
- r8 时点 lifecycle contract remediation 已通过 dynamic `9 类 / 14 case`、Unit shape/seal=
  `13+3`、Integration=`11+5`，并由 raw-byte runner seal关闭 false/subshell、source/eval 与
  CRLF drift；两路独立 quality B/H/M/L=`0/0/0/0`。该时点 closure 必须 commit/push 并证明
  clean `HEAD == origin/main` 后运行 fresh r9；这些结果仍不表示 Step 4 exit 已通过；
- 9.3.4 只有在 fresh Step 4 Unit replacement、fresh formal、实现质量闸门、测试证据覆盖
  审计与版本验收全部通过后，才允许带上述分类债务签收；任一失败即撤销临时例外并保持
  Step 5 关闭。该债务不得跨过 9.3.5 版本验收；
- Step 4 threshold 仍为
  `diagnostic-pending`；r10 observation 是 failed run 的不可复用 partial evidence，r11 又在
  bootstrap-negative 且零 lane fail closed，尚无
  reviewed/frozen baseline 或 Step 4 exit evidence。
  `can_enter_coverage_audit=no`，Step 5/formal/coverage audit/acceptance
  portable authority、Step 6 CI/release 与 Step 7 version acceptance 仍未完成，不能
  据此签收 9.3.4 或把 9.3.5 标为 ready。

## Superseding r9 requirement boundary（2026-07-17）

- r9=`step4-coverage-20260717-diagnostic-r9` 在 clean/pushed commit
  `a0466ec04c51c436413e85836a7dee6153e18010` 上完成 required lanes、inventory=
  `773+59/5,707/F0E0S0` 与 `23 exec / 48 sessions`，随后在 `coverage-report` 因 verifier
  对 all-loaded classes 做同名单 ID 校验而 fail closed；`exec-manifest`、aggregate、
  source-after、threshold、summary absent，r9 excluded/non-reusable；
- requirement clarification：production class-ID consistency 的权威域只能是 fresh sealed
  24-module production class universe；runtime/test/dependency execution data 不得按名称丢弃，
  必须以 JaCoCo class ID 完整进入 aggregate exact union；任何名称命中 production universe
  的 forged/旧 ID 仍必须 fail closed；
- machine contract 新增
  `jacoco.class_id_consistency_scope=frozen-24-module-production-class-universe`；focused
  exec scope/aggregate=`17/17`、contract mutation=`21/21`、XML identity/provenance=
  `68/68`、overlay=`12/12`；
- quality review 另确认 lifecycle validator 的 raw comment/dead-context 与 dynamic `trap`
  拼接可伪绿；其 coded executable-stream regression 已通过，Unit/Integration shape=
  `16/16 + 14/14`、semantic stream=`2/2 + 5/5`、raw seal=`2/2`、outer/library=
  `3/3 + 3/3`，三路独立正式质量最终 B/H/M/L=`0/0/0/0`；
- 当时 Step 4=`in-progress`，threshold=`diagnostic-pending`，
  `can_enter_coverage_audit=no`。只有 remediation commit/push/clean HEAD 与 fresh r10
  diagnostic 全部通过，才允许进入 exact threshold
  review/freeze；Step 5、formal、coverage audit、acceptance 与 9.3.5 保持关闭。

## Superseding r10 requirement boundary（2026-07-17）

- r10=`step4-coverage-20260717-diagnostic-r10` 在 clean/pushed commit
  `47e0c027cd205a49d40db400ba26b99e6f97d60e` 上完成全部 required lanes、inventory=
  `773+59/5,707/F0E0S0`、`23 exec / 48 sessions / 16,947 class IDs`、aggregate exact
  union、coverage observation、source-after 与 cleanup，随后在 `sensitive-scan` fail closed；
- aggregate partial observation：line=`54,478/76,830`、branch=`25,980/44,870`，但
  `sensitive-scan.env` 与 `summary.env` absent，r10 excluded/non-reusable，禁止冻结阈值或
  拼接后续 evidence；
- root cause：demo identity producer 使用 credential-shaped authorization label 描述非凭据
  identity result；五条扫描规则行为正确且保持不变；
- remediation：producer 改用明确的 demo identity result 措辞；outer bootstrap-negative
  新增内存 `7 dangerous + 3 safe` probe，和最终扫描共用同一 pattern 数组，`rg rc>1`
  双向 fail closed，不落 `RUN_ROOT`、不回显 fixture；launcher request smoke test 通过；
- 该时点 Step 4=`in-progress`，formal remediation quality 最终 B/H/M/L=`0/0/0/0`；
  commit/push/clean HEAD 与 fresh r11 仍 required。threshold=`diagnostic-pending`、
  `can_enter_coverage_audit=no`；Step 5、formal、
  coverage audit、acceptance 与 9.3.5 保持关闭。

## Superseding r11 requirement boundary（2026-07-17）

- r11=`step4-coverage-20260717-diagnostic-r11` 从 clean/pushed commit
  `141592ca9f4219d87a018774ee607b09a8e5a8a1` 启动，在 run-owned Git/source seal 与任何
  test lane 建立前，于 `bootstrap-negative` 以 stable `E_SOURCE_SEAL` fail closed；历史
  outer actual raw=`57f5da9a23c4973beef54a6bfd303c3dfd38fccb03a7bc2cadadbfaa3206f649`，
  lifecycle frozen raw=
  `02a920d91d1b8792cad47d65ce860352a8e9ecf39106f4489a714df01888dbaa`；
- r11=`failed / excluded / non-reusable`，Unit、Integration、database、external、Addon 与
  其他测试 lane=`0`；exec、aggregate、observation、sensitive receipt 与 summary 均 absent。
  bootstrap 前置绿色不得与后续 run 拼接，也不得冻结 threshold；
- binding requirement 现明确为 Unit runner、Integration runner、outer runner、lifecycle
  library 四个独立 frozen constant 各自与 canonical raw bytes 及 top manifest entry 形成
  early exact binding；canonical positive=`1`，negative=`6`：outer+manifest refresh/nested
  stale、outer-only drift、valid-64 nested-only wrong、missing、duplicate、invalid-format
  constant，失败稳定码=`E_SOURCE_SEAL_BINDING`。不得从 manifest 动态派生 nested
  constant 形成 self-certifying seal；
- outer 同时要求 raw CRLF mutation 触发 raw source-seal negative，executable no-op mutation
  触发 semantic executable-stream negative；当前 raw/semantic=
  `90b4b979e55c17243644cce186767a4647ce79c85b431adcb415bddd18cc1cec` /
  `065211912aab5227125ef02f40e2965fce7ff5060df5c7b91a902c4ad4f34cae`，lifecycle tool=
  `61bf7b990bdef6e0d75c53010644bcc6d1525a67119cd36c5f82eeb911e005fc`，top manifest=
  `a9b105ce2f8f640dfa09863e797697bcf9892a7b0fa68b38f83b5bbd7435afb4`；
- safety review 发现 preflight path-check/read 之间存在 TOCTOU Medium；现已对全部输入使用
  descriptor-bound strict read（`O_NOFOLLOW`、open 后 `fstat`、fd read、post-`lstat`
  stable identity）关闭，错误仍为 `E_SOURCE_SEAL_BINDING`；两路 post-fix implementation
  review 与独立 docs/status review 最终 B/H/M/L=`0/0/0/0`；
- focused lifecycle suite=`PASS`，manifest=`60/60`、successor=`14/14`、contract
  mutations=`21/21`、overlay negatives=`12/12`。这些只证明 remediation，不是 r12、formal
  或 Step 4 exit；
- formal-r1 已在 Cfreeze `86d505e` 上完成全量 lane 后以 `E_FORMAL_LOW` fail closed；唯一
  counter 漂移来自 `WatchServiceFileTracer` shutdown hook 与 JaCoCo dump hook 的退出竞态。
  5/5 fresh-fork isolated shutdown regression bitmap 完全一致，既有 11 testcase 不变。
  该边界已由 r14/Cfreeze/formal-r2 历史链路推进并由下方 formal-r2 boundary supersede；
  Step 5 与 9.3.5 始终保持关闭。

## Superseding r12 requirement boundary（2026-07-17）

- r12=`step4-coverage-20260717-diagnostic-r12` 在 clean/pushed commit
  `05351ecab0d7fc43d12dfa307ffecf81feb41539` 上完整发布 all-lane diagnostic、aggregate、
  sensitive 与 cleanup；该 run 合法揭示 critical below-floor=`9` 和 structural N/A=`1`，
  因而不是 Step 4 exit 或可直接冻结的 reviewed baseline；
- threshold requirement 明确为 exact producer/consumer lifecycle：consumer 必须消费真实六字段
  critical row 与八字段 metric，不得接受未知/缺失字段；默认 positive denominator，唯一 N/A
  由 exact FQCN/module/metric machine tuple授权；
- JSON numeric identity 必须类型严格：boolean 不得冒充 integer/number，float zero 不得冒充
  integer zero；observation recompute、candidate、frozen equivalence、formal receipt 都使用同一
  strict identity；frozen receipt 必须证明 retained raw exec replay；
- coverage remediation 只能补测试，不得改变 production、critical set、0.80/0.70 floor、
  exclusion 或 report/testcase cardinality；九类 focused=`136/F0E0S0` 只作 fresh r13 前置；
- 该时点 Step 4=`in-progress`，threshold=`diagnostic-pending`，precommit quality 已通过；
  commit/push 与 fresh r13 仍 required。只有 r13 below-floor=`0`、N/A=`1`、candidate verified 后，才允许
  direct-child Cfreeze；Step 5、formal、coverage audit、acceptance 与 9.3.5 保持关闭。

## Superseding r13 sealed requirement boundary（2026-07-17）

- r13=`step4-coverage-20260717-diagnostic-r13` 已在 clean/pushed Cdiag commit
  `b76552e21479c75111f648a4aa678abe018cc3f9` 以
  `diagnostic-observed / completed / exit 0` 封存；required=`773+59/5,707/F0E0S0`、
  Addon=`2/6`、exec=`23/48`，sensitive scan=`passed`、cleanup=`0/0/0`；
- exact critical requirement 已满足：below-floor class=`0`；唯一 structural N/A=`1`，只允许
  machine-authorized tuple：FQCN=
  `com.foggyframework.dataset.db.model.spi.NamespaceScope`、module=`foggy-dataset-model`、
  metric=`branch`；
- threshold freeze candidate 已通过 public verification，SHA-256=
  `8bb47382444fd66893d250a8787416c9ce73f9590be4c66308fb7a2e3e014d00`；该 candidate 只允许
  进入 review 与 direct-single-parent Cfreeze，不得等同 canonical confirmed threshold 或 formal pass；
- review evidence SHA-256=`2ab3dc50ed15399c07c1281c70961bf56593eae925727e5cc357bb448e737d8e`；
  canonical threshold SHA-256=`0cfc6765eda1aa8a5209e46bf668136ee1786c4761d66a07262ac3557e7227cb`
  已 confirmed，contract/publication=`formal-ready`；
- r13/Cfreeze 时点 Step 4 仍为 `in-progress`，transition=`confirmed/formal-ready`、formal=`pending`、
  `can_enter_coverage_audit=no`、`can_enter_acceptance=no`。只有 fresh formal、最终
  implementation quality 依次通过后才可启动 coverage audit；Step 5 与 9.3.5 保持关闭。

## Superseding formal-r1 fail-closed requirement boundary（2026-07-17）

- formal-r1=`step4-coverage-20260717-formal-r1`，tested Cfreeze=
  `86d505810524383da6211bcc2a7965e9a4afb34e`；unit/integration/DB/external/Addon、
  report inventory、exec/session、class universe 与 provenance 全部通过，formal gate 返回
  `E_FORMAL_LOW`，未发布 summary、coverage gate、candidate 或 final manifest；
- r13 与 formal-r1 全量 XML 逐类 diff 只有 `WatchServiceFileTracer`：aggregate 与 critical
  同步少 `9 line / 3 branch`。该类 line=`195/244`，低于 80% floor；原因是 tracer 与
  JaCoCo 两个 JVM shutdown hook 的并发无序，不是漏跑、skip 或 class-tree drift；
- requirement 新增稳定性约束：reviewed threshold 不得包含仅由 JVM exit hook 顺序决定的
  incidental coverage。必须在 JVM exit 前用隔离实例显式完成 lifecycle，且不得关闭 singleton；
- regression 已放入既有 testcase，5/5 fresh fork 对目标类均为 `177/245 probes`，formal-r1
  缺失的 7 probes 全部命中，bitmap unique=`1`，报告保持 `11/F0E0S0`；
- formal-r1 保持 immutable failed evidence，不复用、不修补、不盲重跑。该时点 canonical state=
  `diagnostic-ready/diagnostic-pending`；后续 Cdiag -> fresh diagnostic -> review -> direct-child
  Cfreeze -> fresh formal 顺序必须完整重走。`can_enter_coverage_audit=no`、
  `can_enter_acceptance=no`，Step 5 与 9.3.5 保持关闭。

## Superseding r14 / second Cfreeze requirement boundary（2026-07-17）

- new Cdiag=`322bb346cca19998a90d6d990505ef033f3a496a` 已 commit/push/clean；fresh r14
  `step4-coverage-20260717-diagnostic-r14` 完成 exact all-lane authority：
  `773+59/5707/F0E0S0`、Addon `2/6`、23 exec/48 sessions、24 modules/2098 classes；
- r14 critical below-floor=`0`，unique structural N/A=`NamespaceScope.branch`；
  `WatchServiceFileTracer=204/244 line,99/128 branch`，显式 lifecycle 不再依赖 JVM hook order；
- r14 candidate 必须保持 immutable `review-required` bytes，SHA-256=
  `9087774387f0bb4b177a1b5f2fe28a4102e0434afe7a5b316aa106511c9e6d55`；canonical confirmed
  threshold 是 review 后的独立 successor，不得回写 candidate；
- review 记录 non-critical PostgreSQL Pivot probe variance 为 Low。Requirement 不要求 formal
  复现 r13 的偶发高水位；它要求 formal 不低于 fresh r14 exact threshold，并对所有 critical
  minimum 逐项 fail closed；
- 该时点 working-tree machine state 已为 `formal-ready/confirmed`。Cfreeze commit 必须是
  `322bb346…` 的 direct-single-parent child，required paths 恰为 threshold/contract/manifest，
  其他变化仅允许 `docs/9.3.4/**`；
- direct-parent/formal-delta、push、clean identity 通过后才允许 fresh formal。该 Cfreeze 已以
  `1901a10138bac06a09b875c907b7aea6e2789b04` 完成并运行 formal-r2；本节其余 future boundary
  由下节 supersede。Formal PASS 前
  `can_enter_coverage_audit=no`；final implementation quality PASS 前仍不得启动 coverage audit；
  audit PASS 前不得 acceptance。Step 5 与 9.3.5 保持关闭。

## Superseding formal-r2 fail-closed requirement boundary（2026-07-17）

- Cfreeze=`1901a10138bac06a09b875c907b7aea6e2789b04` 已 commit/push/clean，唯一 direct parent=
  `322bb346cca19998a90d6d990505ef033f3a496a`；
- formal-r2=`step4-coverage-20260717-formal-r2` 完成 unit/integration/DB/external/Addon、
  report inventory、exec/session、class universe 与 provenance；required=
  `773+59/5707/F0E0S0`、Addon=`2/6`、exec=`23/48`、cleanup=`0/0/0`、sensitive PASS；
- formal gate 正确返回 `E_FORMAL_LOW`：aggregate line exact `54622/76830`，branch=
  `26105/44870`，比 reviewed exact threshold 少 `1`；success summary/gate absent；
- 12 critical class 全部 exact，below-floor=`0`，唯一 N/A=`NamespaceScope.branch`；2066 个
  reportable class 只有 `FileSystemListPresetStore` branch `18/26 -> 17/26`。差异只在 Unit
  class ID=`d1bd017e92baa090` 的 probe 106；测试报告/exec/session 未丢失；
- requirement 新增确定性约束：exact aggregate 不得依赖 `Files.find(...).findFirst()` 的未定义
  目录遍历顺序。已有多 regular-file 的 testcase 必须以不存在 ID 查询强制执行
  filename-false outcome；不得通过重跑或下调 threshold 掩盖；
- regression 不改 production、floor、critical set、exclusion 或 testcase cardinality；5/5
  fresh fork 均命中 probe 106、bitmap unique=`1`，Data Viewer=`104/F0E0S0`；
- historical recovery machine state=`diagnostic-ready/diagnostic-pending`：threshold SHA-256=
  `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96`，contract SHA-256=
  `15dae282395d920ffb3d2aae4c518f0d1c8be09aaed8b08e40044f9d9bc6b0b0`，manifest SHA-256=
  `cc356897f6588beedf00c057c5988a176b6cef241d4d9c274103b691d254dc60`；
- 该 historical sequence 已完成 Cdiag `9270d2d4…` 并进入 r15；r15 fail-closed 后由下节
  supersede。`can_enter_coverage_audit=no`、`can_enter_acceptance=no`，Step 5 与 9.3.5 保持关闭。

## Superseding diagnostic-r15 fail-closed requirement boundary（2026-07-17）

- formal-r2 deterministic recovery Cdiag=
  `9270d2d4e58684226aeb15eff55b027e6aa4a7eb` 已 commit/push/clean；fresh r15 在 Unit
  `Bean2MapUtilsTest#testCachingMechanism` 单次纳秒倍率断言 fail closed；
- r15 partial authority exact 为 26 XML reports=`124/F1E0S0`、`jacoco-ut.exec`=
  `2/48 sessions`；source-after、final sensitive scan、inventory、aggregate、observation、candidate、
  summary/gate absent。该轮必须 immutable 且不得与后续 run 拼接；
- correctness requirement：Surefire/Failsafe required lane 不得用 single-sample wall-clock ratio
  或固定毫秒上限充当缓存/批量复制正确性 oracle；性能 SLA 必须与 correctness authority 分离；
- deterministic regression 必须保持已有 testcase cardinality，通过三个同类、不同 source 实例并
  精确校验各 target 证明 cache metadata 不保留实例数据；1000-copy 可以保留批量 correctness，
  不得保留环境相关墙钟门；
- remediation 不得修改 production/public API/POM/runner/floor/critical/exclusion；已完成 focused
  `10/10` fresh JVM、class=`23/F0E0S0`、module=`27/F0E0S0`；formal pre-Cdiag quality 已
  PASS，B/H/M/L=`0/0/0/0`；
- only allowed sequence：one new Cdiag commit/push/clean -> fresh diagnostic
  -> candidate/review -> direct-child Cfreeze -> fresh formal -> final implementation quality ->
  coverage audit -> acceptance。当前 Step 4=`in-progress`，Step 5 与 9.3.5 关闭。

## Superseding diagnostic-r16 reviewed-Cfreeze requirement boundary（2026-07-17）

- superseding Cdiag=`f863c672029d5d1e5a4903df74cf6cba22a04a85` 已 commit/push；fresh r16=
  `step4-coverage-20260717-diagnostic-r16` 完成 exact all-lane authority：
  `773+59/5707/F0E0S0`、exec/session/identity=`23/48/16948`、aggregate line=
  `54624/76830`、branch=`26111/44870`；
- 12 个 critical class 全部达标，唯一 structural N/A=`NamespaceScope.branch=0/0`；candidate
  SHA-256=`2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919`
  经两路独立复算，review SHA-256=
  `88b99e76e5584d3cd17bcdcffd138f1fe6655ce0b7795d3430d2f15a018c8fb3`，B/H/M/L=
  `0/0/0/1`；
- canonical threshold 已为 `confirmed` / SHA-256=
  `ca6a25c66fbbe9a595adde74f1b7589bd3829b93edebfd5b11dc394ab8d088c8`，contract 已为
  `formal-ready` / SHA-256=
  `6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`；
- pre-Cfreeze implementation quality=`PASS / 0/0/0/1`，只授权一次 direct-child Cfreeze 与
  fresh formal；
- Cfreeze 必须是 Cdiag 的 direct-single-parent child，并通过 push、formal-delta 与 clean identity；
  当前 Cfreeze 尚未提交，fresh formal 尚未运行；
- 唯一 Low 是 formal 必须完整复现 r16 aggregate 高水位；不得下调 threshold、重写 candidate
  或用重跑挑选低基线。formal PASS 与 post-formal final quality PASS 前不得启动 coverage audit；
- 当前 Step 4、coverage audit、acceptance 均未签收，Step 5 与 9.3.5 保持关闭。

## Superseding formal-r3 fail-closed requirement boundary（2026-07-17）

- Cfreeze=`a63c82c53ebaad1a1c22d78647fbda70b4bd6594` 已 commit/push/clean，唯一
  direct parent=`f863c672029d5d1e5a4903df74cf6cba22a04a85`；
- formal-r3=`step4-coverage-20260717-formal-r3` 完成 required=
  `773+59/5707/F0E0S0`、Addon=`2/6`、exec/session=`23/48`、cleanup=`0/0/0`、
  sensitive PASS，然后以 `E_FORMAL_LOW` fail closed；line exact `54624/76830`，
  branch=`26110/44870` 比 exact threshold `26111/44870` 少一个 outcome；
- requirement 定位为唯一 class/method/line 差异：
  `QueryModelSupport#getMergedJoinGraph` line 316 inner double-check；r16 内层 outcome 为
  `0 missed/2 covered`，formal-r3 为 `1 missed/1 covered`。这是 test coverage determinism
  缺口，不是 product regression、test/report/exec 丢失或 class-universe drift；
- 新增确定性要求：既有
  `RuntimeNamedDataSourceResolverBindingTest#publicationGuardSerializesTheCallbackWithAConcurrentRebind`
  必须受控暂停 QueryModel first build，确认 second caller 在 exact support monitor 上
  `BLOCKED` 后释放，并精确断言只 build 一次且两结果 identity 相同；
  不新增/改名 `@Test`，不修改 production 或降阈；
- targeted、protected overlay 与 5/5 fresh Maven/JVM 已 PASS；QueryModelSupport class id=
  `d242dafe9de31249`、probes=`34/629`、packed bitmap=
  `4P-_7xsAAIADAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEA`、
  unique=`1`，Surefire=`1/F0E0S0`。`foggy-runtime-api` full module=
  `128/F0E0S0`，`RuntimeNamedDataSourceResolverBindingTest=5/F0E0S0`。formal-r3 recovery
  pre-Cdiag quality=`PASS / 0/0/0/0`，machine/contract/overlay/negative suites 通过；
  record=`docs/9.3.4/quality/step4-formal-r3-recovery-implementation-quality.md`。all-lane
  diagnostic 尚未完成；当前回归结果不是 Step 4 exit evidence；
- test bytecode 变化后 machine 已恢复 `diagnostic-ready/diagnostic-pending`；contract/
  threshold/manifest SHA-256=`15dae282…` / `0df17a87…` / `cc356897…`，manifest=
  `60/60`；
- only allowed sequence：one new Cdiag commit/push/clean ->
  fresh diagnostic -> candidate/review -> direct-child Cfreeze -> fresh formal -> final implementation
  quality -> coverage audit -> acceptance。当前 Step 4=`in-progress`，
  `can_enter_coverage_audit=no`、`can_enter_acceptance=no`，Step 5 与 9.3.5 closed。

## Superseding diagnostic-r17 final-mysqld handoff requirement boundary（2026-07-17）

- Cdiag `316a71f753827f8f34063b0eb0669271f696c5ee` 已 commit/push/clean，并由 fresh
  `step4-coverage-20260717-diagnostic-r17` 消耗；r17 在 outer `child-unit / exit 1`、Unit
  `unit-mysql57-lifecycle-negative / exit 1` immutable fail closed；
- third HUP child 在 `fixture-first-apply`、callback ready 前退出；canonical lifecycle receipt、
  normal fixture、Unit XML/exec、source-after、sensitive/model/inventory、aggregate、observation、
  summary/candidate/final 全 absent。r17=`excluded/non-reusable`；cleanup receipts 只能证明资源
  cleanup，不得计为 lifecycle `5/5`、full Unit 或 QueryModel all-lane PASS；
- readiness requirement：run-owned authority MySQL57/8 只有在 `/proc/1/comm=mysqld` 且原 ping
  成功后才可 healthy；provisioner 之后仍须验证 business identity 与 fixture watermark，不得只用
  stock ping 或只用 watermark。冻结 Step 3 provisioner 不改，差异须由 successor declared
  amendment/authority manifest/contract/overlay/top manifest 共同绑定；
- runtime oracle：MySQL57 RED 必须捕获旧配置 premature healthy；MySQL57/8 patched GREEN 必须
  证明 first healthy 即 final mysqld、premature=`0`。真实 lifecycle 必须覆盖
  INT/TERM/HUP/callback-failure/leader-kill，unexpected failure 保留 no-clobber diagnostics，成功
  diagnostics 删除，所有路径 residue=`0/0/0`；
- focused 现状：旧修复字节三个唯一 run=`15/15`；penultimate diagnostics 字节=`5/5` / receipt
  `159fbe80595933e29f05f13c0f1d82e9b65d7f947dddec253477f0b3f3876799`，但 latest callback
  OSError/成功日志存在性加固后的 final current bytes r2 已 `5/5` PASS，receipt=
  `e3bad41ad9ec634c1702ffe20f4c0ddbff3050a227956e2ba9054a11f6b606c7`，successful logs absent，
  demo exact restore=`runner_rc=0 / restore_rc=0` 且 healthy/listening；完整 Unit 不得由 focused
  证据推导；
- static regressions 已通过 overlay=`12/12`、Unit fixture=`36/36`、coverage=
  `27 + source/Git 22 + replay 12`；current machine=`diagnostic-ready/diagnostic-pending`；
- pre-Cdiag formal implementation quality=`PASS / 0/0/0/0`，record=
  `docs/9.3.4/quality/step4-diagnostic-r17-recovery-implementation-quality.md`；
- only allowed sequence：replacement Cdiag commit/push/clean -> fresh diagnostic ->
  candidate/review -> direct-child Cfreeze -> fresh
  formal -> final quality -> coverage audit -> acceptance。Step 4=`in-progress`，Step 5/9.3.5 closed。

## Superseding diagnostic-r18 governed-high-water requirement boundary（2026-07-17）

- replacement Cdiag=`5be1edaa16c5883cde2f66396ac26a1ae113430b` 已 commit/push/clean；fresh
  `step4-coverage-20260717-diagnostic-r18` 完成 all-lane authority 并经 public validator 复算
  `VALID`：required=`773+59/5707/F0E0S0`、Addon=`2/6`、exec/session/identity=
  `23/48/16940`、cleanup=`0/0/0`、sensitive/model gate PASS；
- r18 aggregate exact 为 line=`54622/76830`、branch=`26107/44870`，低于 r16 reviewed
  high-water line=`54624/76830`、branch=`26111/44870`，delta=`-2 line / -4 branch`。因此
  r18 decision 必须保持 `threshold-candidate-not-authorized`，threshold candidate absent；不得
  以 complete diagnostic PASS 代替 high-water 审查，不得降低 threshold；
- exact XML delta 仅为 `BaselineRatioCalculator=-2 line/-3 branch` 与
  `ResultShaper=-1 branch`，对应 PostgreSQL exec bitmap 中的 NULL column baseline exclusion、
  NULL coordinate key 与 NULL row tree fallback。两个 production source、class universe、分母与
  report inventory 未变，本次判定为 incidental data-path coverage，不是 product regression；
- blocker 记录=
  `docs/9.3.4/workitems/BUG-step4-pivot-null-axis-coverage-oracle.md`，governed evidence=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r18-governed-high-water-gap-20260717.md`；
- deterministic regression 必须位于既有 `PivotSqlParityIT` S12 节点，显式证明 NULL column
  不进入 first/last baseline domain，以及 NULL row 被 `ResultShaper` 保留为
  `__null__` tree node。当前 amendment 无新增/改名 `@Test`、无 production 修改；三次 fresh
  JVM/JaCoCo focused 均 `1/F0E0S0` 且目标 bitmap `3/3 identical`，完整 test class=
  `23/F0E0S0`；successor amendment/protected-tree/contract/overlay/hash manifest 已作为同一
  replacement Cdiag identity 同步绑定并通过 validators；
- focused/整类绿色不是 all-lane authority。canonical machine 保持
  `diagnostic-ready/diagnostic-pending`；本次 implementation quality 已 PASS，当前可且仅可
  commit/push/clean 唯一 replacement Cdiag，再运行 fresh r19 diagnostic；
- r19 必须完整 PASS 且 aggregate 不低于 r16 high-water，才可生成 candidate 并进入
  independent review；否则继续 fail closed，不得 freeze。当前 candidate/Cfreeze/formal/final
  quality/coverage audit/acceptance/Step 5 全关闭，`can_enter_coverage_audit=no`、
  `can_enter_acceptance=no`。

## Superseding diagnostic-r19 reviewed-Cfreeze requirement boundary（2026-07-17）

- replacement Cdiag=`613b11a0ae6732f865f918551cd9116079771b5e` 已 commit/push/clean；fresh
  `step4-coverage-20260717-diagnostic-r19` 完成 `diagnostic-observed / completed / exit 0` 并经
  public validator 复算。required=`773+59/5707/F0E0S0`、Addon=`2/6`、Unit=
  `681+55/4941/F0E0S0`、Integration=`47+4/320/F0E0S0`、Step 3 required=`45/446`；
- exact execution identity=`23 exec / 48 sessions / 16,931 classes`，production universe=
  `24 modules / 2,098 classes`，source before=after=`a4ccff29…38f65`，cleanup=`0/0/0`，
  model/sensitive gates PASS；
- aggregate line=`54,624/76,830`、branch=`26,111/44,870`，与 r16 reviewed high-water exact
  相等且相对 r18 恢复 `+2/+4`；critical=`12`、positive metrics=`23`、below-floor=`0`，唯一
  N/A=`NamespaceScope / foggy-dataset-model / branch`；
- immutable candidate SHA-256=`6588e30b…f545b8` 已 public verify，并由两路 independent review
  exact 复算通过；canonical machine exact 投影为 threshold=`confirmed`、contract/publication=
  `formal-ready`；
- 当前只满足建立 Cfreeze 的前置条件。Cfreeze 必须是 `613b11a0…` 的唯一 direct-single-parent
  child，并在 commit/push/topology/clean proof 后运行一次 fresh formal。fresh formal、final
  implementation quality、coverage audit、acceptance 尚未发生；Step 4=`in-progress`，
  `can_enter_coverage_audit=no`、`can_enter_acceptance=no`，Step 5/9.3.5 closed。

## Superseding formal-r4 / quality-reviewed requirement boundary（2026-07-18）

- Cfreeze `f97483a0…` 已满足 direct-single-parent、formalization delta、push/clean；fresh
  formal-r4=`formal-passed / completed / exit 0`，public final artifact复算通过；
- required=`773+59/5707/F0E0S0`、Addon=`2/6`、exec/session/class identity=`23/48/16953`，
  database/external=`29/370 + 16/76`；source exact、三 child lifecycle、negative、model、
  sensitive、cleanup 与外部 exact restore 均符合 requirement；
- aggregate line=`54624/76830`、branch=`26111/44870`，critical=`12/23/below0`，唯一 N/A=
  `NamespaceScope.branch`；confirmed threshold 未降低、exclusion/critical set 未扩大；
- post-formal quality=`ready-for-coverage-audit / 0/0/0/1`，mandatory fixes=`0`。At that
  formal-r4 checkpoint only evidence coverage audit was open：`can_enter_coverage_audit=yes`、
  `can_enter_acceptance=no`；Step 4、Step 5 与 9.3.5 仍关闭。

## Superseding Step 4 accepted requirement boundary（2026-07-18）

- final quality、coverage audit 与 feature acceptance 已按顺序完成；audit=
  `ready-for-acceptance / critical-major gap 0/0`，acceptance=
  `signed-off / accepted / blocking none`；
- Step 4 direct requirements `COVERAGE-AGG/COVERAGE-CRITICAL` 与其消费的 inventory、runner、DB、
  skip、regression、source/provenance/lifecycle/cleanup requirements 均有同一 Cfreeze 的 formal
  evidence；Pivot legacy fallback 由同 source companion `1/F0E0S0` 补齐；
- 25 个 Step 4 workitem 均 closed；Unit MySQL classification DEBT 继续 open，且不得跨越
  9.3.5 version acceptance；
- Step 4=`passed`，`can_enter_step5=yes`；Step 5=`ready / not-started`。这不满足
  `CI-REQUIRED`、`RELEASE-SAME-ARTIFACT` 或 version completion definition；
- 9.3.4 `acceptance_status` 继续 `not-started`，版本状态仍 `in-progress`；9.3.5=`queued`。

## Superseding formal-r6 requirement boundary（2026-07-19）

- `COVERAGE-AGG/COVERAGE-CRITICAL` 的 replacement authority 必须包含 deterministic mandatory
  negative fixtures；malformed fsmonitor v2 output 不可作为 formal precondition；
- formal-r6 正确 fail closed，但因未启动 source/test/coverage，它不能满足任何 replacement exit；
- protocol fix 位于 formal allowlist 外，触发 new diagnostic requirement；不得以历史 feature
  acceptance、r22 candidate 或同-ID rerun豁免；
- r23 满足全 lane diagnostic correctness，但不满足可复现 threshold authority：其唯一新增 branch
  来自 MapBeanInfo inner double-check 偶发调度，candidate/capsule 必须 absent；
- current definition of done：controlled regression 后 replacement Cdiag、fresh diagnostic-r24、new
  review/Cfreeze、fresh formal-r7、final quality/audit/acceptance 全 PASS；在此之前
  `can_enter_step5=no` 且 9.3.5 closed。

## Superseding diagnostic-r24 reviewed-Cfreeze requirement boundary（2026-07-19）

- replacement Cdiag=`414c8b12…` 已 clean/pushed；r24 满足全部 lane、source/provenance、critical、
  model/sensitive、cleanup 与 DB restore requirement；
- exact coverage requirement 输入更新为 line=`54624/76830`、branch=`26112/44870`；target
  MapBeanInfo outcome 已由 controlled existing-node regression 稳定为 branch=`4/4`、probe=`_wU`；
- immutable candidate、deterministic capsule、empty-directory materialize 与两路 independent review
  全部 PASS，B/H/M/L=`0/0/0/0`；
- canonical threshold/contract=`confirmed/formal-ready`，frozen r24 replay 与 fail-closed negatives PASS；
- 当前 requirement exit 仅到 `ready-for-direct-child-Cfreeze`。fresh formal-r7、final quality、coverage
  audit、acceptance 未发生，故 `can_enter_step5=no`、9.3.5 closed。

## Superseding formal-r7 repository portability requirement boundary（2026-07-19）

- formal-r7 正确拒绝仓外 test input；完整 Unit 不足以替代未完成的 Integration/Step3/aggregate；
- repo-contained catalog identity必须在 authority 启动测试前由 Git blob + raw SHA双重绑定，外部诱饵无效；
- runner seal/hash closure 属 mandatory authority input；任何漏级联均阻断 Cdiag；
- 该历史 portability checkpoint 当时把 definition of done 更新为 clean/pushed Cdiag→fresh
  r25→review/Cfreeze→fresh formal-r8→final quality/audit/acceptance；后续 7/12 审计已永久将 r25
  降级为 `superseded / non-candidate`，当前 definition of done 以本文件后续 r26 boundary 为准。
  此前 `can_enter_step5=no`、9.3.5 closed。

## Superseding Unit MySQL 7/12 remediation requirement boundary（2026-07-19）

- r7 的 `6 reports / 11 errors` 是不可变的 observed-failure fact，只描述当次 Maven error set；
  follow-up source/runtime audit 另确认第 7 个真实消费者
  `DatasetJdbcUtilsTest#getOrCreateDataSource`（`1` testcase node）。旧实现捕获
  `SQLException` 后仅 `printStackTrace`，连接失败仍可让 Surefire 伪绿，因此不得把 r7 的 `6/11`
  解释为消费者全集；
- current known database-consumer lower bound 必须至少为 `7 execution reports / 12 testcase nodes`，
  并与 historical observed failure=`6/11` 分字段持久化。发现更多消费者时仍须 fail closed、扩充机器
  契约并重跑正式链，禁止通过 catch-and-log、降级断言或只重跑已知失败集制造绿色；
- 修复不得改变 frozen Unit execution authority：完整 Unit 仍须由唯一 Maven invocation 产生
  `681 positive + 55 structural / 4,941 testcase / F0E0S0`。Unit fixture negatives 的新验收值为
  `42/42`，真实 lifecycle 保持 `5/5`；
- `step4-coverage-20260719-diagnostic-r25` 在 HEAD
  `5aaffbb4cd217d3d891c22eca4d3ae31d4e9d6e7` 上虽已 full-chain public-valid，但发生于上述
  消费者断言与 `7/12` 契约修复前，必须永久标记为
  `pre-remediation / superseded / non-candidate`，不得生成或复用其 candidate、threshold review 或
  freeze authority；
- 修复后的唯一授权顺序为 new clean/pushed Cdiag→fresh diagnostic-r26→new candidate/review→
  direct-child Cfreeze→fresh formal-r8（若该 ID 已被占用则使用下一可用 formal ID）→post gates。
  9.3.4 replacement formal/post-gate chain 全部 PASS 前，`can_enter_step5=no`，9.3.5 保持 closed。
  9.3.4 version signoff 后，9.3.5 只允许先执行 Gate 0 的 classification-debt migration；该债务关闭前
  9.3.5 version acceptance 仍保持 closed。

## Superseding diagnostic-r26 reviewed-Cfreeze requirement boundary（2026-07-19）

- 修复后的 Cdiag=`4fe86929de6206aa3e514c974635e90395c28b2e` 已 commit/push/clean；fresh-clone
  isolated r4 在 digest-pinned random-port MySQL 上得到 positive Maven=`0`、XML=`1/F0E0S0`，
  wrong-password Maven=`1`、XML=`1/F0E1S0`，并证明 container absent、port released；
- fresh `step4-coverage-20260719-diagnostic-r26`=`diagnostic-observed / completed / exit 0`，public
  validation PASS。required=`773+59/5707/F0E0S0`、Addon=`2/6/F0E0S0`、exec/session=`23/48`、
  source before=after、cleanup 与四个 demo DB exact restore 均满足 requirement；Unit fixture schema 2
  保持 historical=`6/11` 与 current lower bound=`7/12`，negative/lifecycle=`42/42 + 5/5`；
- aggregate exact line=`54624/76830`、branch=`26112/44870`；candidate SHA-256=
  `b8bd24113223e9a9c79280b248582ff34ad7a29013c2f93c4c7f4ebea682797a`，portable capsule 与两路
  independent review 均 PASS，B/H/M/L=`0/0/0/0`；r26 是 reviewed candidate source，但不是 formal、
  coverage audit、Step 4 feature acceptance 或 9.3.4 version acceptance；
- 当前只授权以 `4fe86929…` 为唯一 direct parent、且仅包含六个 machine formalization path 与
  `docs/9.3.4/**` 的 Cfreeze。Cfreeze commit/push/topology/clean 后必须运行 fresh formal-r8；
- formal-r8 PASS 后只开放 final implementation quality→Step 4 coverage audit→Step 4 feature
  acceptance。三门均 PASS 后才可把 Step 4 标为 `passed` 并开放 Step 5；9.3.4 仍为
  `in-progress`，必须继续完成 Steps 5–7 和 version signoff。只有 9.3.4 version signoff 后，9.3.5
  才可先进入 Gate 0 classification-debt migration，债务关闭前不得执行 9.3.5 version acceptance。

## Superseding formal-r8 recovery requirement boundary（2026-07-19）

- formal-r8 tested exact Cfreeze=`7c18019e…`，通过 required=`773+59/5707/F0E0S0`、Addon=
  `2/6` 与所有 child cleanup/lifecycle 后，于 `coverage-report` rc126；failure capsule、source
  recomputation、sensitive scan 与四 DB exact restore 已封存；
- r8 没有 exec-manifest、aggregate、coverage observation/gate、summary 或 Step 4 candidate/final，
  永久不得复用或拼接；
- report runner 必须以显式 `python3` 调用 Git `100644` Python tools；contract negative 必须精确绑定
  runner raw bytes、完整 logical executable stream、四个 tool target 与七个 top-level logical
  dispatch，拒绝 comment/heredoc/dead-scope/wrapper/literal/rebind/direct/dynamic/inline-Python 变异与
  `100755/120000/untracked/missing` Git-mode 变异，并在四个 `0644` copy 上
  证明 direct denied / interpreter PASS；
- replacement workitem denominator=`31`。当前 machine 必须为
  `diagnostic-ready / diagnostic-pending`；独立 pre-Cdiag code/docs reviews 已 PASS，唯一授权链为 new Cdiag→fresh r27→reviewed
  candidate/capsule→direct-child Cfreeze→fresh formal-r9→post gates `31/31`。所有后续版本门继续关闭。

## Superseding diagnostic-r27 high-water requirement boundary（2026-07-19）

- r27 is a complete public-valid diagnostic, but branch=`26111/44870` and complexity=`17658/35571` are
  below the r26 reviewed high-water by one each. Its candidate/capsule are non-canonical and must stay
  absent from Git authority; public candidate verification does not override this governed-high-water rule.
- The unique delta is the false outcome of `ExportWithChartTool.java:248`, caused by unspecified `Map.of`
  entry iteration before a short-circuit `break`. The only approved remediation is ordered test fixture data;
  production behavior, test cardinality, report authority and thresholds remain unchanged.
- Five fresh JVM/JaCoCo proofs must be treated only as Cdiag quality input. A clean/pushed new Cdiag and
  fresh diagnostic-r28 must reach branch >= `26112/44870` and complexity >= `17659/35571` before new
  candidate/capsule/review/Cfreeze authority exists.

## Superseding formal-r9 strict-umask receipt requirement boundary（2026-07-19）

- formal-r9 on Cfreeze=`34cd2452…` completed every child/report-inventory/exec prerequisite but stopped
  fail-closed in `coverage-report` with `E_OUTPUT: unexpected output mode: 0600`; outer restore and receipt
  succeeded. It is permanently `failed / excluded / non-reusable / non-candidate`; absent aggregate/XML/
  observation/gate/summary/candidate/final artifacts must never be synthesized or combined with a successor.
- The public effective-POM receipt contract remains exact `0644` under a strict `umask 077`. The publisher
  must keep its staging inode private while writing, then descriptor-bind `0644` and fsync before no-replace
  publication. Accepting `0600` or weakening the outer environment is forbidden.
- The recovery is itself a governed authority-tool delta, so its changed tools cannot be added to r9's
  formalization child. Machine state must return exactly to `diagnostic-ready / diagnostic-pending`, with
  pending threshold bytes=`0df17a…`; r28 observations remain historical high-water only, not successor
  evidence.
- The only authorized successor is a clean/pushed Cdiag containing the recovery records, followed by fresh
  diagnostic-r29 under the same strict umask, renewed high-water review/candidate/capsule/dual review,
  direct-child Cfreeze, a new formal run, then the post-formal quality→31/31 audit→acceptance sequence.
  Step 5–7, 9.3.5, and 9.4.0 remain closed.

## Superseding diagnostic-r32 WatchService delete high-water requirement boundary（2026-07-20）

- r32 completed all governed lanes and remains public-valid, but branch=`26111/44870` and
  complexity=`17658/35571` are below the reviewed high-water by one. This is a permanent
  `diagnostic-observed / non-freezable` boundary, not a failed-excluded run and not permission to lower a
  threshold.
- The only approved recovery is test-only: one existing mock-key test must explicitly exercise filtered delete
  non-match and match in addition to its unfiltered deletion path. Production behavior, POM, runner, identity
  counts, floors, critical policy, and exclusions must not change.
- Five focused JVMs restoring line 442=`4/4` and method branch/complexity=`11/12` / `6/7`, plus one full
  `foggy-core` suite=`97/F0E0S0`, are Cdiag quality proof only. The reviewed Low test-hygiene debt is
  non-blocking and cannot be used to widen scope.
- r33 has consumed the one fresh-run authorization and is excluded before canonical Unit authority; its final
  fallback cleanup failure does not establish a primary cause or cleanup closure.
- A clean/pushed docs-only Cdiag, independent governed readiness preflight, and fresh r34 must again prove every
  lane, source/cleanup closure, critical policy, line >= `54624/76830`, branch >= `26112/44870`, and complexity
  >= `17659/35571` before new candidate/Git-safe closure/review/Cfreeze authority exists. Step 5–7, 9.3.5, and
  9.4.0 stay closed.

## Superseding formal-r10 report-stage public-receipt requirement boundary（2026-07-20）

- A formal evidence receipt is public only when it is regular, non-link, non-empty, byte/provenance exact,
  and mode exact `0644`. The mode is mandatory provenance, not an ambient filesystem assumption.
- formal-r10 mechanically passed but is contract-invalid because its final report-stage effective-POM receipt
  was `0600`; its prior provenance omitted the mode. It is immutable historical evidence only and is
  excluded from audit and acceptance.
- The required implementation enforces and asserts `0644` after report-stage copy and before publication,
  and requires the provenance consumer to verify the recorded exact mode. Strict-umask real-copy and
  mutation/negative coverage are mandatory Cdiag static gates.
- Only a fresh Cdiag → fresh r35 → fresh candidate/review → direct-child Cfreeze → fresh formal → final
  quality → `legacy 31 + supplemental 4` audit → feature acceptance can reopen Step 4. Step 5–7, 9.3.5,
  and 9.4.0 remain closed until then.

## r35 reviewed Cfreeze requirement boundary（2026-07-20）

- The repaired Cdiag and fresh r35 have now proven all lanes/high-water and exact public receipt mode=`0644`.
  Their candidate/capsule reviews authorize a single Cfreeze only; diagnostic material is not formal evidence.
- The Cfreeze must be a direct single child of Cdiag `93b3993e…`, retain the governed Step 4→Step 6 hash
  closure, and be pushed before formal-r11 starts in a clean clone.
- formal-r11 must independently reproduce all authority checks under strict umask, including regular/non-link
  exact-`0644` final report receipt. Post-formal quality, same-Cfreeze Pivot supplemental coverage,
  replacement audit and feature acceptance remain mandatory; Step 5–7, 9.3.5 and 9.4.0 remain closed.

## formal-r11 evidence-complete signoff requirement boundary（2026-07-20）

- formal-r11, final replay, Pivot companion, quality and the 35-row replacement audit plus separate
  report-stage gate have passed. These are evidence prerequisites, not a substitute for official acceptance.
- Feature acceptance is issued: the Step 4-scoped canonical candidate is `ACCEPTED`, while the separate
  canonical 9.3.4 spec is `ULTRA_EXECUTING` for later Steps 5–7. Step 5 is `ready / not-started`; Steps 6–7,
  9.3.5 and 9.4.0 remain closed until their own gates complete.

## Superseding post-signoff Addon context-publication boundary（2026-07-20）

- A Step 5 rehearsal exposed a WSL `mtime_ns` inversion between the authenticated Step 3 parent marker and
  the Addon child context. This is a successor publication-order defect, not permission to weaken the
  `E_CROSS_RUN_SPLICE` consumer guard.
- The only remediation is authenticated parent-bound child timestamp publication in the declared Addon
  successor runner; frozen Step 3 artifacts, thresholds and report totals remain immutable.
- Since runner bytes change, formal-r11 and the accepted feature record are historical for the new bytes.
  A new Cdiag → diagnostic → candidate/review → direct-child Cfreeze → formal → Step 5 rehearsal chain is
  mandatory before downstream authority resumes.

## r37 Addon-context revalidation boundary（2026-07-20）

- The authenticated-parent mtime publication repair changes declared successor-runner bytes but leaves the
  strict anti-splice consumer rule unchanged. Cdiag `9743f97d…` and fresh r37 re-prove the all-lane diagnostic
  boundary; r37 is reviewed, not yet formal authority.
- Only one direct-single-parent Cfreeze may project the r37 candidate into `confirmed/formal-ready` state. A
  new formal run and Step 5 rehearsal remain required before any downstream authority resumes.

## r38 Addon-context formal revalidation boundary（2026-07-20）

- Direct-child Cfreeze 62361688d838ba0a73348900502924decfbeeb68 has consumed the authorized r37 candidate.
  Fresh formal-r38 is formal-passed with the unchanged governed required union, Addon companion, execution
  counts, high-water coverage, public receipt, source seal, and cleanup closure.
- Same-Cfreeze Pivot supplemental evidence, independent final implementation quality, and a newly scoped
  35-row-plus-one-gate r38 audit all pass. The authenticated parent/child mtime remedy is a separately
  governed BUG control and is not silently counted as an audit row.
- The canonical BUG is ACCEPTED by foggy-projects. formal-r11 and its acceptance remain historical for the
  changed runner bytes. The r38 acceptance changes Step 5 to ready/not-started for a fresh rehearsal only;
  Steps 6-7, 9.3.5, and 9.4.0 remain closed.

## Superseding Step 5 Pivot TTL clock-oracle boundary（2026-07-20）

- Fresh Step 5 rehearsal `step5-rehearsal-20260720-r3` stopped fail-closed at the SQLite broad integration
  variant. Its structured aggregate is `307/F1E0S0` with one failed `PivotIT#testOuterCacheTtlExpiryFlatPivotE1b`;
  release/Step 4/integration status stops at `step4-release-successor` / `child-integration` /
  `variant-sqlite-broad`. Raw logs/XML are not evidence inputs.
- The failure is a test-only correctness-oracle defect: the test combines TTL=1ms and a fixed sleep while
  `PivotPipeline` passes independent wall-clock values to `PivotOuterResponseCache` lookup/store. The only
  accepted repair is a private test-only controlled-time wrapper that delegates to the real local provider,
  explicitly advances from 100 to 102, and preserves the existing expiry/miss/re-execution/store assertions.
  No production API/SPI/POM/runner/floor/critical/exclusion/testcase-cardinality change is allowed.
- Focused fresh forks=`5 x 1/F0E0S0`, full `PivotIT=55/F0E0S0`, real provider contract=`8/F0E0S0`, and
  independent implementation audit=`ACCEPT / B/H/M/L=0/0/0/0` are implementation prerequisites only.
  They do not replace replacement authority.
- r38 acceptance is historical for its exact source and cannot be spliced with this new test source. The only
  authorized continuation is clean/pushed Cdiag → fresh diagnostic/candidate/review → direct-child Cfreeze
  → fresh formal → final quality/audit → new Step 5 rehearsal and portable replay. Until all pass,
  `can_enter_step5=no`; Steps 6–7, 9.3.5 and 9.4.0 remain closed.
