---
doc_role: module-responsibility
doc_purpose: Define ownership and dependency boundaries for the 9.3.4 test and CI evidence-chain delivery.
version: 9.3.4
status: ready
created_at: 2026-07-14
updated_at: 2026-07-16
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
| `foggy-dataset` classification-debt and datasource-adapter owner | find, migrate and reclassify every real DB consumer represented by or discovered beyond the r7 known set；scope fixture credentials only to its test resource | fixture contract + `V934_UNIT_MYSQL57_URL/USERNAME/PASSWORD` placeholders + `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md` | known 6 execution keys / 11 testcase nodes, profile-boundary isolation, discovery expansion, debt closure evidence | global Spring datasource/profile override, treating 6/11 as exhaustive proof or carrying the debt through 9.3.5 acceptance | 4 / 9.3.5 acceptance |
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
- 当前机器契约静态验证=`20/20`，Unit fixture negatives=`36/36`（原
  fixture/manifest probes=`20/20`、connection typed=`7/7`、atomic publisher=`3/3`、
  profile boundary=`6/6`），negative receipt 文件 schema/tamper 另为 `4/4`，真实
  lifecycle=`5/5`，report inventory=`30/30`。
- 当前 identity：top=`60/60` /
  `6056a930a1d0deec59767ffc0239485ae42b4067e343c0d68e3f899c3440e587`；
  successor=`14/14` /
  `acb580e92a72eb407f31f5d6f9a8139a3509f3a0bfbf58537922465f4086a112`；
  coverage diagnostic/formal=
  `c062219a6335ae41330c6d5924d6fce60941c5d168b361081fbb41df77428477` /
  `341991d6b5a15d19cdb9e0de70a8cc6ace29480227596c413c07e6bf7fdbc73d`；
  fixture contract/tool/Unit runner/datasource adapter=
  `7aa1e21aef85b51a13aacc8c134a1c363c595deffbfb3acf6aafdb942519b53a` /
  `cc19390ce6c0cfb307b7632dbe4e25540b1e4d49d11ec1512739f6724646d345` /
  `45536c0a969731f6b7c87acecdb225b13a8a0fca45a9a04c9cdfb2173fc60c66` /
  `9500cd4d50930b121a36798857cd0a1cc0c8b2190b0a2fc9ad0ea464394bb256`；
  declared amendments=`18` /
  `8e21b8527f290061361ef0b8fbf084d51b2536ef479e1f70e45488f996090bfc`；
  overlay contract/tool=
  `84d09bfc333bb40d8ef830979734933717555845cebe9943f70ff7087a9a482d` /
  `1fea2816504519b7e7f1dc6839744ee943a9a4bf3feb783375e21e935da63d31`。
- fresh r3 Unit、remediation quality、commit/push 与 fresh r8 diagnostic 均 pending；
  Step 5、formal、coverage audit、acceptance 仍关闭。上述 readiness 不代表实现质量已经
  通过，也不是 Step 4 exit evidence。
