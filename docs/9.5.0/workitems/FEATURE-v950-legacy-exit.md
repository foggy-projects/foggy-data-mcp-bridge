---
doc_type: delivery-spec
delivery_type: cross-module
version: 9.5.0
ticket: v950-legacy-exit
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: repository-owner-via-user-request
approved_at: 2026-07-24
open_questions: []
---

# Delivery Spec: 9.5.0 Model Legacy Exit

## Document Purpose

- intended_for: ultra-implementation / independent-signoff
- purpose: 冻结 9.5.0 旧聚合坐标、旧包根、兼容查询桥和 SPI v1 退出的唯一执行边界。
- canonical_path: `docs/9.5.0/workitems/FEATURE-v950-legacy-exit.md`

## Goal

- version_goal: 在 9.4.0/9.4.1 已完成 SPI v2 建立与采用后，结束一个兼容周期并完成第一方 legacy exit。
- target_outcome: 第一方构建不再依赖 `foggy-dataset-model` 聚合坐标，生产源码不再引用
  `com.foggyframework.dataset.db.model.*`，provider capability 与实际角色一致，旧查询兼容桥退出。
- critical_outcomes:
  - 14 个第一方旧聚合直接依赖归零；
  - 聚合之外 11 模块/110 文件的旧包生产导入归零，旧包根从新引擎源码中消失；
  - `DatasetClientProxy` 不再计入 model SPI 查询 bypass，治理守卫变为零 allowlist；
  - MODEL_LOAD 有稳定小型 port；namespace isolation 固化为所有 load/query/refresh 的非可选请求维度，
    不作为可虚报的独立 capability；ATOMIC_REFRESH 只在真实原子切换 adapter 存在时声明；
  - duplicate/missing/unsupported/role mismatch 继续 fail closed，TCK 只验证真实发布的角色。
- success_is_sufficient_when: 新模块图可由 reactor 构建，旧坐标/旧包/旧 bridge 守卫为零，focused 与
  affected tests 通过，迁移文档、breaking 清单和实施证据完整并进入 `READY_FOR_SIGNOFF`。

## Scope

- in_scope:
  - 将旧 `foggy-dataset-model` 实现模块物理归位为 `foggy-dataset-model-engine`；
  - 将旧包根 `com.foggyframework.dataset.db.model` 迁移到
    `com.foggyframework.dataset.model` 下的 `config/def/engine/lifecycle/semantic/spi` 等真实所有权；
  - 更新第一方 POM、源码、测试、Spring 元数据、反射字符串、发布/检查脚本及文档中的受影响身份；
  - API 增加 JDK-only model-load 与 atomic-refresh port/DTO；core discovery 校验 capability-role；
  - engine/starter 为已有 JDBC loader/catalog refresh 路径发布真实 provider 角色；
  - 删除 `LegacyQueryFacadeAdapter`；将引擎高级查询方法保留为 engine-internal advanced port，
    稳定公共入口仍为 `model-api QueryFacade/DTO`；
  - 将 9.4.1 allowlist 守卫升级为旧坐标、旧包导入、兼容 adapter 和治理 bypass 全部为零；
  - 同步 9.5.0 README、迁移表、breaking/rollback 说明和 workitem 实施结果。
- affected_modules:
  `foggy-dataset-model-api`, `-core`, `-jdbc`, `-starter`, `-web`, `-tck`,
  new `foggy-dataset-model-engine`, runtime/MCP/memory-grid/launcher and all model addons.
- external_dependencies: none; external consumers must migrate source and Maven dependencies before adopting 9.5.0.

## Non-Goals

- out_of_scope:
  - 数据库 schema、数据搬迁或不可逆运行时迁移；
  - 新增 backend 功能、改变查询语义、放宽 namespace/security/field-access/physical-column 权限；
  - 为没有真实原子切换语义的 Mongo/vector/cache/preagg provider 虚报 ATOMIC_REFRESH；
  - GitHub CI、tag、release、publish、Step 5/7、authority/replay/source-seal 或通用五库矩阵。
- do_not_touch:
  原始脏工作区及其中 `docs/9.3.5/README.md`、`docs/9.3.5/workitems/` 用户内容；
  `foggy-dataset` 的 `.ds/.ms` DatasetClient 查询机制不改产品语义。
- non_blocking_or_waivable_items:
  非 9.3.5 查询桥用途的历史 `@Deprecated` 方法不要求在本版本机械删除；必须记录剩余项但不得冒充
  SPI v2 公共契约。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 删除旧 Maven 坐标并引入 `foggy-dataset-model-engine` | 旧坐标是传递聚合，已完成一个兼容周期 | 外部 POM 必须显式迁移；不发布同名空壳或静默转发 |
| 旧 Java 包根整体退出，目标根为 `com.foggyframework.dataset.model` | 包名必须反映 model v2 所有权，避免继续把内部类型当旧 SPI | 这是明确授权的 source/binary breaking change |
| engine 统一承载 JDBC/semantic/lifecycle/pivot/compose 实现 | 当前 612 个实现文件高度内聚，先结束聚合/包债务，避免在同一版本制造循环拆分 | API/core 保持 JDK-only/adapter-free；addon 实现继续归各 addon |
| namespace isolation 是请求与 catalog identity 的不变量，不增加可选 capability | 可选 capability 会允许 provider 暗示可关闭隔离 | 缺 namespace/currentness 处理必须 fail closed |
| MODEL_LOAD 与 ATOMIC_REFRESH 使用独立小角色 | capability 必须对应可调用 port | 只有 engine 的真实 loader/atomic catalog swap 可声明 |
| 删除 `LegacyQueryFacadeAdapter`，稳定 facade 保持 | 一个兼容周期已结束 | engine 内部高级端口可保留，但不再以 legacy bridge 对外 |
| 不改变查询、权限或数据结果语义 | 本版本是物理/SPI exit | 任何语义放宽或数据迁移触发 `NEEDS_REPLAN` |

## Acceptance Criteria

- [x] AC-1: reactor、dependency management、第一方 POM 中没有旧
  `com.foggysource:foggy-dataset-model` 模块或依赖；目标依赖显式指向 API/core/engine/adapter。
- [x] AC-2: tracked production Java 与 Spring/runtime 元数据中没有
  `com.foggyframework.dataset.db.model`；新 engine 源码路径与包声明一致。
- [x] AC-3: architecture guard 要求旧坐标、旧包 import/FQCN、`LegacyQueryFacadeAdapter` 与 model-query
  bypass 均为零，不保留 legacy allowlist。
- [x] AC-4: `ModelLoadBackendProvider` 对应真实 load port；namespace 作为必填/显式默认 identity 维度；
  engine 只有在现有 catalog 原子切换路径可验证时声明 ATOMIC_REFRESH。
- [x] AC-5: core/TCK 对 MODEL_LOAD/QUERY/ATOMIC_REFRESH/CACHE_INVALIDATION 的 capability-role overclaim
  在 discovery fail closed，descriptor/identity 仍不可变且唯一。
- [x] AC-6: JDBC/query-cache 现有能力不扩大；Mongo/vector/preagg 未接入的能力保持不声明。
- [x] AC-7: `LegacyQueryFacadeAdapter` 及其 compatibility test 删除；公共查询控制器和消费者直接使用
  stable QueryFacade DTO 或 engine-internal mapper/port。
- [x] AC-8: runtime/MCP/memory-grid/launcher/addons 在新模块图上 compile，focused unit/context/TCK 与
  affected reactor tests 实际通过。
- [x] AC-9: 9.5.0 README/migration/breaking/rollback 和本 workitem 记录精确命令、结果、偏差及残余风险；
  完成 review 后状态为 `READY_FOR_SIGNOFF`，不得自行伪造 `ACCEPTED`。

## Contract / Data / Security Constraints

- API or event contract: 9.5.0 明确允许删除旧 Maven/Java/bridge SPI；稳定
  `com.foggyframework.dataset.model.api.QueryFacade` 请求/结果契约不得破坏。
- data and migration: 无数据库 DDL/DML；不得进行不可逆数据迁移。
- compatibility and rollback: 外部升级需一次性替换坐标与 import；回滚方式为回退 9.5.0 提交并恢复
  9.4.1 制品，不提供双包运行时。迁移清单必须可机械检查。
- permissions and secrets: 不涉及凭据；namespace、field access、physical denied columns、datasource
  currentness 和 fail-closed 错误不得降低。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/2/3 | must-pass | critical | architecture test + inventory commands | 9.4.1 14/110/1 baseline | zero-count output |
| AC-4/5/6 | must-pass | critical | API/core/TCK/JDBC/cache/engine provider tests | 9.4.1 QUERY/cache TCK | exact Maven results |
| AC-7 | must-pass | major | facade/controller focused tests and removed-class guard | 9.4.1 facade tests | exact Maven results |
| AC-8 | must-pass | critical | affected reactor compile/test slices with `-pl ... -am` | unchanged semantic behavior evidence | module/test totals |
| external binary compatibility | intentional break | major | documented removed/renamed surface; no compatibility claim | 9.4.1 assessment | migration table |
| large authority/replay | approval-required | major | not run in implementation scope | earlier 9.4 evidence only | explicit omitted reason |

## Validation Budget and Evidence Sufficiency

- assurance_level: elevated
- lightweight_validation: inventory, architecture tests and focused API/core/TCK tests, each expected `<5m`.
- medium_validation: engine and addon/runtime affected reactor slices, expected `5-30m` each.
- expensive_validation: one full release-governance authority is recommended only after a final candidate.
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: final breaking candidate before tag/release/publish.
- estimated_full_chain_wall_clock: lean `60-120m`; semantic/portable replay plus full DB matrix `2-4h`,
  based on 9.4.1 entry assessment and prior authority history.
- full_chain_prerequisites: final candidate SHA, database fixtures, runtime dependencies and explicit user approval.
- user_approval_status: not-requested
- decision_if_not_approved: finish implementation with focused/affected evidence and report authority as signoff prerequisite.
- expensive_validation_trigger: only the final candidate and explicit approval; maximum one attempt.
- maximum_expensive_attempts: 1 after approval; two non-product failures would require `NEEDS_REPLAN`.
- reusable_evidence: 9.4.1 accepted provider/catalog behavior and unchanged query/security semantic tests.
- stop_when_evidence_is_sufficient: zero legacy guards pass, all changed modules compile, focused provider/bridge and
  affected runtime/addon tests pass, review finds no unauthorized semantic/security change.
- validation_not_required: `mvn install`, generic five-DB matrix, Step 5/7, CI changes, tag/release/publish.

## Waiver Policy

- waivable_items: non-bridge historical deprecations and unexecuted large authority before signoff.
- authorized_role: repository owner.
- non_waivable_guards: zero old coordinate/package/bridge, capability truthfulness, namespace/security/data correctness,
  stable model-api QueryFacade compatibility and no irreversible migration.
- required_risk_record: exact remaining symbol, owner, reason, migration target and signoff impact.

## Risks and Open Questions

- known_risks:
  broad package/Maven renaming intentionally breaks external source/binary compatibility; reflection/configuration
  strings can evade Java compile checks, so tracked-text guards and launcher/context tests are required.
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、根 `CLAUDE.md`、9.4.0 migration、9.4.1 adoption/assessment；仓库未发现 `AGENTS.md`。
- 在 scope 内自主决定局部移动顺序；先 mechanical identity migration，再 provider role，再 bridge cleanup。
- 使用 Maven reactor `-pl ... -am`；不得运行 `mvn install`。
- 如需改变查询语义、权限、数据正确性、不可逆迁移或本契约 breaking 边界，设置 `NEEDS_REPLAN` 并停止。
- 未经再次明确批准，不得运行大型 authority/replay/rehearsal/source-seal/full-chain。
- 达到 evidence sufficiency 后停止，填写 Implementation Result 并设为 `READY_FOR_SIGNOFF`。

## Implementation Result

- implementation_summary:
  旧聚合模块已物理归位为 engine，第一方旧坐标/旧包/查询 bridge/bypass 归零；新增稳定
  MODEL_LOAD 与 ATOMIC_REFRESH 小角色，JDBC engine provider 复用真实 namespace-aware loader
  和 atomic catalog publication，catalog/TCK 对四种 capability-role overclaim fail closed。
- changed_paths:
  root reactor/dependency management；`foggy-dataset-model-{api,core,jdbc,starter,web,tck,engine}`；
  runtime/MCP/memory-grid/launcher；model addons/viewer/GraphQL/benchmark；Spring metadata、
  Docker/launcher identity、顶层验证脚本；`docs/9.5.0/**`。
- tests_and_results:
  - focused API/core/TCK/engine reactor：13/13 modules success；API 6、core 6、TCK 3、engine 16 tests。
  - JDBC engine operational provider/TCK：5 tests；13/13 modules success。
  - affected production compile：30/30 reactor modules success。
  - launcher/client affected test-compile：28/28 reactor modules success。
  - JDBC/cache/engine/launcher provider、context、assembly slice：27/27 reactor modules success；
    cache 23、launcher 7，及 engine/JDBC focused tests 全部通过。
  - standalone pivot/benchmark reactor test-compile：pivot 无源码；benchmark 13 main + 2 test sources
    实际编译通过。
  - `LegacyExitArchitectureTest`、tracked-text inventory、`git diff --check`、top-level `bash -n`
    和 launcher JAR/assembly boundary 检查通过。
  - owner 批准的 lean root authority：
    `mvn -B -ntp clean verify -DskipITs -Dsurefire.failIfNoTests=false
    -Dfailsafe.failIfNoTests=false` 在前 28 个模块完成 4945 tests，0 failures；
    engine 有 2 个既有 skipped。launcher 因测试夹具仍指向已删除模块而产生 1 个 context error，
    随后按 `BUG-launcher-smoke-sqlite-fixture-path` 最小修复。
  - 修复后 focused launcher smoke：27/27 reactor modules success，
    `DataViewerApiSmokeTest` 6 tests 全通过；完整制品
    `verify -Dmaven.test.skip=true -DskipITs` 32/32 modules success。
- validation_commands:
  ```bash
  mvn -pl foggy-dataset-model-api,foggy-dataset-model-core,foggy-dataset-model-tck,foggy-dataset-model-engine \
    -am test -DskipITs \
    -Dtest=LegacyExitArchitectureTest,ModelApiDependencyBoundaryTest,QueryFacadePublicApiTest,BackendProviderCatalogTest,BackendProviderTckSelfTest,JdbcEngineBackendProviderTest,JdbcEngineBackendProviderTckTest,DbModelAutoConfigurationTest,QueryFacadePortBoundaryTest,QueryFacadeDtoMapperTest \
    -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.failIfNoTests=false

  mvn -pl foggy-mcp-launcher,addons/foggy-dataset-client -am \
    test-compile -DskipTests -DskipITs

  mvn -pl foggy-dataset-model-jdbc,addons/foggy-dataset-model-cache,foggy-dataset-model-engine,foggy-mcp-launcher \
    -am test -DskipITs \
    -Dtest=JdbcQueryBackendProviderTest,JdbcQueryBackendProviderTckTest,JdbcEngineBackendProviderTest,JdbcEngineBackendProviderTckTest,DbModelAutoConfigurationTest,QueryCacheBackendProviderTckTest,QueryCacheBackendProviderAutoConfigurationTest,QueryCacheAutoConfigurationContextTest,AutoConfigurationBoundaryContractTest,FullAddonAutoConfigurationAssemblyTest \
    -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.failIfNoTests=false

  mvn -f /tmp/foggy-v950-standalone-reactor/pom.xml \
    -pl :foggy-dataset-pivot,:foggy-benchmark-spider2 -am \
    test-compile -DskipTests -DskipITs
  ```
- manual_or_experience_evidence:
  第一方旧坐标精确计数 0；生产旧包引用 0；旧 adapter/旧 engine QueryFacade 0；受治理 bypass 0；
  顶层脚本旧路径/FQCN 0；`.github` diff 0；原始脏工作区状态保持不变。
- deviations:
  standalone benchmark 原 POM 未显式指向根 parent，补充
  `<relativePath>../../pom.xml</relativePath>`；其测试同时暴露旧 semantic import 与已移除的
  二参 resolver 调用，已按当前显式 `SemanticRequestContext` 契约修正。顶层验证脚本同步迁移
  新模块路径/FQCN；历史 `scripts/v934/**` source-sealed 证据保持不改。
- residual_risks:
  外部调用方存在有意的 Maven/source/binary breaking；大型 authority/replay 未运行；
  独立 pivot 模块当前无 Java 源码；历史 sealed 脚本不是 9.5.0 可执行入口。
- reused_evidence:
  9.4.1 已签收的 stable QueryFacade、JDBC QUERY、query-cache invalidation、
  duplicate/missing/unsupported/role mismatch 与 launcher boundary 证据。
- omitted_validation_and_reason:
  按 owner 明确预算未运行 `mvn install`、Step 5/7、扩展 semantic/portable replay、
  source-seal、通用五库矩阵、GitHub CI、tag/release/publish；test-only 修复后也未重复完整
  root `clean verify`。这些排除项不得被描述为已验证。
- readiness:
  `ACCEPTED_WITH_RISKS`；最终实现和 test-only authority 修复已在固定提交上完成 review、
  零守卫、focused smoke 与完整制品复核。正式记录见
  `docs/9.5.0/acceptance/version-signoff.md`。

## Code Review Result

- reviewed_implementation_sha: `00e4f090ca868e22d7e386e86ebfa4c01272e0bc`
- reviewed_range: `63a8f15ff870ee40c83c458d7db455d6b5977deb..00e4f090ca868e22d7e386e86ebfa4c01272e0bc`
- conclusion:
  未发现核心 blocker；未发现未授权的查询语义、权限、namespace isolation、数据正确性或不可逆迁移变化。
  Maven/Java/bridge breaking 与已批准 Gate P0 契约一致。authority 发现的 launcher fixture
  路径漏改为 test-only 缺陷，已在独立 BUG workitem 中修复并通过最小半径重验。
- final_guard_review:
  旧 POM artifact 0、旧 root module 0、生产旧包 0、runtime metadata 旧包 0、
  `LegacyQueryFacadeAdapter` 0、旧 engine `service/QueryFacade.java` 0、
  顶层脚本旧模块/旧 FQCN 0、`.github` diff 0；`git diff --check` 与顶层脚本 `bash -n` 通过。
- provider_review:
  starter auto-configuration 明确在 engine auto-configuration 之后执行；
  engine 的同名 JDBC provider 优先发布 QUERY/MODEL_LOAD/ATOMIC_REFRESH，
  fallback adapter 不会覆盖真实角色。catalog/TCK 对四类 capability-role mismatch 均 fail closed。
- residual_signoff_risk:
  外部 Maven/source/binary breaking 是本版本有意影响；没有一条 post-fix 完整 root
  `clean verify` 绿线，接受证据由原 authority 的 modules 1-28、修复后 launcher focused smoke
  与 32-module artifact verify 组合而成；semantic/portable replay、source-seal 与通用数据库矩阵未运行。

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: codex-reviewer
- signed_off_at: 2026-07-24
- acceptance_record: `docs/9.5.0/acceptance/version-signoff.md`
- blocking_items: none
- follow_up_required: yes-before-tag-release-publish

## References

- requirement / issue: user-approved sequential execution of 9.4.1 landing, 9.5.0 Gate P0 and implementation
- architecture / glossary: `docs/9.4.0/model-spi-v2-migration.md`
- related work items:
  `docs/9.4.1/workitems/FEATURE-v941-spi-v2-adoption-hardening.md`,
  `docs/9.4.1/9.5.0-entry-assessment.md`
