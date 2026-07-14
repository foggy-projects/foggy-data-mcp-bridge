---
doc_role: contract
doc_purpose: Freeze the 9.3.4 test inventory, runner, database, coverage and release evidence invariants.
version: 9.3.4
status: proposed
created_at: 2026-07-14
updated_at: 2026-07-14
---

# Test Lane and Evidence Contract

## 文档作用

- doc_type: execution-contract
- intended_for: project-root-session / build and CI owners / reviewer
- purpose: 为 Step 1 review 冻结可被 runner 独立断言的 schema 与不变量。

## 1. Source and Execution Inventory

`source-inventory.tsv` 每个 workspace discovery candidate source 一行：

```text
source_id | module | reactor_member | source_root | source_path | top_level_fqcn | kind | discovery_patterns | owner | reason
```

- `kind ∈ {executable,helper,generator}`。executable 的
  execution ownership 由下表表达；helper/generator 不得有 execution row，并必须有
  owner/reason，不能靠文件名收窄静默消失。
- `reactor_member=false` 的 workspace source 仍必须有 owner/reason/disposition；它不
  得被计入 root reactor required execution。Step 1 从 active root `<modules>` 重新
  推导成员，不能硬编码当前数量。

`execution-inventory.tsv` 每个实际 report execution key 一行：

```text
execution_key | source_id | report_fqcn | runner | lane | variant_key | db_kind | infra_kind | execution_step | required | owner
```

- `execution_key` 是 versioned stable key，至少 length-frame
  `(runner,lane,variant_key,report_fqcn)`；全表唯一。
- 每个 executable reactor source 至少一个 execution row；helper/generator 与
  non-reactor excluded source 为 0 rows。`runner ∈ {surefire,failsafe}` 且对每个
  execution key 恰好一个。
- JUnit `@Nested`/dynamic container 可让一个 source 对应多个
  `Outer$Nested` report FQCN；每个 expected report 单独一行，不把 top-level FQCN
  当成唯一报告。当前 65 个含 `@Nested` 的 source 只是 diagnostic，Step 1 必须以
  discovery-only JUnit test plan + POM variant config + available fresh diagnostic XML
  review/freeze exact mapping；不要求提前执行 Step 3 external fixtures。
- 数据库/provider/profile 重复使用 `variant_key`；五库 parity 同一 report FQCN 有
  五个 db-kind execution rows。相同 `(report_fqcn, db_kind, lane)` 不得重复。
- `sqlite-broad-integration` 与 `database-contract-matrix[sqlite]` 使用两个显式、
  互斥 execution-key 子集：前者承担广覆盖 integration，后者只承担五库同构
  preflight/parity/capability contract；同一 `(report_fqcn, sqlite)` overlap 必须为 0。
- actual raw XML report keys 与当前 Step/variant 的 expected execution subset 做双向
  差集：orphan=0、unexpected=0、runner overlap=0。不能把 source 行直接与 report
  FQCN 集比较。
- Step 1 discovery 取当前 Maven 默认/显式 pattern 的并集，包括 `Test*`、`*Test`、
  `*Tests`、`*TestCase`、`IT*`、`*IT`、`*ITCase`、`*E2E`、`*E2ETest`；prefix-only
  `Test*`/`IT*` 也必须逐项判定 executable 或 helper，不能因 final include 收窄而
  静默消失。
- optional 必须有业务原因、owner 和复核时点；“环境可能不可用”不是 release
  optional 理由。
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
- reports 必须晚于 run marker，且 testcase node count 等于 suite tests 总和。
- Step 2 actual exit 由 all unit + hermetic IT 组成；`infra_kind` 为 DB/Redis/other
  external 的 required suite 只能以 reviewed exact manifest defer 到 Step 3，不能
  标 pass。Step 2 对 `execution_step=2` subset 做 exact compare，Step 3 对
  `execution_step=3` subset 做 exact compare；两者 execution-key 并集必须等于全部
  required execution inventory 且交集为空。

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
- Step 3 correctness run 不承诺 exec；Step 4 接好 agent 后必须重新执行 unit、
  hermetic IT 和全部 Step 3 external lanes，只有 Step 4 run 的 exec 可进 coverage。
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
  执行 owning-module `jacoco:check`；全 reactor 首次只生成 diagnostic candidate
  baseline，人工 review 后冻结。
- critical class candidate floor 为 LINE `0.80` / BRANCH `0.70`；最终值取
  `max(reviewed observed value, candidate floor)`，不足先补测试，任何例外需显式
  workitem/approval，runner 不自动下调。
- missing/empty exec、class/source SHA mismatch、threshold regression、未授权
  exclusion/threshold change全部失败。

初始 critical set：CatalogSnapshotStore、ModelBuildSingleFlight、
CatalogRefreshCoordinator、DatasourceCatalogConvergence、NamespaceScope、
CommittedSourceRevisionRegistry、RuntimeNamedDataSourceResolver、
WatchServiceFileTracer、QueryFingerprintBuilder、SecurityPolicyFingerprint、
CaffeineQueryCacheProvider、RedisQueryCacheProvider。Step 1 可经 review 增补，不得
静默删除。

## 6. Predecessor Regression Migration

- 9.3.1–9.3.3 historical runs、raw XML、FQCN/count 和 v933 runner 均 read-only。
- Step 1 冻结 migration edges：

  ```text
  mapping_group | relation | declared_old_count | declared_successor_count | criterion | predecessor_node | successor_execution_key | disposition | owner | reviewer
  ```

  `relation ∈ {1:1,1:N,N:1}`；1:1 是默认。每个 predecessor node 恰属一个 group；
  successor execution key 必须存在于 frozen execution inventory。一个 execution
  key 可支撑多个不同 criterion group，但同 group 内 old/successor distinct node
  cardinality 必须等于 declared values，edge tuple duplicate=0；9.3.4 新增 key 可不
  进入 edge 表。split/merge 必须写 criterion-preservation rationale/reviewer。
  测试/报告总数永远从去重 execution/testcase ledger 计算，禁止按 migration edges
  求和放大证据。
- migration 同时覆盖 package invariants：新增 POM-only coverage reporter 可改变
  reactor module count，但不得增加 production main JAR、Launcher nested JAR 或
  auto-configuration surface；新 expected count/hash set 必须单独 review/freeze，不能
  继续硬套 v933 的 25-module常量。
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
