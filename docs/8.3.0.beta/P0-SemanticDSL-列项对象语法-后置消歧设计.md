# P0 · SemanticDSL · columns 列项对象语法 + 后置消歧设计（G5 v2）

> **状态**：Draft v2 for review
> **目标版本**：8.3.0.beta
> **关联 gap**：[G5](compose-query-manuals-gap-tracker.md#g5)（v2 已缩窄到 columns 范围）
> **前置依赖**：[G10](compose-query-manuals-gap-tracker.md#g10)（compose 引擎前置改造，硬阻塞 F5）
> **关联 spec**：[G2 spec v4](P0-SemanticDSL-CTE-派生-Join-Union-语法设计.md)（`model` 多态 + combinator 唯一形态）
> **创建日期**：2026-04-26 · **修订日期**：2026-04-26（v1 → v2）

## 0. 摘要

本 spec 为 SemanticDSL 的 **`columns` 字段**补全两类对象形态：

1. **F4 基本对象** `{field, agg?, as?}` —— 显式表达聚合 + alias，避免依赖 `"SUM(x) AS y"` 字符串拼接
2. **F5 plan-qualified** `{plan, field, agg?, as?}` —— 通过 plan 引用解决 join 后重名歧义（**G2 §3.4 标记的 LLM 自我修复硬需求**）

v2 范围**仅限于 `columns`**——v1 中规划的 `groupBy` / `orderBy` / `slice` 三个位置已分别移交给 [G11](compose-query-manuals-gap-tracker.md#g11) / [G12](compose-query-manuals-gap-tracker.md#g12) 后续处理（涉及 `List<String>` 类型迁移和 `SliceShape` 字符串强转修复，工作量超出 G5 单 spec 合理范围）。

**核心策略**：

- **F4 立即可做（Phase 1）**：`columns` 已是 `List<Object>`，无 AST 改动，纯加性扩展
- **F5 阻塞于 G10（Phase 2）**：现有引擎在 join schema 阶段就拒绝重名输出（`JOIN_OUTPUT_COLUMN_CONFLICT`）、并在派生后清空 plan provenance；F5 必须等 G10 的引擎前置改造落地

## 1. 背景与原则

### 1.1 v1 → v2 修订动机

v1 (2026-04-26) 收到代码核实层面的 ❌ 评估，5 项代码事实推翻了 v1 的核心假设：

| 编号 | v1 假设 | 代码事实 | 影响 |
|------|--------|---------|------|
| C1 | F5 在 join 后消歧 | `SchemaDerivation.java:212-223` 在 join schema 派生时就抛 `JOIN_OUTPUT_COLUMN_CONFLICT` | F5 没机会触发 → 必须先改 schema 派生 |
| C2 | `PlanColumnRef.plan` 在管道中可用 | `SchemaDerivation.java:225-234` 显式 `withSourceModelCleared(c)`；`ComposePlanner.java:255` 编译时丢弃 `ref.plan()` | F5 plan 引用全程被忽略 → 必须改 schema/compiler |
| C3 | F5 在 4 位置统一适用 | `BaseModelPlan.java:24-25` `groupBy/orderBy` 是 `List<String>`；Python 同 | groupBy/orderBy 需要类型迁移 |
| C4 | slice 接受 plan-qualified | `SliceShape.java:22-66` `String.valueOf(entry.get("field"))` 强转字符串 | slice 字段无法存对象 |
| C5 | "按 `plan.<field>` 路径校验" 与 fieldAccess 协同 | `FieldAccessPermissionStep.java:44-83` 假设统一 QM 命名空间，未支持 plan 路由 | 措辞错误 + 权限框架需升级 |

### 1.2 v2 的核心修订

| 修订 | v1 | v2 |
|------|-----|-----|
| **范围** | F4/F5 在 4 类位置统一 | **仅 columns** F4/F5；slice/groupBy/orderBy 移至 G11/G12 |
| **F5 落地路径** | "spec 落地后引擎自动支持" | 显式声明依赖 [G10](compose-query-manuals-gap-tracker.md#g10) 引擎前置改造；F5 是 Phase 2，G10 是 Phase 0 硬前置 |
| **F4 落地路径** | 与 F5 一同评估 | 拆分为 Phase 1，可与 G10 并行立即推进（columns `List<Object>` 已就绪） |
| **权限协同表述** | "按 `plan.<field>` 路径校验" | "F5 `plan` 引用解析到 lineage/binding，沿用现有 PerBaseCompiler 的 per-BaseModelPlan ModelBinding 校验，不引入新权限路径" |
| **验收标准** | 仅功能测试 | 追加**真实 SQL 数据比对**要求（CLAUDE.md 集成测试规范） |

### 1.3 与 G2 / G7 / G10 的关系

- **G2 v4** 的 §3.4 重名消歧硬需求，由本 spec 的 **F5（columns）** 直接落实
- **G7** 已验证 DSL/链式 IR 独立——F5 通过对象引用而非 Proxy 属性表达 plan 关系，独立性不受影响
- **G10**（新增）承接 v1 评审中暴露的引擎前置改造工作；F5 实现路径如下：

```
G5 spec v2 (本文件)
    ├── Phase 1: F4 columns       ← 无架构改动，可立即推进
    └── Phase 2: F5 columns       ← 硬阻塞于 G10
                  ↑
                 G10 (新增 gap): 引擎前置改造
                  ├── SchemaDerivation 允许 join 输出携带歧义列
                  ├── OutputSchema 保留 plan provenance
                  ├── ComposePlanner plan-aware 编译
                  └── FieldAccessPermissionStep plan-routed 校验
```

## 2. 列项形态（columns 中）

### 2.1 形态总览

| 形态 | 示例 | 状态 | Phase |
|------|------|------|-------|
| **F1 · 字符串短写** | `"product$id"` | 已存在 | — |
| **F2 · 字符串带 alias** | `"name AS customer_name"` | 已存在 | — |
| **F3 · 字符串带表达式** | `"SUM(amount) AS total"` | 已存在 | — |
| **F4 · 对象基本** | `{field, agg?, as?}` | **G5 新增** | Phase 1 |
| **F5 · 对象 plan-qualified** | `{plan, field, agg?, as?}` | **G5 新增** | Phase 2（依赖 G10） |

### 2.2 F1-F3 字符串形态（完全保留）

```javascript
columns: [
  "product$id",                          // F1
  "customer$caption",                    // F1（维度 caption）
  "product.category$id",                 // F1（嵌套维度）
  "name AS customer_name",               // F2（重命名）
  "SUM(amount) AS totalSales",           // F3（聚合 + 重命名）
  "YEAR(orderDate) AS orderYear"         // F3（函数 + 重命名）
]
```

100% 兼容当前实现，零迁移。

### 2.3 F4 基本对象（Phase 1 · 立即可做）

```javascript
columns: [
  { field: "salesAmount" },                              // 等价 F1 "salesAmount"
  { field: "name", as: "customer_name" },                // 等价 F2 "name AS customer_name"
  { field: "amount", agg: "sum", as: "totalSales" },     // 等价 F3 "SUM(amount) AS totalSales"
  { field: "amount", agg: "count" }                      // F3 不易表达：默认 alias 由引擎给出
]
```

#### F4 字段定义

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `field` | string | ✅ | 列名 / 别名 / 维度后缀（如 `"product$id"` / `"customer_name"`） |
| `agg` | `"sum" \| "avg" \| "count" \| "max" \| "min" \| "count_distinct"` | ⬜ | 聚合函数；未指定时不聚合 |
| `as` | string | ⬜ | 输出列别名；未指定时引擎按规则生成（基础列保留原名，聚合列形如 `<agg>_<field>`） |

#### `agg` 函数白名单与 SQL lowering

`agg` 是**聚合函数白名单**，与 Compose `AggregateColumn` / `SemanticQueryRequest` 聚合能力对齐（**不引用** `REQ-FORMULA-EXTEND` —— 那是标量/表达式函数白名单，与本 spec 的列级聚合不同）：

| `agg` 值 | SQL lowering | 对齐 AST 节点 |
|---------|--------------|---------------|
| `"sum"` | `SUM(field)` | `AggregateColumn(field, "sum")` |
| `"avg"` | `AVG(field)` | `AggregateColumn(field, "avg")` |
| `"count"` | `COUNT(field)` | `AggregateColumn(field, "count")` |
| `"max"` | `MAX(field)` | `AggregateColumn(field, "max")` |
| `"min"` | `MIN(field)` | `AggregateColumn(field, "min")` |
| `"count_distinct"` | `COUNT(DISTINCT field)` | 通过现有 `AllowedFunctions.COUNT_DISTINCT` + `SqlFunctionExp` lowering 自动处理；无需 AST 扩展 |

**AST 验证结论**（2026-04-27 代码勘察确认）：

- Java：`AllowedFunctions.java:222-223` 已注册 `COUNT_DISTINCT` / `COUNTD` 为聚合函数；`SqlFunctionExp.java:117-126` 自动 lower 为 `COUNT(DISTINCT field)`
- Python：`inline_expression.py:16-17` / `field_validator.py:42` 同等支持

→ F4 实施只需在 DSL 入口把 `{agg: "count_distinct", field: x}` 归一化为字符串 `"COUNT_DISTINCT(x) AS alias"`，引擎 lowering 自动完成。`AggregateColumn` AST **完全不动**。

#### Phase 1 实施模式：normalize-at-entry（**v2 patch · 2026-04-27 修正**）

::: warning v1/v2 → v2-patch 设计修正
v1/v2 spec 原写"规范化为 `AggregateColumn(field, agg).as(alias)` 内部 IR"。代码勘察发现这一假设与实际架构不符：

- `DslQueryFunction.buildRequest()` 输出 `SemanticQueryRequest.columns: List<String>` —— **需要字符串**
- `ScriptRuntime.runScript()` 输出 `BaseModelPlan.columns: List<Object>`，但 `ComposePlanner.extractStringCols()` 在 plan compile 阶段又转回字符串 —— 等同字符串约束
- Python `from_()` 强约束 `Tuple[str, ...]`

正确实施模式是 **入口归一化（normalize-at-entry）**：在 DSL 入口处把 Map 形态归一化为字符串形态 `"AGG(field) AS alias"` / `"AGG(field)"` / `"field AS alias"` / `"field"`，下游编译/验证零改动。这比"扩展 AST"侵入性显著小，且与现有架构契合。
:::

实施模式：

- **Java 入口 1**：`DslQueryFunction.toStringList()`（legacy path）—— 加 Map 分支调用新增 `normalizeColumnMap(map, index)` 方法
- **Java 入口 2**：`ScriptRuntime.runScript()` 的 `from`/`dsl` lambda（M7 path · 第 141-147 行附近）—— 在 `ExpressionWhitelistValidator.validateColumns` 之前插入归一化（与入口 1 复用 `normalizeColumnMap`）
- **Python 入口 1**：`script_runtime._from_dsl()`（line 183-211）—— `validate_columns()` 调用之前归一化
- **Python 入口 2**：`engine/compose/plan/dsl.py` 的 `from_()` 入口 —— 同等归一化
- **Python 入口 3**：`plan/plan.py` 的 `_validate_columns()`（line 786 附近）—— 当前强约束 `isinstance(c, str)`，允许通过 normalize 后的字符串

下游不变：

- `BaseModelPlan` / `DerivedQueryPlan` / AST 类型完全不动
- `SchemaDerivation` / `ComposePlanner` / `FieldAccessPermissionStep` 不动
- 现有所有验证 / 编译路径走 string 输入即可

每端约 80-120 行代码（含 4-7 个错误码 + 单元 + 集成测试）。

### 2.4 F5 plan-qualified（Phase 2 · 阻塞于 G10）

```javascript
columns: [
  // 引用特定 plan 的列（解决 join 后重名）
  { plan: customers, field: "name", as: "customer_name" },
  { plan: orders, field: "name", as: "order_number" },
  { plan: orders, field: "amount", agg: "sum", as: "totalRevenue" }
]
```

#### F5 字段定义

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `plan` | `QueryPlan` 引用 | ✅ | 列所属的 plan；必须在当前 `dsl({model: ...})` 的 plan 谱系中可达（详见 §5.1） |
| `field` | string | ✅ | 在 `plan` 的输出 schema 中可见的列名 / 别名 |
| `agg` | string | ⬜ | 同 F4 |
| `as` | string | ⬜ | 同 F4 |

**关键语义**：F5 等价于链式 API 的 `<plan>.<field>.as(<alias>).agg?(<func>)`，但通过对象引用而非 Proxy 属性，DSL 独立性不受影响（[G7](compose-query-manuals-gap-tracker.md#g7)）。

#### Phase 2 硬前置：G10 必须先落地

F5 在当前引擎下**不可实现**——具体障碍见 §1.1 的 C1/C2/C5。G10 必须提供以下 4 项能力作为 F5 实施前置：

| G10 能力 | F5 依赖点 |
|---------|----------|
| (1) `SchemaDerivation` 允许 join 输出携带歧义列 | F5 触发条件需要 join 不预先 fail |
| (2) `OutputSchema` / `ColumnAliasParts` 保留 plan provenance | F5 需要在 schema 阶段定位列归属 |
| (3) `ComposePlanner` 在编译时识别 plan-qualified 引用 | F5 需要正确生成 SQL（带 table alias 区分） |
| (4) `FieldAccessPermissionStep` 支持 plan-routed 校验 | F5 引用的字段需路由到对应 BaseModelPlan 的 ModelBinding 做 fieldAccess 检查 |

G10 落地前，F5 在 spec 中**不开放**。Phase 1 (F4) 不受 G10 阻塞，可优先推进。

### 2.5 混合数组

F1-F5 可在同一数组中**任意混用**（Phase 1 完成后 F1-F4 混用即可工作；Phase 2 完成后 F5 加入）：

```javascript
columns: [
  "product$id",                                          // F1
  { plan: customers, field: "name", as: "customer_name" }, // F5（Phase 2）
  "SUM(amount) AS totalSales",                           // F3
  { field: "orderDate", agg: "max", as: "lastOrder" }    // F4（Phase 1）
]
```

引擎按位置依次解析；同一数组里的形态选择不影响相邻列项。

## 3. F4 实施细节（Phase 1）

### 3.1 字符串 ↔ F4 等价映射

| 字符串形态 | F4 等价 |
|-----------|--------|
| `"name"` | `{ field: "name" }` |
| `"name AS alias"` | `{ field: "name", as: "alias" }` |
| `"SUM(amount) AS total"` | `{ field: "amount", agg: "sum", as: "total" }` |
| `"YEAR(orderDate) AS year"` | （无 F4 等价 · 函数表达式仍走字符串） |
| `"product.category$caption"` | `{ field: "product.category$caption" }` |

引擎在解析阶段统一规范化为对象 IR，编译阶段不区分用户原始形态。

### 3.2 F4 校验

- `field` 缺失 → `COLUMN_FIELD_REQUIRED`
- `agg` 不在白名单 → `COLUMN_AGG_NOT_SUPPORTED`
- `as` 不是字符串 → `COLUMN_AS_TYPE_INVALID`

## 4. F5 实施细节（Phase 2 · 等 G10）

### 4.1 F5 落地后的解析流程

（待 G10 spec 落定后细化）大致如下：

1. **Parser**：`{plan: <ref>, field: "name", as: "..."}` 识别为对象，将 plan 引用与 field 名一同记录
2. **Resolver**：通过 plan 引用查找其在当前 `model` 谱系中的 lineage（§5.1）
3. **Permission**：定位 lineage 的 BaseModelPlan，调用其 ModelBinding 的 `fieldAccess` 校验 `field`（§5.4）
4. **Schema**：在派生 schema 中标记列归属于 plan，避免与同名列冲突
5. **Compile**：在 SQL 编译时按 plan-qualified 路径生成（含正确的 table alias 区分）

每一步具体实现取决于 G10 暴露的 API，本 spec 不冻结实现细节。

## 5. plan-qualified 解析规则（F5）

### 5.1 plan 可见性

`plan` 引用必须满足"**在当前查询的 plan 谱系中可达**"——从当前 `dsl({model: M, ...})` 的 `M` 出发，按以下规则递归遍历：

| Plan 类型 | 谱系子节点 |
|----------|-----------|
| `BaseModelPlan` | （叶子节点；自身可达） |
| `DerivedQueryPlan` | `source` |
| `JoinPlan` | `left`, `right` |
| `UnionPlan` | `left`, `right`（含数组重载折叠出的所有节点） |

具体地，当且仅当 `plan` 引用的对象**等于** `M`、或属于 `M` 谱系遍历到的任意节点时，引用合法。

::: warning 等价判定：按对象身份，不按 model 名称
"等于"判定**严格按对象身份（plan node identity）**，**不按 model 名称相等**。这避免了同模型多实例的歧义场景：

```javascript
const a1 = dsl({ model: "X", columns: ["id", "name AS a1_name"] });
const a2 = dsl({ model: "X", columns: ["id", "name AS a2_name"] });   // 同模型，不同实例
const joined = a1.join(a2, "inner", [{ left: "id", op: "=", right: "id" }]);

// ✅ 合法：plan: a1 严格等于 a1（对象身份）
const ok = dsl({
  model: joined,
  columns: [
    { plan: a1, field: "a1_name" },
    { plan: a2, field: "a2_name" }
  ]
});

// 引擎按对象身份区分 a1 / a2，即使两者 model 名都是 "X"
```

实现层使用 Java `==` / Python `is` 比较，不使用 `equals()` / `==` 值比较。
:::

```javascript
const a = dsl({ model: "A", columns: ["id", "name"] });
const b = dsl({ model: "B", columns: ["partner_id", "name"] });
const joined = a.join(b, "inner", [{ left: "id", op: "=", right: "partner_id" }]);

// ✅ 合法：a 和 b 都在 joined 的谱系中
const ok = dsl({
  model: joined,
  columns: [
    { plan: a, field: "name", as: "a_name" },
    { plan: b, field: "name", as: "b_name" }
  ]
});

// ❌ 非法：c 不在 joined 谱系中
const c = dsl({ model: "C", columns: ["x"] });
const bad = dsl({
  model: joined,
  columns: [
    { plan: c, field: "x" }    // → COLUMN_PLAN_NOT_VISIBLE
  ]
});
```

### 5.2 plan === model（自引用）

允许 `plan === model`（即 plan 引用就是当前 dsl 的 model）。这种情况虽然冗余，但语义合法，按 F4 处理：

```javascript
const base = dsl({ model: "X", columns: ["id", "name"] });

const derived = dsl({
  model: base,
  columns: [
    { plan: base, field: "name", as: "n" }   // ✅ 合法但冗余；等价 { field: "name", as: "n" }
  ]
});
```

**field 校验路径**：即使 `plan === model`，`field` 仍需在**当前 model 的输出 schema** 中可见（与 §5.3 规则一致）。允许这种冗余写法的目的是让 LLM 在生成 join 后置消歧代码时不需要"判断 plan 是否就是 model"再切形态——保留冗余写法降低生成的认知负担。

### 5.3 plan-qualified field 的 schema 可见性

`field` 必须在 `plan` 的**输出 schema** 中可见（即在该 plan 的 `columns` 投影里出现，含 alias）：

```javascript
const a = dsl({ model: "A", columns: ["id", "name AS a_name"] });

// ❌ 非法：a 投影后没有 "name"，只有 "a_name"
const bad = dsl({
  model: someJoined,
  columns: [{ plan: a, field: "name" }]   // → COLUMN_FIELD_NOT_FOUND
});

// ✅ 合法
const good = dsl({
  model: someJoined,
  columns: [{ plan: a, field: "a_name" }]
});
```

这与 G2 §2.4 schema 派生规则严格一致：派生 / plan-qualified 引用一律基于上一阶段的输出 schema，不绕路到底层物理列。

### 5.4 与 fieldAccess / deniedColumns 的协同（修正自 v1）

::: warning v1 → v2 措辞修订
**v1 错误措辞**："F5 通过 `plan` 引用列时，`fieldAccess` 白名单按 `plan.<field>` 路径校验"——这个表述错误地暗示了一个新的"plan 路径"权限维度。

**v2 正确表述**：F5 不引入新权限语义。`plan` 引用解析到 lineage 后，沿用现有 `PerBaseCompiler` 的 per-BaseModelPlan ModelBinding 校验：
:::

具体地：

1. F5 中的 `plan` 通过谱系遍历（§5.1）解析到一个 `BaseModelPlan`（或其派生链上溯到的 BaseModelPlan）
2. 该 BaseModelPlan 已有自己的 `ModelBinding`（含 `fieldAccess` / `deniedColumns` / `systemSlice`）
3. F5 中的 `field` 按现有规则校验该 binding 的 `fieldAccess`：在白名单内 → 通过；不在 → `FIELD_ACCESS_DENIED`（与现有错误码一致）
4. `deniedColumns` 在 SQL 构建后照旧拦截

**改动只在权限"路由"层**——F5 的 `plan` 引用让权限框架知道该字段应路由到哪个 BaseModelPlan binding，而非引入新维度。这是 G10 的工作（C5）。

## 6. 错误码全集（columns only）

::: tip 错误码命名约定（统一）
本表中 `COLUMN_*` 是**完整的对外公开错误码**——直接使用，不再追加 `COMPOSE_DSL_VALIDATION_` 前缀。

`COMPOSE_DSL_VALIDATION_*` 是**M3 沙箱错误码族的内部归类标签**（用于错误聚合 / 监控分类），不构成对外公开形态。落地 PR 中：

- 抛错 / API 响应 / 用户文档：用完整 `COLUMN_*` 短码（如 `COLUMN_PLAN_NOT_VISIBLE`）
- 错误聚合 / 内部分类：归入 `COMPOSE_DSL_VALIDATION_*` 子族（实现细节）
:::

| 错误码 | 触发条件 | Phase |
|--------|---------|-------|
| `COLUMN_FIELD_REQUIRED` | F4 / F5 对象形态中 `field` 字段缺失 | 1 / 2 |
| `COLUMN_AGG_NOT_SUPPORTED` | `agg` 不在 §2.3 聚合白名单中 | 1 |
| `COLUMN_AS_TYPE_INVALID` | `as` 不是字符串 | 1 |
| `COLUMN_PLAN_NOT_VISIBLE` | F5 中 `plan` 引用不在当前 model 的 plan 谱系中（按对象身份判定，§5.1） | 2 |
| `COLUMN_FIELD_NOT_FOUND` | F5 中 `field` 不在 `plan` 的输出 schema 里 | 2 |
| `COLUMN_PLAN_TYPE_INVALID` | `plan` 字段不是 `QueryPlan` 引用 | 2 |
| `FIELD_ACCESS_DENIED`（沿用现有） | F5 解析的 BaseModelPlan binding 拒绝该 field | 2 |

## 7. 不在 G5 范围（已移交 / 推迟）

| 类别 | 转入 |
|------|------|
| `slice` 中的 plan-qualified 引用 + `slice` 字段对象化 | [G11](compose-query-manuals-gap-tracker.md#g11)（含 `SliceShape` 字段强转修复） |
| `groupBy` / `orderBy` 中的 F4 / F5 | [G12](compose-query-manuals-gap-tracker.md#g12)（含 `List<String>` → `List<Object>` 类型迁移） |
| 表前缀字符串 `"plan_var.field"` | 不引入（与维度嵌套 `product.category$id` 冲突） |
| 嵌套对象 `{field: {expr: "..."}}` | 不引入（用 `"SUM(x) AS y"` 字符串或 calculatedFields 表达） |
| Subquery 作为列项 | 不引入（通过派生 + join 表达） |

## 8. 完整示例

### 8.1 F4 基本场景（Phase 1 · 立即可用）

```javascript
const sales = dsl({
  model: "FactSalesQueryModel",
  columns: [
    "product$id",                                          // F1
    { field: "amount", agg: "sum", as: "totalSales" },     // F4 显式聚合 + alias
    { field: "amount", agg: "avg", as: "avgPrice" },       // F4 多次聚合
    { field: "orderDate", agg: "max", as: "lastOrder" }    // F4 非数值聚合
  ],
  groupBy: ["product$id"]
});
```

### 8.2 F5 LLM 自我修复路径（Phase 2 · 等 G10）

```javascript
// 1. LLM 第一轮生成（按业务直觉，未注意 name 冲突）
const customers = dsl({
  model: "OdooResPartnerModel",
  columns: ["id", "name"]                  // ⚠️ 冲突源
});

const orders = dsl({
  model: "OdooSaleOrderModel",
  columns: ["partner_id", "name", "amount"]  // ⚠️ 冲突源
});

const joined = customers.join(orders, "inner", [
  { left: "id", op: "=", right: "partner_id" }
]);
// G10 落地前：joined 在此处 fail（JOIN_OUTPUT_COLUMN_CONFLICT）
// G10 落地后：joined 派生成功，schema 携带歧义列 + plan provenance

// 2. LLM 第一次尝试派生（命中 JOIN_AMBIGUOUS_COLUMN）
const result_v1 = dsl({
  model: joined,
  columns: ["name", "amount"]              // ❌ "name" 在 customers 和 orders 都有 → JOIN_AMBIGUOUS_COLUMN
});

// 3. LLM 看到错误后用 F5 后置消歧（保留上游 customers / orders 不动）
const result_v2 = dsl({
  model: joined,
  columns: [
    { plan: customers, field: "name", as: "customer_name" },
    { plan: orders, field: "name", as: "order_number" },
    { plan: orders, field: "amount", as: "order_amount" }
  ]
});

return {
  plans: { result: result_v2 },
  metadata: { title: "客户订单（F5 后置消歧）" }
};
```

## 9. 验收标准

| 项 | 标准 |
|----|------|
| **字符串形态 100% 兼容** | F1-F3 现有用法零迁移；既有测试脚本继续通过 |
| **Phase 1 (F4) 解析与编译** | F4 在 Java / Python 双端正确规范化为 IR；输出 SQL 与等价 F3 字符串完全相同 |
| **Phase 1 (F4) 真实 SQL 数据比对** | 至少 3 个集成测试用例：(1) 基本 alias `{field, as}`；(2) 显式聚合 `{field, agg, as}`；(3) 混合数组（F1+F3+F4）。每个用例必须通过 `queryFacade.queryModelData()` 执行真实查询 + 等价原生 SQL 基线 + 逐行比对（CLAUDE.md 集成测试规范） |
| **Phase 2 (F5) 解析与编译** | 等 G10 落地后；F5 输出 SQL 与链式 API `<plan>.<field>.as(...)` 完全相同 |
| **Phase 2 (F5) 真实 SQL 数据比对** | 至少 3 个集成测试用例：(1) F5 在 join 后消歧；(2) F5 引用 base plan（自引用）；(3) F5 + 聚合。每个用例必须通过真实 SQL 数据逐行比对 |
| **plan 谱系遍历** | §5.1 的可见性规则在双端实现一致；4 类 plan（Base / Derived / Join / Union）均覆盖测试 |
| **错误码覆盖** | §6 中所有错误码在 M3 沙箱错误码族中找到归属（`COMPOSE_DSL_VALIDATION_*`） |
| **与 G2 协同** | F5 落地后，G2 §3.4 中"🚧 G5 收口后可用"段移除 🚧 标记 |
| **与 fieldAccess / deniedColumns 协同** | F5 通过解析的 BaseModelPlan binding 校验 `fieldAccess`；deniedColumns 在 SQL 构建后照旧拦截（参 §5.4） |
| **Java / Python 双端 parity** | 对象形态解析、plan 谱系校验、错误码触发条件双端测试 case 对等 |

## 10. 实施落点

### 10.1 文档更新

- **Phase 1 完成后**：Manual A §2 / §4 落稿（F4 部分）
- **Phase 2 完成后**：Manual A §3.6 后置消歧段落正稿；G2 spec §3.4 中"🚧 G5 收口后可用"段去 🚧；gap tracker G5 标 closed
- query-dsl.md 加 deprecation banner 指向本 spec + Manual A

### 10.2 代码改动 · Phase 1（F4 · 立即可推进）· **v2-patch 重新评估**

::: warning Phase 1 实际工作量
原 v1/v2 评估假设 IR 扩展，工作量小。代码勘察确认应走 **normalize-at-entry 模式**（详见 §2.3 实施模式）后，工作量重新评估。
:::

| 端 | 改动点 | 估时 |
|----|-------|------|
| Java | `DslQueryFunction.toStringList()` 增加 Map 分支 + 新增 `normalizeColumnMap()` 工具方法（约 60-95 行含 javadoc） | 小到中等 |
| Java | `ScriptRuntime.runScript()` 在 `from`/`dsl` lambda 中复用 `normalizeColumnMap`（参 :141-147）；推荐抽出到独立 utility class `engine/compose/plan/ColumnObjectNormalizer.java` | 小 |
| Java | F4 错误码 `COLUMN_FIELD_REQUIRED` / `COLUMN_AGG_NOT_SUPPORTED` / `COLUMN_AS_TYPE_INVALID` + Phase 2 占位错误码 `COLUMN_PLAN_NOT_VISIBLE`（含 `plan` 键时 fail-loud） | 极小 |
| Java | `count_distinct` lowering —— **零代码改动**（已通过 `AllowedFunctions.COUNT_DISTINCT` + `SqlFunctionExp` 自动支持） | 0 |
| Python | `script_runtime._from_dsl()` (`:183-211`) + `engine/compose/plan/dsl.py.from_()` + `plan/plan.py._validate_columns()` (`:786`) 同步 normalize 逻辑 | 小到中等 |
| Python | F4 错误码（沿用 ValueError 异常体系，错误消息以 `COLUMN_*:` 前缀） | 极小 |
| 测试 | Java：`F4ColumnObjectIntegrationTest.java`（继承 `EcommerceTestSupport`）覆盖 7 个用例（4 SQL 用例 + 3 错误用例） | 中等 |
| 测试 | Python：`tests/compose/plan/test_f4_column_object.py` 镜像 7 用例 | 中等 |
| 文档 | Manual A `docs-site/zh/dataset-model/compose-query/dsl-manual.md` §2.1-§2.2 增 F4 文档 + 一个示例 block；§2 中 G5 🚧 标记保留 F5 部分 | 极小 |

### 10.3 代码改动 · Phase 2（F5 · 等 G10）

| 端 | 改动点 | 估时 |
|----|-------|------|
| Java | `DslQueryFunction.buildRequest()` 中 columns 解析支持 `{plan, field, agg?, as?}` Map | 小 |
| Java | F5 plan 谱系遍历（`QueryPlan.collectVisiblePlans()` 工具方法） | 中等 |
| Java | F5 plan-routed fieldAccess 校验（依赖 G10 暴露的 binding 路由 API） | 中等 |
| Java | F5 错误码 `COLUMN_PLAN_NOT_VISIBLE` / `COLUMN_FIELD_NOT_FOUND` / `COLUMN_PLAN_TYPE_INVALID` | 极小 |
| Python | `dsl.py` 同步对等改动 | 中等 |
| 测试 | F5 真实 SQL 数据比对 + 4 类 plan 覆盖 | 中等 |

### 10.4 与 G2 / G10 / G11 / G12 的协调

| spec / gap | 协调点 |
|----|-------|
| **G2 spec v4** | 本 spec Phase 2 是 G2 §3.4 后置消歧硬需求的落实；Phase 2 收口后同步移除 G2 中的 🚧 占位 |
| **G10**（新增） | F5 的硬前置；G10 spec 单独立项，覆盖 SchemaDerivation / OutputSchema / ComposePlanner / FieldAccessPermissionStep 4 项改造 |
| **G11**（新增 · 后续） | `slice` F4/F5（含 SliceShape 字段强转修复）；G5 v2 不涉及，G11 单独立项 |
| **G12**（新增 · 后续） | `groupBy` / `orderBy` F4/F5（含 `List<String>` → `List<Object>` 类型迁移）；G5 v2 不涉及，G12 单独立项 |

## 11. 评审 Checklist

请评审者重点关注：

- [ ] **决策 D1**：v2 范围**仅限于 columns**（slice/groupBy/orderBy 移交 G11/G12）—— 是否合适？还是应留在 G5 内分期？
- [ ] **决策 D2**：F4 / F5 拆为 Phase 1 / Phase 2，F5 显式依赖 G10 —— 是否合适？还是 G5 整体阻塞于 G10？
- [ ] **决策 D3**：F4 字段 `{field, agg, as}` 与字符串形态 `"SUM(amount) AS total"` 的等价映射规则（§3.1）—— 是否清晰？是否漏覆盖某些字符串形态？
- [ ] **决策 D4**：plan === model 自引用允许且按 F4 处理（§5.2）—— 是否合理？还是直接拒绝？
- [ ] **决策 D5**：plan 谱系遍历规则（§5.1）的 4 类 plan 子节点定义 —— 是否完整？是否需要追加规则？
- [ ] **决策 D6**：与 fieldAccess / deniedColumns 协同采用"plan 解析到 BaseModelPlan binding 后沿用现有校验"路径（§5.4）—— 是否符合现有架构？还是要引入独立的 plan-aware 权限维度？
- [ ] **决策 D7**：验收标准（§9）追加**真实 SQL 数据比对**要求 —— 是否符合 worktree CLAUDE.md 的集成测试规范？
- [ ] **决策 D8**：错误码归类 `COMPOSE_DSL_VALIDATION_*` 子族 —— 是否合并到现有错误码族？

## 维护记录

| 日期 | 操作 | 备注 |
|------|------|------|
| 2026-04-26 | 创建 Draft v1 | 基于 G2 §3.4 硬需求 + Phase 0 IR 独立性结论；覆盖 F4 / F5 在 4 类位置 |
| 2026-04-26 | 修订 Draft v2 | (1) 收到 v1 ❌ 评估，5 项代码事实推翻 v1 假设；(2) 范围收窄至 **columns only**，slice/groupBy/orderBy 移交 G11/G12；(3) F4 / F5 拆 Phase 1 / Phase 2，F5 显式依赖 [G10](compose-query-manuals-gap-tracker.md#g10) 引擎前置改造；(4) §5.4 修正"按 `plan.<field>` 路径校验"措辞为"plan 解析到 BaseModelPlan binding 后沿用现有 fieldAccess 校验"；(5) §9 验收追加**真实 SQL 数据比对**要求（CLAUDE.md 集成测试规范） |
| 2026-04-26 | v2 评审反馈 patch | v2 收到 ✅ 评估通过 + 4 项小修订：(a) §2.3 F4 `agg` 白名单改为对齐 Compose `AggregateColumn` / `SemanticQueryRequest`（不再引用 REQ-FORMULA-EXTEND），追加 `count_distinct` SQL lowering 规则（`COUNT(DISTINCT field)`）+ 实现 PR AST 验证要求；(b) §5.1 plan 谱系遍历追加"按对象身份（plan node identity）判定，不按 model 名称相等"warning，含同模型多实例示例；(c) §5.2 `plan === model` 自引用补"按当前 model 输出 schema 校验 field"明示；(d) §6 错误码表头追加命名约定 tip：`COLUMN_*` 是完整对外公开短码，`COMPOSE_DSL_VALIDATION_*` 是内部归类标签；(e) §10.2 Phase 1 工作量"极低 → 小到中等"重估，加 `count_distinct` AST 验证子项 |
| 2026-04-27 | v2-patch 实施前勘察修正 | F4 实施前代码勘察暴露 v1/v2 spec 两处与实际架构不符：(1) `count_distinct` lowering **已经被引擎支持**（`AllowedFunctions.java:222-223` + `SqlFunctionExp.java:117-126` + Python `inline_expression.py:16-17`）—— 移除"PR 阶段验证 AST"要求，明确"零 AST 改动"；(2) F4 实施模式从"规范化为 `AggregateColumn` IR"修正为 **normalize-at-entry**（在 DSL 入口把 Map 归一化为字符串 `"AGG(field) AS alias"`），原因是 `DslQueryFunction.buildRequest()` / `ScriptRuntime.runScript()` 实际都是 string-oriented，下游强约束字符串。§2.3 / §10.2 改写：列出双端 5 个具体入口（Java 2 个 + Python 3 个）+ 工作量重估（含双端 normalize utility + 7 用例集成测试 + Manual A 更新）|
