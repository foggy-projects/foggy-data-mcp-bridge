---
doc_type: delivery-spec
delivery_type: cross-module
version: 9.5.5
ticket: SPIKE-runtime-artifact-store-lifecycle-foundations
status: APPROVED
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: owner
approved_at: 2026-08-01
open_questions: []
---

# Delivery Spec: Runtime Artifact / Store 生命周期基础探针

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结 Runtime authoring workspace 与 published artifact 双 store 的只读技术探针、证据边界和后续
  安全实施门槛。
- canonical_path:
  `docs/9.5/9.5.5/workitems/SPIKE-runtime-artifact-store-lifecycle-foundations.md`

## Goal

- version_goal: 为 9.5.5 模型交付稳定化建立真实磁盘对象 inventory、跨 store/live 引用图、restart/failure
  matrix 和 fail-closed retention/diagnostics 结论。
- target_outcome: 准确区分 workspace store 已有的 ownership-aware cleanup 与 published store 尚未闭环的
  immutable artifact GC、artifact staging/metadata temporary recovery；给出后续最小 workitem 边界，不直接
  实现删除或公共 API。
- critical_outcomes:
  - 任何可删除性结论必须同时证明 owner、对象类型、完整 identity 和跨 workspace/publication/live registry
    不可达；未知、foreign、symlink、corrupt 或竞态中的对象一律归为 preserve/fail-closed。
  - live current、下一 workspace base、publish/recovery、production rollback/forward recovery 所需 artifact
    不能被误判为垃圾。
  - 探针不得修改生产实现或真实 `.foggy-runtime` 数据。
- success_is_sufficient_when: AC-1 至 AC-7 全部有源码映射和必要的临时目录 characterization evidence，且
  后续 cleanup/retention 与 diagnostics workitem 的进入条件、不可豁免 guard 和仍需 owner 选择的产品策略清楚。

## Scope

- in_scope:
  - `foggy-runtime-api` 当前 authoring workspace store、published bundle artifact/attempt store、publication
    coordinator、release import/promotion、Bundle registry/source identity 和配置 root 的只读审计。
  - 在 `foggy-runtime-api/src/test` 增加最小 characterization probe；只能使用测试创建的临时目录、临时
    Bundle/source 和测试 ApplicationContext，不访问仓库或用户真实 store。
  - 在本 canonical workitem 的 Implementation Result 中记录 inventory、reference/restart matrix、设计结论和
    后续 workitem 建议；只有发现 current architecture 事实错误时才同步 canonical architecture。
- affected_modules:
  - `docs/9.5/9.5.5`
  - `foggy-runtime-api` test sources only
- external_dependencies: none；不得新增 Maven、Node、数据库或外部服务依赖。

## Non-Goals

- out_of_scope:
  - GC、delete、retention scheduler、startup cleanup、store migration、repair、quarantine 或磁盘 mutation API 的
    生产实现。
  - 新 Runtime route/DTO/config/capability、Console 运维 UI、CLI、launcher 或后台任务。
  - package registry/history、任意历史 rollback、签名/KMS、审批/RBAC、Git、JAR binding、跨 Runtime
    orchestration、VS Code 或 Agent。
  - 多进程、shared-NFS writer、分布式锁或外部进程直接修改 store 的一致性承诺。
- do_not_touch:
  - `foggy-runtime-api/src/main/**`
  - `addons/foggy-runtime-console/**`
  - `foggy-dataset-model-*`、`foggy-mcp-launcher`、POM、依赖与生产配置
  - `.foggy-runtime/**`、既有 9.5.0～9.5.4 acceptance 正文与 canonical 历史状态
- non_blocking_or_waivable_items: 本探针不选定默认 retention 时长、不交付 Console 体验，也不扩展当前
  single-process/non-shared-NFS 边界；这些不影响技术事实验收。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| workspace store 与 published store 分开建模 | 两者 owner/schema、临时文件与 restart cleanup 能力不同 | 不得从 workspace orphan cleanup 推断 published root 已具备相同能力 |
| release package v1 不形成服务端 package registry | export/import 传递 canonical JSON，import 后持久事实是 workspace provenance | inventory 必须明确“非持久化”，不得虚构 package GC |
| artifact reachability 跨 store 与 live registry 判定 | attempt/workspace terminal 状态或时间戳本身不能证明后续 base/recovery/rollback 不再引用 | 禁止 age-only、status-only 或 filename-only 删除策略 |
| unknown/foreign/symlink/corrupt 默认保留并 fail closed | 当前 ownership 不变量优先于可用性自动修复 | 探针不得把删除未知现场建议为默认恢复方式 |
| 后续任何 lifecycle mutation 复用单进程 publication lock | publish/recover/promotion/rollback 与 Bundle mutation 已共享该边界 | 本探针只评价可行性，不实现锁内 scan/delete |
| diagnostics 先于自动 cleanup | 操作者需要看到容量、引用与 blocked reason，不能依赖目录猜测 | 后续契约不得泄露 secret、Authorization、模型内容或不必要的绝对路径 |
| 只维护本 canonical workitem | 避免 inventory、design、plan 多文档重复 | 结论写入 Implementation Result；未来实现各自另立 spec |

## Acceptance Criteria

- [ ] AC-1: 建立完整磁盘对象 inventory，至少覆盖两个 configurable root、root owner、workspace registry/
  marker/revision/head/staging/tombstone、published artifact/manifest、publication attempt/rollback metadata、原子
  JSON temporary，以及 release package 的非持久化边界；每项标明 owner、identity、创建/更新方和现有清理行为。
- [ ] AC-2: 建立跨 workspace store、published store、Bundle registry/live source 的 artifact reference graph；对
  `DRAFT`、`VALIDATED`、`STALE`、`PUBLISHING`、`RECOVERY_REQUIRED`、`PUBLISHED`、`ROLLING_BACK`、
  `ROLLBACK_REQUIRED`、`ROLLED_BACK`、`DISCARDED` 及 publication/rollback attempt 状态逐项说明必须保留的
  current/base/candidate/previous artifact 和 restart 后用途。
- [ ] AC-3: 为每类对象给出 `must-retain`、`provably-unreachable-candidate` 或 `unknown-preserve` 分类规则；证明
  current registry source、任何 workspace base、未完成 recovery/rollback、仍可作为下一 workspace base 的
  immutable artifact，以及 identity/ownership 不完整对象绝不进入可删除候选。
- [ ] AC-4: 建立 restart/failure matrix，区分 workspace root 已实现的 ownership-bearing orphan/staging/revision
  cleanup 与 published root 对 artifact `.staging-*`、metadata temporary、unknown entry 的当前行为；至少用真实
  生产 store 类和临时目录 characterization 证明一个成功路径与关键中断/foreign/symlink/corrupt 路径，禁止
  Mockito 伪造 filesystem Resource。
- [ ] AC-5: 冻结后续 cleanup/retention 的安全算法边界：在 publication lock 内取得一致引用快照，完整验证 root/
  storeId/manifest/hash/attempt/registry identity，先形成可诊断计划，再只处理可证明 owned 且不可达的对象；
  任一漂移、未知或验证失败零删除并保留现场。默认 retention 时长若仍属产品选择，明确列为后续 owner 决策，
  不在探针中猜测。
- [ ] AC-6: 给出最小 operability inventory：容量/对象数、对象状态、引用或 blocked reason、人工处置证据与
  redaction/auth 边界；区分可直接复用的内部事实、需要 additive Runtime API 的能力和仅属 Console 展示的部分，
  但不创建 route/DTO/UI 实现 spec。
- [ ] AC-7: 输出后续 workitem 划分和优先级，至少区分 ownership-proven interrupted-write recovery、immutable
  artifact retention/GC、read-only diagnostics；明确哪些可以合并、哪些必须等待前项验收，并确认 Engine/SPI、
  Console 大改、Git/JAR/Agent 不属于下一实现半径。

## Contract / Data / Security Constraints

- API or event contract: 本探针不得新增或改变 REST、DTO、配置键、capability、错误码或事件；未来建议必须是
  additive 且管理面 fail-closed。
- data and migration: 无数据库或 filesystem migration；所有新增测试数据仅位于测试临时目录并由测试生命周期
  回收，不读取或修改真实 `.foggy-runtime`。
- compatibility and rollback: 生产行为零变化；回滚为删除本探针新增测试/文档。若结论要求改变既有 immutable
  artifact、rollback 或 store schema 语义，设置 `NEEDS_REPLAN`，不得在本 workitem 实现。
- permissions and secrets: 不记录 auth-code、Authorization、连接串、模型源码、query result 或用户绝对路径；
  diagnostics 建议必须继承 Runtime 管理 auth 边界并最小化 path 暴露。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/2 | must-pass | major | production source trace + persisted schema/fixture review | 9.5.3/9.5.4 accepted store/publication evidence | inventory 与 reference graph 逐项回指源码字段/状态 |
| AC-3 | must-pass | critical | reference reachability review，含 live/previous/recovery/rollback | immutable-base、rollback R2 evidence | non-deletable matrix 无 age/status shortcut |
| AC-4 | must-pass | major | test-only real filesystem characterization + focused existing store tests | workspace ownership/restart 与 publication store tests | temp-dir success/interruption/foreign/symlink/corrupt 结果及零真实目录访问 |
| AC-5 | must-pass | critical | failure/drift thought experiment 对照 production lock/identity | publication lock、tamper/drift accepted evidence | safe algorithm guards 与 `NEEDS_REPLAN` 条件 |
| AC-6/7 | must-pass | major | source/API capability review | current Runtime envelope/auth/capability conventions | diagnostics boundary 与有序后续 workitems |

## Validation Budget and Evidence Sufficiency

- assurance_level: standard；本交付只读且禁止生产 mutation，不因未来 GC 风险升级。
- lightweight_validation:
  - `git status --short`、`git diff --check`、完整 tracked diff 审计及每个 untracked 文件逐项
    `git diff --no-index --check /dev/null <file>`，单次 `<5m`。
  - source/schema/state/reference trace 与 workitem 自审，单次 `<5m`。
- medium_validation:
  - 必须运行新增 characterization probe（若新建 test class，追加到以下 `-Dtest` selection）和既有 store/
    publication 基线：
    `mvn -B -ntp -pl foggy-runtime-api -am -DskipITs
    -Dtest=RuntimeAuthoringPublicationStoreTest,RuntimeAuthoringWorkspaceStoreTest,RuntimeAuthoringWorkspacePublishServiceTest,RuntimeAuthoringReleasePackageServiceTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`，预计 `5-15m`。
  - 只在探针改变现有 test fixture/helper 且 focused selection 无法覆盖时，增加 `foggy-runtime-api` affected
    test lane；预计 `5-20m`，最多一次。
- expensive_validation: none。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none；只读 test/docs 探针不形成 release candidate 或公共契约变更。
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: none；若需要生产实现、真实 store mutation、公共 API/schema 或跨进程一致性，
  设置 `NEEDS_REPLAN`。
- maximum_expensive_attempts: 0
- reusable_evidence:
  - 9.5.3 workspace ownership、publish/recovery、immutable-base/race R2 signoff。
  - 9.5.4 release package、production promotion、rollback/restart signoff。
  - 仅当生产输入、测试选择或 store schema 事实改变时，对应证据才失效。
- stop_when_evidence_is_sufficient: AC-1 至 AC-7 均有可追溯 matrix，关键 interrupted-write 行为有真实临时目录
  characterization，focused tests 和 whitespace checks 通过；此后停止，不为证据完美扩展测试。
- validation_not_required:
  - 完整 Maven reactor、Engine tests、Console build/Playwright、launcher package、数据库矩阵
  - authority、replay、rehearsal、source-seal、tag、release、publish
  - 真实部署目录、shared NFS、多进程或长时间容量压测

## Waiver Policy

- waivable_items: none；AC-1 至 AC-7 是本探针结论完整性的最小集合。
- authorized_role: owner
- non_waivable_guards: ownership、跨 store/live reference safety、unknown/foreign/corrupt preservation、零生产
  mutation、证据真实性。
- required_risk_record: single-process/non-shared-NFS、默认 retention 时长未选定、package 无服务端 registry 等
  边界必须如实保留，不得误报为已实现产品能力。

## Risks and Open Questions

- known_risks:
  - published root 严格校验会把不符合 final artifact/attempt layout 的中断临时对象视为 corruption；探针只能
    characterization 和设计 guard，不能直接删除现场。
  - attempt/workspace terminal 状态不等于 artifact 无引用；连续 publish、一步 rollback 和下一 workspace base
    会形成跨记录引用。
  - 源码审计无法证明所有 filesystem/OS crash 时序；只承诺生产代码可稳定构造的临时目录路径和已声明的
    single-process/local-filesystem 边界。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目 `CLAUDE.md`、9.5/9.5.5 README、canonical runtime lifecycle、9.5.3/9.5.4 相关
  workitem/signoff，以及 `foggy-runtime-api` store/publication 生产源码和测试。
- 在 scope 内自主选择 test-only probe 结构和 inventory 表达；不得把本 spec 写成生产实现说明书或复制多份
  inventory/design/progress 文档。
- 如需改变目标、范围、已确认决策、兼容或安全边界，设置 `NEEDS_REPLAN` 并停止扩展。
- 不得修改生产 Java、POM、Console、Engine、launcher 或真实 `.foggy-runtime`；不得通过放宽 ownership/
  corruption 校验来让探针通过。
- 运行与改动面匹配的验证，记录精确命令、测试类/数量、结果、证据路径和未运行原因。
- 未经用户明确批准，不得主动运行预计超过 30 分钟或包含 authority/replay/rehearsal/source-seal 的大型链路。
- 达到 evidence sufficiency 后停止；在本文件填写 inventory/reference/restart 结论、后续 workitem 建议、
  changed paths、tests、deviations 和 residual risks。
- 完成后将状态改为 `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

> 由 Ultra 扫描/探针执行会话填写。

- implementation_summary:
- disk_object_inventory:
- artifact_reference_matrix:
- restart_and_failure_matrix:
- recommended_follow_up_workitems:
- changed_paths:
- tests_and_results:
- manual_or_experience_evidence:
- deviations: none
- residual_risks:
- reused_evidence:
- omitted_validation_and_reason:
- readiness: READY_FOR_SIGNOFF | NEEDS_REPLAN | BLOCKED

## References

- architecture / glossary:
  - `docs/architecture/runtime-and-model-lifecycle.md`
  - `docs/dev-guide/bundle-namespace.md`
- related work items:
  - `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-publish-recovery-api.md`
  - `docs/9.5.3/workitems/BUG-runtime-authoring-published-base-republish-and-query-race-evidence.md`
  - `docs/9.5.4/workitems/FEATURE-runtime-release-package-production-promotion-api.md`
