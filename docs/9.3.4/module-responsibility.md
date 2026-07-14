---
doc_role: module-responsibility
doc_purpose: Define ownership and dependency boundaries for the 9.3.4 test and CI evidence-chain delivery.
version: 9.3.4
status: ready
created_at: 2026-07-14
updated_at: 2026-07-14
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
| `foggy-dataset-model` | broad SQLite integration and five-DB semantic parity/capability contracts | dataset/demo fixtures | preflight, QueryFacade/native, dialect assertions | rerun all unit tests per DB | 2/3/4 |
| Runtime/MCP/fsscript/cache/addons | module integration ownership and 9.3.1–9.3.3 successor regressions | model/fsscript/runtime ports | correctly named IT, hermetic/external lane ownership, exact reports, Step 4 exec provenance | new lifecycle/API design | 2/3/4/5 |
| `foggy-dataset-demo/docker` | deterministic five-DB images/init/sentinel fixture | Docker Compose | pinned image identity, automated init, before/after | production deployment redesign | 3 |
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
- 不在本版本创建 9.4.0 production `model-api/core/jdbc/starter/web` 模块。
