---
doc_role: workitem
doc_purpose: Define the 9.3.4 test runner, database, coverage, CI and immutable release evidence requirements.
version: 9.3.4
priority: P0
status: in-progress
acceptance_status: not-started
created_at: 2026-07-14
updated_at: 2026-07-17
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
  `in-progress / r8 bootstrap-negative excluded / lifecycle remediation quality passed /
  ready-for-commit-and-fresh-r9`
  （不是 `passed`）；
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
- Step 4 已完成 diagnostic-ready 静态收口：exact `23 exec / 48 sessions`，
  required report overlay=`773 positive + 59 structural / 5,707 testcase / F0E0S0`，
  Addon companion 单列 `2/6`；contract/source identity=`20/20 + 22/22`，
  effective POM/toolchain/report inventory=`4/4 + 5/5 + 30/30`；Step 2 derived view/
  successor overlay=`12/12 + 12/12`，XML=`63/63`，logger=`9 类 / 14 case`；Unit fixture
  negatives=`36/36`（原 fixture/manifest probes=`20/20`、connection typed=`7/7`、
  atomic publisher=`3/3`、profile boundary=`6/6`），negative receipt 文件
  schema/tamper 另为 `4/4`，真实 lifecycle=`5/5`；restricted credential receipt 的 closed
  Unit Maven window 必须由 root `configure init_connect -> Maven -> 同一 root batch 先
  disable 再按 connection_id SELECT` 闭合，保存有序 observed user，并证明窗口内全部
  non-super MySQL connections exclusively 使用 run-owned credential；callback 返回后的
  provisioner `foggy` 控制面连接在窗口外；
- toolchain receipt 绑定 Step 1 raw 工具版本、compiler/JaCoCo/test ASM=
  `9.6/9.7/9.7.1` 与 24 个 production module effective compiler；Step 4 report
  amendment=`12 rows = 4 new + 8 changed`，SHA-256=
  `998ae49927721576c26327b8477010b0238843565e6afdbc70987e97544a028c`，
  successor declared amendments=`18`，SHA-256=
  `8e21b8527f290061361ef0b8fbf084d51b2536ef479e1f70e45488f996090bfc`；本地
  `SHA256SUMS`=`60/60`，SHA-256=
  `0c4e6c18f4af0a2c35418604ce80d20828ceb4c275375b7d12461b4683553a81`；
  successor=`14/14`，SHA-256=
  `acb580e92a72eb407f31f5d6f9a8139a3509f3a0bfbf58537922465f4086a112`；coverage
  contract diagnostic/formal SHA-256=
  `c062219a6335ae41330c6d5924d6fce60941c5d168b361081fbb41df77428477` /
  `341991d6b5a15d19cdb9e0de70a8cc6ace29480227596c413c07e6bf7fdbc73d`；coverage tool /
  contract-negative / XML tool SHA-256=
  `27afd37350fa7f1646fba4be59791ec6bdec94fe57e0cdfecc2a08e0f43f2f18` /
  `732d799619461a4b49c8e9bfbb0a3487b107c36110b9e55cd91a405352d0ddb0` /
  `b837314ac4166eeeab94124b53e4f776dcdf8095a3b3915e14e45b81d910d439`；overlay contract /
  overlay tool / outer SHA-256=
  `84d09bfc333bb40d8ef830979734933717555845cebe9943f70ff7087a9a482d` /
  `1fea2816504519b7e7f1dc6839744ee943a9a4bf3feb783375e21e935da63d31` /
  `02a920d91d1b8792cad47d65ce860352a8e9ecf39106f4489a714df01888dbaa`；
  successor database/required contracts SHA-256=
  `553dabf2b4c266b531fb4ce36f4a498dce223b6449106274a3a2b103ccb775ea` /
  `893ac03231cb4f6fd8ae427c01aa3f9f04267c96e3945814b9b70a3445a58af5`；
- Unit fixture contract/tool/runner/datasource-adapter SHA-256=
  `7aa1e21aef85b51a13aacc8c134a1c363c595deffbfb3acf6aafdb942519b53a` /
  `cc19390ce6c0cfb307b7632dbe4e25540b1e4d49d11ec1512739f6724646d345` /
  `45536c0a969731f6b7c87acecdb225b13a8a0fca45a9a04c9cdfb2173fc60c66` /
  `9500cd4d50930b121a36798857cd0a1cc0c8b2190b0a2fc9ad0ea464394bb256`；
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
- lifecycle contract remediation 已通过 dynamic `9 类 / 14 case`、Unit shape/seal=
  `13+3`、Integration=`11+5`，并由 raw-byte runner seal关闭 false/subshell、source/eval 与
  CRLF drift；两路独立 quality B/H/M/L=`0/0/0/0`。本轮 closure 必须 commit/push 并证明
  clean `HEAD == origin/main` 后运行 fresh r9；这些结果仍不表示 Step 4 exit 已通过；
- 9.3.4 只有在 fresh Step 4 Unit replacement、fresh formal、实现质量闸门、测试证据覆盖
  审计与版本验收全部通过后，才允许带上述分类债务签收；任一失败即撤销临时例外并保持
  Step 5 关闭。该债务不得跨过 9.3.5 版本验收；
- Step 4 threshold 仍为
  `diagnostic-pending`，尚无 all-lane aggregate baseline/review 或 Step 4 exit evidence。
  `can_enter_coverage_audit=no`，Step 5/formal/coverage audit/acceptance
  portable authority、Step 6 CI/release 与 Step 7 version acceptance 仍未完成，不能
  据此签收 9.3.4 或把 9.3.5 标为 ready。
