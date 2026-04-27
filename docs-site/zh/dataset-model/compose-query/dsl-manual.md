# Compose Query · DSL 配置式手册（Manual A）

> **状态**：Working draft · §1-§4 / §9 / §11 已落稿，其余 🚧 等 spec 收口
> **风格定位**：以 `dsl({...})` 为唯一对外入口的 JSON 配置式查询 DSL；面向 AI 生成、JSON-first 分析师、声明式偏好
> **镜像手册**：[链式 API 手册（Manual B）](./api-manual.md)
> **缺口跟踪**：[compose-query-manuals-gap-tracker.md](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md)
> **遗留文档**：[query-dsl.md](../tm-qm/query-dsl.md)（基础查询历史参考，未涵盖 CTE / 派生 / 时间窗口）

::: tip 关于"🚧 待补"标记
本手册采用骨架先行策略，章节标题已固定，能力随 spec 补齐分批落稿。看到 🚧 表示对应章节有未关闭的 gap，按编号跳到 [gap tracker](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md) 查看上下文与目标版本。
:::

::: info 决策契约
- **决策 1（功能对齐 ≠ 形态对齐）**：本手册与链式 API 手册**功能必须等价**，但写法可不同——见 gap tracker 中的 Layer 1 / Layer 2 划分
- **决策 2（骨架先行）**：能力补齐过程中所有缺口在 gap tracker 留档，本文不直接吞掉
- **决策 3（query-dsl.md deprecated）**：旧的 1193 行 query-dsl.md 保留为遗留参考，新功能一律以本手册为准
:::

::: tip DSL 是 first-class 入口（架构验证 2026-04-26）
**DSL 配置式与链式 API 在 IR 层完全独立**——两者各自构造同一份 `QueryPlan` AST，互不调用、互不依赖。这意味着：

- 使用 DSL 的代码**不需要**理解链式 API 即可表达完整能力
- timeWindow / 派生 / Join / Union / 计算字段 等高阶能力由 DSL 编译器**直接展开为 IR**，零绕路
- 未来如整体移除链式 API（仅保留 DSL），本手册的契约**不发生任何变化**

完整验证结论见 [G7](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md#g7--dsl-与链式-api-是否互不依赖架构验证--closed)。
:::

---

## 1. 入门：`dsl({...})` 单步查询

### 1.1 最小示例

```javascript
const sales = dsl({
  model: "FactSalesQueryModel",
  columns: ["product$id", "product$caption", "salesAmount"],
  slice: [
    { field: "salesDate$id", op: ">=", value: "2025-01-01" }
  ],
  groupBy: ["product$id"],
  orderBy: [{ field: "salesAmount", dir: "desc" }],
  limit: 100
});

return {
  plans: { top_sales: sales },
  metadata: { title: "Top sales by product" }
};
```

### 1.2 顶层字段全集

| 字段 | 类型 | 必填 | 语义 |
|------|------|------|------|
| `model` | string | ✅ | QM 模型名（如 `OdooSaleOrderModel`） |
| `columns` | `string[]` | ⬜ | 输出列清单。支持 `attr` / `dim$id` / `dim$caption` / `dim$attr` / `dim.nested$attr` / `expr AS alias` |
| `slice` | `SliceItem[]` | ⬜ | 过滤条件（等价 SQL WHERE）。支持 `$or` / `$and` 嵌套；详见 §3.1 算子目录 |
| `groupBy` | `(string \| {field, agg})[]` | ⬜ | 分组维度，简写 `"field"` 或对象 `{field, agg: "SUM"}` |
| `orderBy` | `(string \| {field, dir})[]` | ⬜ | 排序，简写 `"-field"`(desc) / `"field"`(asc)，或对象 `{field, dir}` |
| `calculatedFields` | `CalculatedFieldDef[]` | ⬜ | 计算字段定义；详见 §4 |
| `timeWindow` | object | ⬜ | 时间窗口高层语义（yoy/mom/ytd/rolling 等）；详见 §9 |
| `limit` | number | ⬜ | 返回条数上限 |
| `start` | number | ⬜ | 分页起始位置（默认 0） |
| `returnTotal` | boolean | ⬜ | 是否在结果中附带总记录数（用于分页） |
| `distinct` | boolean | ⬜ | `SELECT DISTINCT`，与 `groupBy` 互斥 |
| `hints` | object | ⬜ | 引擎提示，普通用户无需关心；引擎自动追加 `fromCompose: true` |

::: warning 字段命名差异（DSL vs 链式 API）
- DSL 使用 `slice`（不是 `where`）作为过滤条件字段
- 链式 API 使用 `.where([...])` 方法
- **语义完全等价**——这是决策 1（功能对齐 ≠ 形态对齐）的体现
:::

### 1.3 执行语义

- `dsl({...})` 返回一个 **plan 对象**（QueryPlan AST 节点），**未立即执行**
- 在脚本末尾通过 `return { plans: {name: plan}, metadata: {...} }` 暴露给宿主
- 引擎按需将每个 plan 编译为 SQL 并在数据源上执行
- plan 对象上仍提供 `.join() / .union()` 等数据流方法，可与 `dsl({source: prev, ...})` 配置式互换使用（详见 §6 / §7，待 G2 收口）

### 1.4 与链式 API 的对应关系（速览）

| DSL（本手册） | 链式 API（Manual B） |
|--------------|---------------------|
| `dsl({model: "X"})` | `Query.from("X")` |
| `dsl({slice: [...]})` | `.where([...])` |
| `dsl({columns: [...]})` | `.select(base.col, ...)` |
| `dsl({groupBy: [...]})` | `.groupBy(...)` |

完整互译表见 [Manual A · 附录 A](#附录-a-dsl--链式-api-互译表) / [Manual B · 附录 A](./api-manual.md#附录-a-链式-api--dsl-互译表)（两份手册都达 §1-§11 完整后写）。

---

## 2. 列与维度引用约定

### 2.1 引用形式速查

| 场景 | 形态 | 例子 |
|------|------|------|
| 事实表属性 / 度量 | 裸名（字符串） | `"orderAmount"`, `"salesAmount"` |
| 显式聚合 | `AGG(field)` 表达式 | `"SUM(amount)"`, `"COUNT(*)"`, `"AVG(price)"` |
| 维度 ID（surrogate key） | `dim$id` | `"product$id"`, `"customer$id"` |
| 维度显示名（caption） | `dim$caption` | `"product$caption"`, `"team$caption"` |
| 维度属性 | `dim$attr` | `"customer$customerType"`, `"product$brand"` |
| 嵌套维度 | `dim.child$attr` | `"product.category$caption"`, `"order.customer.region$id"` |
| 内联表达式别名 | `expr AS alias` | `"YEAR(orderDate) AS orderYear"` |

### 2.2 别名复用（迁移到 §2.5 · 见 §2.4 末尾）

### 2.3 输出列名映射规则

引擎返回结果时，**字段路径中的 `.` 转为 `_`**：

| 输入 | 输出列名 |
|------|---------|
| `"product$id"` | `product$id`（不变） |
| `"product.category$caption"` | `product_category$caption` |
| `"order.customer.region$id"` | `order_customer_region$id` |

### 2.4 列项对象语法（F4 · G5 Phase 1）

除字符串短写形态外，`columns` 数组也接受**对象形态** `{field, agg?, as?}`，用于显式表达聚合 + alias，避免依赖字符串拼接：

```javascript
dsl({
  model: "FactSalesQueryModel",
  columns: [
    "product$id",                                          // F1 字符串短写
    { field: "salesAmount", agg: "sum", as: "totalSales" }, // F4 对象（显式聚合）
    { field: "amount", agg: "avg", as: "avgPrice" },       // F4 对象
    { field: "orderDate", agg: "max", as: "lastOrder" },   // F4 对象（非数值聚合）
    "YEAR(orderDate) AS orderYear"                         // F3 字符串（函数表达式仍用字符串）
  ],
  groupBy: ["product$id"]
});
```

#### F4 字段定义

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `field` | string | ✅ | 列名 / 别名 / 维度后缀（如 `"product$id"` / `"customer_name"`） |
| `agg` | `"sum" \| "avg" \| "count" \| "max" \| "min" \| "count_distinct"` | ⬜ | 聚合函数；未指定时不聚合 |
| `as` | string | ⬜ | 输出列别名 |

#### 字符串 ↔ 对象等价映射

| 字符串形态 | 对象形态等价 |
|-----------|------------|
| `"name"` | `{ field: "name" }` |
| `"name AS alias"` | `{ field: "name", as: "alias" }` |
| `"SUM(amount) AS total"` | `{ field: "amount", agg: "sum", as: "total" }` |
| `"COUNT(*) AS cnt"` | `{ field: "*", agg: "count", as: "cnt" }`（特殊场景，仍推荐字符串） |
| `"YEAR(orderDate) AS year"` | （**无对象等价** · 函数表达式仍走字符串） |

#### count_distinct 特殊说明

`agg: "count_distinct"` 自动 lower 为 SQL `COUNT(DISTINCT field)`：

```javascript
{ field: "customer_id", agg: "count_distinct", as: "uniqueCustomers" }
// → SQL: COUNT(DISTINCT customer_id) AS uniqueCustomers
```

#### 错误码（F4 校验）

| 错误码 | 触发条件 |
|--------|---------|
| `COLUMN_FIELD_REQUIRED` | 对象形态缺 `field` 或 `field` 非字符串 / 空白 |
| `COLUMN_AGG_NOT_SUPPORTED` | `agg` 不在白名单（`sum/avg/count/max/min/count_distinct`） |
| `COLUMN_AS_TYPE_INVALID` | `as` 不是字符串 |
| `COLUMN_FIELD_INVALID_KEY` | 对象包含未知键 |

🚧 **待补**：plan-qualified 形态 `{plan: <ref>, field, as}`（F5 · 用于 join 后置消歧）—— 当前为 Phase 2，硬阻塞于 [G10](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md#g10) 引擎前置改造。如临时遇到 join 后重名冲突，请用源 plan 构造期 rename（即 `"name AS alias"`）；G10 落地后会开放 `{plan, field, as}` 后置消歧路径。详见 [G5 spec v2](../../../../docs/8.3.0.beta/P0-SemanticDSL-列项对象语法-后置消歧设计.md)。

### 2.5 别名复用

`columns` 中通过 `AS`、F4 对象的 `as`、或 `calculatedFields` 中通过 `name` 定义的别名，可在**同一 plan 后续位置**引用：

```javascript
dsl({
  model: "FactSalesQueryModel",
  columns: ["YEAR(orderDate) AS orderYear", "SUM(amount) AS totalSales"],
  groupBy: ["orderYear"],            // ← 引用别名 orderYear
  orderBy: [{ field: "totalSales", dir: "desc" }]   // ← 引用别名 totalSales
});
```

---

## 3. 过滤 / 分组 / 排序 / 分页

### 3.1 `slice` 算子目录

| 类别 | 算子 | value 类型 | 例子 |
|------|------|-----------|------|
| 比较 | `=` | any | `{field: "status", op: "=", value: "ACTIVE"}` |
| 比较 | `!=` | any | `{field: "status", op: "!=", value: "DELETED"}` |
| 比较 | `<` `<=` `>` `>=` | number / date | `{field: "amount", op: ">=", value: 100}` |
| 集合 | `in` | array | `{field: "type", op: "in", value: ["A", "B"]}` |
| 集合 | `not in` | array | `{field: "type", op: "not in", value: ["X"]}` |
| 空值 | `is null` | （无） | `{field: "phone", op: "is null"}` |
| 空值 | `is not null` | （无） | `{field: "phone", op: "is not null"}` |
| 区间 | `[]` 全闭 | `[min, max]` | `{field: "date", op: "[]", value: ["2024-01-01", "2024-12-31"]}` |
| 区间 | `[)` 左闭右开 | `[min, max]` | `{field: "date", op: "[)", value: ["2024-01-01", "2025-01-01"]}` |
| 区间 | `(]` 左开右闭 | `[min, max]` | `{field: "qty", op: "(]", value: [0, 100]}` |
| 区间 | `()` 全开 | `[min, max]` | `{field: "qty", op: "()", value: [0, 100]}` |
| 模糊 | `like` | string | `{field: "name", op: "like", value: "Smith"}` |
| 模糊 | `left_like` | string | `{field: "name", op: "left_like", value: "Smith"}`（前缀匹配） |
| 模糊 | `right_like` | string | `{field: "name", op: "right_like", value: "John"}`（后缀匹配） |
| 层级 | `childrenOf` | string (id) | `{field: "team$id", op: "childrenOf", value: "T001"}`（直接子节点） |
| 层级 | `descendantsOf` | string (id) | `{field: "team$id", op: "descendantsOf", value: "T001"}`（所有后代，不含自身） |
| 层级 | `selfAndDescendantsOf` | string (id) | `{field: "team$id", op: "selfAndDescendantsOf", value: "T001"}` |
| 层级 | `ancestorsOf` | string (id) | `{field: "team$id", op: "ancestorsOf", value: "T999"}`（所有祖先，不含自身） |
| 层级 | `selfAndAncestorsOf` | string (id) | `{field: "team$id", op: "selfAndAncestorsOf", value: "T999"}` |

### 3.2 逻辑组合：`$or` / `$and`

```javascript
slice: [
  { $or: [
    { field: "status", op: "=", value: "PENDING" },
    { field: "status", op: "=", value: "PROCESSING" }
  ]},
  { field: "amount", op: ">", value: 1000 }   // 顶层数组隐式 AND
]
```

### 3.3 字段间比较：`$field`

```javascript
slice: [
  { field: "actualEndDate", op: ">", value: { $field: "plannedEndDate" } }
]
// 等价 SQL: actualEndDate > plannedEndDate
```

### 3.4 `groupBy`

```javascript
// 简写
groupBy: ["customer$id", "product$category"]

// 完整对象
groupBy: [
  { field: "customer$id" },
  { field: "totalAmount", agg: "SUM" }
]
```

### 3.5 `orderBy`

```javascript
// 简写（- 前缀表示 desc）
orderBy: ["-salesAmount", "orderId"]

// 完整对象
orderBy: [
  { field: "salesAmount", dir: "desc" },
  { field: "orderId", dir: "asc" }
]
```

### 3.6 分页

```javascript
{
  start: 0,            // 起始偏移（从 0 开始）
  limit: 20,           // 每页大小
  returnTotal: true    // 是否在结果元信息中附带总记录数
}
```

---

## 4. 计算字段

### 4.1 顶层 `calculatedFields` 数组

计算字段在**顶层 `calculatedFields` 数组**中定义（**不**在 `columns` 中内联）：

```javascript
dsl({
  model: "FactSalesQueryModel",
  calculatedFields: [
    {
      name: "profitRate",
      caption: "利润率(%)",
      expression: "profit / sales * 100",
      agg: "SUM"          // 该计算字段被聚合时使用的聚合函数
    },
    {
      name: "netAmount",
      expression: "salesAmount - returnAmount"
    }
  ],
  columns: ["product$id", "profitRate", "netAmount"],   // ← 像普通字段一样引用
  slice: [{ field: "profitRate", op: ">", value: 20 }],  // ← slice 也能引用
  groupBy: ["product$id"]
});
```

### 4.2 表达式支持的运算与函数

| 类别 | 内容 |
|------|------|
| 算术 | `+ - * / %` |
| 数学函数 | `ABS`, `ROUND`, `SQRT`, `POWER` |
| 日期函数 | `YEAR`, `MONTH`, `DATE_ADD`, `DATEDIFF` |
| 字符串函数 | `CONCAT`, `SUBSTRING`, `UPPER`, `LOWER`, `LENGTH` |
| 聚合函数 | `SUM`, `AVG`, `COUNT`, `MAX`, `MIN` |
| 条件函数 | `COALESCE`, `IFNULL` |
| 高阶函数 | 详见 [REQ-FORMULA-EXTEND v1.4](../../../../foggy-data-mcp-bridge/docs/v1.4/REQ-FORMULA-EXTEND-non-aggregation-functions-需求.md)：`if(c, a, b)`, `v in (...)`, `&&`, `\|\|`, `!`, 比较运算, `is_null`, `is_not_null`, `between`, `date_diff`, `date_add`, `now` |

完整 formula 语法参考 v1.4 spec；本手册不重复罗列。

### 4.3 引用规则

- 计算字段定义后可在 `columns` / `slice` / `orderBy` / `groupBy` 中**按 `name` 引用**（如普通字段）
- 计算字段**可引用其他已定义的计算字段**（递归依赖会展开到基础列）
- **禁止循环依赖**——引擎检测到会报错
- 表达式**依赖级列权限校验**：`fieldAccess` / `deniedColumns` 会按表达式实际引用的基础字段做白名单校验，无法解析的表达式 fail-closed

🚧 **待补**：计算字段在 timeWindow 上下文里的语义（"客单价同比"等联动场景）。参考 [G6](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md#g6--计算字段在-timewindow-上下文里的语义)。

---

## 5. 派生查询：`dsl({ source: prevPlan, ... })`

🚧 **待补**：参考 [G2](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md#g2--dsl-配置缺完整-cte--派生--join--union-语法)

**已知现状**（来自测试脚本，非正式 spec）：

```javascript
const base = dsl({
  model: "FactSalesQueryModel",
  columns: ["product$id", "salesAmount"],
  groupBy: ["product$id"]
});

const filtered = dsl({
  source: base,                                      // ← 实际可用，但未正式 spec
  columns: ["product$id", "salesAmount", "returnAmount"]
});
```

待 G2 收口的事项：
- `source` 字段的正式 spec
- 派生查询的 schema 推导规则
- 列引用是否支持 base.alias / base.column 两种形态
- 与 plan 方法 `prev.where(...).select(...)` 的等价关系

---

## 6. Join：`dsl({ join: ... })` 或 `plan.join(...)`

🚧 **待补**：参考 [G2](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md#g2--dsl-配置缺完整-cte--派生--join--union-语法)

**已知现状**（来自测试脚本）：plan 对象上提供 `.join()` 方法：

```javascript
const salesByProduct = dsl({...});
const returnsByProduct = dsl({...});

const joined = salesByProduct.join(returnsByProduct, "inner", [
  { left: "product$id", op: "=", right: "product$id" }
]);
```

待 G2 收口：纯配置式 `dsl({join: {...}})` 形态、多键 join、非等值 join、self-join。

---

## 7. Union：`dsl({ union: [...] })`

🚧 **待补**：参考 [G2](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md#g2--dsl-配置缺完整-cte--派生--join--union-语法)

待决策：列对齐策略（按名 / 按位置）、`unionAll` 是否独立入口。

---

## 8. CTE 复用与命名 plans

🚧 **待补**：参考 [G2](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md#g2--dsl-配置缺完整-cte--派生--join--union-语法)

要点（待 spec 化）：多次引用同一 plan 时引擎自动 CTE 化；顶层 `return { plans: { name1, name2, ... } }` 多 plan 返回机制。

---

## 9. 时间窗口语义层（高层快捷方式）

### 9.1 顶层 `timeWindow` 对象

```javascript
dsl({
  model: "FactSalesQueryModel",
  columns: ["salesDate$id", "salesAmount"],
  groupBy: ["salesDate$id"],
  timeWindow: {
    field: "salesDate$id",
    grain: "month",
    comparison: "yoy",
    value: ["2024-01-01", "2025-01-01"],
    targetMetrics: ["salesAmount"]
  }
});
```

### 9.2 `timeWindow` 字段全集

| 字段 | 类型 | 必填 | 语义 |
|------|------|------|------|
| `field` | string | ✅ | 时间轴字段（`dim$id` 或裸属性）。**前置条件**：在 TM/QM 中标记 `timeRole = business_date` |
| `grain` | enum | ✅ | 对齐粒度：`day` / `week` / `month` / `quarter` / `year` |
| `comparison` | enum | ✅ | 计算模式：`yoy` / `mom` / `wow` / `ytd` / `mtd` / `rolling_7d` / `rolling_30d` / `rolling_90d` |
| `value` | `[string, string]` | ✅ | 当前期间 `[start, end]`，支持绝对日期（`"2024-01-01"`）和相对表达式（`"now"`, `"-1Y"`, `"-7D"`） |
| `range` | `"[)" \| "[]"` | ⬜ | 开闭规则，默认 `"[)"`（左闭右开）；其他值报 `TIMEWINDOW_RANGE_INVALID` |
| `targetMetrics` | `string[]` | ⬜ | 应用窗口的度量字段名；留空则作用于所有度量 |
| `rollingAggregator` | `"sum" \| "avg" \| "count"` | ⬜ | rolling 模式下的聚合覆盖；默认继承度量原聚合 |

### 9.3 各 `comparison` 含义

| 值 | 含义 |
|----|------|
| `yoy` | Year-over-Year，去年同期对比 |
| `mom` | Month-over-Month，上月对比 |
| `wow` | Week-over-Week，上周对比 |
| `ytd` | Year-to-Date，年初至今累计 |
| `mtd` | Month-to-Date，月初至今累计 |
| `rolling_7d` | 滚动 7 天合计 |
| `rolling_30d` | 滚动 30 天合计 |
| `rolling_90d` | 滚动 90 天合计 |

### 9.4 `grain × comparison` 兼容矩阵

❌ 表示不合法组合，引擎报 `TIMEWINDOW_GRAIN_INCOMPATIBLE`。

|  | day | week | month | quarter | year |
|---|---|---|---|---|---|
| **yoy** | ❌ | ✅ | ✅ | ✅ | ✅ |
| **mom** | ❌ | ❌ | ✅ | ❌ | ❌ |
| **wow** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **ytd** | ✅ | ✅ | ✅ | ✅ | ❌ |
| **mtd** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **rolling_7d** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **rolling_30d** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **rolling_90d** | ✅ | ✅ | ❌ | ❌ | ❌ |

### 9.5 输出列后缀

参考 [§11 输出后缀规约](#11-输出后缀规约)。

### 9.6 错误码

| 错误码 | 触发条件 |
|--------|---------|
| `TIMEWINDOW_FIELD_NOT_FOUND` | `field` 在 QM 中找不到 |
| `TIMEWINDOW_FIELD_NOT_TIME` | `field` 非时间类型，或未标记 `timeRole = business_date` |
| `TIMEWINDOW_GRAIN_INCOMPATIBLE` | `grain × comparison` 组合不合法（参 §9.4） |
| `TIMEWINDOW_VALUE_PARSE_FAILED` | `value` 中的日期解析失败 |
| `TIMEWINDOW_TARGET_NOT_AGGREGATE` | `targetMetrics` 指向了非聚合字段 |
| `TIMEWINDOW_RANGE_INVALID` | `range` 取值非 `"[)"` 或 `"[]"` |

### 9.7 前置条件清单

使用 `timeWindow` 前必须满足：

1. `field` 对应的 TM 字段标注了 `timeRole = business_date`
2. 选择的 `grain` 与 `comparison` 在 §9.4 矩阵中是 ✅
3. `value` 是合法日期或相对表达式
4. `targetMetrics` 中列出的字段是聚合度量（不是普通维度）

---

## 10. 时间窗口原语层（底层窗口函数）

🚧 **待补**：参考 [G3](../../../../docs/8.3.0.beta/compose-query-manuals-gap-tracker.md#g3--双侧缺底层窗口原语暴露-lag--lead--rolling--over)

底层 IR 已实现（`OverClause` / `WindowColumn` / `WindowFrame` · v1.5 Java），但 DSL 形态未确定。待决策：列项级别 `{field, agg, over: {...}, as}` vs 顶层 `dsl({window: {...}})`。

---

## 11. 输出后缀规约

`timeWindow` 启用时，每个 `targetMetric` 会按 comparison 模式自动生成附加列。命名规则：

| 后缀 | 含义 | 适用 comparison |
|------|------|----------------|
| （无后缀） | 当期值 | 全部 |
| `__prior` | 前期值 | `yoy`, `mom`, `wow` |
| `__diff` | 差值（当期 − 前期） | `yoy`, `mom`, `wow` |
| `__ratio` | 增长率 `(当期 − 前期) / 前期`；前期为 0 / NULL 时输出 NULL | `yoy`, `mom`, `wow` |
| `__ytd` | 年初至今累计 | `ytd` |
| `__mtd` | 月初至今累计 | `mtd` |
| `__rolling_{N}{unit}` | 滚动窗口值（如 `salesAmount__rolling_30d`） | `rolling_7d` / `rolling_30d` / `rolling_90d` |

### 示例

输入：

```javascript
timeWindow: {
  field: "salesDate$id",
  grain: "month",
  comparison: "yoy",
  value: ["2024-01-01", "2025-01-01"],
  targetMetrics: ["salesAmount"]
}
```

输出列（在原 `salesAmount` 之外追加）：
- `salesAmount`（当期）
- `salesAmount__prior`（去年同期）
- `salesAmount__diff`（差值）
- `salesAmount__ratio`（同比增长率）

### Override

当前**不支持** override 默认后缀。如需自定义列名，可在结果消费侧做 alias 映射。如未来需要 override，将作为新 gap 登记。

---

## 12. 错误码与诊断

🚧 **待补**

待覆盖：
- §9.6 timeWindow 错误码（已就绪）
- 沙箱错误码（与 `docs/8.2.0.beta/P0-ComposeQuery-沙箱白名单错误码与防护用例清单.md` 对齐）
- DSL 解析阶段错误（缺字段 / 类型不匹配 / 算子不存在）
- 治理层错误（fieldAccess / deniedColumns 拒绝）

---

## 13. 真值 SQL 编译预览（4 方言）

🚧 **待补**

要覆盖：
- MySQL / PostgreSQL / MSSQL / SQLite 编译差异
- CTE / 子查询 fallback 策略（与 v1.5 P1 spec 对齐）

---

## 附录 A · DSL ↔ 链式 API 互译表

🚧 **待补**：两本手册都达 §1-§11 完整后再写

预览结构：

| 场景 | DSL（本手册） | 链式 API（Manual B） |
|------|--------------|---------------------|
| 基础查询 | `dsl({model, columns, slice})` | `Query.from(model).where(...).select(...)` |
| 派生 | `dsl({source: prev, ...})` | `prev.where(...).select(...)` |
| ... | ... | ... |

---

## 附录 B · 从 query-dsl.md 迁移

🚧 **待补**：query-dsl.md 加 deprecation banner 时同步起草

预期内容：
- 字段引用：完全继承 `$id` / `$caption` 后缀（已在本手册 §2）
- 操作符：完全继承（已在本手册 §3.1）
- 新增能力指引（CTE / 派生 / timeWindow，分别指向本手册 §5-§8 / §9）

---

## 维护记录

| 日期 | 操作 | 备注 |
|------|------|------|
| 2026-04-26 | 创建骨架 | 初始化 13 节占位，🚧 标记关联 G1-G7 |
| 2026-04-26 | §1-§4 / §9 / §11 落稿 | 路径 A 第一批：基础查询 / 字段引用 / 算子 / 计算字段 / 时间窗口 / 后缀规约。剩余 §5-§8 / §10 / §12-§13 等 G2-G6 spec 收口 |
| 2026-04-27 | §2.4 F4 列项对象语法落稿 | G5 Phase 1 (F4) 实施完成：`{field, agg?, as?}` 对象形态 + 6 个聚合白名单（含 `count_distinct` lowering）+ 4 个错误码（`COLUMN_FIELD_REQUIRED` / `COLUMN_AGG_NOT_SUPPORTED` / `COLUMN_AS_TYPE_INVALID` / `COLUMN_FIELD_INVALID_KEY`）+ 字符串 ↔ 对象等价映射表。F5 plan-qualified 形态保留 🚧（依赖 G10） |
