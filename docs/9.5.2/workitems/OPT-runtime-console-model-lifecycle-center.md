---
doc_type: delivery-spec
delivery_type: optimization
version: 9.5.2-follow-up
ticket: runtime-console-model-lifecycle-center
status: READY_FOR_SIGNOFF
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: user
approved_at: 2026-07-30
open_questions: []
---

# Delivery Spec: Runtime Console 模型生命周期操作中心

## Goal

- 将模型 validate、selected refresh、refresh all 与结果诊断收敛为一个可理解的生命周期操作中心。
- 清楚表达 candidate validate 与 atomic publish refresh 的边界，不把保存资源误报为发布。

## Scope

- 当前 Namespace、选中模型、操作可用性和风险提示。
- 路径候选校验、刷新已选、刷新全部及相应确认。
- catalog state、generation before/after、duration、成功/失败数量、warning/error 明细。
- 当前页面会话内最近操作历史；Namespace 切换时清空。
- desktop/mobile E2E、normalization unit tests。

## Non-Goals

- 不新增 Runtime endpoint、任务队列、持久化审计历史或回滚 API。
- 不把 Bundle resource save 与 model publish 合并为一个请求。
- 不自动修复模型、自动重试或绕过 refresh 原子发布语义。

## Acceptance Criteria

- [x] AC-1: 中心明确展示当前 Namespace、可见 QM、已选 QM 和最近状态。
- [x] AC-2: validate 说明其隔离候选语义，要求路径且不会触发 catalog publish。
- [x] AC-3: selected refresh 无选择时不可执行；请求只包含已选模型。
- [x] AC-4: refresh all 有更强风险说明与明确确认；请求使用空 models 数组。
- [x] AC-5: 结果展示 catalogState、generation before/after、duration、成功/失败计数。
- [x] AC-6: warnings/errors/failures 被结构化为诊断表，不因无明细伪造错误。
- [x] AC-7: 页面会话历史区分 validate/selected/all、成功/失败及时间；Namespace 切换清空。
- [x] AC-8: RuntimeRequestError 的 code/phase/message/next action 在中心可诊断。
- [x] AC-9: reload 仅在 refresh 成功后触发；validate 不重载 catalog。
- [x] AC-10: desktop/mobile、typecheck、unit、build、full E2E 和 diff check 通过。
- [x] AC-11: changed paths 仅限 Console frontend/docs，无 API/engine 改动。

## Validation Budget

- assurance_level: standard
- required: unit + typecheck + build + focused/full desktop/mobile Playwright + diff check。
- prohibited: authority/replay/rehearsal/source-seal/database matrix/tag/release/publish。

## Ultra Execution Contract

- 视觉继续使用黑白线框控制台，以状态机和 generation 轨迹作为核心识别元素。
- 如需要真实持久化历史、回滚或 job API，必须 `NEEDS_REPLAN`。
- 完成后填 Implementation Result 并设置 `READY_FOR_SIGNOFF`，不得自行签收。

## Implementation Result

- implementation_summary:
  - 新增统一生命周期中心，集中展示 Namespace/可见/已选/最新状态，并提供候选路径校验、刷新已选、
    刷新全部三个明确操作。
  - 结果区结构化展示 catalogState、generation before/after、duration、成功/失败计数，以及
    warning/error/failure 明细；清洁结果不再伪造诊断行。
  - RuntimeRequestError 的 code、phase、message、suggestedNextAction 进入页面诊断；最近六次操作
    保存在当前页面会话并随 Namespace 切换清空。
  - 原 ModelCatalog 中分散的按钮与折叠维护工具已移除，卡片只负责选择/详情，职责边界更清楚。
- changed_paths:
  - `addons/foggy-runtime-console/frontend/src/features/models/ModelCatalog.vue`
  - `addons/foggy-runtime-console/frontend/src/features/models/ModelLifecycleCenter.vue`
  - `addons/foggy-runtime-console/frontend/src/features/models/modelLifecycle.ts`
  - `addons/foggy-runtime-console/frontend/tests/e2e/runtime-console.spec.ts`
  - `addons/foggy-runtime-console/frontend/tests/unit/modelLifecycle.test.ts`
  - `docs/9.5.2/README.md`
  - `docs/9.5.2/workitems/OPT-runtime-console-model-lifecycle-center.md`
- tests_and_results:
  - `npm run typecheck`: passed。
  - `npm run test:unit`: 7 files / 20 tests passed。
  - `npm run build`: passed，2467 modules transformed。
  - focused Namespace workspace Playwright: desktop 1/1、mobile 1/1 passed。
  - `FONTCONFIG_FILE=/tmp/foggy-runtime-console-fonts.conf npx playwright test`: desktop/mobile
    8/8 passed。
  - `git diff --check`: passed。
- manual_or_experience_evidence:
  - 浏览器断言覆盖 selected 禁用态、selected/all 请求范围与确认、candidate validate、PUBLISHED/
    CANDIDATE_VALID、generation、duration 和三条会话历史。
  - isolated CJK 组件截图：
    `test-results/...desktop-chromium/model-lifecycle-center-desktop.png` 与
    `test-results/...mobile-chromium/model-lifecycle-center-mobile.png`；移动端 generation 指标已
    调整为无裁切的纵向布局。
- deviations: none
- residual_risks:
  - 操作历史仅存在于当前页面内存，不是持久化审计记录；真实审计仍由 Runtime/宿主日志承担。
- omitted_validation_and_reason:
  - 未运行 Maven、数据库矩阵和 release authority；changed surface 仅为 Console frontend/docs。
- readiness: READY_FOR_SIGNOFF
