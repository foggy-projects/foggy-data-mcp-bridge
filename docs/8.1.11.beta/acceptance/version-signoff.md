---
acceptance_scope: version
version: 8.1.11.beta
target: v8.1.11.beta 版本整体签收
status: signed-off
decision: accepted
signed_off_by: backend-module
signed_off_at: 2026-04-20
reviewed_by: N/A
blocking_items: []
follow_up_required: no
evidence_count: 69
doc_role: acceptance
doc_purpose: v8.1.11.beta 三个同版本交付项（fsscript IN/NOT IN + 计算字段 SQL 翻译 + slice $expr validation gap 修复）的正式版本级签收记录
---

# Version Acceptance

## Background

v8.1.11.beta 是 `foggy-data-mcp-bridge` 的一个功能增强版本，由三个同版本交付项构成，三者形成上下游依赖：

1. **P2-fsscript支持in和not-in算子** —— fsscript CUP parser 语法层新增 `term3 NOT IN term2` 产生式 + 运行时 `IN.execute` / `NOT_IN.execute`，统一等值契约走 `Equal.eq`
2. **P2-计算字段支持in和not-in算子** —— `foggy-dataset-model` 的 `SqlExpFactory` + `AllowedFunctions` 白名单放行 + 新 `SqlListExp` AST 节点 + `SqlFragment.isComparisonOperator` 纳入 IN/NOT IN 类型推断
3. **BUG-001 slice `$expr` validation gap** —— `QueryRequestValidationStep.validateSliceItem` 与 `validateCondChildren` 两处 mirror 补 `$expr` 短路守卫；连带恢复 3 条因该 gap 临时剔除的 slice 集成测试

版本目标：向 QM/DSL/slice 三条表达式路径同步开放 SQL 风格的 `v in (...)` / `v not in (...)` 成员测试能力，生成标准 SQL `IN` / `NOT IN`，保持等值语义与 fsscript `==` 契约一致，并解锁长期被 validation 误拦的 `$expr` 通路。

## Acceptance Basis

- **需求与 BUG 文档**：
  - `docs/8.1.11.beta/P2-fsscript支持in和not-in算子-需求.md`
  - `docs/8.1.11.beta/P2-计算字段支持in和not-in算子-需求.md`
  - `docs/8.1.11.beta/workitems/BUG-001-slice-expr-validation-gap.md`
- **测试覆盖审计**：`docs/8.1.11.beta/coverage/8.1.11.beta-coverage-audit.md`（结论 `ready-for-acceptance`）
- **项目交付模式**：root `CLAUDE.md` 定义的 `single-root-delivery`（非跨 repo 工程，聚合在 owning repo 的版本目录追踪）
- **测试强制契约**：root `CLAUDE.md` 对查询链路的要求 —— "必须包含真实 SQL 数据比对"，本版本全部 slice / calcField 集成用例遵守

## Module Summary

| 工作项 | 类型 | 交付状态 | 关联 owning module | 关键证据 |
|---|---|---|---|---|
| P2-fsscript支持in和not-in算子 | requirement | `ready-for-verification` → **accepted** | foggy-fsscript | `InNotInExpTest.java` 35 用例 |
| P2-计算字段支持in和not-in算子 | requirement | `ready-for-verification` → **accepted** | foggy-dataset-model | `SqlExpFactoryInOperatorTest.java` 22 + `InOperatorCalcFieldIntegrationTest.java` 12 |
| BUG-001-slice-expr-validation-gap | bug | `ready-for-verification` → **accepted** | foggy-dataset-model | `InOperatorCalcFieldIntegrationTest.java` 中 5 条 `sliceExpr*` 用例 |

三者构成完整能力闭环：fsscript 提供运行时语义基础 → 计算字段层翻译到 SQL → slice `$expr` validation 放行后让用户可以通过 DSL 正常使用。

## Checklist

### 版本目标对齐
- [x] fsscript 语法层支持 `v in (...)` / `v not in (...)`，grammar 产生式对称挂在 term3
- [x] 运行时等值语义与 fsscript `==` 一致（调用 `Equal.eq`），类型契约显式锁定
- [x] 计算字段 SQL 翻译生成标准 `(col IN ('a', 'b'))` / `(col NOT IN (...))`
- [x] QM `columnGroups.items[].formula` 与 DSL `calculatedFields` 共享同一条 `CalculatedFieldService.compileExpression` 编译链路
- [x] slice `$expr` 路径通过 `queryFacade.queryModelData` 端到端打通
- [x] SQL 三值逻辑（`NOT IN (v, NULL)` 恒 UNKNOWN）与原生 SQL 行为逐行一致
- [x] 空 IN / NOT IN 列表在编译期明确拒绝（`IllegalArgumentException`，含清晰引导信息）
- [x] 现有 `for (var x in list)` 循环与 `(item, index) in list` 元组迭代零回归

### 实现完成度
- [x] 所有 Fix Checklist 条目已勾选（三个 work item 的 Execution Checkin 段均已完成）
- [x] 代码改动文件清单已记录在对应 Execution Checkin
- [x] CUP 冲突数从 100 提升到 104（全部为 shift 解决，符合既有风格）
- [x] `AllowedFunctions.MEMBERSHIP_OPERATORS` 白名单 + `toSqlOperator` 映射 + `isMembershipOperator` API 齐备
- [x] `SqlListExp` 正确处理 `NullExp`（审计期间发现并修复的 bug：不会静默丢弃 null 元素）

### 测试通过
- [x] fsscript 模块全量：`Tests run: 298, Failures: 0, Errors: 0, Skipped: 0`
- [x] foggy-dataset-model 模块全量（SQLite profile）：`Tests run: 1014, Failures: 0, Errors: 0, Skipped: 0`
- [x] 本版本新增 69 个专项用例（fsscript 35 + SQL 翻译 22 + 集成 12）全部绿
- [x] 真实 SQL 数据比对：slice 与 calcField 集成用例均与原生 SQL `WHERE` / `CASE WHEN` 基线逐行或按计数对齐
- [x] 覆盖 AND/OR + IN 真值表 / 一元 NOT + IN / 算术 LHS 与 RHS / 多 IN 链式 / `==` 左结合 / `&&` 非短路锁定 / SQL NULL 3VL

### 文档完整度
- [x] 中英文语法手册 `FSScript-Syntax-Manual.{zh-CN.}md` 补 fsscript runtime + 计算字段 / QM formula 使用示例
- [x] 三个 work item 文档状态 `ready-for-verification`
- [x] 两份"文档作用"头 (`doc_type` / `intended_for` / `purpose`) 在 requirement doc 顶部完整
- [x] 覆盖审计记录 `docs/8.1.11.beta/coverage/8.1.11.beta-coverage-audit.md` 已产出

### 流程合规
- [x] `foggy-implementation-quality-gate`（轻量 self-check）：三份 work item 均已在 Execution Checkin 记录 `self-check-only` 结论
- [x] `foggy-test-coverage-audit`：已完成，结论 `ready-for-acceptance`
- [x] 非目标项（TM computed / MongoDB / Python / Odoo / LIKE + IN）明示 out-of-scope
- [x] `simplify` 代码评审：已在开发期间执行，must-fix（复用 `Equal.eq`）+ 高价值 nice-to-have 全部闭环

### 体验验证
- [x] N/A —— 三项均为纯后端引擎改造（parser + SQL 翻译 + validation 层），无 UI 交互面，设计上不需要手工验证或 Playwright 测试；已在三份 work item 的 "体验进度" 段明示

## Evidence

### 代码证据

- fsscript 改动：`foggy-fsscript/src/main/resources/datasetexp.cup` (+ `ExpParser.java` / `ExpSymbols.java` 自动生成) + `fun/IN.java` + 新增 `fun/NOT_IN.java` + `exp/FunTable.java` + `test/java_cup/JavacupHelper.java` (`-expect 104`)
- 计算字段翻译层改动：`foggy-dataset-model/.../engine/expression/AllowedFunctions.java` + 新增 `sql/SqlListExp.java` + `SqlExpFactory.java` + `SqlFragment.java` + `CalculatedFieldService.java`
- BUG-001 改动：`foggy-dataset-model/.../plugins/result_set_filter/QueryRequestValidationStep.java` 两处 mirror

### 测试证据

- **单元**：`foggy-fsscript/.../InNotInExpTest.java`（35）+ `foggy-dataset-model/.../expression/SqlExpFactoryInOperatorTest.java`（22）= 57 条
- **集成（真实 SQL 比对）**：`foggy-dataset-model/.../ecommerce/InOperatorCalcFieldIntegrationTest.java`（12）
- **回归基线**：两层模块各自 `mvn test` 全绿（298 + 1014）

### 文档证据

- `docs/8.1.11.beta/coverage/8.1.11.beta-coverage-audit.md`（覆盖审计，逐项 covered / out-of-scope 判断）
- 三份 work item 的 `## Execution Checkin` 段详列改动文件与自检结论
- `foggy-fsscript/docs/FSScript-Syntax-Manual.{zh-CN.}md` 的语法说明 + 计算字段使用示例

### 体验证据

- N/A —— 纯后端改造，本版本无 UI 交互面，已明示

## Risks / Open Items

### 非阻断项（follow-up，不影响本次 accepted 结论）

- **嵌套 `$or`/`$and` 内 `$expr` 子条件独立集成用例缺失**：`validateCondChildren` 与 `validateSliceItem` 为镜像代码，顶层 `$expr` 主路径已覆盖；嵌套场景未构造独立用例。建议后续遇到真实嵌套 `$or:[{$expr: ...}]` 用法时顺手补一条（覆盖审计里已显式列出）。
- **demo TM/QM 使用示例**：建议后续在 `foggy-dataset-demo/.../ecommerce/query/` 加一条带 IN formula 的展示 QM；非阻断。
- **跨 repo 对齐（Python / Odoo 引擎）**：本版本仅交付 Java；Python 引擎同款 calc field 支持需另行立项。

### 已明示范围外（out-of-scope）

- TM 文件 computed field（TM 无原生 computed 概念）
- MongoDB 引擎（`MongoCalculatedFieldProcessor` 独立路径）
- `LIKE + IN` 组合（`LIKE` 未进 `AllowedFunctions` 白名单是 pre-existing 限制）
- `v in (SELECT ...)` 子查询（明确非目标）

### 风险评估

无阻断签收的风险项。fsscript `&&` 非短路是 pre-existing 运行时契约，本版本仅做行为锁定测试（`andDoesNotShortCircuit_behaviorLockdown`），不改变既有语义。

## Final Decision

**accepted**

- 版本目标已达成：QM / DSL / slice 三条路径均可使用 SQL 风格 IN / NOT IN
- 测试证据矩阵完整：69 条专项用例 + 两层模块全量回归（1312 条）零失败
- 流程合规：coverage audit + execution checkin + simplify 代码审查 + must-fix 修复全部闭环
- 遗留项均为非阻断后续项，已明示路径

三个 work item 状态统一升级为 `closed` / `accepted`，版本 `8.1.11.beta` 可交付。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: backend-module
- signed_off_at: 2026-04-20
- acceptance_record: docs/8.1.11.beta/acceptance/version-signoff.md
- blocking_items: none
- follow_up_required: no
- evidence_count: 69 (新增用例) + 1312 (两层全量回归基线)
