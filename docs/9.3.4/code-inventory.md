---
doc_role: code-inventory
doc_purpose: Record planned 9.3.4 code touchpoints and protected boundaries.
version: 9.3.4
status: in-progress
created_at: 2026-07-14
updated_at: 2026-07-15
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
    notes: central Surefire/Failsafe/JaCoCo; preserve UTF-8 argLine; remove accidental multi-db runner ownership
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

## Protected Boundaries

- `docs/9.3.3/evidence/**` and existing `target/v933-*` are historical authority；
  不覆盖或重算为 9.3.4 pass。
- production lifecycle/query/cache implementation默认 do-not-touch；只有测试治理
  确认 BUG 并建立 workitem 后可最小修改。
- existing uncommitted 9.3.1–9.3.3 worktree remains user-owned until an explicit
  clean-commit transition is prepared；不得清理以满足 authority。
- plugin versions, image digests, inventory counts and coverage thresholds must
  be frozen by evidence, not copied from this planning document without Step 1 review。
