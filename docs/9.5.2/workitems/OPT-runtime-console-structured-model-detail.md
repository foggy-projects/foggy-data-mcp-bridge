---
doc_type: delivery-spec
delivery_type: optimization
version: 9.5.2-follow-up
ticket: runtime-console-structured-model-detail
status: READY_FOR_SIGNOFF
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: user
approved_at: 2026-07-30
open_questions: []
---

# Delivery Spec: Runtime Console 结构化模型详情

## Goal

- 将模型详情从原始 JSON 阅读器提升为面向运维和分析开发者的结构化模型契约。
- 用户能快速识别模型来源、字段构成、度量/维度、物理映射和使用提示，同时仍可在高级区域查看
  Runtime 原始 JSON。

## Scope

- in_scope:
  - 解析现有 `/api/v1/models/{model}/describe` 的 `data` 或 JSON `content`。
  - 结构化展示模型摘要、字段分类/搜索、物理表、场景、错误与原始 JSON。
  - 保留 loading/error/empty、retry、Escape、焦点返回和移动端抽屉行为。
  - 对字段规范化函数增加单元测试，对 drawer 增加 desktop/mobile E2E。
- affected_modules:
  - `addons/foggy-runtime-console/frontend`
  - `docs/9.5.2`

## Non-Goals

- 不增加 Runtime API 字段，不修改 model engine。
- 不根据 `physicalTables` 推断 QM→TM typed 依赖。
- 不在本项实现 Bundle、生命周期、查询工作台或 TM 草稿增强。

## Confirmed Decisions

| Decision | Rationale | Constraint |
|---|---|---|
| 兼容 `data` 与 JSON `content` | Runtime response 同时保留两种载体 | `data` 优先 |
| 字段分类仅使用显式 metadata | 避免凭名称猜测语义 | measure/calculated 标记之外归为维度/属性 |
| 原始 JSON 收纳到高级区域 | 主流程应可扫描 | 不删除原始响应 |
| 继续使用右侧 drawer | 保持 catalog 上下文与焦点语义 | mobile 宽度不溢出 viewport |

## Acceptance Criteria

- [x] AC-1: Drawer 首屏展示模型身份、来源、Namespace、字段/度量数量和主时间字段。
- [x] AC-2: `models` 信息中的 name/type/factTable/purpose/scenarios 被结构化展示。
- [x] AC-3: `fields` 支持搜索以及全部/维度/度量/计算字段过滤，并展示类型、聚合、来源列和说明。
- [x] AC-4: `physicalTables` 显式展示 table/role；缺失时有真实 empty state。
- [x] AC-5: QM→TM 依赖区域继续明确“API 未提供 typed 依赖”，不得从物理表推测。
- [x] AC-6: 原始 Runtime JSON 位于默认收起的高级区域，可用于诊断。
- [x] AC-7: `data`、JSON `content`、无效 content 和空响应均可安全规范化。
- [x] AC-8: loading/error/empty/retry、Escape、焦点返回和模型/Namespace 变化竞态行为保持正确。
- [x] AC-9: desktop/mobile E2E、typecheck、unit、build 和全量 Console E2E 通过。
- [x] AC-10: changed paths 仅限 Console frontend/docs，无后端契约变化。

## Validation Budget and Evidence Sufficiency

- assurance_level: standard
- required_validation:
  - normalization unit tests
  - `npm run typecheck`
  - `npm run test:unit`
  - `npm run build`
  - focused desktop/mobile Playwright
  - full Console Playwright
  - `git diff --check`
- prohibited: authority/replay/rehearsal/source-seal/database matrix/tag/release/publish。
- stop_when_evidence_is_sufficient: AC-1～AC-10 均有当前源码与浏览器证据。

## Ultra Execution Contract

- 在既有黑白线框工业控制台视觉体系内完成，不另起一套主题。
- 如发现必须新增 typed dependency API，先设置 `NEEDS_REPLAN`，不得从物理表模拟。
- 完成后填写 Implementation Result，状态改为 `READY_FOR_SIGNOFF`，不得自行设置 `ACCEPTED`。

## Implementation Result

- implementation_summary:
  - 新增纯前端 describe normalization，将 `data` 或 JSON `content` 规范化为模型信息、显式字段类型、
    物理表、示例、诊断和原始响应。
  - 详情 drawer 改为黑白工业档案式结构：模型 manifest、四项摘要、来源/用途契约、可搜索分类字段
    目录、物理映射、typed dependency 说明和高级原文。
  - 保留并重验证 loading/error/empty/retry、Escape、焦点返回、Namespace 变化 request version
    隔离与移动端 viewport 边界。
- changed_paths:
  - `addons/foggy-runtime-console/frontend/src/features/models/ModelDetailDrawer.vue`
  - `addons/foggy-runtime-console/frontend/src/features/models/modelDetail.ts`
  - `addons/foggy-runtime-console/frontend/tests/e2e/runtime-console.spec.ts`
  - `addons/foggy-runtime-console/frontend/tests/unit/modelDetail.test.ts`
  - `docs/9.5.2/README.md`
  - `docs/9.5.2/workitems/OPT-runtime-console-structured-model-detail.md`
- tests_and_results:
  - `npm run typecheck`: passed。
  - `npm run test:unit`: 5 files / 14 tests passed。
  - `npm run build`: passed，2462 modules transformed。
  - focused Namespace workspace Playwright: desktop 1/1、mobile 1/1 passed。
  - `FONTCONFIG_FILE=/tmp/foggy-runtime-console-fonts.conf npx playwright test`: desktop/mobile
    8/8 passed。
  - `git diff --check`: passed。
- manual_or_experience_evidence:
  - desktop/mobile 浏览器断言覆盖模型契约、场景、字段总数、物理表 role、字段类型过滤、字段搜索、
    高级原文、Escape 与焦点返回；browser console/page error 为零。
  - CJK 截图：
    `test-results/...desktop-chromium/structured-model-detail-desktop.png` 与
    `test-results/...mobile-chromium/structured-model-detail-mobile.png`。
- deviations: none
- residual_risks:
  - Runtime 未来若新增未知 metadata 形态，结构化层会忽略未知字段，但高级原文仍完整保留用于诊断。
- omitted_validation_and_reason:
  - 未运行 Maven、数据库矩阵和 release authority；changed surface 仅为 Console frontend/docs，
    无 API 或 engine 改动。
- readiness: READY_FOR_SIGNOFF

## References

- related work item: `OPT-runtime-console-namespace-workspace.md`
- Runtime DTO: `foggy-runtime-api/.../ModelDescribeResponse.java`
