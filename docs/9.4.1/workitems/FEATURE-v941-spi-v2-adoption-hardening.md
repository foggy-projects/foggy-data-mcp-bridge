---
doc_type: delivery-spec
delivery_type: cross-module
version: 9.4.1
ticket: v941-spi-v2-adoption-hardening
status: READY_FOR_SIGNOFF
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: repository-owner-via-user-request
approved_at: 2026-07-24
open_questions: []
---

# Delivery Spec: 9.4.1 SPI v2 Adoption/Hardening

## Document Purpose

- intended_for: ultra-implementation / independent-signoff
- purpose: 固定 9.4.1 无破坏 adoption、依赖冻结、provider/TCK 加固与定向验证边界。
- canonical_path: `docs/9.4.1/workitems/FEATURE-v941-spi-v2-adoption-hardening.md`

## Goal

- version_goal: 在保留 9.4.0 legacy 聚合和桥接 API 的前提下，让第一方消费者开始显式采用 SPI v2，并冻结新增 legacy debt。
- target_outcome: 可安全迁移的查询/自动配置消费者使用稳定 API 或名称边界；provider capability 与角色精确匹配并在发现期 fail closed。
- critical_outcomes:
  - 不新增旧 `foggy-dataset-model` 直接依赖或 controller/runtime/addon 查询 bypass；
  - 已知 legacy 消费者有准确基线、原因和 9.5.0 去向；
  - JDBC/query-cache 的声明能力保持精确、不可变，catalog/TCK 拒绝 capability-role impostor；
  - 旧聚合、deprecated facade/constructor/SPI 和 Maven 坐标保持兼容。
- success_is_sufficient_when: affected reactor tests、architecture guard、provider/TCK 与迁移文档全部通过并形成 `READY_FOR_SIGNOFF`。

## Scope

- in_scope:
  - 重新确认 15 个直接 legacy 聚合 POM、12 个模块/114 个生产 Java 文件旧包导入基线；
  - 添加仓库级依赖与 bypass 守卫，使用显式 allowlist 冻结暂留项；
  - addon 自动配置去除对 legacy `DbModelAutoConfiguration.class` 的编译期排序硬引用；
  - 移除 Odoo bridge 的冗余直接 legacy 聚合依赖，保留其经 MCP 获得的兼容运行时；
  - GraphQL/DataViewer 的 QueryFacade 调用直接构造稳定 request DTO，不再调用 deprecated legacy adapter；
  - core catalog 在发现期验证已迁移 QUERY/CACHE_INVALIDATION capability 对应的小型 provider role；
  - 扩充 API/core/TCK/JDBC/cache 的精确能力、不可变性和 fail-closed 回归。
- affected_modules: `foggy-dataset-model-api`, `-core`, `-jdbc`, `-tck`, `foggy-dataset-model-cache`, `foggy-dataset-graphql`, `foggy-data-viewer`, addon auto-config consumers, `foggy-odoo-bridge-java`。
- external_dependencies: none。

## Non-Goals

- out_of_scope:
  - 9.5.0 legacy exit、物理迁移 loader/engine/semantic/pivot/compose；
  - 新增 MODEL_LOAD、namespace isolation、ATOMIC_REFRESH capability/port/TCK 声明；
  - 删除/改签名旧 facade、高级方法、构造器、SPI 或 Maven 坐标；
  - GitHub CI、tag、release、publish、Step 5/7、authority/replay/source-seal、通用五库矩阵。
- do_not_touch: 原始脏工作区及其 `docs/9.3.5/README.md`、`docs/9.3.5/workitems/` 用户内容。
- non_blocking_or_waivable_items: 需要 9.5.0 物理拆分的深层 legacy 导入和显式记录的兼容依赖。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| legacy 聚合与 bridge 继续保留 | 9.4.1 是 adoption，不是 exit | 不删除、不改签名、不改变外部坐标 |
| 依赖守卫使用精确 POM allowlist；旧导入按模块/文件数冻结并记录 | 阻止新增债务，同时允许深层消费者暂留 | 允许只减不增；新增项必须更新 canonical 决策 |
| 自动配置排序改用 `afterName` | 去除对旧配置类的编译硬引用且保持排序 | 字符串 JVM 名称保持原值 |
| 仅校验已迁移 QUERY/CACHE_INVALIDATION 的角色一致性 | 这些能力已有小型 port；其他能力尚未实现 | 不虚报 MODEL_LOAD/ATOMIC_REFRESH/namespace |
| capability-role overclaim 在 catalog discovery 立即失败 | 失败越早越不可能静默路由 | 合法 provider 行为不变；非法 provider 更早失败 |
| QueryFacade 消费者直接构造 JDK DTO | 逐步停止依赖 deprecated adapter | 旧公开转换方法继续保留 |

## Acceptance Criteria

- [x] AC-1: 直接 legacy POM 依赖不高于基线 15，且所有剩余项都在精确 allowlist；Odoo 冗余项已移除或有验证阻断说明。
- [x] AC-2: 聚合之外旧包生产导入不高于 12 模块/114 文件基线，README/migration 记录剩余模块、原因和 9.5.0 目标边界。
- [x] AC-3: architecture guard 拒绝新增 direct legacy POM、controller/runtime/addon loader+query bypass 和未批准 DataSetModel direct-query 文件。
- [x] AC-4: GraphQL/DataViewer 稳定查询路径不再生产依赖 `LegacyQueryFacadeAdapter`，原公开 API 保持兼容。
- [x] AC-5: addon auto-config 不再编译期导入 legacy `DbModelAutoConfiguration`，排序语义保持。
- [x] AC-6: catalog 对 QUERY/CACHE_INVALIDATION capability-role overclaim 在 discovery fail closed；identity missing/duplicate/unsupported 继续显式失败。
- [x] AC-7: JDBC 仅声明 QUERY，query-cache 仅声明 CACHE_INVALIDATION；descriptor capability 精确且不可变，TCK 与真实能力一致。
- [x] AC-8: 受影响模块 compile/unit/context/TCK/compatibility tests 实际通过，未运行项如实记录。
- [x] AC-9: README、migration、canonical workitem 状态和实施证据同步，先 review 后进入 `READY_FOR_SIGNOFF`。

## Contract / Data / Security Constraints

- API or event contract: 只允许新增兼容 API/异常或提前失败；不得删除/改签名现有公共 API/SPI。
- data and migration: 无数据库或不可逆迁移。
- compatibility and rollback: 所有变更可通过回退本分支提交恢复；legacy aggregate 继续工作一个兼容周期。
- permissions and secrets: 不涉及权限或凭据；不得降低 namespace/security/data correctness 现有边界。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/2/3 | must-pass | major | API architecture guard + inventory commands | 9.4.0 baseline counts | test output + final counts |
| AC-4/5 | must-pass | major | GraphQL/DataViewer/addon affected unit/context tests | existing addon tests | Maven results |
| AC-6/7 | must-pass | major | API/core/JDBC/TCK/cache tests | 9.4.0 provider tests | Maven results |
| AC-8 | must-pass | major | affected reactor `-pl ... -am` | none after code changes | exact commands/results |
| large authority/replay | non-goal | N/A | prohibited | 9.4.0 signoff only | not run |

## Validation Budget and Evidence Sufficiency

- assurance_level: standard
- lightweight_validation: inventory/guard tests and focused module unit tests, each expected `<5m`.
- medium_validation: affected addon/reactor tests expected `5-30m` only if needed after focused tests.
- expensive_validation: none approved.
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: only if focused/affected evidence cannot decide compatibility or final release candidate requires it.
- estimated_full_chain_wall_clock: not-estimated; not approved for this delivery.
- full_chain_prerequisites: explicit user approval.
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: none in current scope.
- maximum_expensive_attempts: 0
- reusable_evidence: 9.4.0 accepted 32-module reactor, dependency graph and launcher artifact evidence for unchanged surfaces.
- stop_when_evidence_is_sufficient: all changed modules and guard/TCK/context tests pass, diff review finds no compatibility expansion, inventories are reproducible.
- validation_not_required: full reactor, five-DB matrix, Step 5/7, authority/replay/source-seal, CI/tag/release/publish, `mvn install`.

## Waiver Policy

- waivable_items: documented deep legacy imports requiring 9.5.0 physical split.
- authorized_role: repository owner.
- non_waivable_guards: no new bypass, capability truthfulness, API/SPI compatibility, security/namespace/data correctness.
- required_risk_record: module, current dependency reason, target physical boundary and 9.5.0 breaking impact.

## Risks and Open Questions

- known_risks: static guard detects governed source patterns but cannot prove all dynamic call graphs; focused tests and review remain required.
- open_questions: none

## Ultra Execution Contract

- 先读取本文件及 9.4.0 roadmap/README/migration/signoff；仓库未发现 `AGENTS.md`。
- 在 scope 内自主决定具体文件、类和实现结构。
- 如需改变目标、范围、兼容或安全边界，设置 `NEEDS_REPLAN` 并停止扩展。
- 使用 Maven reactor `-pl ... -am`，不得运行 `mvn install`。
- 未经用户明确批准，不得运行预计超过 30 分钟或 authority/replay/rehearsal/source-seal 大型链路。
- 完成后填写 `Implementation Result`，并将状态改为 `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

- implementation_summary: `15 -> 14` direct aggregate dependencies and `12/114 -> 11/110` legacy import inventory; GraphQL/DataViewer stable request adoption; nine auto-config hard references removed; catalog discovery role validation and JDBC/cache TCK hardening added.
- changed_paths: model API architecture guard; core catalog/tests; TCK/JDBC/cache provider tests; GraphQL/DataViewer request paths; addon auto-config ordering; Odoo POM; 9.4.0/9.4.1 governance docs.
- tests_and_results:
  - API/core/TCK/JDBC affected reactor: BUILD SUCCESS; API 8, core 6, TCK 3, JDBC 5 tests.
  - Web/GraphQL/DataViewer focused reactor: BUILD SUCCESS; web 4, DataViewer 21, GraphQL 8 tests.
  - addon auto-config/cache affected reactor: 23/23 modules BUILD SUCCESS; DataViewer 6, Mongo 6, Vector 7, cache 23, GraphQL 5 selected tests; all changed production modules compiled.
  - inventory guard: 14 exact direct dependencies, 11 modules/110 files, one approved bypass, zero unapproved bypass.
- manual_or_experience_evidence: N/A
- deviations: initial focused run exposed a web test fixture claiming QUERY/CACHE_INVALIDATION without implementing the roles; the fixture was corrected and the full affected selection then passed. No production provider had this defect.
- residual_risks: same-session review is not organizationally independent; 14 direct dependencies, 110 old-package files and one approved bypass remain for a separately authorized 9.5.0; owner-excluded large authority flows were not run.
- reused_evidence: 9.4.0 signed-off baseline for unchanged full-reactor/artifact surfaces
- omitted_validation_and_reason: full chain and authority flows are owner-excluded and not approved
- readiness: READY_FOR_SIGNOFF

## References

- requirement / issue: user-approved 9.4.1 SPI v2 adoption/hardening continuation
- architecture / glossary: `docs/9.4.0/model-spi-v2-migration.md`
- related work items: `docs/9.4.0/workitems/FEATURE-v934-v940-speed-forward.md`
