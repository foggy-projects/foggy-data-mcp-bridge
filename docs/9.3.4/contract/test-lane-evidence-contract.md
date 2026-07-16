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
`coverage-report-amendment.tsv` exact=`11 rows = 4 new + 7 changed`，SHA-256=
`937666fc1926ec1c4764ebb50d4b4d4bdd1f1013f0d63cc77d9a1856fae153d2`；
successor declared amendments=`17`，SHA-256=
`1e4f15c9e403d454fe07404e45b1226eae94f70faa154433a8db39531a305b47`。L2 fixture
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

当前静态合同结果为 contract=`20/20`、source identity=`22/22`、XML=`63/63`、overlay=
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

当前 Step 4=
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

当前 successor remediation 静态身份为：coverage contract diagnostic/formal=
`16677d3ae64a7d24aa5796e7c1bbb8ca5af347d6843878471a7e48bdc52c82af` /
`d8e7efa775d021d42485f1ffa6cb51a98a3f3f6662b1793e6b06f69852d12463`；
successor manifest=`14/14` / SHA-256=
`9fa9ddb23aa36c48961e54393f1fe747bf5d0433645cb1a0529e607db4f211cb`；
top manifest=`56/56` / SHA-256=
`be8c4c9c1698674917f1115388d3e7b6a6078d698daf52cb4fa55916166460f9`；
overlay contract/tool=
`cd691d3d91540dd6ddba0045648493d16feaf9ebf3175da3b9ad15b0e399aadd` /
`4df218807847beb789dcf1ef748e13bf21f39da071e4bcf7337fe97b78f8c84a`；
coverage tool=`bf317dd09bb2f909773dba602ab00037acf112b835a166bfd64ef9709045179a`；
declared amendments=`17` / SHA-256=
`187aac883460b259cd002f6c12bb72d8d9824d1e4dd8f12a12959f6866bfccfe`；
database/required contracts=
`553dabf2b4c266b531fb4ce36f4a498dce223b6449106274a3a2b103ccb775ea` /
`893ac03231cb4f6fd8ae427c01aa3f9f04267c96e3945814b9b70a3445a58af5`。

当前 Step 4=`in-progress / r5 historical fail-closed / database-state successor
remediation quality passed B/H/M/L=0/0/0/0 / commit-push + fresh r6 pending`。threshold 仍为
`diagnostic-pending`，`can_enter_coverage_audit=no`；r1–r5 均不得拼接，完成
commit/push/clean HEAD 与 fresh r6 之前，Step 5、formal、coverage
audit、acceptance 和 9.3.5 都保持关闭。
