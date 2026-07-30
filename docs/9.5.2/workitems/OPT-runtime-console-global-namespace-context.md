---
doc_type: delivery-spec
delivery_type: optimization
version: 9.5.2-follow-up
ticket: runtime-console-global-namespace-context
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: user
approved_at: 2026-07-30
open_questions: []
---

# Delivery Spec: Runtime Console 全局 Namespace 上下文一致性

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 固定 Console 在所有 Namespace-sensitive 工作台中的切换、重载和竞态边界。
- canonical_path: docs/9.5.2/workitems/OPT-runtime-console-global-namespace-context.md

## Goal

- version_goal: 在 Namespace workspace 之上建立 Console 全局、可观察且无 stale response 的请求上下文。
- target_outcome: 用户从顶部切换数据与模型空间后，查询、Tables、Compose 与 FSScript 立即使用并
  展示新空间的数据；旧空间的迟到响应不得覆盖新空间状态。
- critical_outcomes:
  - session、顶部选择器、路由和请求 Header 继续使用同一 Namespace。
  - Namespace-sensitive 页面在上下文变化后重新读取候选、表、模型或诊断状态。
  - 空 Namespace 不发送 `X-NS`，刷新后仍可恢复。
  - 在途请求具备版本隔离或取消机制，旧响应不可覆盖新上下文。
- success_is_sufficient_when: desktop/mobile E2E 能在不离开工作台的情况下切换空间，并断言 UI 数据与
  后续请求都来自新 Namespace。

## Scope

- in_scope:
  - 为当前 Namespace 增加可观察 revision/change contract。
  - Query、Tables、Compose、FSScript 与相关 context rail 在切换后重载或清空 namespace-scoped 状态。
  - Namespace workspace 保持现有路由恢复和 stale-response 防护。
  - 增加空 Namespace、快速连续切换和同页切换 E2E。
- affected_modules:
  - `addons/foggy-runtime-console/frontend`
  - `docs/9.5.2`
- external_dependencies: 现有 Vue/session/API client；不新增状态框架。

## Non-Goals

- out_of_scope:
  - 不新增 Namespace CRUD 或后端 discovery API。
  - 不改变数据源全局 registry 语义。
  - 不实现第三阶段的模型详情、Bundle、生命周期、查询历史或 TM 草稿增强。
- do_not_touch: Runtime API、model engine、auth、RuntimeEnvelope、数据库和 launcher。
- non_blocking_or_waivable_items: 非 Namespace-sensitive 的静态编辑内容可保留，但执行结果必须清空。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 继续复用现有 session store | 已有单一 Namespace 真值 | 不引入 Pinia |
| 每个异步 loader 防止 stale commit | 仅 watch 重载不能阻止迟到响应 | 可使用 request version 或取消信号 |
| 切换时清空旧结果与诊断 | 旧结果容易被误认为新空间结果 | 用户输入脚本可保留 |
| 空 Namespace 是有效上下文 | Runtime 兼容要求 | storage 保留空值、Header 省略 |

## Acceptance Criteria

- [x] AC-1: 顶部切换后 session、路由（适用页面）和后续 `X-NS` 一致。
- [x] AC-2: Query 模型候选和结果在切换后重载/清空，不显示旧空间结果。
- [x] AC-3: Tables 的默认绑定、数据源/表列表和结果在切换后重载/清空。
- [x] AC-4: Compose 与 FSScript 在切换后清空旧执行结果/诊断，执行请求使用新 Namespace。
- [x] AC-5: 快速 A→B 切换时 A 的迟到响应不能覆盖 B 的 UI。
- [x] AC-6: 空 Namespace 在所有相关工作台保持有效且不发送 `X-NS`。
- [x] AC-7: context rail、顶部状态和页面可访问名称能感知当前空间。
- [x] AC-8: desktop/mobile Playwright、typecheck、unit、build 和全量 Console E2E 通过。
- [x] AC-9: 改动仅限 Console frontend/docs，无 Runtime API 或 engine 契约变化。

## Contract / Data / Security Constraints

- API or event contract: endpoint、payload、`X-NS` 与 auth 语义 unchanged。
- data and migration: none。
- compatibility and rollback: 保持既有 sessionStorage key 与 hash route。
- permissions and secrets: token/Authorization 不进入 URL、日志或截图。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1–AC-6 | must-pass | major | unit + Playwright | workspace E2E | header/UI/race assertions |
| AC-7 | must-pass | medium | Playwright desktop/mobile | current a11y selectors | accessible state assertions |
| AC-8 | must-pass | major | typecheck + unit + build + full E2E | existing suite | exact command results |
| AC-9 | must-pass | major | git diff audit | prior boundary audit | changed paths |

## Validation Budget and Evidence Sufficiency

- assurance_level: standard
- lightweight_validation: unit/typecheck/static request mapping，1–5 分钟。
- medium_validation: build + focused/full Playwright，5–20 分钟。
- expensive_validation: none unless addon Java or launcher changes unexpectedly。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: unexpected Runtime API/launcher change only
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: unexpected non-frontend path
- maximum_expensive_attempts: 0
- reusable_evidence: 登录、非 Namespace 页面和 workspace card/drawer 证据可复用。
- stop_when_evidence_is_sufficient: AC-1～AC-9 有当前源码绿色证据，竞态和空值 E2E 通过。
- validation_not_required: Maven、数据库矩阵、authority/replay/tag/publish。

## Waiver Policy

- waivable_items: 微动效和非关键提示文案。
- authorized_role: product owner / delivery owner
- non_waivable_guards: 请求 Namespace 一致性、stale response 防护、空 Namespace、secret 边界。
- required_risk_record: 记录受影响页面、可检测性与后续 owner。

## Risks and Open Questions

- known_risks:
  - watcher 之间可能产生重复请求或路由循环，必须以 E2E 验证。
  - 用户正在编辑的 DSL/script 不应因切换空间丢失，只有结果与候选需要重置。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目 `CLAUDE.md` 和前端测试规范。
- 在 scope 内自主选择 composable、watcher 或 request version 结构。
- 如需后端 endpoint、状态框架或破坏 session key，设置 `NEEDS_REPLAN`。
- 先建立竞态/空 Namespace 回归断言，再实现并运行验证。
- 完成后填写 Implementation Result，状态改为 `READY_FOR_SIGNOFF`，不得自行设置 `ACCEPTED`。

## Implementation Result

- implementation_summary:
  - session store 增加单调递增的 Namespace revision，并在发布响应式变更前先持久化 storage。
  - 新增轻量 scope snapshot helper；Query、Tables、Compose、FSScript 仅提交仍属于当前
    Namespace revision 的异步响应。
  - Query 切换后重载 QM 候选并清空旧结果；Tables 重新解析 Namespace 默认数据源、重载表清单并
    清空 inspect/SQL 结果；Compose/FSScript 保留编辑输入但清空执行结果。
  - context rail、页面说明和 Query 可访问状态持续展示当前空间；空 Namespace 保持空值且省略
    `X-NS`。
- changed_paths:
  - `addons/foggy-runtime-console/frontend/src/components/ConsoleShell.vue`
  - `addons/foggy-runtime-console/frontend/src/composables/useNamespaceScope.ts`
  - `addons/foggy-runtime-console/frontend/src/pages/ComposePage.vue`
  - `addons/foggy-runtime-console/frontend/src/pages/FsscriptPage.vue`
  - `addons/foggy-runtime-console/frontend/src/pages/QueryPage.vue`
  - `addons/foggy-runtime-console/frontend/src/pages/TablesPage.vue`
  - `addons/foggy-runtime-console/frontend/src/stores/session.ts`
  - `addons/foggy-runtime-console/frontend/tests/e2e/runtime-console.spec.ts`
  - `addons/foggy-runtime-console/frontend/tests/unit/session.test.ts`
  - `docs/9.5.2/README.md`
  - `docs/9.5.2/workitems/OPT-runtime-console-global-namespace-context.md`
- tests_and_results:
  - `npm run typecheck`: passed。
  - `npm run test:unit`: 4 files / 11 tests passed。
  - `npm run build`: passed，2461 modules transformed。
  - `npm run test:e2e -- --grep "namespace context reloads" --project=desktop-chromium`: 1/1 passed。
  - `npm run test:e2e -- --grep "namespace context reloads" --project=mobile-chromium`: 1/1 passed。
  - `FONTCONFIG_FILE=/tmp/foggy-runtime-console-fonts.conf npx playwright test`: desktop/mobile
    8/8 passed。
  - `git diff --check`: passed。
- manual_or_experience_evidence:
  - Playwright 在 desktop/mobile 上同页切换 default、finance 与空 Namespace，断言模型/表候选、
    旧结果清理、脚本保留、Header/body scope、reload 恢复和 browser console/page error 为零。
  - 全量 E2E 使用临时 CJK fontconfig；Namespace 工作区截图中的中文可读，字体文件与临时配置
    未进入产品或 Git。
- deviations: none
- residual_risks:
  - 前端通过 revision 忽略旧响应但不主动取消网络请求；旧请求仍可能在 Runtime 侧完成只读工作，
    不会覆盖新空间 UI。
- reused_evidence:
  - 复用阶段一已验证的登录、workspace route/card/drawer、CJK 字体环境与浏览器零错误基线；
    本次全量 E2E 对受影响场景重新执行。
- omitted_validation_and_reason:
  - 未运行 Maven、数据库矩阵、authority/replay/rehearsal/tag/release/publish；changed surface 仅为
    Console frontend/docs，且 delivery spec 明确禁止扩大验证半径。
- readiness: READY_FOR_SIGNOFF

## References

- requirement / issue: Namespace workspace 签收后的功能正确性优先项。
- architecture / glossary: docs/architecture/runtime-and-model-lifecycle.md
- related work items: docs/9.5.2/workitems/OPT-runtime-console-namespace-workspace.md

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-07-31
- acceptance_record: docs/9.5.2/acceptance/OPT-runtime-console-global-namespace-context-signoff.md
- blocking_items: none
- follow_up_required: yes
