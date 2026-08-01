---
acceptance_scope: feature
version: 9.5.3
target: FEATURE-runtime-console-authoring-workspace
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-08-01
reviewed_by: N/A
blocking_items: []
follow_up_required: yes
evidence_count: 12
assurance_level: standard
---

# Feature Delivery Signoff: Runtime Console authoring workspace 草稿闭环

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对当前 main 工作树中尚未提交的 Console authoring workspace 实现形成独立、可复核的
  正式签收结论；tracked 与 untracked 文件均进入审计范围。

## Background

- delivery_spec: `docs/9.5.3/workitems/FEATURE-runtime-console-authoring-workspace.md`
- target_outcome: 在不修改 Engine、Runtime API 或 live Bundle/catalog 的前提下，交付围绕一个
  `workspaceEligible` Bundle 的创建、资源编辑、CAS revision、diff、validate、candidate query 和
  discard 草稿闭环。
- signoff_scope: Console frontend、对应 unit/Playwright 和 9.5.3 文档；不含 publish/恢复。
- critical_outcomes:
  - capability 决定创建资格，JAR/configured 等来源不被推断为可写；
  - mutation、diff、validate、query 都绑定 exact candidate revision；
  - 冲突不覆盖服务端且不丢浏览器内 dirty 草稿；
  - candidate query 不回退 live query，save 不伪装成 publish；
  - Runtime API/Engine/launcher/依赖与 live 行为零改动。
- non_blocking_or_waivable_items: 语法高亮、行号、快捷键、逐行 diff、复杂文件树和高级编辑体验。

## Acceptance Basis

- approved delivery spec: canonical spec 为 `READY_FOR_SIGNOFF`、`assurance_level=standard`、
  `open_questions=[]`；AC-1 至 AC-12 均声明 must-pass。
- changed paths / diff:
  - 实际 `git status --short` 包含 6 个 tracked 修改和 5 个 untracked 文件；全部逐项读取。
  - 变更只位于 `addons/foggy-runtime-console/frontend` 和 `docs/9.5.3`；没有 Engine、Runtime API
    production、launcher、Maven、数据库或依赖文件改动。
  - `git diff --check` 与每个 untracked 文件的 `git diff --no-index --check /dev/null <file>` 通过。
- test records:
  - reviewer 独立重跑 `npm run typecheck`：exit 0。
  - reviewer 独立重跑 `npm run test:unit`：10 files / 31 tests 通过。
  - reviewer 独立重跑 `npx playwright test --grep "authoring workspace"`：desktop/mobile 2/2 通过
    （9.1s）。
  - 复用同一候选上实现阶段 `npm run build`：exit 0，2474 modules；仅既有 PURE annotation 与
    vendor chunk warning。
  - 复用同一候选上实现阶段 `npx playwright test`：desktop/mobile affected lane 10/10 通过
    （49.9s）。签收开始后产品源码和测试选择未改变，复用前提成立。
- experience evidence:
  - desktop/mobile focused screenshots 已人工检查；关键布局、revision、状态、资源、query result
    与 terminal action 可辨识且无横向溢出。
  - 宿主缺少 CJK 字体导致中文截图使用缺字 fallback；DOM accessible name 与交互断言不受影响。
- migration / compatibility evidence: additive 前端入口，无数据库/服务端 workspace migration；旧
  Namespace、Bundle、live Query 与 lifecycle affected E2E 通过。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | 仅 server-declared eligible Bundle 可创建 | strict `=== true` gate；JAR mock read-only | source review + focused Playwright | pass |
| AC-2 | list/create/open/deep link 与 Namespace 一致 | `authoring` tab、`workspaceId` query、multiple workspace | focused Playwright request/URL assertions | pass |
| AC-3 | metadata/revision/state/diagnostics，零内部路径 | server DTO 映射与 revision facts | source review + screenshots | pass |
| AC-4 | TM/QM/FSScript read/new/edit/save/delete 与 dirty guard | strict path policy、显式 save/delete、route/beforeunload guard | unit + desktop/mobile Playwright | pass |
| AC-5 | CAS mutation；冲突零自动重试且保留草稿 | failed save 后 local/server 并列，第二次显式 save 才前移 | Playwright request count/body/revision assertions | pass |
| AC-6 | pinned diff，无 merge/rebase 声称 | exact revision + includeContent，stale response guard | source review + Playwright | pass |
| AC-7 | exact 成功/失败 validation evidence | invalid/current issue 后再次 validate 成功 | unit + Playwright negative/positive flow | pass |
| AC-8 | workspace candidate query、Auth client、零 live fallback | exact workspace route/body/identity/rows/warnings | client source + Playwright route/body/negative assertions | pass |
| AC-9 | 状态矩阵和稳定错误恢复 | state policy、path/safe-to-repair metadata、无 destructive auto repair | unit + client unit + Playwright | pass |
| AC-10 | discard 二次确认且零 live mutation | target/revision confirm、terminal state、negative live route assertions | Playwright | pass |
| AC-11 | desktop 完整闭环、mobile 基本闭环与可达性 | 两 viewport 完整 focused flow、dirty confirm、无溢出 | Playwright + screenshots/manual | pass |
| AC-12 | 要求的构建/测试/边界检查通过 | typecheck/unit/build/focused/full E2E/diff checks 全绿 | command records + changed path audit | pass |

## Implementation Quality

- scope and changed surface: 功能调用面只包含 `/authoring/workspaces` 和既有 `/bundles` inventory；
  source review 未发现 live `resources/save`、`models/refresh` 或 live `query/*` fallback。
- maintainability and duplication: types 与纯 policy 已拆分并有 unit tests；但
  `AuthoringWorkspace.vue` 当前为 939 行，将 API 编排、状态、模板和样式集中在一个组件，低于执行
  契约期望的有界组件化水平。当前行为和测试可判断，分类为非阻断维护 follow-up。
- error handling and edge cases: 覆盖 invalid path、dirty leave、revision conflict、invalid validation、
  stale/discarded action policy、empty/read-only source 和异步 revision guard；destructive action 不自动
  重试。
- contract, data and compatibility: RuntimeEnvelope 与同源 client 保持；DELETE params 为 additive client
  能力；dirty content 不进入 localStorage/URL/log；无数据迁移。
- terminology and documentation: workspace/candidate/live/publish 边界一致，README 与 canonical 状态已
  回写。Scope 曾要求复用 query 的执行耗时和 CSV；当前 authoring inspector 已复用 DSL、rows 和
  warnings，但未展示 duration 或 CSV 导出，分类为非阻断契约/体验 follow-up，未计作 AC-8 通过内容。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | major | capability unit/source + read-only JAR E2E | new | pass |
| AC-2 | core-blocker | major | create body、Namespace header、deep link E2E | new | pass |
| AC-3 | core-blocker | major | DTO mapping/source + screenshots | new | pass |
| AC-4 | core-blocker | major | path unit、resource CRUD、dirty guard E2E | new | pass |
| AC-5 | core-blocker | critical | conflict request count、local/server copy、CAS body | new | pass |
| AC-6 | core-blocker | major | exact diff request/result E2E | new | pass |
| AC-7 | core-blocker | major | invalid/current evidence + valid evidence E2E | new | pass |
| AC-8 | core-blocker | major | workspace query route/body/identity + zero live fallback | new + client reuse | pass |
| AC-9 | core-blocker | critical | state matrix/error metadata unit + negative E2E | new | pass |
| AC-10 | core-blocker | critical | discard confirm/terminal + zero live mutation | new | pass |
| AC-11 | core-blocker | major | desktop/mobile/dirty/visual evidence | new | pass |
| AC-12 | core-blocker | major | independent focused + reusable affected/build + diff audit | new + reused | pass |
| 939-line feature component | scoped-risk | medium | `wc -l` + source review | new | follow-up |
| candidate duration/CSV | process-gap | minor | scope/source comparison | new | follow-up |
| publish/recovery | out-of-scope | deferred | canonical non-goal/roadmap | reused | not evaluated |

## Evidence Sufficiency

- assurance_level: standard
- why_existing_evidence_is_sufficient_or_not: reviewer 独立重跑会直接改变签收决定的 typecheck、全部
  unit 和 focused desktop/mobile；build/affected E2E 与当前候选、测试选择和环境一致，可复用。
  Engine/Runtime API accepted evidence未因纯前端改动失效。
- new_validation_that_could_change_decision: none；AC-1 至 AC-12 的正负向结果均已覆盖。
- expensive_validation_omitted_and_reason: 未运行 Maven、launcher、数据库矩阵、authority/replay/
  rehearsal/source-seal、release 或 publish；changed surface 未触发，且契约明确排除。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: 纯 Console frontend/doc 变更，focused + affected lane 已覆盖；未修改公共 API、
  安全模型、迁移或装配。
- estimated_wall_clock_and_basis: not-estimated
- scope_and_prerequisites: none
- maximum_attempts: 1
- decision_impact: no additional decision value at standard assurance
- user_approval: not-requested
- execution_status: not-run

## Waivers

| Waived Item | Authority | Reason | Bounded Impact | Non-Waivable Guards | Follow-up |
|---|---|---|---|---|---|
| none | N/A | 无 must-pass 失败需要 waiver | N/A | 全部 core guards 已通过 | N/A |

## Failed Items

- none

## Risks / Follow-ups

- `scoped-risk`: 后续 Console cleanup 应把 939 行 authoring 组件拆为 workspace catalog、resource
  editor、candidate inspector/composable，保持现有行为与测试契约不变。
- `process-gap`: 补齐 candidate query execution duration 与 CSV export，或在后续 spec 中明确删除
  该非核心 Scope 描述；当前不得宣称这两项已交付。
- `scoped-risk`: 测试宿主缺少 CJK 字体，仅影响截图文字渲染可读性；不影响 DOM/交互证据。
- `out-of-scope`: publish、失败恢复、release package、Git、JAR binding 和 Agent 仍需独立 workitem。

## Final Decision

- decision: accepted
- rationale: AC-1 至 AC-12 的核心用户结果、revision/CAS 安全、candidate/live 隔离和 desktop/mobile
  兼容均有充分证据；独立最小半径重验证通过，没有产品正确性或不可豁免 guard 阻断。维护性与
  query 辅助输出缺口边界清楚，不改变草稿闭环正确性，保留为后续事项。
- blocking_items: none
- follow_up_owner_and_due: Console owner；在 publish/恢复 workitem 前安排有界 cleanup 与 query
  辅助输出收敛，具体日期未冻结。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record: `docs/9.5.3/acceptance/FEATURE-runtime-console-authoring-workspace-signoff.md`
- blocking_items: none
- follow_up_required: yes
