---
doc_role: contract
doc_purpose: Freeze the 9.3.4 test inventory, runner, database, coverage and release evidence invariants.
version: 9.3.4
status: confirmed
created_at: 2026-07-14
updated_at: 2026-07-20
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

### 2.1 9.3.4-only Unit 全 lane replacement 例外

DB/Redis/other external required suite → Step 3 是默认归属，不因本节改变。r7 在停止
ambient MySQL 后发现 Step 2 Unit 历史分类与真实依赖不一致，因此只为 9.3.4 Step 4
建立一次临时 replacement exception。它不改写 Step 2 confirmed inventory，也不允许把
新的 DB suite 留在 Step 2。

机器权威为 `scripts/v934/step4/unit-mysql57-fixture-contract.json`。该契约绑定 Step 2
execution/discovery inventory、分类债务 workitem、完整 frozen Unit lane 与以下 r7 已知清单：

```text
v934|8:surefire|4:unit|4:unit|50:com.foggyframework.dataset.db.dialect.FDialectTest | 2
v934|8:surefire|4:unit|4:unit|54:com.foggyframework.dataset.db.utils.JdbcTableUtilsTest | 4
v934|8:surefire|4:unit|4:unit|55:com.foggyframework.dataset.db.fsscript.SyncSqlTableTest | 1
v934|8:surefire|4:unit|4:unit|55:com.foggyframework.dataset.db.table.dll.JdbcUpdaterTest | 2
v934|8:surefire|4:unit|4:unit|60:com.foggyframework.dataset.db.data.dll.SqlTableRowEditorTest | 1
v934|8:surefire|4:unit|4:unit|63:com.foggyframework.dataset.table.curd.BugFixInsertUpdateMapTest | 1
```

这 6 个 execution key / 11 个 testcase node 是已确认的隐藏依赖清单，不是对其余
Unit suite “绝无数据库访问”的穷尽证明。run-owned credential 与 fixture 对完整 Unit Maven
invocation 可见，因此 exception 的实际 authority scope 是整个 frozen Unit lane：
`681 positive + 55 structural / 4,941 testcase`、一个 Maven invocation、一个
`jacoco-ut.exec`，全局仍为 `23 exec / 48 sessions`。发现新的隐藏 consumer 时必须先更新
机器契约和债务清单，并重新通过 fresh Step 4 formal、质量闸门和测试证据覆盖审计；不能把
新 consumer 静默解释为已获授权。

该 Maven invocation 不得以全局 `spring.datasource.*`、`spring.test.*` 或 active-profile
参数覆盖其他模块的 profile datasource。run-owned MySQL 只允许通过
`foggy-dataset/src/test/resources/application.yml` 中
`V934_UNIT_MYSQL57_URL/USERNAME/PASSWORD` 三个 placeholder 进入 r7 已知默认/隐藏
consumer；`foggy-dataset-model` 的 SQLite 等其他 profile 必须保持各自 URL、driver 与
初始化脚本。outer/callback 必须拒绝 underscore/dotted/hyphen 的 Spring/custom key，以及
`@argfile`、`VMOptionsFile`、`javaagent/agentlib/agentpath` 间接注入。adapter consumer
inventory 必须由 scrubbed Git environment 枚举 `HEAD` tree，并设置 no-replace object。
connection receipt 的 authority scope 是 closed Unit Maven observation window：root 先配置
`init_connect`，再运行 Maven，Maven 返回后由同一 root batch 先 disable `init_connect`、
再按 `connection_id` SELECT。receipt 必须保存有序 `connection_id + observed user`，并证明
该窗口内所有 non-super MySQL connections exclusively 使用 restricted run-owned
credential；callback 返回后的 provisioner `foggy` 控制面连接位于窗口外。superuser 只可
用于 fixture 管理、窗口开闭与 receipt 读取。

Step 2 parent 的 execution identity/cardinality 只保留结构与 migration provenance；其
Unit 绿色在 r7 后不得作为 correctness evidence 复用。9.3.4 Unit correctness 由 fresh
Step 4 全 lane replacement evidence 完整替代，不能用 focused、partial、failed 或跨 run
证据补齐。临时例外与关闭标准由
`docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md` 持有：9.3.4
只有在 fresh replacement、fresh formal、实现质量闸门、测试证据覆盖审计和版本验收全部
通过后才允许带债务签收；债务必须在 9.3.5 版本验收前通过迁移真实 DB consumer 或恢复
可证明的 `none/hermetic/step=2` 分类而关闭。

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
- 9.3.4 Step 4 的 Unit exec 必须按 `unit-mysql57-fixture-contract.json` 执行完整 Unit
  lane replacement：pinned/run-owned MySQL 5.7、restricted non-super exclusive connection
  receipt、profile-scoped datasource adapter、fixture before/after、真实 lifecycle 与
  fallback cleanup 均为同一 run 的 required evidence；
  ambient listener、旧 Step 2 Unit XML 或只重跑 6 个已知 suite 均不能替代该 authority。
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
`scripts/v934/step4/SHA256SUMS` 的 r2/r3 historical identity 已通过 exact 51 项
校验，manifest SHA-256=
`348ade918a5020b9b65b9fb93e4bb7034e73f197c8545c7cbbfeb3d34d044ac1`；successor
manifest exact=`12`，SHA-256=
`6ac8a24dd983c1929f6d21430f57adca503893e69b368b37a08731f5a5355948`。Cdiag 工具增量
必须完成 fresh identity/manifest 级联后才可运行 r4，不得延用该历史哈希。

Unit/Integration 子 runner 必须显式拥有 run logger：只允许 managed FIFO
logger、保存 logger PID、关闭全部写端并 bounded wait。logger flush/reap 成功之前
不得发布 child `completed`/summary；slow logger、logger nonzero/timeout、持有 pipe 的
descendant 和真实 process-group residue 均必须由
`run_log_lifecycle_negative_test.sh` 拒绝，不得以 fixed sleep 或降低 residue 检查强度
规避。

outer 只能消费 exact typed child ready/completion receipt。ready identity 至少绑定
run/child/PID/PGID/SID/starttime/boot-id/status，并按 mode、link count、inode、hash 和
`O_NOFOLLOW` 式读取复验；信号与 cleanup 操作必须核对 starttime/boot-id 身份，
不得仅对可重用的裸 PID 操作。发现残留时，必须在 kill 前持久化 bytes-safe
PID/PPID/PGID/SID/stat/starttime/comm/cmdline snapshot。

Step 4 diagnostic/formal 发布是双态 fail-closed state machine：

- diagnostic 只能在 `diagnostic-pending` threshold 上生成 observation，不产生
  formal gate/candidate/final；formal 只能消费 confirmed threshold；
- gate/candidate/final/status 必须是 run id 推导的 canonical run-root path，
  `coverage_xml_tool.py` 对 child lifecycle、formalization delta 与该路径执行 typed
  重算，`coverage_xml_negative_tool.py` 负责 pending/confirmed-safe fail-closed 负例；
- 成功 `run-status.env` 只允许在 summary 与所有该 mode 必需证据已经验证
  后作为最后一次不可逆原子发布；不得先发布绿色 status 再 post-verify；
- formal 必须从 confirmed threshold 的 diagnostic commit/run id 定位真实历史 run，
  从该 commit 读取当时 threshold/contract blob，对 canonical diagnostic run 重算
  aggregate、reviewed 与 critical evidence；仅结构正确的 synthetic run 不得通过；
- threshold freeze commit `Cfreeze` 必须是 diagnostic commit 的唯一直接单父
  child，只允许 reviewed threshold/contract/manifest 与文档 allowlist delta；merge、多提交、
  shallow repository、replace refs 或 grafts 均必须 fail closed。

Step 4 report successor 保持执行库存与报告库存分离：
`coverage-report-amendment.tsv` exact=`12 rows = 4 new + 8 changed`，SHA-256=
`998ae49927721576c26327b8477010b0238843565e6afdbc70987e97544a028c`；
successor declared amendments=`18`，SHA-256=
`8e21b8527f290061361ef0b8fbf084d51b2536ef479e1f70e45488f996090bfc`。L2 fixture
作为 Integration report source amendment；Pivot fixture 作为 database source successor
amendment。两者均不改变 report identity/testcase，
因此 required overlay 仍为 `773 positive + 59 structural / 5,707 testcase / F0E0S0`，
exec/session 仍为 `23/48`。Step 2 derived view negatives=`12/12`，successor overlay
negatives=`12/12`；后者包含 Redis 显式路径错绑与 Git 环境隔离探针。

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

## Step 4 Diagnostic / Fix Record

Pre-r4 status（2026-07-16，historical）：Step 4=
`in-progress / r3 historical fail-closed / pre-r4 quality passed / identity refreshed /
fresh r4 pending`，不是
`passed`。静态契约已收口为 exact `23 exec / 48 sessions`；required report
overlay=`773 positive + 59 structural / 5,707 testcase / F0E0S0`，Addon companion=
`2/6` 仍与 required 总计分离。contract/source-Git=`20/20 + 7/7`，
effective POM/toolchain/report inventory=`4/4 + 5/5 + 27/27`；Step 2 derived view/
successor overlay=`12/12 + 12/12`，XML=`63/63`，logger=`9 类 / 14 case`。

clean/pushed HEAD `bc100b0f63bd3ff62d1105611dae41741790aedd` 的
`step4-coverage-20260716-diagnostic-r1` 在 `child-unit` 以
`3115 tests / 1 failure / 0 errors / 0 skipped` fail closed，未进入 reporter/model/
aggregate/threshold。根因不是生产 Matcher：测试腐化 daily，但 watermark=null 时 daily
正确 fail closed，实际 SQL 命中 monthly；同时 raw-vs-raw 与 nullable/empty assertion
允许伪绿。

修复契约要求 monthly 腐化/恢复精确一行且先核对 hit/name；三个 snapshot 比较显式关闭
hybrid，必须依次命中 `daily_product_sales`、`daily_product_sales`、
`daily_customer_channel_sales`；空/缺失/null/非数值 fixture fail closed。focused class=
`9/F0E0S0`，四类组合=`57/F0E0S0`，monthly corruption diff=`1000.00`，source
SHA-256=`affb6e415c1770eae7c9ab6f3505ac67acfe03eac1461bd2a48addf3ee790623`；canonical
workitem=`docs/9.3.4/workitems/BUG-step4-preagg-validation-wrong-table.md`。

`coverage-thresholds.json` 仍为 `diagnostic-pending`；r1 不得拼接为绿色，且尚无
aggregate baseline/review、confirmed threshold 或
`docs/9.3.4/evidence/step-4/step4-coverage-exit-<date>.md`。因此 Step 4 exit 未满足，
上述 r1 段作为历史失败记录保留。

r2 从 clean/pushed HEAD `0101a44a07784bf6b484d490c7fb508727fbab70` 启动：Unit=
`681 execution + 55 structural / 4,941 testcase / F0E0S0`；Integration 已执行
`312/F1E0S0`，唯一失败为 `PreAggregationL2CacheIT`，outer 在
`child-integration` fail closed，summary absent，未进入 database/external/Addon/
aggregate/threshold。immutable failed record=
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r2-fail-closed-20260716.md`，明确
`excluded-from-step4-exit`。

L2 修复只为 snapshot fixture 显式设置 hybrid=false，并强制 exact preAgg name/table、raw
negative 与 post-rewrite L2 identity；focused=`1/F0E0S0`、组合=`30/F0E0S0`，source
SHA-256=`bb5d6884401579447382587861e197feac2884ab701065d7826fe7a676e0c313`。
Pivot legacy fallback 两次稳定 RED；修复只在 legacy 分支关闭 hybrid，V934 FULL 分支继续
使用 production 默认，legacy/V934 SQLite focused 各 `1/F0E0S0`，source SHA-256=
`5c6dcd3b4afba4d93a93c1af47cc4484a1dfc9976da92669bfbcc4529ede6155`。生产
Matcher、hybrid 默认、threshold/exclusion 均未修改。

最终 identity 为 amendment=`11 / 4 new + 7 changed`、declared=`17`、top manifest=
`51/51`、successor manifest=`12/12`；static positives/negatives 全绿，required totals 保持
`23/48 + 773/59/5707`。但 threshold 仍为 `diagnostic-pending`，尚无 aggregate baseline/
review、confirmed threshold 或 Step 4 exit evidence。修复与 identity 必须先提交、推送并
验证新的 clean HEAD，才可执行 r3；`can_enter_coverage_audit=no`，Step 5、9.3.5 与
acceptance 必须保持关闭。

r3 从 clean/pushed HEAD `e16693297239f2a861f3b93b3de60c1bb783bda0` 启动：
contract/successor/toolchain/Step 2 view/fresh class universe 全部通过，Unit=
`681 positive + 55 structural / 4,941 testcase / F0E0S0`。Unit leader 返回后，
outer 在同 process group 观测到 live member 并在 `child-unit` fail closed；
Integration/database/external/Addon/aggregate/threshold 均未执行，summary absent。
immutable failed record=
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md`，
canonical bug=
`docs/9.3.4/workitems/BUG-step4-child-run-log-tee-residue-race.md`。r3 Unit 不得与其他
run 拼接，该段 supersede 上文的 r3 next entry，但保留 r1/r2 失败历史。

r3 根因为 Unit/Integration 的异步 `exec > >(tee ...)` logger 未被保存 PID、
close 和 wait，child shell 可以在同 PGID logger 排空前返回。Cdiag 已改为
managed FIFO logger，并收口 9 类 / 14 case lifecycle 负例、PID/PGID/SID/
starttime/boot-id ready/completion identity、kill-before member snapshot、Git 环境隔离、
source/context/manifest 四元绑定、exact raw exec replay 与 typed XML/formal validation。
最终快测、正式质量闸门与 54/54 identity 已通过；尚待 commit/push 与 fresh r4。

threshold 仍为 `diagnostic-pending`，尚无 all-lane aggregate baseline/review、confirmed
threshold 或 Step 4 exit evidence。formal 模式仍不可开启；只有 fresh r4 完整通过、
阈值人工 review 并由 direct-single-parent `Cfreeze` 冻结后，才能从 fresh formal
replay 继续。`can_enter_coverage_audit=no`，Step 5、9.3.5 与 acceptance 保持关闭。

Superseding r4 record（2026-07-16）：reported launch head=
`ceea084ca25a9d679ba128e3f6bd50a63322c112`，run=
`step4-coverage-20260716-diagnostic-r4`。r4 在任何 lane 前于 `source-before` 以 exit code
`2` fail closed；run-owned Git/source seal、run context、summary、aggregate/threshold 全
absent，decision=`excluded-from-step4-exit`。reported launch head 不得改写为 run-owned
`tested_commit`。immutable record=
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r4-fail-closed-20260716.md`。

source identity 合同现明确区分 authoritative Git mode 与 worktree permission bits：
HEAD/index 的 path+Git mode+blob 必须 exact；worktree 必须是 canonical regular file、exact
content、当前 owner 与可证明 private primary group、single link、stable stat/inode，且禁止
world-write/special-bit。worktree executable bit 不得替代 Git mode。security Git 调用关闭
fsmonitor/untracked-cache 并要求 ordinary index flags；错误详情必须写入日志但不替代 typed
status/seal。tracked full-path canonical preflight 必须在任何 worktree-aware Git 操作前拒绝
FIFO 等特殊文件；完整读取前后的 raw stat identity 必须 exact，不能因 Git clean 过滤后内容
等价而接受运行中的并发重写。
source seal 清除 ambient/global Git clean 配置并显式复算 raw 与 CRLF-input 两个 candidate，
使真实 CRLF worktree 在 HEAD/index clean-equivalent 时通过；HEAD-fixed attributes 若声明
external clean filter，则在任何 worktree-aware Git hash/driver hook 执行前 fail closed，
negative 证明 hook 未执行。

Historical pre-r5 静态合同结果为 contract=`20/20`、source identity=`22/22`、XML=`63/63`、overlay=
`12/12`。declared amendments SHA-256 保持
`1e4f15c9e403d454fe07404e45b1226eae94f70faa154433a8db39531a305b47`；diagnostic/formal
coverage contract SHA-256=
`5f4b49fd161b4f381a4f8c2238583eb56f27b577973ff93ce0659d84cca75f1d` /
`58c3479666d0b786ea0ad8327b72b05c9e006dfdb516eacce9098ea83ef4c405`；successor
manifest=`12/12`，SHA-256=
`751018ac7c2357cface77dd125c5edc757ad488a500a3c8d9eece0354767381a`；top manifest=
`54/54`，SHA-256=
`ebda814b1278f92cf1ba7dc202170e4a77cb7e1f4485e6cb1375d152592a76d0`；coverage tool /
contract-negative / XML tool SHA-256=
`07a36a2be8edc0afc0ab1031b052c2208a4e32769c4cdb475a397f81e6121ac9` /
`732d799619461a4b49c8e9bfbb0a3487b107c36110b9e55cd91a405352d0ddb0` /
`b837314ac4166eeeab94124b53e4f776dcdf8095a3b3915e14e45b81d910d439`；overlay contract /
overlay tool / outer SHA-256=
`2d4fe0024caac33199e2ccf87289dd9a262302d3faabad6b038adadb2b2974cb` /
`a16aadf9c4d540cda8b95d1fc1ded94cf420aa0cfe5a1653b8f90d4cb72e0f51` /
`254c7603554787ca38d880ac607f7dd4a21ae89064674490858245f0824951c9`。

Historical pre-r5 Step 4=
`in-progress / r4 historical fail-closed / source-policy remediation statically passed /
final review passed B/H/M/L=0/0/0/2 / amend/push + fresh r5 pending`。decision=
`ready-with-risks`；两项 Low 均 accepted：`/usr/bin/echo` 平台前提漂移会 fail closed；同 UID
视为 build authority，未来更强隔离改用 readonly snapshot/独立 checkout。只放行
amend/push + fresh r5。threshold 仍为
`diagnostic-pending`，`can_enter_coverage_audit=no`；r1–r4 均不得拼接，clean commit/push 与
fresh r5 完成前 Step 5、formal、coverage audit、acceptance 和 9.3.5 都保持关闭。

Superseding r5 record（2026-07-16）：tested commit=
`a35b99cb08f42817d8e75c440f18910b6961841b`，run=
`step4-coverage-20260716-diagnostic-r5`。r5 建立 run-owned Git/source seal，完成
Unit=`681+55/4,941/F0E0S0`、Integration=`47+4/320/F0E0S0` 与
Addon=`2/6/F0E0S0`；随后 database-state companion 因选择 frozen Step 3 authority
manifest，以 `E_AUTHORITY_MANIFEST: stale authority artifact:
foggy-dataset-model/pom.xml` fail closed。database cells、external、aggregate、threshold、
source-after 和 summary 均 absent，r5=`excluded-from-step4-exit`，已通过的 partial
lanes 不得跨 run 拼接或复用。immutable record=
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r5-fail-closed-20260716.md`，BUG=
`docs/9.3.4/workitems/BUG-step4-database-state-successor-authority-manifest.md`。

Step 4 successor contract 现明确要求：database positive report、database-state
companion、required final verifier 与 report_inventory replay 必须从同一 successor
database authority 派生。frozen Step 3 tools/contracts 必须保持字节不变；Step 4
`database_state_negative_tool.py` 只改写 matrix contract selector，
`step3_required_report_tool.py` 只改写 exact frozen state-tool argv，非目标 verifier
argv 必须保持不变。database runner、required runner 与 report_inventory 必须
各自精确绑定 successor adapter；adapter/selector/contract/manifest 任一漂移必须
fail closed，original frozen state validate 保留为 stale-predecessor 负例对照。

当前 successor + Unit fixture/lifecycle remediation 静态身份为：coverage contract diagnostic/formal=
`c062219a6335ae41330c6d5924d6fce60941c5d168b361081fbb41df77428477` /
`341991d6b5a15d19cdb9e0de70a8cc6ace29480227596c413c07e6bf7fdbc73d`；
successor manifest=`14/14` / SHA-256=
`acb580e92a72eb407f31f5d6f9a8139a3509f3a0bfbf58537922465f4086a112`；
top manifest=`60/60` / SHA-256=
`0c4e6c18f4af0a2c35418604ce80d20828ceb4c275375b7d12461b4683553a81`；
overlay contract/tool=
`84d09bfc333bb40d8ef830979734933717555845cebe9943f70ff7087a9a482d` /
`1fea2816504519b7e7f1dc6839744ee943a9a4bf3feb783375e21e935da63d31`；
coverage tool=`27afd37350fa7f1646fba4be59791ec6bdec94fe57e0cdfecc2a08e0f43f2f18`；
declared amendments=`18` / SHA-256=
`8e21b8527f290061361ef0b8fbf084d51b2536ef479e1f70e45488f996090bfc`；
fixture contract/tool/Unit runner/datasource adapter=
`7aa1e21aef85b51a13aacc8c134a1c363c595deffbfb3acf6aafdb942519b53a` /
`cc19390ce6c0cfb307b7632dbe4e25540b1e4d49d11ec1512739f6724646d345` /
`45536c0a969731f6b7c87acecdb225b13a8a0fca45a9a04c9cdfb2173fc60c66` /
`9500cd4d50930b121a36798857cd0a1cc0c8b2190b0a2fc9ad0ea464394bb256`；
database/required contracts=
`553dabf2b4c266b531fb4ce36f4a498dce223b6449106274a3a2b103ccb775ea` /
`893ac03231cb4f6fd8ae427c01aa3f9f04267c96e3945814b9b70a3445a58af5`。

Unit fixture quality r2=`step4-unit-fixture-quality-20260716-r2` 在 commit
`a603f839a98d99b2d7beb8379f76b4d85539328c`、source=`3,981 files` /
`087d074f3497aff3fe305806f82b4d62ff41cdd4d3b26556e58f034138b14c2c`
上先通过 lifecycle `5/5`，随后因全局 Spring datasource 覆盖 SQLite profile，使
`foggy-dataset-model` 以 `3,115 tests / 631 errors` fail closed；根因是
`org.sqlite.JDBC` 与 `jdbc:mysql://127.0.0.1:13306/...` mismatch。该运行
excluded/non-reusable。

Fresh Unit fixture quality r3=`step4-unit-fixture-quality-20260716-r3` 已在 commit
`50161a0a869430e353f3933d9bb00dda59d9c4b1`、source before=after=`3,982 files` /
`1db06cc18bb86c288ffa79e7094e3b9e509d866ddc23b6c93bcaaa0422b99eb2` 上通过。
唯一 Surefire invocation=`681 positive + 55 structural = 736 raw reports / 4,941 testcase /
F0E0S0`；fixture negatives=`36/36`、receipt schema/tamper=`4/4`，closed receipt 的
`18/18` connections 全部为 `v934_unit`；真实 lifecycle=`5/5`，run-owned
container/volume/network cleanup=`0/0/0` 且 port free。evidence window 外 demo MySQL 已按
exact ID `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166`
恢复为 `running/healthy`。record=
`docs/9.3.4/evidence/step-4/step4-unit-fixture-quality-r3-pass-20260717.md`。

r8 时点 Step 4=`in-progress / r8 bootstrap-negative excluded / lifecycle remediation quality
passed / ready-for-commit-and-fresh-r9`；该历史状态已由下方 r9 contract supersede。Unit authority 必须为唯一 Maven invocation
创建 pinned、run-owned MySQL 5.7，并封存 schema before/after、resource identity 与 cleanup；
不得借用 ambient listener；r3 已动态证明该边界。当前 contract static=`20/20`、Unit fixture negatives=
`36/36`（原 fixture/manifest probes=`20/20`、connection typed=`7/7`、atomic
publisher=`3/3`、profile boundary=`6/6`）、negative receipt 文件 schema/tamper 另为
`4/4`、真实 lifecycle=`5/5`、report inventory=`30/30`；r3 是 Unit remediation subgate
证据，不是 coverage exit 或 Step 4 passed 结论。r8 在 bootstrap-negative 因 stale Unit
direct-trap static shape 拒绝 fixture-aware wrapper，run identity/lane/aggregate/threshold/
summary absent，r8 excluded/non-reusable。remediation 必须同时满足 executable contract、
critical slices、canonical references 与 raw-byte Unit/Integration runner seals；当前 dynamic=
`9 类 / 14 case`、Unit shape/seal=`13+3`、Integration=`11+5`，两路独立 quality=
`0/0/0/0`。该 r8 时点 threshold 仍为 `diagnostic-pending`，`can_enter_coverage_audit=no`；r1–r8 与
Unit quality r1/r2 均不得拼接；formal remediation quality 已通过；本轮 closure
当时 commit/push/clean HEAD 与 fresh r9 完成前，Step 5、formal、
coverage audit、acceptance 和 9.3.5 都保持关闭。只有
fresh formal、实现质量闸门、测试证据覆盖审计和 9.3.4 验收全部通过，9.3.4 才允许带
`DEBT-unit-mysql57-fixture-classification-migration.md` 签收；该债务必须在 9.3.5
版本验收前关闭。

## Superseding r9 exec identity contract（2026-07-17）

r9 已证明原 `class-name -> one ID` 规则不符合 JaCoCo execution-data identity：23 份 raw
exec 含 `16,693` 个唯一名称、`16,939` 个 class-ID identity、135 个同名多 ID，但 fresh
sealed `2,098` 个 production class 中冲突为 0。r9 在 exec-manifest 发布前 fail closed，
aggregate/source-after/threshold/summary absent，必须 excluded/non-reusable。

自本节起生效且延续至今的 class-ID contract：

1. `coverage-contract.json/jacoco.class_id_consistency_scope` 必须精确为
   `frozen-24-module-production-class-universe`；任何缺失、扩张或替换都 fail closed；
2. 每个 raw exec 仍必须命中至少一个 fresh production class；凡 binary name 命中 frozen
   universe，其 observed ID 集必须精确等于 current production CRC64 ID；
3. 非 production 的 test/runtime/dependency class 不按名称排除；aggregate 以 JaCoCo class
   ID 为主 key，同 ID 的 name/probe-count 必须 compatible，bitmap 必须为所有 inputs 的
   exact OR；同名不同 ID 分别保留；
4. exec manifest 的 `unique_execution_classes` 表示唯一 class ID 数；aggregate provenance
   必须同时封存 production consistency scope 与
   `exact-session-and-jacoco-class-id-probe-bitmap-union`；XML verifier 精确消费新 schema；
5. r9 focused regression=`17/17`，contract mutations=`21/21`，overlay=`12/12`，但这些
   不是 diagnostic/formal exit evidence；
6. lifecycle semantic validator bypass 当时重新打开 implementation quality；coded mutation
   已证明 comment/dead-context/dynamic-trap 不能满足 executable contract；该 r9 remediation
   的独立正式质量与后续 fresh runs 已完成。

该 r9 时点 threshold=`diagnostic-pending`、`can_enter_coverage_audit=no`；当时要求 fresh r10、
threshold freeze、formal、最终质量、coverage audit、acceptance 按序，Step 5 不得提前。

## Superseding formal-r2 deterministic coverage contract（2026-07-17）

1. reviewed exact aggregate threshold 不得依赖未定义执行顺序，包括 JVM shutdown-hook
   scheduling、filesystem directory traversal、random UUID filename ordering 或其短路副作用；
2. formal-r2=`step4-coverage-20260717-formal-r2` 完成 exact
   `773+59/5707/F0E0S0`、Addon=`2/6`、23 exec/48 sessions 后，aggregate branch 比 reviewed
   threshold 少 `1` 并 fail closed；唯一变化为非 critical
   `FileSystemListPresetStore#findById` filename-false outcome；
3. `Files.find(...).findFirst()` 的 coverage 必须由已有多 regular-file 的 testcase 执行
   nonexistent-ID 查询来确定性覆盖，不得依赖目标/非目标 UUID 文件谁先出现；
4. 回归不得增加 testcase/report identity，不得改 production、floor、critical set 或 exclusion；
   focused 5/5 必须命中 probe 106，Data Viewer module 必须 F0E0S0；
5. focused evidence 不替代 authority。测试字节变化后必须恢复
   `diagnostic-ready/diagnostic-pending`，从 new Cdiag 完整重走 fresh diagnostic -> candidate/
   review -> direct-child Cfreeze -> fresh formal；
6. 当前 `can_enter_coverage_audit=no`、`can_enter_acceptance=no`。fresh formal PASS 后仍须最终
   implementation quality，再按 coverage audit -> acceptance 顺序执行；Step 5 不得提前。

## Superseding diagnostic-r15 deterministic correctness contract（2026-07-17）

1. required correctness lane 不得用单次 `System.nanoTime/currentTimeMillis`、样本倍率或固定毫秒
   上限判定缓存/批量操作正确；JVM/JIT/GC/OS/JaCoCo 调度差异不得成为 Surefire 红绿 oracle；
2. `Bean2MapUtilsTest#testCachingMechanism` 必须以 observable behavior 验证重复复制：使用
   三个同类、不同 source 实例，已完成的 target 必须保留各自调用时的精确值，证明 cache metadata 不保存
   instance data；不得通过反射锁定 private cache identity；
3. `testPerformanceWithManyObjects` 必须继续执行既有 1000-copy batch 并校验首末结果，但不得
   在 correctness lane 声明 `<1000ms` 等环境相关 SLA；性能治理如需新增，应使用独立多 fork
   benchmark/telemetry authority；
4. remediation 必须保持 test/report cardinality，不改 production/public API/POM/runner/floor/
   critical/exclusion。focused 10/10、class=`23/F0E0S0`、module=`27/F0E0S0` 是最小回归门；
5. r15 partial `26 reports / 124/F1E0S0`、`2/48 sessions` 不得复用；最终 sensitive scan、
   source-after、inventory、aggregate、observation、candidate、summary/gate absent 必须如实记录；
6. 测试字节变化后 machine 保持 `diagnostic-ready/diagnostic-pending`，必须从 new Cdiag 完整重走
   fresh diagnostic -> candidate/review -> direct-child Cfreeze -> fresh formal；fresh formal PASS 后
   仍按 final quality -> coverage audit -> acceptance。Step 5 不得提前。

## Superseding diagnostic-r16 / reviewed Cfreeze contract（2026-07-17）

1. replacement Cdiag=`f863c672029d5d1e5a4903df74cf6cba22a04a85` 上的 fresh diagnostic
   `step4-coverage-20260717-diagnostic-r16` 是当前唯一可用于本次 threshold review 的
   diagnostic authority：required=`773+59/5707/F0E0S0`、Addon=`2/6`、exec/session=
   `23/48`、unique execution class IDs=`16,948`；
2. reviewed aggregate observation 精确为 `54624/76830 line, 26111/44870 branch`；12 个
   critical row 全部通过、below-floor=`0`、唯一 N/A 为 `NamespaceScope.branch`。
   Bean2MapUtils 关键回归 coverage 也不得低于 r16 observation；
3. `FileSystemListPresetStore#findById` 的 filename-false outcome 必须由既有 testcase 的
   missing-ID assertion 显式穷尽 predicate，formal-r2 缺失的 branch/probe 106 已在 r16
   恢复；不得重新依赖 UUID 文件遍历顺序或 `findFirst()` 偶然短路；
4. candidate SHA-256=
   `2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919`；review
   SHA-256=`88b99e76e5584d3cd17bcdcffd138f1fe6655ce0b7795d3430d2f15a018c8fb3`，
   B/H/M/L=`0/0/0/1`。唯一 Low 只允许由 fresh formal 复核非 critical probe 稳定性，不构成
   aggregate/critical threshold 下调、exclusion 扩张或 authority 重跑挑选的授权；
5. canonical Cfreeze machine worktree 必须保持 threshold `confirmed` / SHA-256=
   `ca6a25c66fbbe9a595adde74f1b7589bd3829b93edebfd5b11dc394ab8d088c8`，contract
   `formal-ready` / SHA-256=
   `6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`，并由 Cdiag
   的 direct-single-parent Cfreeze commit 封存；
6. fresh formal 必须使用唯一新 run ID 重跑全部 23 exec/48 sessions，保持
   `773+59/5707/F0E0S0` 与 exact inventory，并复现（达到或高于）aggregate minimum
   `54624/76830 line, 26111/44870 branch`、12 个 critical minima 和唯一 N/A；任何不足均
   fail closed，禁止复用 r16 exec/XML 或降低 threshold；
7. 当前仅完成 reviewed Cfreeze worktree，尚无 direct-child Cfreeze commit/push 或 fresh formal
   result。两个 implementation regression 可按 diagnostic verified 关闭，但不得据此声明
   Step 4/9.3.4 accepted；`can_enter_coverage_audit=no`、`can_enter_acceptance=no`，Step 5
   保持关闭。

## Superseding formal-r3 QueryModel DCL determinism contract（2026-07-17）

1. formal-r3 是 immutable failed authority：它在 Cfreeze
   `a63c82c53ebaad1a1c22d78647fbda70b4bd6594` 上完成全部 required lanes 后，
   必须因 aggregate branch `26110/44870 < 26111/44870` 保持 `E_FORMAL_LOW`；
   line exact `54624/76830` 不得被用来覆盖 branch failure；
2. exact aggregate 不得依赖 `QueryModelSupport#getMergedJoinGraph` 两个 caller 的偶然
   调度。测试必须在受控的首次 build 窗口内证明第二 caller 已阻塞于 exact
   `QueryModelSupport` monitor，释放后再断言内层 false outcome、single build 与两调用者
   same graph；
3. regression 必须置于既有
   `RuntimeNamedDataSourceResolverBindingTest#publicationGuardSerializesTheCallbackWithAConcurrentRebind`；
   禁止新增/改名 `@Test`、
   降低 aggregate/critical threshold、扩大 exclusion 或修改 production 来制造绿色；
4. targeted/overlay 与 5/5 fresh Maven/JVM 已 PASS；QueryModelSupport class id=
   `d242dafe9de31249`、probes=`34/629`，5 份 packed bitmap 完全一致、unique=`1`，
   Surefire=`1/F0E0S0`。`foggy-runtime-api` full module=`128/F0E0S0`，
   `RuntimeNamedDataSourceResolverBindingTest=5/F0E0S0`。formal-r3 recovery pre-Cdiag
   implementation quality 已 `PASS / 0/0/0/0`，machine/contract/overlay/negative suites 通过。
   这些仍只证明确定性回归与 Cdiag 准备；下一步必须从 clean/pushed
   new Cdiag 重跑完整 diagnostic authority；
5. 因 test bytecode 已变，r16 candidate 与 formal-r3 threshold 不得直接复用。canonical
   machine 必须保持 contract=`diagnostic-ready`、threshold=`diagnostic-pending`、
   manifest=`60/60`，直到 fresh diagnostic -> review -> direct-child Cfreeze 再次完成；
6. fresh formal PASS 后仍必须按 final implementation quality -> coverage audit -> acceptance
   顺序执行。当前 `can_enter_coverage_audit=no`、`can_enter_acceptance=no`，Step 4
   `in-progress`，Step 5 closed。

## Superseding diagnostic-r17 final-mysqld handoff readiness contract（2026-07-17）

1. replacement Cdiag=`316a71f753827f8f34063b0eb0669271f696c5ee` 上的 fresh diagnostic
   `step4-coverage-20260717-diagnostic-r17` 是 immutable failed authority：它在
   `child-unit` 的第三个 MySQL 5.7 lifecycle probe 中、callback ready 前以
   `E_LIFECYCLE` fail closed。该轮未产生 Unit XML/exec、source-after、aggregate、
   observation、candidate 或 final summary，必须 excluded/non-reusable；cleanup-only JSON
   不得解释成 fixture/lifecycle PASS，也不得与后续 run 拼接；
2. RED runtime observation 已证明 stock `mysqladmin ping` 会在 MySQL entrypoint 的临时
   initialization server 阶段提前报告 healthy。自本节起，Step 4 successor authority Compose
   中 MySQL 5.7 与 MySQL 8 的 readiness 必须同时满足 PID 1 `/proc/1/comm` 精确为
   `mysqld` 和各自原有 `mysqladmin ping`；两条件必须在同一 healthcheck 成功。该 amendment
   只约束 run-owned Step 4 authority，不修改 frozen Step 3 provisioner；
3. PID 1 handoff guard 只是 provisioner callback 的前置条件。其后既有 business identity、
   schema/sentinel 与四个 `preagg_watermark` 校验必须原样执行，禁止以延长 sleep、只看端口、
   降低 identity/watermark 条件或借用 ambient DB 替代；
4. lifecycle provisioner 的 combined stdout/stderr 只允许写入 probe run root 内 no-clobber、
   mode `0600` 的 failure-only diagnostics。受控 failure 必须保留原 typed `FixtureError` code
   （例如 lifecycle=`E_LIFECYCLE`、cleanup=`E_CLEANUP`）并携带 diagnostics path；成功日志只能
   在进程已达到 expected terminal state、
   finalizer/cleanup 全部完成后删除。failure log 不自动成为发布证据，正式引用或归档前必须
   通过 sensitive scan；
5. runtime GREEN 已分别证明 MySQL 5.7/MySQL 8 首次 healthy 时 PID 1=`mysqld`。修复旧字节
   lifecycle 共 `15/15` PASS；penultimate current-byte `5/5` receipt SHA-256=
   `159fbe80595933e29f05f13c0f1d82e9b65d7f947dddec253477f0b3f3876799`。最终 current-byte
   run=`step4-unit-lifecycle-handoff-current-20260717-r2` 在 fixture tool SHA-256=
   `9be62daaf7a3d2d873c7647078c0bf798ab25c491a163e90960d4143965be5be` 上完成 `5/5`，
   receipt SHA-256=`e3bad41ad9ec634c1702ffe20f4c0ddbff3050a227956e2ba9054a11f6b606c7`；
   successful probe 的 `provisioner.log` 全部 absent，demo restore=`runner_rc=0 /
   restore_rc=0` 且四库 healthy/listening；
6. successor overlay negatives=`12/12`、Unit fixture negatives=`36/36`、coverage contract
   negatives=`27`、source/Git negatives=`22`、replay negatives=`12`。这些 focused/static
   结果只证明 remediation contract，不替代 clean-source 全量 Unit 或 diagnostic authority；
7. dirty worktree 上尝试的 full Unit 在 source seal 预检即被 untracked files fail closed，未执行
   Maven，因此不得记录为 Unit PASS 或 product failure。pre-Cdiag formal implementation
   quality 已 `PASS / B/H/M/L=0/0/0/0`，记录=
   `docs/9.3.4/quality/step4-diagnostic-r17-recovery-implementation-quality.md`；该 PASS 只授权
   创建并 push 唯一 replacement clean Cdiag，再由 fresh diagnostic 从 lifecycle、full Unit
   到 aggregate 完整证明；
8. 本修复不得修改 production/public API、test/report cardinality、coverage floor、critical set、
   threshold 或 exclusion。canonical machine 保持 `diagnostic-ready/diagnostic-pending`；
   当前只授权 one replacement Cdiag commit/push/clean + fresh diagnostic；candidate/review、
   direct-child Cfreeze 与 fresh formal 均 pending。fresh formal PASS 后仍必须按 final
   implementation quality -> coverage audit -> acceptance/signoff 顺序执行；当前 full Unit
   authority、Step 4、Step 5、audit、acceptance 与 9.3.5 均关闭。

## Superseding diagnostic-r18 governed-high-water / Pivot NULL-axis contract（2026-07-17）

1. replacement Cdiag=`5be1edaa16c5883cde2f66396ac26a1ae113430b` 上的 fresh
   `step4-coverage-20260717-diagnostic-r18` 是 complete/public-valid diagnostic authority：
   required=`773+59/5707/F0E0S0`、Addon=`2/6`、exec/session/identity=`23/48/16940`、
   class universe=`24/2098`、cleanup=`0/0/0`；
2. complete diagnostic PASS 不自动授权 threshold candidate。r18 aggregate exact=
   `54622/76830 line, 26107/44870 branch`，必须与 r16 reviewed high-water
   `54624/76830 line, 26111/44870 branch` 比较并记录 delta=`-2/-4`。r18 decision
   固定为 `threshold-candidate-not-authorized`，candidate 必须 absent；禁止降低 aggregate/
   critical threshold、扩大 exclusion、选择性重跑或复用 r18 exec/XML 来制造 freeze；
3. r16/r18 exact delta 仅允许解释为
   `BaselineRatioCalculator=-2 line/-3 branch` 与 `ResultShaper=-1 branch`的 NULL-axis
   coverage-oracle gap，因为 production source/class tree、denominator、test/report inventory 均无
   drift。该结论不得外推为 product defect，也不允许忽略 exact high-water；
4. 既有 `PivotSqlParityIT` S12 必须包含不依赖数据库偶发 LEFT JOIN 结果的
   deterministic semantic oracle：NULL column-axis member 不得进入 first/last baseline domain，
   NULL row-axis member 必须保留为 `__null__` tree node，并对这些可观测业务结果
   作精确断言；禁止只为命中 probe 而不证明语义；
5. regression 必须保持 testcase/report identity，不新增/改名 `@Test`，不改
   production/public API/POM/runner/floor/critical/threshold/exclusion。当前三次 fresh JVM/JaCoCo
   focused 均 `1/F0E0S0` 且两目标 bitmap `3/3 identical`，完整 test class=`23/F0E0S0`；
   这些仍只是 focused/class evidence，不是 all-lane authority；
6. 变更的 test bytes 已由 successor declared amendment、protected-tree manifest、
   database/required contract、overlay contract、successor/top SHA256SUMS 完整绑定；required
   negatives 与 pre-Cdiag implementation quality=`PASS / 0/0/0/0`，record=
   `docs/9.3.4/quality/step4-diagnostic-r18-pivot-null-axis-implementation-quality.md`。当前只可建立
   唯一 replacement Cdiag commit/push/clean；
7. fresh r19 diagnostic 必须重跑全部 `23 exec / 48 sessions`、通过 public
   validation，且 line/branch 均达到或超过 r16 reviewed high-water，才允许生成
   candidate 并进入 independent review。任一不足都必须 fail closed，不得进入
   Cfreeze/formal；
8. canonical machine 保持 `diagnostic-ready/diagnostic-pending`。当前
   `can_enter_coverage_audit=no`、`can_enter_acceptance=no`，candidate/Cfreeze/formal/final
   implementation quality/coverage audit/acceptance/Step 5/9.3.5 均关闭。fresh formal PASS 后
   仍必须依次完成 final implementation quality -> coverage audit -> acceptance/signoff。
   governed evidence=
   `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r18-governed-high-water-gap-20260717.md`；
   BUG=`docs/9.3.4/workitems/BUG-step4-pivot-null-axis-coverage-oracle.md`。

## Superseding diagnostic-r19 reviewed threshold / formal-ready contract（2026-07-17）

1. r19 必须且已经从 clean/pushed Cdiag `613b11a0…` 完整产生 `773+59/5707/F0E0S0`、
   Addon=`2/6`、`23 exec / 48 unique sessions`；source-before/source-after byte-identical；
2. aggregate exact reviewed threshold 固定为 line=`54,624/76,830`、branch=`26,111/44,870`；
   critical identity/order 固定为 Step 1 的 12 类，23 个适用 minimum 必须 exact 等于 observed；
   唯一允许的 N/A 是 `NamespaceScope / foggy-dataset-model / branch = 0/0/null`；
3. candidate `6588e30b…f545b8` 必须永久保持 immutable `review-required`；canonical confirmed
   threshold 只能 exact 投影 candidate，并绑定 review evidence SHA；
4. machine transition 为 threshold=`confirmed`、contract/publication=`formal-ready`；允许的 non-doc
   delta 只含 threshold、contract、Step 4 SHA256SUMS 三件套；
5. Cfreeze 必须是 Cdiag 的 direct-single-parent child，通过 formalization-delta、push 与 clean
   identity 后才允许 fresh formal；formal 必须 fresh 重跑所有 lane，不得复用 diagnostic bytes；
6. 当前 `can_enter_coverage_audit=no`、`can_enter_acceptance=no`。formal/final quality/audit/
   acceptance/Step 5/9.3.5 均 pending；machine formal-ready 不等于 Step 4 exit。

## Superseding formal-r4 / quality-reviewed contract（2026-07-18）

1. Cfreeze `f97483a0…` 必须且已经是 Cdiag `613b11a0…` 的 direct-single-parent child，
   formalization delta=`passed`，push/clean identity成立；
2. formal-r4 必须且已经 fresh 重跑全部 `23 exec / 48 sessions` 与 required lanes，产生
   `773+59/5707/F0E0S0`、Addon=`2/6`，没有复用 diagnostic exec/XML；
3. aggregate denominator 必须 exact，covered 不低于 confirmed minimum；actual=
   minimum=`54624/76830 line, 26111/44870 branch`。12 类/23 适用指标通过、below=`0`，
   唯一 N/A 仍为 `NamespaceScope.branch`；
4. run-status、summary、coverage gate、candidate、final、source、report/exec/provenance、
   model、negative、lifecycle、cleanup 哈希链及 public final replay 全部通过；
5. evidence window 内 live report inventory `verify + validate` 必须 PASS；外层恢复 demo DB 后的
   sealed public gate 是 final artifact verifier。不得要求 live validator 把外部恢复状态误作
   run-owned port-free，也不得由此降低 fail-closed；
6. post-formal quality=`ready-for-coverage-audit / B/H/M/L 0/0/0/1`。当前
   `can_enter_coverage_audit=yes`、`can_enter_acceptance=no`；coverage audit/feature acceptance
   未完成前 Step 4/5/9.3.5 仍关闭。

## Superseding Step 4 feature-accepted contract（2026-07-18）

1. coverage audit 必须且已经映射 14 个 requirement/acceptance item 与 25 个 Step 4
   workitem；Step 4 scope critical/major gap=`0/0`；
2. formal 五库只执行 V934 Pivot 分支的边界必须显式记录；同一 Cfreeze/final source 的 legacy
   companion=`1/F0E0S0`，并绑定 property absent、source/XML SHA 与 exact preAgg oracle；它不
   改写 formal totals 或 final artifact；
3. feature acceptance 必须且已经为 `signed-off / accepted / blocking none`；25 个 Step 4
   workitem 只能在该 decision 后关闭；classification DEBT 继续 open；
4. Step 4=`passed`，`can_enter_step5=yes`；Step 5=`ready / not-started`，其输入只能是已签收的
   formal-r4 与 companion，不得选择历史 diagnostic 或降低 threshold；
5. Step 5 必须生成 portable/single-authority candidate 并拆分 live/durable replay 入口；该 entry
   不等于 Step 5 exit、remote CI、release same artifact 或 version acceptance；
6. 9.3.4 继续 `in-progress`，Steps 6/7 pending，9.3.5=`queued`。

## Superseding formal-r6 recovery contract（2026-07-19）

1. formal-r6=`failed / bootstrap-negative / immutable`；不得续跑、补写或与 r22 拼接；
2. malformed synthetic fsmonitor token 只能修成 v2 NUL-token；exact lowercase precondition 与
   validator rc=2 rejection 均不可删除、放宽或以 retry 替代；
3. changed tool 不在 prior formalization allowlist，必须恢复 diagnostic machine state 并形成 new
   Cdiag；r22 Cfreeze 不再是可执行 formal parent；
4. authority 顺序固定为 Cdiag→fresh diagnostic→new candidate/capsule→dual review→direct-child
   Cfreeze→fresh formal→quality→coverage audit→acceptance；
5. current `can_enter_step5=no`，Steps 5–7/9.3.5 均 closed。

## Superseding diagnostic-r23 scheduling-high-water contract（2026-07-19）

1. diagnostic PASS 只证明 run/evidence 完整，不自动授权 freeze；若 exact-observed counter 来自
   incidental scheduling，candidate/capsule 必须 absent；
2. r23 branch=`26112/44870` 的唯一新增 outcome 是 MapBeanInfo inner double-check，historical probe
   证明不稳定，因此不得手工降低/修改 candidate，也不得反复 formal 直到碰中；
3. remediation 必须保留 test/report cardinality，只在既有节点用 controlled monitor interleaving
   确定覆盖 create/cache/inner-non-null paths，并以多 fresh JVM probe identity 证明稳定；
4. replacement r24 必须从 clean/pushed new Cdiag 运行全部 lane，source before/after exact；只有 r24
   public-valid 且目标 branch/probe 稳定，才允许生成全新 candidate/capsule 和启动 dual review；
5. r22/r23/r6 均为历史只读输入，不能拼接；当前 authority 顺序为
   replacement Cdiag→r24→candidate/capsule→dual review→Cfreeze→formal-r7→post-gates。

## Superseding r24 reviewed-threshold contract boundary（2026-07-19）

1. Cdiag=`414c8b12…` 与 r24 source/run/provenance exact；public diagnostic validator PASS；
2. reviewed aggregate exact 为 line=`54624/76830`、branch=`26112/44870`；12 critical、23 applicable、
   1 structural N/A、below-floor=`0`，target probe=`10/11 / _wU`；
3. candidate=`f13f3c35…2ee` 保持 immutable `review-required`；双审 receipt=`APPROVE / 0/0/0/0`；
4. capsule 两次独立 rebuild 与 canonical exact，空目录 materialize PASS；source closure mismatch=`0`；
5. canonical threshold/contract=`confirmed/formal-ready`，machine delta 只允许六个 exact paths；
6. 下一状态只能是 direct-single-parent Cfreeze 后 fresh formal-r7；任何 counter/source/provenance/
   negative/cleanup failure 均 fail closed，且不得降低 reviewed exact threshold。

## Superseding formal-r7 repository-contained input contract（2026-07-19）

1. `CalculateMvpIT` parity catalog 必须是 bridge HEAD 中 exact tracked `100644` blob
   `d7879a6a…` 且 raw SHA=`f52eba37…`；仓外父/兄弟目录同名文件无 authority；
2. Step 4 runner 必须在 Docker/Test 启动前验证该 identity；missing/tampered/index-stage drift 均失败；
3. runner bytes变化必须同步 raw source seal、executable-stream seal、Step4 manifest 与 Step6 bindings；
4. r7 永久 failed/excluded；下一链路改为 Cdiag→fresh diagnostic-r25→candidate/capsule/双审→
   direct-child Cfreeze→fresh formal-r8→post-gates。

## Superseding Unit MySQL consumer-authority contract（2026-07-19）

1. historical r7 observed failure 必须永久保持 `6 reports / 11 errors`，不得回写为 `7/12`；该字段
   只表示 r7 实际进入 Maven error set 的节点，不表示所有真实数据库消费者；
2. current known consumer contract 必须至少列出 `7 execution reports / 12 testcase nodes`。新增的
   第 7 个 key 为 `DatasetJdbcUtilsTest#getOrCreateDataSource`、node=`1`；其旧
   `catch (SQLException) { printStackTrace(); }` 路径属于可伪绿行为，修复后连接/查询失败必须传播为
   test failure；
3. fixture validator 必须同时验证 historical=`6/11` 与 known lower bound=`7/12` 的不同语义、exact
   key/node inventory、缺失/篡改/新增消费者拒绝以及假绿回归；negative suite 必须达到 `42/42`，
   lifecycle suite 仍须 exact `5/5`；
4. consumer 修复与契约扩展不得新增、删除或重命名 test node。full Unit authority 保持唯一 Maven
   invocation=`681+55/4941/F0E0S0`，并继续绑定 run-owned MySQL identity、restricted non-super
   connection、publisher、profile isolation、cleanup 与 report inventory；
5. r25（HEAD=`5aaffbb4cd217d3d891c22eca4d3ae31d4e9d6e7`）虽 full-chain public-valid，仍因
   运行在本 contract 生效前而 exact classified 为 `pre-remediation / superseded / non-candidate`；
   不得据其 counters/artifacts 进入 candidate、review 或 Cfreeze；
6. replacement authority 必须从修复后的 new Cdiag 开始，经 fresh diagnostic-r26、new
   candidate/review、direct-single-parent Cfreeze、fresh formal-r8（或下一可用 formal ID）和全部
   post gates。该 formal chain 未完成时，9.3.5 closed；9.3.4 version signoff 后，9.3.5 只允许先进入 Gate 0
   classification-debt migration，债务关闭前不得执行 9.3.5 version acceptance。

## Superseding formal-r8 interpreter-dispatch contract（2026-07-19）

1. formal-r8=`failed / coverage-report / exit 126 / immutable`；required/report inventory 的先行 PASS
   不得替代缺失的 exec provenance、aggregate、coverage gate、candidate/final，r8 raw artifact 不得续跑、
   补写或与 replacement run 拼接；
2. runner 使用的四个 Python 工具必须保持 Git `100644` 并可由普通 fresh clone 正确物化；其中
   exec verify、contract validate、exec verify-aggregate 三个历史错误 command positions 必须全部显式
   使用 `python3`，不得以 chmod、worktree executable bit 或只修首个失败点替代；
3. contract negative 必须先精确绑定 report runner raw bytes 和完整 logical executable command
   stream，再读取 Git stage mode 并绑定四个 target assignment 与七个 top-level logical interpreter
   dispatch；comment/heredoc/dead-scope decoy、`command/env/exec/if/!` wrapper、command substitution、
   braced/unquoted/literal/direct/dynamic target、target rebind、inline Python heredoc direct call 与七处
   去解释器均必须拒绝；semantic probes 必须在显式关闭两层 source seal 后独立拒绝 `33/33`；
   `100755/120000/untracked/missing` Git-mode mutations 必须 fail closed，四个 `0644` copy 必须证明
   direct denied/interpreter passed；report runner probe 前后 hash 必须一致；
4. 任何 authority-tool delta 都必须撤回旧 formal-ready 状态并恢复
   `diagnostic-ready / diagnostic-pending`，同时闭合 Step 4 manifest 和 Step4→Step6 hash bindings；不得改写
   r26 reviewed counter、降低 floor/critical/exclusion 或复用旧 candidate；
5. replacement 顺序固定为 new Cdiag→fresh diagnostic-r27→new candidate/capsule/双审→direct-child
   Cfreeze→fresh formal-r9→final quality→coverage audit `31/31`→feature acceptance。三门未完成前
   `can_enter_step5=no / can_enter_coverage_audit=no / can_enter_acceptance=no`。

## Superseding diagnostic-r27 aggregate high-water contract（2026-07-19）

1. A public-valid diagnostic whose aggregate counter is below the reviewed high-water is non-freezable even if
   `freeze-thresholds` and `verify-threshold-candidate` mechanically pass.
2. r27 branch/complexity=`26111/44870` / `17658/35571` must not replace r26
   `26112/44870` / `17659/35571`; its temporary candidate/capsule are non-canonical.
3. The controlled `LinkedHashMap` fixture only stabilizes the existing `ExportWithChartTool` test path. Five
   fresh JVM exact probe proofs authorize a new Cdiag, not a threshold exception.
4. Fresh r28 must meet or exceed r26 aggregate high-water before candidate/capsule/review/Cfreeze may start.

## Superseding formal-r9 strict-umask effective-POM publication contract（2026-07-19）

1. `reporter_effective_pom_tool.py` public JSON receipts are exact `0644` artifacts regardless of caller
   umask. Their staging inode is `0600` while content is written and fsynced; the same descriptor must set
   `0644` and fsync before the no-replace link becomes visible. A final mode other than `0644` is an error.
2. Contract-negative evidence must directly invoke the real publisher under `umask 077` and reject a
   non-regular, symlinked, content-different, or non-`0644` result. This probe is no-container and mandatory
   before any Cdiag or formal authority run.
3. formal-r9=`coverage-report / exit 2 / failed` is an immutable failed-run boundary. Its all-lane counts and
   `23/48` exec receipt describe completed prerequisites only; they cannot supply aggregate, threshold,
   candidate, final, audit, or acceptance authority.
4. An authority-tool change outside a prior Cfreeze exact allowlist must reset the successor tuple to
   `diagnostic-ready / diagnostic-pending` and exact pending threshold predecessor bytes. The sole recovery
   sequence is new Cdiag→fresh diagnostic-r29→new candidate/capsule/dual review→direct-child Cfreeze→fresh
   formal→quality→coverage audit→acceptance; downstream execution remains closed before all gates pass.

## Superseding diagnostic-r32 WatchService delete high-water contract（2026-07-20）

1. A completed public-valid diagnostic below the reviewed high-water is non-freezable even when its required
   lanes, source seal, cleanup, technical candidate, and Git-safe tooling checks pass.
2. r32 line=`54624/76830`, branch=`26111/44870`, complexity=`17658/35571` must not replace the governed
   line >= `54624/76830`, branch >= `26112/44870`, complexity >= `17659/35571`. Its non-canonical material
   must remain isolated and cannot enter Git authority.
3. The approved remediation is limited to the existing mock-key delete test. It must synchronously prove the
   line-442 unfiltered, filtered-reject, and filtered-match outcomes without changing production behavior,
   test/report identity, floor, critical policy, or exclusions.
4. Five focused JVMs and one full owning-module suite are Cdiag quality input only. r33 consumed that fresh-run
   authorization but failed before canonical Unit authority; its final cleanup label cannot be promoted to a
   primary cause or cleanup closure.
5. A clean/pushed docs-only Cdiag and independent governed readiness preflight are required before fresh r34
   may satisfy every all-lane gate and the governed high-water. Only r34's complete authority may begin new
   candidate/Git-safe closure/review/Cfreeze processing.

## Superseding v9.3.4 risk-tiered release exit policy（2026-07-22）

1. The canonical decision record is
   `docs/9.3.4/workitems/DECISION-v934-risk-tiered-release-exit-policy.md`. From 2026-07-22 onward it
   supersedes historical clauses that automatically invalidate the complete Step 1–4 business evidence chain
   for every test-only or evidence-packaging byte change. Historical run facts and failure records remain
   immutable.
2. Release blockers are limited to repeatable production defects; data correctness, isolation, security or
   permission defects; public API/SPI/compatibility breaks; unprovable test or release-artifact authenticity;
   and real required-test failure/error/skip.
3. Deterministic test-oracle repairs and non-product timing/scheduling/order issues require targeted regression,
   the affected lane, source/delta identity, and the last clean full formal only. Non-authenticity-impacting
   evidence format, mtime, mode, layout, and portability issues receive the same bounded treatment. They do not
   reopen Steps 1–3 and do not automatically restart `Cdiag -> diagnostic -> Cfreeze -> formal`.
4. Further evidence-schema hardening, non-security mode/mtime perfection, incidental exact-high-water drift
   above unchanged floors, test-infrastructure refactoring, permanent Unit MySQL fixture reclassification, and
   new probes without a direct authenticity impact are 9.3.5 debt.
5. This closeout permits at most one fresh clean-clone formal from exact Cfreeze
   `b05dd0ec659c283b1a59a82c1c67710f4c10368e`; after it passes, at most one fresh Step 5 rehearsal plus
   portable replay. A new B/C-only governance finding cannot automatically trigger another complete Step 4
   certification loop. Coverage floors, exclusions, required tests, skip policy, source identity, and artifact
   authenticity remain unchanged and fail closed.
