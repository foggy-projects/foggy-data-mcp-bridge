---
type: requirement
version: 8.3.0.beta
req_id: P2-ComposeQuery-Columns-API
priority: P2
status: in-design
last_updated: 2026-04-26
---

# Compose Query · `columns` API 双字段过渡态收口

## 背景

8.2.0.beta M2 引入 `QueryPlan` 体系时把 `columns` 元素从 `String` 扩展到 `String | PlanExpression`（`ColumnExpr / ProjectedColumn / AggregateColumn / WindowColumn / PlanColumnRef / BinaryExpr / CaseWhenExpr / LiteralExpr`），核心 plan 类（`BaseModelPlan / DerivedQueryPlan`）的字段类型已经迁移到 `List<Object>`，但为了兼容既有调用方，Builder 与 Options 层留下了过渡态：

- `BaseModelPlan.Builder` / `DerivedQueryPlan.Builder`：`@Deprecated columns(List<String>)` + `columnsObj(List<Object>)` 双 setter
- `Dsl.FromOptions` / `QueryOptions`：`List<String> columns` + `List<Object> columnsObj` 双字段并存
- 取值逻辑分散在 `Dsl.from()` 两处 + `QueryPlan.query(opts)` 一处：`opts.columnsObj() != null ? opts.columnsObj() : (List<Object>)(List<?>) opts.columns()`
- 跨 fsscript 边界：`ScriptRuntime` 把 `columns` 强转为 `List<String>`，限制 fsscript 调用方只能传字符串

经 simplify 复核（2026-04-26）确认这是合并性技术债务，不阻断当前迭代，但每新增一个 PlanExpression 子类型 / Options 字段都会让债务面扩大，且 `ScriptRuntime` 的 `List<String>` 强转会在 JS 调用方传 `Query.col(...).sum()` 时直接 ClassCastException —— 限制了 8.3.0.beta P1 时间分析等后续能力的表达力。

## 目标

把 `columns` 收口到**单字段 + 单 setter + wildcard 接收 + 构造时校验**的形态，与 Python 端 `columns: List[Union[str, ColumnExpr, ProjectedColumn, ...]]` 在语义上完全对齐。

### 必要交付项

1. `BaseModelPlan` / `DerivedQueryPlan`：单字段 `List<Object> columns`、单 setter `Builder.columns(List<?>)`、`columnsObj` 全删
2. `Dsl.FromOptions` / `QueryOptions`：单字段 `List<Object> columns`、单 setter `columns(List<?>)`、`columnsObj` 全删
3. `Dsl.from()` / `QueryPlan.query(opts)`：取值 ternary 退化为 `opts.columns()`
4. `ScriptRuntime` fsscript 桥接：`(List<Object>) args.get("columns")` 替代 `(List<String>)` 强转，让 JS 可以传 `[Query.col('a'), Query.col('b').sum().as('total')]`
5. `ExpressionWhitelistValidator.validateColumns / validateDerivedColumns`：升级为 `List<Object>` heterogeneous，对 `PlanExpression` 子类型分支做 Layer-B 白名单校验
6. 构造时类型校验：在 `QueryPlan.validateColumnElements` 收口，元素必须 `String | PlanExpression`，否则抛 `IllegalArgumentException` 带 index + 实际类型
7. 守护测试 ≥3 条：拒绝非法元素 / 接受 mixed list / 反射断言 `columnsObj` 字段与方法不可重新引入
8. 文档：`8.2.0.beta P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md` §QueryOptions / §Builder 段补写"heterogeneous wildcard 单字段"约束；progress 文档 follow-up 关闭

### 非目标

- 不引入第三种列形式（如 `Map<String, Expression>` 用作 select-as-map）
- 不修改对外 fluent API 形态（`QueryPlan.where() / .select() / .groupBy()` 链路语义保持不变）
- 不重写 `ComposePlanner.compileExpression` 的 visitor / dispatch 逻辑（独立技术债，留 v1.5+）
- 不改 Python 端代码（Python 本来就单字段，仅同步 spec 文字）
- 不改外部消费者（`foggy-dataset-mcp` `LocalDatasetAccessor` 等只用 plan output，不构造 plan）

## 核心契约

### Builder/Options API

```java
// 三个类的最终统一形态：
public Builder columns(List<?> v) {
    // 接受任意 list；元素类型在 build() 时统一校验
    this.columns = v;
    return this;
}
```

调用方写法：

```java
// 既有写法继续 work（List<String> 是 List<? extends Object> 的子类型）
.columns(List.of("a", "b"))

// 新写法：mixed
.columns(List.of("rawCol", new ColumnExpr("astCol"), 
                 amountRef.sum().as("total")))
```

### 类型校验

```java
// QueryPlan.validateColumnElements
static void validateColumnElements(List<?> columns, String fieldName) {
    if (columns == null || columns.isEmpty()) return;  // 空允许（fluent 中间态）
    for (int i = 0; i < columns.size(); i++) {
        Object c = columns.get(i);
        if (c == null) {
            throw new IllegalArgumentException(
                fieldName + "[" + i + "] must not be null");
        }
        if (c instanceof String s) {
            if (s.isEmpty()) {
                throw new IllegalArgumentException(
                    fieldName + "[" + i + "] string must not be empty");
            }
            continue;
        }
        if (c instanceof PlanExpression) continue;
        throw new IllegalArgumentException(
            fieldName + "[" + i + "] must be String or PlanExpression, "
            + "got: " + c.getClass().getName());
    }
}
```

### Layer-B 白名单分支（沙箱升级）

| 元素类型 | 校验路径 |
|---|---|
| `String` | 现有源码扫描（保留） |
| `ColumnExpr` / `PlanColumnRef` | 字段名白名单（与 String 同源） |
| `ProjectedColumn` | 递归校验内部 expr + alias 合法性（同源码扫描 alias） |
| `AggregateColumn` | 函数白名单（`sum/count/avg/min/max/...`），递归校验 ref |
| `WindowColumn` | 函数白名单 + 递归校验 partitionBy/orderBy/frame |
| `BinaryExpr` | 操作符白名单 + 递归校验 left/right |
| `CaseWhenExpr` | 递归校验 whens/elseExpr |
| `LiteralExpr` | 直接通过（类型化值，不可注入） |
| 其他 `PlanExpression` 子类型 | **fail-closed** 抛 `SANDBOX_UNKNOWN_EXPR` |

## 跨仓影响

| 仓 | 是否需要改动 | 说明 |
|---|---|---|
| `foggy-data-mcp-bridge-wt-dev-compose` | ✅ 主改动面 | 本需求覆盖 |
| `foggy-data-mcp-bridge-python` | ❌ 无代码改动 | Python 本来就 `List[Union[str, ...]]` 单字段；仅 spec 文字同步 |
| `foggy-data-mcp-bridge` (mainline) | ❌ 不直接消费 plan builder | M6 SQL 编译入口走 `compilePlanToSql(plan, ...)` |
| `foggy-dataset-mcp` | ⚠️ 1 处 import 验证 | `LocalDatasetAccessor.java` grep 出现 `columnsObj` 但仅做参数名引用，需逐行核 |
| `foggy-odoo-bridge-pro` | ❌ 不直接构造 plan | 走 `compose.script` 工具，间接通过 fsscript（受益于 Step 3 放宽） |

## 阻断与依赖

- **不阻断**任何 8.2.0.beta 收尾 / 8.3.0.beta P1 时间分析的继续推进
- **被依赖于**：8.3.0.beta P1 时间分析（`TimeWindowExpander.java:309` 已经在用 `.columnsObj(finalCols)`，本需求收口后该处可改回 `.columns(...)`）
- **解锁**：fsscript 端 `Query.col(...).sum().as(...)` 链式表达可以作为 `columns` 元素进入沙箱，M9 沙箱第二阶段（PlanExpression 白名单）

## 验收标准

| # | 验收项 | 度量 |
|---|---|---|
| AC-1 | `BaseModelPlan` / `DerivedQueryPlan` / `Dsl.FromOptions` / `QueryOptions` 中均无 `columnsObj` 字段、方法、参数名 | grep 全仓 0 命中 |
| AC-2 | `Dsl.from()` / `QueryPlan.query(opts)` 均无 ternary `columnsObj() != null ?` | grep 0 命中 |
| AC-3 | `ScriptRuntime` 不再有 `(List<String>) args.get("columns")` | grep 0 命中 |
| AC-4 | `ExpressionWhitelistValidator.validateColumns / validateDerivedColumns` 签名为 `List<Object>` | 编译通过 + 测试覆盖每种 PlanExpression 子类型 |
| AC-5 | `compose` 包全量测试基线持平或上升 | sqlite lane 397 → ≥397 + 守护测试净增 |
| AC-6 | 全仓 `foggy-dataset-model` sqlite lane 0 regression | 与 simplify 复核基线对比 0 failure |
| AC-7 | 守护测试覆盖 mixed list 接受 / 非法元素拒绝 / `columnsObj` 不可回流 | ≥3 条新测试 |
| AC-8 | 文档 follow-up 项关闭 | 8.2.0.beta P0 progress + 8.3.0.beta P2 progress 双向回写 |

## 风险与缓解

| 风险 | 缓解 |
|---|---|
| 删 `columnsObj` 后大量测试 / demo 调用编译失败 | 分 step 提交，每步独立 build 绿；IDE refactor "rename method" 批量改 |
| `List<?>` 失去编译期元素类型检查 | Plan 构造期 fail-closed runtime 校验 + 守护测试 |
| Layer-B 白名单覆盖 PlanExpression 子类型时遗漏 | 兜底分支 `else throw SANDBOX_UNKNOWN_EXPR`；M9 24 条用例补 PlanExpression 4-6 条 |
| Python parity 审计抱怨 Java 多了字段类型 | 改完后语义对等；spec 文字同步即可 |
| fsscript 外部 demo / 集成测试假设 columns 是字符串 | 仅消费场景不受影响；Step 3 改完后 grep 验证无 `columns: [...]` 字面 List<String> 假设 |

## 工作量预估

| 维度 | 估算 |
|---|---|
| 主代码改动 | ~6 文件、~80–100 行净改动（多数是删除） |
| 测试代码改动 | ~20–30 处机械替换 |
| 沙箱新逻辑（Step 4） | ~50–80 行 |
| 守护测试 | ~40 行 |
| Python parity | 0 行代码 |
| 文档 | 2–3 处 progress / spec 段 |
| **总耗时** | **0.5–1 个工作日**（沙箱不扩 PlanExpression）/ **1–2 天**（含沙箱 Layer-B 扩展） |

## 关联文档

- 上游基线：`docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`
- 上游 progress：`docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-progress.md`
- 沙箱用例：`docs/8.2.0.beta/P0-ComposeQuery-沙箱白名单错误码与防护用例清单.md`
- 时间分析依赖方：`docs/8.2.0.beta/P1-ComposeQuery-时间分析能力增强-需求.md`
- 进度跟踪：`docs/8.3.0.beta/P2-ComposeQuery-Columns-API-收口-progress.md`
