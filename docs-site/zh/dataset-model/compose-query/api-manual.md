# Compose Query · 链式 API 手册（Manual B）

> **状态**：Draft skeleton · 持续补齐中
> **风格定位**：以 `Query.from(...)` 为入口的 fluent / chained API；面向 SDK 调用方、需要 IDE 补全和静态分析的开发者
> **镜像手册**：[DSL 配置式手册（Manual A）](./dsl-manual.md)
> **缺口跟踪**：[compose-query-manuals-gap-tracker.md](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md)
> **优先级**：本手册晚于 Manual A 落稿（用户决策："先对齐 DSL，再考虑链式 API"）；当前为骨架占位

::: tip 关于"🚧 待补"标记
本手册采用骨架先行策略，章节标题已固定，能力随 spec 补齐分批落稿。看到 🚧 表示对应章节有未关闭的 gap，按编号跳到 [gap tracker](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md) 查看上下文与目标版本。
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

完整结论与证据见 [G7 closure note](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md#g7--dsl-与链式-api-是否互不依赖架构验证--closed)；移除级别选择见 [G8](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md#g8--移除链式-api-时的级别选择level-1-vs-level-2)。
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

🚧 **待补**：参考 [G6](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md#g6--计算字段在-timewindow-上下文里的语义)

要覆盖的：
- 表达式风格：`base.salesAmount.minus(base.returnAmount).as("netAmount")`
- formula 字符串风格（兼容 DSL 形态）
- 计算字段递归依赖

---

## 5. 派生查询：`prev.where(...).select(...)`

🚧 **待补**：与 Manual A §5 对仗

预览：

```javascript
const base = Query.from("OdooSaleOrderModel").groupBy(...).select(...);
const filtered = base
  .where([{ field: "totalSales", op: ">", value: 50000 }])
  .select(base.teamId$caption, base.totalSales);
```

要覆盖的：
- 派生 plan 上的列引用（alias 提升为 first-class 属性）
- 派生 vs 重新 `Query.from()` 的语义差异

---

## 6. Join：`a.innerJoin(b).on(...)`

🚧 **待补**：与 Manual A §6 对仗

预览（来源：8.2.0.beta CTE 手册）：

```javascript
const joined = premiumCustomers.innerJoin(pendingOrders)
  .on(premiumCustomers.id, pendingOrders.partnerId);
const finalPlan = joined.select(
  premiumCustomers.name.as("customer_name"),
  pendingOrders.name.as("order_number"),
  pendingOrders.amountTotal.as("order_amount")
);
```

要覆盖的：
- `.innerJoin / .leftJoin / .rightJoin / .fullJoin / .crossJoin`
- 多键 ON：`.on(a.x, b.x).and(a.y, b.y)`
- 非等值 join

---

## 7. Union：`a.union(b)`

🚧 **待补**

要覆盖的：
- `.union()` vs `.unionAll()`
- 列对齐策略

---

## 8. CTE 复用与命名 plans

🚧 **待补**

要覆盖的：
- 多次引用同一 plan 时引擎自动 CTE 化
- 顶层返回 `{ plans: { name1: planA, name2: planB } }` 多 plan 返回

来源：8.2.0.beta P0-ComposeQuery-CTE使用参考手册（已有，可直接迁移并精简）

---

## 9. 时间窗口语义层（高层快捷方式）

🚧 **待补**：参考 [G1](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md#g1--链式-api-缺时间窗口语义层)

形态候选（**spec 未确定**）：

```javascript
// 候选 A：方法链 helper
const yoy = base.compareTo("yoy", { grain: "month", targetMetrics: ["salesAmount"] });

// 候选 B：配置对象
const yoy = base.timeWindow({
  field: "salesDate$id",
  grain: "month",
  comparison: "yoy",
  targetMetrics: ["salesAmount"]
});
```

要决定的事：
- 形态选择（决策 1：功能对齐即可，不要求和 DSL 字面对仗）
- 是否沿用 DSL 同款后缀规约（建议是，见 G4）

---

## 10. 时间窗口原语层（底层窗口函数）

🚧 **待补**：参考 [G3](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md#g3--双侧缺底层窗口原语暴露-lag--lead--rolling--over)

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

🚧 **待补**：参考 [G4](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md#g4--输出后缀规约链式侧未继承)

建议沿用 DSL 同款后缀（默认值），允许 `.as("custom")` override。

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
