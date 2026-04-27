# P0 · SemanticDSL · CTE / 派生 / Join / Union 语法设计

> **状态**：Draft v4 for review
> **目标版本**：8.3.0.beta
> **关联 gap**：[G2](compose-query-manuals-gap-tracker.md#g2--dsl-配置缺完整-cte--派生--join--union-语法)
> **创建日期**：2026-04-26

## 0. 摘要

本 spec 为 SemanticDSL（`dsl({...})` 配置式查询语言）补全 CTE / 派生查询 / Join / Union 语法定义。当前 DSL 只覆盖单层查询和 timeWindow 包装，缺少多阶段编排能力。本 spec 依托已 frozen 的 `QueryPlan` AST（M2 完成 · 8.2.0.beta）和已锁的链式 API spec，在不改 AST 的前提下补 DSL 表层。

**核心策略**：

- **3 个入口**：`dsl({model: <string|plan>, ...})` 单 plan 入口（多态识别基础 / 派生）+ `.join()` / `.union()` combinator 方法
- **AI 工具说明 surface 控制**：DSL 暴露给 AI 工具说明只列上述 3 入口；单 plan plan-method（`.where() / .select() / .groupBy()` 等）属链式 API 范围，不在 DSL 文档中
- **不超越 AST**：不承诺 AST 没有的能力（CROSS JOIN / 递归 CTE / 跨数据源 join 等）

## 1. 背景与原则

### 1.1 与链式 API spec 的关系

链式 API 的派生 / Join / Union 语义已在 `docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md` 中规范。本 spec **不重新定义语义**，只补 DSL 表层语法。两套表层（链式 + DSL）通过同一 `QueryPlan` AST 通信，[G7](compose-query-manuals-gap-tracker.md#g7--dsl-与链式-api-是否互不依赖架构验证--closed) 已验证 IR 独立性。

### 1.2 决策 1（功能对齐 ≠ 形态对齐）

DSL 与链式 API **功能严格等价**（Layer 1），但允许形态不同。本 spec 中：

- 派生查询：链式 `prev.where(...).select(...)` ↔ DSL `dsl({model: prev, slice, columns})`
- Join：链式 `a.innerJoin(b).on(...)` ↔ DSL `a.join(b, "inner", [{left, op, right}])`
- Union：链式 `a.union(b, {all: true})` ↔ DSL `a.union(b, {all: true})`（同形）

### 1.3 DSL 入口与 AI 工具说明的边界

DSL 推荐的对外入口有 **3 类**：

| 入口 | 适用范围 | 例子 |
|------|---------|------|
| **`dsl({...})` 配置式** | 单 plan 操作（基础 + 派生，靠 `model` 字段类型多态识别） | `dsl({model: "X", columns, slice, ...})`, `dsl({model: prevPlan, columns, ...})` |
| **`.join()` combinator** | 双 plan 连接 | `a.join(b, "inner", [{left, op, right}])` |
| **`.union()` combinator** | 双 plan 合并 | `a.union(b, {all: true})` |

#### AI 工具说明 surface 控制

底层 `QueryPlan` AST 在代码层暴露了更广的 plan-method 能力（`.where()`, `.select()`, `.groupBy()`, `.orderBy()` 等），它们由链式 API 使用，技术上 DSL 用户也可调用。但 **DSL 提供给 AI 的工具说明只列上述 3 入口**——AI 不会生成 `.where().select()` 这种链式风格代码，从而保持 DSL 用户面的简洁性，避免与链式 API 风格混淆。

这与 [G7](compose-query-manuals-gap-tracker.md#g7--dsl-与链式-api-是否互不依赖架构验证--closed) 的"DSL 与链式 API IR 层独立"对齐：

- **AST 层**：3 入口与单 plan plan-method 共存，互不依赖（Phase 0 已验证）
- **文档层**：Manual A 与 AI 工具说明只暴露 3 入口；单 plan plan-method 属 Manual B / 链式 API 范围
- **部署层**：未来 deprecate 链式 API（[G8](compose-query-manuals-gap-tracker.md#g8--移除链式-api-时的级别选择level-1-vs-level-2) Level 2 决策启动）时可同步移除单 plan plan-method 而不影响 DSL

## 2. 派生查询（Derived Query）

### 2.1 基本形态

`dsl({...})` 通过 `model` 字段的**类型**区分基础查询和派生查询：

```javascript
// 基础查询：model 是字符串（QM 名）
const base = dsl({
  model: "FactSalesQueryModel",
  columns: ["product$id", "salesAmount"],
  groupBy: ["product$id"]
});

// 派生查询：model 是 plan 引用
const top = dsl({
  model: base,                                                  // ← 接受 QueryPlan 引用
  slice: [{ field: "salesAmount", op: ">", value: 50000 }],
  columns: ["product$id", "salesAmount"]
});
```

### 2.2 `model` 字段（多态）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `model` | `string \| QueryPlan` | ✅ | 字符串：基础查询的 QM 名；plan 引用：派生查询的上一阶段 |

`model` **不接受**内联对象字面量（如 `dsl({model: {model: "X", ...}, ...})`）——派生时必须先把上一阶段构造成 plan 变量。这保持 DSL 入口唯一性。

类型不匹配（如 `model: 123` / `model: null`）→ `MODEL_TYPE_INVALID`。

### 2.3 顶层字段（基础 / 派生通用）

```
dsl({
  model: <string|plan>,  ← 必填
  columns: [...],        ← 推荐显式（见 §2.4）
  slice: [...],          ← 可选
  groupBy: [...],        ← 可选
  orderBy: [...],        ← 可选
  limit: N,              ← 可选
  start: N,              ← 可选
  distinct: true,        ← 可选
  calculatedFields: [],  ← 可选
  timeWindow: {...}      ← 可选（见 §6）
})
```

`hints` 字段引擎自动处理，用户脚本中**不需指定**。

### 2.4 Schema 派生规则

`model` 为 plan 引用时（即派生查询），**只能引用上一阶段已投影的列或别名**（含 calculatedFields 产生的别名）。

```javascript
const base = dsl({
  model: "FactSalesQueryModel",
  columns: ["product$id", "SUM(amount) AS totalSales"],   // 投影 product$id, totalSales
  groupBy: ["product$id"]
});

// ✅ 合法：引用 base 中投影的列
const top = dsl({
  model: base,
  slice: [{ field: "totalSales", op: ">", value: 50000 }],
  columns: ["product$id", "totalSales"]
});

// ❌ 非法：amount 在 base 中未投影
const wrong = dsl({
  model: base,
  slice: [{ field: "amount", op: ">", value: 100 }]   // → DERIVED_REFERENCE_NOT_PROJECTED
});
```

**底层物理列被严格隔离**——派生层不会"绕过" `columns` 投影回溯到底层 QM 字段，与 `fieldAccess` / `deniedColumns` 治理边界一致。

### 2.5 派生层的 HAVING 语义

派生层 `slice` 中引用聚合别名时，引擎自动编译为 SQL `HAVING`：

```javascript
const grouped = dsl({
  model: "FactSalesQueryModel",
  columns: ["product$id", "SUM(amount) AS totalSales"],
  groupBy: ["product$id"]
});

// 等价 SQL: GROUP BY product_id HAVING SUM(amount) > 50000
const filtered = dsl({
  model: grouped,
  slice: [{ field: "totalSales", op: ">", value: 50000 }]
});
```

引擎在编译时根据 `slice` 引用的列是否为聚合别名，自动选择 `WHERE` / `HAVING`。

### 2.6 强制要求与边界

| 项 | 规则 |
|----|------|
| `columns` 字段 | 推荐派生链上每一段都显式声明，避免 schema 不确定 |
| 中间 `orderBy` 无 `limit` 时 | 编译器**可能优化丢弃**（不保证保留）；依赖排序的场景须在最终阶段也加 `orderBy` |
| 派生层不重新定义权限 | `fieldAccess` / `deniedColumns` 由底层 BaseModelPlan 注入；派生层不可削弱 |

## 3. Join

### 3.1 形态

```javascript
const customers = dsl({ model: "OdooResPartnerModel", columns: [...] });
const orders = dsl({ model: "OdooSaleOrderModel", columns: [...] });

const joined = customers.join(orders, "inner", [
  { left: "id", op: "=", right: "partnerId" }
]);
```

`.join(other, type, on)` 是 DSL 中 Join 的**唯一形态**——配置式 `dsl({join: {...}})` 在 v3 spec 中**不提供**（双 plan 操作通过 combinator 表达更自然，避免变量在 `left: a, right: b` 中重复声明）。

### 3.2 ON 条件格式

`on` 参数是 `JoinCondition` 数组，每个条件形如：

```javascript
{ left: "field_on_left", op: "=", right: "field_on_right" }
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `left` | string | ✅ | 左侧 plan 中可见的列名 / 别名 |
| `op` | `"="`（本期仅承诺 `"="`） | ✅ | 比较运算符 |
| `right` | string | ✅ | 右侧 plan 中可见的列名 / 别名 |

#### 多条件按 AND 连接

```javascript
const joined = a.join(b, "inner", [
  { left: "company_id", op: "=", right: "company_id" },
  { left: "fiscal_year", op: "=", right: "fiscal_year" }
]);
// 等价 SQL: ON a.company_id = b.company_id AND a.fiscal_year = b.fiscal_year
```

**不支持** OR 逻辑组合或嵌套。需要 OR 时，可在 join 后的派生 `slice` 中表达。

### 3.3 Join 类型

| `type` 值 | SQL 等价 | 说明 |
|----|---------|------|
| `"inner"` | `INNER JOIN` | 只保留两侧匹配的行 |
| `"left"` | `LEFT OUTER JOIN` | 保留左侧全部，右侧不匹配为 NULL |
| `"right"` | `RIGHT OUTER JOIN` | 反之 |
| `"full"` | `FULL OUTER JOIN` | 两侧全部，不匹配为 NULL；**部分方言不支持**（如 SQLite），编译期报方言错误 |

**不支持** `CROSS JOIN`（笛卡尔积）。`on` 列表非空是硬约束。

### 3.4 重名列消歧

Join 后两侧若存在同名列，**必须**在源 plan 构造期就完成重命名。这是当前 DSL 唯一支持的消歧路径。

```javascript
// ✅ 推荐：源 plan 构造期 rename
const customers = dsl({
  model: "OdooResPartnerModel",
  columns: ["id", "name AS customer_name"]
});
const orders = dsl({
  model: "OdooSaleOrderModel",
  columns: ["partner_id", "name AS order_number", "amount AS order_amount"]
});

const joined = customers.join(orders, "inner", [
  { left: "id", op: "=", right: "partner_id" }
]);
// joined.schema = [id, customer_name, partner_id, order_number, order_amount]
// — 无歧义，可直接投影

const result = dsl({
  model: joined,
  columns: ["customer_name", "order_number", "order_amount"]
});
```

#### 错误：源 plan 未 rename

```javascript
const bad_customers = dsl({ model: "X", columns: ["id", "name"] });
const bad_orders = dsl({ model: "Y", columns: ["partner_id", "name"] });
const bad_joined = bad_customers.join(bad_orders, "inner", [
  { left: "id", op: "=", right: "partner_id" }
]);

const bad_result = dsl({
  model: bad_joined,
  columns: ["name"]   // → JOIN_AMBIGUOUS_COLUMN
});
```

错误消息格式（建议）：

> `column "name" exists in both \`bad_customers\` (left) and \`bad_orders\` (right).`
> `Rename in source plans using "name AS xxx", or — when G5 lands — use`
> `{plan: <ref>, field: "name", as: "..."} for post-join disambiguation.`

#### 后置消歧（计划中 · 等 [G5](compose-query-manuals-gap-tracker.md#g5) 收口）

::: tip 为什么必须有后置消歧路径
LLM 在生成 join 链时**很可能在源 plan 构造期就引入冲突列**——尤其是按业务模型直觉写 `columns: ["id", "name", ...]` 时。如果 DSL 唯一的消歧机制是"必须回到源 plan rename"，LLM 一旦发现冲突就要重写整段上游脚本，回归代价大、上下文消耗高。

G5 落地后将提供**派生层后置消歧**通道，让 LLM 在保留上游代码不变的前提下修复冲突——这是 G5 收口的硬性要求，不是可选增强。
:::

G5 收口后将支持列项对象语法：

```javascript
// 🚧 G5 收口后可用：派生层后置消歧
const result = dsl({
  model: bad_joined,
  columns: [
    { plan: bad_customers, field: "name", as: "customer_name" },
    { plan: bad_orders, field: "name", as: "order_number" },
    { plan: bad_orders, field: "amount", as: "order_amount" }
  ]
});
```

`{plan: <ref>, field: "...", as: "..."}` 在 AST 层等价于链式 API 的 `bad_customers.name.as("customer_name")`，但通过**对象引用**而非 Proxy 属性，DSL 独立性不受影响。详见 [G5](compose-query-manuals-gap-tracker.md#g5)。

### 3.5 约束与边界

| 项 | 规则 |
|----|------|
| 同数据源 | 左右两侧必须来自同一数据源；跨源 → `JOIN_CROSS_DATASOURCE` |
| `on` 非空 | 至少一条 join 条件，否则 → `JOIN_ON_REQUIRED` |
| `op` 仅 `"="` | 本期不承诺非等值 join；后续按需以独立 spec 扩展 |
| 自连接（self-join） | **本期不承诺**——同一 plan 不能作为 left 和 right；如需，请通过两次独立 `dsl({...})` 构造两个 plan |
| `FULL OUTER JOIN` 方言限制 | SQLite / 旧 MySQL 不支持，编译期报 `JOIN_FULL_OUTER_DIALECT_UNSUPPORTED` |

## 4. Union

### 4.1 形态

```javascript
const onlineOrders = dsl({ model: "OnlineSalesQueryModel", columns: [...] });
const offlineOrders = dsl({ model: "OfflineSalesQueryModel", columns: [...] });

// UNION（默认去重）
const allOrders = onlineOrders.union(offlineOrders);

// UNION ALL（保留重复）
const allOrdersWithDupes = onlineOrders.union(offlineOrders, { all: true });

// 多 plan union（推荐：数组形态，避免链式重复）
const consolidated = planA.union([planB, planC, planD], { all: true });

// 多 plan union（等价：链式形态，编译产物完全相同）
const consolidatedAlt = planA.union(planB, { all: true })
                             .union(planC, { all: true })
                             .union(planD, { all: true });
```

`.union(other, opt?)` 是 DSL 中 Union 的**唯一形态**（不提供 `dsl({union: [...]})` 配置式）。`other` 参数支持类型多态：

| `other` 类型 | 语义 | 推荐场景 |
|-------------|------|---------|
| `QueryPlan` | 与单个 plan union | 二元 union |
| `QueryPlan[]` | 与多个 plan 依次 union（左结合折叠） | ≥3 plan 的多元 union |

数组形态 `planA.union([B, C, D], opt)` **完全等价于** `planA.union(B, opt).union(C, opt).union(D, opt)`，编译产物相同（左结合 `UnionPlan` 嵌套）。两者在 SQL 层等价（`((A UNION B) UNION C) UNION D` ≡ `A UNION B UNION C UNION D`）。

### 4.2 列对齐策略

**按位置对齐**——以**左侧 plan**（`.union()` 调用方）的 schema 为输出列名基准：

```javascript
const a = dsl({ model: "X", columns: ["id", "name", "amount"] });
const b = dsl({ model: "Y", columns: ["customer_id", "customer_name", "total"] });

const u = a.union(b);
// 输出列名以 a 为准：id / name / amount
// b 的 customer_id / customer_name / total 按位置对齐到 id / name / amount
```

**约束**：

| 项 | 规则 |
|----|------|
| 列数一致 | 各 plan 必须有相同列数；否则 → `UNION_COLUMN_COUNT_MISMATCH` |
| 类型兼容 | 对应位置的列类型必须可强转；否则 → `UNION_COLUMN_TYPE_MISMATCH` |
| 同数据源 | 跨数据源 union → `UNION_CROSS_DATASOURCE` |

### 4.3 与 Join / 派生的混合

union 后可继续派生：

```javascript
const allOrders = a.union(b, { all: true });

const totalsByYear = dsl({
  model: allOrders,
  columns: ["year", "SUM(amount) AS yearlyTotal"],
  groupBy: ["year"]
});
```

union 不能直接进 join（**本期不承诺**）——需通过派生层做一次投影显式规范化 schema 后再 join。

### 4.4 不在本期承诺

- `INTERSECT` / `EXCEPT`
- 按列名对齐（仅按位置）

## 5. CTE 复用

### 5.1 自动 CTE 化

同一 plan 在脚本中被引用 N 次（N ≥ 2）时，引擎**自动**将其编译为单次 CTE，避免子查询重复。

```javascript
const monthlySales = dsl({
  model: "FactSalesQueryModel",
  columns: ["month", "SUM(amount) AS total"],
  groupBy: ["month"]
});

const top10 = dsl({ model: monthlySales, orderBy: ["-total"], limit: 10 });
const bottom10 = dsl({ model: monthlySales, orderBy: ["total"], limit: 10 });

return {
  plans: { top: top10, bottom: bottom10 },
  metadata: { title: "Sales extremes" }
};
```

引擎编译为：

```sql
WITH monthly_sales AS (SELECT month, SUM(amount) AS total FROM ... GROUP BY month)
-- top: SELECT * FROM monthly_sales ORDER BY total DESC LIMIT 10
-- bottom: SELECT * FROM monthly_sales ORDER BY total ASC LIMIT 10
```

详细 dedup 策略见 M6 实现（`docs/8.2.0.beta/M6-SQLCompilation-*.md`），用户无需关心。

### 5.2 顶层 `plans` 多 plan 返回

```javascript
return {
  plans: {
    top_sales: top10,
    bottom_sales: bottom10,
    by_region: regionalPlan
  },
  metadata: { title: "Multi-perspective dashboard" }
};
```

每个 named plan 独立执行；同名引用自动复用 CTE。

### 5.3 不支持

- 显式命名 CTE（如 `dsl({as: "myCTE", ...})`）——本期不承诺；自动 dedup 已覆盖大多数场景
- 递归 CTE（`WITH RECURSIVE`）——hierarchy 类需求请走 QM 层级算子（见 query-dsl.md `childrenOf` / `descendantsOf`）

## 6. 与 timeWindow 的交互

### 6.1 timeWindow 适用范围

`timeWindow` 只能作为 `dsl({...})` 的顶层字段（含基础查询和派生查询）：

```javascript
// 基础查询 + timeWindow
dsl({ model: "FactSalesQueryModel", timeWindow: {...} })

// 派生查询 + timeWindow（model 是 plan 引用）
dsl({ model: prevPlan, timeWindow: {...} })

// join / union 后再加 timeWindow（同样走派生层）
const joined = a.join(b, "inner", [{ left: "id", op: "=", right: "partner_id" }]);
const yoyPlan = dsl({
  model: joined,
  timeWindow: {
    field: "salesDate$id",
    grain: "month",
    comparison: "yoy",
    value: ["2024-01-01", "2025-01-01"],
    targetMetrics: ["amount"]
  }
});
```

`.join()` / `.union()` combinator 方法**不直接接受** `timeWindow` 参数——通过派生层 `dsl({model: combinedPlan, timeWindow: {...}})` 表达。

### 6.2 派生层使用 timeWindow 的前置条件

派生层的 `timeWindow.field` 必须**已被上游投影**（同 §2.4 schema 派生规则）。否则报 `TIMEWINDOW_FIELD_NOT_PROJECTED`。

## 7. 错误码全集（本 spec 新增）

| 错误码 | 触发条件 |
|--------|---------|
| `MODEL_TYPE_INVALID` | `dsl({model: ...})` 中 model 不是字符串也不是 `QueryPlan` 引用 |
| `DERIVED_REFERENCE_NOT_PROJECTED` | 派生层（`model: <plan>`）引用了上一阶段未投影的字段 |
| `JOIN_ON_REQUIRED` | `.join(other, type, on)` 中 on 列表为空 |
| `JOIN_CROSS_DATASOURCE` | join 两侧 BaseModelPlan 来自不同数据源 |
| `JOIN_AMBIGUOUS_COLUMN` | join 后引用了两侧重名列且未消歧 |
| `JOIN_FULL_OUTER_DIALECT_UNSUPPORTED` | 当前数据源方言不支持 FULL OUTER JOIN |
| `JOIN_OP_NOT_SUPPORTED` | `op` 取值非 `"="`（本期硬限制） |
| `UNION_COLUMN_COUNT_MISMATCH` | union 各 plan 列数不一致 |
| `UNION_COLUMN_TYPE_MISMATCH` | union 对应位置列类型不兼容 |
| `UNION_CROSS_DATASOURCE` | union 各 plan 来自不同数据源 |
| `TIMEWINDOW_FIELD_NOT_PROJECTED` | 派生层 timeWindow.field 未被上游投影 |

与 M3 沙箱错误码（`COMPOSE_*`）协同：上述均归类为 `COMPOSE_DSL_VALIDATION_*` 子族。

## 8. 不在本期承诺的能力

| 类别 | 说明 | 推迟原因 |
|------|------|---------|
| `CROSS JOIN` | 笛卡尔积 | 反 BI 模式，迟延到后续按需引入 |
| 非等值 join（`<` / `>` / `BETWEEN`） | `op != "="` | M2/M6 仅承诺等值；后续按真实需求驱动 |
| Self-join（同 plan 两侧） | join 自身 | 通过两次独立 `dsl({...})` 替代；自动 alias 留待后续 |
| 跨数据源 join / union | 多库 | 多库事务开销大；compose query 聚焦单库多表 |
| 显式命名 CTE | `dsl({as: "name", ...})` | 自动 dedup 已覆盖；显式命名留待真实场景 |
| 递归 CTE | `WITH RECURSIVE` | 走 QM 层级算子（query-dsl.md `childrenOf`） |
| `INTERSECT` / `EXCEPT` | 集合差/交 | 业务可在派生 `slice` 中表达 |
| Union 按列名对齐 | 仅按位置 | 大多数场景按位置即可；按名后续按需扩展 |
| `dsl({join: {...}})` / `dsl({union: [...]})` 配置式 | 双 plan 操作仅 combinator 方法 | combinator 表达更自然，避免 `left: a, right: b` 中变量重复声明 |
| `model` 内联对象字面量 | 仅接受字符串或 plan 引用 | 保持 DSL 入口唯一性，构造一律走 `dsl({...})` 显式 |

## 9. 完整示例 · 派生 + Join + Union 混合场景

```javascript
// 在线订单 + 离线订单合并
const online = dsl({
  model: "OnlineSalesQueryModel",
  columns: ["customerId", "amount", "salesDate"]
});

const offline = dsl({
  model: "OfflineSalesQueryModel",
  columns: ["customerId", "amount", "salesDate"]
});

const allOrders = online.union(offline, { all: true });

// 客户主数据（构造期就 rename name 避免后续 join 冲突）
const customers = dsl({
  model: "OdooResPartnerModel",
  columns: ["id", "name AS customer_name", "category$caption"],
  slice: [{ field: "category$caption", op: "contains", value: "A级" }]
});

// 订单 join 客户（combinator 形态）
const enriched = allOrders.join(customers, "inner", [
  { left: "customerId", op: "=", right: "id" }
]);
// enriched.schema = [customerId, amount, salesDate, id, customer_name, category$caption]

// 派生：按月聚合并加 yoy
const monthlyYoy = dsl({
  model: enriched,
  columns: ["customer_name", "salesDate AS salesDate$id", "SUM(amount) AS totalAmount"],
  groupBy: ["customer_name", "salesDate$id"],
  timeWindow: {
    field: "salesDate$id",
    grain: "month",
    comparison: "yoy",
    value: ["2024-01-01", "2025-01-01"],
    targetMetrics: ["totalAmount"]
  }
});

return {
  plans: { a_grade_monthly_yoy: monthlyYoy },
  metadata: { title: "A级客户月度同比" }
};
```

> 💡 **关键编排技巧**：`customers` 在构造期就用 `"name AS customer_name"` 避免与 `allOrders` 中潜在的 name 字段冲突。这是 §3.4 推荐的消歧路径。

## 10. 验收标准

本 spec 在收口前需要满足：

| 项 | 标准 |
|----|------|
| DSL 测试脚本迁移 | `real_sql_*_scenario.js` 由实现 PR 同步迁移到 v3 形态（`source: prev` → `model: prev`；如有 `dsl({join/union: ...})` 写法迁移到 combinator）；语义意图（派生 / Join / Union）保持不变 |
| 链式 API 测试脚本不破坏 | `derived_query_scenario.js` / `join_scenario.js` / `union_scenario.js` 不受本 spec 影响（属 Manual B / 链式 API 范围） |
| AST 兼容 | 不要求修改 `BaseModelPlan / DerivedQueryPlan / JoinPlan / UnionPlan` |
| 错误码覆盖 | §7 中所有错误码在 M3 沙箱错误码族中找到归属（`COMPOSE_DSL_VALIDATION_*`） |
| 与 timeWindow 协同 | §6 规则不与 8.3.0 timeWindow spec 冲突 |
| Java / Python 双端对等 | DSL 解析器在两端有同等行为；测试覆盖二端 parity |
| `.union()` 数组重载双端对等 | `.union(other, opt)` 的 `other` 在 Java / Python 两端同等支持 `QueryPlan` 和 `QueryPlan[]`；多元 union 测试用例覆盖二端 parity |

## 11. 实施落点

### 11.1 文档更新

- 本 spec 落地后：Manual A §5-§8 落稿（gap tracker 中 G2 标 closed）
- query-dsl.md 加 deprecation banner 指向本 spec + Manual A

### 11.2 代码改动（预估）

| 端 | 改动点 | 估时 |
|----|-------|------|
| Java | `DslQueryFunction.buildRequest()` 中 `model` 字段类型分派（string vs plan） | 中等 |
| Java | 错误码 `COMPOSE_DSL_VALIDATION_*` 子族落地（§7） | 小 |
| Java | DSL 测试脚本迁移（`real_sql_*_scenario.js` 中 `source: ...` → `model: ...`） | 小 |
| Java / Python | `.union(other, opt)` 数组重载（`other` 可接 `QueryPlan` 或 `QueryPlan[]`，左结合折叠为已有 `UnionPlan` 二元嵌套，AST 不变） | 极小（每端 ~5 行） |
| Python | `dsl.py` 同步对等改动 | 中等 |
| 测试 | 双端 parity test：每个新错误码至少 1 个 case + `.union([...], opt)` 多元用例 | 中等 |
| 过渡支持（可选） | `source` 字段保留为 deprecated alias 一个版本（实施期决定是否保留） | 极小 |

### 11.3 与已有 spec 的协调

| spec | 协调点 |
|------|-------|
| `docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md` | 链式侧已锁；本 spec 引用，不重定义 |
| `docs/8.2.0.beta/P0-ComposeQuery-沙箱白名单错误码与防护用例清单.md` | §7 错误码追加为 `COMPOSE_DSL_VALIDATION_*` 子族 |
| `docs/8.3.0.beta/P1-SemanticDSL-时间窗口能力设计.md` | §6 与之协同；不冲突 |

## 12. 评审 Checklist

请评审者重点关注以下决策：

- [ ] **决策 D1**：`model` 字段多态（`string | QueryPlan`），通过类型识别基础 / 派生查询 —— LLM 学习成本是否可接受？
- [ ] **决策 D2**：combinator 方法（`.join()` / `.union()`）是 join / union 的**唯一形态**，不提供 `dsl({join:...})` / `dsl({union:...})` 配置式 —— 是否合适？
- [ ] **决策 D3**：DSL 与 AI 工具说明 surface 控制（不在 spec 层硬禁止单 plan plan-method） —— 是否更优于 v2 的硬禁止？
- [ ] **决策 D4**：`join.on` 仅承诺 `op = "="` —— 是否过严？
- [ ] **决策 D5**：Union 列对齐**按位置**（左侧 plan 为基准）而非按名 —— 是否符合常见使用场景？
- [ ] **决策 D6**：自动 CTE dedup（无显式命名）—— 是否够用？
- [ ] **决策 D7**：CROSS JOIN / Self-join / 跨数据源 / 非等值 join 全部推迟 —— 是否有 P0 业务场景被遗漏？
- [ ] **决策 D8**：错误码归类为 `COMPOSE_DSL_VALIDATION_*` 子族 —— 是否合并到现有错误码族？

## 维护记录

| 日期 | 操作 | 备注 |
|------|------|------|
| 2026-04-26 | 创建 Draft v1 | 基于三角验证：链式 spec / AST 能力 / 测试脚本现状 |
| 2026-04-26 | 修订 v2 | (1) 移除 spec 中所有"链式 API plan-method 单 plan 操作"示例；(2) §3.6 重名消歧改为构造期 rename 强制 + Option C 留作 G5 后置消歧；(3) §9.2 plan-method 等价段移除；(4) §1.3 重写为"DSL 入口与 combinator 方法的边界" |
| 2026-04-26 | 修订 v3 | (1) `model` 字段多态化：吸收 v2 的独立 `source` 字段，通过类型（string / QueryPlan）区分基础与派生查询；(2) 移除 `dsl({join: {...}})` / `dsl({union: [...]})` 配置式，combinator 方法 `.join()` / `.union()` 成为 join/union 唯一形态；(3) §1.3 改为"DSL 入口与 AI 工具说明的边界"——不在 spec 层硬禁止单 plan plan-method，改为 AI 工具说明 surface 控制；(4) 错误码：移除 `DERIVED_SOURCE_AND_MODEL_CONFLICT` 和 `TIMEWINDOW_NOT_ALLOWED_ON_JOIN_UNION`，新增 `MODEL_TYPE_INVALID`；(5) §10 验收标准：DSL 测试脚本迁移由实现 PR 同步完成；(6) §12 决策 Checklist 重订为 D1-D8 |
| 2026-04-26 | 修订 v4 | `.union(other, opt)` 数组重载：`other` 多态化为 `QueryPlan \| QueryPlan[]`，多元 union 推荐数组形态 `planA.union([B, C, D], opt)`，链式形态保留为等价写法；§4.1 形态表加多态说明；§10 / §11.2 加双端对等 + 实现成本（极小，左结合折叠到现有 `UnionPlan` 二元 AST，零 AST 改动） |
