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
  `in-progress / r14 diagnostic sealed / thresholds confirmed /
  second Cfreeze working tree formal-ready / fresh formal pending`
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
  Addon companion 单列 `2/6`；contract/source identity=`21/21 + 22/22`，
  effective POM/toolchain/report inventory=`4/4 + 5/5 + 30/30`；Step 2 derived view/
  successor overlay=`12/12 + 12/12`，XML=`68/68`，logger=`9 类 / 14 case`；Unit fixture
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
  `SHA256SUMS`=`60/60`，当前 SHA-256=
  `a9b105ce2f8f640dfa09863e797697bcf9892a7b0fa68b38f83b5bbd7435afb4`；
  successor=`14/14`，SHA-256=
  `e63b315e9607c1f7efbf3f0bffe99e0800a4c1062e9fcfa5c2c569ecf67cc5db`；coverage
  contract diagnostic/formal SHA-256=
  `58f7dfc0716539dd741595aefcd3f5b37d6456703e8e5430854c721393a923f0` /
  `cabedad99522bb1c76e8cd35eb25922a1117d445256f1e346b47687dbadbb66e`；coverage tool /
  contract-negative / XML tool SHA-256=
  `ef5b78f25ffebf48e45e363a15fb1c4bc53341488a8e703133f01bb7b2c40bef` /
  `9df394efa046de4a494d31b00dd3900fe875f07a9e31aabccff29a231a1c1ecc` /
  `0600b66657824b3fc7cf3b15ca0474e0885977605c64c60366a4a13607eb18bf`；overlay contract /
  overlay tool / 当前 outer raw SHA-256=
  `001963c511b036d54c08abab2fcf0a0ab204b920614b35e10da809ed3f42c4d8` /
  `780e9a3d61626b8a37f85a185942de7c1862a119cd10b553962a98d5e2acd301` /
  `90b4b979e55c17243644cce186767a4647ce79c85b431adcb415bddd18cc1cec`；
  当前 outer executable-stream / lifecycle regression tool SHA-256=
  `065211912aab5227125ef02f40e2965fce7ff5060df5c7b91a902c4ad4f34cae` /
  `61bf7b990bdef6e0d75c53010644bcc6d1525a67119cd36c5f82eeb911e005fc`；
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
  当前 machine tuple 已恢复 `diagnostic-ready/diagnostic-pending`，必须先提交/推送新 Cdiag、
  fresh diagnostic 并重新 review/Cfreeze/formal；Step 5 与 9.3.5 保持关闭。

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
- formal-r1 保持 immutable failed evidence，不复用、不修补、不盲重跑。当前 canonical state=
  `diagnostic-ready/diagnostic-pending`；新 Cdiag -> fresh diagnostic -> review -> direct-child
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
- working-tree machine state 已为 `formal-ready/confirmed`。Cfreeze commit 必须是
  `322bb346…` 的 direct-single-parent child，required paths 恰为 threshold/contract/manifest，
  其他变化仅允许 `docs/9.3.4/**`；
- direct-parent/formal-delta、push、clean identity 通过后才允许 fresh formal。Formal PASS 前
  `can_enter_coverage_audit=no`；final implementation quality PASS 前仍不得启动 coverage audit；
  audit PASS 前不得 acceptance。Step 5 与 9.3.5 保持关闭。
