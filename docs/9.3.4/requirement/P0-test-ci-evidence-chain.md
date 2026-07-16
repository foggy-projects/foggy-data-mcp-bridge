---
doc_role: workitem
doc_purpose: Define the 9.3.4 test runner, database, coverage, CI and immutable release evidence requirements.
version: 9.3.4
priority: P0
status: in-progress
acceptance_status: not-started
created_at: 2026-07-14
updated_at: 2026-07-16
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
  `in-progress / diagnostic r1 fail-closed / r2 pending`（不是 `passed`）；
- Step 2 confirmed successor：`step2-candidate-r8e-20260715`，
  `724 positive + 59 structural` 已由 Surefire/Failsafe exact 覆盖，testcase=`5,205`，
  F/E/S=`0/0/0`；
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
  Addon companion 单列 `2/6`；raw contract/effective POM/toolchain/report inventory
  负例分别为 `8/8`、`4/4`、`5/5`、`27/27`；Step 2 derived view/successor
  overlay 负例=`12/12`、`8/8`；
- toolchain receipt 绑定 Step 1 raw 工具版本、compiler/JaCoCo/test ASM=
  `9.6/9.7/9.7.1` 与 24 个 production module effective compiler；Step 4 report
  amendment=`10 rows = 4 new + 6 changed`，SHA-256=
  `5a1a07e2c47835fa244b90a06334341e13660a305d9eb7c74c64ee2f36a06504`，
  successor declared amendments=`15`；本地 `SHA256SUMS` 已生成并通过 exact 49 项校验，
  manifest SHA-256=
  `c735e8c1f7b74d72afe2d1d1872128d11a16acbd7373c750e59709624560106e`；
- clean/pushed HEAD `bc100b0f63bd3ff62d1105611dae41741790aedd` 的 diagnostic r1
  `step4-coverage-20260716-diagnostic-r1` 在 `child-unit` 以
  `3115 tests / 1 failure / 0 errors / 0 skipped` fail closed。根因是
  `PreAggregationDataValidationTest` 腐化 daily 表但实际命中 monthly，并允许
  raw-vs-raw/nullable-empty 伪绿；修复 focused=`9/F0E0S0`、组合=`57/F0E0S0`，
  source SHA-256=
  `affb6e415c1770eae7c9ab6f3505ac67acfe03eac1461bd2a48addf3ee790623`，见
  `docs/9.3.4/workitems/BUG-step4-preagg-validation-wrong-table.md`；
- Step 4 JaCoCo 仍未通过：threshold=`diagnostic-pending`，r1 未到 aggregate/report 阶段，
  尚无 all-lane aggregate baseline/review 或 Step 4 exit evidence。下一动作是提交、推送
  修复并验证新的 clean HEAD 后执行 r2。Step 5
  portable authority、Step 6 CI/release 与 Step 7 version acceptance 仍未完成，不能
  据此签收 9.3.4 或把 9.3.5 标为 ready。
