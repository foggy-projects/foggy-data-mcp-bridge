# Compose Query · 链式 API 手册（Manual B）

> **状态**：8.x 同步版 · 已同步派生 / Join / Union / CTE 复用；链式入口长期策略仍以 Manual A 为准
> **风格定位**：以 `Query.from(...)` 为入口的 fluent / chained API；面向 SDK 调用方、需要 IDE 补全和静态分析的开发者
> **镜像手册**：[DSL 配置式手册（Manual A）](./dsl-manual.md)
> **缺口跟踪**：`docs/8.3.0.beta/compose-query-manuals-gap-tracker.md`
> **优先级**：本手册晚于 Manual A 落稿（用户决策："先对齐 DSL，再考虑链式 API"）；新能力优先以 DSL 手册作为规范入口

::: tip 关于"🚧 待补"标记
本手册采用骨架先行策略，章节标题已固定，能力随 spec 补齐分批落稿。看到 🚧 表示对应章节有未关闭的 gap，可在 `docs/8.3.0.beta/compose-query-manuals-gap-tracker.md` 查看上下文与目标版本。
:::

::: info 决策契约
- **决策 1（功能对齐 ≠ 形态对齐）**：本手册与 DSL 配置式手册**功能必须等价**，但写法可不同——见 gap tracker 中的 Layer 1 / Layer 2 划分
- **决策 2（骨架先行）**：能力补齐过程中所有缺口在 gap tracker 留档
- **决策 3（query-dsl.md deprecated）**：本手册不承担遗留 query-dsl.md 的迁移引导，由 Manual A 负责
:::

::: warning 关于本手册的存续性
**架构验证已完成（2026-04-26）**：DSL 配置式（Manual A）与链式 API 在 IR 层完全独立，未来如整体移除 `Query.from(...)` 入口，本手册可安全 deprecate，**不影响 DSL 任何能力**。

具体而言：
- DSL 解析器直接构造 QueryPlan AST，不经过 `Query.from(...)`
- timeWindow 比较模式展开（`comparison: "yoy"` 等）在编译器层完成，与链式 API 无关
- `dsl({...})` 返回的 plan 对象上的 `.join()` / `.where()` 等方法属于 plan-level 能力，移除链式入口时**保留**

完整结论与证据见 `docs/8.3.0.beta/compose-query-manuals-gap-tracker.md` 的 G7 closure note；移除级别选择见同文件 G8。
:::

---

## 1. 入门：`Query.from(...)` 链式查询

🚧 **待补**：等 Manual A §1 落稿后镜像

预览（来源：`docs/8.2.0.beta/P0-ComposeQuery-CTE使用参考手册.md`）：

```javascript
const salesBase = Query.from("FactSalesQueryModel");
const sales = salesBase
  .where([{ field: "salesDate$id", op: ">=", value: "2025-01-01" }])
  .groupBy(salesBase.product$id)
  .select(
    salesBase.product$id,
    salesBase.product$caption,
    salesBase.salesAmount.sum().as("totalSales")
  );
```

要覆盖的小节：
- 双变量模式（`xxxBase` + `xxx`）的语义和必要性
- 链的执行模型（`.select()` 是终态还是可继续）
- 与 `dsl({...})` 的对应关系

---

## 2. 列与维度引用约定（Proxy 属性）

🚧 **待补**：等 Manual A §2 落稿后镜像

要覆盖的：
- Proxy 属性访问：`base.product$id`
- 链式聚合：`base.salesAmount.sum() / .avg() / .count()`
- alias：`.as("...")`
- 与 DSL 字符串短写 / 对象长写的功能对应

---

## 3. 过滤 / 分组 / 排序 / 分页

🚧 **待补**

要覆盖的：
- `.where([{field, op, value}])`
- `.groupBy(...)` / `.having(...)`
- `.orderBy(...)`
- `.limit() / .offset()`

---

## 4. 计算字段

🚧 **待补**：参考 `docs/8.3.0.beta/compose-query-manuals-gap-tracker.md` 的 G6。

要覆盖的：
- 表达式风格：`base.salesAmount.minus(base.returnAmount).as("netAmount")`
- formula 字符串风格（兼容 DSL 形态）
- 计算字段递归依赖

---

## 5. 派生查询：`prev.where(...).select(...)`

链式 API 中，任何已构造出的 plan 都可以继续 `.where()` / `.select()` / `.orderBy()`，形成派生查询。派生层只能引用上一层已投影出的列。

```javascript
const base = Query.from("OdooSaleOrderModel").groupBy(...).select(...);
const filtered = base
  .where([{ field: "totalSales", op: ">", value: 50000 }])
  .select(base.teamId$caption, base.totalSales);
```

约束与 Manual A §5 一致：

- 聚合后的 alias 会成为派生层可引用字段
- 未投影字段不能越层引用
- 字段权限按底层依赖字段追溯校验
- 重新 `Query.from(model)` 是从 QM 开始的新查询；对已有 plan 继续调用方法是派生查询

---

## 6. Join：`a.join(b, type, on)`

plan-level Join 入口与 DSL 返回的 plan 对象一致：

```javascript
const joined = premiumCustomers.join(pendingOrders, "inner", [
  { left: "id", op: "=", right: "partnerId" }
]);

const finalPlan = joined.select(
  premiumCustomers.name.as("customer_name"),
  pendingOrders.name.as("order_number"),
  pendingOrders.amountTotal.as("order_amount")
);
```

约束：

- join 类型支持 `inner` / `left` / `right` / `full`
- `on` 只支持等值条件，可写多键
- 不支持公开非等值 join、自连接、跨数据源 join
- 不提供 `crossJoin` 作为公开组合入口

---

## 7. Union：`a.union(b)`

```javascript
const allOrders = onlineOrders.union(offlineOrders, { all: true });
const allChannels = onlineOrders.union([offlineOrders, partnerOrders], { all: true });
```

约束：

- 默认按 `UNION` 去重；`{ all: true }` 对应 `UNION ALL`
- 列按左侧 plan 的输出 schema 对齐
- 所有分支列数和兼容类型必须一致
- Union 后仍可继续作为派生 plan 追加过滤、排序或聚合

---

## 8. CTE 复用与命名 plans

CTE 复用是编译器内部能力。链式 API 中同一个 plan 被多个下游 plan 引用时，引擎会自动识别公共子计划，在支持 CTE 的方言上编译为 `WITH ... AS (...)`，否则退回派生子查询。

公开边界：

- 不支持手写 CTE 名称
- 顶层多 plan 返回中的业务名称只用于结果映射，不等于 SQL CTE 名称
- 不支持递归 CTE 或注入任意 SQL 文本

---

## 9. 时间窗口语义层（高层快捷方式）

链式入口目前不作为 timeWindow 的首选公开写法。需要稳定的 AI 生成和 MCP 调用时，请使用 Manual A 的 DSL 形态：

```javascript
dsl({
  model: "FactSalesQueryModel",
  columns: ["salesDate$id", "salesAmount"],
  groupBy: ["salesDate$id"],
  timeWindow: {
    field: "salesDate$id",
    grain: "month",
    comparison: "yoy",
    value: ["2025-01-01", "2026-01-01"],
    targetMetrics: ["salesAmount"]
  }
});
```

如 SDK 后续保留链式 helper，语义必须与 DSL 完全一致，包含：

- `grain × comparison` 兼容矩阵
- `targetMetrics` 只指向原生聚合度量
- 输出后缀：`__prior` / `__diff` / `__ratio` / `__ytd` / `__mtd` / `__rolling_{N}{unit}`
- timeWindow + calculatedFields 的执行顺序：先窗口展开，再外层标量计算

候选 helper 仅供 SDK 设计参考：

```javascript
const yoy = base.timeWindow({
  field: "salesDate$id",
  grain: "month",
  comparison: "yoy",
  value: ["2025-01-01", "2026-01-01"],
  targetMetrics: ["salesAmount"]
});
```

---

## 10. 时间窗口原语层（底层窗口函数）

🚧 **待补**：参考 `docs/8.3.0.beta/compose-query-manuals-gap-tracker.md` 的 G3。

形态候选：

```javascript
const withLag = base.select(
  base.salesDate$id,
  base.salesAmount,
  base.salesAmount.lag(1).over({
    partitionBy: [base.product$id],
    orderBy: [base.salesDate$id]
  }).as("salesAmountLag1")
);
```

底层 IR 已实现：`OverClause` / `WindowColumn` / `WindowFrame`（v1.5 Java）

---

## 11. 输出后缀规约

链式 API 如暴露 timeWindow helper，必须沿用 Manual A §11 的默认后缀：

| 后缀 | 含义 |
|------|------|
| `__prior` | 前期值 |
| `__diff` | 差值 |
| `__ratio` | 增长率 |
| `__ytd` | 年初至今累计 |
| `__mtd` | 月初至今累计 |
| `__rolling_{N}{unit}` | 滚动窗口值 |

---

## 12. 错误码与诊断

🚧 **待补**

与 Manual A §12 共享同一套错误码体系（沙箱 `COMPOSE_*` + 治理层）。

---

## 13. 真值 SQL 编译预览（4 方言）

🚧 **待补**

与 Manual A §13 共享同一份编译规则（同一份 IR）。

---

## 附录 A · 链式 API ↔ DSL 互译表

🚧 **待补**：两本手册都达到 §1-§11 完整后再写

镜像 Manual A 的附录 A，方向反转。

---

## 附录 B · 原 QueryPlan API 映射

🚧 **待补**：可选，仅在内部 IR 类型暴露给 SDK 调用方时启用

要覆盖的：
- `BaseModelPlan / DerivedQueryPlan / JoinPlan / UnionPlan` 类型与链式构造方法的对应

---

## 维护记录

| 日期 | 操作 | 备注 |
|------|------|------|
| 2026-04-26 | 创建骨架 | 初始化 13 节占位，与 Manual A 章节严格对仗 |
| 2026-05-03 | 同步 8.x 已验收能力 | 补齐派生查询 / Join / Union / CTE 复用，并明确 timeWindow 以 DSL 为稳定公开入口 |
