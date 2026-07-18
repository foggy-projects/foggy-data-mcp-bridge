---
doc_role: code-inventory
doc_purpose: Record planned 9.3.4 code touchpoints and protected boundaries.
version: 9.3.4
status: in-progress
created_at: 2026-07-14
updated_at: 2026-07-19
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
    notes: proactive audit exposed two stable legacy RED runs after hybrid default changed; only the legacy branch disables hybrid while V934 FULL keeps production defaults; r18 follow-up adds deterministic NULL-axis baseline/tree semantics inside the same S12 node; current source SHA-256 18ebeedf8d79a84d5ba59ff991aeffeefea73cbb8aa51c6c10444ed64ba6d325; @Test remains 23
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
    notes: Step 1 coverage policy/SHA256SUMS stay immutable; exec/report inventories stay distinct; observed/final thresholds exist only in this successor; prior exact identities remain historical; current r11 binding-remediation identity is exact-60/SHA a9b105ce2f8f640dfa09863e797697bcf9892a7b0fa68b38f83b5bbd7435afb4
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
    notes: r1-r11 remain excluded failed diagnostics; r11 stopped at bootstrap-negative with E_SOURCE_SEAL before source seal and every lane; early four-way binding and raw/semantic negatives are implemented, formal quality passed, and commit/push remains required before fresh r12; partial evidence is non-reusable
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
- 在 Step 3 exit 时点，Step 4 coverage 仅进入 diagnostic-ready；Step 5–7 的 single release runner、workflow、
  runtime-only image 与 version authority paths 仍保持 planned/not-started，不得从
  Step 3 result 或 Step 4 静态库存推断已通过。

## Step 4 Diagnostic / Fix Inventory Result

- state：`in-progress / diagnostic-r18 PASS / threshold candidate not authorized / Pivot NULL-axis
  remediation verified / pre-Cdiag quality PASS / replacement Cdiag pending`，不是 `passed`；
- frozen structure：`23 exec / 48 sessions`；required report overlay=
  `773 positive + 59 structural / 5,707 testcase / F0E0S0`，Addon companion 独立为
  `2/6`；
- historical r16 preflight evidence（已被 formal-r3 supersede）：contract mutations=`27/27`、source/Git identity=`22/22`、
  threshold/frozen replay=`12/12`、XML negatives=`118/118`、successor overlay negatives=`12/12`、
  toolchain seal/negative=`5/5`、Step 2 view=`728+59/5261`、class universe=
  `24 modules / 2,098 classes`、Unit fixture lifecycle negatives=`5/5`；r16 在单一 authority
  window 内重新产生完整证据，历史 bootstrap 的 contract/XML=`21/21`、`68/68` 与 r15 partial
  report inventory/fixture receipts 均未拼入 r16；
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
- r9 时点 evidence boundary：r1–r9 均不能作为 threshold/formal/Step 4 exit；fresh r10 pending，
  `can_enter_coverage_audit=no`，Step 5、formal、coverage audit 与 acceptance 关闭。

### Superseding r10 remediation inventory

- r10 immutable record/BUG：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r10-sensitive-scan-fail-closed-20260717.md`、
  `docs/9.3.4/workitems/BUG-step4-sensitive-scan-authorization-context-false-positive.md`；
  required=`773+59/5,707/F0E0S0`、Addon=`2/6`、exec=`23/48/16,947 IDs`、aggregate 与
  observation 已生成，但 sensitive/summary receipt absent，excluded/non-reusable；
- modified producer：
  `foggy-mcp-launcher/src/main/java/com/foggyframework/mcp/launcher/DemoSecurityIdentityResolver.java`，
  仅把 credential-shaped label 改成明确的 demo identity result；source SHA-256=
  `5372ba04a92334e75ad1b4628ccbfe36063c29e90fba34aa9f8a22e9baf60a7d`；
- modified outer：`scripts/verify-v934-step4-coverage.sh`，五条 pattern 原样上移为唯一数组；
  bootstrap 内存 probes=`7 dangerous + 3 safe`，`rg rc>1` 双路径 fail closed，不落 run root；
  SHA-256=`57f5da9a23c4973beef54a6bfd303c3dfd38fccb03a7bc2cadadbfaa3206f649`；
- identity：top Step 4 manifest=`60/60` / SHA-256=
  `b2f02f142ee78fc5abff5a307a8403861e3e213f09d68e037f7b7eb2df2054c5`；successor
  manifest/overlay bindings不变；
- focused evidence：launcher request smoke=`1/F0E0S0`，bash syntax、contract=`21/21`、
  overlay=`12/12`、manifest exact 与 diff check 通过；
- r10 closure 时点 evidence boundary：r1–r10 均不能作为 threshold/formal/Step 4 exit；两路
  post-fix formal quality B/H/M/L=`0/0/0/0`，commit/push/clean HEAD 与 fresh r11 pending；
  `can_enter_coverage_audit=no`，Step 5、formal、
  coverage audit 与 acceptance 关闭。

### Superseding r11 remediation inventory

- r11 immutable record/BUG：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r11-outer-source-seal-fail-closed-20260717.md`、
  `docs/9.3.4/workitems/BUG-step4-outer-runner-source-seal-binding-drift.md`；launch HEAD=
  `141592ca9f4219d87a018774ee607b09a8e5a8a1`，`bootstrap-negative / E_SOURCE_SEAL /
  exit 1`，历史 outer actual/frozen raw=
  `57f5da9a23c4973beef54a6bfd303c3dfd38fccb03a7bc2cadadbfaa3206f649` /
  `02a920d91d1b8792cad47d65ce860352a8e9ecf39106f4489a714df01888dbaa`；零 lane，
  excluded/non-reusable；
- modified outer：`scripts/verify-v934-step4-coverage.sh`；在 run-root/source-seal/lane 前调用
  early binding preflight，当前 raw SHA-256=
  `90b4b979e55c17243644cce186767a4647ce79c85b431adcb415bddd18cc1cec`，executable-stream
  SHA-256=`065211912aab5227125ef02f40e2965fce7ff5060df5c7b91a902c4ad4f34cae`；
- modified lifecycle authority：`scripts/v934/step4/run_log_lifecycle_negative_test.sh`；独立
  `UNIT_RUNNER_SHA256`、`INTEGRATION_RUNNER_SHA256`、`OUTER_RUNNER_SHA256`、
  `LIBRARY_SHA256` 与 canonical raw bytes/top manifest rows 做 four-way exact binding，失败码=
  `E_SOURCE_SEAL_BINDING`；tool SHA-256=
  `61bf7b990bdef6e0d75c53010644bcc6d1525a67119cd36c5f82eeb911e005fc`；
- preflight strict reader：全部四类 input 以 `O_NOFOLLOW` descriptor open、`fstat`、fd read、
  post-`lstat` stable identity 关闭 path-check/read TOCTOU；安全复核的 Medium 已修，最终
  post-fix implementation review 与 docs/status review B/H/M/L=`0/0/0/0`；
- binding regressions：canonical positive=`1`；outer+manifest refresh/nested stale、outer-only
  drift、valid-64 nested-only wrong、missing、duplicate、invalid-format constant negatives=
  `6`；outer raw CRLF 与 executable no-op 两个 negatives 分别触达
  raw/semantic seal；focused lifecycle suite=`PASS`；
- identity/evidence：top manifest=`60/60` / SHA-256=
  `a9b105ce2f8f640dfa09863e797697bcf9892a7b0fa68b38f83b5bbd7435afb4`；successor=
  `14/14`，coverage contract=`21/21`，overlay=`12/12`；
- 当前 evidence boundary：r1–r11 均不能作为 threshold/formal/Step 4 exit；r11 remediation
  quality passed，但 commit/push/clean HEAD 与 fresh r12 pending；
  `can_enter_coverage_audit=no`，Step 5、threshold freeze、formal coverage run、coverage audit
  与 acceptance 关闭。

### Superseding r12 threshold/coverage remediation inventory

- immutable diagnostic：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r12-pass-with-threshold-gaps-20260717.md`；
  run=`step4-coverage-20260717-diagnostic-r12`，tested commit=
  `05351ecab0d7fc43d12dfa307ffecf81feb41539`，required=`773+59/5,707/F0E0S0`、
  Addon=`2/6`、exec=`23/48/16,935`、aggregate line=`54,478/76,830`、branch=
  `25,980/44,870`、sensitive passed、cleanup=`0/0/0`；
- workitem：
  `docs/9.3.4/workitems/BUG-step4-threshold-freeze-observation-applicability-gap.md`；
  r12 below-floor=`9`，structural N/A=`1`，candidate absent，r12 不可直接 freeze；
- threshold tooling：`scripts/v934/step4/{coverage-contract.json,coverage_tool.py,
  coverage_contract_negative_tool.py,coverage_xml_tool.py,coverage_xml_negative_tool.py,SHA256SUMS}`；
  exact enriched row/metric、唯一 N/A policy、strict JSON identity、frozen replay receipt schema
  与 formal replay one-call binding 均纳入 machine contract；
- test amendments：cache 三类、core WatchService、dataset lifecycle 三类、runtime resolver
  共八份既有 test source，覆盖九个 critical production classes；不增加 report/testcase node，
  focused=`136/F0E0S0`；dataset protected-tree 三项通过 successor exact amendment 声明；
- successor inventory：`scripts/v934/step4/successor/{declared-amendments.tsv,
  database-matrix-protected-trees.tsv,database-matrix-contract.json,
  step3-required-contract.json,overlay-contract.json,overlay_tool.py,SHA256SUMS}`；declared
  amendments=`21`，database matrix expected report/source amendment cardinality不变；
- focused gates：XML=`118/118`、contract mutations=`27/27`、threshold/frozen replay=
  `12/12`、overlay=`12/12`、top manifest=`60/60`、successor=`14/14`；
- precommit exact-type audit：`BUG-step4-evidence-json-numeric-type-alias-bypass.md` 记录
  child-lifecycle recursive identity、三处 provenance size、threshold schema 与 aggregate
  ratio 的 bool/int/float alias；9 项新增 negatives 已纳入 XML=`118/118`；
- 当前 boundary：remediation precommit quality 已通过，B/H/M/L=`0/0/0/0`；
  commit/push 与 fresh r13 pending。r13 前 threshold=`diagnostic-pending`、
  `can_enter_coverage_audit=no`，Step 5/formal/audit/acceptance 关闭。

### Historical r13 diagnostic / committed Cfreeze inventory

- fresh diagnostic：run=`step4-coverage-20260717-diagnostic-r13`，tested commit=
  `b76552e21479c75111f648a4aa678abe018cc3f9`；sealed required=
  `773 positive + 59 structural / 5,707 testcase / F0E0S0`、Addon=`2/6`、exec=
  `23/48`、critical below-floor=`0`、structural N/A=`1`、sensitive scan=`passed`、
  cleanup container/volume/network=`0/0/0`；
- applicability identity：唯一 N/A 为
  `com.foggyframework.dataset.db.model.spi.NamespaceScope / foggy-dataset-model / branch`，
  reviewed threshold 必须保留 exact `observed={covered:0,total:0,fraction:null}` 与
  `minimum=null`；所有其他 critical line/branch denominator 保持 positive；
- reviewed candidate：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r13-threshold-candidate-20260717.json`，
  SHA-256=`8bb47382444fd66893d250a8787416c9ce73f9590be4c66308fb7a2e3e014d00`，public
  verification=`passed`；candidate 本身保持 `review-required`，不得冒充 canonical confirmed
  threshold；
- Cfreeze exact machine delta 仅为
  `scripts/v934/step4/{coverage-thresholds.json,coverage-contract.json,SHA256SUMS}` 加
  `docs/9.3.4/**` allowlist writeback；已提交并 push 为
  `86d505810524383da6211bcc2a7965e9a4afb34e`，其唯一直接 parent 为
  `b76552e21479c75111f648a4aa678abe018cc3f9`。threshold 当时转为
  `confirmed`（SHA-256=`0cfc6765eda1aa8a5209e46bf668136ee1786c4761d66a07262ac3557e7227cb`）、
  contract/publication 转为 `formal-ready`（SHA-256=
  `6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`）；manifest=`60/60`、
  commit/direct-parent/formal-delta proof 均已完成；
- historical boundary：r13、verified candidate 与 Cfreeze formal-ready transition 原只放行
  fresh formal；formal-r1 已执行并 fail closed，该 boundary 不再是 current。
- new deterministic test delta：
  `foggy-core/src/test/java/com/foggyframework/core/utils/file/WatchServiceFileTracerTest.java`
  在既有 `testWatchServiceAvailable` 内反射创建 isolated tracer，显式 shutdown，并证明
  singleton 不受污染；没有新增 testcase/report identity。
- historical boundary：formal-r1 immutable failed；pending machine trio 当时恢复为 b765 exact blobs，
  只放行新 Cdiag 与 fresh diagnostic。`can_enter_coverage_audit=no`、
  `can_enter_acceptance=no`；formal 与 post-formal final quality pending，Step 4/Step 5、coverage
  audit、acceptance 均未通过；后续已由 r14/Cfreeze/formal-r2 inventory supersede。

### r14 diagnostic / second Cfreeze inventory

- diagnostic evidence：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r14-pass-20260717.md`；run=
  `step4-coverage-20260717-diagnostic-r14`，tested Cdiag=
  `322bb346cca19998a90d6d990505ef033f3a496a`，required=`773+59/5707/F0E0S0`、
  Addon=`2/6`、exec=`23/48`、class universe=`24/2098`、cleanup=`0/0/0`；
- immutable candidate：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r14-threshold-candidate-20260717.json`，
  SHA-256=`9087774387f0bb4b177a1b5f2fe28a4102e0434afe7a5b316aa106511c9e6d55`；
- review：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r14-threshold-review-20260717.md`，
  SHA-256=`666f97d2eadef4dde43a575dba5793243e5674de1f09038c5ba8b760e8e4c680`，two independent
  reviews PASS，combined B/H/M/L=`0/0/0/1`；
- mandatory machine delta：
  `scripts/v934/step4/coverage-thresholds.json` SHA-256=
  `04544480ef73df4bfcba4ddb1d0323b8314fbb4a6934eae5eae51bb2a958486e`、
  `coverage-contract.json` SHA-256=
  `6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`、
  `SHA256SUMS` SHA-256=`915bf603c2cb04766143d73f0a2e81ab1a30863506fc194169d07dc06db173e3`；
- validators：manifest=`60/60`、full contract=`formal/confirmed/passed`、frozen diagnostic and
  successor overlay PASS；
- historical boundary：single direct-child Cfreeze=`1901a10138bac06a09b875c907b7aea6e2789b04`
  已 commit/push/clean；其 fresh formal-r2 随后 fail closed，由下方 inventory supersede。

### formal-r2 failure / ListPreset deterministic recovery inventory

- immutable formal evidence：
  `docs/9.3.4/evidence/step-4/step4-coverage-formal-r2-list-preset-branch-order-fail-closed-20260717.md`；
  run=`step4-coverage-20260717-formal-r2`，tested Cfreeze=
  `1901a10138bac06a09b875c907b7aea6e2789b04`，required=`773+59/5707/F0E0S0`、
  Addon=`2/6`、exec=`23/48`、class universe=`24/2098`、cleanup=`0/0/0`、sensitive PASS；
- gate delta：aggregate line exact `54622/76830`，branch=`26105/44870`，比 confirmed
  `26106/44870` 少 `1`；12 critical exact，below-floor=`0`，唯一 N/A=
  `NamespaceScope.branch`；
- unique class delta：
  `addons/foggy-data-viewer/.../FileSystemListPresetStore` line=`69/88` 不变，branch=
  `18/26 -> 17/26`；Unit `jacoco-ut.exec` class ID=`d1bd017e92baa090` 只缺 probe 106；
- root cause：`Files.find(...).findFirst()` 对 UUID 文件的遍历顺序未定义，导致 filename-false
  branch 是否在 short-circuit 前执行不稳定；workitem=
  `docs/9.3.4/workitems/BUG-step4-list-preset-files-find-coverage-order.md`；
- deterministic test delta：只在既有
  `ListPresetServiceTest.FileStoreTests#shouldIsolatePresetByUserAndBusinessKey` 增加 nonexistent-ID
  empty assertion；无 production、runner、floor、critical-set、exclusion 或 testcase-cardinality
  变化；focused 5/5 probe106 hit、bitmap unique=`1`，Data Viewer=`104/F0E0S0`；
- historical recovery machine inventory：threshold=`diagnostic-pending` SHA-256=
  `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96`，contract=
  `diagnostic-ready` SHA-256=`15dae282395d920ffb3d2aae4c518f0d1c8be09aaed8b08e40044f9d9bc6b0b0`，
  manifest=`60/60`；
- historical boundary completed through Cdiag `9270d2d4…` and r15；the r15 inventory below
  supersedes its pending sequence。

### r15 Unit timing-oracle failure / deterministic recovery inventory

- prior recovery Cdiag=`9270d2d4e58684226aeb15eff55b027e6aa4a7eb` 已 commit/push/clean；immutable
  diagnostic=`step4-coverage-20260717-diagnostic-r15` 在 `child-unit` exit 1；record=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r15-bean2map-timing-oracle-fail-closed-20260717.md`；
- partial inventory：26 XML reports=`124/F1E0S0`，仅 `jacoco-ut.exec`=`2/48 sessions`；
  source-after、final sensitive scan、report/exec inventories、aggregate、observation、candidate、
  summary/gate absent，不得跨 run 复用；
- test inventory delta：
  `foggy-bean-copy/src/test/java/com/foggyframework/bean/copy/utils/Bean2MapUtilsTest.java`
  只修改既有 `testCachingMechanism` 与 `testPerformanceWithManyObjects`；删除单次
  `nanoTime/currentTimeMillis` 及阈值，保留重复/1000-copy correctness，无 `@Test` 增删；
- behavioral oracle：三个同类、不同 source 实例依次复制 `John Doe/30`、`Jane Doe/31`、
  `Alex Doe/32` 并逐 target 精确断言，证明 cache metadata 不保存实例值；不通过反射耦合
  private cache identity，不新增 production test seam；
- verification inventory：10 个 fresh JVM focused=`1/F0E0S0`，完整 class=`23/F0E0S0`，
  完整 module=`27/F0E0S0`；无 production、public API、POM、runner、floor、critical set、
  exclusion 或 machine file 变化；
- formal pre-Cdiag implementation quality：PASS，B/H/M/L=`0/0/0/0`，record=
  `docs/9.3.4/quality/step4-r15-recovery-implementation-quality.md`；
- r15 closure boundary：machine=`diagnostic-ready/diagnostic-pending`；new Cdiag
  commit/push/clean -> fresh diagnostic -> review/Cfreeze -> fresh formal。`can_enter_coverage_audit=no`、
  `can_enter_acceptance=no`，Step 5 closed。

### Superseding r16 diagnostic / reviewed Cfreeze inventory

- new Cdiag=`f863c672029d5d1e5a4903df74cf6cba22a04a85` 已 commit/push/clean；fresh diagnostic=
  `step4-coverage-20260717-diagnostic-r16` 已完整通过：required lanes=
  `773 positive + 59 structural / 5,707 testcase / F0E0S0`，raw coverage inventory=
  `23 exec / 48 sessions / 16,948 unique execution class identities`；
- aggregate observation=`54,624/76,830 line`、`26,111/44,870 branch`；12 个 critical class
  全部通过，唯一 `NamespaceScope` branch 为 contract-defined `N/A`，不得伪装成正数阈值；
- immutable candidate=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r16-threshold-candidate-20260717.json`，
  SHA-256=`2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919`；独立 review=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r16-threshold-review-20260717.md`，
  SHA-256=`88b99e76e5584d3cd17bcdcffd138f1fe6655ce0b7795d3430d2f15a018c8fb3`，
  B/H/M/L=`0/0/0/1`；Low 仅要求 fresh formal 复现 aggregate，不授权降阈值或重跑碰运气；
- reviewed machine state：canonical threshold=`confirmed`，SHA-256=
  `ca6a25c66fbbe9a595adde74f1b7589bd3829b93edebfd5b11dc394ab8d088c8`；contract=
  `formal-ready`，SHA-256=
  `6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`；
- Cfreeze 工作树只包含 machine contract trio 与 `docs/9.3.4/**` 文档证据；runner、production、
  public API 与测试清单均无新增改动。formal-delta direct-single-parent allowlist 继续生效；
- current authorization 仅为提交并 push direct-child Cfreeze、随后执行一个唯一 fresh formal。
  Cfreeze 尚未提交，fresh formal 尚未运行；`can_enter_coverage_audit=no`、
  `can_enter_acceptance=no`，不得声明 Step 4 完成，Step 5 保持关闭。

## Protected Boundaries

- `docs/9.3.3/evidence/**` and existing `target/v933-*` are historical authority；
  不覆盖或重算为 9.3.4 pass。
- production lifecycle/query/cache implementation默认 do-not-touch；只有测试治理
  确认 BUG 并建立 workitem 后可最小修改。
- existing uncommitted 9.3.1–9.3.3 worktree remains user-owned until an explicit
  clean-commit transition is prepared；不得清理以满足 authority。
- plugin versions, image digests, inventory counts and coverage thresholds must
  be frozen by evidence, not copied from this planning document without Step 1 review。

### Superseding formal-r3 failure / deterministic QueryModel regression inventory

- tested Cfreeze=`a63c82c53ebaad1a1c22d78647fbda70b4bd6594`，formal-r3=
  `step4-coverage-20260717-formal-r3`；full inventory=`773+59/5707/F0E0S0`、
  `23 exec / 48 sessions`，line exact `54624/76830`，branch=
  `26110/44870 < 26111/44870`，因而在 final gate immutable fail closed；
- exact delta inventory：唯一变化类为
  `foggy-dataset-model/.../QueryModelSupport`，唯一方法为 `getMergedJoinGraph()`，
  唯一源码位置为 line 316 inner `if (mergedJoinGraph == null)`；r16=
  `0 missed/2 covered`，formal-r3=`1 missed/1 covered`；
- remediation code inventory 仅包含既有测试文件
  `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/RuntimeNamedDataSourceResolverBindingTest.java`；
  在既有 `publicationGuardSerializesTheCallbackWithAConcurrentRebind` 内加受控
  QueryModel contention 和 single-build/same-result 断言，
  不改 production/public API/POM/runner/floor/critical/exclusion，不新增或改名 `@Test`；
- current verification inventory 为 targeted selector PASS + protected overlay PASS + 5/5 fresh
  Maven/JVM PASS；QueryModelSupport class id=`d242dafe9de31249`、probes=`34/629`、packed bitmap=
  `4P-_7xsAAIADAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEA`、
  unique=`1`，Surefire=`1/F0E0S0`；`foggy-runtime-api` full module=
  `128/F0E0S0`，`RuntimeNamedDataSourceResolverBindingTest=5/F0E0S0`。formal-r3 recovery
  pre-Cdiag quality=`PASS / 0/0/0/0`，machine/contract/overlay/negative suites 通过；
  record=`docs/9.3.4/quality/step4-formal-r3-recovery-implementation-quality.md`。all-lane
  diagnostic 仍 pending，不得作为 Step 4 exit evidence；
- reset machine inventory：contract=`diagnostic-ready`、threshold=`diagnostic-pending`；该轮
  manifest identity 只属于 formal-r3 recovery 的历史 pre-Cdiag snapshot，不是 current identity；
- 该 historical boundary 随后建立 Cdiag `316a71f753827f8f34063b0eb0669271f696c5ee`，并被
  diagnostic-r17 消耗；current boundary 见下一节。Step 4 `in-progress`，Step 5 closed。

### Superseding diagnostic-r17 / final-mysqld handoff remediation inventory

- consumed Cdiag=`316a71f753827f8f34063b0eb0669271f696c5ee`；fresh run=
  `step4-coverage-20260717-diagnostic-r17`。outer 在 `child-unit / exit 1`、Unit 在
  `unit-mysql57-lifecycle-negative / exit 1` immutable fail closed；r17 永久
  `excluded/non-reusable`，canonical lifecycle receipt、fixture/Unit XML、exec、source-after、
  aggregate、observation、summary、candidate/final success-only artifact 均 absent；
- exact failed child=`unit-mysql57-49a76dbc70c3bd98`，phase=`fixture-first-apply / exit 1`，
  callback-ready 与 fixture-first receipt absent；r17 run log 中两个 passed JSON 仅是 EXIT-trap
  cleanup receipts，不是 lifecycle 或正常 fixture PASS；outer/run-owned cleanup=`0/0/0`，四个
  demo database container 精确恢复；
- confirmed RED inventory：unfixed stock ping 在 samples `15..40` 已报告 healthy，而
  `/proc/1/comm=docker-entrypoi`；sample `41` 才为 healthy/final `mysqld`。该证据证明 health
  与 final-server handoff 之间存在窗口，不是通过重复运行推断；
- remediation code inventory：
  `foggy-dataset-demo/docker/docker-compose-v934-authority.yml` 作为 Step 4 declared amendment，
  MySQL57/8 healthcheck 均增加 PID1 exact `mysqld` 并保留原 ping；冻结 Step 3 provisioner
  不改。`scripts/v934/step4/unit_mysql_fixture_tool.py` 为每个 lifecycle probe 使用 run-owned、
  no-clobber、failure-retained combined diagnostics，成功后删除，不改变成功 receipt schema；
  successor declared amendment/overlay/database authority/required contracts 与各层 manifest 同步封印；
- runtime GREEN inventory：MySQL57 与 MySQL8 都在首次 healthy 时即为 PID1=`mysqld`，
  premature healthy=`0`；修复后旧工具字节三轮 lifecycle=`15/15`。penultimate 加固字节 focused
  `5/5` receipt=
  `159fbe80595933e29f05f13c0f1d82e9b65d7f947dddec253477f0b3f3876799`；最新加固字节 r2=
  `step4-unit-lifecycle-handoff-current-20260717-r2` 已 `5/5` PASS，receipt SHA-256=
  `e3bad41ad9ec634c1702ffe20f4c0ddbff3050a227956e2ba9054a11f6b606c7`，所有成功
  `provisioner.log` absent、run-owned residue=`0/0/0`，demo exact restore=
  `runner_rc=0/restore_rc=0` 且 healthy/listening；
- static verification inventory：successor overlay negatives=`12/12`、Unit fixture negatives=
  `36/36`、coverage contract negatives=`27 + source/Git 22 + frozen replay 12`，全部 PASS；machine
  保持 `diagnostic-ready/diagnostic-pending`；
- pre-Cdiag formal implementation quality：PASS，B/H/M/L=`0/0/0/0`，record=
  `docs/9.3.4/quality/step4-diagnostic-r17-recovery-implementation-quality.md`；只授权
  replacement Cdiag commit/push/clean 与 fresh diagnostic，不授权 full Unit/Step 4 exit 或任何
  downstream gate；
- 在 r17 recovery snapshot，完整 Unit replacement=`681+55/4941/F0E0S0` 尚未由当时 remediation
  后的 clean authority 证明，不能从 focused lifecycle 推断；该缺口随后由 r18 full Unit 补齐，但
  r18 未达到 r16 aggregate high-water。r17 snapshot 的下一许可链曾为 replacement Cdiag commit/push/clean -> fresh diagnostic ->
  candidate/review -> direct-child Cfreeze -> fresh formal -> final quality -> coverage audit ->
  acceptance；当前 `can_enter_coverage_audit=no`、`can_enter_acceptance=no`，Step 5 closed。

### Superseding diagnostic-r18 / Pivot null-axis oracle inventory

- immutable diagnostic inventory：Cdiag=
  `5be1edaa16c5883cde2f66396ac26a1ae113430b`，run=
  `step4-coverage-20260717-diagnostic-r18`，public validation PASS；required=
  `773+59/5707/F0E0S0`、Addon=`2/6`、exec/session/class identity=`23/48/16940`、
  class universe=`24 modules/2098 classes`、critical below-floor=`0`、cleanup=`0/0/0`；
- governed high-water inventory：r18 aggregate=`54622/76830 line`、`26107/44870 branch`，相对
  r16 reviewed high water=`54624/76830`、`26111/44870` 为 `-2 line/-4 branch`；r18 candidate
  absent，decision=`threshold-candidate-not-authorized`。精确差异仅在
  `BaselineRatioCalculator`（`-2 line/-3 branch`）和 `ResultShaper`（`-1 branch`）的
  PostgreSQL exec bitmap；
- remediation source inventory：仅修改
  `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotSqlParityIT.java`，
  current SHA-256=`18ebeedf8d79a84d5ba59ff991aeffeefea73cbb8aa51c6c10444ed64ba6d325`；
  在既有 S12 `testBaselineRatioParity` 内构造确定性 null-column/null-row 输入，断言 null column
  不进入 first/last domain、null row 与 named row 隔离，并在 tree 输出映射为 `__null__`；production
  unchanged，`@Test` static count=`23 -> 23`；三次 fresh JVM/JaCoCo focused=
  `3 x 1/F0E0S0`，`BaselineRatioCalculator=79/131 probes`、`ResultShaper=45/139 probes`，
  bitmap `3/3 identical`；完整 test class=`23/F0E0S0`；
- successor binding inventory：`foggy-dataset-model/src/test/java` protected tree=
  `d11f9d478b4d4c29e036fd714586663591677f9ea0f833c505cba299523f0ea4`（306 files）；
  database amendment=`ec4b1668...9414`、declared amendment=`8f989dc2...827`、database contract=
  `cca2158b...67e5`、required contract=`a7ae5fc7...53a3`、overlay contract=`b0fba556...3982`、
  successor manifest=`8cec5511...f1e9`、top manifest=`812b148b...1de5`；validators=
  successor `14/14`、top `60/60`、database `7 variants/29 reports/370 nodes`、required=
  `45/446/F0E0S0`、overlay=`22 amendments/9 bindings`、coverage=`23/48/773+59/5707`；
- tracking inventory：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r18-governed-high-water-gap-20260717.md`
  与 `docs/9.3.4/workitems/BUG-step4-pivot-null-axis-coverage-oracle.md`。machine 保持
  `diagnostic-ready/diagnostic-pending`；本 inventory 只支撑 implementation quality 与 replacement
  Cdiag/r19，不支撑 candidate/Cfreeze/formal/audit/acceptance；pre-Cdiag quality=
  `PASS / 0/0/0/0`，record=
  `docs/9.3.4/quality/step4-diagnostic-r18-pivot-null-axis-implementation-quality.md`；Step 5 closed。

### Superseding diagnostic-r19 / Cfreeze inventory

- sealed r19 inventory：Cdiag=`613b11a0ae6732f865f918551cd9116079771b5e`，run=
  `step4-coverage-20260717-diagnostic-r19`，required=`773+59/5707/F0E0S0`、Addon=`2/6`、
  exec/session/class identity=`23/48/16931`、class universe=`24/2098`；aggregate=
  `54624/76830 line, 26111/44870 branch`；
- evidence inventory：pass record、immutable candidate `6588e30b…f545b8`、two-review threshold
  record 与 pre-Cfreeze quality；candidate 保持 `review-required`；
- Cfreeze machine delta 精确只有 `scripts/v934/step4/coverage-thresholds.json`、
  `coverage-contract.json`、`SHA256SUMS`；其余变更仅 `docs/9.3.4/**`。没有 production、test、
  POM、runner、successor manifest、floor、critical set 或 exclusion 变化；
- At that r19 checkpoint machine=`confirmed/formal-ready`；direct-child Cfreeze commit/push/topology/clean 与
  fresh formal pending。Step 4/5、final quality、coverage audit、acceptance 仍关闭。

### Superseding formal-r4 / final-quality inventory

- tested Cfreeze=`f97483a0b87a82734d21888e7b5bea74b0c5fe55`，formalization delta=
  `passed / direct-single-parent / 17 paths`；Cfreeze 没有 production/test/POM/runner/floor/
  critical/exclusion/successor byte delta；
- fresh formal-r4 inventory：required=`773+59/5707/F0E0S0`、Addon=`2/6`、Unit=
  `681+55/4941`、Integration=`47+4/320`、DB/external=`29/370 + 16/76`，exec=
  `23/48/16953`，production universe=`24/2098`；
- aggregate exact=`54624/76830 line, 26111/44870 branch`；critical=`12/23/below0`；source
  before=after=`96e2c871…c3a3`，final artifact public replay、model/sensitive/cleanup PASS；
- post-formal implementation quality=`ready-for-coverage-audit / 0/0/0/1`。当前 code inventory
  已封存；只开放 test evidence coverage audit，Step 4/5、acceptance 与 9.3.5 仍关闭。

### Superseding Step 4 accepted inventory

- formal inventory 不变：Cfreeze=`f97483a0…`、required=`773+59/5707/F0E0S0`、Addon=
  `2/6`、exec=`23/48/16953`、production universe=`24/2098`、aggregate=
  `54624/76830 line, 26111/44870 branch`；
- coverage audit 补入唯一 companion inventory：final `PivotSqlParityIT.java` SHA=
  `18ebeedf…d325`，legacy focused XML=`1/F0E0S0`、SHA=`802933e6…0dd`；该 companion
  不加入 formal report/exec/coverage totals；
- 25 个 Step 4 workitem 均 closed；`DEBT-unit-mysql57-fixture-classification-migration.md`
  仍 open，owner/截止点保持 9.3.5 version acceptance；
- Historical formal-r4 inventory 当时为 accepted/frozen input；formal-r6 recovery 已重新关闭
  downstream entry。Current Step 4=`in-progress / replacement Cdiag`、Step 5=`hold/closed`；
  historical bytes 只读保留且不得被拼接为 replacement authority。

### Superseding formal-r6 recovery inventory

- implementation delta：`scripts/v934/step4/coverage_contract_negative_tool.py`，只把 synthetic
  fsmonitor hook response 从 newline token 改为 protocol-v2 NUL token；
- diagnostic state delta：Step 4 threshold/contract/manifest 与 Step 6 contract/tool/manifest，恢复
  `diagnostic-pending / diagnostic-ready` 并级联 exact SHA；
- evidence delta：formal-r6 failure、BUG、recovery quality 和本轮状态同步；
- production/POM/API/test/report/exec/database delta=`0`；threshold floor、critical set、exclusion 和
  real source-hash rejection semantic 不变；
- current inventory 只授权 new Cdiag/fresh diagnostic；r22 capsule 与 r6 artifacts 只读保留。

### Superseding diagnostic-r23 remediation inventory

- r23 dynamic inventory=`773+59/5707/F0E0S0`、Addon=`2/6`、exec/session/classes=
  `23/48/16962`、production universe=`24/2098`，public validation/source/model/sensitive/cleanup PASS；
- r23 aggregate=`54624/76830 line, 26112/44870 branch`；唯一相对 r22 的 class/method delta 是
  `BeanInfoHelper$MapBeanInfoHelper#getBeanProperty` inner double-check，不能冻结为 exact minimum；
- remediation inventory 只增加既有 `BeanInfoHelperTest#getClassHelper` 内部 assertions/interleaving；
  `@Test`、report/testcase cardinality、production/POM/runner/threshold/critical/exclusion delta=`0`；
- final test source SHA-256=`52bf8b885f6cd0e6e65fafe4f4afa753699edf5b5e902b93c778ef36f020966c`；
  five fresh JVM class probe=`a6629aa379049ec7 / 10/11 / _wU` exact，owning module=`97/F0E0S0`；
- current inventory 只授权 replacement Cdiag/fresh diagnostic-r24；r23 candidate/capsule absent，
  Step 5–7/9.3.5 继续 closed。

## Execution check-in — diagnostic-r24 freeze inventory（2026-07-19）

- replacement Cdiag=`414c8b12…`；r24 source closure=`4,077 tracked files / 102,591,681 bytes`，
  before/after exact；production universe=`24 modules / 2,098 bytecode classes`；
- required inventory=`773+59/5707/F0E0S0`、Addon=`2/6`、exec/session=`23/48`，runtime-loaded
  identities=`16,953`；
- r24 exact aggregate=`54624/76830 line, 26112/44870 branch, 17659/35571 complexity`；target
  class id/probe=`a6629aa379049ec7 / 10/11 / _wU`；
- candidate/capsule/review 已闭合；canonical formalization 只改六个 machine paths，其余仅
  `docs/9.3.4/**`。production/test/POM/runner/workflow/floor/critical/exclusion inventory delta=`0`；
- current inventory authorizes one direct-child Cfreeze and fresh formal-r7 only；Step 5–7/9.3.5 closed。
