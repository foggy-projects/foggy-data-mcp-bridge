---
doc_type: delivery-spec
delivery_type: feature
version: 9.5.3
ticket: FEATURE-runtime-console-authoring-workspace
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: user
approved_at: 2026-08-01
open_questions: []
---

# Delivery Spec: Runtime Console authoring workspace 草稿闭环

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结 Console 对已验收 Runtime authoring workspace API 的首个手工草稿创作闭环；本事项
  不包含 publish，并且不改变 Engine、Runtime API 或 Bundle 生产行为。
- canonical_path: `docs/9.5.3/workitems/FEATURE-runtime-console-authoring-workspace.md`

## Goal

- version_goal: 在 Runtime Console 产品章程内，让持有管理凭据的模型开发者能够围绕一个
  `workspaceEligible` Bundle 创建、编辑、比较、校验和查询隔离草稿，为后续独立的开发环境
  publish/恢复 workitem 提供稳定 UI 与交互基线。
- target_outcome: 用户从当前 Namespace 选择一个明确可编辑的 Bundle，创建或打开持久 workspace，
  对其中 TM/QM/FSScript 做轻量编辑，并在同一 exact candidate revision 上完成 save/delete、diff、
  validate 和 candidate query；全过程不修改 live Bundle/catalog。
- critical_outcomes:
  - Console 只依据服务端 `workspaceEligible`/`editable` capability 开放创建入口，不从 path、source
    文本或加载顺序猜测可编辑性。
  - 所有资源 mutation 都提交当前 `expectedCandidateRevision`；冲突时不覆盖服务端内容，也不丢失
    浏览器内未保存草稿。
  - diff、validation evidence 和 candidate query 明确绑定 exact revision，用户能区分草稿结果与
    live Runtime 结果。
  - `DRAFT`、`VALIDATED`、`STALE`、`DISCARDED` 状态及稳定错误码形成可理解、可恢复的交互。
  - Console 不把 save、validate 或现有 Bundle resource save 描述成 publish/apply/refresh。
  - Engine 与 Runtime API 生产代码保持零改动；现有 live model/query/resource 行为保持兼容。
- success_is_sufficient_when: AC-1 至 AC-12 均有当前源码对应的 must-pass 证据，桌面端完成完整草稿
  闭环，移动端完成查看和基本编辑/校验闭环，且 source review 确认没有 live mutation、API/Engine
  扩展或越界能力声明。

## Scope

- in_scope:
  - 在“数据与模型空间”中增加清晰的“模型创作”任务入口，并与当前 Namespace、URL 和 context
    rail 保持一致。
  - 扩展 Console Bundle 类型和展示，忠实消费 `sourceType`、`editable`、`workspaceEligible`、
    `managedByRuntimeApi`、`namespaceBindings`、`sourceIdentity` 等既有 additive 字段。
  - 按当前 Namespace 列出 active workspace；从一个 eligible Bundle 创建、打开和 discard
    workspace。允许服务端返回同一 Bundle 的多个 active workspace，不在前端伪造唯一性。
  - 展示 workspace identity、target Namespace、source Bundle/source kind、base revisions、candidate
    revision、状态、更新时间、最后验证证据和 diagnostics；内部绝对路径不得进入 UI。
  - 展示 `.tm`、`.qm`、`.fsscript` 资源树/列表和 metadata，按需读取单个 UTF-8 内容。
  - 提供克制的纯文本轻量编辑：打开、修改、新建、保存和删除资源；不自动保存，不新增 Monaco、
    CodeMirror 或其他编辑器依赖。
  - save/delete 使用 optimistic revision guard；成功后以服务端响应的新 workspace metadata 为
    唯一 head，并刷新相关资源/diff/validation 视图。
  - 展示 immutable base 与 pinned candidate 的 resource-level diff；支持查看服务端返回的 base/
    candidate content，不在浏览器内实现 merge 引擎。
  - 对 current candidate revision 执行完整 workspace validate，展示文件统计、cascading error、按
    path/type/code/category 分类的问题及 exact validation evidence。
  - 提供 workspace candidate query validate/execute。模型名由用户明确选择或输入；若 UI 从 `.qm`
    文件名提供建议，必须标成建议并允许修改，不得把文件名推断冒充 canonical model identity。
  - 复用现有查询 DSL、结果表格、warning、耗时和 CSV 能力；candidate query 必须调用 workspace
    route，并展示 workspace/revision/candidate catalog identity。
  - 对 capability unavailable、empty、loading、failure、revision conflict、stale、discarded 和无资源
    状态提供明确下一步；危险/终结动作继续二次确认。
  - 单元测试、typecheck、production build、desktop/mobile Playwright 和有界人工体验证据。
- affected_modules:
  - `addons/foggy-runtime-console/frontend`
  - `docs/9.5.3`
- external_dependencies: 复用现有 Vue 3、Vue Router、Element Plus、Axios、Vite、Vitest、Playwright
  和 Runtime API v1；不新增 npm/Maven 依赖。

## Non-Goals

- out_of_scope:
  - publish、apply、refresh、promotion、rollback、rebase、revision merge、release package 或生产导入。
  - Git clone/branch/commit/push、Git 凭据、VS Code 插件、AI Agent 或跨 Runtime 控制面。
  - JAR/classpath/configured external 的 fork、编辑、覆盖、升级或多 Namespace binding。
  - 多用户 owner、RBAC、审批、审计或把共享 Runtime auth-code 描述成用户身份。
  - 大型 Web IDE、语法服务器、自动完成、格式化器、任意文件系统、终端、二进制资源或通用 diff/
    merge 工具。
  - 自动保存、后台自动 validate/query、自动冲突修复或 mutation 自动重试。
  - 修改现有 live Query、Model lifecycle 或 Bundle 高级资源操作的服务端语义。
- do_not_touch:
  - `foggy-dataset-model-engine` 及其他 Engine/model production modules。
  - `foggy-runtime-api/src/main`、Runtime API routes/DTO/error/auth/capability 和 workspace store 行为。
  - `foggy-mcp-launcher`、Maven dependency/configuration、数据库、Bundle live source 和 catalog。
  - `docs/9.5.3/runtime-model-authoring-design.md` 的 `PROPOSED` 状态；本事项不能把后续路线写成已交付。
  - 与本事项无关的 Console 页面和当前工作树中的用户改动。
- non_blocking_or_waivable_items:
  - 语法高亮、行号、快捷键、可拖动分栏、逐行 diff 和高级筛选属于后续体验优化。
  - 首期可以用资源列表表达浅层目录，不要求任意深度的 IDE 文件树动画。
  - 移动端不要求承担长时间专业编码，但必须支持检查、简单修改、保存、validate 和错误恢复。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 本 workitem 只交付草稿闭环，publish/恢复另立契约 | 当前 workspace API 已验收，但 publish 尚无安全 Runtime 原语 | UI 不得出现伪 publish，不调用 live resource save 代替 publish |
| 模型创作属于当前 Namespace 工作区 | Namespace 是运行上下文，Bundle 是源码/可编辑性边界 | 路由、顶部 Namespace、请求 `X-NS` 和选中 workspace 必须一致 |
| 只有 `workspaceEligible=true` 的 Bundle 可创建 workspace | capability 是服务端事实，JAR/configured external 永远只读 | 不能从 path、sourceType 或 managed 标记自行放宽 |
| 一个 workspace 固定一个 Namespace 和一个 source Bundle | 与已验收服务端 contract 一致 | Console 不允许客户端覆盖 workspace metadata 或内部路径 |
| 允许同 Bundle 多个 active workspace | 服务端没有声明前端唯一性 | UI 应展示 identity/revision/time，避免误开错误草稿 |
| 轻量编辑采用现有依赖内的文本控件 | 满足小范围人工创作，不建设 Web IDE | 不新增编辑器依赖；资源类型仍只限 TM/QM/FSScript |
| 不自动保存，mutation 必须显式提交 | 草稿是模型代码面，隐式写入会扩大冲突和误操作 | 离开 dirty resource/workspace 前必须提醒；不得后台自动重试 |
| 服务端 candidate revision 是唯一并发真值 | workspace store 已提供 immutable revision 与 CAS | 所有 save/delete/discard 使用 exact revision；成功后才前移 head |
| revision conflict 时保留本地草稿 | 自动覆盖或刷新会造成代码丢失 | 提供复制/保留草稿及显式刷新入口，不自动合并或覆盖 |
| diff、validate、query 是独立动作 | 保存不等于有效，也不等于查询或发布 | UI 不串成隐式副作用；内容变化后旧 validation 明确失效 |
| candidate query 使用显式模型名和 workspace route | 资源路径不一定等于 canonical QM identity | 文件名只能作为可修改建议，不能作为权威解析结果 |
| STALE workspace 保留可读与草稿迁移能力 | 当前 API 无 rebase；用户需要 diff/content 来重建 workspace | 禁止 validate/query；保存/删除/diff 是否可用忠实遵循服务端 contract |
| DISCARD 是终结动作 | 服务端 tombstone 不可继续操作 | 二次确认并提交 expected revision；成功后只保留 metadata/状态查看 |
| Console 不持久化 workspace 内容副本 | 服务端 store 是持久化权威，浏览器不是第二个源码库 | dirty 内容只保存在当前页面内存；不得写 localStorage/URL/log |

## State and Action Matrix

| State | Read resource/diff | Save/delete | Validate/query | Discard |
|---|---:|---:|---:|---:|
| `DRAFT` | yes | yes | validate yes; query 需满足服务端 exact validation guard | yes |
| `VALIDATED` | yes | yes；成功 mutation 后旧 evidence 失效 | yes，绑定当前已验证 revision | yes |
| `STALE` | yes | 按既有 API 允许，状态仍为 stale | no；解释需新建 workspace 并迁移内容 | yes |
| `DISCARDED` | metadata only | no | no | no |

Console 必须以服务端响应和稳定错误码为最终权威；该矩阵不能通过客户端绕开后端 guard。

## Acceptance Criteria

- [x] AC-1: Console 消费完整 Bundle capability；只有当前 Namespace 下服务端明确返回
  `workspaceEligible=true` 的 Bundle 显示创建入口。JAR/classpath、configured external、inactive、
  mismatch 和其他不可写来源只读展示并解释原因；没有文本推断或越权请求。
- [x] AC-2: 用户能在 Namespace 的模型创作入口列出、创建并深链接打开 workspace；创建请求只提交
  当前 Namespace 和所选 source Bundle。多个 active workspace、empty/loading/error/capability
  unavailable 均可辨识，切换 Namespace 不复用旧请求结果或选中态。
- [x] AC-3: workspace 详情准确显示 server-owned identity、source、base/candidate revisions、状态、
  timestamps、diagnostics 和 validation evidence；不展示内部绝对路径，不把 workspaceId 当权限身份。
- [x] AC-4: 用户能浏览并按需读取 TM/QM/FSScript，进行轻量新建/编辑/保存/删除；不支持类型和无效
  path 在发送前提示，Runtime 仍是最终权威。没有自动保存，dirty 离开保护和删除确认可用。
- [x] AC-5: 每次 save/delete/discard 都使用当前 exact expected candidate revision。成功响应后才更新
  head；`WORKSPACE_REVISION_CONFLICT` 或并发变化时零自动 mutation retry、零服务端覆盖，并保留本地
  dirty 内容供复制或显式恢复。
- [x] AC-6: diff 请求固定当前 candidate revision，并以 resource/change type、base/candidate hash 和
  可选内容展示 immutable base 对比；刷新或过期响应不能覆盖新 workspace/revision 视图，UI 不声称
  提供 merge/rebase。
- [x] AC-7: validate 固定当前 candidate revision，完整展示 total/valid/invalid/cascading counts、按
  resource 分类的问题和 validation timestamp/revisions。内容 mutation 后旧 evidence 不再显示为当前
  有效结论；失败不会被包装成通过。
- [x] AC-8: candidate query validate/execute 只调用 workspace query routes，提交 exact revision 和
  用户确认的 model/payload，并复用业务 `Authorization`。结果显示 candidate workspace/revision/
  catalog identity、phase、warnings 和 rows；不得回退 live query route。
- [x] AC-9: `DRAFT`、`VALIDATED`、`STALE`、`DISCARDED` 的动作可用性符合状态矩阵；稳定
  `WORKSPACE_*`/`CANDIDATE_*` code、phase、path、suggested next action 和 safe-to-auto-repair 信息得到
  可理解表达，但 destructive mutation 永不因该标志自动执行。
- [x] AC-10: discard 具有 target Bundle、workspace 和 revision 的明确二次确认；成功后进入 terminal
  view。整个创建、保存、删除、diff、validate、query 和 discard 流程不调用 live Bundle save、model
  refresh 或 live query，不修改 live source/catalog。
- [x] AC-11: 桌面端可完成完整草稿闭环；移动端能完成 workspace/resource 检查、简单编辑保存、
  validate 和错误恢复。键盘焦点、状态文字、busy/disabled、离开提醒、对比度和窄屏无溢出均有实际
  浏览器证据。
- [x] AC-12: `git diff --check`、所有 untracked no-index whitespace check、Console typecheck、unit、
  build、focused 与 affected full Playwright 均实际通过；changed paths 只限 Console frontend 和
  9.5.3 文档，没有新增依赖、Runtime API/Engine/launcher/Maven/数据库改动。

## Contract / Data / Security Constraints

- API or event contract:
  - 只复用已验收 `/api/v1/bundles` capability 和 `/api/v1/authoring/workspaces` create/list/get/
    discard/resources/content/save/delete/diff/validate/query validate/query execute routes。
  - `RuntimeEnvelope`、`X-Foggy-Runtime-Code`、`X-NS` 和业务 `Authorization` 语义不变。
  - resource content path 必须作为正确编码的 query value 发送；workspaceId、revision、path、model
    均不得拼接出跨 origin URL。
  - 如果现有 API 无法完成任一 must-pass AC，设置 `NEEDS_REPLAN`；不得在本事项中扩展 controller、
    DTO、error、store 或 Engine port。
- data and migration:
  - 不新增数据库、浏览器持久化 schema 或模型资源迁移。
  - workspace/revision/content 继续只由当前 Runtime 的既有 server-side store 持久化。
  - query result、validation detail 和 dirty editor content 只保留必要的页面会话状态。
- compatibility and rollback:
  - 新 authoring 入口是 additive；现有 Namespace、Bundle、Models、Query、lifecycle 和执行工具保持
    可用，旧 hash route 不失效。
  - Runtime 不支持 authoring capability 时，Console 降级为解释性只读状态，不影响其他工作台。
  - UI 可通过回退 Console 静态构建恢复，不修改或迁移任何 server-side workspace 数据。
- permissions and secrets:
  - 所有 workspace route 继续由 Runtime 管理凭据保护；workspaceId 不是 auth。
  - 只有 candidate query 复用既有业务 `Authorization`；不得把管理 token 代替数据面身份。
  - token、Authorization、数据源 secret、内部路径和完整草稿内容不得写入 URL、localStorage、日志、
    screenshot 或验收记录。测试只使用显式假 token/fixture。
  - Console 不是不可信脚本 sandbox；界面需提示 validate/query 会使用当前 Runtime 依赖、权限和数据源。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1–AC-3 | must-pass | major | unit + mocked Playwright | 已验收 Bundle inventory/API contract | capability gate、Namespace/route 一致性、多个 workspace 和 metadata mapping |
| AC-4–AC-5 | must-pass | major | unit + desktop/mobile Playwright | workspace save/delete CAS 后端证据 | dirty/new/save/delete、exact revision、conflict 保留草稿和零自动重试 |
| AC-6–AC-7 | must-pass | major | unit + desktop/mobile Playwright | workspace diff/validate 后端证据 | pinned revision、stale response guard、diff content、validation issue/evidence 展示 |
| AC-8 | must-pass | major | unit + Playwright request/response assertions | candidate query 与 Console live query helpers | workspace route、model/revision/body/Auth、candidate identity 和 rows；断言零 live fallback |
| AC-9–AC-10 | must-pass | critical | state-matrix unit + Playwright negative paths + source review | Runtime 稳定错误与 isolation tests | state/action gate、confirm、错误恢复、零 live save/refresh/query 请求 |
| AC-11 | must-pass | major | desktop/mobile/keyboard Playwright + stable screenshots/manual review | 现有 responsive shell | 关键 viewport、focus、dirty leave、busy/error 和窄屏证据 |
| AC-12 | must-pass | major | typecheck + all unit + build + focused/affected E2E + diff checks | 未触及后端的 accepted evidence 可复用 | 精确命令、exit code、test/project 数量、changed/untracked paths |
| syntax highlighting/line diff/advanced editor | waivable | minor | none unless implemented | N/A | 未实现时记录为 follow-up，不影响核心签收 |
| publish/rebase/Git/JAR binding/Agent | non-goal | deferred | none | 路线设计 | UI 不声明或调用这些能力 |

## Validation Budget and Evidence Sufficiency

- assurance_level: standard
- lightweight_validation:
  - `git diff --check`；对每个 untracked 文件执行等效
    `git diff --no-index --check /dev/null <file>`。
  - 在 `addons/foggy-runtime-console/frontend` 运行 `npm run typecheck` 和 `npm run test:unit`；预计
    每轮 1–5 分钟。
  - 静态检查 authoring endpoint mapping、state/action matrix、live route negative assertions 和
    changed paths。
- medium_validation:
  - `npm run build`。
  - `npx playwright test --grep "authoring workspace"` 覆盖 desktop/mobile focused flow。
  - `npx playwright test` 覆盖当前 Console affected E2E lane；预计每轮 5–15 分钟。
  - 对 desktop/mobile 稳定状态截图做一次人工检查；宿主缺少 CJK 字体时可使用临时 fontconfig，
    不向产品加入字体依赖。
- expensive_validation: none by default；纯 Console 改动不运行 Maven reactor、launcher package、
  数据库矩阵或真实外部 Runtime 环境。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none；若实现需要 Runtime API、Engine、auth、依赖或 launcher 改动，
  直接设置 `NEEDS_REPLAN`，不以扩大验证替代重新规划。
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: none within approved scope
- maximum_expensive_attempts: 0；不得启动超过 30 分钟或 authority/replay/rehearsal/source-seal 链路。
- reusable_evidence:
  - candidate overlay、workspace API、store ownership、auth、revision、diff、validate/query 和 live-state
    isolation 的 accepted 后端证据继续有效，因为本事项不得修改其输入或生产实现。
  - 现有 Console session/client、Namespace、Bundle、query/lifecycle helper 测试可复用未改变部分；
    新 authoring flow 和被改动 UI 必须基于当前源码重跑。
- stop_when_evidence_is_sufficient: 所有 must-pass AC 都有一次当前源码对应的绿色自动化证据，
  desktop/mobile/keyboard 与 screenshots 覆盖关键状态，source review 确认零 live mutation、零 API/
  Engine 改动且 diff/untracked checks 通过后停止。
- validation_not_required: Maven reactor、Engine/Runtime API tests、Console Maven package、launcher、真实
  数据库矩阵、authority/replay/rehearsal/source-seal、tag、release、publish、Git 或 JAR packaging。

## Waiver Policy

- waivable_items: 仅语法高亮、行号、快捷键、拖动分栏、逐行 diff、高级筛选和非关键微动效。
- authorized_role: product owner / delivery owner
- non_waivable_guards: capability gate、Namespace/revision 一致性、dirty 内容保护、mutation confirm、
  禁止自动 mutation retry、权限/secret、candidate query Auth、state guard、零 live mutation、零 API/
  Engine 改动、desktop 核心闭环和至少移动端基本闭环。
- required_risk_record: waiver 必须记录具体缺失体验、受影响 viewport/用户、当前替代路径和后续 owner；
  不得用 waiver 接受数据丢失、越权、revision 错绑、live mutation 或虚假 publish。

## Risks and Open Questions

- known_risks:
  - 轻量 textarea 不提供 DSL 语法服务，复杂创作仍更适合后续 VS Code 插件；本闭环面向小范围人工
    创建、检查和修订。
  - `.qm` path 不保证等于 canonical model name；candidate query 必须保留显式可编辑模型名，不能
    静默依赖文件名推断。
  - `STALE` 尚无 rebase；用户只能保留/复制内容，创建新 workspace 并显式迁移所需草稿。
  - 共享 Runtime auth-code 不提供用户 owner、审计或多人协作；同一 workspace 并发依靠 revision
    conflict，而不是协同编辑。
  - 标准浏览器内存中的 dirty 内容在页面强制关闭或崩溃后无法恢复；本事项不新增本地持久化以避免
    形成第二源码库，UI 必须通过显式保存和 before-leave 提示降低风险。
  - 动态/外部只读依赖仍受既有部署 currentness 约束；Console 只展示服务端 stale 结论。
- open_questions: none

## Ultra Execution Contract

- 先完整读取本文件、项目 `CLAUDE.md`、Console 产品章程、9.5.3 authoring 设计、已验收 workspace
  API spec，以及适用的前端专项技能。
- 本文件已由用户批准；实现必须遵守 `APPROVED` 范围，不能重新打开 publish、Git、Agent、JAR
  binding 或后端扩展。
- 在 scope 内自主决定组件、composable、纯函数、样式和测试拆分；页面不得重新堆叠为难以维护的
  单体，也不要求照抄本文术语为文件名。
- 开始实现时将状态改为 `ULTRA_EXECUTING`。先检查并保护实际工作树；不得覆盖或回退无关用户改动。
- 如需改变目标、范围、API/Engine/依赖、安全边界或 must-pass 行为，设置 `NEEDS_REPLAN` 并停止
  相关扩展。
- 运行与改动面匹配的验证，记录精确命令、exit code、测试数量、截图/报告路径和未运行原因；不得
  声称未实际运行的测试通过。
- 未经用户明确批准，不得运行 Maven reactor、launcher、数据库矩阵、authority/replay/rehearsal/
  source-seal、tag、release 或 publish。
- 达到 evidence sufficiency 后停止，不因已验收后端能力追加重复验证。
- 完成后填写 `Implementation Result` 的 summary、changed paths、tests、experience evidence、
  deviations、residual risks、reused evidence 和 omitted validation，并将状态改为
  `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED` 或改写历史验收记录。

## Implementation Result

> 由 Ultra 执行会话填写。

- implementation_summary:
  - 在 Namespace 工作区增加“模型创作”入口和可深链接的 `workspaceId`，按当前 `X-NS` 列出、
    创建、打开和 discard workspace；只有服务端 `workspaceEligible=true` 的 Bundle 可创建。
  - 新增独立 authoring types/policy/component，覆盖 workspace metadata/revision/state、TM/QM/FSScript
    资源读取、新建、编辑、CAS save/delete、dirty leave guard 和 terminal state。
  - revision conflict 不自动重试 mutation；刷新服务端 metadata/content 后在并列面板保留本地 dirty
    草稿，只有用户再次显式保存才使用新 expected revision。
  - 增加 exact diff、成功/失败 validation evidence、candidate query validate/execute、candidate
    identity/result/warning 展示；查询只调用 workspace route，不回退 live query。
  - Runtime error client 保留 `path` 和 `safeToAutoRepair`，并允许 DELETE query params 传递 exact
    revision；提示明确声明只可安全刷新 metadata，不会自动执行 mutation。
  - `frontend-design` 影响了实现的高密度工业工作台布局：workspace revision spine、资源编辑区和
    diff/validate/query inspector 使用现有黑白线框 token，在不引入新主题/依赖的前提下保持桌面与
    移动一致。
- changed_paths:
  - `addons/foggy-runtime-console/frontend/src/api/client.ts`
  - `addons/foggy-runtime-console/frontend/src/features/authoring/AuthoringWorkspace.vue`
  - `addons/foggy-runtime-console/frontend/src/features/authoring/authoringWorkspace.ts`
  - `addons/foggy-runtime-console/frontend/src/features/authoring/types.ts`
  - `addons/foggy-runtime-console/frontend/src/features/namespace/types.ts`
  - `addons/foggy-runtime-console/frontend/src/pages/NamespacesPage.vue`
  - `addons/foggy-runtime-console/frontend/tests/unit/authoringWorkspace.test.ts`
  - `addons/foggy-runtime-console/frontend/tests/unit/client.test.ts`
  - `addons/foggy-runtime-console/frontend/tests/e2e/runtime-console.spec.ts`
  - `docs/9.5.3/README.md`
  - `docs/9.5.3/workitems/FEATURE-runtime-console-authoring-workspace.md`
- tests_and_results:
  - `npm run typecheck`：exit 0。
  - `npm run test:unit`：exit 0；10 个 test files、31 个 tests 全部通过。
  - `npm run build`：exit 0；Vite 转换 2474 个 modules 并完成 production build；仅有既有依赖
    PURE 注释和大 chunk warning。
  - `npx playwright test --grep "authoring workspace"`：exit 0；desktop Chromium + Pixel 7 mobile
    Chromium 共 2/2 focused tests 通过（9.2s）。
  - `npx playwright test`：exit 0；desktop/mobile affected Console lane 共 10/10 tests 通过
    （49.9s）。
  - `git diff --check` 及对全部 untracked 文件逐一执行的
    `git diff --no-index --check /dev/null <file>`：exit 0。
- manual_or_experience_evidence:
  - focused Playwright 使用有状态 Runtime mock 实测 eligible/read-only capability、workspace
    create/open/deep link、TM/QM/FSScript read、新建/非法 path/delete、dirty route guard、CAS conflict、
    exact diff、一次 invalid evidence 后成功 validate、candidate query 和 discard。
  - conflict 断言失败 save 只发送一次，保留 `LocalDraftOrder` 并展示服务端 `ServerOrder`；第二次
    显式 save 才使用服务端新 revision。query 请求断言 exact workspace revision/body/Namespace，
    并断言零 live `query/*`、`resources/save`、`models/refresh` fallback。
  - desktop screenshot：
    `addons/foggy-runtime-console/frontend/test-results/runtime-console-authoring--f3497-the-isolated-candidate-loop-desktop-chromium/authoring-workspace-desktop.png`
  - mobile screenshot：
    `addons/foggy-runtime-console/frontend/test-results/runtime-console-authoring--f3497-the-isolated-candidate-loop-mobile-chromium/authoring-workspace-mobile.png`
  - 人工检查确认 desktop/mobile 无横向溢出，资源、revision、状态与主动作层级清楚；截图前移除
    短暂 toast 并回到页面顶部，未隐藏产品内容。
- deviations: none
- residual_risks:
  - 当前宿主缺少 CJK 字体，截图中的中文使用缺字 fallback；DOM 文案、accessible name 和交互断言
    均通过，产品继续使用既有 CJK font stack，未新增字体依赖。
  - 编辑器是受范围约束的 textarea，不提供语法高亮、行号、自动完成或 merge；这些均为批准的
    waivable follow-up，不影响草稿闭环。
  - dirty 内容只在当前页面内存；正常路由/刷新有离开确认，但浏览器崩溃或强制终止仍可能丢失未
    保存内容，契约明确不使用 localStorage 建立第二源码库。
  - Vite 保留既有 vendor 大 chunk warning；本事项未新增依赖，Namespace 页面仍按 route 懒加载。
- reused_evidence:
  - 复用已签收 foundation/candidate-query/workspace API/store ownership 的 Bundle/JAR/FSScript、
    permission/cache、revision/CAS、diff/validate/query、auth 和 live-state isolation 证据；本事项没有
    修改其生产输入或实现。
  - 现有 Console session/client、Namespace、Bundle、live query/lifecycle E2E 作为 affected lane
    一并基于当前源码重跑 10/10，不以历史结果替代。
- omitted_validation_and_reason:
  - 未运行 Maven reactor、Engine/Runtime API tests、Console Maven package、launcher、数据库矩阵、
    authority/replay/rehearsal/source-seal、tag、release、publish、Git 或 JAR packaging；changed paths
    严格限于 Console frontend 和 9.5.3 文档，契约明确不触发这些验证。
- readiness: READY_FOR_SIGNOFF

## References

- requirement / issue: 用户确认 Engine 原语已稳定，并批准按“先 Console 草稿闭环、publish/恢复
  后置独立 workitem”的顺序继续推进。
- architecture / glossary:
  - `CLAUDE.md`
  - `docs/design/runtime-console-product-charter.md`
  - `docs/9.5.3/README.md`
  - `docs/9.5.3/runtime-model-authoring-design.md`
  - `docs/architecture/runtime-and-model-lifecycle.md`
  - `docs/dev-guide/bundle-namespace.md`
- related work items:
  - `docs/9.5.3/workitems/SPIKE-runtime-model-authoring-foundations.md`
  - `docs/9.5.3/workitems/FEATURE-runtime-candidate-query-overlay.md`
  - `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-api.md`
  - `docs/9.5.3/workitems/BUG-runtime-authoring-workspace-store-root-ownership.md`
  - `docs/9.5.2/workitems/OPT-runtime-console-namespace-workspace.md`
  - `docs/9.5.2/workitems/OPT-runtime-console-bundle-resource-operations.md`

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record: `docs/9.5.3/acceptance/FEATURE-runtime-console-authoring-workspace-signoff.md`
- blocking_items: none
- follow_up_required: yes
