---
doc_type: delivery-spec
delivery_type: bug
version: 9.5.3
ticket: BUG-runtime-authoring-workspace-store-root-ownership
status: APPROVED
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: owner
approved_at: 2026-08-01
open_questions: []
---

# Delivery Spec: Runtime authoring workspace store root ownership

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 修复 workspace API 首次正式验收发现的 destructive orphan cleanup 与 store/Bundle path
  overlap，使 filesystem store 只能识别和清理有明确 ownership 证明的内部数据。
- canonical_path:
  `docs/9.5.3/workitems/BUG-runtime-authoring-workspace-store-root-ownership.md`

## Goal

- version_goal: 使 9.5.3 Runtime authoring workspace API 满足原 feature 的 AC-2、AC-9 和不可豁免的
  持久化数据安全边界，重新进入正式验收。
- target_outcome:
  - 首次打开不存在或空的专用 store root 时建立 versioned ownership identity；非空且不能证明属于
    Foggy authoring store 的目录稳定拒绝，任何原有文件保持逐字节不变。
  - startup/restart cleanup 只删除 root ownership、workspace ownership、严格内部命名和 registry
    状态共同证明的 staging/orphan/revision；未知或 foreign entry 一律保留并 fail closed。
  - store root 与 live/configured/Runtime-managed external filesystem Bundle source 在相等、祖先、后代
    或 symlink-equivalent 情形下均于任何 store/source mutation 前拒绝。
  - 已由当前 9.5.3 实现创建的合法 v1 dedicated store 可安全、幂等迁移，metadata、base/head revision、
    validation evidence 和 tombstone 均不丢失。
- critical_outcomes: 非 workspace 文件零删除、live Bundle/source 零写入、ownership proof、path
  disjointness、迁移/故障原子性均不可豁免。
- success_is_sufficient_when: AC-1 至 AC-6 全部通过；sentinel preservation、owned orphan cleanup、v1
  restart migration、三向 Bundle overlap、symlink/fault cases 和原 feature Runtime affected lane 均有
  自动化证据。

## Scope

- in_scope:
  - Runtime-local authoring store root/workspace ownership metadata、registry schema、初始化、迁移和清理。
  - configured/live/Runtime registry filesystem Bundle path 与 authoring store root 的集中冲突判定。
  - Runtime-managed Bundle add/update/enable/restore 与 workspace store 初始化/操作的 mutation-before-check。
  - stable failure mapping、focused regression tests、原 feature 与架构/版本状态回写。
- affected_modules:
  - `foggy-runtime-api`
  - `docs/architecture`
  - `docs/9.5.3`
- external_dependencies: none；复用现有 Java NIO、Jackson、JUnit、Mockito 和临时目录测试基础。

## Non-Goals

- out_of_scope:
  - 不实现 publish/apply/rollback/rebase、release package、Git、Console、Agent 或 JAR 多 Namespace。
  - 不增加多进程/NFS lease、filesystem sandbox、OS user isolation 或 store encryption/signing。
  - 不改变 workspace resource/revision/diff/validate/query 成功语义，不修订 TM/QM/FSScript 内容 hash。
  - 不扩大 candidate query mode、Bundle editable eligibility 或 datasource/permission 语义。
- do_not_touch:
  - `foggy-dataset-model-engine` 和 Model SPI v2 公共模块
  - `addons/foggy-runtime-console/frontend`
  - `foggy-mcp-launcher`
  - POM/Maven 依赖图、数据库 schema 和已签收 spike/candidate 历史记录
  - 首次 rejected acceptance 记录的历史测试结果与结论
- non_blocking_or_waivable_items:
  - 多进程/shared-NFS writer、nested/fat-JAR packaging 与绕过 watcher 的其他 dependency drift 继续保留
    为原 feature scoped risks；不得借本 BUG 扩大。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| ownership identity 放入 versioned `workspaces.json` v2，并使用 opaque `storeId` | registry 必须先证明 root 属于 authoring store，旧实现遇到 v2 会在 cleanup 前 fail closed | `storeId` 至少 128-bit、不可含路径或 secret；不作为权限身份 |
| 每个 workspace directory 都有匹配 root `storeId` 与 `workspaceId` 的 ownership metadata | 仅凭目录名或“未被 registry 引用”不能证明可以递归删除 | marker 必须在写 revision 前原子建立；symlink/identity mismatch 视为 corruption |
| 不存在或严格空目录可初始化；非空 unowned root 拒绝且零 mutation | 防止配置到现有目录时删除业务文件 | 返回 `WORKSPACE_STORE_FAILURE`；sentinel、目录时间以外的内容与结构保持不变 |
| owned root 中 foreign/unknown entry 保留并拒绝 | 安全优先于自动整理 | 返回 `WORKSPACE_STORE_CORRUPT`；不得静默忽略、重命名或递归删除 |
| cleanup 需要 root + child ownership 与严格内部类型三重证明 | lexical `startsWith(root)` 不是删除授权 | 只清理匹配 storeId 的 orphan workspace、已知 staging 和无 lease 旧 revision |
| v1 只在 registry、全部 record/revision 和 root layout 完整验证后迁移 | 保留当前未发布实现产生的合法草稿，同时拒绝借迁移认领任意目录 | migration 幂等；workspace marker 先写、v2 registry 最后原子切换；任一失败零删除、可重试 |
| store root 与任一 direct-filesystem Bundle source 必须双向 disjoint | 防止 store 写入 source，或 cleanup 把 source 当 orphan | 比较相等、root contains source、source contains root 和 symlink-equivalent；Spring resource/JAR/classpath 不伪装为 filesystem |
| 所有相关 mutation 先判定 overlap，再写 registry、注册 Bundle、初始化/清理 store | 事后 stale 检查无法恢复已删除文件 | configured overlap 必须在 Runtime 对外 ready 前 fail closed；Runtime Bundle mutation 返回稳定 `BUNDLE_PATH_CONFLICT` |

## Acceptance Criteria

- [ ] AC-1: 不存在或空的 safe/disjoint root 可初始化 v2 ownership；已有任意 sentinel 的 unowned root
  返回 `WORKSPACE_STORE_FAILURE`，sentinel、其他文件和目录均不删除、不改写，且不产生 registry、marker
  或临时文件。
- [ ] AC-2: v2 restart 只接受有效 root/workspace ownership、registry 与 revision identity；匹配
  `storeId` 的 crash orphan/staging/old revision 可安全清理，foreign marker、未知 root entry、symlink、
  identity mismatch 返回 `WORKSPACE_STORE_CORRUPT` 并完整保留可疑项。
- [ ] AC-3: 合法 v1 dedicated store 自动迁移到 v2 后 exact metadata/state/base/head/content/validation
  evidence/tombstone 可恢复；未知 entry、无效 v1 或 marker/registry persistence fault 时不切换 v2、
  不删除任何 revision，修复环境后可幂等重试。
- [ ] AC-4: store root 与 selected、其他 live configured、active/inactive Runtime-managed external
  filesystem source 的相等、祖先、后代和 symlink-equivalent 组合全部在首个 filesystem mutation 前
  稳定拒绝；source 文件、Bundle registry/live inventory、catalog/cache 和 committed revision 不变。
- [ ] AC-5: Runtime Bundle add/update/enable/restore 不能引入与已配置 store root 重叠的 filesystem
  source；公共 route 使用 `BUNDLE_PATH_CONFLICT`，失败不持久化 record、不部分注册 Bundle，configured
  startup overlap 不进入 ready 状态且不得执行 store cleanup。
- [ ] AC-6: 原 feature 其他 AC 证据保持有效；workspace create/save/delete/diff/validate/query、auth、
  real SQLite、standard JAR/external/FSScript、CAS/lease/corruption 和既有 Runtime Bundle/model API
  compatibility affected lane 全部通过。

## Contract / Data / Security Constraints

- API or event contract:
  - 不新增 route、request/response DTO、Header 或 capability claim。
  - workspace 配置/ownership失败继续使用 `WORKSPACE_STORE_FAILURE`；已拥有 store 的 metadata/layout
    破坏使用 `WORKSPACE_STORE_CORRUPT`。
  - Bundle mutation overlap additive 使用 `BUNDLE_PATH_CONFLICT`；RuntimeEnvelope 和 HTTP auth 行为不变。
- data and migration:
  - `workspaces.json` internal schema 从 v1 升为 v2，新增 opaque `storeId`；workspace ownership metadata
    是内部文件，不进入 TM/QM/FSScript snapshot、diff 或 API。
  - v1 migration 必须先完整只读验证，再以可重试顺序写 workspace ownership，最后 atomic switch registry；
    禁止通过删除 unknown entry 让 migration“通过”。
- compatibility and rollback:
  - 默认配置键和 quota 不变；新建 dedicated store、现有合法 v1 store 和 v2 restart 均兼容。
  - 旧代码读取 v2 必须因未知 schema 在 cleanup 前 fail closed；回退代码不得被描述为可安全继续写 v2
    store。必要回退先停用 authoring 并保留目录备份。
  - formerly destructive 的非空 unowned root 现在稳定失败是有意安全收紧，不提供旧行为兼容。
- permissions and secrets:
  - ownership identity 不是 auth token；不得记录/返回 store absolute path、Bundle absolute path、
    `storeId`、auth-code、Authorization 或文件内容。
  - path conflict 和 corruption message 只报告稳定分类与安全相对 identity，不返回内部绝对路径。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1 | must-pass | critical | focused store init + sentinel preservation tests | existing empty temp-root tests | nonempty unowned root zero mutation |
| AC-2 | must-pass | critical | ownership/foreign/symlink/orphan cleanup matrix | existing corruption/lease tests | only matching owned targets deleted |
| AC-3 | must-pass | critical | real v1 fixture migration + persistence fault/restart tests | current restart evidence | exact recovery and idempotent no-loss retry |
| AC-4 | must-pass | critical | equal/ancestor/descendant/symlink Bundle overlap service tests | source drift/live snapshots | pre-mutation rejection and unchanged live state |
| AC-5 | must-pass | critical | Bundle controller/registry restore atomicity tests | existing Bundle CRUD tests | stable error, zero record/live partial mutation |
| AC-6 | must-pass | major | original workspace + Runtime affected lane | 22/104 signoff rerun | updated Runtime selection all pass |

## Validation Budget and Evidence Sufficiency

- assurance_level: elevated
- lightweight_validation:
  - `git diff --check` 及每个 untracked 文件的等效 no-index whitespace check，单次 `<5m`。
  - store ownership、sentinel、v1/v2 migration、overlap 和 fault focused tests，单次 `<5m`。
- medium_validation:
  - Runtime store/service/Bundle controller focused lane，预计 `<5-10m`。
  - 原 feature 10-class Runtime affected lane，预计 `5-15m`。
- expensive_validation: none；engine 未改时复用 2026-08-01 的 4 classes / 22 tests 证据。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none；blocker 局限于 Runtime filesystem/config boundary，focused +
  affected lane可直接判断。
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: 若修复需要 launcher、公共 Model SPI、数据库 migration、新外部依赖或
  修改 workspace 成功 API 语义，设置 `NEEDS_REPLAN`，不自行扩展。
- maximum_expensive_attempts: 0
- reusable_evidence:
  - rejected signoff 中通过的 AC-1、AC-3 至 AC-8、AC-10 source evidence。
  - 2026-08-01 engine 4 classes / 22 tests 与 Runtime 10 classes / 104 tests；只有直接依赖 store/
    Bundle path 行为的 Runtime tests 需刷新。
- implementation_validation_commands:
  - `mvn -B -ntp -pl foggy-runtime-api -am -DskipITs
    -Dtest=RuntimeAuthoringWorkspaceStoreTest,RuntimeAuthoringWorkspaceServiceTest,RuntimeAuthoringWorkspaceRealExecutionTest,RuntimeAuthoringWorkspacesControllerTest,RuntimeCandidateQueryServiceTest,RuntimeApiAuthCodeGateTest,RuntimeBundlesControllerTest,RuntimeCapabilitiesControllerEnabledTest,RuntimeModelValidationIsolationTest,RuntimeModelsControllerCompatibilityTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`
- stop_when_evidence_is_sufficient: AC-1 至 AC-5 的 destructive negative matrix 全部转绿，AC-6 affected
  lane通过、工作树检查通过；此后停止，不扩展到 publish/Console/launcher/full reactor。
- validation_not_required:
  - 未改 engine 时不重复 engine lane
  - 完整 Maven reactor、Console build/Playwright、launcher package、数据库矩阵
  - authority/replay/rehearsal/source-seal/tag/release/publish

## Waiver Policy

- waivable_items: none；多进程/NFS、nested-JAR 等原 residual risks 不是本 BUG 的失败项。
- authorized_role: owner
- non_waivable_guards: foreign data zero deletion、root/workspace ownership、Bundle path disjointness、
  migration no-loss、live-state isolation 和证据真实性。
- required_risk_record: 任何不能证明 ownership 的 entry 必须保留并失败，不得以 operator 可恢复为由
  waiver destructive cleanup。

## Bug Context

- bug_source: acceptance-found
- severity: critical
- environment: 9.5.3 `main` commit `2dfbd806`，默认或自定义
  `foggy.runtime-api.authoring-workspaces.path`。
- current_behavior: `loadIfNeeded()` 在读到零/部分 registry records 后调用 `cleanupOrphans()`；root 中任一
  未被 registry key 命中的 child 都直接进入递归 `deleteTree`。配置没有 dedicated-root ownership 或
  与 Bundle path 的 overlap guard。
- expected_behavior: 只有显式匹配当前 store ownership 的内部 orphan 可清理；unowned/foreign/overlap
  全部在任何 mutation 前保留数据并稳定失败。
- reproduction_steps:
  1. 将 authoring workspace path 指向一个已存在、无 `workspaces.json` 且包含 sentinel 的目录。
  2. 构造/调用 workspace store 的首个 list/create 操作。
  3. 当前 `loadIfNeeded:638-664` 进入 `cleanupOrphans:672-710`，在 `:685-687` 对 sentinel child 调用
     `deleteTree`；若该目录是 Bundle source 或其祖先，模型文件同样处于删除范围。
- reproduction_status: confirmed
- existing_evidence:
  - `docs/9.5.3/acceptance/FEATURE-runtime-authoring-workspace-api-signoff.md`
  - production source trace `RuntimeAuthoringWorkspaceStore:638-710,807-847`。
- existing_tests: `RuntimeAuthoringWorkspaceStoreTest`、`RuntimeAuthoringWorkspaceServiceTest`、
  `RuntimeBundlesControllerTest`；当前均只使用新建 dedicated temp root，未覆盖 foreign sentinel/overlap。
- regression_protection: required
- waiver_reason_and_risk: N/A

## Risks and Open Questions

- known_risks:
  - v1 migration 的 fault ordering 必须可重试，不能因部分 marker 写入把合法 store 误判为 foreign。
  - 不存在路径、relative/absolute、case-sensitive filesystem、symlink 和 `fileKey == null` 情形需要保守
    比较；无法证明 disjoint 时 fail closed。
  - owned orphan cleanup 仍是 destructive action，必须把删除 authority 收窄到 root `storeId`、child
    marker、strict name/type 和 no-symlink 全部匹配。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目 `CLAUDE.md`、原 feature spec、首次 rejected signoff 和 Runtime/model lifecycle
  架构。
- 优先建立能复现 sentinel 删除、root/source overlap、foreign marker 与 v1 migration fault 的 RED tests，
  再完成生产修复；不得删除或放宽失败断言。
- 在 scope 内自主决定 marker DTO、internal helper 和 bean wiring；不得让 caller 提交 store path、
  `storeId` 或 deletion target。
- 任何 recursive cleanup 前都必须在同一调用链重新证明 ownership 与 no-symlink；不能仅依赖启动时
  布尔标记、目录名、lexical prefix 或注释。
- 如需改变公共成功语义、配置键、原 feature目标、迁移承诺或受影响模块，设置 `NEEDS_REPLAN`。
- 运行精确验证命令并记录 classes/tests/failures/skips/耗时；不得声称未执行的证据通过。
- 未经用户明确批准，不得运行完整 reactor、Console/Playwright、launcher、数据库矩阵或任何
  authority/replay/rehearsal/source-seal/tag/release/publish 链路。
- 修复达到 evidence sufficiency 后填写 `Implementation Result`，将本 BUG 与原 feature 状态更新为
  `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`，也不得重写首次拒签记录。

## Implementation Result

> 由 Ultra 执行会话填写。

- implementation_summary:
- changed_paths:
- tests_and_results:
- manual_or_experience_evidence:
- deviations: none
- residual_risks: none
- reused_evidence:
- omitted_validation_and_reason:
- readiness: READY_FOR_SIGNOFF | NEEDS_REPLAN | BLOCKED

## References

- requirement / issue: owner 要求按 9.5.3 路线继续推进，首次 workspace API signoff 发现 destructive
  store-root cleanup blocker。
- architecture / glossary:
  - `docs/architecture/runtime-and-model-lifecycle.md`
  - `docs/9.5.3/runtime-model-authoring-design.md`
- related work items:
  - `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-api.md`
  - `docs/9.5.3/acceptance/FEATURE-runtime-authoring-workspace-api-signoff.md`
