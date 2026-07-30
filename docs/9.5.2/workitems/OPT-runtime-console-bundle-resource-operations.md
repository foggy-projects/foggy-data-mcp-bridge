---
doc_type: delivery-spec
delivery_type: optimization
version: 9.5.2-follow-up
ticket: runtime-console-bundle-resource-operations
status: READY_FOR_SIGNOFF
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: user
approved_at: 2026-07-30
open_questions: []
---

# Delivery Spec: Runtime Console Bundle 资源操作产品化

## Goal

- 将 Bundle export/save 从原始 JSON 控制面改造成有边界、有预览、有冲突意识的资源工作台。
- 保留专家 JSON 能力，但默认用户无需理解底层请求结构。

## Scope

- export 向导：全部或指定相对路径、是否包含内容、请求摘要和结构化结果。
- save 向导：多文件 path/content/baseSha256、添加/移除、写入确认和结构化结果。
- 前端路径规则与 Runtime 对齐：仅相对路径、禁止 `..`、仅 `.tm`、`.qm` 和 model-list 文件。
- 原始 JSON 位于默认收起的专家区域；显式启用后才覆盖向导 payload。
- configured/non-writable Bundle 的保存入口禁用并解释原因。
- 单元测试与 desktop/mobile E2E。

## Non-Goals

- 不修改 resources API 或文件写入语义。
- 不把 `validate/refresh` flag 描述为真实执行；保存后生命周期动作留给后续生命周期中心。
- 不实现在线代码编辑器、diff/merge 或自动冲突修复。

## Acceptance Criteria

- [x] AC-1: 高级抽屉明确展示 Bundle、Namespace、根路径、可写性与可见 QM。
- [x] AC-2: export 可选择全部资源或按行指定路径，并控制 `includeContent`。
- [x] AC-3: save 支持一至多个文件、path/content/baseSha256，且有明确写入确认。
- [x] AC-4: 前端拒绝绝对路径、目录穿越和不支持文件类型；不发送无效请求。
- [x] AC-5: 非 Runtime-managed 或不可写 Bundle 的 save 入口不可用并说明原因。
- [x] AC-6: 默认 payload 来自结构化向导；专家 JSON 需显式启用，格式错误时不发送。
- [x] AC-7: export/save 结果展示 path/type/size/sha256/writable 与 warnings。
- [x] AC-8: 界面明确 save 不会自动完成模型 validate/refresh。
- [x] AC-9: Namespace 切换关闭 drawer，旧结果不残留；desktop/mobile 无溢出。
- [x] AC-10: typecheck、unit、build、focused/full Playwright 与 diff check 通过。
- [x] AC-11: changed paths 仅限 Console frontend/docs，无 API/engine 改动。

## Validation Budget

- assurance_level: standard
- required: unit + typecheck + build + desktop/mobile focused/full Playwright + `git diff --check`。
- prohibited: authority/replay/rehearsal/source-seal/database matrix/tag/release/publish。

## Ultra Execution Contract

- 保持当前黑白线框工业控制台，不引入新的主题或状态框架。
- 前端校验是提前反馈，Runtime 仍为最终路径与并发冲突权威。
- 完成后状态改为 `READY_FOR_SIGNOFF` 并填写 Implementation Result，不自行签收。

## Implementation Result

- implementation_summary:
  - Bundle 高级抽屉新增操作 manifest、全部/指定路径导出、includeContent 控制、多文件原子保存、
    base SHA 并发保护输入和结构化结果。
  - 新增与 Runtime 对齐的资源路径校验；绝对路径、`..` 和非 TM/QM/model-list 文件在浏览器内
    拒绝且不发请求。
  - 原始 JSON 默认只读并收起；用户显式启用专家覆盖后才可编辑并取代向导 payload。
  - save 固定发送 `validate=false`、`refresh=false`，界面明确引导到后续生命周期动作。
- changed_paths:
  - `addons/foggy-runtime-console/frontend/src/features/bundles/BundleCatalog.vue`
  - `addons/foggy-runtime-console/frontend/src/features/bundles/bundleResources.ts`
  - `addons/foggy-runtime-console/frontend/tests/e2e/runtime-console.spec.ts`
  - `addons/foggy-runtime-console/frontend/tests/unit/bundleResources.test.ts`
  - `docs/9.5.2/README.md`
  - `docs/9.5.2/workitems/OPT-runtime-console-bundle-resource-operations.md`
- tests_and_results:
  - `npm run typecheck`: passed。
  - `npm run test:unit`: 6 files / 17 tests passed。
  - `npm run build`: passed，2463 modules transformed。
  - focused Namespace workspace Playwright: desktop 1/1、mobile 1/1 passed。
  - `FONTCONFIG_FILE=/tmp/foggy-runtime-console-fonts.conf npx playwright test`: desktop/mobile
    8/8 passed。
  - `git diff --check`: passed。
- manual_or_experience_evidence:
  - desktop/mobile 真实浏览器断言覆盖只读/可编辑专家 payload、无效路径零请求、指定路径导出、
    includeContent、base SHA 保存、确认机制、warning 与 Header/body Namespace。
  - 稳定状态 CJK 截图：
    `test-results/...desktop-chromium/bundle-resource-operations-desktop.png` 与
    `test-results/...mobile-chromium/bundle-resource-operations-mobile.png`。
- deviations: none
- residual_risks:
  - 前端校验与 Runtime 当前文件类型规则同步；Runtime 若扩展新资源后缀，Console 需要同步放行。
  - 本增量不提供 diff/merge；SHA 冲突仍需重新导出并由用户合并。
- omitted_validation_and_reason:
  - 未运行 Maven、数据库矩阵和 release authority；changed surface 仅为 Console frontend/docs。
- readiness: READY_FOR_SIGNOFF

## References

- `RuntimeResourcesController`
- `ResourceExportRequest`
- `ResourceSaveRequest`
