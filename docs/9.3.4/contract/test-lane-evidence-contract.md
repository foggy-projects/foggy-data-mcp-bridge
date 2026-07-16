---
doc_role: contract
doc_purpose: Freeze the 9.3.4 test inventory, runner, database, coverage and release evidence invariants.
version: 9.3.4
status: confirmed
created_at: 2026-07-14
updated_at: 2026-07-16
---

# Test Lane and Evidence Contract

## 文档作用

- doc_type: execution-contract
- intended_for: project-root-session / build and CI owners / reviewer
- purpose: 冻结经 Step 1 双路独立复核、可被 runner 独立断言的 schema 与不变量。

## 1. Source and Execution Inventory

`source-inventory.tsv` 每个 workspace discovery candidate source 一行：

```text
source_id | module | reactor_member | source_root | source_path | top_level_fqcn | kind | discovery_patterns | disposition | owner | reason
```

- `kind ∈ {executable,helper,generator}`。executable 的
  execution ownership 由下表表达；helper/generator 不得有 execution row，并必须有
  owner/reason，不能靠文件名收窄静默消失。
- `reactor_member=false` 的 workspace source 仍必须有 owner/reason/disposition；它不
  得被计入 root reactor required execution。Step 1 从 active root `<modules>` 重新
  推导成员，不能硬编码当前数量。

`execution-inventory.tsv` 在 Step 2 successor 中每个正向 report execution key 一行；
confirmed Step 1 的 829 行是不可变的 pre-amendment report identity baseline，其中
59 行随后被 typed 为 structural container，不回写或伪造 Step 1：

```text
execution_key | source_id | report_fqcn | runner | lane | variant_key | db_kind | infra_kind | execution_step | required | owner | optional_reason | review_at
```

`discovery-inventory.tsv` 每个 reactor source 的 JUnit discovery report owner 一行；
无 report 的 reviewed helper/generator 使用唯一 `report_fqcn=none` 行：

```text
module | source_id | source_fqcn | report_fqcn | discovered_test_nodes | runtime_deferred_containers | engine_ids | source_sha256 | test_classes_sha256 | main_classes_sha256
```

- `module/source_id/source_fqcn` 必须与 source inventory exact 关联；non-reactor source
  不得有 discovery row。report owner 只能是 top-level FQCN 或其 `$Nested`；实际 report
  使用固定 `junit-jupiter` engine，`none` 行的 node/deferred 均为 0、engine=`none`。
- source、当前 owning module `target/test-classes` 与 `target/classes` tree SHA 必须由
  validator 现场复算；orphan/duplicate/missing/tampered row 全部 fail closed。

Step 2 successor 另有 `structural-report-inventory.tsv`：

```text
module | source_id | source_fqcn | report_fqcn | runner | lane | variant_key | owner | discovered_test_nodes | runtime_deferred_containers | positive_sibling_execution_keys | disposition | rationale
```

- structural row 只能是 `report_fqcn=source_fqcn`、discovery/deferred=`0/0` 的
  top-level ClassSource container；必须有同 source/runner/variant 的非空正向
  `$Nested` sibling execution set，disposition=`reviewed-structural-container`。
- structural report 必须 fresh 且 suite identity exact，
  `tests/testcase/failure/error/skipped=0`；它不计入 execution/test totals。普通正向
  execution report 的 tests 必须大于 0，不能借 structural 规则放宽。
- successor positive execution 与 structural report 集不相交；二者并集与该
  generation 的实际 raw Maven XML report 集 exact。
- runtime manifest 的 `report_count` 只表示 positive execution reports；
  `structural_report_count` 单列 structural，`raw_report_count` 必须等于两者之和。
  lane `summary.env` 使用 `execution_reports/structural_reports/raw_reports`，不保留
  含混的单一 `reports` 字段。

`discovery-classpath.tsv` 冻结 discovery 实际使用的有序 effective classpath：

```text
module | ordinal | entry_identity | entry_sha256
```

- 每个 discovery module 的 ordinal 从 1 连续递增，module set 与 discovery inventory
  exact；entry identity 只允许 `m2:<repo-relative>` 或 `repo:<workspace-relative>`，文件/
  class tree SHA 必须现场复算。
- Maven 提供依赖顺序；属于 active reactor GAV 的本地仓 JAR 必须原位替换为本次
  reactor `target/classes`，不得冻结同版本陈旧 `.m2` JAR。raw/normalized cardinality、
  order、live SHA 任一不一致均失败；classifier 歧义不猜测。

`rename-successor-plan.tsv` 以 execution key 为粒度冻结 Step 2 的受控改名：

```text
rename_group | current_source_id | current_source_path | current_top_level_fqcn | current_report_fqcn | current_execution_key | target_source_id | target_source_path | target_top_level_fqcn | target_report_fqcn | target_execution_key | runner | lane | variant_key | db_kind | infra_kind | execution_step | required | owner | optional_reason | review_at | rationale | reviewer
```

- Step 1 baseline 保持 immutable `generation=step1-pre-rename`。当前 33 个真实
  `*IntegrationTest` 的 target 只能机械改为 `*IT`；nested suffix 原样保留，source ID
  和 length-framed key 必须重算；runner/lane/variant/DB/infra/step/required/optional/
  owner 全部不变，target path/FQCN/key 不得碰撞。
- plan exact 覆盖 33 sources、62 reports、74 pre-amendment identities 与 50 predecessor
  edges。Step 2 不覆盖本目录 baseline；在 `scripts/v934/successor/step2/` 生成 post-
  rename candidate，以 confirmed Step 1 summary 的 manifest SHA + rename-plan SHA
  作为 parent link，只允许 approved rename/POM delta 与 reviewed structural-container
  amendment，并经独立 review/confirm 后才成为 Step 2/3 exact-compare inventory。
  successor 分类必须保持 33 source、62 discovery report rename，其中 58 report/
  70 key 留在 positive execution，4 report 转为 structural；不得改写 parent SHA。

- `execution_key` 是 versioned stable key，精确编码为
  `v934|<byte-len>:<runner>|<byte-len>:<lane>|<byte-len>:<variant_key>|<byte-len>:<report_fqcn>`；
  length 按 UTF-8 byte 计算，全表唯一。空语义不使用空字符串：无 profile/provider
  变体写 `default`，非数据库 lane 的 `db_kind` 写 `none`。
- 每个 executable reactor source 至少一个 execution row；helper/generator 与
  non-reactor excluded source 为 0 rows。`runner ∈ {surefire,failsafe}` 且对每个
  execution key 恰好一个。
- JUnit `@Nested`/dynamic container 可让一个 source 对应多个
  `Outer$Nested` report FQCN；每个 expected report 单独一行，不把 top-level FQCN
  当成唯一报告。当前 65 个含 `@Nested` 的 source 只是 diagnostic，Step 1 必须以
  discovery-only JUnit test plan + POM variant config + available fresh diagnostic XML
  review/freeze exact mapping；不要求提前执行 Step 3 external fixtures。
- 数据库/provider/profile 重复使用 `variant_key`；五库 parity 同一 report FQCN 有
  五个 db-kind execution rows。相同
  `(report_fqcn, db_kind, lane, variant_key)` 不得重复；同一 DB/lane 只有在 provider/
  profile 语义确实不同且有 review 记录时才允许多个 variant。
- `sqlite-broad-integration` 与 `database-contract-matrix[sqlite]` 使用两个显式、
  互斥 execution-key 子集：前者承担广覆盖 integration，后者只承担五库同构
  preflight/parity/capability contract；同一 `(report_fqcn, sqlite)` overlap 必须为 0。
- actual raw XML report identities 与当前 Step/variant 的 expected positive execution
  subset 加 structural subset 做双向差集：orphan=0、unexpected=0、runner overlap=0。
  positive/structural metrics 必须分开，不能把 source 行直接与 report FQCN 集比较。
- Step 1 discovery 取当前 Maven 默认/显式 pattern 的并集，包括 `Test*`、`*Test`、
  `*Tests`、`*TestCase`、`IT*`、`*IT`、`*ITCase`、`*E2E`、`*E2ETest`；prefix-only
  `Test*`/`IT*` 也必须逐项判定 executable 或 helper，不能因 final include 收窄而
  静默消失。
- active reactor 使用根 POM default-active module graph 推导，XML comments 不计
  module；Step 1 不激活 release/coverage/multi-db 等额外 profile。source path 使用
  repo-relative POSIX real path，只扫描 versioned `src/test/java`；generated-test root
  只有被 active Maven build 显式注册且有独立 disposition 时才进入。
- discovery authority 是与当前 source SHA 及 `target/test-classes` hash 绑定的 JUnit
  Platform discovery-only plan；fresh diagnostic XML 仅用于交叉验证 report FQCN，
  不能覆盖 discovery 结果。动态测试在 discovery 阶段只冻结 owning report FQCN，
  testcase cardinality 留到 owning execution lane；无法静态发现的 engine/provider
  必须有 versioned override、理由和 reviewer，不能从旧 XML 猜测。
- optional 必须有业务原因、owner 和复核时点；“环境可能不可用”不是 release
  optional 理由。`required=true` 时 `optional_reason/review_at` 固定写 `none`；
  `required=false` 时两列均非空且 `review_at` 使用 `YYYY-MM-DD`。
- 9.3.4 最终不保留 `*IntegrationTest` 作为永久双义命名：真 integration 改
  `*IT`，纯 unit 改 `*Test`。

## 2. Runner Ownership

- final Surefire includes: `*Test`, `*Tests`, `*TestCase`；excludes all
  `IT*`, `*IT`, `*ITCase`, `*E2E`, `*E2ETest`。prefix-only `Test*` 若为 executable test，先改成后缀
  命名；若为 helper，写入 frozen non-executable inventory + owner/reason 后排除。
- Failsafe includes: `IT*`, `*IT`, `*ITCase`, `*E2E`, `*E2ETest`；显式绑定
  `integration-test` and `verify`。
- root pin plugin versions/config；module 只补 owning configuration，不创建互相
  冲突的第二套默认。
- `failIfNoTests`/`failIfNoSpecifiedTests` 在 owning lane fail closed；wrapper 可为
  `-am` helper 放宽 Maven 层，但随后必须独立断言 owning fresh report/FQCN/count。
- reports 必须晚于 run marker，且 testcase node count 等于 suite tests 总和；positive
  suite tests 必须大于 0，reviewed structural suite 必须严格等于 0。
- Step 2 actual exit 由 all unit + hermetic IT 组成；`infra_kind` 为 DB/Redis/other
  external 的 required suite 只能以 reviewed exact manifest defer 到 Step 3，不能
  标 pass。Step 2 对 confirmed Step 2 successor positive inventory 的
  `execution_step=2` subset 做 exact compare，并同时 exact 校验同 variant 的 structural
  raw reports；Step 3 对同一 successor positive inventory 的 `execution_step=3`
  subset 做 exact compare。两者 execution-key 并集必须等于该 generation 全部 required
  positive execution inventory 且交集为空。

## 3. Skip Contract

Skip manifest record：

```text
lane | fqcn#method | reason | owner | expiry | required
```

- required release lane target=`0`；unexpected `<skipped>` 或 assumption 立即失败。
- unsupported DB capability 用 expected refusal assertion 作为通过用例。
- generator/snapshot-only class 不属于 release execution inventory；不得用 skip
  隐藏归属错误。

## 4. Required Database Contract

固定 DB kinds：`sqlite`, `mysql57`, `mysql8`, `postgres15`,
`sqlserver2022`。

每 lane 必须保留：

- JDBC product + exact/approved version、driver-aware physical coordinate、
  catalog/schema；SQL Server 分号 URL 不用通用冒号 parser 猜测。
- image reference + digest（SQLite 为 JDBC artifact/version/hash）。
- 同构 sentinel fixture manifest/hash + before/after；完整 demo 总行数不要求跨库
  相同。
- required preflight、QueryFacade/native parity、DB-specific positive/refusal
  capability suite 的 fresh XML/testcase inventory。
- SQL Server native oracle 使用其方言的 `TOP`/`OFFSET FETCH`，不复用
  `LIMIT`；初始化由可复跑 fixture automation 持有，不能只靠手工 shell 状态。

任一 DB unavailable、wrong product/version/host/port/catalog/schema/sentinel、
fixture mutation 或 report missing 都失败。

## 5. Coverage Contract

- JaCoCo version centrally pinned (`0.8.12` candidate, Step 1 review freeze)。
- Step 1 policy `scripts/v934/coverage-thresholds.json` 与其 `SHA256SUMS` 保持不可变；
  Step 4 在 `scripts/v934/step4/coverage-thresholds.json` 建 parent-linked successor，
  绑定 parent policy/hash manifest、current commit/source/classes、lane ledger 与 reviewer。
- Step 3 correctness run 不承诺 exec；Step 4 接好 agent 后必须重新执行 unit、
  6 个 hermetic/SQLite IT variants、five-DB 7 variants、7 个 required external variants
  和 Addon companion 2 variants；optional LLM 保持 reviewed/excluded。只有 Step 4 run
  的 23 个唯一 exec 可进 coverage。
- Step 4 exit 后，canonical unit/integration/DB runner 默认在各自唯一执行中产 run-owned
  exec；Step 5–7 coverage stage 只收集/校验这些 exec，不得再次执行同一 suite。
- UT 写独立 `jacoco-ut.exec`；每个 integration/DB lane 写唯一
  `jacoco-it-<lane>.exec`；不得并发覆盖同一文件。
- exec manifest 记录 commit、module/classes hash、lane、session/tool version；merge
  前逐项核对。
- build-only aggregate module只产 XML/HTML，不含生产源码、不进 Launcher；不得对
  该空 reporter 使用标准 `jacoco:check` 冒充 aggregate gate。
- versioned coverage verifier 解析 aggregate XML，精确检查 expected module/package/
  class presence、counter totals、reviewed aggregate thresholds 和 critical-class
  thresholds；missing/duplicate/zero/unexpected XML/counter 都非零退出。
- 保留 model 既有 LINE `0.77` / BRANCH `0.62` 门，并改为对其 UT+IT merged exec
  执行 owning-module `jacoco:check`；同时保留 inherited
  `SemanticScaleSqlSupport` LINE/BRANCH=`1.00/1.00` 单类门，不得因 profile 重构静默
  删除。全 reactor 首次只生成 diagnostic candidate baseline，人工 review 后冻结。
- critical class candidate floor 为 LINE `0.80` / BRANCH `0.70`；最终值取
  `max(reviewed observed value, candidate floor)`，不足先补测试，任何例外需显式
  workitem/approval，runner 不自动下调。
- missing/empty exec、class/source SHA mismatch、threshold regression、未授权
  exclusion/threshold change全部失败。

Step 4 run root 固定为 `target/v934-step4-coverage/runs/<run-id>/`；exec 文件位于
`exec/`，名称集合由 successor ledger exact 冻结（UT=`jacoco-ut.exec`，其余为
`jacoco-it-<variant>.exec`），`exec-manifest.json` 记录 file SHA/size/session/tool、
commit/source/classes/lane identity。aggregate XML/HTML 只能写入同一 run 的 `report/`；
docs evidence 固定写入
`docs/9.3.4/evidence/step-4/step4-coverage-exit-<date>.md`。同名覆盖、跨 run 拼接或
从 Step 2/3 target 借用 exec 都失败。

Step 4 在 compile、lane 子运行、report 与最终 summary 边界必须复验
run-owned toolchain receipt。receipt 绑定 Step 1 raw 工具版本、实际 Maven/JDK
链路、compiler realm ASM `9.6`、JaCoCo realm ASM `9.7`、test classpath ASM
`9.7.1` 和 24 个 production module effective compiler；任一字段缺失、非 canonical
JSON、跨 run 替换、版本/realm/hash 漂移均必须 fail closed。本地出版的
`scripts/v934/step4/SHA256SUMS` 已生成并通过 exact 48 项校验，
manifest SHA-256=
`c8ae4f1015760ee831935c6c34fa0b83c9e8954065b909bbb80ab2b4a67d2417`。

Historical `scripts/verify-v934-step2-successor.sh` 继续冻结 Step 2 的 24 个
production reactor generation 语义。Step 4 加入第 25 个 build-only reporter 后，
不得直接重跑该 historical runner 来改写 Step 2 authority；当前正式链路是
immutable Step 2 parent + `step2_report_view_tool.py` derived view + Step 4 overlay。
该 historical runner 在当前 25-module root 上 fail closed 是预期 supersession
boundary，不是应放宽为 25 的待修契约。

初始 critical set：CatalogSnapshotStore、ModelBuildSingleFlight、
CatalogRefreshCoordinator、DatasourceCatalogConvergence、NamespaceScope、
CommittedSourceRevisionRegistry、RuntimeNamedDataSourceResolver、
WatchServiceFileTracer、QueryFingerprintBuilder、SecurityPolicyFingerprint、
CaffeineQueryCacheProvider、RedisQueryCacheProvider。Step 1 可经 review 增补，不得
静默删除。

## 6. Predecessor Regression Migration

- 9.3.1–9.3.3 historical runs、raw XML、FQCN/count 和 v933 runner 均 read-only。
- Step 1 先把 sealed raw XML 规范化到 `predecessor-node-inventory.tsv`：

  ```text
  predecessor_node | criterion | historical_lane | variant_key | report_fqcn | raw_report_sha256 | authority_run_id
  ```

  authority run 的 raw XML exact set、suite FQCN、SHA 与 node 必须双向一致；validator
  从 sealed run 重新生成 nodes，不能只相信 TSV 自述。
- Step 1 冻结 migration edges：

  ```text
  mapping_group | relation | declared_old_count | declared_successor_count | criterion | predecessor_node | successor_execution_key | disposition | rationale | owner | reviewer
  ```

  Step 2 successor 为 structural amendment 增加 typed
  `successor_structural_report_fqcn`（紧随 `successor_execution_key`）；两列严格 XOR。
  positive 引用必须存在于同 generation execution inventory；structural 引用必须存在
  于 structural inventory 且 disposition=`structural-container-successor`。confirmed
  Step 1 原表不回写。`relation ∈ {1:1,1:N,N:1}`；1:1 是默认。每个 predecessor node
  恰属一个 group。一个 execution
  key 可支撑多个不同 criterion group，但同 group 内 old/successor distinct node
  cardinality 必须等于 declared values，edge tuple duplicate=0；9.3.4 新增 key 可不
  进入 edge 表。split/merge 必须写 criterion-preservation rationale/reviewer。
  测试/报告总数永远从去重 execution/testcase ledger 计算，禁止按 migration edges
  求和放大证据。
- migration 同时覆盖 package invariants：新增 POM-only coverage reporter 可改变
  reactor module count，但不得增加 production main JAR、Launcher nested JAR 或
  auto-configuration surface；新 expected count/hash set 必须单独 review/freeze，不能
  继续硬套 v933 的 25-module常量。
- package/reactor/JAR/Launcher/auto-configuration successor delta 单独写入
  `scripts/v934/package-successor-inventory.tsv`，不塞入 execution edge 表。
- 9.3.4 只运行 current-source successor regression；不得把旧 v933 runner 因新命名
  失败解释为产品回归，也不得修改旧 runner 令其“重新通过”。

## 7. Authority and Evidence Contract

建议 authority root：

```text
target/v934-release-evidence/runs/<commit-sha>-<workflow-run-id>-<attempt>/
```

Step 5 rehearsal 使用独立 candidate root：

```text
target/v934-release-evidence/candidates/<source-state>-<run-id>/
```

必须包含 environment/source manifest、inventory、lane summaries、raw
Surefire/Failsafe XML/testcases、skip manifest、五库 identity/fixture、JaCoCo
exec/report/check、9.3.1–9.3.3 successor regression/migration manifest、已测
JAR/Launcher nested checksum、Docker image digest + embedded-JAR checksum、
summary、inner/outer `SHA256SUMS`、archive digest。

- authority 起点/终点必须是同一 clean commit；source/worktree、fixed DB fixture、
  reports 和 packaged artifacts 在运行中不可漂移。
- failed run 永不更新 authority pointer；diagnostic/superseded 明确排除。
- Step 5 candidate 无论 dirty/clean 都不得更新 final authority pointer；只允许独立
  candidate pointer。Step 7 才能写 final authority pointer。
- evidence archive 上传后必须下载并重验 digest；artifact missing=`error`。
- archive/artifact 不含 env dump、password、token、credential JDBC URL 或敏感日志。

## 8. CI and Release Contract

- reusable/required jobs：inventory+unit、SQLite broad integration、五库 contract
  matrix、coverage collector/check、package/evidence、always-run aggregator；coverage
  job 下载 upstream exec，不重复执行测试。
- stable aggregator 显式检查每个 required result；`failure`, `skipped`,
  `cancelled` 都失败，只有全部 `success` 通过。
- five-DB cells 各自产 lane artifact；collector 校验 db-kind exact set、cardinality
  `5`、SHA/run/attempt、manifest/XML freshness 后才产 matrix summary。仅
  `needs.<matrix>.result=success` 不足以过门。
- PR/main 分支规则实际要求 stable aggregator；仅 YAML 存在不算证据。
- release 必须 `needs` 同一 full gate，校验 tag SHA，下载已测 JAR/evidence；禁止
  `--skip-external-db`, `-DskipTests`, `skipITs` 后重新构建发布物。
- release Docker build 使用 runtime-only Dockerfile 直接 COPY 下载的已测 JAR；
  push 前从 image 回读 `/app/app.jar` SHA 并与 authority manifest exact compare。
- JAR、evidence archive 与 digest 一同作为 release asset；CI artifact 名含
  SHA/run/attempt，不覆盖。

## Contract Freeze Rule

Step 1 结束时把 `status` 改为 `confirmed`，记录 inventory/hash、reviewer 和
decision。未 confirmed 前只允许 diagnostic inventory，不进入 POM/rename 改造。

Step 1 的机器权威还包括：

- `scripts/v934/discovery-inventory.tsv` 与 `discovery-classpath.tsv`：冻结 report owner、
  source/class tree hash 和 current-reactor effective classpath；
- `scripts/v934/rename-successor-plan.tsv`：冻结 Step 1 pre-rename→Step 2 post-rename
  的 exact source/report/execution/predecessor delta 与 successor confirmation chain；
- `scripts/v934/predecessor-node-inventory.tsv`：冻结 sealed predecessor raw XML nodes；
- `scripts/v934/maven-variant-inventory.tsv`：schema 为
  `module | profile | activation | plugin | execution_id | current_owner | current_variant | v934_disposition | owner`；
  exact 覆盖 root default、model `multi-db`/`model-lifecycle` 与 cache
  `query-cache-real-query` 的当前 owner→successor disposition；
- `scripts/v934/database-contract.tsv`：冻结五库 expected identity/sentinel/schema；
  Step 3 才写 observed evidence；
- `scripts/v934/package-successor-inventory.tsv`：冻结 build-only reporter 的 expected
  delta 和 production surface 不变量；
- `scripts/v934/contract-freeze.json`：冻结 baseline、profile/path/discovery policy、
  skip schema、stable check name、evidence layout、reviewer 与 decision；
- `scripts/v934/SHA256SUMS`：精确覆盖 generator、inventory、migration、DB/package/
  coverage policy、negative probe 结果与 freeze record；validator 必须拒绝缺行、增行、
  重复、乱序或内容漂移。`SHA256SUMS` 自身不入表，因此不存在自引用。

freeze/summary 还必须绑定 wrapper、Python inventory tool、override、Java discovery
source、实际编译出的全部 helper class（含 inner class）tree SHA，以及 Java/Javac/Maven
版本；summary 在 candidate→confirmed 后必须原子刷新 freeze/manifest digest、reviewer、
reviewed_at、decision 和 evidence status，candidate digest 不得冒充 confirmed evidence。

`scripts/v934/coverage-thresholds.json` 在 Step 1 只冻结 tool version、critical FQCN、
既有 model 门和 candidate floor；该文件不可更新。aggregate observed baseline/最终
reviewed threshold 只能在 Step 4 由实际全 lane exec 产出，并写入 parent-linked
`scripts/v934/step4/coverage-thresholds.json`。

同一 exec 只允许默认串行 Maven reactor/fork 写入：禁止 `-T`、`forkCount>1` 或并行
fork 共享 destFile。agent sessionId 必须包含 run/lane/variant/module；显式 child JVM
也必须有独立 session。`RedisCrossJvmCacheIT` 的 writer/reader 子 JVM 必须显式附加
同版本 agent，顺序 append 到 `redis7` exec，不能只覆盖 owning Surefire/Failsafe JVM。

状态词固定为：contract `proposed -> confirmed`；Step
`ready -> in-progress -> passed|blocked`；test plan 在 Step 1 只记录
`step1_result=passed`，全版本 `result` 在 Step 7 前仍为 `not-run|in-progress`。

Confirmed record（2026-07-14）：run=`step1-candidate-r8-20260714`，decision=`passed`，
reviewer=`dual-independent-review:precommit_scope_audit+v934_step1_contract`；freeze=
`ff418e04f6a938a853ce7bbd0700223627f42520705530e819a53e5591e82876`，manifest=
`e601c6c70ff02e9e50b86fd2b14b14aba9cfede096b42c93ff6e5968a918640f`，summary=
`579e9430bea6f873e7c4465cd1a6e45c49d348d84a89d5d648d25e3a5a4bbc50`。证据：
`docs/9.3.4/evidence/step-1/inventory-contract-freeze-20260714.md`。

Step 2 confirmed record（2026-07-15）：run=`step2-candidate-r8e-20260715`，
decision=`passed`，reviewer=
`dual-independent-review:v934_r8e_identity_review+v934_snapshot_skip`；freeze=
`44b11ed756bf41e3b271ac57b59c2c882a0b31a56963f42ae154fdb5d37b2fb6`，manifest=
`4259a452bf4282f85ebb8bfe092127ec3ebec95652e7c009792081f86b84b919`，summary=
`f6b80aa5f48c6f32aaa99336823dd00d183d75a096767c74f7de2c21c1ac4b75`。current counts=
`770 positive = 724 Step 2 + 46 Step 3 deferred`、`59 structural`、`519 predecessor`。
Step 2 actual runner evidence 为 `5,205 testcase / F0/E0/S0`，并通过
INT/TERM/HUP=`130/143/129` durable fail-closed probe。证据：
`docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`。

## Step 3 Confirmed Exit Record

Confirmed result（2026-07-16）：tested commit=
`ce3d70c391c7b8bd8046fe66dde0ad568d66601e`，parent run=
`step3-required-20260716-final-r4`，contract SHA=
`f2bd52df7ed2829051ad263f97d560d3f8babe048d25864edb725eb671ba4d1b`。
Database child=`29 reports / 370 testcase / F0E0S0`，required external child=
`16/76/F0E0S0`；两者 execution-key exact union=`45/446`，
gap/overlap/extra=`0/0/0`。DB state=`18/18`、Redis state=`4/4`，source/fixture
before=after，resource residue=`0/0/0`。

PreAgg Addon lifecycle=`2 reports / 6 testcase / F0E0S0` 是 required companion，
`included_in_required_totals=false`，不得加到 45/446。Optional LLM=
`reviewed-optional-excluded`，未执行、未计为 passed，next review=`2026-08-31`。
Parent final/candidate SHA=
`9040bff263101ed7ad33dbc4681bbf37f48e39b00957d2bf8224fb506afa5282` /
`6b1e5f3502dd2e666e5546ecf3c6469e9b5060bdafa8e7d4e1068fd41688cfa4`。

本 record 只确认 Step 3 correctness。它没有 JaCoCo exec；Step 4 必须用带 agent 的
canonical runners 重新执行 unit、hermetic/SQLite、五库与全部 required external lanes。
证据：`docs/9.3.4/evidence/step-3/step3-required-matrix-exit-20260716.md`。

## Step 4 Diagnostic-ready Record

Superseding status（2026-07-16）：Step 4=`in-progress / diagnostic-ready`，不是
`passed`。静态契约已收口为 exact `23 exec / 48 sessions`；required report
overlay=`773 positive + 59 structural / 5,707 testcase / F0E0S0`，Addon companion=
`2/6` 仍与 required 总计分离。raw contract/effective POM/toolchain/report inventory
负例分别精确为 `8/8`、`4/4`、`5/5`、`27/27`。

该状态只表示允许在本基线完成提交、推送并验证 clean HEAD 后启动
fresh all-lane diagnostic。`coverage-thresholds.json` 仍为
`diagnostic-pending`；尚无 aggregate baseline/review、confirmed threshold 或
`docs/9.3.4/evidence/step-4/step4-coverage-exit-<date>.md`。因此 Step 4 exit 未满足，
Step 5 必须保持关闭。
