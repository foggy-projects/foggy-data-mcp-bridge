# Compose Query · DSL 配置式手册（Manual A）

> **状态**：8.x 同步版 · 已同步 timeWindow、CTE / 派生 / Join / Union；底层窗口原语与 SQL 预览仍为规划项
> **风格定位**：以 `dsl({...})` 为唯一对外入口的 JSON 配置式查询 DSL；面向 AI 生成、JSON-first 分析师、声明式偏好
> **镜像手册**：[链式 API 手册（Manual B）](./api-manual.md)
> **缺口跟踪**：`docs/8.3.0.beta/compose-query-manuals-gap-tracker.md`
> **遗留文档**：[query-dsl.md](../tm-qm/query-dsl.md)（基础查询历史参考，未涵盖 CTE / 派生 / 时间窗口）

::: tip 关于"🚧 待补"标记
本手册已补齐 8.x 已验收的 timeWindow、CTE / 派生 / Join / Union 口径。看到 🚧 表示该章节仍属于未公开或未定稿能力，可在 `docs/8.3.0.beta/compose-query-manuals-gap-tracker.md` 中查看上下文与目标版本。
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

完整验证结论见 `docs/8.3.0.beta/compose-query-manuals-gap-tracker.md` 的 G7 closure note。
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
- plan 对象上仍提供 `.join() / .union()` 等数据流方法；派生查询使用 `dsl({model: prevPlan, ...})`（详见 §5-§7）

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

#### Plain alias-only 形态语义（v2-patch-2）

`{field, as}` 不带 `agg` 时（plain alias-only），引擎将其合成为请求级 `CalculatedFieldDef(origin=PLAIN_ALIAS)`，等价于 SQL `base AS alias`。元数据自动从 base 字段继承：

| 维度 | 行为 |
|------|------|
| **type** | 自动继承 base 字段类型（如 base 是 `MONEY`，alias 也是 `MONEY`） |
| **caption** / label | 从 base 字段拷贝（如 `salesAmount` 的 caption "销售金额"，alias 后仍是 "销售金额"） |
| **description** | 从 base 字段拷贝 |
| **formatter** | 由 type 自动派生 |

```javascript
// 简单重命名 — 类型 / caption 全继承
{ field: "salesAmount", as: "revenue" }
// → SQL: t0.sales_amount AS revenue
// → caption: "销售金额"（从 salesAmount 继承）

// chain rename — 链式 alias 也支持
columns: [
  { field: "salesAmount", as: "x" },
  { field: "x", as: "y" }
]
// → SELECT t0.sales_amount AS x, t0.sales_amount AS y FROM ...
```

#### 限制：维度后缀字段不能用 plain alias

`{field, as}` 中 `field` 含 `$` 后缀（维度成员）时**不支持**，引擎抛 `COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED`。维度成员的结构化语义在 calc field 路径会扁平化丢失，强制使用基础字段或在 G11/G12 框架内处理。

```javascript
// ❌ 不支持
{ field: "product$id", as: "productId" }   // → COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED

// ✅ 直接使用维度成员
"product$id"
```

#### 错误码（F4 校验）

| 错误码 | 触发条件 |
|--------|---------|
| `COLUMN_FIELD_REQUIRED` | 对象形态缺 `field` 或 `field` 非字符串 / 空白 |
| `COLUMN_AGG_NOT_SUPPORTED` | `agg` 不在白名单（`sum/avg/count/max/min/count_distinct`） |
| `COLUMN_AS_TYPE_INVALID` | `as` 不是字符串 |
| `COLUMN_FIELD_INVALID_KEY` | 对象包含未知键 |
| `COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED` | plain alias `{field, as}` 中 `field` 含 `$` 后缀 |
| `COLUMN_ALIAS_COLLIDES_WITH_CALCULATED_FIELD` | plain alias 命中已有 calc field 名（QM 预定义或 request.calculatedFields） |
| `COLUMN_ALIAS_COLLIDES_WITH_PHYSICAL_FIELD` | plain alias 命中 QM 物理字段名（防止静默 shadow） |
| `COLUMN_ALIAS_DUPLICATE` | 同请求多列 alias 重复 |
| `COLUMN_FIELD_NOT_FOUND` | plain alias 的 base 字段在 QM 不存在（错误信息包含 alias 视角） |

🚧 **F5 待补**：plan-qualified 形态 `{plan: <ref>, field, as}`（用于 join 后置消歧）—— Java 引擎链路已落地（G10 PR2/PR3/PR4），当前 `g10Enabled()` 默认 OFF；用户级开放等待默认值切换决策 + Python PR5 跨语言对齐。如临时遇到 join 后重名冲突，请用源 plan 构造期 rename（即 `"name AS alias"`）。详见 `docs/8.3.0.beta/P0-SemanticDSL-列项对象语法-后置消歧设计.md`。

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
| 高阶函数 | 详见 `docs/v1.4/REQ-FORMULA-EXTEND-non-aggregation-functions-需求.md`：`if(c, a, b)`, `v in (...)`, `&&`, `\|\|`, `!`, 比较运算, `is_null`, `is_not_null`, `between`, `date_diff`, `date_add`, `now` |

完整 formula 语法参考 v1.4 spec；本手册不重复罗列。

### 4.3 引用规则

- 计算字段定义后可在 `columns` / `slice` / `orderBy` / `groupBy` 中**按 `name` 引用**（如普通字段）
- 计算字段**可引用其他已定义的计算字段**（递归依赖会展开到基础列）
- **禁止循环依赖**——引擎检测到会报错
- 表达式**依赖级列权限校验**：`fieldAccess` / `deniedColumns` 会按表达式实际引用的基础字段做白名单校验，无法解析的表达式 fail-closed

在 `timeWindow` 上下文中，执行顺序固定为：

1. 先按基础度量和 `targetMetrics` 展开时间窗口
2. 再在外层投影执行后置标量 `calculatedFields`

因此：

- `timeWindow.targetMetrics` 只能引用 QM 原生聚合度量，不能引用请求内 `calculatedFields`
- 后置 `calculatedFields` 可以引用 timeWindow 输出列，例如 `salesAmount__prior`、`salesAmount__diff`、`salesAmount__ratio`
- 后置 `calculatedFields` 只能做标量表达式，不允许再声明 `agg`、`windowFrame`、`partitionBy`、`windowOrderBy` 等窗口或聚合语义
- 如果字段权限拒绝了表达式依赖的基础列，整体 fail-closed

示例：

```javascript
dsl({
  model: "FactSalesQueryModel",
  columns: ["salesDate$id", "salesAmount", "客单价同比变化"],
  groupBy: ["salesDate$id"],
  timeWindow: {
    field: "salesDate$id",
    grain: "month",
    comparison: "yoy",
    value: ["2025-01-01", "2026-01-01"],
    targetMetrics: ["salesAmount"]
  },
  calculatedFields: [
    {
      name: "客单价同比变化",
      expression: "salesAmount__ratio * 100"
    }
  ]
});
```

---

## 5. 派生查询：`dsl({ model: prevPlan, ... })`

派生查询把上一个 `QueryPlan` 当作新的输入模型，再继续过滤、投影、聚合或排序。公开 DSL 形态使用 `model: prevPlan`；早期实验里的 `source` 写法不要在新文档和新用法中继续扩散。

```javascript
const base = dsl({
  model: "FactSalesQueryModel",
  columns: ["product$id", "sum(salesAmount) as totalSales"],
  groupBy: ["product$id"]
});

const filtered = dsl({
  model: base,
  columns: ["product$id", "totalSales"],
  slice: [{ field: "totalSales", op: ">", value: 100000 }],
  orderBy: [{ field: "totalSales", dir: "desc" }]
});
```

派生查询的列空间来自上一层 plan 的输出 schema：

- 只能引用上一层已经投影出来的列
- 不能越过上一层直接引用底层模型字段
- 聚合后的别名会成为下一层可引用字段
- 字段治理仍然按表达式依赖的底层字段追溯校验

常见错误：

| 错误码 | 触发条件 |
|--------|---------|
| `MODEL_TYPE_INVALID` | `model` 既不是 QM 名称，也不是合法 `QueryPlan` |
| `DERIVED_REFERENCE_NOT_PROJECTED` | 派生层引用了上一层未投影的列 |

---

## 6. Join：`plan.join(...)`

Join 的公开入口是 plan 组合方法 `.join()`。8.x 设计已明确不提供 `dsl({ join: ... })` 配置式入口，避免在 JSON 对象中引入过多计划图结构。

```javascript
const salesByProduct = dsl({
  model: "FactSalesQueryModel",
  columns: ["product$id", "sum(salesAmount) as totalSales"],
  groupBy: ["product$id"]
});

const returnsByProduct = dsl({
  model: "FactReturnQueryModel",
  columns: ["product$id", "sum(returnAmount) as totalReturns"],
  groupBy: ["product$id"]
});

const joined = salesByProduct.join(returnsByProduct, "inner", [
  { left: "product$id", op: "=", right: "product$id" }
]);
```

约束：

- join 类型支持 `inner` / `left` / `right` / `full`；`full` 受 SQL 方言能力限制，不支持时 fail-closed 或降级由编译器策略决定
- `on` 条件必须是等值条件，可写多键
- 不支持公开非等值 join、自连接、跨数据源 join
- 当左右两侧存在同名非键列时，必须通过上游投影 alias 消除歧义

常见错误：

| 错误码 | 触发条件 |
|--------|---------|
| `JOIN_ON_REQUIRED` | 缺少 join 条件 |
| `JOIN_CROSS_DATASOURCE` | 左右 plan 来自不同数据源 |
| `JOIN_AMBIGUOUS_COLUMN` | join 后输出列名冲突且无法推断 |
| `JOIN_TYPE_UNSUPPORTED` | 当前方言不支持请求的 join 类型 |

---

## 7. Union：`plan.union(...)`

Union 的公开入口是 plan 组合方法 `.union()`。8.x 设计已明确不提供 `dsl({ union: [...] })` 配置式入口。

```javascript
const online = dsl({
  model: "OnlineOrderQueryModel",
  columns: ["orderNo", "customer$id", "salesAmount"]
});

const offline = dsl({
  model: "OfflineOrderQueryModel",
  columns: ["orderNo", "customer$id", "salesAmount"]
});

const allOrders = online.union(offline, { all: true });
```

也可以一次合并多个 plan：

```javascript
const allOrders = online.union([offline, partner], { all: true });
```

约束：

- 默认按 `UNION` 去重；`{ all: true }` 对应 `UNION ALL`
- 列按左侧 plan 的输出 schema 对齐
- 所有分支的列数和兼容类型必须一致
- Union 之后可继续作为派生 `model` 输入做过滤、排序或聚合

常见错误：

| 错误码 | 触发条件 |
|--------|---------|
| `UNION_COLUMN_COUNT_MISMATCH` | 分支列数不一致 |
| `UNION_COLUMN_TYPE_MISMATCH` | 对齐列类型不兼容 |
| `UNION_EMPTY_BRANCH` | 没有可合并分支 |

---

## 8. CTE 复用与命名 plans

CTE 是编译器内部优化，不是用户显式声明语法。多次引用同一个 `QueryPlan` 时，引擎会自动识别公共子计划，在支持 CTE 的方言上编译为 `WITH ... AS (...)`；不支持或不适合 CTE 的场景会退回派生子查询。

```javascript
const productSales = dsl({
  model: "FactSalesQueryModel",
  columns: ["product$id", "sum(salesAmount) as totalSales"],
  groupBy: ["product$id"]
});

const highValue = dsl({
  model: productSales,
  columns: ["product$id", "totalSales"],
  slice: [{ field: "totalSales", op: ">", value: 100000 }]
});

const ranked = dsl({
  model: productSales,
  columns: ["product$id", "totalSales"],
  orderBy: [{ field: "totalSales", dir: "desc" }],
  limit: 20
});
```

上例中 `productSales` 被两个下游 plan 复用，编译器可把它提升为公共 CTE。

公开边界：

- 用户不需要、也不能手工指定 CTE 名称
- 顶层返回多个 plan 时，业务侧的 plan 名称只用于结果映射，不等于 SQL CTE 名称
- 不支持递归 CTE
- 不支持把任意 SQL 文本注入为 CTE

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
| `value` | `[string, string]` | ⬜ | 当前期间 `[start, end]`；不传则由查询条件和引擎窗口规则决定。传入时必须恰好两个值，支持绝对日期（`"2024-01-01"`）和相对表达式（`"now"`, `"-1Y"`, `"-7D"`） |
| `range` | `"[)" \| "[]"` | ⬜ | 开闭规则，默认 `"[)"`（左闭右开）；其他值报 `TIMEWINDOW_RANGE_INVALID` |
| `targetMetrics` | `string[]` | ⬜ | 应用窗口的度量字段名；留空则作用于所有度量 |
| `rollingAggregator` | `"sum" \| "avg" \| "count" \| "min" \| "max"` | ⬜ | rolling / YTD / MTD 模式下的窗口聚合覆盖；不填默认 `sum` |

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
3. 如果提供 `value`，必须恰好两个值，且都是合法日期或相对表达式
4. `targetMetrics` 中列出的字段是聚合度量（不是普通维度）

---

## 10. 时间窗口原语层（底层窗口函数）

🚧 **待补**：参考 `docs/8.3.0.beta/compose-query-manuals-gap-tracker.md` 的 G3。

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
| 派生 | `dsl({model: prev, ...})` | `prev.where(...).select(...)` |
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
| 2026-05-03 | 同步 8.x 已验收能力 | 补齐 timeWindow + calculatedFields 契约，以及派生查询 / Join / Union / CTE 复用的公开 DSL / plan 组合口径 |
