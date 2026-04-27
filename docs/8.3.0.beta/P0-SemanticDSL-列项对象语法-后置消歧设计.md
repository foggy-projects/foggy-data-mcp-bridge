# P0 · SemanticDSL · columns 列项对象语法 + 后置消歧设计（G5 v2）

> **状态**：Draft v2-patch-2（已落档，可入实施）
> **目标版本**：8.3.0.beta
> **关联 gap**：[G5](compose-query-manuals-gap-tracker.md#g5)（v2 已缩窄到 columns 范围）
> **前置依赖**：[G10](compose-query-manuals-gap-tracker.md#g10) PR2/PR3/PR4 ✅ 已落地于 worktree HEAD（`g10Enabled()` 默认 OFF）；Python PR5 单独立项
> **关联 spec**：[G2 spec v4](P0-SemanticDSL-CTE-派生-Join-Union-语法设计.md)（`model` 多态 + combinator 唯一形态）
> **创建日期**：2026-04-26 · **最近修订**：2026-04-27（v2-patch-2 落档）

## 0. 摘要

本 spec 为 SemanticDSL 的 **`columns` 字段**补全两类对象形态：

1. **F4 基本对象** `{field, agg?, as?}` —— 显式表达聚合 + alias，避免依赖 `"SUM(x) AS y"` 字符串拼接
2. **F5 plan-qualified** `{plan, field, agg?, as?}` —— 通过 plan 引用解决 join 后重名歧义（**G2 §3.4 标记的 LLM 自我修复硬需求**）

v2 范围**仅限于 `columns`**——v1 中规划的 `groupBy` / `orderBy` / `slice` 三个位置已分别移交给 [G11](compose-query-manuals-gap-tracker.md#g11) / [G12](compose-query-manuals-gap-tracker.md#g12) 后续处理（涉及 `List<String>` 类型迁移和 `SliceShape` 字符串强转修复，工作量超出 G5 单 spec 合理范围）。

**核心策略**：

- **F4 入口归一化已落地（Phase 1）**：`ColumnObjectNormalizer.normalizeColumns / normalizeColumnsToStrings` + `DslQueryFunction.toStringList` + `ScriptRuntime.runScript` 在 v2-patch 周期已落盘；本 v2-patch-2 周期补齐 plain-alias 引擎 pipeline + 元数据继承（§3.1.1 / §3.1.2）
- **F5 引擎链路已落地（Phase 2 · Java）**：G10 PR2/PR3/PR4 已落盘 HEAD（`SchemaDerivation` join 歧义放行 / `OutputSchema.planProvenance` / `ComposePlanner.compilePlanColumnRef` plan-aware SQL / `ComposePlanAwarePermissionValidator` plan-routed 校验），默认 OFF。剩余工作 = DSL Map 入口契合 + 集成测试 + Python PR5 + `g10Enabled()` 默认值切换决策

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
- **G10**（拆分为多 PR 推进）承接引擎前置改造工作；当前真实状态（2026-04-27 代码勘察）：

```
G5 spec v2-patch-2 (本文件)
    ├── Phase 1: F4 columns       ← 入口 normalize 已落地；本周期补 plain-alias 引擎 + 元数据继承
    └── Phase 2: F5 columns       ← Java 引擎链路全在，默认 OFF；剩 DSL 入口 + 测试 + Python PR5
                  ↑
                 G10 引擎前置改造（多 PR）
                  ├── PR2 ✅ SchemaDerivation 允许 join 输出携带歧义列 + OutputSchema.planProvenance
                  ├── PR3 ✅ ComposePlanner.compilePlanColumnRef plan-aware 编译（planAliasMap）
                  ├── PR4 ✅ ComposePlanAwarePermissionValidator plan-routed 校验
                  └── PR5 ❌ Python 引擎镜像（PR2+PR3+PR4 等价能力，单独立项）
```

**G10 默认 OFF 影响**：`ComposeFeatureFlags.g10Enabled()` 默认 `false`（line 83 默认值，含系统属性 `foggy.compose.g10.enabled` / 环境变量 `FOGGY_COMPOSE_G10_ENABLED` / 测试覆盖 slot）。这意味着：

- F5 代码已在 HEAD，但用户级**不开放**——`SchemaDerivation` 仍按 legacy 抛 `JOIN_OUTPUT_COLUMN_CONFLICT`；F5 plan-qualified SQL 退回裸列名；plan-aware permission validator 不执行
- LLM 提示词、G2 spec、Manual A 引导用户用 F5 的时机 = G10 默认值切换时机，需协调决策（不阻塞 spec 落档，独立 follow-up）

## 2. 列项形态（columns 中）

### 2.1 形态总览

| 形态 | 示例 | 状态 | Phase |
|------|------|------|-------|
| **F1 · 字符串短写** | `"product$id"` | 已存在 | — |
| **F2 · 字符串带 alias** | `"name AS customer_name"` | 已存在 | — |
| **F3 · 字符串带表达式** | `"SUM(amount) AS total"` | 已存在 | — |
| **F4 · 对象基本** | `{field, agg?, as?}` | 入口 normalize 已落地；plain-alias 引擎与元数据继承本周期补齐 | Phase 1 |
| **F5 · 对象 plan-qualified** | `{plan, field, agg?, as?}` | Java 引擎链路已落地（PR2/PR3/PR4），`g10Enabled()` 默认 OFF；剩 DSL Map 入口 + 测试 + Python PR5 | Phase 2 |

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

#### 3.1.1 Plain-field alias-only 支持方案（2026-04-27 补充 · v2-patch-2 落档）

F4 Phase 1 已采用 normalize-at-entry：`{field: "name", as: "alias"}` 会归一化为字符串 `"name AS alias"`。代码勘察确认当前 Java 引擎只把带函数的 `"SUM(x) AS y"` / `"YEAR(x) AS y"` 识别为 inline expression；`"name AS alias"` 会被 `InlineExpressionParser.parse()` 显式返回 `null`（line 98-100 命中 `isSimpleColumnName`），随后 `SchemaAwareFieldValidationStep:114` 把整串 `"name AS alias"` 当作字段名调 `validateField` 校验、`FieldAccessPermissionStep:128-135` 同样把整串过 fieldAccess 白名单——两处都报字段不存在。这是 F4 必须补齐的引擎能力，不应通过规避测试收口。

##### 实施路线：Plain alias 转合成 `CalculatedFieldDef`（Option A）

复用现有 `"SUM(x) AS y"` 已走通的 inline expression → CalculatedFieldDef pipeline。`{field:"name", as:"alias"}` 在 `InlineExpressionPreprocessStep` 内合成为 `CalculatedFieldDef(name="alias", expression="name", origin=PLAIN_ALIAS)`，下游 schema 校验 / SQL 编译 / visitor 渲染全自动接，无双 alias 风险。

##### Step 顺序硬约束（必须先理解）

worktree HEAD 实际 step 执行顺序：

| Step | order | 行为 |
|------|-------|------|
| `FieldAccessPermissionStep` | **-25** | 看到原始 `"name AS alias"`，**早于** preprocess |
| `InlineExpressionPreprocessStep` | **5** | 合成 calc field |
| `SchemaAwareFieldValidationStep` | **8** | 看到转换后的 `"alias"`（calc field name） |
| `AutoGroupByStep` | **10** | 从 columns 派生 groupBy |
| `PhysicalColumnPermissionStep` | 1100（execute 阶段） | SQL 构建后 |

**关键事实**：`FieldAccessPermissionStep` 跑在 preprocess 之前。Option A 不能"只改 preprocess 一处"——FieldAccess 自身必须 alias-aware，否则 plain alias 在它这里就被拒，根本走不到合成阶段。

##### Java 落点（六行重写）

| # | 落点 | 方案 |
|---|------|------|
| 1 | **alias 解析** | **复用** `engine/compose/schema/AliasExtractor.extract(String)` —— 已存在，返回 `ColumnAliasParts(expression, outputName, hasAlias)`，identifier 正则与 `AS` 大小写不敏感解析均已就绪。**不新增** `PlainColumnAlias` / `ColumnAliasParser`，**不动** `InlineExpressionParser`（保持表达式判定语义不变） |
| 2 | **权限校验**（必改） | `FieldAccessPermissionStep:128-135` 在 `InlineExpressionParser.parse(column) == null` 之后、`stripDimensionSuffix` 之前接 `AliasExtractor.extract`：`hasAlias && simpleField` 时仅取 `expression()` 走 `stripDimensionSuffix` + 白名单匹配，alias 不进入权限维度 |
| 3 | **inline 预处理**（主合成点） | `InlineExpressionPreprocessStep.parseAndConvert()`（line 100-150 附近）扩展 plain-alias 分支：`InlineExpressionParser.parse(column) == null` 时再调 `AliasExtractor.extract(column)`，若 `hasAlias && simpleField(expression)` 则合成 `CalculatedFieldDef(name=outputName, expression=expression, origin=PLAIN_ALIAS)`，并把 `result.getColumns().add(outputName)` 替换 columns[i]，进 `existingFields` 之前先做命名冲突检测（见下） |
| 4 | **schema 校验** | **不改**——preprocess 跑完后 columns 已是 `["alias"]`，SchemaAware @8 走现有 calc field 路径自动通过；加 1 个 regression 测试守护"plain alias 经 preprocess 后 SchemaAware 识别为合成 calc field"是关键守护 |
| 5 | **SQL 输出** | **不改**——合成 calc 经 `SqlCalculatedFieldProcessor.processCalculatedField:124-131` 构造 `CalculatedDbColumn(name=outputName, sqlFragment=t0.<base>)`，`SimpleSqlJdbcQueryVisitor:76-77` 渲染为 `t0.name AS alias`，无双 alias |
| 6 | **groupBy 协同** | `AutoGroupByStep:118-121` 从 columns 派生 groupBy 时收 `outputName`，下游 `SqlColumnRefExp` 通过 calcFieldMap 解析回 `t0.name`，最终 SQL 形如 `SELECT t0.name AS alias FROM ... GROUP BY t0.name`。**用户显式 `groupBy:["alias"]` 仍按 G11/G12 范围拒绝**（FieldAccess @-25 看到 alias 时尚不在 calcFieldMap，按未知字段处理） |

`InlineExpressionParser` 保持现状——plain-alias 处理**仅在 `parse() == null` 之后**接管，避免劫持 `"SUM(x) AS y"` / `"YEAR(x) AS y"` 等真实表达式。

##### 命名冲突 fail-fast（preprocess 阶段，三类）

`InlineExpressionPreprocessStep` 合成前必须先做冲突检测，避免合成项与已有命名空间静默碰撞：

| 冲突场景 | 错误码 | 触发条件 |
|---------|-------|---------|
| **C1**：alias 命中已有 calc field 名（用户显式或 QM 声明态） | `COLUMN_ALIAS_COLLIDES_WITH_CALCULATED_FIELD` | `outputName ∈ existingCalcFieldNames`（含 QM `predefinedCalculatedFields` + request `calculatedFields`） |
| **C2**：alias 命中 QM 物理字段名 | `COLUMN_ALIAS_COLLIDES_WITH_PHYSICAL_FIELD` | `outputName ∈ qmDeclaredFieldNames`（含 measure / dimension / attribute） |
| **C3**：同请求多列 alias 重复 | `COLUMN_ALIAS_DUPLICATE` | 同 columns 数组内 `outputName` 出现 ≥2 次 |

C2 是治理硬要求——不检测会导致合成 calc 静默 shadow 真实物理列，metadata 与查询语义不一致。

##### 维度后缀字段拒绝

`{field, as}` 中 `field` 含 `$` 后缀（如 `product$id` / `customer$caption`）时**不支持** plain-alias-as-calc 路径，preprocess 阶段抛 `COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED`。理由：维度成员的结构化语义（"它是 product 维度的 $id 成员"）会在 calc field 路径扁平化丢失，G5 v2-patch 期间不打开；用户/LLM 在维度后缀字段上想要 alias 时，应在 G11/G12 框架内解决。

详见 §3.1.2 元数据继承与拒绝规则。

##### 边界规则

| 规则 | 行为 |
|------|------|
| **接管顺序** | plain-alias 仅在 `InlineExpressionParser.parse() == null` 之后接管；`isSimpleColumnName(expression)` 真才合成 |
| **alias === base** | `{field:"a", as:"a"}` normalize 时已被 `ColumnObjectNormalizer` 折成 `"a"`（无 alias 形态），preprocess 不会看到 `"a AS a"` |
| **空字符串 alias** | `{field:"a", as:""}` 在 `ColumnObjectNormalizer` 入口抛 `COLUMN_AS_TYPE_INVALID` |
| **base 含点号嵌套维度** | `{field:"product.category$id", as:"x"}` 同 `$` 后缀规则，抛 `COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED` |
| **chain rename** | `[{field:"name", as:"a"}, {field:"a", as:"b"}]` —— preprocess 顺序合成 `calc{a→name}` + `calc{b→a}`；`CalculatedFieldService.resolveBaseColumnReferences("b") → "a" → "name"` 由 visited 集合保证终止；fieldAccess 白名单仍按 base `"name"` 校验 |
| **错误信息脱敏** | base 不存在时错误信息以 alias 视角输出："field 'name' (referenced by alias 'customer_name') not found"，与 `QueryErrorSanitizer` 协同；不暴露 `"name AS alias"` 整串 |
| **DEBUG 日志** | preprocess 区分两种合成的措辞：`origin=INLINE_EXPRESSION` 输出 "Inline expression converted: ..."；`origin=PLAIN_ALIAS` 输出 "Plain field alias rewritten: ..." |

##### Python 落点

> 本节 Python 落点放入 §10.x Python PR5 单独立项，G5 v2-patch 当前阶段只确保 Java 完整闭环。Python 现状（`field_validator._parse_column_expr` 已在语义校验层剥 alias）与 Java Option A 实施路径不完全对称，待 PR5 时按 parity 矩阵重新约束。

#### 3.1.2 合成 calc field 的元数据继承（Option A 必备配套）

代码勘察确认现有 calc field（含 QM 声明态与 `"SUM(x) AS y"` 合成态）对 base 字段元数据**仅部分继承**：

| 元数据维度 | 现状 | 来源 |
|-----------|------|------|
| `JdbcColumnType` / type | ✅ 自动继承 | `SqlFragment.inferColumnType` 走 `column.getSelectColumn().getType()`；MONEY 字段 alias 后仍是 MONEY |
| `formatter` | ✅ 传递性继承 | `CalculatedDbColumn.getFormatter()` 由 type 派生 |
| `referencedColumns`（物理列依赖） | ✅ 自动追踪 | `SqlFragment.referencedColumns` 集合记录所有依赖列对象，是 fieldAccess / deniedColumns 链路工作的基础 |
| `caption` / label | ❌ 丢失 | `CalculatedDbColumn` 构造时使用合成方提供的 caption，**不会反查 `base.getCaption()`** |
| `description` / tooltip | ❌ 丢失 | 默认是合成方给的 `"公式: <expression>"` |
| 维度成员语义 | ❌ 扁平化 | 维度后缀字段经过 calc 路径后仅保留 type，"是哪个维度的哪个成员"的结构化信息丢失 |
| metadata `sourceField` 段 | ❌ 不存在 | `SemanticServiceV3Impl.createCalculatedFieldInfo:1711-1738` 输出 `type`/`description`/`expression`/`predefined`，无 `dependsOn` / `sourceFields` |

**Option A 必须显式补齐继承**，避免 `{field, as}` 后字段语义降级（label 从中文/业务描述变成 alias 字面）。

##### 元数据继承落点

| 落点 | 改动 |
|------|------|
| `CalculatedFieldDef` 字段扩展 | 加 transient `origin` 枚举字段（`USER_DECLARED` / `INLINE_EXPRESSION` / `PLAIN_ALIAS`），不参与序列化；preprocess 合成时按场景 set。同时确认 `caption` / `description` 可被合成方显式注入（若现状缺字段需补） |
| `InlineExpressionPreprocessStep` 合成时 | `origin=PLAIN_ALIAS` 分支：用 `expression()` 调 `SqlExpContext.findJdbcColumnForSelectByName(base, true)` 探测 base 列对象；若 base 是直接物理列（非维度后缀），把 `base.getCaption()` / `base.getDescription()` 拷给新 `CalculatedFieldDef`；type 由引擎推断链路自动继承，无需显式拷贝 |
| `SqlCalculatedFieldProcessor.processCalculatedField:124-131` | 构造 `CalculatedDbColumn` 时使用 `fieldDef.getCaption()`（已被前一步从 base 拷贝过）作为 caption，而不是默认的 alias 字面值 |
| `SemanticServiceV3Impl.createCalculatedFieldInfo` | 对 `origin=PLAIN_ALIAS` 的 calc field 在 metadata 输出加 `sourceField: "<base name>"` + `aliasOf: "<base name>"` 段。请求级合成项目前不进 metadata pipeline，但保留语义留位，便于未来 origin-aware metadata 扩展 |

补强后元数据矩阵：

| 维度 | Option A 补强后 |
|------|----------------|
| type | ✅ 引擎自动继承 |
| formatter | ✅ 由 type 派生 |
| **caption** | ✅ **从 base 显式拷贝** |
| **description** | ✅ **从 base 显式拷贝** |
| referencedColumns | ✅ 引擎自动追踪 |
| **sourceField metadata 段** | ✅ **新增**（origin=PLAIN_ALIAS 时） |
| 维度成员 `$id` / `$caption` 后缀 alias | ❌ **拒绝**（fail-fast）—— G5 v2-patch 期间 `COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED` |
| measure 聚合默认 / baseline | 仍丢（`{field, agg, as}` 路径不在本节） |

##### 验证要点

1. `{field:"amount", as:"revenue"}` 真查后，结果列 `revenue` 的 `caption` = "金额"（base.caption），不是 "revenue"
2. `{field:"product$id", as:"productId"}` 在 preprocess 阶段拒绝，错误码 `COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED`
3. `describe_model_internal` 在合成路径之后调用——返回的 `calculatedFields` 不含合成项 `revenue`（请求级隔离）
4. base 字段不存在时，错误信息 alias 视角："field 'name' (referenced by alias 'customer_name') not found"

### 3.2 F4 校验

- `field` 缺失 → `COLUMN_FIELD_REQUIRED`
- `agg` 不在白名单 → `COLUMN_AGG_NOT_SUPPORTED`
- `as` 不是字符串 → `COLUMN_AS_TYPE_INVALID`

## 4. F5 实施细节（Phase 2 · Java 引擎层已就绪）

### 4.1 F5 已落地的实现链路（基于 G10 PR2/PR3/PR4 真实代码）

> **状态**：Java 引擎核心链路已落地于 worktree HEAD（`ComposeFeatureFlags.g10Enabled()` 默认 OFF）。F5 用户级开放 = G10 转默认 ON 时机决策 + Python PR5 跨语言 parity 完成。

实际链路（2026-04-27 代码勘察）：

1. **Parser / Normalize**：`ScriptRuntime.runScript()` 走 `ColumnObjectNormalizer.normalizeColumns()`（保留对象 IR），F5 `{plan, field, agg?, as?}` 在脚本 DSL 中以 `PlanColumnRef(plan, name)` 对象形式进入 `BaseModelPlan.columns: List<Object>`，**不归一化为字符串**。`DslQueryFunction.toStringList()`（legacy buildRequest 路径）走 `normalizeColumnsToStrings`，**不支持 F5**（`SemanticQueryRequest.columns: List<String>` 不能承载 `PlanColumnRef`）

2. **Schema 派生**：`SchemaDerivation`（PR2）当 `g10Enabled() == true` 时，join 输出允许携带歧义列 + `OutputSchema.planProvenance`；`g10Enabled() == false` 时仍按 legacy 抛 `JOIN_OUTPUT_COLUMN_CONFLICT`

3. **Compile 期 plan-aware SQL**：`ComposePlanner.compilePlanColumnRef`（PR3，`ComposePlanner.java:316-331`）在 G10 ON 时通过 `planAliasMap.get(ref.plan())` 取 table alias 前缀，输出 `customers.name`；G10 OFF 退回裸列名。`planAliasMap` 已贯穿 `BinaryExpr` / `CaseWhenExpr` / `WindowColumn` / `ProjectedColumn` 全部递归路径

4. **Permission**：`ComposePlanAwarePermissionValidator`（PR4，挂入点 `ComposePlanner.compilePlanToSql:525-526`）在 G10 ON 时执行：
   - `validatePlanQualified(ref, planCtx)`：F5 plan-qualified 列直接路由到 `planCtx.resolveFieldAccess(ref.plan())`，校验 base field 是否在该 plan binding 的 `fieldAccess` 白名单中
   - `validateBareField(name, schema, planCtx)`：bare field 引用通过 `OutputSchema` lookup，命中多 plan 时抛 `JOIN_AMBIGUOUS_COLUMN`，命中唯一带 `planProvenance` 时通过 `provenance.resolve()` 路由到对应 plan binding
   - plan 引用未在 `planCtx` 中绑定时抛 `COLUMN_PLAN_NOT_BOUND`

5. **错误码族**：`ComposeSchemaErrorCodes` 已注册 `JOIN_AMBIGUOUS_COLUMN` / `COLUMN_PLAN_NOT_BOUND` 等所有 PR4 抛出的代码

### 4.2 F5 用户开放的剩余依赖

| 项 | 状态 |
|----|------|
| Java 引擎链路（PR2 / PR3 / PR4） | ✅ 已落地于 HEAD |
| Java F5 DSL 入口集成测试（`F5ColumnObjectIntegrationTest.java`） | ⏳ 待补 —— 现有 `FluentApiCompileTest` 走 `PlanColumnRef` + `.sum().as()` 链式但非 Map DSL |
| Python PR5（Java PR3+PR4 镜像） | ❌ 未启动 —— 单独立项 |
| `ComposeFeatureFlags.g10Enabled()` 默认值切换 | 未决定 —— G10 默认 OFF 意味着 F5 代码在 HEAD 但用户级不开放，需协调 LLM 提示词 / G2 / Manual A 一起决策 |
| Manual A § 后置消歧段落落稿 | ⏳ |

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
| `COLUMN_AS_TYPE_INVALID` | `as` 不是字符串（含空字符串） | 1 |
| `COLUMN_FIELD_INVALID_KEY` | F4 / F5 对象包含未识别 key | 1 / 2 |
| `COLUMN_ALIAS_COLLIDES_WITH_CALCULATED_FIELD` | F4 plain alias 命中已有 calc field 名（§3.1.1 C1） | 1 |
| `COLUMN_ALIAS_COLLIDES_WITH_PHYSICAL_FIELD` | F4 plain alias 命中 QM 物理字段名（§3.1.1 C2，治理硬要求） | 1 |
| `COLUMN_ALIAS_DUPLICATE` | 同请求多列 alias 重复（§3.1.1 C3） | 1 |
| `COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED` | F4 `{field, as}` 中 `field` 含 `$` 后缀（维度成员），plain-alias-as-calc 路径拒绝（§3.1.1 / §3.1.2） | 1 |
| `COLUMN_PLAN_NOT_VISIBLE` | F5 中 `plan` 引用不在当前 model 的 plan 谱系中（按对象身份判定，§5.1） | 2 |
| `COLUMN_PLAN_NOT_BOUND` | F5 中 `plan` 引用未绑定 ModelBinding，或 plan-aware permission 解析失败（PR4 引入） | 2 |
| `COLUMN_FIELD_NOT_FOUND` | F5 中 `field` 不在 `plan` 的输出 schema 里 | 2 |
| `COLUMN_PLAN_TYPE_INVALID` | `plan` 字段不是 `QueryPlan` 引用 | 2 |
| `JOIN_AMBIGUOUS_COLUMN`（PR4 引入） | bare-field 引用命中 join 后多 plan 同名列；G10 ON 时由 `ComposePlanAwarePermissionValidator` 抛出 | 2（仅 G10 ON） |
| `JOIN_OUTPUT_COLUMN_CONFLICT`（沿用现有） | G10 OFF 时 `SchemaDerivation` 在 join 输出 schema 派生时直接拒绝重名列 | 2（G10 OFF 时的 legacy 行为） |
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

### 8.2 F5 LLM 自我修复路径（Phase 2 · Java 引擎已就绪 · `g10Enabled()` 默认 OFF）

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
// G10 OFF（默认）：joined 在此处直接 fail —— SchemaDerivation 抛 JOIN_OUTPUT_COLUMN_CONFLICT
// G10 ON：joined 派生成功，schema 携带歧义列 + plan provenance

// 2. LLM 第一次尝试派生
const result_v1 = dsl({
  model: joined,
  columns: ["name", "amount"]
  // G10 OFF：根本走不到这一步（join 已 fail）
  // G10 ON：bare-field "name" 命中 join 后多 plan 同名列 → ComposePlanAwarePermissionValidator 抛 JOIN_AMBIGUOUS_COLUMN
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
| **Phase 1 (F4) 解析与归一化** | F4 在 Java（已落地）/ Python（已落地）双端正确归一化为字符串；输出 SQL 与等价 F3 字符串完全相同 |
| **Phase 1 (F4) plain alias-only 引擎链路** | Java：`{field, as}` 经 `InlineExpressionPreprocessStep` 合成 `CalculatedFieldDef(origin=PLAIN_ALIAS)`，下游全链路通过；`F4ColumnObjectIntegrationTest` 必须含**至少**以下用例（每条均通过 `queryFacade.queryModelData()` 执行真实查询 + 等价原生 SQL 基线 + 逐行比对，CLAUDE.md 集成测试规范）： |
| | (a) `{field:"name", as:"customer_name"}` 真查 —— 验证 alias-only 基本路径；返回数据等价于原生 SQL `SELECT name AS customer_name FROM ...` |
| | (b) `{field:"amount", agg:"sum", as:"total"}` 真查 —— 等价 F3 `"SUM(amount) AS total"` |
| | (c) `{field:"orderDate", agg:"max", as:"lastOrder"}` 真查 —— 非数值聚合 |
| | (d) `{field:"qty", agg:"count_distinct", as:"distinctQty"}` 真查 —— count_distinct lowering 验证（已被现有 `F4ColumnObjectIntegrationTest:122-136` 覆盖） |
| | (e) 字符串 `"name AS customer_name"` 与对象 `{field, as}` 路径产出 schema / row key 完全一致（断言两条路径同行同值） |
| | (f) **混合数组**：`["product$id", {field:"name", as:"productName"}, "SUM(amount) AS total"]` 真查 —— F1 + F4 plain alias + F3 共存 |
| | (g) **groupBy 协同**：`columns:[{field:"name", as:"productName"}], groupBy:["name"]` —— 真查输出按 base 字段分组、按 alias 输出（SQL 形如 `SELECT t0.name AS productName ... GROUP BY t0.name`） |
| | (h) **chain rename**：`columns:[{field:"name", as:"a"}, {field:"a", as:"b"}]` —— `resolveBaseColumnReferences` 链式解析正常，最终 fieldAccess 仍按 base `"name"` 校验 |
| **Phase 1 (F4) 元数据继承（§3.1.2）** | (i) `{field:"amount", as:"revenue"}` 真查后，结果列 `revenue` 的 `caption` = base.caption（如"金额"），不是 "revenue" |
| | (j) `describe_model_internal` 在合成路径之后调用 —— 返回 `calculatedFields` 不含合成项 `revenue`（请求级隔离守护） |
| **Phase 1 (F4) 命名冲突 fail-fast（§3.1.1）** | (k) C1：alias 命中已有 calc field 名 → `COLUMN_ALIAS_COLLIDES_WITH_CALCULATED_FIELD` |
| | (l) C2：alias 命中 QM 物理字段名 → `COLUMN_ALIAS_COLLIDES_WITH_PHYSICAL_FIELD` |
| | (m) C3：同请求多列 alias 重复 → `COLUMN_ALIAS_DUPLICATE` |
| | (n) `{field:"product$id", as:"productId"}` → `COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED`（维度后缀拒绝） |
| **Phase 1 (F4) 错误信息 sanitizer 协同** | (o) base 字段不存在时错误信息以 alias 视角输出（"field 'name' (referenced by alias 'customer_name') not found"），与 `QueryErrorSanitizer` 协同；不暴露 `"name AS alias"` 整串 |
| **Phase 1 (F4) SQL 字符串守护** | (p) 上述每条真查用例除数据比对外，**额外断言生成的 SQL 中无双 alias 串**（不出现 `"name AS X" AS Y` 模式）；可用 `buildSqlOnly()` 取出 SQL 字符串做正则断言 |
| **Phase 2 (F5) 解析与编译（Java 引擎已就绪）** | F5 输出 SQL 与链式 API `<plan>.<field>.as(...)` 完全相同；G10 ON 时 PlanColumnRef 走 `compilePlanColumnRef` 输出 plan-qualified `<alias>.<col>` |
| **Phase 2 (F5) 真实 SQL 数据比对** | `F5ColumnObjectIntegrationTest` 至少：(1) F5 在 join 后消歧；(2) F5 引用 base plan（自引用）；(3) F5 + 聚合；(4) bare-field ambiguity → `JOIN_AMBIGUOUS_COLUMN`；(5) plan 引用未绑定 → `COLUMN_PLAN_NOT_BOUND`。每个用例真实 SQL 数据逐行比对 |
| **plan 谱系遍历** | §5.1 的可见性规则按对象身份判定；4 类 plan（Base / Derived / Join / Union）均覆盖测试 |
| **错误码覆盖** | §6 中所有错误码均有专属测试触发；`COMPOSE_DSL_VALIDATION_*` 仅作内部归类标签 |
| **与 G2 协同** | F5 用户级开放后（含 G10 默认值切换），G2 §3.4 中"🚧 G5 收口后可用"段移除 🚧 标记 |
| **与 fieldAccess / deniedColumns 协同** | F5 通过 `ComposePlanAwarePermissionValidator` plan-routed 校验 `fieldAccess`；deniedColumns 在 SQL 构建后照旧拦截（参 §5.4） |
| **Java / Python 双端 parity** | F4：Java 完整闭环；Python 在 PR5 立项时按 parity 矩阵补齐<br>F5：Java 引擎链路已落地，Python PR5 镜像时同步达成 parity |

## 10. 实施落点

### 10.1 文档更新

- **Phase 1 完成后**：Manual A §2 / §4 落稿（F4 部分）
- **Phase 2 完成后**：Manual A §3.6 后置消歧段落正稿；G2 spec §3.4 中"🚧 G5 收口后可用"段去 🚧；gap tracker G5 标 closed
- query-dsl.md 加 deprecation banner 指向本 spec + Manual A

### 10.2 代码改动 · Phase 1（F4 · v2-patch-2 重估）

::: warning Phase 1 已落地部分（v2-patch 周期完成）
代码勘察确认 **F4 normalize-at-entry 已落地于 worktree HEAD**：

- Java：`engine/compose/plan/ColumnObjectNormalizer.java`（已存在，含 `normalizeColumns` / `normalizeColumnsToStrings`）；`DslQueryFunction.toStringList:317-319`（已委托）；`ScriptRuntime.runScript:147-150`（已挂入）；F4 错误码 `COLUMN_FIELD_REQUIRED` / `COLUMN_AGG_NOT_SUPPORTED` / `COLUMN_AS_TYPE_INVALID` / `COLUMN_FIELD_INVALID_KEY` / `COLUMN_PLAN_NOT_VISIBLE` 占位均已注册；`F4ColumnObjectIntegrationTest.java` 现含 7+ case 包含 count_distinct lowering
- Python：`column_normalizer.py` / `script_runtime._from_dsl:201` / `dsl.py.from_:96` / `tests/compose/plan/test_f4_column_object.py` 已落地

本 v2-patch-2 周期 Phase 1 剩余工作仅为 **plain-alias 引擎 pipeline 补齐 + 元数据继承（§3.1.1 / §3.1.2）**，不再涉及入口 normalize。
:::

| 改动点 | 文件 / 落点 | 估时 |
|-------|-----------|------|
| **`FieldAccessPermissionStep` alias-aware 化**（必，order @-25 早于 preprocess） | `plugins/result_set_filter/FieldAccessPermissionStep.java:128-135`，在 `InlineExpressionParser.parse() == null` 之后、`stripDimensionSuffix` 之前接 `AliasExtractor.extract`，按 base field 走白名单匹配 | 小（约 15-25 行 + 4 测试用例） |
| **`InlineExpressionPreprocessStep` plain-alias 合成分支** | `plugins/result_set_filter/InlineExpressionPreprocessStep.java:100-150`，扩展 `parseAndConvert()`：`parse() == null` 后调 `AliasExtractor.extract`，`hasAlias && simpleField` 合成 `CalculatedFieldDef(name=outputName, expression=expression, origin=PLAIN_ALIAS)`；先做 C1/C2/C3 命名冲突检测；从 base 探测 caption / description 拷贝 | 中等（约 50-80 行 + 8 测试用例覆盖每种冲突 + chain rename + 元数据继承） |
| **`CalculatedFieldDef.origin` transient 字段** | `engine/calculated/CalculatedFieldDef.java`，加枚举 `Origin { USER_DECLARED, INLINE_EXPRESSION, PLAIN_ALIAS }` + `@JsonIgnore transient origin`；preprocess 三处 set | 极小（约 15 行） |
| **维度后缀 `$` 拒绝** | preprocess 内识别 `$` → 抛 `COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED` | 极小 |
| **新错误码注册 + i18n** | `COLUMN_ALIAS_COLLIDES_WITH_CALCULATED_FIELD` / `COLUMN_ALIAS_COLLIDES_WITH_PHYSICAL_FIELD` / `COLUMN_ALIAS_DUPLICATE` / `COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED` | 极小 |
| **`SqlCalculatedFieldProcessor` caption 继承** | `engine/calculated/SqlCalculatedFieldProcessor.java:124-131`，`origin=PLAIN_ALIAS` 时 `caption` 取 `fieldDef.getCaption()`（已被 preprocess 注入），不退默认值 | 极小 |
| **`SemanticServiceV3Impl.createCalculatedFieldInfo` sourceField 段** | line 1711-1738 附近，`origin=PLAIN_ALIAS` 时输出 `sourceField` / `aliasOf` 字段 | 极小（请求级合成不进 metadata，本节为预留） |
| **DEBUG 日志措辞分流** | preprocess `INLINE_EXPRESSION` / `PLAIN_ALIAS` 日志区分 | 极小 |
| **`F4ColumnObjectIntegrationTest` 补全** | §9 用例 (a)-(p) 全覆盖，含 SQL 字符串守护断言 | 中等 |
| **`describe_model_internal` metadata 隔离守护测试** | 集成测试断言：plain-alias 请求后 metadata 不含合成项 | 小 |
| **Manual A 文档** | `docs-site/zh/dataset-model/compose-query/dsl-manual.md` §2.1-§2.2 F4 plain alias 段 + §3.1.2 元数据继承说明 | 小 |
| **Python plain-alias 引擎 pipeline + parity** | 移交 Python PR5 单独立项 | — |

**合计**：中等——比 §10.2 v2-patch 写的"小到中等"略大，主要增量是 §3.1.1 三类冲突检测 + §3.1.2 元数据继承 + §9 验收用例 (a)-(p) 完整覆盖。无新增 spec 改动，全部为实施落点。

### 10.3 代码改动 · Phase 2（F5 · v2-patch-2 重估）

::: warning Phase 2 工作量重估
原 v1/v2 评估假设 F5 全部依赖 G10 spec 落定。代码勘察确认 **G10 PR2 / PR3 / PR4 已落地于 worktree HEAD**（`SchemaDerivation` / `OutputSchema.planProvenance` / `ComposePlanner.compilePlanColumnRef` / `ComposePlanAwarePermissionValidator`），Java F5 引擎链路全在，仅默认 OFF。剩余工作集中在 DSL 入口契合 + 测试 + Python PR5 + 默认值切换决策。
:::

| 端 | 改动点 | 估时 |
|----|-------|------|
| Java | `ScriptRuntime.runScript()` 已支持 `PlanColumnRef` 对象 IR；DSL Map 形态 `{plan:<ref>, field, agg?, as?}` 进入 `ColumnObjectNormalizer` 时识别 `plan` key 并构造 `PlanColumnRef`；`DslQueryFunction.toStringList()` legacy 路径**不支持 F5**（约束写入 spec，明确入口分流） | 小 |
| Java | F5 plan 谱系遍历（`QueryPlan.collectVisiblePlans()` 工具方法）—— 在 normalize 阶段做 §5.1 可见性校验，按对象身份判定 | 中等 |
| Java | F5 错误码 `COLUMN_PLAN_NOT_VISIBLE` / `COLUMN_FIELD_NOT_FOUND` / `COLUMN_PLAN_TYPE_INVALID` 注册（`ComposePlanAwarePermissionValidator` 已抛 `COLUMN_PLAN_NOT_BOUND` / `JOIN_AMBIGUOUS_COLUMN`，本批补 DSL 入口三个） | 极小 |
| Java | `F5ColumnObjectIntegrationTest.java` 新增 —— 4 类 plan + bare-field ambiguity + plan-qualified miss-binding + plan === model 自引用 + chain rename + 真实 SQL 数据比对（CLAUDE.md 集成测试规范） | 中等 |
| Java | `ComposeFeatureFlags.g10Enabled()` 默认值切换决策（Phase 2 收口前需独立评估，含与 G2 / Manual A / LLM 提示词协调） | 极小但需协调 |
| Python PR5（**单独立项**） | `engine/compose/security/ComposePlanAwarePermissionValidator` 镜像；`engine/compose/compilation/` 加 plan-aware compile；错误码 `JOIN_AMBIGUOUS_COLUMN` / `COLUMN_PLAN_NOT_BOUND` / `COLUMN_PLAN_NOT_VISIBLE` 等同步；F5 真实 SQL 测试 | 中等到大 |
| 文档 | Manual A §3.6 后置消歧正稿；G2 spec §3.4 移除 🚧；gap tracker G5 标 closed | 小 |

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
| 2026-04-27 | alias-only 补充方案 | 代码勘察确认 `"field AS alias"` 在 Java 引擎现状会被当作字段名校验，F4 `{field, as}` 不能靠测试规避收口；新增 §3.1.1，明确补 plain alias parser、schema / 权限 base field 校验、SELECT alias 输出、Java/Python parity 测试和 §10.2 工作量调整 |
| 2026-04-27 | v2-patch-2 评审落档 | 三轮评审 + 代码深度勘察后定型：(1) §3.1.1 重写为 **Option A 路线**——plain alias 转合成 `CalculatedFieldDef(origin=PLAIN_ALIAS)`，复用 `engine/compose/schema/AliasExtractor.extract`（已存在），不新增 parser；step 顺序硬约束 `FieldAccessPermissionStep@-25` 必须 alias-aware，不能只改 preprocess；preprocess 阶段三类命名冲突 fail-fast（C1 命中 calc / C2 命中物理字段 / C3 同请求 alias 重复）；维度后缀字段 `$` fail-fast 拒绝；接管顺序 `parse() == null` 之后；(2) **新增 §3.1.2 元数据继承**——`origin=PLAIN_ALIAS` 时从 base 列拷贝 `caption` / `description`，type / formatter / referencedColumns 由引擎推断链路自动继承，metadata 加 `sourceField` / `aliasOf` 段；(3) §6 错误码表新增 `COLUMN_ALIAS_COLLIDES_WITH_CALCULATED_FIELD` / `COLUMN_ALIAS_COLLIDES_WITH_PHYSICAL_FIELD` / `COLUMN_ALIAS_DUPLICATE` / `COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED` / `COLUMN_PLAN_NOT_BOUND`（PR4 已抛）/ `COLUMN_FIELD_INVALID_KEY`，并区分 `JOIN_AMBIGUOUS_COLUMN`（G10 ON）vs `JOIN_OUTPUT_COLUMN_CONFLICT`（G10 OFF legacy）；(4) §4.1 整段重写——G10 PR2/PR3/PR4 已落地于 worktree HEAD（`SchemaDerivation` join 歧义放行 / `OutputSchema.planProvenance` / `ComposePlanner.compilePlanColumnRef` plan-aware SQL / `ComposePlanAwarePermissionValidator`），F5 引擎链路全在但 `g10Enabled()` 默认 OFF；(5) §1.3 G10 状态由"硬阻塞"改为"PR2/3/4 done · PR5 Python 单独立项 · 默认值切换决策独立 follow-up"；(6) §10.2 工作量重估——F4 入口 normalize 已落地，本周期剩 plain-alias 引擎 + 元数据继承 + 完整 §9 用例 (a)-(p)；(7) §10.3 F5 工作量重估——Java 引擎链路已就绪，剩 DSL Map 入口契合 + `F5ColumnObjectIntegrationTest` + Python PR5 + 默认值切换；(8) §8.2 示例区分 G10 ON / OFF 行为；(9) §9 验收 16 个用例展开（alias-only 真查 / 字符串等价 / count_distinct / 混合数组 / groupBy 协同 / chain rename / 元数据 caption 继承 / metadata 隔离 / C1-C3 + 维度后缀拒绝 / 错误信息脱敏 / SQL 字符串守护）—— 全部对齐 worktree CLAUDE.md 集成测试规范 |
