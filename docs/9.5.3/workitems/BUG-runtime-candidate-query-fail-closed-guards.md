---
doc_type: delivery-spec
delivery_type: bug
version: 9.5.3
ticket: BUG-runtime-candidate-query-fail-closed-guards
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: owner
approved_at: 2026-07-31
open_questions: []
---

# Delivery Spec: Runtime candidate-query fail-closed guards

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 修复 candidate-query overlay 首次正式验收发现的权限 action、selected source provenance、
  backend mode 与 close cleanup 缺口。
- canonical_path:
  `docs/9.5.3/workitems/BUG-runtime-candidate-query-fail-closed-guards.md`

## Goal

- version_goal: 使 9.5.3 Runtime candidate-query internal primitive 满足原 delivery spec 的
  AC-5、AC-8、AC-9 及 request-local cleanup 决策，重新进入正式验收。
- target_outcome:
  - validate/execute 始终按自身 phase 授权，调用方不能通过传入错误 action 改变判定。
  - selected source 必须是 registry record 对应的实际 live Runtime-managed external Bundle；
    configured/JAR/path mismatch 全部在 detached open 前 fail closed。
  - 解析后的 candidate QueryModel 必须是普通 JDBC QueryModel；其他 backend 在 semantic execution
    前返回稳定 unsupported。
  - close 主动清理 request-local script/model/catalog/loader/ApplicationContext 引用并保持幂等。
- critical_outcomes: permission action、Bundle ownership/provenance 和 backend mode 都是不可豁免的
  fail-closed guards；修复不得改变现有 live query 行为。
- success_is_sufficient_when: AC-1 至 AC-5 全部通过，原 candidate real SQLite 与 affected
  engine/Runtime lanes 通过，正式 re-signoff 不再存在 core blocker。

## Scope

- in_scope:
  - engine candidate session phase action、resolved backend guard 与 detached close lifecycle。
  - Runtime internal service 对 registry record 与实际 live external Bundle identity/path 的绑定。
  - 上述缺陷的长期自动化回归测试和原 feature 状态/证据回写。
- affected_modules:
  - `foggy-dataset-model-engine`
  - `foggy-runtime-api`
  - `docs/9.5.3`
- external_dependencies: none；使用现有 Mockito、Spring test、SQLite 与临时目录/JAR fixture。

## Non-Goals

- out_of_scope:
  - 不新增 REST route、workspace API/store、publish/refresh、Console、Git、launcher 或 Maven 依赖。
  - 不为 Mongo/vector/preaggregation 实现 candidate query；只稳定拒绝非 JDBC backend。
  - 不改变 registry restore 或现有 Bundle CRUD 的公共语义；candidate 入口自行验证 provenance。
  - 不扩展 pivot、Compose/CTE、Semantic SQL、memory-grid、synthetic member 或 JAR 多 Namespace。
- do_not_touch:
  - `addons/foggy-runtime-console/frontend`
  - `foggy-mcp-launcher`
  - Model SPI v2 公共模块、POM 和已签收 spike 历史
- non_blocking_or_waivable_items: 标准 `jar:` fixture 不代表所有 nested/fat-JAR packaging，继续作为
  已声明 scoped risk，不在本 BUG 扩展。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| candidate session 根据调用方法覆盖 permission action | phase 是执行语义的唯一真值，不能信任 caller action | validate=`VALIDATE`，execute=`EXECUTE`；保留 identity/session |
| Runtime 同时验证 registry record 与实际 live Bundle | registry name 本身不能证明当前 live ownership | 必须 external、enabled、同 name/Namespace、同规范化真实 path |
| resolved QueryModel 仅接受生产 JDBC implementation | request shape 不能代表 backend | 非 JDBC 在 semantic/cache/DB 调用前返回 `CANDIDATE_MODE_UNSUPPORTED` |
| close 清理 request-local authority 并禁止复用 | try-with-resources 之外也要满足明确 lifecycle 契约 | 幂等；不清理或修改任何 live catalog/cache/Bundle |

## Acceptance Criteria

- [x] AC-1: candidate `validate` 固定 `PermissionAction.VALIDATE`，`execute` 固定
  `PermissionAction.EXECUTE`；空 action 或 caller 注入相反 action 均不能改变结果，action-sensitive
  permission resolver 回归测试通过。
- [x] AC-2: Runtime candidate open 只接受 enabled registry record 对应的实际 live
  `ExternalFileBundle`/external definition，且 name、Namespace、规范化真实 path 一致；configured、JAR、
  absent、disabled、wrong Namespace 和 path mismatch 均以稳定 `CANDIDATE_SOURCE_INVALID` 在 factory
  open 前拒绝。
- [x] AC-3: detached resolution 得到非 JDBC QueryModel 时，validate/execute 均在 semantic service、
  query facade、cache 和 backend 调用前返回稳定 `CANDIDATE_MODE_UNSUPPORTED`。
- [x] AC-4: close 幂等清理 request-local catalog、model loader、table loader、FSScript loader/cache 和
  validation ApplicationContext 引用；close 后 session 不可复用，且 live state 不变。
- [x] AC-5: 原 feature AC-1 至 AC-9 的通过证据保持有效；真实 SQLite overlay/external/JAR、权限、
  stale、cache isolation 和 engine/Runtime compatibility focused/affected lanes 全部通过。

## Contract / Data / Security Constraints

- API or event contract: 不新增或改变 REST、DTO、Header、事件、Model SPI v2 或配置键；错误继续使用
  existing candidate stable code。
- data and migration: 无数据库、registry schema 或 filesystem migration；只读比较 record/live path。
- compatibility and rollback: live query、Bundle CRUD/restore 与 validation API 默认路径保持行为兼容；
  回滚可整体回退本 BUG 和原未提交 feature。
- permissions and secrets: 不记录 Authorization、registry 内容、数据库连接或绝对 candidate 文件内容；
  phase action 与 ownership mismatch 必须 fail closed。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1 | must-pass | critical | candidate unit + action-sensitive permission integration | model permission tests、82-test lane | opposite-action injection rejected/overridden |
| AC-2 | must-pass | critical | Runtime service provenance unit tests | bundle/controller compatibility tests | configured/JAR/path mismatch before factory open |
| AC-3 | must-pass | major | non-JDBC QueryModel focused test double | advanced-mode guards | stable unsupported and zero semantic invocation |
| AC-4 | must-pass | major | detached lifecycle unit/integration tests | validation isolation tests | idempotent close、cleared refs、closed rejection、live unchanged |
| AC-5 | must-pass | major | original focused and affected engine/Runtime lanes | 82/52/63 prior evidence | updated selections all pass |

## Validation Budget and Evidence Sufficiency

- assurance_level: elevated
- lightweight_validation:
  - `git diff --check` 与每个 untracked 文件等效 whitespace check，`<5m`。
  - phase/provenance/backend/close focused tests，单次 `<5m`。
- medium_validation:
  - 原 engine candidate/permission focused lane 与 engine affected lane，预计各 `<5m` 至 `10m`。
  - Runtime affected lane，预计 `<5m` 至 `10m`。
- expensive_validation: none；不运行完整 reactor、addon 数据库矩阵或 launcher package。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: 若修复需要公共 API/SPI、registry schema/restore 语义或 addon production
  改动，设置 `NEEDS_REPLAN`，不自行扩展。
- maximum_expensive_attempts: 0
- reusable_evidence:
  - 原 feature 的 82/82、52/52、63/63 测试记录中未受断言语义变化影响的条目。
  - 首次 rejected signoff 中 AC-1 至 AC-4、AC-6、AC-7 的源码和真实 SQLite/JAR 证据。
- stop_when_evidence_is_sufficient: 四个缺口均有直接负向回归保护，原 focused/affected lanes 通过，
  working-tree checks 通过；此后停止扩展。
- validation_not_required:
  - 完整 Maven reactor、Console build/Playwright、launcher package、数据库矩阵
  - authority/replay/rehearsal/source-seal/tag/release/publish

## Waiver Policy

- waivable_items: none
- authorized_role: owner
- non_waivable_guards: phase-specific permission、selected Bundle provenance、JDBC-only mode、证据真实性
- required_risk_record: nested/fat-JAR 与绕过 watcher 的 filesystem drift 继续保留为原 feature scoped risk。

## Bug Context

- bug_source: acceptance-found
- severity: critical
- environment: 9.5.3 candidate-query overlay，当前 `main` 未提交工作树。
- current_behavior:
  - validate 默认使用 EXECUTE action，engine caller 可向 execute 注入其他 action。
  - enabled record 只按 name/Namespace 关联 live Bundle，未验证 external 类型/path。
  - 非 JDBC QueryModel 可进入 execute；close 后 detached loader/catalog 引用保留至 GC。
- expected_behavior: phase、selected source ownership、backend 和 close lifecycle 均按上述 confirmed
  decisions fail closed。
- reproduction_steps: 见
  `docs/9.5.3/acceptance/FEATURE-runtime-candidate-query-overlay-signoff.md` 的 AC-5、AC-8、AC-9
  source trace 与 session cleanup evidence。
- reproduction_status: confirmed
- existing_evidence: 首次正式验收记录；原 197 项测试通过但缺少相应负向选择。
- existing_tests: `CandidateQuerySessionTest`、`CandidateQueryRealExecutionTest`、
  `DetachedModelValidationSessionBuilderTest`、`RuntimeCandidateQueryServiceTest`。
- regression_protection: required
- waiver_reason_and_risk: N/A

## Risks and Open Questions

- known_risks:
  - path 比较必须接受语义等价的 relative/absolute 表达，同时拒绝 symlink/不同真实目录。
  - cleanup 测试应验证可观察 lifecycle，不依赖不稳定的强制 GC。
  - JDBC-only 判定必须发生在 detached model 成功解析后、任何 semantic/backend execution 前。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目 `CLAUDE.md`、原 feature spec 与首次 signoff。
- 在 scope 内自主决定具体文件、类和实现结构；优先建立失败回归测试，再完成生产修复。
- 不得通过删除权限/ownership/cache 断言、放宽错误或改测试 fixture 掩盖缺陷。
- 如需改变目标、范围、兼容或安全边界，设置 `NEEDS_REPLAN` 并停止扩展。
- 运行与改动面匹配的验证，记录精确命令、测试数量、结果和未运行原因。
- 未经用户明确批准，不得运行完整 reactor、数据库矩阵或任何大型
  authority/replay/rehearsal/source-seal 链。
- 完成后填写 `Implementation Result`，并将状态改为 `READY_FOR_SIGNOFF`；不得自行设置
  `ACCEPTED`。

## Implementation Result

- implementation_summary:
  - candidate `validate`/`execute` 在 request-local catalog pin 之后分别覆盖为
    `PermissionAction.VALIDATE`/`EXECUTE`，调用方传入相反 action 不能改变 phase 授权。
  - Runtime internal service 现在把 enabled registry record 与实际 live
    `ExternalFileBundle`/`ExternalBundleDefinition` 的 name、Namespace、definition/base/root real path
    绑定；configured、JAR、absent、disabled、错误 Namespace/name/path 和 symlink 在 factory open 前
    稳定 fail closed。
  - detached resolution 后只允许 `JdbcQueryModelImpl` 进入 semantic service；其他 QueryModel 在
    validate/execute 两个 phase 都返回 `CANDIDATE_MODE_UNSUPPORTED`。
  - detached session close 现在幂等清理 request-local catalog、root/file FSScript loader、TM/QM
    loader、source cache 和 validation ApplicationContext 引用，并阻止 closed session 复用。
- changed_paths:
  - engine implementation：
    `foggy-dataset-model-engine/src/main/java/com/foggyframework/dataset/model/candidate/DefaultCandidateQueryFactory.java`、
    `foggy-dataset-model-engine/src/main/java/com/foggyframework/dataset/model/validation/DetachedModelValidationSessionImpl.java`
  - engine regression evidence：`CandidateQuerySessionTest`、`CandidateQueryRealExecutionTest`、
    `DetachedModelAuthoringFoundationProbeTest`
  - Runtime implementation/evidence：`RuntimeCandidateQueryService`、
    `RuntimeCandidateQueryServiceTest`
  - delivery record：本 BUG canonical spec
- tests_and_results:
  - 修复前定向回归：engine 2 classes / 14 tests 中 2 failures + 1 error，分别复现 caller action
    未覆盖、非 JDBC 未拒绝、detached catalog field/cleanup 缺失；Runtime testCompile 因预期新增的
    `SystemBundlesContext` constructor dependency 失败。以上失败随后均由生产修复转绿。
  - `mvn -B -ntp -pl foggy-dataset-model-engine -DskipITs
    -Dtest=CandidateQuerySessionTest,CandidateQueryRealExecutionTest,DetachedModelAuthoringFoundationProbeTest,DetachedModelValidationSessionBuilderTest,QueryFacadeCatalogIdentityTest,SemanticRequestContextTest,FieldAccessPermissionStepTest,ModelPermissionServiceTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：
    8 classes / 84 tests，0 failures/errors/skips，37.290s。
  - `mvn -B -ntp -pl foggy-dataset-model-engine -DskipITs
    -Dtest=DbModelAutoConfigurationTest,QueryFacadeImplTest,PhysicalColumnPermissionIT,SemanticQueryServiceV3ValidatePipelineTest,QueryCacheProviderTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：
    5 classes / 52 tests，0 failures/errors/skips，29.847s。
  - `mvn -B -ntp -pl foggy-runtime-api -am -DskipITs
    -Dtest=RuntimeCandidateQueryServiceTest,RuntimeModelValidationIsolationTest,RuntimeBundlesControllerTest,RuntimeCapabilitiesControllerEnabledTest,RuntimeModelsControllerCompatibilityTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：
    5 classes / 64 tests，0 failures/errors/skips；14-module reactor 全部成功，1:00。
  - `git diff --check` 与 15 个 untracked 文件逐项
    `git diff --no-index --check /dev/null <file>`：通过；scope/route/high-confidence secret checks
    无命中。
- manual_or_experience_evidence:
  - 真实 SQLite candidate fixture 的 action-sensitive permission resolver 只接受 validate=`VALIDATE`、
    execute=`EXECUTE`；两个 caller 均故意传入相反 action，validate 不执行数据库、execute 仍返回受
    row predicate 约束的真实结果。
  - Runtime provenance tests 接受语义等价的 relative/absolute real directory，拒绝 configured、
    absent、JAR、错误 name/Namespace、不同目录和 symlink，并断言 factory 零调用。
  - 普通 `QueryModel` test double 在 validate/execute 均稳定 unsupported，semantic service 零调用；
    默认正向 fixture 改为 production JDBC implementation 类型。
  - 真实 detached production load 后 close 两次；catalog、FSScript/source cache 清空，loader/catalog
    fields 与 validation ApplicationContext 置空，closed validation/resolve/execution context 被拒绝，
    live catalog identity、script cache、Bundle cache/inventory 和 source revision 保持不变。
- deviations: none
- residual_risks:
  - 动态 JAR fixture 继续只证明标准 `jar:` Resource，不覆盖所有 nested/fat-JAR packaging。
  - source revision 继续只覆盖 Runtime 已知 committed mutation；绕过 registry/watcher 的 live
    filesystem 变更受既有部署约束和后续 workspace stale/revalidate 规则管理。
  - pivot、Compose/CTE、Semantic SQL、memory-grid、synthetic member、workspace store/resource API、
    publish/rollback、Git 和 JAR 多 Namespace 仍是明确 non-goal/后续 workitem。
- reused_evidence:
  - 首次 signoff 中 AC-1 至 AC-4、AC-6、AC-7 的 source/SQLite/external/standard-JAR 证据继续有效；
    本轮 focused/affected lanes 已在修复后重新覆盖对应测试选择。
  - `DetachedModelValidationSessionBuilderTest`、catalog identity、field/model/physical permission、cache
    provider 与 Runtime model/bundle compatibility tests 均纳入上述通过记录。
- omitted_validation_and_reason:
  - 未运行完整 Maven reactor、Console build/Playwright、launcher package、数据库矩阵、
    authority/replay/rehearsal/source-seal/tag/release/publish；delivery spec 明确排除，且三条更新后的
    focused/affected lane 已覆盖 changed surface。
- readiness: READY_FOR_SIGNOFF

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-07-31
- acceptance_record:
  `docs/9.5.3/acceptance/FEATURE-runtime-candidate-query-overlay-signoff-r2.md`
- blocking_items: none
- follow_up_required: no

## References

- requirement / issue: owner 已确认推进首次 rejected signoff 的 remediation 1 至 4。
- architecture / glossary:
  - `docs/architecture/runtime-and-model-lifecycle.md`
  - `docs/9.5.3/runtime-model-authoring-design.md`
- related work items:
  - `docs/9.5.3/workitems/FEATURE-runtime-candidate-query-overlay.md`
  - `docs/9.5.3/acceptance/FEATURE-runtime-candidate-query-overlay-signoff.md`
