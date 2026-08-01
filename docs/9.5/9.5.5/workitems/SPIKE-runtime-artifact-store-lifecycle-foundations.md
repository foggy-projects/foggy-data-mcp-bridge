---
doc_type: delivery-spec
delivery_type: cross-module
version: 9.5.5
ticket: SPIKE-runtime-artifact-store-lifecycle-foundations
status: ACCEPTED
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

- [x] AC-1: 建立完整磁盘对象 inventory，至少覆盖两个 configurable root、root owner、workspace registry/
  marker/revision/head/staging/tombstone、published artifact/manifest、publication attempt/rollback metadata、原子
  JSON temporary，以及 release package 的非持久化边界；每项标明 owner、identity、创建/更新方和现有清理行为。
- [x] AC-2: 建立跨 workspace store、published store、Bundle registry/live source 的 artifact reference graph；对
  `DRAFT`、`VALIDATED`、`STALE`、`PUBLISHING`、`RECOVERY_REQUIRED`、`PUBLISHED`、`ROLLING_BACK`、
  `ROLLBACK_REQUIRED`、`ROLLED_BACK`、`DISCARDED` 及 publication/rollback attempt 状态逐项说明必须保留的
  current/base/candidate/previous artifact 和 restart 后用途。
- [x] AC-3: 为每类对象给出 `must-retain`、`provably-unreachable-candidate` 或 `unknown-preserve` 分类规则；证明
  current registry source、任何 workspace base、未完成 recovery/rollback、仍可作为下一 workspace base 的
  immutable artifact，以及 identity/ownership 不完整对象绝不进入可删除候选。
- [x] AC-4: 建立 restart/failure matrix，区分 workspace root 已实现的 ownership-bearing orphan/staging/revision
  cleanup 与 published root 对 artifact `.staging-*`、metadata temporary、unknown entry 的当前行为；至少用真实
  生产 store 类和临时目录 characterization 证明一个成功路径与关键中断/foreign/symlink/corrupt 路径，禁止
  Mockito 伪造 filesystem Resource。
- [x] AC-5: 冻结后续 cleanup/retention 的安全算法边界：在 publication lock 内取得一致引用快照，完整验证 root/
  storeId/manifest/hash/attempt/registry identity，先形成可诊断计划，再只处理可证明 owned 且不可达的对象；
  任一漂移、未知或验证失败零删除并保留现场。默认 retention 时长若仍属产品选择，明确列为后续 owner 决策，
  不在探针中猜测。
- [x] AC-6: 给出最小 operability inventory：容量/对象数、对象状态、引用或 blocked reason、人工处置证据与
  redaction/auth 边界；区分可直接复用的内部事实、需要 additive Runtime API 的能力和仅属 Console 展示的部分，
  但不创建 route/DTO/UI 实现 spec。
- [x] AC-7: 输出后续 workitem 划分和优先级，至少区分 ownership-proven interrupted-write recovery、immutable
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

### Implementation Summary

- 只读审计 production workspace/artifact/publication/release/Bundle registry 源码，并在既有 publication store
  test 中加入两个 `@TempDir` characterization；没有修改 `src/main`、POM、Console、Engine、launcher 或真实
  `.foggy-runtime`。
- 证实 workspace store 已有 storeId/workspace marker 驱动的 staging、temporary、orphan 和旧 revision
  cleanup；published store 只有严格 layout 验证，没有 cleanup/enumeration API。
- 证实 production `prepareArtifact` 失败可留下含部分 TM 的 `.staging-*`；模拟 attempt metadata atomic rename
  前退出留下 `.json.tmp-*`。两者在 store 重建后均返回 `WORKSPACE_ARTIFACT_CORRUPT`，现场零删除。
- AC-1 至 AC-7 已完成；未发现需要修改既有 canonical architecture 的事实错误。

### Disk Object Inventory

| 对象 | Owner / identity | 创建与引用 | 当前 cleanup / classification |
|---|---|---|---|
| workspace configurable root | `authoring-workspaces.path`；v2 `workspaces.json.storeId` 为 root owner | `RuntimeAuthoringWorkspaceStore` | 不存在或严格空目录才初始化；unknown/foreign/symlink fail closed |
| `workspaces.json` | schema v2 + storeId + `StoredWorkspace[]` | 全部 workspace 状态、base/candidate、validation、publication、release provenance | 原子 replace；主文件永久 `must-retain` |
| workspace registry/migration/marker temporary | 文件内容中的 matching storeId/workspaceId；固定 `.tmp-UUID`/migration 名称 | registry、v1→v2 migration、workspace marker atomic write | load 时验证内容/owner 后删除；不匹配或不可读则 `unknown-preserve` |
| `{workspaceId}/.workspace-owner.json` | version + storeId + workspaceId | 证明目录归当前 workspace store 所有 | 删除 workspace/staging/revision 前必须匹配；`must-retain` |
| `{workspaceId}/revisions/{sha256}` | 目录名 + canonical TM/QM/FSScript content hash | non-discarded record 的 base/candidate；in-memory revision lease 临时 pin 旧 head | 只清理非 base/candidate 且无 lease、内容验证通过的 owned revision |
| `{workspaceId}/.staging-UUID` | 父 workspace marker + 受限名称/layout | revision atomic stage | `finally` 尝试删除；restart 验证 owned tree 后清理；unsafe 则保留并 fail closed |
| discarded tombstone | `workspaces.json` 中 `DISCARDED` record | 保留 identity/state/evidence；revision 不再可读 | base/candidate 删除，workspace tree 最迟下次 load 删除；tombstone `must-retain` |
| published configurable root | `published-bundles-path`；`.foggy-published-owner.json.storeId` | `RuntimePublishedBundleArtifactStore` | 只做严格完整校验；当前无枚举/cleanup/GC |
| `artifacts/.staging-UUID` | 仅名称；`.artifact.json` 在资源写完后才写，早期失败可能没有 owner marker | `prepareArtifact` 的原子目录 move 前阶段 | 当前作为 unknown layout 阻断整个 store；本探针实证保留 partial TM，`unknown-preserve` |
| `artifacts/{attemptId}` | root storeId + `.artifact.json` 的 attempt/workspace/Namespace/Bundle/revision + content hash | live published source、publish/recovery、production rollback/forward recovery、下一 workspace base | immutable；当前永不删除；按引用图为 `must-retain` 或未来候选 |
| `attempts/{attemptId}.json` | attempt UUID + schema/status/identity；自身不含 storeId，但位于 owned root | 保存 previous source/artifact、candidate、catalog generation 和 rollback evidence | 当前永不删除；corrupt/missing cross-object evidence 时 fail closed |
| published metadata `.tmp-UUID` | atomic writer 的目标派生名；attempt body 无独立 storeId wrapper | owner/attempt JSON atomic move 前阶段 | writer 无 `finally`/restart cleanup；本探针实证 restart 阻断且保留，`unknown-preserve` |
| Runtime Bundle registry record | name/Namespace/path/watch/enabled/immutablePublication/artifactRevision | 当前 live source 和“可作为下一 workspace base”的 authoritative edge | 不由 artifact store 清理；current matching artifact `must-retain` |
| release package v1 | canonical JSON/packageId；无 server-side path/store identity | export response / import request；导入后只保存 workspace `ReleaseImportEvidence` 与 candidate revision | 非持久 package，无 package GC 对象；不得虚构 registry |

### Artifact Reference Matrix

| Workspace / attempt 状态 | 必须保留的 workspace revision | 必须保留的 published artifact / metadata |
|---|---|---|
| `DRAFT` / `VALIDATED` / `STALE` | non-discarded base + candidate；active lease 额外 pin 旧 revision | 无仅由该状态产生的 artifact edge；但 live registry 或其他 attempt edge 仍独立生效 |
| `PUBLISHING`，attempt `PUBLISHING` / `SOURCE_APPLIED` | base + candidate + validation/publication evidence | candidate artifact、attempt metadata、`previousPath`；previous immutable artifact 用于失败恢复 |
| `RECOVERY_REQUIRED` | base + candidate | candidate 与 previous base、attempt/workspace evidence；显式 recover 依赖两侧 currentness |
| normal `PUBLISHED` | base + candidate | candidate artifact 是 live registry/下一 workspace base；previous path 仍由 terminal attempt evidence 引用，当前无 evidence retention，故两者都保留；previous 已不再是 normal publish 的操作输入 |
| imported `PUBLISHED` | base + candidate + release provenance | candidate live artifact与 previous base 都保留，以支持 exact 一步 rollback |
| `ROLLING_BACK` / `ROLLBACK_REQUIRED` | base + candidate | candidate 用于 forward recovery，previous base 用于 rollback；attempt/rollback evidence 均 `must-retain` |
| `PUBLISHED` + `FORWARD_RECOVERED` | base + candidate | candidate 再次 current；操作者仍可重试 pinned rollback，因此 previous base 继续 `must-retain` |
| `ROLLED_BACK` | base + candidate | previous base current；9.5.4 契约明确 candidate artifact 保留，不得在本探针降级 |
| `DISCARDED` | revision 无需保留，只留 registry tombstone | tombstone 的 publication evidence 仍是 evidence edge；不能仅因 discard 删除 artifact/attempt |
| artifact 无 attempt | 按对应 workspace record 判断 | owned/完整且跨全部事实源无 operational/evidence edge 时，满足未来 retention 后可成为候选 |
| attempt `FAILED` / `RECOVERED` | 按对应 workspace record 判断 | 不再承担 live mutation 时仍是 persisted evidence edge；只有 owner 明确允许 terminal evidence retirement 后，attempt 与 artifact 才可成组进入候选 |

### Reachability Classification

| 分类 | 必须满足的规则 |
|---|---|
| `must-retain` | root/owner/registry；live registry source；non-discarded workspace base/candidate；active lease；非 terminal publication/rollback 两侧 artifact；imported rollback/forward-recovery 两侧；当前契约要求保留的 rolled-back candidate；尚未获准退休的 terminal attempt/tombstone evidence 及其 artifact edge |
| `provably-unreachable-candidate` | publication lock 内一致快照；root/storeId/real path/manifest/hash/attempt identity 全通过；不被任何 retained workspace、retained attempt/evidence、live Bundle registry/source 引用；非 recovery/rollback 输入；再满足 owner 选定的 retention/grace |
| `unknown-preserve` | staging/temporary 无可验证 owner wrapper、foreign/unknown entry、symlink、corrupt/missing schema/hash、cross-store identity 冲突或扫描期间漂移；零删除并报告 blocked reason |

时间戳、terminal state、目录名、单个 attempt status 或“当前 Bundle 不指向”均不能单独证明不可达。

### Restart and Failure Matrix

| 场景 | 当前行为 | Evidence |
|---|---|---|
| workspace revision stage 失败 | `finally` ownership-aware 删除；失败保留时下次 load 只删除安全 owned staging | production source；`RuntimeAuthoringWorkspaceStoreTest` |
| workspace registry/migration/marker temporary | 验证 matching storeId/workspaceId 后清理；foreign/symlink/corrupt 保留并 fail closed | production `cleanupOrphans`；18-test store lane |
| workspace old revision / discard | lease 结束后清理非 base/candidate；discard 保留 tombstone并删除 owned revisions/tree | production source；store tests |
| restart 遇到 `PUBLISHING` | 仅把 workspace evidence 收敛为 `RECOVERY_REQUIRED`，不自动覆盖 live source | production reconcile；store/publish tests |
| restart 遇到 `ROLLING_BACK` | 仅收敛为 `ROLLBACK_REQUIRED`，要求 pinned forward recovery | production reconcile；store/publish tests |
| published artifact success | final UUID dir、manifest、三类资源 hash 校验通过 | existing success test |
| production artifact prepare 中途失败 | 留下含已写资源的 `.staging-*`；restart 严格拒绝，partial evidence 不删除 | 新增 `failedArtifactPreparationLeavesStagingAndRestartPreservesEvidence` |
| publication metadata rename 前退出 | `.json.tmp-*` 被 attempts layout 视为 unknown；restart 拒绝且 temp/final artifact 不删除 | 新增 `restartPreservesPublicationMetadataTemporaryAndFailsClosed` |
| published foreign/symlink/manifest/attempt corruption | 整体 fail closed，不 follow、不删除 artifact 或 sentinel | existing publication store negative tests |
| final artifact 与 attempt 单边存在 | 当前 root validation 各自校验，不建立全局 reachability/cleanup | production source review；后续 diagnostics 必须显式报告 |

### Safe Lifecycle Algorithm Boundary

后续 mutation spec 必须遵循：取得单进程 publication lock → 对 workspace root、published root、Bundle registry/live
source 建立同一只读快照 → 校验 root/storeId/marker/manifest/hash/attempt/cross-reference → 输出 plan 与 blocked
reason → mutation 前复核 snapshot identity → 仅删除 owner 可证明且不可达、满足 retention 的候选。任一漂移或
unknown/corrupt/symlink 均零删除。legacy partial staging/temporary 没有可靠 owner wrapper，不能仅按名字自动清理；
未来写入格式应先落 ownership-bearing marker/wrapper，再允许 restart cleanup。

默认 retention/grace、terminal attempt/tombstone evidence 是否允许退休及其保留期、是否允许人工确认 legacy
orphan 属于产品策略，留给后续 owner 决策；本探针不猜测数值。当前这些 evidence 没有删除语义，因此在策略
冻结前保持 `must-retain`。

### Minimum Operability Inventory

- reusable-now：workspace list/get 的 state/revision/validation/publication/release evidence；Bundle inventory 的
  immutable/artifact revision；两个 store 的 owner/manifest/attempt schema 与 filesystem size。
- small-extension：只读 lifecycle inventory service 和 management-auth API，返回 root health、对象/字节数、
  attempt/status、稳定 artifact identity、reference class、blocked reason 和是否可形成 cleanup plan；不得返回
  模型内容、token、连接串或默认绝对路径。
- Console-only：容量摘要、blocked reason、人工处置提示和显式 cleanup plan confirmation；必须等待 read-only
  API 与 mutation workitem 分别验收，不能从目录名自行推断。
- current gap：没有 artifact/attempt 枚举、容量、reference/blocked reason 或 cleanup plan 的稳定 Runtime API。

### Recommended Follow-up Workitems

1. `FEATURE-runtime-artifact-lifecycle-inventory`（P0）：先交付只读 cross-store/live reference scanner、redacted
   management diagnostics 和 stable blocked reasons；无 delete。
2. `BUG-runtime-published-store-interrupted-write-recovery`（P0）：在 1 的事实模型上为新 staging/metadata 写入增加
   ownership-bearing marker/wrapper，并只清理可证明 owned 的 interrupted writes；legacy unknown 保留。生产 mutation
   与 failure recovery 需 elevated 风险评估，不能与 inventory 混为同一验收。
3. `FEATURE-runtime-published-artifact-retention`（P1）：等待 1、2 独立验收及 owner 决定 retention/grace、terminal
   attempt/tombstone evidence 是否可退休及保留策略后，实现 evidence+artifact 成组的 plan-first GC；不得删除
   rollback、recovery、live 或 unknown 对象。
4. Console operability（P2，可选）：只消费 accepted diagnostics/plan API；不在 API 前实现目录扫描或自动清理。

Engine/SPI、Console 大改、Git、JAR binding、Agent、签名/KMS、跨 Runtime orchestration 均不进入上述前三项。

### Changed Paths and Validation

- changed_paths:
  - `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/RuntimeAuthoringPublicationStoreTest.java`
  - `docs/9.5/9.5.5/workitems/SPIKE-runtime-artifact-store-lifecycle-foundations.md`
  - `docs/9.5/9.5.5/README.md`
- tests_and_results:
  - 定向：`mvn -B -ntp -pl foggy-runtime-api -am -DskipITs
    -Dtest=RuntimeAuthoringPublicationStoreTest -Dsurefire.failIfNoTests=false
    -Dsurefire.failIfNoSpecifiedTests=false test`：1 class / 9 tests，全部通过，`57.106s`。
  - canonical focused：`mvn -B -ntp -pl foggy-runtime-api -am -DskipITs
    -Dtest=RuntimeAuthoringPublicationStoreTest,RuntimeAuthoringWorkspaceStoreTest,RuntimeAuthoringWorkspacePublishServiceTest,RuntimeAuthoringReleasePackageServiceTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：4 classes / 46 tests，全部通过，
    `53.766s`。
- manual_or_experience_evidence: production source/schema/state trace complete；测试只使用 JUnit `@TempDir`，没有
  读取仓库 `.foggy-runtime`、真实 Bundle 或外部服务。
- deviations: none
- residual_risks:
  - 当前 production published store 遇到 partial staging/metadata temporary 会持续 fail closed，仍需人工保留现场；
    本探针没有实现恢复。
  - artifact/attempt 无 GC，默认 retention/grace 与 evidence 保留期尚未由 owner 选择。
  - 一致性仍限单进程、本地非 shared-NFS filesystem；测试不能覆盖所有 OS/power-loss crash 时序。
- reused_evidence: 9.5.3 workspace ownership、publish/recovery、immutable-base/race R2，以及 9.5.4 package/
  promotion/rollback accepted evidence；本次生产输入未变化。
- omitted_validation_and_reason: 未运行 affected lane、完整 reactor、Console/Playwright、launcher、数据库矩阵、
  authority/replay/rehearsal/source-seal/tag/release/publish；test-only/docs diff 和 46-test focused lane 已覆盖全部 AC，
  追加运行不会改变本探针结论。
- readiness: READY_FOR_SIGNOFF

## References

- architecture / glossary:
  - `docs/architecture/runtime-and-model-lifecycle.md`
  - `docs/dev-guide/bundle-namespace.md`
- related work items:
  - `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-publish-recovery-api.md`
  - `docs/9.5.3/workitems/BUG-runtime-authoring-published-base-republish-and-query-race-evidence.md`
  - `docs/9.5.4/workitems/FEATURE-runtime-release-package-production-promotion-api.md`

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record:
  `docs/9.5/9.5.5/acceptance/SPIKE-runtime-artifact-store-lifecycle-foundations-signoff.md`
- blocking_items: none
- follow_up_required: no
