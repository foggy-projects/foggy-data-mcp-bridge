---
type: progress
version: 8.3.0.beta
req_id: P2-ComposeQuery-Columns-API
priority: P2
status: accepted
last_updated: 2026-04-26
---

# Compose Query · `columns` API 收口 — Progress

> 状态口径：`not-started` / `in-design` / `in-progress` / `blocked` / `ready-for-review` / `accepted` / `rejected`

## 关联规范文档

- 需求：`P2-ComposeQuery-Columns-API-收口-需求.md`
- 上游基线：`../8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`
- 沙箱用例：`../8.2.0.beta/P0-ComposeQuery-沙箱白名单错误码与防护用例清单.md`

## 当前阶段判断

- 当前阶段：`accepted`
- 当前目标：把 dual columns API 收口为单字段 + 单 setter + wildcard 接收的形态
- 当前范围：仅 Java 主仓 compose 包；Python 无代码改动

## 前置条件检查表

| item | 说明 | 状态 |
|---|---|---|
| 8.2.0.beta M2 / M9 已完成 | `BaseModelPlan / DerivedQueryPlan / ExpressionWhitelistValidator` 主体已落地 | `done` |
| simplify 复核基线 | sqlite lane 397 compose tests 全绿（M6 sql compiler 1537 全仓基线） | `done` (2026-04-26) |
| Python parity 单字段已确认 | Python 端 `columns: List[Union[str, ColumnExpr, ...]]` | `done` |

## Step 追踪

| step | 内容 | 状态 | 备注 |
|---|---|---|---|
| S0 | 创建需求 + progress 文档 | `completed` | `2026-04-26` |
| S1 | `BaseModelPlan / DerivedQueryPlan` Builder 单 setter `columns(List<?>)` + 抽 `validateColumnElements` | `completed` | `QueryPlan.validateColumnElements(List<?>, fieldName)` 接管两个 plan 的 column 校验；fluent intermediate stage 仍允许 null/empty |
| S2 | `Dsl.FromOptions / QueryOptions` 单字段 + Builder 单 setter；删 `columnsObj` 字段；`Dsl.from() / QueryPlan.query()` ternary 退化 | `completed` | 三处 ternary 全部退化为 `opts.columns()`；`QueryOptions.of(List<?>)` factory 同步 wildcard |
| S3 | `ScriptRuntime` 把 `(List<String>) args.get("columns")` 改为 `List<Object>` | `completed` | fsscript 边界放宽，可传 `Query.col(...).sum().as(...)` 这类 PlanExpression 元素 |
| S4 | `ExpressionWhitelistValidator.validateColumns / validateDerivedColumns` 升级为 `List<Object>` heterogeneous + Layer-B 白名单分支 | `completed` | 新增 `validatePlanExpression` 递归走 `ColumnExpr / PlanColumnRef / LiteralExpr / ProjectedColumn / AggregateColumn / WindowColumn / BinaryExpr / CaseWhenExpr` 八种节点；`assertFunctionAllowed` + `ALLOWED_BINARY_OPS` 白名单；未识别 `PlanExpression` 子类型 fail-closed `LAYER_B_FUNCTION_DENIED` |
| S5 | 测试 / demo 调用面 `columnsObj(` → `columns(` 机械替换 | `completed` | `TimeWindowExpander.java:309` 改回 `.columns(...)`；`LocalDatasetAccessor.java:311` 局部变量 `columnsObj` → `columnsValue` 重命名（grep 卫生） |
| S6 | 文档回写：8.2 P0 progress + 8.3 P2 progress 双向；spec 文字同步 | `completed` | 见下方 §文档回写 |
| S7 | 守护测试 ≥3 条 | `completed` | `ColumnsApiContractTest` 7 tests：mixed list 接受 / 非法元素拒绝 / null+空字符串拒绝 / DerivedQueryPlan 空允许 / `Query.col(...).sum().as(...)` 示例入口 / 反射断言 columnsObj 不可回流 / 反射断言 wildcard 单 setter |
| S8 | 全量 compose 回归 + sqlite lane 全仓回归 | `completed` | sqlite lane **1699 passed / 7 failures / 1 skipped** —— 7 failures 全部预存（同 simplify 基线：2 namespace 校验拆除 + 5 sandbox `buildResult` NPE），0 regression |

## 验收对照

| AC | 验收点 | 状态 | 证据 |
|---|---|---|---|
| AC-1 | `columnsObj` 全消失（4 类范围） | ✅ `passed` | `BaseModelPlan / DerivedQueryPlan / Dsl.FromOptions / QueryOptions` 4 类 grep 0 命中（含字段、方法、参数）；`ColumnsApiContractTest.columnsObjMustNotResurface` 反射断言通过 |
| AC-2 | ternary 全消失 | ✅ `passed` | `Dsl.java:75/87` + `QueryPlan.java:267` 三处全部退化；grep `columnsObj() != null` 0 命中 |
| AC-3 | `ScriptRuntime` `(List<String>) args.get("columns")` 强转消失 | ✅ `passed` | 改为 `(List<Object>) args.get("columns")` |
| AC-4 | `ExpressionWhitelistValidator` 签名 `List<Object>` | ✅ `passed` | `validateColumns(List<?>, String)` + `validateDerivedColumns(List<?>, String)` 全升级，PlanExpression 八节点白名单覆盖 |
| AC-5 | compose 包测试基线持平或上升 | ✅ `passed` | compose subtree 570 → **576** passed（净 +6 守护测试）；compose 内 7 个 pre-existing failures 不变 |
| AC-6 | 全仓 sqlite lane 0 regression | ✅ `passed` | `1699 passed / 7 failures / 1 skipped`；7 failures 与 simplify 基线 1:1 对齐 |
| AC-7 | 守护测试 ≥3 条 | ✅ `passed` | `ColumnsApiContractTest` 7 tests 全绿 |
| AC-8 | 双向 follow-up 回写 | ✅ `passed` | 本 progress + 需求文档已收口；8.2 P0 progress 已在 M10 备注补交叉引用 |

## 文档回写

- 需求文档：本目录下，已包含目标 / 契约 / 风险 / 工作量 / 验收。
- 进度文档：本文件 — `accepted` 状态。
- 验收记录：`acceptance/P2-ComposeQuery-Columns-API-acceptance.md`。
- 上游 `8.2.0.beta` 不再需要 follow-up：M2 既有的 `Builder.columnsObj` 过渡态本来在 progress 内被标记为"内部过渡"，本需求把它替换为干净 API；8.2.0.beta P0 M10 行已补"`columnsObj` follow-up：8.3.0.beta P2 已 accepted"交叉引用。
- Python parity：Python `foggy.dataset_model.engine.compose.plan` 一直是 `columns: List[Union[str, ...]]` 单字段；本次 Java 改动后两端语义对等，无需 Python 代码改动。spec 文字同步建议在下次 P0 spec 修订（M10 签收前）一并落实。

## 当前测试基线

- compose subtree（compilation + schema + plan + sandbox + authority + context + runtime + security）：**576 passed / 7 failures / 1 skipped**
- 全仓 sqlite lane：**1699 passed / 7 failures / 1 skipped**
- 守护测试：`ColumnsApiContractTest` 7 / 7 passed
- Review 复跑：`mvn -pl foggy-dataset-model "-Dtest=ColumnsApiContractTest,BaseModelPlanTest,DerivedQueryPlanTest,ScriptRuntimeTest" -P!multi-db test` → **44 passed / 0 failures**

### 7 个 pre-existing failures（不阻断本需求）

| Test | 类型 | 原因 |
|---|---|---|
| `ComposeQueryContextTest.namespaceRequiredNonBlank` | namespace 校验 | 用户在 in-flight 改动里把 `namespace == null/empty` 校验改为 fallback 到 `""`，旧测试未同步 |
| `AuthorityRequestTest.namespaceRequired` | namespace 校验 | 同上 |
| `SandboxLayerATest.a10_legalBusinessParamShouldBeAccepted` | mock NPE | `semanticService.generateSql` 在测试中返回 null → `PerBaseCompiler.compileBaseModel` 的 `buildResult.getParams()` NPE |
| `SandboxLayerBTest.b05_allowedDateDiffShouldBeAccepted` | mock NPE | 同上 |
| `SandboxLayerBTest.b06_allowedIifSumShouldBeAccepted` | mock NPE | 同上 |
| `SandboxLayerCTest.c06_legalChainShouldBeAccepted` | mock NPE | 同上 |
| `SandboxLayerCTest.c07_legalTosqlDebugShouldBeAccepted` | mock NPE | 同上 |

## 改动文件清单

### 主代码（compose 包内）

1. `foggy-dataset-model/src/main/java/.../compose/plan/QueryPlan.java`
   - 新增 `validateColumnElements(List<?>, String)` 静态包内可见辅助
   - `query(opts)` ternary 退化为 `opts.columns()`
   - fluent `select(...)` 内部 `.columnsObj(columns)` → `.columns(columns)`
2. `foggy-dataset-model/src/main/java/.../compose/plan/BaseModelPlan.java`
   - 内部循环校验抽走，调 `validateColumnElements`
   - Builder 删 `@Deprecated columns(List<String>)` 重载和 `columnsObj(List<Object>)`，单 setter `columns(List<?>)`
3. `foggy-dataset-model/src/main/java/.../compose/plan/DerivedQueryPlan.java`
   - 同上
4. `foggy-dataset-model/src/main/java/.../compose/plan/Dsl.java`
   - `FromOptions` 单字段 `List<Object> columns` + 单 setter `columns(List<?>)`
   - `from(opts)` 两处 ternary 退化
   - `from(opts)` 入口校验从 "`columns OR columnsObj` 二选一非空" 退化为 "`columns` 非空"
5. `foggy-dataset-model/src/main/java/.../compose/plan/QueryOptions.java`
   - 单字段 + 单 setter；`of(List<?>)` factory 同步 wildcard
6. `foggy-dataset-model/src/main/java/.../compose/plan/TimeWindowExpander.java`
   - `.columnsObj(finalCols)` → `.columns(finalCols)`
7. `foggy-dataset-model/src/main/java/.../compose/runtime/ScriptRuntime.java`
   - `(List<String>) args.get("columns")` → `(List<Object>) args.get("columns")`
8. `foggy-dataset-model/src/main/java/.../compose/sandbox/ExpressionWhitelistValidator.java`
   - `validateColumns / validateDerivedColumns` 签名升级为 `List<?>`
   - 新增 `validatePlanExpression` 递归节点白名单
   - 新增 `ALLOWED_BINARY_OPS`
   - 新增 `assertFunctionAllowed` 收口函数白名单分发
9. `foggy-dataset-model/src/main/java/.../compose/plan/QueryFactory.java`
   - 补齐 `Query.col(columnName)`，让文档示例 `Query.col(...).sum().as(...)` 有真实 fsscript 入口

### 主代码（compose 外）

10. `foggy-dataset-mcp/src/main/java/.../mcp/spi/impl/LocalDatasetAccessor.java`
   - 局部变量 `columnsObj` → `columnsValue`（grep 卫生，与 compose API 无关）

### 测试代码

11. `foggy-dataset-model/src/test/java/.../compose/plan/ColumnsApiContractTest.java`（新增）
    - 7 个守护测试：mixed list / 非法元素 / null+空 / fluent intermediate / `Query.col` PlanExpression 示例 / 反射禁 columnsObj / 反射要求 wildcard

### 文档

12. `docs/8.3.0.beta/P2-ComposeQuery-Columns-API-收口-需求.md`（新增）
13. `docs/8.3.0.beta/P2-ComposeQuery-Columns-API-收口-progress.md`（本文件）
14. `docs/8.3.0.beta/acceptance/P2-ComposeQuery-Columns-API-acceptance.md`（签收记录）

## 遗留与跟踪

- 不在本需求范围内的相关债务（独立 follow-up）：
  - `ComposePlanner.compileExpression` 100+ 行 instanceof 链 → 留作 v1.5 visitor 重构
  - 8.2.0.beta P0 M10 签收阶段把"`columnsObj` 已下线"作为一行 changelog 落入 progress 备注

## 签收准备

- ✅ 需求 / 进度文档完整
- ✅ 8 项验收标准全部 `passed`
- ✅ 0 regression（与 simplify 基线 1:1）
- ✅ 守护测试 7 / 7 全绿
- ✅ Review 通过，签收状态转 `accepted`

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex reviewer
- signed_off_at: 2026-04-26
- acceptance_record: docs/8.3.0.beta/acceptance/P2-ComposeQuery-Columns-API-acceptance.md
- blocking_items: none
- follow_up_required: no
