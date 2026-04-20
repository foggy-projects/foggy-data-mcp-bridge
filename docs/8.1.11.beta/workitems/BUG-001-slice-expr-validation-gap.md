---
type: bug
bug_source: regression-found
version: 8.1.11.beta
ticket: BUG-001
severity: minor
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
---

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: backend-module
- signed_off_at: 2026-04-20
- acceptance_record: ../acceptance/version-signoff.md
- blocking_items: none
- follow_up_required: no

# BUG-001 — QueryRequestValidationStep 未识别 slice `$expr` 形态

## 文档作用

- doc_type: bug
- intended_for: execution-agent / reviewer
- purpose: 记录 `$expr` slice 被 validation 层错误拒绝的缺陷，给出复现、测试决策、修复清单与验证策略

## Background

在推进 `P2-计算字段支持in和not-in算子-需求.md` 的集成测试阶段发现：通过 `queryFacade.queryModelData` 调用路径，任何带 `$expr` 的 slice 都会在 `QueryRequestValidationStep` 被拦截，报错 `查询条件第N项的field字段不能为空`。

`$expr` 是 v8.3.0 引入的合法 slice 形态（见 `CondRequestDef.expr` 字段的 JavaDoc，专为字段间比较设计，例如 `"actualAmount > budgetAmount"`）。validation 层只识别两种形态：
- `$or` / `$and` 逻辑组 → 递归
- `{field, op, value}` 字段条件 → 三者必填

遗漏了 `$expr` 形态的分支，导致 `field` 必填检查误伤所有 `$expr` slice。

现有 `FieldComparisonTest` 能跑通是因为它走 `queryEngine.analysisQueryRequest` + `jdbcTemplate.queryForList` 的兜底路径，绕开 validation 步骤；任何通过 `QueryFacade` 的外部调用者都会命中这条 gap。

## Reproduction

**稳定步骤**：

1. 构造 `DbQueryRequestDef`，`queryModel="DimProductQueryModel"`
2. 添加 `SliceRequestDef`：
   ```java
   SliceRequestDef slice = new SliceRequestDef();
   slice.setExpr("brand in ('Apple', 'Nike')");  // 或任意合法表达式
   req.setSlice(Collections.singletonList(slice));
   ```
3. 调用 `queryFacade.queryModelData(PagingRequest.buildPagingRequest(req, 10))`

**实际结果**：

```
ExRuntimeExceptionImpl 查询条件第1项的field字段不能为空
  at QueryRequestValidationStep.validateSliceItem(...):143
```

**期望结果**：`$expr` 表达式被编译、纳入 WHERE 子句、查询正常返回。

`reproduction_status: confirmed` —— 在本仓的 `InOperatorCalcFieldIntegrationTest` 初版的 3 个 slice 用例里一致触发，与 slice 表达式内容无关（不是 IN 特定的问题）。

## Expected vs Actual

| 维度 | 期望 | 实际 |
|------|------|------|
| DSL `{ "$expr": "brand in ('Apple','Nike')" }` 提交 | 正常执行 | 被 validation 拦截 |
| 错误信息 | 如果 `$expr` 不合法，应来自表达式编译器 | 来自 validation 的 field 必填检查，误导性极强 |
| `_isLogicalGroup` 语义 | 不识别 `$expr`（`$expr` 不是逻辑组），这部分是正确的 | 无问题 |
| `$expr` 表达式本身的合法性 | 由 `SqlExpFactory.createUnresolvedFunCall` 在编译期兜底 | 当前编译期兜底存在，但 validation 根本不让走到编译期 |

## Impact Scope

- 任何通过 `QueryFacade.queryModelData` 发起的 `$expr` slice 请求都不可用
- 影响 DSL 外部消费方（本仓 `foggy-dataset-mcp` 的 MCP 接口、以及 Python / Odoo 等上游网关）
- 内部 `JdbcModelQueryEngine.analysisQueryRequest` 直接调用路径不受影响（绕开 validation）
- 本次 `v8.1.11.beta` 计算字段 IN/NOT IN 需求里的 3 个 slice 集成用例因此被临时剔除（`sliceExprIn_filtersByBrand`, `sliceExprNotIn_filtersByBrand`, `sliceExpr_inCombinedWithComparison`），现通过修复重新引入并验证

严重度评估：`minor`
- 并非核心链路阻塞（有 analysisQueryRequest 兜底）
- 但对外 DSL 契约是明确破坏 —— 承诺了 `$expr` 却在 validation 直接拒
- 修复极小（加一条 guard）

## Test Strategy

优先 `integration-test`：
- 跨 validation + 编译 + SQL 执行多层，单测只能覆盖 validation 单点，不足以证明端到端打通
- 已有测试模型 `DimProductQueryModel` + SQLite 种子数据可直接复用
- 真实 SQL 数据比对：native `WHERE brand IN (...)` → expected rows；DSL `$expr: "brand in (...)"` → actual rows；逐行等值

`automation_decision: required`
- 问题可稳定复现
- 属于 DSL 对外契约
- 修复逻辑简单但容易静默回归（一行守卫漏掉就重新挂），必须有自动化兜底
- 与 v8.1.11.beta 正在交付的 IN/NOT IN 能力同步验证

## Code Inventory

待修复：

| 文件 | 方法 | 改动类型 |
|------|------|---------|
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/QueryRequestValidationStep.java` | `validateSliceItem(SliceRequestDef, int)` | 新增 `$expr` 形态守卫 |
| 同上 | `validateCondChildren(List<CondRequestDef>)` | 镜像同样守卫，覆盖嵌套逻辑组内的 `$expr` 子条件 |

测试恢复：

| 文件 | 改动类型 |
|------|---------|
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/InOperatorCalcFieldIntegrationTest.java` | 恢复 3 个 slice 集成测试（`sliceExprIn_filtersByBrand` / `sliceExprNotIn_filtersByBrand` / `sliceExpr_inCombinedWithComparison`） |

## Fix Checklist

- [x] 1. 在 `validateSliceItem` 顶部加入 `$expr` 短路（位置：`_isLogicalGroup` 检查之后、`field` 必填检查之前）
- [x] 2. `validateCondChildren` 里镜像同样守卫，覆盖 `$and` / `$or` 内部的 `$expr` 子条件
- [x] 3. 单元测试：跳过（集成测试已端到端覆盖，按 `optional` 收口）
- [x] 4. 恢复 `InOperatorCalcFieldIntegrationTest` 的 3 条 slice 集成用例（`sliceExprIn_filtersByBrand` / `sliceExprNotIn_filtersByBrand` / `sliceExpr_inCombinedWithComparison`），并更新类级 javadoc 引用 BUG-001
- [x] 5. 全量回归：`mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite` → `Tests run: 1006, Failures: 0, Errors: 0, Skipped: 0`
- [x] 6. 回写 BUG work item 状态到 `ready-for-verification`，记录自检结论

## Verification

1. **复现测试**：修复前把 3 个 slice 用例临时加到测试类里，单跑应 3 条全红
2. **修复测试**：修复后同 3 条全绿，且真实 SQL 数据与原生 SQL `WHERE brand IN (...)` 逐行等值
3. **回归测试**：`mvn test -pl foggy-dataset-model` 全量通过
4. **契约验证**：非 `$expr` 形态的 slice（普通 `{field, op, value}` 和 `$or`/`$and`）依旧被正常校验，没有绕过

## References

- 需求文档：
  - [`P2-fsscript支持in和not-in算子-需求.md`](../P2-fsscript支持in和not-in算子-需求.md)
  - [`P2-计算字段支持in和not-in算子-需求.md`](../P2-计算字段支持in和not-in算子-需求.md) —— 已把 slice `$expr` 遗留项更新为 "已关联 BUG-001"
- 相关代码：
  - `QueryRequestValidationStep.java`（`validateSliceItem` + `validateCondChildren` 两处 mirror 新增 `$expr` 守卫）
  - `CondRequestDef.java`（`$expr` 字段定义）
  - `FieldComparisonTest.java`（现有绕开 validation 的 `$expr` 用法，现在可以切换到 `queryFacade` 路径）

## Execution Checkin

**完成工作摘要**：在 `QueryRequestValidationStep` 的两处 slice 校验入口（`validateSliceItem` 处理顶层 slice；`validateCondChildren` 处理 `$or` / `$and` 内的嵌套条件）都加上 `$expr` 短路守卫 —— 有 `$expr` 时跳过 `field` / `op` / `value` 必填检查，把表达式合法性交给 `SqlExpFactory` 在编译期兜底。恢复 v8.1.11.beta 计算字段需求里因本 BUG 临时剔除的 3 条 slice 集成测试，端到端对比真实 SQL `WHERE brand IN/NOT IN (...)` 行集等值。

**实际改动文件清单**：

| 文件 | 改动 |
|------|------|
| `foggy-dataset-model/.../plugins/result_set_filter/QueryRequestValidationStep.java` | `validateSliceItem` 与 `validateCondChildren` 各加 4 行 `$expr` 守卫，附 BUG-001 引用注释 |
| `foggy-dataset-model/src/test/.../ecommerce/InOperatorCalcFieldIntegrationTest.java` | 恢复 3 个 `@Order(1..3)` 的 slice 集成用例；类级 javadoc 从 "slice 不在范围" 改为 "已由 BUG-001 修复" |
| `docs/8.1.11.beta/workitems/BUG-001-slice-expr-validation-gap.md` | 本文档（新增） |
| `docs/8.1.11.beta/P2-计算字段支持in和not-in算子-需求.md` | 遗留项引用 BUG-001 |

**测试状态**：
- 针对测试 `InOperatorCalcFieldIntegrationTest`：8 个全绿（5 个既有 calcField + 3 个恢复的 slice）
- 全量回归 `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite`：`Tests run: 1006, Failures: 0, Errors: 0, Skipped: 0`

**自检结论**：`self-check-only` —— 4 行改动 + 4 行 mirror 守卫，逻辑与 `$or` / `$and` 的现有兜底完全同型；集成测试端到端通过（真实 SQL 数据比对），不需要升级到正式 `foggy-implementation-quality-gate`。

**遗留**：无。BUG 可进入验收。
