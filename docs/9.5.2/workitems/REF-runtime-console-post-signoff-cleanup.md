---
doc_type: delivery-spec
delivery_type: refactor
version: 9.5.2-follow-up
ticket: runtime-console-post-signoff-cleanup
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: user
approved_at: 2026-07-30
open_questions: []
---

# Delivery Spec: Runtime Console 签收后清理

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 清理 Namespace workspace 签收中记录的非阻断维护与证据治理事项。
- canonical_path: docs/9.5.2/workitems/REF-runtime-console-post-signoff-cleanup.md

## Goal

- version_goal: 让已签收的 Namespace workspace 只保留单一活跃实现，并提高后续交付证据准确性。
- target_outcome: 删除不可达的旧模型/Bundle 页面，记录从真实 Git diff 生成 changed paths 的规则，
  并在具备 CJK 字体的受控浏览器环境补充中文视觉证据。
- critical_outcomes:
  - 不再保留与新 feature catalog 重复的不可达页面实现。
  - 清理不改变路由、Runtime 请求或用户功能。
  - 后续 Implementation Result 使用真实提交边界，不手工漏列文件。
- success_is_sufficient_when: active import graph 唯一、构建与 E2E 仍通过、CJK 截图可读且文档规则明确。

## Scope

- in_scope:
  - 删除未被路由或组件引用的旧 `ModelsPage.vue`、`BundlesPage.vue`。
  - 在 9.5.2 交付文档中写明 changed paths 的 Git 取证规则。
  - 使用现有系统/宿主字体补充 CJK 浏览器截图；不向产品 bundle 内嵌字体。
- affected_modules:
  - `addons/foggy-runtime-console/frontend`
  - `docs/9.5.2`
- external_dependencies: none。

## Non-Goals

- out_of_scope: 不改变 Namespace、模型、Bundle 功能；不调整视觉设计；不新增依赖。
- do_not_touch: Runtime API、model engine、launcher、数据库与已签收历史结果。
- non_blocking_or_waivable_items: 截图文件可保持测试产物，不要求进入生产静态资源。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 删除不可达旧页面而非继续同步 | 活跃路由已经由 Namespace workspace 承接 | 旧 hash URL 由 router redirect 保持 |
| Git diff 是 changed paths 真值 | 手工清单已出现遗漏 | 不改写历史 Implementation Result |
| CJK 字体只用于证据宿主 | 字体缺失是环境限制 | 不增加前端体积或字体许可风险 |

## Acceptance Criteria

- [x] AC-1: 旧 Models/Bundles 页面删除，仓库无活跃引用，旧 `/models`、`/bundles` 路由仍兼容。
- [x] AC-2: typecheck、unit、build 与 Console E2E 通过，无用户行为回归。
- [x] AC-3: 交付文档明确用 `git diff --name-only <base>...HEAD` 生成 changed paths。
- [x] AC-4: CJK 字体环境下桌面/移动截图中文可读，浏览器 console error 为 0。
- [x] AC-5: 改动仅在 Console frontend 与 `docs/9.5.2`，无后端契约变化。

## Contract / Data / Security Constraints

- API or event contract: unchanged。
- data and migration: none。
- compatibility and rollback: 删除仅涉及不可达源文件；Git revert 可恢复。
- permissions and secrets: 截图、日志不得包含真实 token。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1–AC-3 | must-pass | medium | source review + typecheck + build | accepted workspace evidence | commands and path audit |
| AC-4 | must-pass | medium | Playwright desktop/mobile | existing selectors | readable screenshots + console log |
| AC-5 | must-pass | medium | git diff audit | previous boundary audit | exact changed paths |

## Validation Budget and Evidence Sufficiency

- assurance_level: standard
- lightweight_validation: source references、`git diff --check`、typecheck/unit，1–5 分钟。
- medium_validation: build + focused/full Console E2E，5–15 分钟。
- expensive_validation: none。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: none
- maximum_expensive_attempts: 0
- reusable_evidence: 已签收功能证据可复用，仅重跑受删除和截图环境影响的范围。
- stop_when_evidence_is_sufficient: AC-1～AC-5 有当前源码证据且测试绿色。
- validation_not_required: Maven、Runtime API、数据库、authority/replay/tag/publish。

## Waiver Policy

- waivable_items: none。
- authorized_role: product owner / delivery owner
- non_waivable_guards: 路由兼容、无功能回归、无 secret。
- required_risk_record: N/A。

## Risks and Open Questions

- known_risks: 系统字体定位可能因宿主差异失败；失败时记录环境证据，不修改产品掩盖。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目 `CLAUDE.md` 和前端测试规范。
- 在 scope 内自主完成删除、文档和证据环境配置。
- 如需改变功能、API 或视觉产品决策，设置 `NEEDS_REPLAN`。
- 运行风险相称验证并记录精确结果。
- 完成后填写 Implementation Result，状态改为 `READY_FOR_SIGNOFF`，不得自行设置 `ACCEPTED`。

## Implementation Result

- implementation_summary:
  - 删除不再被 router/import graph 引用的旧 `ModelsPage.vue`、`BundlesPage.vue`，旧 hash route
    继续由 router 重定向到 Namespace 子工作区。
  - 9.5.2 README 新增实际 Git diff 取证规则，避免 Implementation Result 手工漏列路径。
  - focused Playwright 使用临时本地 Noto Sans SC 字体副本重跑，桌面/移动截图中文可读，并新增
    console/page error 零错误断言。
- changed_paths:
  - `addons/foggy-runtime-console/frontend/src/pages/ModelsPage.vue`（删除）
  - `addons/foggy-runtime-console/frontend/src/pages/BundlesPage.vue`（删除）
  - `addons/foggy-runtime-console/frontend/tests/e2e/runtime-console.spec.ts`
  - `docs/9.5.2/README.md`
  - `docs/9.5.2/workitems/REF-runtime-console-post-signoff-cleanup.md`
- tests_and_results:
  - `npm run typecheck`：exit 0。
  - `npm run test:unit`：4 files / 10 tests passed。
  - `npm run build`：exit 0，2460 modules transformed；无旧 Models/Bundles page chunk。
  - `FONTCONFIG_FILE=/tmp/foggy-runtime-console-fonts.conf npx playwright test`：6/6 passed。
  - CJK focused 重跑：desktop/mobile 2/2 passed；新增 browser error 断言为 0。
- manual_or_experience_evidence:
  - `addons/foggy-runtime-console/frontend/test-results/runtime-console-namespace--3ae14--and-keyboard-focus-aligned-desktop-chromium/namespace-workspace-desktop.png`
  - `addons/foggy-runtime-console/frontend/test-results/runtime-console-namespace--3ae14--and-keyboard-focus-aligned-mobile-chromium/namespace-workspace-mobile.png`
- deviations: none
- residual_risks: CJK 字体来自临时证据环境，不进入产品 bundle；生产显示仍依赖客户端系统字体。
- reused_evidence: 已签收 Namespace workspace 的路由/API 证据继续有效；全量 Console E2E 已重跑。
- omitted_validation_and_reason: 未运行 Maven、Runtime API、数据库或 authority/replay；无相关改动。
- readiness: READY_FOR_SIGNOFF

## References

- requirement / issue: Namespace workspace 正式验收 follow-ups。
- architecture / glossary: docs/9.5.2/acceptance/OPT-runtime-console-namespace-workspace-signoff.md
- related work items: docs/9.5.2/workitems/OPT-runtime-console-namespace-workspace.md

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-07-31
- acceptance_record: docs/9.5.2/acceptance/REF-runtime-console-post-signoff-cleanup-signoff.md
- blocking_items: none
- follow_up_required: yes
