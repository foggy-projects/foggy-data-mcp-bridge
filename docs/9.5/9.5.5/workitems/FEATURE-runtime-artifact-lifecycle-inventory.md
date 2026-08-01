---
doc_type: delivery-spec
delivery_type: feature
version: 9.5.5
ticket: FEATURE-runtime-artifact-lifecycle-inventory
status: APPROVED
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: user
approved_at: 2026-08-01
open_questions: []
---

# Delivery Spec: Runtime Artifact Lifecycle 只读 Inventory

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结 Runtime workspace/published artifact/live registry 的只读 lifecycle inventory 管理 API、
  fail-closed 诊断、redaction 与证据边界。
- canonical_path: `docs/9.5/9.5.5/workitems/FEATURE-runtime-artifact-lifecycle-inventory.md`

## Goal

- version_goal: 为 9.5.5 模型交付稳定化提供可操作的磁盘容量、对象状态、跨 store/live 引用分类和稳定 blocked
  reason，使后续 interrupted-write recovery 与 retention/GC 不依赖目录猜测。
- target_outcome: 新增 management-auth 保护的只读 `GET /api/v1/authoring/artifacts/lifecycle`，在单进程
  publication lock 内扫描两个 configurable root、workspace registry、published artifact/attempt 和 live Bundle
  registry，返回 redacted、deterministic inventory；绝不创建、修改、删除或修复磁盘对象。
- critical_outcomes:
  - 每次扫描对 workspace、published 与 live registry 形成同一锁内事实快照；current/base/candidate/previous、
    recovery/rollback 与 evidence 引用不能漏标为 retain。
  - owner/identity/hash 完整且无引用的对象才可标为 `PROVABLY_UNREACHABLE_CANDIDATE`；该分类不是删除授权。
  - unknown、foreign、symlink、corrupt、partial staging、metadata temporary 或无法验证对象统一
    `UNKNOWN_PRESERVE` 并给出稳定 blocked reason，扫描零 mutation。
  - API、日志、错误与测试不返回 auth-code、Authorization、连接串、模型内容、root absolute path 或 storeId。
- success_is_sufficient_when: AC-1 至 AC-9 的 focused/affected Runtime API 证据通过，真实 `@TempDir` 证明 healthy/
  missing/foreign/symlink/corrupt/interrupted 场景与 scan 前后字节级零 mutation，auth/envelope/capability/redaction
  均闭环。

## Scope

- in_scope:
  - additive lifecycle inventory DTO、route、capability、controller/service 与 stable error/blocked reason。
  - workspace root、published root 与 live Bundle registry 的 lock-consistent read-only scan。
  - root health、对象/字节聚合、对象 type/status/identity、reference class/references、blocked reason。
  - source/schema/reference review，以及 production stores 创建的真实临时目录 fixtures。
  - runtime lifecycle architecture、Bundle/Namespace developer guidance、9.5.5 workitem/result/signoff。
- affected_modules: `foggy-runtime-api`、`docs/architecture`、`docs/dev-guide`、`docs/9.5/9.5.5`。
- external_dependencies: none；不得新增 Maven、数据库、Node、CLI 或外部服务依赖。

## Non-Goals

- out_of_scope:
  - cleanup/delete/repair/quarantine、retention/grace、scheduler、startup recovery、filesystem migration 或写入
    ownership marker/wrapper。
  - Console/UI、人工 confirmation、cleanup plan execution、CLI、metrics exporter、告警或审计持久化。
  - 任意历史 rollback、package registry、Git/JAR/Agent、跨 Runtime orchestration、分布式锁、多进程/shared-NFS。
- do_not_touch: Engine/Model SPI、Console、launcher、POM/dependencies、数据库、真实 `.foggy-runtime`、既有
  9.5.0～9.5.4 acceptance 正文。
- non_blocking_or_waivable_items: 大目录分页/streaming、OS power-loss 时序、外部进程并发写和 retention 产品策略；
  必须记录边界，不影响本次只读 inventory 核心验收。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 路由固定为 `GET /api/v1/authoring/artifacts/lifecycle` | 归入现有 authoring management auth pattern | additive `RuntimeEnvelope`；capability `authoring.artifacts.lifecycleInventory=supported` |
| response 包含 capturedAt、health、root/summary/object/blockedReasons | 同时服务机器诊断与后续 Console | 对象 deterministic 排序；不承诺 filesystem traversal 顺序 |
| root 缺失报告 `NOT_INITIALIZED` 而不创建 | read-only 诊断不能改变系统 | 一侧缺失不伪报 corruption；整体 health 仍反映可操作状态 |
| corrupt/unknown 返回成功 envelope 内的 `BLOCKED` inventory | 操作者需要看到现场而不是只有通用 500 | unexpected scan failure 才返回稳定 `ARTIFACT_LIFECYCLE_INVENTORY_FAILED` |
| reference class 只有 `MUST_RETAIN`、`PROVABLY_UNREACHABLE_CANDIDATE`、`UNKNOWN_PRESERVE` | 与 accepted SPIKE 契约一致 | candidate 仅供后续 policy/plan 使用，当前永远不可由 API 删除 |
| publication attempt/tombstone evidence 默认 retain | owner 尚未批准 evidence retirement | terminal status/age 不能单独产生 candidate |
| 扫描复用 publication lock，不调用会初始化 root 的 store API | 获得单进程一致视图且保证 missing root 零写入 | 保持 single-process/local-filesystem 边界，不新增锁拓扑 |
| stable identity 不含 path/storeId | diagnostics 最小暴露 | 可返回 workspaceId、attemptId、revision、Bundle/Namespace 等既有管理身份 |

## Acceptance Criteria

- [ ] AC-1: additive route/capability/DTO 使用 `RuntimeEnvelope`，authoring auth-code 在任何 auth scope 下强制；
  unauthorized/unconfigured/authorized 行为与现有 authoring route 一致。
- [ ] AC-2: response 稳定提供 `capturedAt`、overall health、两个 root health、总对象/字节/分类计数、对象 type/
  identity/status/bytes/referenceClass/references/blockedReason 和去重 blocked reason；对象与引用 deterministic 排序。
- [ ] AC-3: workspace scan 覆盖 root owner/registry/marker/revision/logical base+candidate head/staging/tombstone/
  temporary；non-discarded base/candidate 与 lease-relevant current revision retain，obsolete complete revision 只作
  candidate，unsafe/unknown preserve。
- [ ] AC-4: published scan 覆盖 root owner、final artifact/manifest/content hash、artifact staging、attempt/
  rollback metadata 与 atomic temporary；partial/foreign/symlink/corrupt 不 follow、不删除且给出稳定 blocked reason。
- [ ] AC-5: reference graph 合并全部 workspace state、publication/rollback attempt、previous artifact 与 live Bundle
  registry path/revision；live/current、workspace base/candidate、recovery/rollback 两侧及所有未退休 evidence 均
  `MUST_RETAIN`，不得使用 age/status/filename shortcut。
- [ ] AC-6: root missing、empty/unowned、valid healthy、单边 initialized 与 blocked 均有明确 health；扫描不隐式创建
  owner/root/registry，不改变 mtime/content/layout，任何路径都无 repair/delete/quarantine。
- [ ] AC-7: API/DTO/envelope/blocked reason 不含 root absolute path、storeId、模型内容、auth/Authorization/连接串或
  raw exception；unexpected I/O 映射稳定 phase/code，`safeToAutoRepair=false`。
- [ ] AC-8: 生产实现保持单向依赖和可维护性，无 schema migration、新依赖或 cleanup implementation；architecture/
  dev guide 明确 API 是 read-only diagnostics、candidate 不是删除授权及 single-process 边界。
- [ ] AC-9: compile、focused service/controller/auth/capability/store tests、affected `Runtime*Test` lane、完整 tracked/
  untracked diff checks 通过；changed paths 无 Engine/Console/launcher/POM/database/真实 store 越界。

## Contract / Data / Security Constraints

- API or event contract: additive JSON GET；不接受 root/path 参数，不提供 content download、mutation 或 repair flag；
  `RuntimeEnvelope` 和 stable `runtime.artifacts.lifecycle.inventory` phase。
- data and migration: no persistence/schema/filesystem/database migration；DTO 只承载即时 snapshot，服务不缓存完整
  inventory 或模型内容。
- compatibility and rollback: Runtime disabled 时不注册 route；enabled 时 capability additive；回滚为删除新增
  route/DTO/service/docs，无持久数据需要迁移。
- permissions and secrets: route 位于 `/api/v1/authoring/**`，始终强制 configured auth-code；只返回 redacted stable
  identities/reasons，不记录 configured path、storeId、文件正文或底层异常消息。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/7 | must-pass | critical | controller + random-port auth/redaction/capability | accepted authoring auth/envelope | unauthorized/unconfigured/authorized 与 zero leak |
| AC-2/5 | must-pass | critical | cross-store reference/classification service tests | accepted lifecycle matrix | every retain edge + deterministic aggregate/object assertions |
| AC-3/4/6 | must-pass | critical | real production store + `@TempDir` filesystem characterization | workspace ownership + publication store tests | healthy/missing/staging/temp/foreign/symlink/corrupt snapshots，scan before/after zero mutation |
| AC-8/9 | must-pass | major | source review、compile、focused + affected Runtime lane、diff checks | current module/compatibility baseline | exact commands/counts、changed paths、omitted reason |

## Validation Budget and Evidence Sufficiency

- assurance_level: elevated；新增公共 management API，并暴露受 auth/redaction 约束的 filesystem lifecycle facts。
- lightweight_validation: spec/source review、`git diff --check`、全部 untracked no-index check、compile，单次 `<5m`。
- medium_validation:
  - focused：新增 inventory service/controller，加 publication/workspace store、auth、capability，预计 `5-15m`。
  - affected：`mvn -B -ntp -pl foggy-runtime-api -am -DskipITs -Dtest=Runtime*Test
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`，预计 `5-20m`，最多一次最终运行。
- expensive_validation: none；不运行完整 reactor、数据库矩阵或 release chain。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none；局部 additive read-only Runtime API，focused+affected 足以判断。
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: 若需 Engine/SPI、依赖、launcher、数据库、跨进程锁、持久化 audit 或 mutation，设置
  `NEEDS_REPLAN`。
- maximum_expensive_attempts: 0
- reusable_evidence: accepted 9.5.3 workspace ownership/publish/recovery/race、9.5.4 promotion/rollback 与 9.5.5
  lifecycle SPIKE characterization；仅新增 scanner/API 的输入、测试选择与 security evidence 需要重验。
- stop_when_evidence_is_sufficient: AC-1～AC-9 均有可追溯断言，真实临时目录 zero mutation、auth/redaction、
  deterministic classification、compile/focused/affected/diff 全绿后停止。
- validation_not_required: Console/Playwright、Engine/launcher、DB matrix、真实 deployment store、shared-NFS/
  multi-process、authority/replay/rehearsal/source-seal/tag/release/publish。

## Waiver Policy

- waivable_items: none；pagination/metrics/Console/retention 均为 non-goal 而非失败项。
- authorized_role: owner
- non_waivable_guards: auth、secret/path/storeId redaction、zero filesystem mutation、ownership/hash/reference
  correctness、unknown preservation、evidence truthfulness。
- required_risk_record: candidate 不是删除授权、terminal evidence 默认 retain、single-process/non-shared-NFS 与大目录
  非分页边界必须如实保留。

## Risks and Open Questions

- known_risks:
  - 大 store 使用同步完整扫描，v1 不提供分页/streaming；容量增长后需以独立 workitem 优化。
  - external writer/shared-NFS 不在一致性承诺内；publication lock 只覆盖当前 Runtime 单进程 mutation。
  - terminal attempt/tombstone evidence 无 retirement policy，因此 inventory 会保守 retain，候选数可能偏少。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目 `CLAUDE.md`、accepted lifecycle SPIKE/signoff、runtime lifecycle architecture、相关
  9.5.3/9.5.4 accepted evidence，以及 Runtime auth/routes/store/controller/tests。
- 开始实现时改 `ULTRA_EXECUTING`；在 scope 内自主决定 scanner/DTO/controller 的合理局部结构。
- 优先建立真实 `@TempDir` healthy/reference/interrupted/foreign/symlink/corrupt/zero-mutation tests，再实现。
- 如需改变 API path/response core fields、auth/redaction、reference classification、安全边界或引入 mutation，设置
  `NEEDS_REPLAN` 并停止扩展。
- 运行精确 compile/focused/affected/diff checks，记录命令、类/数量/结果和未运行原因；达到 evidence sufficiency
  后停止，不启动大型 authority/replay。
- 完成后填写 `Implementation Result`，状态改 `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

> 由 Ultra 执行会话填写。

- implementation_summary:
- changed_paths:
- tests_and_results:
- manual_or_experience_evidence:
- deviations: none
- residual_risks:
- reused_evidence:
- omitted_validation_and_reason:
- readiness: READY_FOR_SIGNOFF | NEEDS_REPLAN | BLOCKED

## References

- requirement / issue: 用户在 accepted SPIKE 后批准从 P0 lifecycle inventory 开始并完成后合并回 `main`。
- architecture / glossary: `docs/architecture/runtime-and-model-lifecycle.md`、`docs/dev-guide/bundle-namespace.md`
- related work items:
  - `docs/9.5/9.5.5/workitems/SPIKE-runtime-artifact-store-lifecycle-foundations.md`
  - `docs/9.5.3/workitems/BUG-runtime-authoring-workspace-store-root-ownership.md`
  - `docs/9.5.4/workitems/FEATURE-runtime-release-package-production-promotion-api.md`
