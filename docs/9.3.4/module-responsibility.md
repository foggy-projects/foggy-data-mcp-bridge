---
doc_role: module-responsibility
doc_purpose: Define ownership and dependency boundaries for the 9.3.4 test and CI evidence-chain delivery.
version: 9.3.4
status: ready
created_at: 2026-07-14
updated_at: 2026-07-19
---

# 9.3.4 Module Responsibility

## 文档作用

- doc_type: module-responsibility
- intended_for: project-root-session / build owner / CI owner / reviewer
- purpose: 明确 runner、测试、DB、coverage、workflow 与 release evidence 的单一
  owner；模块职责不拆成独立执行会话。

## Delivery Mode

- mode: single-root-delivery
- root workspace: `foggy-data-mcp-bridge`
- progress authority: `progress/test-ci-evidence-chain-progress.md`
- rule: 所有模块由一个项目顶层会话按 Step 1~7 实施，不生成子模块 requirement、
  prompt 或 progress 副本。

## 职责矩阵

| Module/capability | Responsibility | Dependencies | In scope | Out of scope | Stage |
|---|---|---|---|---|---|
| root Maven build | central Surefire/Failsafe/JaCoCo versions, defaults and argLine ownership | all reactor modules | runner split, plugin management, coverage wiring | production module split | 2/4 |
| root authority scripts | workspace source/reactor execution inventory, migration cardinality, exact report, DB, coverage XML, artifact assertions | Maven/Docker/git/JDK | nested/variant-aware fail-closed entry and expected-negative probes | business behavior ownership | 1/3/4/5 |
| owning reactor modules | classify/rename tests and produce one runner’s fresh XML；Step 4 再产 exec | root build contract | all `Test*/*Test/*IntegrationTest/IT*/*IT` candidates | local bypass of root authority | 1/2/3/4 |
| `foggy-dataset-model` | broad SQLite integration and five-DB semantic parity/capability contracts | dataset/demo fixtures | preflight, QueryFacade/native, dialect assertions；Unit fixture adapter 不得覆盖其 profile datasource | rerun all unit tests per DB | 2/3/4 |
| Runtime/MCP/fsscript/cache/addons | module integration ownership and 9.3.1–9.3.3 successor regressions | model/fsscript/runtime ports | correctly named IT, hermetic/external lane ownership, exact reports, Step 4 exec provenance | new lifecycle/API design | 2/3/4/5 |
| `foggy-dataset-demo/docker` | deterministic five-DB images/init/sentinel fixture | Docker Compose | pinned image identity, automated init, before/after | production deployment redesign | 3 |
| Step 4 Unit MySQL replacement authority | enforce the 9.3.4-only full Unit lane replacement and machine-readable fixture contract | frozen Step 2 execution/discovery inventory, Step 3 provisioner, Docker, `scripts/v934/step4/unit-mysql57-fixture-contract.json` | `681 positive + 55 structural / 4,941 testcase` in one Maven invocation, pinned/run-owned MySQL 5.7, restricted non-super exclusive connection receipt, typed schema/publisher/profile-boundary/lifecycle/cleanup evidence | permanent DB-in-Unit classification or unreviewed execution-key expansion | 4 |
| `foggy-dataset` classification-debt and datasource-adapter owner | find, migrate and reclassify every real DB consumer represented by or discovered beyond the r7 historical set；scope fixture credentials only to its test resource | fixture contract + `V934_UNIT_MYSQL57_URL/USERNAME/PASSWORD` placeholders + `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md` | current reviewed lower bound 7 execution keys / 12 testcase nodes, profile-boundary isolation, discovery expansion, debt closure evidence | global Spring datasource/profile override, treating historical 6/11 or current 7/12 as exhaustive proof, or carrying the debt through 9.3.5 version acceptance | 4 / 9.3.5 Gate 0 |
| build-only coverage reporter | merge/report reactor UT/IT exec | root/modules | aggregate XML/HTML only | empty-project `jacoco:check`, production classes, Launcher packaging | 4 |
| coverage verifier | fail-closed aggregate XML/package/class counter checks and model merged-exec gate | reporter + reviewed thresholds | aggregate/critical/module threshold authority | runtime-discovered threshold acceptance | 4 |
| `.github/workflows` | reusable jobs, exact five-cell collector, stable required aggregator, artifact upload/download verification | authority scripts | PR/main/release wiring, cardinality and result-state checks | branch protection claim without evidence | 6 |
| `release.yml` + release Dockerfile | publish exact tested JAR/image + evidence archive/digest | successful full gate | tag SHA, downloaded JAR, image embedded-JAR hash and release assets | source rebuild/skip-test Docker stage | 6 |
| version docs | contract, inventory, progress, quality/coverage/acceptance and roadmap | all evidence | one top-level authority package | per-module duplicated plans | 1~7 |

## Dependency Boundary

- build-only coverage reporter may depend on reactor modules for report aggregation；生产
  modules不得依赖 reporter。reporter 只出 XML/HTML，versioned verifier 才持有
  aggregate/critical threshold decision。
- tests may depend on existing production APIs/fixtures；9.3.4 不为测试便利新增 model
  → Runtime/MCP/Addons 反向依赖。
- DB sentinel fixture 属于 test infrastructure，不进入 production model contract。
- DB/外部依赖测试移交 Step 3 仍是默认通则。r7 只为 9.3.4 触发一个临时例外：由
  `scripts/v934/step4/unit-mysql57-fixture-contract.json` 机器权威约束完整 Unit lane 的
  Step 4 replacement。contract 中 6 keys / 11 nodes 是已知清单，不是穷尽证明。
- replacement credential 只允许由 `foggy-dataset` test resource 的三个
  `V934_UNIT_MYSQL57_*` placeholder 消费；禁止全局 `spring.datasource.*`、
  `spring.test.*` 或 active-profile 参数覆盖 `foggy-dataset-model` SQLite 等其他 profile。
  outer/callback 还必须拒绝 underscore/dotted/hyphen Spring/custom key 与 `@argfile`、
  `VMOptionsFile`、`javaagent/agentlib/agentpath` 间接注入；adapter consumer inventory 使用
  scrubbed Git environment、`HEAD` tree 与 no-replace object。receipt 的 closed Unit Maven
  window 从配置 `init_connect` 到 Maven 返回后的同一 root batch 先 disable 再 SELECT，
  保存有序 `connection_id + observed user`，并证明该窗口内所有 non-super MySQL
  connections exclusively 使用 restricted run-owned credential；callback 后 provisioner
  `foggy` 控制面位于窗口外。
- 该例外下 Step 2 identity/cardinality 仅保留结构证明；旧 Unit green 不再承担
  correctness，必须由 fresh Step 4 replacement 完整取代。只有 fresh formal、实现质量
  闸门和测试证据覆盖审计均通过，9.3.4 才可携带已登记债务签收；对应 DEBT workitem
  必须在 9.3.5 验收前关闭。
- workflow 只调用 versioned authority scripts；不在 YAML 复制一套 count/skip/hash
  规则。
- release consumer 只下载 authority producer 的 artifacts，不重新构建源码；Docker
  image 直接嵌入同一 JAR 并回读 SHA。

## Forbidden Placement

- 不把 CI/result-state logic 放到生产 Java。
- 不把 coverage baseline 生成器放进 Launcher。
- 不对无 production classes 的 aggregate reporter 执行空 `jacoco:check` 冒充门禁。
- 不让 release Dockerfile 运行 Maven 或 `-DskipTests` 重建发布物。
- 不通过 module profile 重新引入 active-by-default multi-db Surefire executions。
- 不把 9.3.4-only Unit replacement 扩成永久例外，不复用 ambient MySQL，不以只重跑
  已知 6 keys / 11 nodes 代替完整 lane，也不把 Step 2 旧绿色宣称为 correctness。
- 不用全局 Spring datasource/test 参数把 run-owned MySQL 注入所有 reactor test forks；
  不允许 SQLite driver、profile 初始化脚本与 MySQL JDBC URL 混合。
- 不在未完成全部真实 DB consumer 的迁移/重分类及 fresh evidence 前关闭
  `DEBT-unit-mysql57-fixture-classification-migration`。
- 不在本版本创建 9.4.0 production `model-api/core/jdbc/starter/web` 模块。

## Current exception evidence boundary

- Unit fixture quality r2=`step4-unit-fixture-quality-20260716-r2` 在 commit
  `a603f839a98d99b2d7beb8379f76b4d85539328c`、source=`3,981 files` /
  `087d074f3497aff3fe305806f82b4d62ff41cdd4d3b26556e58f034138b14c2c`
  上先通过 lifecycle `5/5`，随后因全局 Spring datasource 覆盖 SQLite profile，使
  `foggy-dataset-model` 以 `3,115 tests / 631 errors` fail closed；根因是
  `org.sqlite.JDBC` 与 `jdbc:mysql://127.0.0.1:13306/...` mismatch。r2
  excluded/non-reusable。
- fresh Unit fixture quality r3=`step4-unit-fixture-quality-20260716-r3` 已在 commit
  `50161a0a869430e353f3933d9bb00dda59d9c4b1`、source before=after=`3,982 files` /
  `1db06cc18bb86c288ffa79e7094e3b9e509d866ddc23b6c93bcaaa0422b99eb2`
  上通过：唯一 Surefire invocation=`681+55=736 raw reports / 4,941 / F0E0S0`；
  negatives=`36/36`、receipt schema/tamper=`4/4`；closed receipt 的 `18/18`
  connections 全部为 `v934_unit`；真实 lifecycle=`5/5`，run-owned cleanup=`0/0/0` 且
  port free。evidence window 外 demo MySQL 已按 exact ID
  `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166`
  恢复为 `running/healthy`。record=
  `docs/9.3.4/evidence/step-4/step4-unit-fixture-quality-r3-pass-20260717.md`。
- 当前 Unit fixture 机器契约静态验证=`20/20`，coverage contract mutations=`21/21`，
  Unit fixture negatives=`36/36`（原
  fixture/manifest probes=`20/20`、connection typed=`7/7`、atomic publisher=`3/3`、
  profile boundary=`6/6`），negative receipt 文件 schema/tamper 另为 `4/4`，真实
  lifecycle=`5/5`，report inventory=`30/30`。
- r8 historical identity：top=`60/60` /
  `6be72b655b322d89763fc4871c953cd0d4bd5516206964d4cb1f8117b3133376`；
  successor=`14/14` /
  `e63b315e9607c1f7efbf3f0bffe99e0800a4c1062e9fcfa5c2c569ecf67cc5db`；
  coverage diagnostic/formal=
  `58f7dfc0716539dd741595aefcd3f5b37d6456703e8e5430854c721393a923f0` /
  `cabedad99522bb1c76e8cd35eb25922a1117d445256f1e346b47687dbadbb66e`；
  fixture contract/tool/Unit runner/datasource adapter=
  `7aa1e21aef85b51a13aacc8c134a1c363c595deffbfb3acf6aafdb942519b53a` /
  `cc19390ce6c0cfb307b7632dbe4e25540b1e4d49d11ec1512739f6724646d345` /
  `45536c0a969731f6b7c87acecdb225b13a8a0fca45a9a04c9cdfb2173fc60c66` /
  `9500cd4d50930b121a36798857cd0a1cc0c8b2190b0a2fc9ad0ea464394bb256`；
  declared amendments=`18` /
  `8e21b8527f290061361ef0b8fbf084d51b2536ef479e1f70e45488f996090bfc`；
  overlay contract/tool=
  `001963c511b036d54c08abab2fcf0a0ab204b920614b35e10da809ed3f42c4d8` /
  `780e9a3d61626b8a37f85a185942de7c1862a119cd10b553962a98d5e2acd301`。
- r8 时点状态=`r8 bootstrap-negative excluded / lifecycle static contract remediation quality
  passed / ready-for-commit-and-fresh-r9`。r8 在 run-owned identity 与 lane 前因过期 Unit
  direct-trap shape fail closed；修复后 dynamic=`9 类 / 14 case`、Unit shape/seal=
  `13+3`、Integration=`11+5`，两路 quality B/H/M/L=`0/0/0/0`。本轮 closure 必须
  commit/push 并证明 clean `HEAD == origin/main` 后才可运行 fresh r9；该状态已由下方 r9
  ownership boundary supersede；
  `can_enter_coverage_audit=no`，Step 5、formal、coverage audit、acceptance 仍关闭。r3 与
  focused lifecycle 都不代表 Step 4 exit 已通过。

## Superseding r9 ownership boundary（2026-07-17）

- `coverage_exec_tool.py` 负责 raw exec/session/class-ID inventory、fresh production class
  universe strict match 与 JaCoCo ID aggregate union；不得通过 POM/agent package exclusion
  隐藏 runtime/test/dependency execution data；
- `coverage_tool.py` 与 `coverage-contract.json` 负责冻结 production consistency scope；
  `coverage_xml_tool.py` 负责 downstream manifest/provenance/merge-semantics exact consumption；
- successor overlay 只维护 Step 4 runtime binding，不改变 Step 3 frozen authority；
- lifecycle static/dynamic test 只负责证明 logger/finalizer/signal/fixture cleanup contract，
  不得把 raw substring 或任意 `ShapeError` 当作 semantic negative；
- r9 failure 是 coverage tooling identity model defect，不是 production Java 或 9.3.5/9.4.0
  module boundary；修复不得扩到产品 API；
- r9 时点 Step 4=`in-progress / r9 excluded / remediation quality passed / commit pending`；
  两个 blocker BUG 的 coded regression 与三路正式质量均通过，fresh r10 前仍须
  commit/push 并证明 clean `HEAD == origin/main`。该 ownership boundary 已由后续 runs
  supersede；Step 5 与后续 gate 始终不得提前。

## Superseding formal-r2 ownership boundary（2026-07-17）

- `addons/foggy-data-viewer` 负责本次 deterministic test regression：只修改
  `ListPresetServiceTest.FileStoreTests#shouldIsolatePresetByUserAndBusinessKey`，用不存在 ID
  查询强制执行已有 regular-file 的 filename-false outcome；
- `FileSystemListPresetStore` 生产实现、Data Viewer public API、starter 装配与模块边界均不改；
  本缺陷属于 test evidence stability，不扩到 9.3.5/9.4.0 产品设计；
- Step 4 coverage owner 负责封存 formal-r2 failure、恢复 diagnostic machine trio，并完整执行
  new Cdiag -> fresh diagnostic -> candidate/review -> Cfreeze -> fresh formal；
- test/report ownership 保持 frozen cardinality：无新 `@Test`；focused 5/5 probe 106 hit，
  Data Viewer module=`104/F0E0S0`；all-lane authority 仍必须由 Step 4 runner 重新产生；
- formal-r2 recovery 时点 Step 4=`in-progress / r15 Unit fail-closed / deterministic timing-oracle remediation verified /
  new Cdiag pending`，machine=`diagnostic-ready/diagnostic-pending`；
  `can_enter_coverage_audit=no`、`can_enter_acceptance=no`，Step 5 与后续 gate 不得提前。

## Superseding diagnostic-r15 ownership boundary（2026-07-17）

- `foggy-bean-copy` test owner 只负责修改
  `Bean2MapUtilsTest#testCachingMechanism` 与 `testPerformanceWithManyObjects` 的 test oracle：
  删除环境敏感单次计时门，保留重复/批量复制 correctness；
- `Bean2MapUtils` production cache、public API、POM、coverage runner/floor/critical/exclusion 均
  不属于本次修复范围；不新增反射耦合、test seam 或 benchmark 到 Surefire correctness lane；
- Step 4 evidence owner 负责封存 r15 partial `124/F1E0S0`、`2/48 sessions`、最终 sensitive scan
  与 success-only artifact absence，并禁止跨 run 拼接；
- test/report owner 保持 frozen cardinality：无 `@Test` 增删；focused 10/10、class 23、module 27
  均 F0E0S0 只证明 remediation，不替代 all-lane authority；
- Step 4 coverage owner 已完成 r15 recovery quality `PASS / 0/0/0/0`；r15 closure 按 new Cdiag ->
  fresh diagnostic -> candidate/review -> Cfreeze -> fresh formal -> final quality 推进；machine=
  `diagnostic-ready/diagnostic-pending`。该时点已由下方 r16 boundary supersede。

## Superseding diagnostic-r16 / reviewed Cfreeze ownership boundary（2026-07-17）

- Step 4 evidence owner 已在 pushed/clean Cdiag
  `f863c672029d5d1e5a4903df74cf6cba22a04a85` 上封存 fresh diagnostic r16：
  `773 positive + 59 structural / 5,707 testcase / F0E0S0`，`23 exec / 48 sessions /
  16,948 unique execution class identities`；
- coverage review owner 已核对 aggregate=`54,624/76,830 line`、`26,111/44,870 branch`，
  12 个 critical class 全部通过，唯一 `NamespaceScope` branch 按 schema 保持 `N/A`；
- threshold review owner 已封存 candidate SHA-256=
  `2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919` 与 review
  SHA-256=`88b99e76e5584d3cd17bcdcffd138f1fe6655ce0b7795d3430d2f15a018c8fb3`，
  B/H/M/L=`0/0/0/1`；Low 的 owner action 是由 fresh formal 复现 aggregate；
- machine contract owner 已将 canonical threshold 切为 `confirmed`（SHA-256=
  `ca6a25c66fbbe9a595adde74f1b7589bd3829b93edebfd5b11dc394ab8d088c8`），contract 切为
  `formal-ready`（SHA-256=
  `6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`）；
- Cfreeze owner 只能提交 machine contract trio 与 `docs/9.3.4/**` 文档证据，并保持
  formal-delta direct-single-parent allowlist；runner、production、public API、测试清单不属于本次
  Cfreeze delta；
- 当前授权仅为 direct-child Cfreeze commit/push 与一个唯一 fresh formal；Cfreeze 尚未提交、
  formal 尚未运行。fresh formal PASS 前不得进入 final quality 之后的 coverage audit/acceptance，
  不得声明 Step 4 完成，Step 5 与 9.3.5 保持关闭。

## Superseding formal-r3 failure / recovery ownership boundary（2026-07-17）

- Step 4 evidence owner 负责封存 Cfreeze `a63c82c53ebaad1a1c22d78647fbda70b4bd6594`
  上的 formal-r3：全 lane complete，line exact，branch `26110/44870` 低于 exact
  `26111/44870`，final gate immutable fail closed；
- coverage-delta owner 负责保留唯一定位结论：
  `QueryModelSupport#getMergedJoinGraph` line 316 inner DCL 的一个 outcome 依赖调度；
  该差异不转交 production owner，因为产品 single-build 语义没有回归；
- `foggy-runtime-api` test owner 仅负责既有
  `RuntimeNamedDataSourceResolverBindingTest#publicationGuardSerializesTheCallbackWithAConcurrentRebind`
  内的受控 QueryModel 并发 oracle；不得新增/改名 `@Test`、修改 production API
  或加入无界等待。当前 targeted/overlay 与 5/5 fresh Maven/JVM PASS，
  QueryModelSupport probes=`34/629`、bitmap unique=`1`；`foggy-runtime-api` full module=
  `128/F0E0S0`，`RuntimeNamedDataSourceResolverBindingTest=5/F0E0S0`；test owner 的
  deterministic verification 已完成；pre-Cdiag quality owner 已完成 formal
  implementation quality，结论=`PASS / 0/0/0/0`，machine/contract/overlay/negative suites
  通过；
- machine contract owner 已恢复 `diagnostic-ready/diagnostic-pending`和 `60/60`
  manifest；不得在 fresh diagnostic/review 前恢复 r16 confirmed threshold；
- Step 4 coverage owner 下一步负责 Cdiag commit/push/clean -> fresh diagnostic ->
  candidate/review -> direct-child Cfreeze -> fresh formal；后置 gate owner 只能在 fresh formal
  PASS 后按 final quality -> coverage audit -> acceptance 顺序接手；
- 当前 Step 4 `in-progress`，Step 5/9.3.5 owner 无开工授权。

## Superseding diagnostic-r17 final-mysqld handoff ownership boundary（2026-07-17）

- Step 4 evidence owner 负责封存 Cdiag
  `316a71f753827f8f34063b0eb0669271f696c5ee` 上的 r17 child-unit immutable failure，明确
  cleanup-only receipt 不是 fixture PASS，并保持 Unit XML/exec、source-after、aggregate、
  observation、candidate/summary absent；r17 excluded/non-reusable；
- `foggy-dataset-demo/docker` successor authority Compose owner 只负责 MySQL 5.7/MySQL 8
  final-server readiness amendment：PID 1 `/proc/1/comm` 必须精确为 `mysqld`，且原
  `mysqladmin ping` 同时成功。frozen Step 3 provisioner、production deployment 与其他 DB
  authority 不属于本次改动；
- Step 4 fixture owner 继续负责 health 后的 business identity、schema/sentinel、四个
  `preagg_watermark` 和 run-owned cleanup；final-mysqld guard 不得降低这些条件，也不得以
  sleep、port-open 或 ambient listener 代替；
- lifecycle tooling owner 负责 probe-local failure diagnostics：no-clobber、mode `0600`，
  controlled failure 保留原 typed `FixtureError` code（如 `E_LIFECYCLE`/`E_CLEANUP`）并携带
  diagnostics path；成功日志只能在 expected terminal state 和 final
  cleanup 后删除。evidence owner 在正式引用 failure log 前负责 sensitive scan；
- runtime verification owner 已完成 MySQL 5.7/MySQL 8 GREEN、修复旧字节 lifecycle
  `15/15`；penultimate current-byte `5/5` receipt SHA-256=
  `159fbe80595933e29f05f13c0f1d82e9b65d7f947dddec253477f0b3f3876799`。最终 current-byte
  `step4-unit-lifecycle-handoff-current-20260717-r2` 在 fixture tool SHA-256=
  `9be62daaf7a3d2d873c7647078c0bf798ab25c491a163e90960d4143965be5be` 上完成 `5/5`，receipt
  SHA-256=`e3bad41ad9ec634c1702ffe20f4c0ddbff3050a227956e2ba9054a11f6b606c7`；successful
  `provisioner.log` absent，demo restore=`0/0` 且四库 healthy/listening。contract owner 已完成
  overlay=`12/12`、Unit=`36/36`、coverage=`27`、
  source/Git=`22`、replay=`12` negatives；这些均不替代 clean-source all-lane authority；
- implementation-quality owner 已完成 pre-Cdiag formal gate，结论=
  `PASS / B/H/M/L=0/0/0/0`，记录=
  `docs/9.3.4/quality/step4-diagnostic-r17-recovery-implementation-quality.md`。该 PASS 只授权
  machine/commit owner 创建并 push 唯一 replacement clean Cdiag，再运行一个 fresh
  diagnostic；dirty worktree full Unit 在 source seal 预检失败、未执行 Maven，不属于 Unit
  PASS/FAIL 行为结论；
- Step 4 coverage owner 随后按 fresh diagnostic -> candidate/review -> direct-child Cfreeze ->
  fresh formal 推进；post-formal owner 只能再按 final implementation quality -> coverage audit
  -> acceptance/signoff 接手。不得改变 test/report cardinality、production/public API、coverage
  floor、critical set、threshold 或 exclusion；
- 当前 machine=`diagnostic-ready/diagnostic-pending`，Step 4=`in-progress`。pre-Cdiag formal
  quality 已 PASS；replacement Cdiag/fresh diagnostic pending，且仅这两个动作已获授权。full
  Unit authority、Step 5、audit、acceptance、9.3.5 owner 仍无开工授权。

## Superseding diagnostic-r18 / deterministic Pivot oracle ownership boundary（2026-07-17）

- Step 4 evidence owner 负责把 clean Cdiag
  `5be1edaa16c5883cde2f66396ac26a1ae113430b` 上的 r18 封存为有效 diagnostic PASS，同时记录
  aggregate 相对 r16 reviewed high water 的 `-2 line/-4 branch`；r18 candidate 必须保持 absent，
  decision=`threshold-candidate-not-authorized`，不得由 threshold owner 降低高水位；
- coverage-delta owner 负责保留精确归因：差异只涉及 `BaselineRatioCalculator=-2 line/-3 branch`
  与 `ResultShaper=-1 branch` 的 PostgreSQL exec bitmap，不得据此修改 production、critical set、
  coverage floor、exclusion 或 runner merge semantics；
- `foggy-dataset-model` test owner 只负责既有
  `PivotSqlParityIT#testBaselineRatioParity` 中的 deterministic semantic oracle：null column 保留为
  可见 cell 但不成为 baseline domain，null row 独立分组并由 tree 映射为 `__null__`。不得新增/
  改名 `@Test`，不得扩大到产品 Pivot API；focused=`3 x 1/F0E0S0`、两目标 bitmap=
  `3/3 identical`、完整 test class=`23/F0E0S0`，test cardinality 保持 `23`；
- successor authority owner 负责同步同一已声明测试 amendment、protected test tree、database/required/
  overlay contracts 与两级 SHA manifest；当前 positive validators 为 successor=`14/14`、top=`60/60`、
  database=`7 variants/29 reports/370 nodes`、required=`45/446/F0E0S0`、overlay=
  `22 amendments/9 bindings`，不得修改 frozen Step 3、report cardinality 或 coverage totals；
- implementation-quality owner 已完成本次 deterministic oracle、顶层文档与 authority 哈希链 formal
  gate，结论=`PASS / 0/0/0/0`，record=
  `docs/9.3.4/quality/step4-diagnostic-r18-pivot-null-axis-implementation-quality.md`。machine/commit
  owner 当前可提交并 push 一个 replacement Cdiag；Step 4 coverage owner 随后运行
  fresh r19，且只有 r19 aggregate 不低于 r16 reviewed high water 才可把 candidate/review owner 接入；
- 当前 machine=`diagnostic-ready/diagnostic-pending`，Step 4=`in-progress`。Cfreeze、formal、final quality、
  coverage audit、acceptance、Step 5 与 9.3.5 owner 均无开工授权。

## Superseding diagnostic-r19 / Cfreeze ownership boundary（2026-07-17）

- diagnostic owner 已封存 Cdiag `613b11a0…` 的 r19：`773+59/5707/F0E0S0`、Addon=`2/6`、
  `23/48/16931`、aggregate=`54624/76830 line, 26111/44870 branch`、critical=`12/23/below0`；
- review owner 已由两路 independent task 确认 candidate `6588e30b…f545b8` 的 exact projection、
  class/session/report/lifecycle/outer binding；confirmation owner 只负责 canonical exact 投影；
- Cfreeze owner 只能修改 threshold、contract、SHA256SUMS 三个 machine 文件与
  `docs/9.3.4/**`，并负责 direct-parent、formalization-delta、push 与 clean identity；
- formal owner 只有在 Cfreeze clean pushed 后才能启动一次 fresh formal；post-formal owner 仍须按
  final implementation quality -> coverage audit -> acceptance 接手；
- At that r19/Cfreeze checkpoint machine=`confirmed/formal-ready`，Step 4=`in-progress`。formal/audit/acceptance、Step 5 与
  9.3.5 owner 尚无开工授权。

## Superseding formal-r4 / post-formal ownership boundary（2026-07-18）

- Cfreeze/formal owner 已完成 `f97483a0…` direct-parent、push/clean、formalization delta 与
  fresh formal-r4；exit evidence/public final replay均 PASS；
- implementation-quality owner 已完成 post-formal gate，decision=
  `ready-for-coverage-audit / B/H/M/L 0/0/0/1`；唯一 Low 交 Step 5 single-authority owner
  拆分 live inventory 与 durable artifact replay；
- coverage-audit owner 当前获授权逐项映射 requirement、25 workitems、positive/negative/
  lifecycle/cleanup/public artifact。acceptance owner 仅在 audit=`ready-for-acceptance` 后接手；
- At that formal-r4 checkpoint Step 4=`in-progress`，`can_enter_coverage_audit=yes`、`can_enter_acceptance=no`；Step 5、
  Step 6/7 与 9.3.5 owner 未因本质量门提前开工。

## Superseding Step 4 accepted ownership handoff（2026-07-18）

- coverage-audit owner 已完成 25/25 workitem 与 requirement matrix，critical/major gap=`0/0`；
  Pivot legacy companion 经两路独立复核；
- acceptance owner 已签署 `accepted / blocking none`，并在 decision 后关闭全部 25 个 Step 4
  workitem；debt owner 继续持有 Unit MySQL classification migration；
- Step 4 owner 只保留 immutable evidence 维护责任，不得改写 formal-r4；
- Step 5 single-authority owner 现获得开工授权，负责 portable candidate、live/durable replay
  入口与 rehearsal；状态=`ready / not-started`；
- Step 6/7 owner 与 9.3.5 owner 仍无开工授权，9.3.4 version owner 尚不能签收版本。

## Superseding formal-r6 recovery ownership（2026-07-19）

- Step 4 authority owner 重新持有唯一 active gate：提交/push Cdiag 并完成 diagnostic→freeze→formal；
- failure-evidence owner 保持 r6 immutable，禁止任何 artifact repair；
- fixture owner 只维护合规 v2 NUL-token synthetic precondition，不能修改 real validator policy；
- Step 5–7 owners 的既有实现可保留但 execution authorization 撤回；9.3.5/9.4.0 owner 继续 queued；
- formal-r7 及后置三门全部 PASS 后才重新交接 downstream ownership。

## Superseding diagnostic-r23 remediation ownership（2026-07-19）

- evidence owner 负责永久记录 r23 为 `diagnostic-observed / public-valid / freeze refused`，保持
  candidate/capsule absent；不得把较高 covered counter 当成天然更可靠；
- coverage-delta owner 负责保存 XML/raw-exec 唯一归因：MapBeanInfo inner double-check probe 4，
  其余 22 exec 对目标类不变；
- `foggy-core` test owner 只维护 existing-node controlled monitor regression、5 秒有界等待和线程
  termination；不得改 production double-check 或增加测试节点；
- machine/Cdiag owner 负责形成一次 clean/pushed replacement Cdiag，再由 Step 4 coverage owner运行
  唯一 fresh diagnostic-r24；
- threshold owner 在 r24 public validation、counter/probe review 前无 freeze 权限；Step 5–7、9.3.5、
  9.4.0 owners 继续 queued/closed。

## Superseding r24 ownership handoff（2026-07-19）

- Step 4 coverage owner 已关闭 r24 all-lane/public-validation、counter/probe 与 cleanup responsibility；
- threshold reviewers 已关闭 candidate/critical/provenance/capsule 双审，combined findings=`0/0/0/0`；
- machine owner 已将 exact projection 封为 `confirmed/formal-ready` 并关闭 Step 4/6 hash cascade；
- Cfreeze owner 当前只负责 direct-single-parent commit/push/topology/clean proof；随后 formal owner
  必须在真正 fresh clone 完整运行 formal-r7；
- quality/audit/acceptance、Step 5–7、9.3.5/9.4.0 owners 在 formal-r7 PASS 前不得接管 execution。

## Superseding formal-r7 portability recovery ownership（2026-07-19）

- failure-evidence owner 保持 formal-r7 immutable/excluded，并维护可解码为 byte-exact raw Failsafe 的 Base64 + provenance capsule；
- catalog owner 只维护 exact repo-contained v1.5.1 bytes，不改 CALCULATE 业务语义或 test nodes；
- authority owner 维护 tracked blob/SHA preflight、raw/executable lifecycle seals 与 Step4→Step6 hash closure；
- diagnostic owner 仅在 clean/pushed Cdiag 和 isolated proof 后运行 fresh r25；
- quality/audit/acceptance、Step 5–7、9.3.5/9.4.0 owners 在 formal-r8 + post-gates PASS 前不得接管。

## Superseding Unit MySQL 7/12 remediation ownership（2026-07-19）

- failure-evidence owner 必须原样保留 r7 observed=`6 reports / 11 errors`；consumer-inventory owner
  另行维护 current known lower bound=`7 reports / 12 testcase nodes`，并纳入第 7 个
  `DatasetJdbcUtilsTest#getOrCreateDataSource / 1 node`。两个 owner 不得互相改写历史与当前字段；
- `foggy-dataset` test owner 负责移除该节点吞掉 `SQLException` 的假绿路径，使 datasource reuse、
  真实连接和 `SELECT 1` 结果均由 required assertions 约束；不得改变 `@Test` identity/cardinality、
  production datasource API 或把 fixture credentials 写死进测试；
- fixture-contract owner 负责把 consumer authority 升级到 lower-bound `7/12`，并把 negatives 扩为
  `42/42`；lifecycle owner 保持真实 probes=`5/5`，不得以 static/focused PASS 替代完整 Unit；
- Unit authority owner 仍须证明唯一 Maven invocation=`681+55/4941/F0E0S0` 及既有 identity、
  receipt、profile isolation、publisher、report inventory 与 cleanup contract；
- evidence owner 将 r25（HEAD=`5aaffbb4cd217d3d891c22eca4d3ae31d4e9d6e7`）永久登记为
  `full-chain public-valid / pre-remediation / superseded / non-candidate`，candidate/review/Cfreeze owner
  对其均无开工授权；
- machine/Cdiag owner 已关闭 `4fe86929…` push/clean、isolated r4 durable proof 和 fresh r26；
  candidate/capsule reviewers 已以 `0/0/0/0` 关闭 review，并只向 direct-child Cfreeze owner 交接；
- Cfreeze、fresh formal-r8 与 post-formal quality/coverage-audit/acceptance owners 尚未完成各自 gate。
  在这些 gate 完成前，Step 5–7、9.3.5/9.4.0 owners 继续 closed/queued；r25 永久保持
  `pre-remediation / superseded / non-candidate`；
- 9.3.4 version signoff 后，classification-debt migration owner=`9.3.5 Gate 0`，实现责任仍由
  `foggy-dataset` 承担，deadline=`9.3.5 version acceptance`。

## Superseding formal-r8 interpreter-portability recovery ownership（2026-07-19）

- failure-evidence owner 已封存 formal-r8 capsule、raw log identity、success-only absence、cleanup、
  sensitive 与 exact demo DB restore，并永久禁止 r8 resume/reuse/candidate promotion；
- runner owner 只负责三个 Python command positions 的显式 interpreter dispatch；Python tool owner
  保持 Git mode=`100644`，不得用 chmod 或主工作树 mode 作为发布前提；
- negative-contract owner 负责 runner raw/292-command-stream seal、四 tool target、七 top-level logical
  call、raw/stream/semantic mutation=`44/44 / 43/43 / 33/33`、Git-stage mode mutation=`4/4`、
  non-executable smoke=`4/4` 与 runner before/after hash；machine owner
  负责 diagnostic reset 和
  Step4→Step6 hash closure；
- independent code/docs quality owners 已关闭全部 finding，B/H/M/L=`0/0/0/0`、mandatory=`0`；当前
  唯一开放责任已移交 Cdiag owner，只可提交/push 一个原子 recovery checkpoint，并把 clean fresh
  clone 交给 diagnostic-r27 owner；
- candidate/reviewer/Cfreeze/formal-r9/final-quality/audit/acceptance owners 必须逐门串行接管，不得复用
  r26/r8 authority；Step 5–7、9.3.5/9.4.0 owners 继续 closed/queued。
