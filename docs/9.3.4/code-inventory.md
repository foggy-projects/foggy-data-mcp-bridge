---
doc_role: code-inventory
doc_purpose: Record planned 9.3.4 code touchpoints and protected boundaries.
version: 9.3.4
status: in-progress
created_at: 2026-07-14
updated_at: 2026-07-17
---

# 9.3.4 Code Inventory

## 文档作用

- doc_type: code-inventory
- intended_for: project-root-session / build and CI owners / reviewer
- purpose: 标注 owner、预期变更和依赖边界；具体测试分类由 Step 1 manifest
  review 后冻结。

```yaml
code_inventory:
  - module: root-build
    path: pom.xml
    role: reactor plugin defaults and unit runner
    expected_change: update
    notes: central Surefire/Failsafe/JaCoCo; preserve UTF-8 argLine; late-evaluate shared argLine so legacy prepare-agent cannot fail open (BUG-step4-legacy-coverage-argline-fail-open); remove accidental multi-db runner ownership
  - module: all-owning-modules
    path: "**/pom.xml"
    role: module-specific test dependencies/configuration
    expected_change: update
    notes: only where Step 1 inventory proves ownership; no conflicting runner defaults
  - module: all-owning-modules
    path: "**/src/test/java/**/*IntegrationTest.java"
    role: currently ambiguous integration naming (candidate count 33)
    expected_change: update
    notes: classify as unit *Test or integration *IT; final ambiguous count zero
  - module: all-owning-modules
    path: "**/src/test/java/**/*.java"
    role: all Failsafe naming candidates (`IT*`, `*IT`, `*ITCase`, `*E2E`, `*E2ETest`; current suffix-IT count 7)
    expected_change: update
    notes: ensure Failsafe-only ownership, classify prefix candidates/helpers and exact lane reports
  - module: frozen-contract-data
    path: scripts/v934/source-inventory.tsv
    role: reviewed workspace source inventory and reactor membership
    expected_change: create
    notes: one row per candidate source; executable/helper/generator and non-reactor disposition
  - module: frozen-contract-data
    path: scripts/v934/execution-inventory.tsv
    role: immutable Step 1 pre-amendment report/runner/lane/variant/infra/earliest-step inventory
    expected_change: create
    notes: 829 historical identities remain immutable; Step 2 successor separates 770 positive executions from 59 structural outer reports
  - module: frozen-contract-data
    path: scripts/v934/discovery-inventory.tsv
    role: JUnit discovery-only source to report mapping with source/main/test class hashes
    expected_change: create
    notes: ClassSource containers including zero-test outer reports; parameterized invocation cardinality remains runtime-deferred
  - module: frozen-contract-data
    path: scripts/v934/discovery-classpath.tsv
    role: ordered effective classpath and live file/tree hashes for each discovery module
    expected_change: create
    notes: active reactor dependencies must resolve to current target/classes, never stale same-version m2 JARs
  - module: frozen-contract-data
    path: scripts/v934/predecessor-node-inventory.tsv
    role: sealed v933 raw XML node and SHA authority
    expected_change: create
    notes: exact 9.3.3 authority report set; validator regenerates nodes from raw XML
  - module: frozen-contract-data
    path: scripts/v934/predecessor-regression-map.tsv
    role: map 9.3.1-9.3.3 historical nodes to current successor typed references
    expected_change: create
    notes: Step 2 successor has 480 positive execution refs and 39 structural FQCN refs; unmapped/XOR/cardinality drift fails
  - module: frozen-contract-data
    path: scripts/v934/rename-successor-plan.tsv
    role: immutable Step1 pre-rename to Step2 post-rename exact execution-key delta
    expected_change: create
    notes: immutable base 33 sources / 62 reports / 74 identities / 50 predecessor edges plus 2 Mongo corrective renames; final successor applies 72 positive + 4 structural mappings and is independently confirmed
  - module: step2-successor-data
    path: scripts/v934/successor/step2/{execution-inventory.tsv,structural-report-inventory.tsv,step2-required-execution.tsv,deferred-step3.tsv,predecessor-regression-map.tsv,contract-freeze.json,SHA256SUMS}
    role: confirmed post-rename positive execution, structural report and typed predecessor authority
    expected_change: create
    notes: parent-linked immutable candidate/confirm flow; raw XML exact set is positive union structural while totals remain separate
  - module: v934-runners
    path: scripts/v934/{authority_runner_lib.sh,step2_successor_tool.py,step2_report_tool.py}
    role: deterministic successor generation and fail-closed raw report evidence
    expected_change: create
    notes: candidate/final split, shared signal-safe durable status, typed structural zero verification, expected-negative probes and atomic confirmation
  - module: v934-step3-external-runners
    path: scripts/{verify-v934-external-matrix.sh,verify-v934-external-redis.sh,verify-v934-external-mongo.sh,verify-v934-external-mysql.sh,verify-v934-external-vector.sh}; scripts/v934/step3/{external-matrix-contract.json,external_matrix_report_tool.py,external_shared_context.sh,external_lane_launcher.py,external_redis_signal_probe.py}
    role: single-outer required external execution, exact report/candidate collection and real signal cleanup evidence
    expected_change: create
    notes: one lock/outer/HEAD/contract owns 7 variants and 16/76 raw Failsafe evidence; launcher resets inherited async signals while preserving the locked FD across exec
  - module: frozen-contract-data
    path: scripts/v934/maven-variant-inventory.tsv
    role: current Maven profile/plugin execution owner to v934 successor disposition
    expected_change: create
    notes: root default, model multi-db/model-lifecycle and cache real-query owners are explicit
  - module: frozen-contract-data
    path: scripts/v934/{database-contract.tsv,package-successor-inventory.tsv,contract-freeze.json,SHA256SUMS}
    role: DB/package/toolchain/freeze policy and exact integrity manifest
    expected_change: create
    notes: freeze is machine-validated and candidate confirmation atomically refreshes summary digests/status
  - module: frozen-contract-data
    path: scripts/v934/coverage-thresholds.json
    role: reviewed aggregate/package/class thresholds and expected set
    expected_change: create
    notes: candidate values require Step 4 review; runtime never rewrites this file
  - module: model
    path: foggy-dataset-model/src/test/java
    role: broad semantic/unit/integration and database parity surface
    expected_change: update
    notes: split runner ownership; extend preflight/parity to MySQL8 and SQL Server; no all-suite fivefold repetition
  - module: step4-preagg-validation-test
    path: foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationDataValidationTest.java
    role: Step 4 Unit validation oracle for raw versus selected pre-aggregation data
    expected_change: update
    notes: r1 exposed wrong-table corruption plus raw-vs-raw/nullable-empty false green; fix asserts exact monthly corruption and daily/daily/daily_customer routing; source SHA-256 affb6e415c1770eae7c9ab6f3505ac67acfe03eac1461bd2a48addf3ee790623
  - module: step4-preagg-l2-cache-test
    path: foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationL2CacheIT.java
    role: Step 4 Integration oracle for post-PreAgg L2 lookup/write/hit identity
    expected_change: update
    notes: r2 exposed an implicit pre-hybrid snapshot assumption; final fixture explicitly disables hybrid, asserts exact name/table and rejects raw fallback while preserving production defaults; focused 1/F0E0S0 plus PreAggregationIT combination 30/F0E0S0; source SHA-256 bb5d6884401579447382587861e197feac2884ab701065d7826fe7a676e0c313
  - module: step4-pivot-legacy-fixture-test
    path: foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotSqlParityIT.java
    role: Legacy/V934 branch oracle for PreAgg-before-Pivot parameter order and exact table identity
    expected_change: update
    notes: proactive audit exposed two stable legacy RED runs after hybrid default changed; only the legacy branch disables hybrid while V934 FULL keeps production defaults; legacy and V934 SQLite focused each 1/F0E0S0; source SHA-256 5c6dcd3b4afba4d93a93c1af47cc4484a1dfc9976da92669bfbcc4529ede6155
  - module: model-test-config
    path: foggy-dataset-model/src/test/resources/application-*.yml
    role: five database profiles
    expected_change: update
    notes: non-sensitive identity, deterministic sentinel, driver-aware SQL Server handling
  - module: cache-and-lifecycle-it
    path: addons/foggy-dataset-model-cache/src/test/java
    role: cache lifecycle, Redis and cross-JVM integration
    expected_change: update
    notes: Failsafe ownership and unique JaCoCo IT exec
  - module: runtime-mcp-fsscript-addons
    path: "{foggy-runtime-api,foggy-dataset-mcp,foggy-fsscript,addons}/**/src/test/java"
    role: module integration and 9.3.1-9.3.3 successor regression owners
    expected_change: update
    notes: inventory-driven rename only; no public API/production refactor
  - module: demo-db-infra
    path: foggy-dataset-demo/docker/docker-compose.yml
    role: five database containers
    expected_change: update
    notes: pin approved versions/digests; remove manual-only initialization authority
  - module: demo-db-infra
    path: foggy-dataset-demo/docker
    role: init scripts and sentinel fixtures
    expected_change: update
    notes: idempotent per-DB setup and fixture manifest/hash
  - module: coverage-report
    path: build-support/foggy-coverage-report/pom.xml
    role: build-only JaCoCo aggregate reporter
    expected_change: create
    notes: XML/HTML only; no production classes; no empty-project jacoco:check; not packaged in Launcher
  - module: v934-step4-contract
    path: scripts/v934/step4/{coverage-contract.json,coverage-thresholds.json,coverage-exec-ledger.tsv,coverage-report-amendment.tsv,coverage_runner_lib.sh,coverage_tool.py,coverage_contract_negative_tool.py,coverage_exec_tool.py,coverage_xml_tool.py,coverage_xml_negative_tool.py,toolchain_receipt_tool.py,step2_report_view_tool.py,JaCoCoExecInspector.java,SHA256SUMS}
    role: parent-linked Step 4 policy successor, exact 23-exec ledger, toolchain receipt, runner instrumentation and fail-closed report/provenance verification
    expected_change: create
    notes: Step 1 coverage policy/SHA256SUMS stay immutable; exec/report inventories stay distinct; observed/final thresholds exist only in this successor; exact-51/SHA 348ade..., pre-r4 exact-54/SHA 589a7d..., post-r4 exact-54/SHA ebda814..., database-state-remediation exact-56/SHA be8c4c..., fixture-hardened exact-59/SHA 2a52db..., profile-isolation exact-60/SHA 6056a930..., and r8 lifecycle exact-60/SHA 0c4e6c... are historical identities; current r9-remediation identity is exact-60/SHA 6be72b655b322d89763fc4871c953cd0d4bd5516206964d4cb1f8117b3133376
  - module: v934-step4-unit-mysql57-fixture
    path: scripts/v934/step4/{unit-mysql57-fixture-contract.json,unit_mysql_fixture_tool.py}; scripts/verify-v934-unit.sh
    role: replace the complete Step 4 Unit lane with one run-owned pinned MySQL 5.7 fixture while retaining the frozen Unit execution identities and cardinality
    expected_change: create
    notes: known hidden dependency inventory is 6 reports / 11 testcase nodes, not proof that other Unit tests are DB-free; Step 2 identity/cardinality remains structural only and its Unit correctness green is not reused; temporary classification exception is tracked by docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md
  - module: v934-step4-run-log-lifecycle
    path: scripts/v934/step4/{run_log_lifecycle_lib.sh,run_log_lifecycle_negative_test.sh}
    role: owned FIFO logger close/reap protocol and process-group residue regression authority
    expected_change: create
    notes: Unit/Integration publish durable green only after logger flush/reap; slow, nonzero, timeout, exit, clean-group and persistent-residue cases are expected-negative checks, not all-lane coverage evidence
  - module: v934-step4-formal-evidence
    path: scripts/v934/step4/{coverage_tool.py,coverage_contract_negative_tool.py,coverage_xml_tool.py,coverage_xml_negative_tool.py,coverage-contract.json}
    role: pending-safe diagnostic/freeze/formal state machine and typed canonical evidence replay
    expected_change: update
    notes: success run-status is the final atomic publication; formal replays the real frozen diagnostic under its historical contract/threshold blobs; Cfreeze is one direct single-parent commit and rejects merge, multi-commit, shallow, replace-ref and graft histories
  - module: v934-step4-successor-state-adapters
    path: scripts/v934/step4/successor/{database_state_negative_tool.py,step3_required_report_tool.py}
    role: select the Step 4 successor database authority for state validation, required final verification and report-inventory replay without mutating frozen Step 3 tools
    expected_change: create
    notes: database runner selects the successor state adapter; required runner and report_inventory select the successor required-report adapter; non-state verifier argv stays byte-equivalent and frozen state remains a fail-closed control
  - module: v934-step4-diagnostic-runner
    path: scripts/verify-v934-step4-coverage.sh
    role: single outer orchestration for fresh all-lane diagnostic, toolchain receipt replay, report publication and final evidence binding
    expected_change: create
    notes: r1-r9 remain excluded failed diagnostics; r9 reached all required lanes and 23/48 exec then failed before exec-manifest because all-loaded class names were treated as one identity; production-scope/JaCoCo-ID remediation is implemented, lifecycle semantic validation is reopened, and fresh r10 remains required; partial lanes are non-reusable
  - module: model-coverage-gate
    path: foggy-dataset-model/pom.xml
    role: inherited model coverage gate over externally merged Unit + all-required-IT exec
    expected_change: update
    notes: Step4 profile runs check only, requires an absolute existing exec, preserves bundle 0.77/0.62 and SemanticScaleSqlSupport 1.00/1.00; legacy coverage profile remains intact
  - module: v934-runners
    path: scripts/verify-v934-test-inventory.sh
    role: source/runner/lane inventory and expected-negative authority
    expected_change: create
    notes: test-compile plus pinned JUnit Platform discovery-only plan and POM variants; normalizes reactor dependencies to current class trees; no test/external fixture execution
  - module: v934-runners
    path: scripts/v934/{inventory_tool.py,JUnitDiscoveryInventory.java,inventory-overrides.json}
    role: inventory generation, discovery-only report ownership, reviewed manual classifications and fail-closed validation
    expected_change: create
    notes: wrapper/tool/source/compiled helper tree and Java/Javac/Maven provenance are frozen
  - module: v934-runners
    path: scripts/verify-v934-unit.sh
    role: all-reactor unit lane
    expected_change: create
    notes: fresh XML, exact owning class/count, no IT overlap
  - module: v934-runners
    path: scripts/verify-v934-integration.sh
    role: hermetic and SQLite broad integration lanes
    expected_change: create
    notes: Step 2 hermetic actual pass; Step 3 external manifest is not pre-claimed; Step 4 adds unique exec files
  - module: v934-runners
    path: scripts/verify-v934-database-matrix.sh
    role: five-DB preflight/parity/capability plus required external integration authority
    expected_change: create
    notes: Step 3 correctness without coverage promise; identity/sentinel/fixture before-after; exact reports and skip zero
  - module: v934-runners
    path: scripts/verify-v934-predecessor-regression.sh
    role: current-source successor replay for 9.3.1-9.3.3 criteria
    expected_change: create
    notes: consumes reviewed migration map; never mutates or calls old exact runner as a required pass
  - module: v934-runners
    path: scripts/verify-v934-coverage.sh
    role: collect run-owned execs, verify provenance and parse aggregate XML thresholds
    expected_change: create
    notes: Step 4 invokes freshly instrumented lane runners first; Step 5-7 script never reruns suites; reporter emits XML/HTML and verifier fail-closes counters/checks
  - module: v934-runners
    path: scripts/verify-v934-release-gate.sh
    role: single local/CI release authority and immutable evidence assembly
    expected_change: create
    notes: Step 5 dirty-safe candidate root/pointer only; Step 7 clean authority; source/DB/report/JAR/image/archive hashes
  - module: inherited-assertions
    path: scripts/assert-v933-test-report.sh
    role: proven fresh report assertion
    expected_change: update
    notes: generalize/version without weakening 9.3.3 historical runners
  - module: inherited-authority
    path: scripts/verify-v933-batch7-regression.sh
    role: sealed predecessor runner design
    expected_change: read-only-analysis
    notes: reuse concepts only; do not mutate or require exact replay after FQCN/runner migration
  - module: required-ci
    path: .github/workflows/test-ci-evidence-chain.yml
    role: reusable PR/main full required workflow and stable aggregator
    expected_change: create
    notes: five DB cells upload exact artifacts; collector enforces set/cardinality=5; aggregator rejects failure/skipped/cancelled
  - module: predecessor-ci
    path: .github/workflows/model-lifecycle-concurrency.yml
    role: 9.3.3 minimum gate
    expected_change: update
    notes: retain historical purpose or route into unified gate without double execution
  - module: legacy-release-readiness
    path: .github/workflows/pivot-release-readiness.yml
    role: partial/legacy parity evidence
    expected_change: update
    notes: remove release authority ambiguity; no missing-artifact warn path
  - module: release
    path: .github/workflows/release.yml
    role: tag release, Docker and GitHub assets
    expected_change: update
    notes: same workflow calls full gate and downloads tested JAR/evidence for GitHub and Docker; no rebuild
  - module: release-image
    path: foggy-mcp-launcher/Dockerfile.release
    role: runtime-only official release image from prebuilt tested Launcher JAR
    expected_change: create
    notes: no Maven/source build; COPY dist JAR; CI reads /app/app.jar hash back from image
  - module: legacy-source-image
    path: foggy-mcp-launcher/Dockerfile
    role: current source-building image used by release.yml
    expected_change: update
    notes: remove from release authority or convert to runtime-only; its -DskipTests rebuild must not publish
  - module: odoo-source-image
    path: docker/odoo/Dockerfile
    role: source-building Odoo image outside current official release job
    expected_change: review
    notes: explicitly non-authoritative unless published; any future release must consume the same tested JAR rule
  - module: ci-docs
    path: .github/README.md
    role: required check and evidence operations
    expected_change: update
    notes: exact job/check/artifact/recovery guidance
  - module: project-guidance
    path: CLAUDE.md
    role: root development/test guidance
    expected_change: update
    notes: limit skipTests to explicit diagnostic/compile use; prohibit for PR/main/release authority
  - module: version-docs
    path: docs/9.3.4
    role: single top-level execution/progress/acceptance authority
    expected_change: update
    notes: no per-module document fan-out
  - module: roadmap
    path: docs/9.3.1/roadmap-9.3.1-to-9.4.0.md
    role: authoritative current release sequence
    expected_change: update
    notes: 9.3.4 status only; do not mark 9.3.5 started
  - module: legacy-roadmap
    path: docs/roadmap.md
    role: older 8.x roadmap
    expected_change: update
    notes: add current-authority link; do not rewrite historical content
```

## Step 2 Inventory Result

- implemented: root Surefire/Failsafe 3.5.3 ownership、35 个受控 source rename、
  successor/report tools、Unit/Integration authority runners 与 shared signal-safe helper；
- confirmed successor: `770 positive = 724 Step 2 + 46 Step 3`、`59 structural`；
- actual Step 2: Surefire `677 + 55 structural`，Failsafe `47 + 4 structural`，
  `5,205 testcase / F0/E0/S0`；
- production fixes limited to Mongo auto-configuration ordering、MultiThreadExecutor
  completion 和 immutable calculated-field list；其余为 test/build/documentation
  governance；
- Step 3–7 planned paths remain `expected_change`，不得从本 Step 2 result 推断已实现。

## Step 3 Inventory Result

- implemented authority：`scripts/verify-v934-step3-required-matrix.sh`，
  `scripts/verify-v934-database-matrix.sh`，四个 external lane runner，PreAgg Addon
  lifecycle runner，以及 `scripts/v934/step3/` 下 parent/DB/external/Addon contract、
  report tool、state-negative tool 与 shared launcher/context；
- implemented database/fixture surface：five-DB run-scoped compose/provision/cleanup，
  native DATE fixtures，SQLite role-aware binding，MySQL final-init readiness marker，
  identity/sentinel/parity/report/resource-state verifiers；
- implemented PreAgg surface：Addon `PreAggPhysicalColumnContract`、shared strict-DAY
  `PreAggWatermarkResolver` 与 explicit query mapping contract，另含 DDL/refresh builder、
  success-only runtime watermark publication、matcher/hybrid rewriter、scheduler/service
  synchronization/state snapshots、SQLite/MySQL57 lifecycle IT；
- implemented Step 4 prerequisite：`MultiThreadExecutor` debug logger 与 shutdown=false、
  error propagation、waiter interrupt deterministic tests；
- actual result：tested commit
  `ce3d70c391c7b8bd8046fe66dde0ad568d66601e`，run
  `step3-required-20260716-final-r4`，DB `29/370` + external `16/76` = exact
  `45/446/F0E0S0`，DB state `18/18`，Redis state `4/4`，Addon companion `2/6`；
- explicit boundary：Addon `2/6` 不计入 45/446；optional LLM reviewed/excluded；
  runtime watermark 非持久化；Mongo production JDBC dialect 解耦未宣称完成；
- Step 4 coverage 仅进入 diagnostic-ready；Step 5–7 的 single release runner、workflow、
  runtime-only image 与 version authority paths 仍保持 planned/not-started，不得从
  Step 3 result 或 Step 4 静态库存推断已通过。

## Step 4 Diagnostic / Fix Inventory Result

- state：`in-progress / r9 coverage-report excluded / exec-scope remediation implemented /
  lifecycle semantic remediation implemented / quality passed / commit pending /
  fresh r10 pending`，不是
  `passed`；
- frozen structure：`23 exec / 48 sessions`；required report overlay=
  `773 positive + 59 structural / 5,707 testcase / F0E0S0`，Addon companion 独立为
  `2/6`；
- fail-closed static evidence：contract/source identity=`21/21 + 22/22`、effective POM=
  `4/4`、toolchain receipt=`5/5`、report inventory=`30/30`；Unit negative receipt=
  `36/36`，其中原 fixture/manifest schema/tamper=`20/20`、connection receipt typed=
  `7/7`、atomic publisher=`3/3`、profile isolation=`6/6`，另有 negative receipt schema
  tamper=`4/4`；真实 fixture
  lifecycle=`5/5`；Step 2 derived view=`12/12`、successor overlay=`12/12`、XML=`68/68`、
  logger=`9 类 / 14 case`；
- build regression：根 Surefire/Failsafe 将共享参数从 `${argLine}` 改为
  `@{argLine}` late evaluation，修复 legacy coverage 无 exec 却 BUILD SUCCESS；三态
  focused 验证已完成，`coverage_tool.py` + manifest + `validate-contract` +
  `8/8` negatives 已自动保护 canonical 形式，见
  `docs/9.3.4/workitems/BUG-step4-legacy-coverage-argline-fail-open.md`；
- toolchain identity：receipt 绑定 Step 1 raw 工具版本、compiler realm ASM `9.6`、
  JaCoCo realm ASM `9.7`、test classpath ASM `9.7.1`，并核对 24 个 production
  module effective compiler；
- report successor：`coverage-report-amendment.tsv`=
  `12 rows / 4 new + 8 changed`，SHA-256=
  `998ae49927721576c26327b8477010b0238843565e6afdbc70987e97544a028c`；declared
  amendments=`18`，SHA-256=
  `8e21b8527f290061361ef0b8fbf084d51b2536ef479e1f70e45488f996090bfc`；required total
  保持 `773/59/5707`；
- Unit hermetic fixture：`unit_mysql_fixture_tool.py` 按
  `scripts/v934/step4/unit-mysql57-fixture-contract.json` 管理 pinned/run-owned MySQL 5.7、
  closed Unit Maven observation window 内的 restricted non-super exclusive connection
  receipt、`M_ETL_TEST` schema before/after seal、
  resource cleanup、typed/static negatives 与 5 个真实 lifecycle probes；Unit summary 和
  report inventory 绑定 fixture manifest、negative receipt 与 lifecycle receipt，report
  inventory=`30/30`；
- datasource profile adapter：`foggy-dataset/src/test/resources/application.yml` 以
  `V934_UNIT_MYSQL57_URL/USERNAME/PASSWORD` placeholder 仅把 run-owned credential 注入
  `foggy-dataset` test context；其他模块的 SQLite/显式 profile 保持不变。outer runner 与
  callback 双层拒绝 underscore/dotted/hyphen 的 Spring/custom key、global datasource
  override 与 `@argfile`、`VMOptionsFile`、`javaagent/agentlib/agentpath` 间接注入；fixture
  contract 绑定 adapter exact hash，并以 scrubbed Git environment、`HEAD` tree、no-replace
  object inventory 要求三个 key 在 tracked resources 中只有该 consumer；
- connection observation：root 配置 `init_connect` 后启动 Maven；Maven 返回后由同一 root
  batch 先 disable `init_connect`、再按 `connection_id` SELECT。receipt 保存有序
  `connection_id + observed user`，窗口内所有 non-super 连接必须为 `v934_unit`；callback
  返回后的 provisioner `foggy` 控制面连接明确在窗口外；
- classification boundary：该 fixture 替换完整 Unit lane并保持唯一 Maven invocation 与
  `681+55/4,941`；`6 reports / 11 testcase nodes` 只是已知隐藏依赖清单。Step 2
  identity/cardinality 只保留结构含义，其 Unit 正确性绿色不复用；迁移关闭条件见
  `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`；
- publication boundary：profile isolation hardening 最终静态 identity 为 top manifest=
  `60/60` / `6056a930a1d0deec59767ffc0239485ae42b4067e343c0d68e3f899c3440e587`、
  successor=`14/14` /
  `acb580e92a72eb407f31f5d6f9a8139a3509f3a0bfbf58537922465f4086a112`；
  fixture contract/tool/Unit runner/report inventory tool SHA-256=
  `7aa1e21aef85b51a13aacc8c134a1c363c595deffbfb3acf6aafdb942519b53a` /
  `cc19390ce6c0cfb307b7632dbe4e25540b1e4d49d11ec1512739f6724646d345` /
  `45536c0a969731f6b7c87acecdb225b13a8a0fca45a9a04c9cdfb2173fc60c66` /
  `7366fb0d5f56ede1bfb8697bf71c935ad6fd6600db2ddeec56aba6b87a38b5b4`；
  diagnostic/formal contract、coverage tool、overlay contract/tool、adapter SHA-256=
  `c062219a6335ae41330c6d5924d6fce60941c5d168b361081fbb41df77428477` /
  `341991d6b5a15d19cdb9e0de70a8cc6ace29480227596c413c07e6bf7fdbc73d` /
  `27afd37350fa7f1646fba4be59791ec6bdec94fe57e0cdfecc2a08e0f43f2f18` /
  `84d09bfc333bb40d8ef830979734933717555845cebe9943f70ff7087a9a482d` /
  `1fea2816504519b7e7f1dc6839744ee943a9a4bf3feb783375e21e935da63d31` /
  `9500cd4d50930b121a36798857cd0a1cc0c8b2190b0a2fc9ad0ea464394bb256`；
- diagnostic r1：clean/pushed HEAD=`bc100b0f63bd3ff62d1105611dae41741790aedd`，run=
  `step4-coverage-20260716-diagnostic-r1`，child-unit=`3115/1/0/0`，正确 fail closed；
  wrong-table corruption 与 raw-vs-raw/nullable-empty 缺陷见
  `docs/9.3.4/workitems/BUG-step4-preagg-validation-wrong-table.md`；
- fix inventory：只修改 `PreAggregationDataValidationTest`，focused=`9/F0E0S0`、组合=
  `57/F0E0S0`；snapshot 路由=`daily_product_sales/daily_product_sales/
  daily_customer_channel_sales`，corruption 路由=`monthly_category_sales`、diff=`1000.00`；
  source SHA-256=
  `affb6e415c1770eae7c9ab6f3505ac67acfe03eac1461bd2a48addf3ee790623`；
- diagnostic r2：clean/pushed HEAD=`0101a44a07784bf6b484d490c7fb508727fbab70`，Unit=
  `681 execution + 55 structural / 4,941 testcase / F0E0S0`；Integration=
  `312/F1E0S0`，唯一失败为 `PreAggregationL2CacheIT`；outer 在
  `child-integration` fail closed，后续 required lanes 与 aggregate/threshold 未执行；
- L2 fix inventory：snapshot-only fixture 显式 hybrid=false，exact name/table、raw negative、
  lookup/write/hit identity；focused=`1/F0E0S0`、组合=`30/F0E0S0`，source SHA-256=
  `bb5d6884401579447382587861e197feac2884ab701065d7826fe7a676e0c313`；
- Pivot fix inventory：legacy fallback 两次稳定 RED；仅 legacy 分支关闭 hybrid，V934 FULL
  保持 production 默认；legacy/V934 SQLite 各 `1/F0E0S0`，source SHA-256=
  `5c6dcd3b4afba4d93a93c1af47cc4484a1dfc9976da92669bfbcc4529ede6155`；
- static result：contract/source/XML/logger/view/successor/DB/external negatives=
  `20/22/63/14/12/12/14/12`，positives coverage=
  `773/59/5707`、Step 2=`724/59`、DB=`7/29/370`、overlay required=`45/446`、Addon=
  `2/6`，全部通过；
- diagnostic r3：clean/pushed HEAD=`e16693297239f2a861f3b93b3de60c1bb783bda0`，Unit=
  `681 positive + 55 structural / 4,941 testcase / F0E0S0`；outer 在 `child-unit`
  leader 返回后检测到 live PGID member 并 fail closed，后续 lane 与
  aggregate/threshold 未执行；immutable failed evidence=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md`；
- r3 bug inventory：`docs/9.3.4/workitems/BUG-step4-child-run-log-tee-residue-race.md`
  记录 Unit/Integration 未管理 process-substitution `tee` 的 close/wait 竞态；受控复现
  证明显式拥有 FIFO logger、关写端并 wait logger 后不再先返回 leader；
- managed lifecycle inventory：`run_log_lifecycle_lib.sh` 被 Unit/Integration 共用，
  `run_log_lifecycle_negative_test.sh` 覆盖 slow/nonzero/timeout/exit/clean-group/
  persistent-residue；child ready/completion 回执绑定 PID/PGID/SID/starttime/
  boot-id、schema/mode/link/inode/hash，真实残留在 kill 前留 bytes-safe member snapshot；
- typed XML/formal inventory：`coverage_xml_negative_tool.py` 与扩展后的
  `coverage_xml_tool.py`/`coverage_tool.py` 类型化验证 child lifecycle、
  formalization delta 与 canonical gate/candidate/final/status path；success status 是全部
  证据通过后最后一次原子发布，不允许 post-green verification；
- frozen replay inventory：formal 从 confirmed threshold 定位真实 diagnostic run，
  在 diagnostic commit 上取回历史 threshold/contract blob 并重算 run evidence；
  拒绝 synthetic run，且 `Cfreeze` 必须是 diagnostic commit 的唯一直接
  single-parent child，拒绝 merge/multi-commit/shallow/replace/graft；
- diagnostic r4：reported launch head=
  `ceea084ca25a9d679ba128e3f6bd50a63322c112`，run=
  `step4-coverage-20260716-diagnostic-r4`；outer 在 `source-before` 以 exit code `2`
  fail closed，run-owned Git/source seal、所有 lane、aggregate/threshold/summary 均 absent；
  record=`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r4-fail-closed-20260716.md`；
- source-policy inventory：exact HEAD/index path+Git mode+blob 与安全 worktree permission/
  content 分层验证；关闭 fsmonitor/untracked-cache，绑定 ordinary index flags，新增安全
  file-mode mapping positives 与 world-write/special-bit/hardlink 等 negatives；tracked FIFO
  在任何 worktree-aware Git 读取前 preflight fail-fast；before/after raw stat identity 比较拒绝
  Git-clean-equivalent concurrent rewrite；
- clean-equivalence inventory：source seal 清除 ambient/global Git clean 配置并显式复算
  raw 与 CRLF-input 两个 candidate，使真实 CRLF worktree 在 HEAD/index clean-equivalent 时
  通过；HEAD-fixed attributes 若声明 external clean filter，则在任何 worktree-aware Git
  hash/driver hook 执行前 fail closed，negative 证明 hook 未执行；
- diagnostic r5 inventory：clean/pushed tested commit=
  `a35b99cb08f42817d8e75c440f18910b6961841b`，run-owned source seal 已发布；Unit=
  `681+55/4,941/F0E0S0`、Integration=`47+4/320/F0E0S0`、Addon=`2/6/F0E0S0`
  后，database-state companion 因 frozen authority selector 以 `E_AUTHORITY_MANIFEST`
  fail closed。database cells、external、aggregate、threshold 和 summary absent，r5 及其
  partial lanes 均 excluded/non-reusable；
- successor adapter inventory：`database_state_negative_tool.py` 选择 successor DB contract；
  `step3_required_report_tool.py` 只重写 frozen state verifier argv；database runner、required
  runner 与 report_inventory selector 均绑定适配器；frozen Step 3 字节不变。record=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r5-fail-closed-20260716.md`，BUG=
  `docs/9.3.4/workitems/BUG-step4-database-state-successor-authority-manifest.md`；
- diagnostic r6/r7 inventory：r6 因 repo demo MySQL 占用 frozen port fail closed；端口释放后
  r7 在 Unit 暴露 6 suites / 11 errors，证明旧绿色隐式依赖 ambient MySQL/schema。两轮均
  excluded/non-reusable；r7 evidence/BUG=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r7-unit-hidden-mysql-fail-closed-20260716.md` /
  `docs/9.3.4/workitems/BUG-step4-unit-hidden-mysql-environment-dependency.md`；
- r7 remediation inventory：完整 Unit lane 由 run-owned MySQL 5.7 替换；machine contract=
  `scripts/v934/step4/unit-mysql57-fixture-contract.json`，classification debt=
  `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`；focused/static
  negatives、connection receipt 与真实 lifecycle 已有证据，但尚未形成 fresh all-lane r8；
- Unit remediation r2 inventory：run=`step4-unit-fixture-quality-20260716-r2`，commit=
  `a603f839a98d99b2d7beb8379f76b4d85539328c`，source=`3,981` /
  `087d074f3497aff3fe305806f82b4d62ff41cdd4d3b26556e58f034138b14c2c`；lifecycle=`5/5`
  后 `foggy-dataset-model=3,115/F0E631S0`，首因是 global MySQL URL 与 SQLite driver
  冲突。final manifest/summary absent；child=`unit-mysql57-90da4977dc197f81` cleanup=
  `0/0/0`、port free，r2 excluded/non-reusable。record=
  `docs/9.3.4/evidence/step-4/step4-unit-profile-isolation-r2-fail-closed-20260716.md`；
- Unit remediation r3 inventory：run=`step4-unit-fixture-quality-20260716-r3`，commit=
  `50161a0a869430e353f3933d9bb00dda59d9c4b1`，source before=after=`3,982` /
  `1db06cc18bb86c288ffa79e7094e3b9e509d866ddc23b6c93bcaaa0422b99eb2`；唯一
  Surefire invocation=`681+55=736 raw reports / 4,941 / F0E0S0`。fixture negatives=
  `36/36`、receipt schema/tamper=`4/4`；closed receipt 内 `18/18` connections 全部为
  `v934_unit`；真实 lifecycle=`5/5`，run-owned cleanup=`0/0/0` 且 port free。evidence
  window 外 demo MySQL 已按 exact ID
  `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166`
  恢复为 `running/healthy`。record=
  `docs/9.3.4/evidence/step-4/step4-unit-fixture-quality-r3-pass-20260717.md`；
- r8 inventory：run=`step4-coverage-20260716-diagnostic-r8`，reported launch HEAD=
  `3a3dd21a9aa956f0bedfffcf648ebb1cac0b756a`；contract/overlay/authority/coverage negatives
  通过后，stale Unit direct-trap static token 在 `bootstrap-negative` 拒绝 fixture-aware
  wrapper。run-owned Git/source identity、lane、aggregate、threshold 与 summary absent，
  cleanup=`0/0/0`，r8 excluded/non-reusable；record/BUG=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r8-bootstrap-negative-contract-drift-fail-closed-20260717.md` /
  `docs/9.3.4/workitems/BUG-step4-unit-lifecycle-static-contract-drift.md`；
- lifecycle remediation inventory：executable-line Unit/Integration contracts、critical
  slices、canonical references 与 raw-byte runner seals 已关闭 comment/quoted heredoc、
  EXIT/0、early return、shadow、false/subshell、source/eval 与 CRLF drift；dynamic=
  `9 类 / 14 case`，Unit shape/seal=`13+3`，Integration=`11+5`；tool/top=
  `8dcc679c2762ff8908b3bc26e8dfb0553a083eb75003dd80366fd82e78d8ed9b` /
  `0c4e6c18f4af0a2c35418604ce80d20828ceb4c275375b7d12461b4683553a81`；两路独立质量
  B/H/M/L=`0/0/0/0`；
- r8 时点 evidence boundary：threshold 仍为 `diagnostic-pending`，r1–r8 与 Unit remediation r2 均未
  生成 all-lane aggregate baseline/review 或 Step 4 exit evidence；r3 只通过 Unit
  remediation subgate；focused lifecycle 只通过 bootstrap contract remediation。正式质量
  B/H/M/L=`0/0/0/0`。该时点 closure commit/push/clean HEAD 和 fresh r9 仍 required；
  `can_enter_coverage_audit=no`，Step 5、formal、coverage audit 与 acceptance 仍关闭。

### Superseding r9 remediation inventory

- r9 immutable record/BUG：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r9-exec-class-scope-fail-closed-20260717.md`、
  `docs/9.3.4/workitems/BUG-step4-exec-class-id-scope-drift.md`；r9 required inventory=
  `773+59/5,707/F0E0S0`、Addon=`2/6`、exec=`23/48`，但 exec-manifest/aggregate/
  source-after/summary absent，excluded/non-reusable；
- modified exec identity path：
  `scripts/v934/step4/{coverage-contract.json,coverage_tool.py,coverage_contract_negative_tool.py,coverage_exec_tool.py,coverage_xml_tool.py,SHA256SUMS}`；
  production scope=`frozen-24-module-production-class-universe`，aggregate identity=
  JaCoCo class ID exact union；
- successor bindings：
  `scripts/v934/step4/successor/{overlay-contract.json,overlay_tool.py,SHA256SUMS}` 同步
  diagnostic/formal dual hashes 与 coverage tool identity；
- focused evidence：r9 raw exec read-only=`16,939 IDs / 135 same-name multi-ID / 0
  production conflict`；exec=`17/17`、contract=`21/21`、XML=`68/68`、overlay=`12/12`；
- newly reopened lifecycle path：
  `scripts/v934/step4/run_log_lifecycle_negative_test.sh`；workitem=
  `docs/9.3.4/workitems/BUG-step4-lifecycle-semantic-validator-bypass.md`。原 r8 lifecycle
  quality 的 B/H/M/L=0 结论被本次可复现 bypass supersede；coded executable-stream
  regression 已通过，Unit/Integration shape=`16/16 + 14/14`、semantic stream=
  `2/2 + 5/5`、raw seal=`2/2`、outer/library=`3/3 + 3/3`；三路独立正式复核最终
  B/H/M/L=`0/0/0/0`；
- 当前 evidence boundary：r1–r9 均不能作为 threshold/formal/Step 4 exit；fresh r10 pending，
  `can_enter_coverage_audit=no`，Step 5、formal、coverage audit 与 acceptance 关闭。

## Protected Boundaries

- `docs/9.3.3/evidence/**` and existing `target/v933-*` are historical authority；
  不覆盖或重算为 9.3.4 pass。
- production lifecycle/query/cache implementation默认 do-not-touch；只有测试治理
  确认 BUG 并建立 workitem 后可最小修改。
- existing uncommitted 9.3.1–9.3.3 worktree remains user-owned until an explicit
  clean-commit transition is prepared；不得清理以满足 authority。
- plugin versions, image digests, inventory counts and coverage thresholds must
  be frozen by evidence, not copied from this planning document without Step 1 review。
