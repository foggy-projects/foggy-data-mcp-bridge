---
doc_type: delivery-spec
delivery_type: optimization
version: 9.5.2-follow-up
ticket: runtime-console-tables-sql-tm-draft
status: READY_FOR_SIGNOFF
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: user
approved_at: 2026-07-30
open_questions: []
---

# Delivery Spec: Runtime Console Tables / SQL 联动与 TM 草稿

## Goal

- 将表发现、结构检查、只读 SQL 与 TM 起草串成一条可控探索链。
- 草稿只基于明确列 metadata 做机械映射，不推断业务语义、现有 TM 或 QM 依赖。

## Scope

- inspect 后可生成 `SELECT * FROM schema.table` 骨架并进入只读 SQL 编辑器。
- 仅对 Runtime 返回的安全标识符生成 SQL；仍由服务端执行只读治理。
- 基于 table/schema/columns/primaryKey 生成可复制、可下载的 TM 草稿。
- JDBC type 到 Foggy property type 的保守机械映射。
- TM 草稿明确标记未校验、未保存、未注册、未刷新；不生成 dimensions/measures。
- Namespace 切换清空 inspect/SQL/draft，保留用户手写 SQL/schema/pattern。

## Non-Goals

- 不自动写入 Bundle、不调用 resources/save、不注册/刷新模型。
- 不推断 caption、业务含义、维度、度量、关联或 QM→TM 依赖。
- 不增加 SQL 方言生成器、DDL/DML 或数据库契约。

## Acceptance Criteria

- [x] AC-1: inspect 成功后保存当前 datasource/schema/table/columns/PK 的结构化上下文。
- [x] AC-2: 安全 schema/table 可生成只读 SELECT 骨架；异常标识符拒绝生成。
- [x] AC-3: 生成 SQL 后可直接使用现有 `/sql/query` 执行，dataSource 与 Namespace 一致。
- [x] AC-4: TM draft 的 model name/tableName/idColumn/properties 来自明确 inspect metadata。
- [x] AC-5: JDBC type 映射有单元测试；未知类型保守映射为 STRING。
- [x] AC-6: draft 不生成 dimensions、measures 业务内容，仅输出空数组并显示人工建模提示。
- [x] AC-7: draft 支持复制选择与 `.tm` 下载；下载不触发 Runtime 写操作。
- [x] AC-8: 页面明确 draft 未校验/未保存/未注册/未刷新。
- [x] AC-9: Namespace 切换关闭 draft 并清空旧 inspect/SQL 结果，用户手写 SQL 保留。
- [x] AC-10: desktop/mobile、typecheck、unit、build、full E2E 和 diff check 通过。
- [x] AC-11: changed paths 仅限 Console frontend/docs，无 API/engine 改动。

## Validation Budget

- assurance_level: standard
- required: unit + typecheck + build + focused/full desktop/mobile Playwright + diff check。
- prohibited: authority/replay/rehearsal/source-seal/database matrix/tag/release/publish。

## Ultra Execution Contract

- 草稿必须以“未发布的机械起点”呈现，不得包装为一键建模。
- 如需自动保存/验证/刷新，需要新的独立交付契约，不在本项扩大。
- 完成后填 Implementation Result 并设置 `READY_FOR_SIGNOFF`，不得自行签收。

## Implementation Result

- implementation_summary:
  - Tables inspect 结果保存 datasource/schema/table/columns/primary key 的结构化上下文，并可从
    当前安全 schema/table 生成只读 `SELECT * FROM ...` 骨架进入现有 SQL 编辑器。
  - 新增保守 JDBC type 映射和 TM 机械草稿生成器；model/table/id/property 仅来自明确 metadata，
    dimensions/measures 保持空数组。
  - 新增黑白线框右侧草稿抽屉，展示未校验/未保存/未注册/未刷新边界，内容可选择复制并可下载
    `.tm`，不调用 Runtime 写端点。
  - Namespace 切换关闭草稿、清空旧 inspect/SQL 结果并隔离迟到响应，同时保留 SQL/schema/pattern
    编辑内容。
- changed_paths:
  - `addons/foggy-runtime-console/frontend/src/features/tables/tableModelDraft.ts`
  - `addons/foggy-runtime-console/frontend/src/pages/TablesPage.vue`
  - `addons/foggy-runtime-console/frontend/tests/e2e/runtime-console.spec.ts`
  - `addons/foggy-runtime-console/frontend/tests/unit/tableModelDraft.test.ts`
  - `docs/9.5.2/README.md`
  - `docs/9.5.2/workitems/OPT-runtime-console-tables-sql-tm-draft.md`
- tests_and_results:
  - `npm run typecheck`: passed。
  - `npm run test:unit`: 9 files / 26 tests passed。
  - `npm run build`: passed，2470 modules transformed。
  - focused Namespace-context Playwright: desktop 1/1、mobile 1/1 passed。
  - `FONTCONFIG_FILE=/tmp/foggy-runtime-console-fonts.conf npx playwright test`: desktop/mobile
    8/8 passed。
  - `git diff --check`: passed。
- manual_or_experience_evidence:
  - desktop/mobile 浏览器断言覆盖 inspect metadata、SELECT 生成与执行、Namespace/header/body 一致、
    TM 内容边界、`.tm` 文件名、响应式 drawer 与 Namespace reset。
  - stable CJK 截图：
    `test-results/...desktop-chromium/tables-tm-draft-desktop.png` 与
    `test-results/...mobile-chromium/tables-tm-draft-mobile.png`。
- deviations: none
- residual_risks:
  - JDBC type 映射刻意保守，复杂方言类型可能落为 STRING，草稿仍需人工复核。
  - 草稿不包含业务语义，也不代表 Runtime 已接受或加载该模型。
- omitted_validation_and_reason:
  - 未运行 Maven、数据库矩阵和 release authority；changed surface 仅为 Console frontend/docs。
- readiness: READY_FOR_SIGNOFF
