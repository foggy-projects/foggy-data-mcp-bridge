---
doc_type: delivery-spec
delivery_type: cross-module
version: 9.5.3
ticket: FEATURE-runtime-candidate-query-overlay
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: owner
approved_at: 2026-07-31
open_questions: []
---

# Delivery Spec: Runtime candidate-query overlay

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结 request-local candidate model 的解析、验证和受治理查询执行边界，作为后续
  authoring workspace API 的首个生产级 Runtime 原语。
- canonical_path: `docs/9.5.3/workitems/FEATURE-runtime-candidate-query-overlay.md`

## Goal

- version_goal: 在不创建临时 Namespace 或持久 candidate catalog 的前提下，让一个不可变草稿
  Bundle revision 能复用目标 Namespace 的只读依赖、数据源与权限流水线完成 validate/query。
- target_outcome:
  - 建立 request-local candidate session，固定草稿内容 revision、目标 Namespace live source
    revision、detached catalog 和 datasource binding identity。
  - 普通 JDBC Semantic Query validate/execute 使用草稿 QM/TM/FSScript，而不是回退到同名 live
    QM，并复用既有 permission、field/row filter、SQL execution 和 physical-column guard。
  - candidate 查询不读写共享 L1/L2 cache，不发布 catalog，不修改 Bundle inventory/source
    revision，并在 live source 或草稿内容漂移时 fail closed。
- critical_outcomes:
  - validate 与 execute 绑定同一 candidate revision 和同一 request-local model resolution。
  - 新执行路径不能绕过 Namespace、Authorization、模型权限、字段/行权限或物理列权限。
  - source overlay 只能替换所选 Runtime-managed Bundle 自身资源；遮蔽 JAR 或其他 Bundle 必须拒绝。
  - 仅新增内部 production port/service，不新增公共 REST、Model SPI v2 或稳定外部 API。
- success_is_sufficient_when: AC-1 至 AC-9 全部通过，真实 SQLite focused tests 返回草稿模型结果，
  权限与 stale/cache/live-state guards 有自动化证据，affected engine/runtime-api lanes 通过。

## Scope

- in_scope:
  - `foggy-dataset-model-engine` request-local candidate identity、detached model resolution 和 governed
    normal JDBC semantic query execution。
  - `foggy-runtime-api` 内部 candidate query 编排：校验 Runtime-managed source Bundle、Namespace、
    base source revision、执行模式和稳定诊断。
  - 草稿 Bundle 对自身 live 版本的 overlay，以及对同 Namespace external/JAR Bundle 和 FSScript
    的只读依赖。
  - candidate query 的共享 cache 禁用、session cleanup、成功/失败 live-state isolation。
  - canonical lifecycle 架构与 9.5.3 版本文档同步。
- affected_modules:
  - `foggy-dataset-model-engine`
  - `foggy-runtime-api`
  - `docs/architecture`
  - `docs/9.5.3`
- external_dependencies: none；复用现有 Spring、JUnit、SQLite/JDBC 与查询权限测试基础。

## Non-Goals

- out_of_scope:
  - 不新增 Console UI、public REST endpoint、workspace CRUD/store、`.fsscript` save/export、Git、
    release package、publish、refresh、promotion 或 rollback。
  - 不实现 JAR 多 Namespace binding，也不允许编辑或覆盖 JAR Bundle。
  - 不支持 candidate pivot、Compose、CTE、Semantic SQL、memory-grid、pre-aggregation、synthetic
    member query 或跨 candidate session cache reuse；这些模式必须显式拒绝，不能静默走 live。
  - 不持久化 candidate catalog、compiled model、query result 或临时 Namespace。
- do_not_touch:
  - `addons/foggy-runtime-console/frontend`
  - `foggy-mcp-launcher`
  - Model SPI v2 公共模块与 Maven 依赖图
  - 9.5.3 已签收 spike 的 Implementation Result 和 acceptance 历史
- non_blocking_or_waivable_items:
  - candidate 模式的高级 semantic/pivot/compose 能力属于明确 non-goal；稳定 unsupported 错误即可。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 新增 engine-internal candidate execution port，不新增 public REST | 先冻结安全执行原语，再由 workspace API 复用 | 不改变现有 `/api/v1/query/*` 行为或 DTO |
| candidate source 必须对应一个 Runtime-managed external Bundle | 首期只允许一个明确 writable Bundle | configured external/JAR 继续只读 |
| session 打开时计算内容寻址 candidate revision | 防止 path/branch 成为可移动执行身份 | revision 至少覆盖草稿内 TM/QM/FSScript 的相对路径和字节 |
| caller 必须携带创建工作区时的 base source revision | live 依赖漂移不能被静默接受 | open、query 前后均需 currentness guard |
| 普通 JDBC semantic validate/execute 复用现有查询流水线 | 权限、过滤、SQL guard 和结果语义不能产生旁路 | root model resolution 与 Bundle context 必须显式替换为 candidate view |
| candidate 禁用共享 L1/L2 cache 与 pre-aggregation | request-local identity 尚无持久 cache lifecycle | 不能读、写或失效 live cache |
| overlay 只允许 selected source Bundle 自身资源 | detached source-first 是机制，不是 authoring policy | 遮蔽其他 external/JAR Bundle fail closed |
| session close 释放 script/model/catalog 引用 | 不持久化第二套 catalog | 不注册 Bundle、Namespace 或 refresh generation |

## Acceptance Criteria

- [x] AC-1: session 计算稳定的 content-addressed `candidateRevision`，记录
  `baseSourceRevision`、Namespace 和 source Bundle；草稿内容改变会产生不同 revision 或在活动
  session 中被拒绝。
- [x] AC-2: candidate QM/TM/FSScript 使用 detached production loader 构建为带 identity 的
  request-local resolution；validate 与 execute 使用同一 model instance/catalog resolution。
- [x] AC-3: 真实 SQLite 查询证明结果来自草稿 QM/TM overlay；同名 live QM 不能被查询路径重新加载。
- [x] AC-4: candidate QM 可查询同 Namespace external/JAR TM 及其 FSScript 依赖，并保持这些
  Bundle 只读。
- [x] AC-5: candidate execute/validate 复用既有 Authorization、模型权限、字段/行权限和物理列
  guard；拒绝、非法权限或受限列均 fail closed。
- [x] AC-6: candidate 查询不与共享 L1/L2 cache provider 交互，不启用 pre-aggregation；成功、DSL
  失败、数据库失败和 close 后 live catalog/cache/Bundle inventory/source revision 不变。
- [x] AC-7: request 的 `baseSourceRevision` 已过期，或 live source 在执行窗口内变化时，返回稳定
  `CANDIDATE_SOURCE_STALE`，不回退到 live model 或发布 candidate。
- [x] AC-8: 草稿只可覆盖 selected Runtime-managed Bundle 自身 TM/QM；遮蔽 JAR、configured 或其他
  Runtime-managed Bundle 返回稳定 `CANDIDATE_OVERLAY_FORBIDDEN`。
- [x] AC-9: Runtime 内部结果携带 candidate/base revision 和明确 phase/diagnostics；unsupported
  candidate mode 显式失败；现有 Runtime query/validate 行为与测试保持兼容。

## Contract / Data / Security Constraints

- API or event contract: 不新增 REST route、Header、公开 DTO、事件或 Model SPI v2 capability；新增
  Java 类型限于 engine/runtime-api 内部调用边界。现有 API 行为不变。
- data and migration: 不新增表、registry schema、workspace 目录或迁移；测试只使用临时 Bundle/JAR
  和 SQLite 数据库。
- compatibility and rollback: production 改动可通过删除 candidate port/service 回退；现有 live query
  默认路径必须保持二进制/源兼容及行为兼容。
- permissions and secrets: candidate query 只透传 opaque Authorization，并沿用非空
  `RequestIdentity`、permission session、field/row/physical-column guard；不得记录 token、SQL 参数中的
  secret 或数据库连接凭据。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/AC-2 | must-pass | major | candidate identity/session unit + production loader integration | detached probe、catalog identity tests | revision、same-instance/pin、close assertions |
| AC-3/AC-4 | must-pass | major | real SQLite query with draft/external/JAR/FSScript fixtures | 9.5.3 spike JAR/external fixture | returned rows and resource provenance |
| AC-5 | must-pass | critical | focused permission integration tests including allow/deny/row/field/physical guard | existing permission ITs | candidate-path permission outcomes and no bypass |
| AC-6/AC-7 | must-pass | critical | cache spy + live identity snapshots + stale mutation tests | validation isolation tests | zero cache interaction、stable errors、unchanged live state |
| AC-8 | must-pass | critical | external/JAR/other-managed overlay policy tests | spike source-first evidence | forbidden-owner diagnostics before execution |
| AC-9 | must-pass | major | runtime-api service tests + existing query/model affected tests | Runtime query and model tests | stable metadata/errors and compatibility pass |

## Validation Budget and Evidence Sufficiency

- assurance_level: elevated
- lightweight_validation:
  - `git diff --check` 和每个 untracked 文件的等效 whitespace check，单次 `<5m`。
  - candidate session/overlay/stale focused unit tests，单次 `<5m`。
- medium_validation:
  - model-engine candidate real-query、permission、cache/isolation focused lane，预计 `5-20m`。
  - runtime-api candidate orchestration与现有 models/query affected lane，预计 `5-20m`。
- expensive_validation: none by default；不运行完整 Maven reactor、数据库矩阵或 launcher package。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none；内部能力、无发布候选且 focused/affected evidence 可覆盖。
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: 若实现被迫新增公共 API/SPI、改变权限语义或跨模块共享运行拓扑，设置
  `NEEDS_REPLAN`，不自行扩大测试。
- maximum_expensive_attempts: 0
- reusable_evidence:
  - `DetachedModelAuthoringFoundationProbeTest`
  - `DetachedModelValidationSessionBuilderTest`
  - `RuntimeModelValidationIsolationTest`
  - 现有 QueryFacade permission/cache/physical-column focused tests
- stop_when_evidence_is_sufficient: AC-1 至 AC-9 均有直接自动化证据，真实 SQLite candidate query
  和权限/stale/cache guards 通过，engine/runtime-api affected lanes 通过；此后停止扩展。
- validation_not_required:
  - Console build/Playwright
  - public Runtime E2E（本 workitem 无新 endpoint）
  - launcher package、完整 reactor、数据库矩阵
  - authority/replay/rehearsal/source-seal/tag/release/publish

## Waiver Policy

- waivable_items: none；unsupported advanced modes 是 non-goal，不是失败项。
- authorized_role: owner
- non_waivable_guards: candidate/live identity、权限 fail-closed、cache isolation、source stale guard、
  overlay ownership 和证据真实性。
- required_risk_record: 任一未能复用完整查询 step 的限制必须记录具体 step、影响模式和 fail-closed
  行为；不得描述为等价支持。

## Risks and Open Questions

- known_risks:
  - 现有 semantic service 与 QueryFacade 多处持有 live loader/context，局部替换必须使用显式
    request-local pin，不能依赖全局或 ThreadLocal 偷换。
  - permission/filter step 中 synthetic member 等高级路径会再次使用 live loader；本 workitem 显式
    拒绝这些 candidate 模式。
  - 动态 JAR fixture 只代表标准 `jar:` Resource，不代表所有 nested/fat-JAR packaging。
  - source revision 只能证明 Runtime 已知的 committed mutation；外部绕过 watcher/registry 的文件
    修改仍由 candidate content check 与既有部署约束共同处理。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目 `CLAUDE.md`、相关架构文档和适用专项技能。
- 在 scope 内自主决定具体文件、类和实现结构；不得以 ThreadLocal/global bean mutation 置换 live
  loader、Bundle context 或权限服务。
- 如需改变目标、范围、已确认决策、兼容或安全边界，设置 `NEEDS_REPLAN` 并停止扩展。
- 优先建立 candidate real-query、permission、stale 和 overlay 失败测试，再完成生产实现。
- 运行与改动面匹配的验证，记录精确命令、测试数量、结果和未运行原因。
- 未经用户明确批准，不得主动运行预计超过 30 分钟或包含
  authority/replay/rehearsal/source-seal 的大型链路。
- 达到 evidence sufficiency 后停止，不追加 Console、launcher 或全数据库验证。
- 完成后填写 `Implementation Result`，并将状态改为 `READY_FOR_SIGNOFF`；不得自行设置
  `ACCEPTED`。

## Implementation Result

- implementation_summary:
  - 新增 engine-internal candidate factory/session/source/identity/result 与稳定错误分类，使用
    TM/QM/FSScript 内容寻址 revision、base source/content 双 stale guard、symlink/path guard 和
    selected-Bundle overlay ownership guard。
  - detached production session 现在可返回 request-local `CatalogResolution` 和只读 Bundle view；
    semantic service 与 QueryFacade 只在显式 candidate context 下使用该 pin，live 路径保持原行为。
  - 普通 JDBC candidate validate/execute 复用 Authorization、模型/字段/行/物理列权限与数据源执行，
    并关闭 L1/L2、pre-aggregation、hybrid query；高级模式稳定 fail closed。
  - 新增无 REST route 的 Runtime 内部编排，只接受 enabled Runtime-managed Bundle，返回精确
    candidate/base revision、catalog identity、phase 和 diagnostics。
- changed_paths:
  - engine candidate port：
    `foggy-dataset-model-engine/src/main/java/com/foggyframework/dataset/model/candidate/**`
  - detached/catalog/query integration：`DbModelAutoConfiguration`、`DetachedModelValidationSession*`、
    `SemanticRequestContext`、`ModelResultContext`、`SemanticQueryServiceV3Impl`、`QueryFacadeImpl`
  - engine tests：`CandidateQuerySessionTest`、`CandidateQueryRealExecutionTest`、
    `DbModelAutoConfigurationTest`
  - Runtime internal orchestration：`RuntimeCandidateQueryService`、`RuntimeApiAutoConfiguration`、
    `RuntimeCandidateQueryServiceTest`
  - architecture/version docs：`docs/architecture/runtime-and-model-lifecycle.md`、
    `docs/9.5.3/runtime-model-authoring-design.md`、`docs/9.5.3/README.md` 与本 spec
- tests_and_results:
  - `mvn -B -ntp -pl foggy-dataset-model-engine -am -DskipTests compile`：
    13-module affected reactor compile 通过。
  - `mvn -B -ntp -pl foggy-dataset-model-engine -DskipITs
    -Dtest=CandidateQuerySessionTest,CandidateQueryRealExecutionTest,DetachedModelAuthoringFoundationProbeTest,DetachedModelValidationSessionBuilderTest,QueryFacadeCatalogIdentityTest,SemanticRequestContextTest,FieldAccessPermissionStepTest,ModelPermissionServiceTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：
    8 classes / 82 tests，通过，30.856s。
  - `mvn -B -ntp -pl foggy-dataset-model-engine -DskipITs
    -Dtest=DbModelAutoConfigurationTest,QueryFacadeImplTest,PhysicalColumnPermissionIT,SemanticQueryServiceV3ValidatePipelineTest,QueryCacheProviderTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：
    5 classes / 52 tests，通过，29.602s。
  - `mvn -B -ntp -pl foggy-runtime-api -am -DskipITs
    -Dtest=RuntimeCandidateQueryServiceTest,RuntimeModelValidationIsolationTest,RuntimeBundlesControllerTest,RuntimeCapabilitiesControllerEnabledTest,RuntimeModelsControllerCompatibilityTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：
    5 classes / 63 tests，通过，58.457s。
  - `git diff --check` 与全部 untracked 文件逐文件
    `git diff --no-index --check /dev/null <file>`：通过。
- manual_or_experience_evidence:
  - 源码审计确认没有新增 controller/route、ThreadLocal/global bean mutation、Model SPI、Maven 依赖、
    launcher 或 Console 改动。
  - 真实 SQLite fixture 比对草稿查询结果和数据库结果；external dependency 使用真实 filesystem
    Bundle，JAR dependency 使用动态标准 `jar:` Spring Resource，而非 Mockito Resource。
- deviations: none
- residual_risks:
  - 动态 JAR fixture 覆盖标准 `jar:` Resource，不代表所有 fat-JAR/nested-JAR packaging；后续打包
    workitem 如改变装配形态需补对应验证。
  - source revision guard 覆盖 Runtime 已知 committed mutation；绕过 watcher/registry 的 live
    filesystem dependency 变更仍受既有部署约束，正式 workspace 需保留 stale/revalidate 提示。
  - pivot、Compose/CTE、Semantic SQL、memory-grid、synthetic member、workspace store/resource API、
    publish/rollback、Git 和 JAR 多 Namespace 均未实现，属于显式 non-goal/后续 workitem。
- reused_evidence:
  - `DetachedModelAuthoringFoundationProbeTest` 与
    `DetachedModelValidationSessionBuilderTest`：2 classes / 5 tests，已独立重跑。
  - `QueryFacadeCatalogIdentityTest`、`SemanticRequestContextTest`、既有 permission/cache/physical-column
    与 Runtime query/model/bundle compatibility tests，均纳入上述 affected lanes。
- omitted_validation_and_reason:
  - 未运行完整 Maven reactor、Console build/Playwright、launcher package、数据库矩阵、
    authority/replay/rehearsal/source-seal/tag/release/publish；delivery spec 明确排除，且 focused +
    affected evidence 已覆盖 changed surface。
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

- requirement / issue: owner 要求按 9.5.3 已验收路线依次推进 candidate-query overlay 1 至 6。
- architecture / glossary:
  - `docs/architecture/runtime-and-model-lifecycle.md`
  - `docs/architecture/runtime-permissions-and-preaggregation.md`
  - `docs/9.5.3/runtime-model-authoring-design.md`
- related work items:
  - `docs/9.5.3/workitems/SPIKE-runtime-model-authoring-foundations.md`
