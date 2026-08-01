---
doc_type: delivery-spec
delivery_type: feature
version: 9.5.3
ticket: FEATURE-runtime-authoring-workspace-publish-recovery-api
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: user
approved_at: 2026-08-01
open_questions: []
---

# Delivery Spec: Runtime authoring workspace publish 与失败恢复 API

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结开发 Runtime 中把 exact validated candidate 发布到唯一 Runtime-managed Bundle、执行
  Namespace atomic catalog refresh，并在任一失败/进程中断后安全恢复旧 live revision 的公共契约。
- canonical_path:
  `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-publish-recovery-api.md`

## Goal

- version_goal: 完成 9.5.3 手工模型创作闭环所需的服务端高风险原语，让后续 Console 只表达真实
  publish/recovery 状态，不调用 `/resources/save` 或 `/models/refresh` 拼装伪发布。
- target_outcome: 对一个 current、exact、fully validated workspace revision，Runtime 在持久 publication
  intent 保护下生成 immutable Bundle artifact、切换唯一 source、执行 full-Namespace refresh 并记录
  `PUBLISHED` evidence；失败时自动恢复或进入可显式重试的 `RECOVERY_REQUIRED`，不得留下未知半发布。
- critical_outcomes:
  - publish 只接受 exact current validated revision 与创建时 base identities，source/head 漂移全部在 live
    mutation 前 fail closed。
  - published artifact 内容与 candidate revision 完全一致，Runtime Bundle registry、live inventory、
    committed source revision 和 catalog generation 收敛到同一发布事实。
  - source/registry/refresh/evidence persistence 任一失败都有 durable attempt 与确定恢复路径；恢复只回到
    已记录的旧 Runtime-managed source，不覆盖第三方漂移。
  - 并发 publish 单赢家；live query 在切换窗口只能观察 old、new 或稳定 not-current，绝不能观察混合/
    半构建 catalog。
  - 既有 low-level resource save 不得修改 immutable published artifact；新 workspace 仍可从已发布 Bundle
    创建。
- success_is_sufficient_when: AC-1 至 AC-10 均有 elevated focused/affected 证据，真实 temp filesystem +
  SQLite 路径证明成功发布、新 live query、自动恢复和显式 recovery，且没有 Engine/Console/launcher/
  dependency 越界改动。

## Scope

- in_scope:
  - `POST /api/v1/authoring/workspaces/{workspaceId}/publish`：exact expected candidate/base identity、
    server-owned Namespace/Bundle、全量 preflight、durable attempt、immutable artifact、Bundle source switch、
    registry persistence、full-Namespace refresh、terminal evidence。
  - `POST /api/v1/authoring/workspaces/{workspaceId}/publish/recover`：只针对同 workspace、candidate 和
    publication attempt 的失败恢复；恢复到 attempt 中记录的旧 source 并重新刷新旧模型。
  - workspace 状态扩展 `PUBLISHING`、`RECOVERY_REQUIRED`、`PUBLISHED`，以及结构化、可脱敏的 last
    publication evidence；`PUBLISHED` 为终态但保留 base/candidate/evidence 供审计。
  - 新增独立 ownership-bearing published-artifact root 与 additive 配置，存放按 Bundle/revision 寻址的
    immutable TM/QM/FSScript snapshot；foreign、symlink、hash mismatch、未知 layout fail closed。
  - Runtime-managed Bundle registry/inventory 对 immutable publication source 的事实表达与 low-level
    mutation guard；published Bundle 仍 `workspaceEligible=true`，但 direct resource `editable=false`。
  - publication/recovery 单进程互斥、restart reconciliation、fault injection、稳定 RuntimeEnvelope、管理
    auth、架构/版本文档和 focused/affected tests。
- affected_modules:
  - `foggy-runtime-api`
  - `docs/architecture`
  - `docs/9.5.3`
- external_dependencies: none；复用现有 Java NIO/Jackson、workspace store ownership、Runtime Bundle
  registry/SystemBundlesContext、`RuntimeModelOperations`/atomic refresh 和当前测试工具。

## Non-Goals

- out_of_scope:
  - Console UI；由下一独立 workitem 接入已经签收的 publish/recovery API。
  - 成功发布后的任意历史 rollback、revision selector、rebase/merge、release package、生产 promotion/
    import、Git、跨 Runtime 编排或审批流。
  - JAR/classpath/configured external publish、JAR fork/upgrade、多 Namespace binding 或非 filesystem source。
  - 多进程/shared-NFS writer、分布式锁、远程 artifact store、保留期/GC、签名或加密。
  - 修改 candidate query mode、模型语言、权限语义、数据库或现有 live query REST contract。
- do_not_touch:
  - `foggy-dataset-model-*` 与 Model SPI v2；若当前 Runtime atomic refresh 不足，必须 `NEEDS_REPLAN`，
    不得顺手增加 Engine public primitive。
  - `addons/foggy-runtime-console/frontend`、`foggy-mcp-launcher`、POM/Maven 依赖图和数据库 schema。
  - 已签收 spike/candidate/workspace/Console/cleanup 的历史验收正文。
- non_blocking_or_waivable_items:
  - published artifacts 本阶段不自动 GC，磁盘占用是有界可观察的后续问题；不得以 GC 为由删除尚被
    registry、workspace 或 recovery attempt 引用的 artifact。
  - 单进程 writer 与非 shared-NFS 前提继续沿用当前 workspace baseline。
  - 成功切换 source 到 refresh 完成之间允许短暂稳定 not-current/不可用；不允许旧新混合或绕过
    catalog source-revision guard。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| publish 与 recovery 使用独立 workspace routes | 高风险 lifecycle 不能由 Console 串联低层 save/refresh | 所有 method 强制 configured management auth-code；不接收 caller path/Namespace/Bundle |
| 请求固定 candidate + base Bundle + base Namespace source revisions | 用户确认的对象必须与服务端 current head/base 完全一致 | 任一缺失或 mismatch 返回稳定 conflict/stale，零 artifact/live mutation |
| candidate 先复制为独立 immutable published artifact，再切换 Bundle path | 不原地覆盖 active 目录，旧 source 可精确恢复 | 新 root 有独立 ownership；artifact hash 等于 candidate revision；不复用 workspace revision path |
| published artifact root 使用 additive 默认配置 `.foggy-runtime/published-bundles` | 与 authoring store 和 operator source 分离，支持 durable provenance | 初始化/foreign/symlink规则沿用 ownership fail-closed 思路；与 authoring store、非自身 Bundle source 双向 disjoint |
| Bundle registry path 切换到 immutable artifact，published `watch=false` | immutable revision 不能被 watcher 或低层 save 静默改写 | inventory 明示 immutable/source identity；新 workspace仍可 fork；Bundle remove/replace会使其他 workspace stale |
| full Namespace refresh 是 publish transaction 的必要阶段 | TM/QM/FSScript 增删和跨 Bundle依赖不能安全缩成 model list | refresh 本身原子发布 catalog；失败不把 candidate catalog 发布为 live |
| durable publication attempt 必须在首次 live mutation 前落盘 | registry/store/进程失败后仍能证明 base、candidate 和恢复目标 | attempt ID opaque；只存安全 identity/path 的内部值，API 不返回绝对 path |
| publish 成功后 workspace `PUBLISHED` 终态 | exact revision 与 live evidence 已固定，不再允许编辑/validate/query/discard | 资源/diff/evidence 可读；创建后续 workspace 是再次修改入口 |
| 失败优先自动恢复；不能证明恢复完成则 `RECOVERY_REQUIRED` | 不把未知半发布包装成普通失败或安全重试 | recovery request 必须 pin attempt + candidate；恢复成功后 workspace `STALE`，保留草稿并要求新建 workspace |
| recovery 只恢复 failed attempt 的 base，不等于历史 rollback | 本 workitem 只关闭失败原子性，不建立版本管理产品 | 若 live source 既非 attempt base 也非 candidate，`WORKSPACE_RECOVERY_CONFLICT` 且零覆盖 |
| 同进程 publication 全局串行 | source revision/catalog/registry 是 Namespace 共享事实，先选择最保守一致性 | 不承诺多进程/NFS；不同 workspace 并发也不得交错事务阶段 |
| failure response 与 GET 使用结构化 publication evidence | Console 需要显示阶段、影响和恢复动作，不能解析日志 | 返回 attempt/status/revisions/generations/timestamps/diagnostics，不返回 path、secret、stack trace |

## Acceptance Criteria

- [x] AC-1: publish request 缺失、candidate/head mismatch、base identity mismatch、非 `VALIDATED`、非
  Runtime-managed filesystem、inactive/immutable corruption 或 Namespace/source/content drift 均在 artifact/
  registry/live/catalog mutation 前拒绝；既有 state、files、inventory、source revision 和 catalog identity
  不变。
- [x] AC-2: publish preflight 对 exact revision 重新验证 store hash、last validation evidence、overlay
  ownership、source currentness 和 target capability；candidate artifact 只含 canonical
  `.tm/.qm/.fsscript`，其 ownership、manifest 和 content hash 原子落盘且等于 `candidateRevision`。
- [x] AC-3: 成功 publish 以 durable attempt 开始，原子切换唯一 Bundle source 并持久化 registry，使用
  `watch=false` 执行 full-Namespace refresh；成功后 live query 使用 candidate TM/QM/FSScript，catalog
  generation/source revision、inventory source identity、registry artifact revision 和 workspace publication
  evidence 完全对应，state 为 terminal `PUBLISHED`。
- [x] AC-4: `PUBLISHED` workspace 不再允许 save/delete/validate/candidate query/discard/re-publish/recover，
  但 metadata、resources、diff、validation 与 publication evidence 可读；从当前 published Bundle 新建
  workspace 得到 candidate-equal base revision，未持久化 candidate catalog/query result。
- [x] AC-5: immutable published Bundle 对现有 `/resources/save` 与任何 direct mutable resource route 返回
  稳定只读错误且逐字节不变；普通 Runtime-managed mutable external Bundle 的原行为保持。public Bundle
  replace/remove 可改变 source identity，但不能原地修改或删除 publication artifact。
- [x] AC-6: source switch、Bundle registry persist、refresh 或 final workspace/evidence persist 任一注入失败
  时，自动 recovery 尝试恢复 exact prior registry/source 与 full-Namespace catalog；恢复完成必须证明旧
  source content、live query behavior、inventory/registry 和 catalog currentness 一致，并记录
  `RECOVERED`，不能把原 publish 返回成功。
- [x] AC-7: 自动恢复未完成或进程在 durable attempt 后中断时，restart/GET 将 workspace 收敛为
  `RECOVERY_REQUIRED`，不自动覆盖未知 live state；显式 recover 只有 attempt/candidate/current live identity
  全部匹配才可幂等恢复。恢复成功后 state 为 `STALE`、旧 live 可查询、candidate 草稿/evidence 保留；
  再次 recover 不重复 mutation。
- [x] AC-8: recovery 时 live source 出现第三方漂移、artifact/manifest/store corruption、base 缺失、registry
  指向未知 path 或 recovery refresh 再失败，返回稳定 `WORKSPACE_RECOVERY_CONFLICT`/`FAILED`，保留现场和
  durable evidence，不删除、猜测、回退 live query 或标记成功；错误明确 `safeToAutoRepair=false`。
- [x] AC-9: 两个 publish、publish 与 save/discard、publish 与 Bundle replace/remove 的受控并发只有一个
  linearized winner；loser 得到 conflict/stale。切换期间并发 live query 只得到 old/new 或稳定
  source-not-current，不得看到 mixed resources、candidate catalog 或错误 Namespace。
- [x] AC-10: 新 routes 在 `none-dev-test-only`、`mutations`、`management-all` 下都要求 configured auth-code，
  RuntimeEnvelope/error phase/status/secret redaction 稳定；既有 workspace、Bundle、resource、model refresh、
  candidate/live query compatibility 与 startup restore 全部通过。

## Contract / Data / Security Constraints

- API or event contract:
  - additive routes：`POST .../{workspaceId}/publish` 与 `POST .../{workspaceId}/publish/recover`。
  - publish body 至少包含 `expectedCandidateRevision`、`expectedBaseBundleRevision`、
    `expectedBaseNamespaceSourceRevision`；recover body 至少包含 `expectedCandidateRevision` 与 opaque
    `publicationAttemptId`。空值、未知字段处理沿用当前 Jackson/Runtime API 规则。
  - workspace/info 或 dedicated response 返回 additive `lastPublication`：attempt/status、candidate/base/
    applied revisions、catalog generation/source revision before/after/recovered、时间与安全 diagnostics。
  - stable phases：`workspaces.publish.preflight|artifact|source|refresh|commit|recovery`；核心错误至少区分
    revision conflict、stale/not-validated、publish failed、recovery required/conflict/failed、artifact/store
    corrupt。HTTP/RuntimeEnvelope 继续使用当前 workspace 映射，不返回 raw exception。
- data and migration:
  - workspace internal schema additive 记录 publication attempt/evidence；必须从当前 ownership-bearing v2
    无损读取，旧 record 缺失 publication 字段等价于 `none`。如需 schema bump，migration 必须先完整只读
    验证、原子提交、可重试、零 revision deletion。
  - published root 独立 ownership/manifest；immutable artifact 只追加不原地更新，本 workitem 无 GC。
  - Runtime Bundle registry 可 additive 记录 immutable/artifact revision；旧 record 默认 mutable external，
    restart 必须恢复相同 source/watch/immutability 事实。
- compatibility and rollback:
  - 新 route/DTO/state 为 additive；既有 workspace 与 live APIs 成功语义不变。
  - 回退旧程序前必须停用 authoring publish；旧程序不理解 immutable registry/artifact metadata，不能声明
    可安全继续低层写。source artifact 本身保留，可由 operator 备份/恢复 registry。
  - 本 workitem 的 recovery 是失败补偿，不承诺成功 publish 的产品 rollback。
- permissions and secrets:
  - publish/recover 仅管理 auth，不接收或透传业务 `Authorization`；不执行 candidate data query。
  - workspace/publish identity 不是权限身份。API、日志、diagnostics、manifest 外显字段不得泄露 auth-code、
    token、JDBC secret、absolute path、storeId、内部 stack trace 或文件内容。
  - recovery 为破坏性控制面操作，任何不确定 identity 必须 fail closed，不能以
    `safeToAutoRepair=true` 诱导自动重试。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/AC-2 | must-pass | critical | temp store/artifact preflight、drift、ownership、fault tests | accepted workspace store/overlay | exact zero-mutation snapshots and candidate hash/manifest |
| AC-3/AC-4 | must-pass | critical | real filesystem + SQLite publish/live query/restart | accepted candidate/workspace real execution | candidate data becomes live, terminal evidence, next workspace base |
| AC-5 | must-pass | critical | resource/Bundle controller immutable matrix | current resource save/Bundle compatibility | published bytes unchanged, mutable legacy path unchanged |
| AC-6 | must-pass | critical | source/registry/refresh/final-persist fault injection | atomic refresh failure evidence | automatic base restore + old live query/catalog currentness |
| AC-7/AC-8 | must-pass | critical | phase-by-phase restart/recover/corruption/drift matrix | workspace restart/corruption | durable idempotence, explicit recovery, no overwrite/delete |
| AC-9 | must-pass | critical | multithread publication/source mutation/query races | workspace CAS/lease + refresh concurrency | single winner and old/new/not-current only |
| AC-10 | must-pass | critical | random-port auth/envelope/secret + affected compatibility lane | accepted 121-test Runtime lane | every new method denied/allowed and old API unchanged |

## Validation Budget and Evidence Sufficiency

- assurance_level: elevated；公共管理 API、live filesystem/source mutation 与失败补偿命中高风险条件。
- lightweight_validation:
  - `git diff --check` 与所有 untracked 文件 no-index whitespace check，单次 `<5m`。
  - publication DTO/state/store/artifact ownership/preflight/controller unit tests，单次 `<5m`。
- medium_validation:
  - focused publish/recovery/store/Bundle/refresh tests，预计 `5-15m`。
  - `foggy-runtime-api -am` affected lane，含真实 SQLite、auth、Bundle/resource/model compatibility，预计
    `5-20m`。
- expensive_validation: none by default；不运行完整 reactor、数据库矩阵或 release chain。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none；不是最终 tag/release/promotion，风险集中在 Runtime API filesystem/
  lifecycle boundary，可由 focused + affected lane 判断。
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: 若必须修改 Model SPI/Engine、引入外部 artifact store/依赖、数据库 migration、
  launcher 装配或多进程语义，设置 `NEEDS_REPLAN`，不得自行扩大验证或实现。
- maximum_expensive_attempts: 0
- reusable_evidence:
  - workspace API R2 的 ownership/path/CAS/real candidate/auth 证据与 cleanup 的 Console 证据；只有 Runtime
    publish 直接触及的 source/registry/refresh/DTO tests 需要刷新。
  - Engine 未改时复用 candidate-query 4 classes / 22 tests；publish 不改变 detached/candidate port。
- implementation_validation_commands:
  - focused（按实际新增类名等效调整并记录）：
    `mvn -B -ntp -pl foggy-runtime-api -am -DskipITs
    -Dtest=RuntimeAuthoringWorkspacePublishServiceTest,RuntimeAuthoringPublicationStoreTest,RuntimeAuthoringWorkspaceStoreTest,RuntimeAuthoringWorkspacesControllerTest,RuntimeBundleRegistryServiceTest,RuntimeBundlesControllerTest,RuntimeModelRefreshLifecycleTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`
  - affected：
    `mvn -B -ntp -pl foggy-runtime-api -am -DskipITs
    -Dtest=RuntimeAuthoringWorkspacePublishServiceTest,RuntimeAuthoringPublicationStoreTest,RuntimeAuthoringWorkspaceStoreTest,RuntimeAuthoringWorkspaceServiceTest,RuntimeAuthoringWorkspaceRealExecutionTest,RuntimeAuthoringWorkspacesControllerTest,RuntimeCandidateQueryServiceTest,RuntimeApiAuthCodeGateTest,RuntimeBundlesControllerTest,RuntimeBundleRegistryServiceTest,RuntimeCapabilitiesControllerEnabledTest,RuntimeModelRefreshLifecycleTest,RuntimeModelValidationIsolationTest,RuntimeModelsControllerCompatibilityTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`
- stop_when_evidence_is_sufficient: AC-1 至 AC-10 的成功、自动恢复、显式 recovery、restart/corruption、
  concurrency、auth 和 compatibility evidence 全绿，source review 证明零 Engine/Console/dependency 越界，
  tracked/untracked checks 通过后停止。
- validation_not_required: Engine 未改时不重跑其 lane；Console/build/Playwright 由下一 workitem执行；完整
  Maven reactor、launcher package、数据库矩阵、authority/replay/rehearsal/source-seal、tag、release、
  production promotion 或真实 publish 环境均不运行。

## Waiver Policy

- waivable_items: 仅 artifact 自动 GC、错误详情排版和非关键 diagnostics 文案；当前 workitem 可无 GC。
- authorized_role: product owner / delivery owner
- non_waivable_guards: exact validated identity、foreign/unknown zero overwrite/delete、durable attempt、immutable
  artifact、single winner、registry/source/catalog convergence、automatic/explicit recovery correctness、live query
  old/new/not-current only、auth/secret guard 和证据真实性。
- required_risk_record: waiver 必须记录磁盘/展示边界、可检测性与 follow-up；不得 waiver 数据丢失、半发布、
  mixed catalog、错误 source 恢复、权限绕过或把 failed/recovery-required 写成 published。

## Risks and Open Questions

- known_risks:
  - publish root 与 Bundle registry 构成第二个 ownership-bearing filesystem boundary；foreign/unknown entry
    必须保留并 fail closed，不能复制早期 workspace cleanup 缺陷。
  - source switch 会推进 committed source revision；refresh 失败后即使恢复相同 base content，也需要再次
    refresh 生成 current catalog，因此原 workspace validation identity 不能伪装仍 current。
  - final evidence persistence 是最危险 fault point；durable attempt 必须足以在“live 已是 candidate”时选择
    保守恢复，而不是根据内存状态猜测成功。
  - immutable artifact retention 会增长磁盘，但在无已证明安全 GC 前保留优先于回收。
  - 当前一致性只覆盖单 Runtime 进程；外部工具绕过 Runtime 修改 published artifact 属于 unsupported
    tampering，必须在下一操作中由 hash/manifest fail closed。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目 `CLAUDE.md`、Runtime/model lifecycle architecture、authoring design、accepted
  workspace API/R2/ownership signoff 和当前 Bundle/resource/refresh 实现。
- 优先建立 RED tests：成功真实 publish、refresh failure 自动恢复、final persist failure/restart recovery、
  third-party drift conflict、immutable resource-save rejection 和并发 single-winner；不得以 Mockito-only
  source 代替所有 filesystem/SQLite 证据。
- 在 scope 内自主决定 publication coordinator、store schema/manifest、DTO 和 fault seam；server 必须组装
  path/Bundle/Namespace，caller 不得提交 filesystem target 或 recovery source。
- publication artifact 创建可以先于 durable attempt，但在任何 live mutation 前必须 hash/ownership 验证并
  成功持久 attempt；任何 recursive delete/cleanup 都不属于本 workitem 授权。
- 如需 Engine/Model SPI、Console、launcher、依赖、数据库、多进程或成功 rollback 语义，设置
  `NEEDS_REPLAN` 并停止相关扩展。
- 运行精确验证命令并记录 classes/tests/failures/skips/耗时；实现类名变化时记录等效 selection，不得以
  `failIfNoSpecifiedTests=false` 将未发现新测试误报为通过。
- 未经用户明确批准，不得运行完整 reactor、Console/Playwright、launcher、数据库矩阵、authority/replay/
  rehearsal/source-seal、tag、release 或 production publish。
- 达到 evidence sufficiency 后填写 `Implementation Result`，将状态改为 `READY_FOR_SIGNOFF`；不得自行
  `ACCEPTED` 或改写已签收历史记录。

## Implementation Result

- implementation_summary:
  - 新增 workspace `publish`/`publish/recover` 管理 routes、pinned request DTO、publication evidence 与
    `PUBLISHING`/`RECOVERY_REQUIRED`/`PUBLISHED` 状态。
  - 新增 ownership-bearing immutable artifact/attempt store、单进程 publication lock 和 publication
    coordinator；实现 exact preflight、durable intent、source/registry/full-Namespace refresh、自动补偿、
    restart reconciliation 与显式幂等 recovery。
  - Runtime Bundle registry/inventory 持久化 immutable artifact revision；published Bundle `watch=false`、
    direct resource save 只读，但仍可作为新 workspace base。Bundle mutation 与 resource save 共用
    publication lock，replace/remove 不回收 artifact。
- changed_paths:
  - `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/**`
  - `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/**`
  - `docs/architecture/runtime-and-model-lifecycle.md`
  - `docs/9.5.3/README.md`
  - 本 canonical delivery spec
- tests_and_results:
  - frozen focused lane：7 classes / 57 tests，0 failures，0 errors，0 skipped，54.862s。
  - frozen affected lane：14 classes / 146 tests，0 failures，0 errors，0 skipped，1m02s。
  - real filesystem + SQLite focused lane：`RuntimeAuthoringWorkspaceRealExecutionTest` 4/4；证明 candidate
    FSScript/TM/QM 成为 live 查询、refresh failure 自动恢复 `LIVE-001`、连续失败后的显式 recovery 恢复
    `LIVE-001`，且 catalog source revision 与 committed source revision 收敛。
  - publication/resource/Bundle lock focused lane：3 classes / 17 tests，0 failures/errors/skips；含 immutable
    save rejection、mutable compatibility、shared lock 与 artifact retention。
  - auth/resource targeted lane：2 classes / 17 tests，0 failures/errors/skips；publish/recover 在所有配置模式
    均受 configured management auth-code 保护。
- manual_or_experience_evidence:
  - source review 确认生产改动只在 `foggy-runtime-api`；没有修改 Engine/Model SPI、Console、launcher、POM、
    dependency graph 或数据库 schema。
  - artifact root、manifest/hash、attempt schema/status、foreign entry 与 symlink 都 fail closed；错误 envelope
    不返回 absolute publication path、suppressed cause、secret 或 stack trace。
- deviations: none
- residual_risks:
  - published artifact 本阶段不做 GC；仍被 registry、workspace 或 attempt 引用的 artifact 必须保留。
  - 一致性承诺仍是单 Runtime 进程、非 shared-NFS writer；外部绕过 Runtime 的 filesystem tampering 在
    下一次 artifact/store 操作中 fail closed。
  - 成功 publish 后的历史 rollback、release package、生产 promotion、Git 与 JAR 多 Namespace binding
    属于后续 workitem。
- reused_evidence:
  - 复用已签收 candidate-query 的 request-local catalog/cache/permission 隔离设计；affected lane重新执行
    `RuntimeCandidateQueryServiceTest` 5/5 和 model validation isolation 2/2。
  - 复用既有 atomic catalog refresh 实现；`RuntimeModelRefreshLifecycleTest` 4/4 重新执行。
- omitted_validation_and_reason:
  - 按冻结预算未运行完整 Maven reactor、Console build/Playwright、launcher package、数据库矩阵、
    authority/replay/rehearsal/source-seal、tag、release 或 production publish；本 workitem 未修改这些边界。
- readiness: READY_FOR_SIGNOFF

## Acceptance Status (R1)

- acceptance_status: rejected
- acceptance_decision: rejected
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record:
  `docs/9.5.3/acceptance/FEATURE-runtime-authoring-workspace-publish-recovery-api-signoff.md`
- blocking_items: subsequent immutable-base publication；AC-9 concurrent live-query evidence
- follow_up_required: yes

## Acceptance Status (R2)

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex R2 reviewer
- signed_off_at: 2026-08-01
- acceptance_record:
  `docs/9.5.3/acceptance/FEATURE-runtime-authoring-workspace-publish-recovery-api-signoff-r2.md`
- blocking_items: none
- follow_up_required: yes

## References

- requirement / issue: 用户批准按“Console cleanup → Runtime publish/失败恢复 API → Console publish 接入”
  顺序推进；本事项为第 2 项。
- architecture / glossary:
  - `CLAUDE.md`
  - `docs/architecture/runtime-and-model-lifecycle.md`
  - `docs/design/runtime-console-product-charter.md`
  - `docs/9.5.3/runtime-model-authoring-design.md`
- related work items:
  - `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-api.md`
  - `docs/9.5.3/acceptance/FEATURE-runtime-authoring-workspace-api-signoff-r2.md`
  - `docs/9.5.3/workitems/BUG-runtime-authoring-workspace-store-root-ownership.md`
  - `docs/9.5.3/workitems/REF-runtime-console-authoring-workspace-cleanup.md`
