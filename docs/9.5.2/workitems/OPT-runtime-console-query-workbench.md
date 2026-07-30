---
doc_type: delivery-spec
delivery_type: optimization
version: 9.5.2-follow-up
ticket: runtime-console-query-workbench
status: READY_FOR_SIGNOFF
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: user
approved_at: 2026-07-30
open_questions: []
---

# Delivery Spec: Runtime Console 查询工作台增强

## Goal

- 在不改变 query DSL/API 的前提下，让用户能理解请求、先校验、执行、诊断、复用和导出结果。

## Scope

- JSON 实时合法性、格式化、示例恢复和 payload 结构摘要。
- 当前 Namespace/模型/模式的 command manifest。
- validate/execute 结果状态、total/rows/hasNext/pagination/execution/warnings 的结构化诊断。
- 当前页面会话的 Namespace-scoped 查询历史与 payload 恢复。
- 结果 CSV 导出，处理列并集、引号/换行和电子表格公式注入。
- context rail 加入当前空间历史快捷入口。

## Non-Goals

- 不实现自然语言转 DSL、服务端保存历史、查询取消或后台任务。
- 不修改 query endpoint/payload/permission。
- 不新增可视化图表、pivot builder 或新的语义查询能力。

## Acceptance Criteria

- [x] AC-1: manifest 同时展示 Namespace、模型、模式和 payload 状态。
- [x] AC-2: 非法 JSON 在发送前被阻止并显示可读错误；格式化不改变语义。
- [x] AC-3: payload 摘要展示 columns/slice/groupBy/orderBy/page 的数量或范围。
- [x] AC-4: validate 与 execute 请求保持独立，结果状态和提示不混淆。
- [x] AC-5: 结构化诊断展示 total、returned、hasNext、pagination、execution 和 warnings。
- [x] AC-6: history 记录 Namespace/model/mode/status/rows/duration/time，失败也可诊断。
- [x] AC-7: 点击当前 Namespace 历史可恢复模型、mode 和原 payload，但不自动执行。
- [x] AC-8: Namespace 切换清空旧结果，并只展示新空间历史；编辑 payload 继续保留。
- [x] AC-9: CSV 导出覆盖列并集、转义与公式注入防护；无结果时禁用。
- [x] AC-10: stale response 防护、desktop/mobile、typecheck、unit、build/full E2E 通过。
- [x] AC-11: changed paths 仅限 Console frontend/docs，无 API/engine 改动。

## Validation Budget

- assurance_level: standard
- required: unit + typecheck + build + focused/full desktop/mobile Playwright + diff check。
- prohibited: authority/replay/rehearsal/source-seal/database matrix/tag/release/publish。

## Ultra Execution Contract

- 保持黑白工业控制台，重点是命令台和执行记录的信息层级。
- 历史仅为本页面会话便利功能，不描述为服务端审计。
- 完成后填 Implementation Result 并设置 `READY_FOR_SIGNOFF`，不得自行签收。

## Implementation Result

- implementation_summary:
  - 查询页新增 Namespace/model/mode/payload command manifest、实时 JSON 状态、格式化/示例恢复和
    columns/slice/group/order/page 摘要。
  - validate 与 execute 继续调用原 endpoint，结果新增 total/returned/hasNext/duration strip，
    pagination/execution 保留在诊断区域。
  - 新增 Namespace-scoped 页面会话历史，成功/失败均记录；恢复只填回 model/mode/payload 并明确
    不自动执行，同时进入 context rail 快捷入口。
  - 新增当前返回页 CSV 导出，支持列并集、引号/换行转义、UTF-8 BOM 和公式注入防护。
- changed_paths:
  - `addons/foggy-runtime-console/frontend/src/features/query/queryWorkbench.ts`
  - `addons/foggy-runtime-console/frontend/src/pages/QueryPage.vue`
  - `addons/foggy-runtime-console/frontend/tests/e2e/runtime-console.spec.ts`
  - `addons/foggy-runtime-console/frontend/tests/unit/queryWorkbench.test.ts`
  - `docs/9.5.2/README.md`
  - `docs/9.5.2/workitems/OPT-runtime-console-query-workbench.md`
- tests_and_results:
  - `npm run typecheck`: passed。
  - `npm run test:unit`: 8 files / 23 tests passed。
  - `npm run build`: passed，2469 modules transformed。
  - focused Namespace-context Playwright: desktop 1/1、mobile 1/1 passed。
  - `FONTCONFIG_FILE=/tmp/foggy-runtime-console-fonts.conf npx playwright test`: desktop/mobile
    8/8 passed。
  - `git diff --check`: passed。
- manual_or_experience_evidence:
  - desktop/mobile 浏览器断言覆盖 execute/validate 分离、payload 摘要、非法 JSON 阻断、格式化、
    诊断 duration、CSV 下载、Namespace 内历史恢复及 A→B stale response。
  - stable CJK 截图：
    `test-results/...desktop-chromium/query-workbench-desktop.png` 与
    `test-results/...mobile-chromium/query-workbench-mobile.png`。
- deviations: none
- residual_risks:
  - CSV 只导出当前浏览器已经收到的结果页，不会绕过分页拉取全量数据。
  - 历史仅在当前页面组件生命周期内存在，不是 Runtime 审计或持久化查询记录。
- omitted_validation_and_reason:
  - 未运行 Maven、数据库矩阵和 release authority；changed surface 仅为 Console frontend/docs。
- readiness: READY_FOR_SIGNOFF
