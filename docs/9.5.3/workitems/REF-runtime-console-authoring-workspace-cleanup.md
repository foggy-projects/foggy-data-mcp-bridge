---
doc_type: delivery-spec
delivery_type: refactor
version: 9.5.3
ticket: REF-runtime-console-authoring-workspace-cleanup
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: user
approved_at: 2026-08-01
open_questions: []
---

# Delivery Spec: Runtime Console authoring workspace 有界清理

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结已验收 authoring workspace 的组件职责拆分，以及 candidate query 执行耗时与 CSV
  补齐范围；保持既有 Runtime 契约、用户流程和安全边界不变。
- canonical_path: `docs/9.5.3/workitems/REF-runtime-console-authoring-workspace-cleanup.md`

## Goal

- version_goal: 在开发环境 publish/恢复进入 Console 前，消除 authoring 主组件继续膨胀的风险，并
  关闭首次 signoff 记录的 query 辅助输出 process gap。
- target_outcome: workspace catalog、resource editor、candidate inspector 和 API/state 编排各有明确
  边界；用户仍完成相同草稿闭环，并可查看 candidate query 执行信息、导出安全 CSV。
- critical_outcomes:
  - 已验收 capability、revision/CAS、dirty guard、diff、validate、candidate query、discard 和
    desktop/mobile 行为不回归。
  - `AuthoringWorkspace.vue` 不再同时承载全部 API 编排、catalog、editor、inspector 模板与全部样式；
    拆分形成可独立理解和测试的职责边界，且不把同等复杂度整体搬到另一个单体组件。
  - candidate query 展示 Runtime 返回的 execution/provider/status/duration 等可用事实，并复用现有
    CSV helper 安全导出当前 candidate rows。
  - 不修改 Runtime API/Engine、不调用 live fallback、不新增依赖或浏览器持久化。
- success_is_sufficient_when: AC-1 至 AC-8 均有当前源码对应证据，focused + affected Console
  desktop/mobile 全绿，source review 确认职责收敛和零后端/契约扩张。

## Scope

- in_scope:
  - 将 authoring UI 按至少三类可维护职责拆分：workspace/source catalog、resource editor、candidate
    diff/validate/query inspector；共享异步编排和状态可放在 composable 或清晰的容器层。
  - 保持单一 server-owned workspace/candidate head，子组件不得各自复制并漂移 revision 状态。
  - 保持当前 Namespace route/deep link、eligible/read-only gate、multiple active workspace、metadata、
    resource CRUD、conflict local/server compare、validation evidence、candidate query 和 discard 流程。
  - 保持 stale response/version guard、dirty route/beforeunload guard、confirm、stable error metadata 和
    terminal state。
  - candidate query 结果展示服务端 `execution` 中存在的 provider/status/durationMs 等字段；缺失字段
    显示明确空状态，不推断或伪造。
  - 为非空 candidate query rows 提供 CSV 导出；必须复用 `queryRowsToCsv` 的 union columns、escaping
    和 spreadsheet formula injection guard，文件名包含明确 candidate model/workspace 上下文。
  - 空 rows 时禁用或拒绝导出并解释；导出不得包含 token、Authorization、内部路径或未展示诊断。
  - 重构相关 unit、focused desktop/mobile Playwright 和 affected Console E2E。
- affected_modules:
  - `addons/foggy-runtime-console/frontend`
  - `docs/9.5.3`
- external_dependencies: 仅复用当前 Vue/Element Plus/RuntimeResultTable/queryWorkbench/browser Blob API；
  不新增 npm/Maven 依赖。

## Non-Goals

- out_of_scope:
  - publish、apply、refresh、rollback、rebase、release package、Git、JAR binding、Agent 或 VS Code。
  - 修改 authoring REST route/DTO/error/state/store/auth 或 candidate execution semantics。
  - Engine/model、launcher、Maven、数据库、Bundle live source/catalog 改动。
  - Monaco/CodeMirror、语法高亮、自动完成、formatter、merge、任意文件系统或 Web IDE。
  - 重新设计 Console 主题、导航、Namespace 信息架构或现有 live Query 页面。
  - CSV 服务端持久化、历史下载、共享链接或后台 export job。
- do_not_touch:
  - `foggy-runtime-api/src/main`、`foggy-dataset-model-*` production、`foggy-mcp-launcher`。
  - `package.json`、lockfile、Maven POM、Runtime config 和 public API 文档。
  - 已签收 workitem/signoff 的历史正文；只在本 workitem 回写新结果。
- non_blocking_or_waivable_items:
  - provider/status 等 execution 字段若 Runtime 未返回可以不展示具体值，但 durationMs 存在时必须
    正确展示。
  - CSV 文件名的具体分隔符和 timestamp 形式可由实现决定，只需可识别且不泄密。
  - 纯视觉微调和非关键动效不属于本次目标。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 先 cleanup，再设计 publish | publish 会增加状态和高风险确认；应先建立稳定组件边界 | 不预埋或伪造 publish API/UI |
| 按职责拆分，不以机械 LOC 为唯一门槛 | 可维护性取决于状态所有权和依赖方向 | 不允许只把 939 行整体重命名或迁移 |
| 容器拥有 authoritative state，展示/局部交互下沉 | 避免子组件各自维护 candidate head | mutation 成功后仍只接受服务端 response 前移 revision |
| CSV 复用现有 helper | 已有 escaping 与公式注入防护 | 不复制一套 authoring-only serializer |
| execution 只展示返回事实 | 不同 provider 返回字段可能不同 | 缺失显示 `—`，不根据 elapsed browser time冒充 Runtime duration |
| UI 行为保持兼容 | 本事项是 refactor + 缺口补齐 | 既有 Playwright 请求体/次数/negative assertions 不放宽 |
| 不新增依赖 | 当前能力可由已有组件与浏览器 API 完成 | package/lockfile 改动触发 `NEEDS_REPLAN` |

## Acceptance Criteria

- [ ] AC-1: authoring 容器、workspace/source catalog、resource editor、candidate inspector 的职责明确；
  主组件不再拥有全部子域模板/样式/局部交互，API/state owner 只有一个，且没有形成新的同等单体。
- [ ] AC-2: eligible/read-only Bundle、workspace list/create/open/deep link、multiple active、metadata/state、
  Namespace 切换与 empty/error 行为保持，所有创建仍只由 `workspaceEligible===true` 开放。
- [ ] AC-3: TM/QM/FSScript read/new/edit/save/delete、严格 path、dirty leave、CAS conflict、local/server
  compare、显式再次保存和 discard terminal 行为保持；mutation request count/body/revision 不放宽。
- [ ] AC-4: exact diff、成功/失败 validation evidence、state/action matrix 和 candidate query
  validate/execute 保持；零 live `resources/save`、`models/refresh`、`query/*` fallback。
- [ ] AC-5: candidate query 结果在 execution 字段存在时准确显示至少 durationMs，并按返回事实展示
  provider/status；字段缺失时显示明确空值，不以浏览器耗时或推断替代。
- [ ] AC-6: 非空 candidate rows 可导出 CSV，内容复用既有安全 serializer，文件名可识别当前 model/
  workspace；空 rows 不生成下载，且导出不包含凭据、内部路径或隐藏数据。
- [ ] AC-7: desktop/mobile 保持无横向溢出、键盘/confirm/disabled/busy/error 可感知；focused
  Playwright 覆盖拆分后的完整 authoring 流、duration 和 download filename/content。
- [ ] AC-8: typecheck、全部 unit、production build、focused 与 affected full Playwright、tracked 和
  untracked whitespace checks 实际通过；changed paths 只限 Console frontend 与本 workitem 文档，
  无依赖、Runtime API、Engine、launcher、数据库或已签收历史记录改动。

## Contract / Data / Security Constraints

- API or event contract: 继续只使用既有 `/bundles` 和 `/authoring/workspaces/**`；请求 method、path、
  header、body、exact revision 和 RuntimeEnvelope mapping 不变。
- data and migration: 无数据库、server workspace、session/localStorage schema 或模型资源迁移；CSV
  仅由当前已展示 rows 在浏览器即时生成。
- compatibility and rollback: 纯前端 additive/refactor，可回退静态构建；旧 hash route、现有
  authoring workspace 数据和 live Console 工作台不变。
- permissions and secrets: Runtime token、业务 Authorization、数据源 secret、内部 path 和 hidden
  response fields 不进入 CSV、URL、localStorage、日志、截图或验收记录。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1 | must-pass | major | source review + component/unit boundaries | accepted cleanup finding | state owner、props/events/composable 和职责说明 |
| AC-2–AC-4 | must-pass | critical | existing + adjusted unit/desktop/mobile Playwright | accepted authoring flow | 原请求 body/count/revision/negative assertions 保持 |
| AC-5 | must-pass | medium | unit + Playwright | mock execution fixture | duration/provider/status 事实映射与缺失空值 |
| AC-6 | must-pass | major | existing CSV unit + authoring Playwright download | queryRowsToCsv tests | filename、CSV content、公式注入 guard reuse、空 rows |
| AC-7 | must-pass | major | focused desktop/mobile + screenshots/manual | accepted viewport evidence | full authoring flow、download、无溢出与可达性 |
| AC-8 | must-pass | major | typecheck + all unit + build + focused/full E2E + diff audit | current baseline | 精确命令、exit code、数量、changed/untracked paths |
| publish/Runtime/Engine | non-goal | deferred | none | accepted backend evidence | source review 确认零改动 |

## Validation Budget and Evidence Sufficiency

- assurance_level: standard
- lightweight_validation: `npm run typecheck`、`npm run test:unit`、`git diff --check`、全部 untracked
  no-index whitespace check 和 source boundary review；每轮预计 1–5 分钟。
- medium_validation: `npm run build`、`npx playwright test --grep "authoring workspace"` 和
  `npx playwright test`；每轮预计 5–15 分钟。
- expensive_validation: none by default。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none；若需要 Runtime API/Engine/依赖/launcher 改动，设置
  `NEEDS_REPLAN`，不得用扩大验证掩盖越界。
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: none within approved scope
- maximum_expensive_attempts: 0
- reusable_evidence: 已验收 workspace API、candidate query、live isolation 和 Console authoring 核心
  证据在对应请求/状态/测试断言未改变时可复用；组件拆分触及的 UI 与测试必须重跑。
- stop_when_evidence_is_sufficient: AC-1 至 AC-8 均有当前源码证据，type/unit/build/focused/full E2E
  通过，CSV/duration 和职责边界经 source/浏览器检查，且零越界 changed path 后停止。
- validation_not_required: Maven reactor、Runtime/Engine tests、launcher、数据库矩阵、authority/replay/
  rehearsal/source-seal、tag、release、publish、Git、JAR packaging。

## Waiver Policy

- waivable_items: 仅 execution 可选字段的具体排版、CSV 文件名分隔符、非关键视觉微调。
- authorized_role: product owner / delivery owner
- non_waivable_guards: single authoritative revision state、capability gate、CAS/dirty/conflict、零自动
  mutation retry、零 live fallback、CSV formula injection/secret guard、desktop/mobile 核心闭环、零
  backend/dependency change。
- required_risk_record: waiver 必须记录具体字段/viewport、用户影响、替代路径和 owner；不得 waiver
  数据泄露、revision 错绑、live mutation、核心回归或把缺失能力写成通过。

## Risks and Open Questions

- known_risks:
  - 拆分时 props/events 过多可能把复杂度从单体转移为隐式耦合；应保持一个明确 state owner。
  - Blob URL 必须在下载触发后 revoke；测试需等待 download event，避免浏览器资源泄漏或假通过。
  - Runtime execution 是开放 map，不同 provider 字段不齐；UI 只能展示已知事实并保留结构兼容。
  - CJK screenshot 字体限制仍是环境风险，不为 cleanup 引入产品字体依赖。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目 `CLAUDE.md`、Console 产品章程、accepted authoring spec/signoff 和适用前端技能。
- 开始实现时将状态改为 `ULTRA_EXECUTING`；保护实际工作树，不覆盖无关改动。
- 在 scope 内自主选择 component/composable 拆分；必须解释 state owner 和依赖方向，不能只做机械
  文件移动或换名。
- 如需修改 API/Engine/依赖、安全边界、publish 语义或已验收请求契约，设置 `NEEDS_REPLAN`。
- 运行契约内验证，记录精确命令、结果、数量、截图/download evidence 和未运行原因。
- 未经用户明确批准，不得运行 Maven、launcher、数据库矩阵、authority/replay/rehearsal/
  source-seal、tag、release 或 publish。
- 达到 evidence sufficiency 后停止；完成后填写 Implementation Result，并将状态改为
  `READY_FOR_SIGNOFF`，不得自行设置 `ACCEPTED` 或改写历史 signoff。

## Implementation Result

> 由 Ultra 执行会话填写。

- implementation_summary: 保留 `AuthoringWorkspace.vue` 为唯一 server-owned workspace/candidate
  revision 与 API 编排 owner，将 workspace/source catalog、resource editor、candidate inspector
  拆为纯 props/events 组件；candidate inspector 展示 Runtime 返回的 provider/status/durationMs，复用
  `queryRowsToCsv` 导出当前可见 rows，并在空结果时禁用下载与显示解释。
- changed_paths:
  - `addons/foggy-runtime-console/frontend/src/features/authoring/AuthoringWorkspace.vue`
  - `addons/foggy-runtime-console/frontend/src/features/authoring/{WorkspaceCatalog,ResourceEditor,CandidateInspector}.vue`
  - `addons/foggy-runtime-console/frontend/src/features/authoring/authoringWorkspace.ts`
  - `addons/foggy-runtime-console/frontend/tests/{unit/authoringWorkspace.test.ts,e2e/runtime-console.spec.ts}`
  - `docs/9.5.3/{README.md,workitems/REF-runtime-console-authoring-workspace-cleanup.md}`
- tests_and_results:
  - `npm run typecheck`: passed。
  - `npm run test:unit`: 10 files / 32 tests passed。
  - `npx playwright test --grep "authoring workspace"`: desktop/mobile 2/2 passed。
  - `npx playwright test`: desktop/mobile affected suite 10/10 passed。
  - `npm run build`: passed，2483 modules transformed。
  - `git diff --check` 与每个 untracked 文件 `git diff --no-index --check`: passed。
- manual_or_experience_evidence: focused Playwright 在 desktop/mobile 完整经过 capability gate、创建/
  打开、resource CRUD、dirty guard、CAS conflict、本地/服务端比较、失败/成功 validation、exact diff、
  candidate execute、execution facts、CSV download 和 discard；实际下载文件名为
  `candidate-OrderQuery-ws-default-001.csv`，内容验证 union columns、当前 rows 与公式注入前置单引号。
- deviations: none
- residual_risks: `AuthoringWorkspace.vue` 仍集中异步 API 编排，这是保持 authoritative revision owner 的
  有意边界；后续 publish 若使编排继续增长，应另行提取单一 composable，而不是在子组件复制状态。
- reused_evidence: 已签收 Console authoring workspace 的 Runtime 契约、live isolation 和既有状态机证据；
  本轮对所有受影响 Console unit/E2E 重新执行，未仅依赖历史记录。
- omitted_validation_and_reason: 按 standard assurance 与明示禁止项，未运行 Maven、Runtime/Engine、
  launcher、数据库矩阵、authority/replay/rehearsal/source-seal、tag、release 或 publish。
- readiness: READY_FOR_SIGNOFF

## References

- requirement / issue: 用户批准按“Console cleanup → Runtime publish/recovery API → Console publish
  接入”顺序推进；本事项落实第 1 项。
- architecture / glossary:
  - `CLAUDE.md`
  - `docs/design/runtime-console-product-charter.md`
  - `docs/9.5.3/runtime-model-authoring-design.md`
- related work items:
  - `docs/9.5.3/workitems/FEATURE-runtime-console-authoring-workspace.md`
  - `docs/9.5.3/acceptance/FEATURE-runtime-console-authoring-workspace-signoff.md`
  - `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-api.md`

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: independent-signoff-reviewer
- signed_off_at: 2026-08-01
- acceptance_record: `docs/9.5.3/acceptance/REF-runtime-console-authoring-workspace-cleanup-signoff.md`
- blocking_items: none
- follow_up_required: no
