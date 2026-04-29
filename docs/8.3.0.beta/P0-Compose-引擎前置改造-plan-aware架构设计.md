# P0 · Compose 引擎前置改造 · plan-aware 架构设计（G10）

> **状态**：Draft v2 for review
> **目标版本**：8.3.0.beta
> **关联 gap**：[G10](compose-query-manuals-gap-tracker.md#g10)
> **下游解锁**：[G5 Phase 2](P0-SemanticDSL-列项对象语法-后置消歧设计.md) F5 columns / [G11](compose-query-manuals-gap-tracker.md#g11) slice F5 / [G12](compose-query-manuals-gap-tracker.md#g12) groupBy/orderBy F5
> **创建日期**：2026-04-27

## 0. 摘要

本 spec 为 Compose 引擎补全 4 项架构改造，使**plan provenance（计划来源）**在 schema 派生、SQL 编译、权限校验全链路可用。这是 G5 Phase 2 (`{plan, field, as}` F5 后置消歧)、G11、G12 的**硬阻塞前置**。

**4 项改造**：

1. **`SchemaDerivation` 允许 join 输出携带歧义列** —— 用 `isAmbiguous` 标记替代提前 fail
2. **`OutputSchema` / `ColumnSpec` 保留 plan provenance** —— 增加 `planProvenance` 字段
3. **`ComposePlanner` plan-aware 编译** —— 用 `plan → tableAlias` 映射生成限定 SQL
4. **`FieldAccessPermissionStep` plan-routed 校验** —— 按 plan 解析的 binding 路由 fieldAccess

**核心策略**：

- **All-or-nothing**：4 项改造必须**整体生效**，不能分步发布（详见 §2.3）
- **Feature flag 落地**：`foggy.compose.g10.enabled`，默认 false；新代码路径通过 flag 切换；不影响现有单 BaseModelPlan 路径
- **真实 SQL 验收**：每项改造必须有 plan-aware 编译 + plan-routed 权限的真实 SQL 数据比对

## 1. 背景

### 1.1 缘起：G5 v1 评审中暴露的 5 项代码事实

G5 spec v1 设计 F5 后置消歧时，假设 `PlanColumnRef.plan` 在引擎管道中可用。代码核实后发现 5 项与假设矛盾的现实：

| C# | 现实 | Evidence |
|----|------|---------|
| C1 | join schema 阶段抛 `JOIN_OUTPUT_COLUMN_CONFLICT`，F5 没机会触发 | `SchemaDerivation.java:212-223` |
| C2 | join 后 `withSourceModelCleared(c)` 显式清除 plan 归属 | `SchemaDerivation.java:225-234` |
| C3 | `ComposePlanner` 编译时丢弃 `ref.plan()` | `ComposePlanner.java:255-260` |
| C4 | `groupBy / orderBy` 是 `List<String>`（→ G12） | `BaseModelPlan.java:24-25` |
| C5 | `FieldAccessPermissionStep` 假设统一 QM 命名空间，未支持 plan 路由 | `FieldAccessPermissionStep.java:44-83` |

C1-C3 + C5 都属于 plan provenance / plan-aware 处理范畴，由 G10 整体覆盖。C4 是类型迁移问题，归 G12。

### 1.2 与 G5 / G11 / G12 的关系

| 下游 | 依赖 G10 的哪几项？ |
|------|------------------|
| G5 Phase 2 (F5 columns) | 改造 #1 + #2 + #3 + #4（全部） |
| G11 (slice F5) | #1 + #2 + #3 + #4（slice 中的 plan-qualified 引用同样需要全链路 plan-aware） |
| G12 (groupBy/orderBy F5) | #1 + #2 + #3 + #4（同上）+ G12 自身的 `List<String> → List<Object>` 类型迁移 |

G10 是上述 3 个 gap 的**单一硬前置**——任何一个下游 gap 都不能在 G10 落地前开工 Phase 2 / Phase 3 部分。

### 1.3 All-or-nothing 落地策略

为什么不能分步发布？

| 假设的分步发布 | 失败原因 |
|---------------|---------|
| 仅 #1（允许歧义列）| 下游引用歧义列时编译器（#3 未改）仍生成无限定 SQL → 直接 SQL 错误 |
| 仅 #2（保留 provenance）| 字段冗余存在但下游（#3 / #4）不消费 → 无功能价值 |
| 仅 #3（plan-aware 编译）| 没有 #2 的 provenance 来源 → 编译器无 plan→alias 信息可用 |
| 仅 #4（plan-routed 权限）| 没有 #2 的 provenance → 权限验证器无法定位 plan binding |

→ 4 项改造在用户可见行为上**互相依赖**，必须打成一个 PR 集合发布。

## 2. 总览：4 项架构改造

### 2.1 改造列表

| # | 改造 | 主要文件 | 范围 |
|---|------|---------|------|
| 1 | `SchemaDerivation` 允许 join 输出携带歧义列 | `engine/compose/schema/SchemaDerivation.java` + `OutputSchema.java` + `ColumnSpec.java` | 中等 |
| 2 | `OutputSchema` / `ColumnSpec` 保留 plan provenance | `ColumnSpec.java` + `SchemaDerivation.java` | 中等 |
| 3 | `ComposePlanner` plan-aware 编译 | `engine/compose/planner/ComposePlanner.java` + `CompileState` | 中等 |
| 4 | `FieldAccessPermissionStep` plan-routed 校验 | `step/FieldAccessPermissionStep.java` + `PerBaseCompiler.java` + 新增 `PlanFieldAccessContext` | 中等-大 |

### 2.2 改造顺序（依赖图）

```
            ┌──────────────────────────────┐
            │ #2 保留 plan provenance      │ ← 最底层：所有后续改造依赖此字段
            └──────────────┬───────────────┘
                           │
          ┌────────────────┴────────────────┐
          ▼                                 ▼
┌──────────────────────┐         ┌────────────────────┐
│ #1 允许 join 歧义列  │         │ #4 plan-routed 权限│
│ （依赖 #2 字段存在） │         │ （直接消费 #2）    │
└──────────┬───────────┘         └────────────────────┘
           │
           ▼
┌──────────────────────┐
│ #3 plan-aware 编译   │
│ （消费 #2 + 检查 #1）│
└──────────────────────┘
```

**关键路径**：#2 → #1 → #3 ∥ #4

#3 和 #4 可在 #1/#2 完成后并行实施。

### 2.3 兼容性策略：Feature flag

由于 4 项改造 all-or-nothing，引入 feature flag 隔离风险：

```yaml
# application.yml
foggy:
  compose:
    g10:
      enabled: false   # 默认 false；改造完整落地后切 true
```

- **flag = false**（默认）：现有单 BaseModelPlan 路径走原逻辑；多 plan compose 场景在 join 处仍 fail（与今日相同）
- **flag = true**：进入 G10 新路径；4 项改造全部生效；F5 / G11 / G12 等可正常工作

切换后**无 fallback**——fail-fast 原则。生产切换前由测试矩阵充分验证。

## 3. 改造 #1：SchemaDerivation 允许 join 输出携带歧义列

### 3.1 当前状态

`SchemaDerivation.java:212-223`：

```java
TreeSet<String> overlap = new TreeSet<>();
for (String n : leftNames) {
  if (rightNames.contains(n)) overlap.add(n);
}
if (!overlap.isEmpty()) {
  throw new ComposeSchemaException(
    ComposeSchemaErrorCodes.JOIN_OUTPUT_COLUMN_CONFLICT, ...);
}
```

→ 任何 left/right 列名交集，立即拒绝。

### 3.2 目标行为

- 不再在 `deriveJoin()` 内抛 `JOIN_OUTPUT_COLUMN_CONFLICT`
- 改为标记歧义列（`ColumnSpec.isAmbiguous = true`）
- 错误延迟到下游消费时——**派生层引用歧义列且未 plan-qualified 时**才报 `JOIN_AMBIGUOUS_COLUMN`（参 G2 spec §3.6）

### 3.3 API 设计

`ColumnSpec` 增加 `isAmbiguous` 字段：

```java
public final class ColumnSpec {
  // 现有字段保留
  private final String name;
  private final String sourceModel;     // 现有：QM 名（join 后被清除）
  
  // G10 #1 新增
  private final boolean isAmbiguous;    // join 时由两侧同名列产生
  
  public boolean isAmbiguous() { return isAmbiguous; }
  // builder 支持 .isAmbiguous(true)
}
```

`SchemaDerivation.deriveJoin()` 改为：

```java
List<ColumnSpec> merged = new ArrayList<>();
for (ColumnSpec c : leftSchema.columns()) {
  boolean ambig = overlap.contains(c.name());
  merged.add(c.toBuilder()
    .isAmbiguous(ambig)
    // .sourceModel(...) 行为由 #2 决定
    .build());
}
// 右侧对称
return OutputSchema.of(merged);
```

`OutputSchema` 构造时（当前 `:39-55` 拒重复）：放宽为允许歧义列共存，但**完全相同的 ColumnSpec**（含 isAmbiguous + planProvenance 都相同）仍拒绝（防止 plan tree 构造错误）。

#### OutputSchema lookup API 升级（v2 patch · 必须）

当前 `OutputSchema` 用 `Map<String, Integer> indexByName`，天然只能映射到一个列。允许 ambiguous duplicates 后，**仅修改 constructor 不够**——必须配套补 lookup API：

```java
public class OutputSchema {
  // 现有：indexByName 仍保留（非歧义场景的 fast-path）
  
  // G10 #1 新增 lookup API
  /**
   * 返回所有同名列。非歧义时返回单元素 list；歧义时返回多元素 list。
   */
  public List<ColumnSpec> getAll(String name);
  
  /**
   * 该列名在当前 schema 中是否歧义（≥2 个同名列）。
   */
  public boolean isAmbiguous(String name);
  
  /**
   * 要求列名唯一；歧义时抛 OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP（带列名 + 候选 plan provenance 列表）。
   */
  public ColumnSpec requireUnique(String name);
  
  /**
   * 旧 API 兼容 —— 歧义时 fail-fast（同 requireUnique 行为）；
   * 调用方应在已知非歧义场景使用，否则迁移到 getAll / requireUnique。
   */
  public ColumnSpec get(String name);   // 行为变化：歧义时不再静默返回第一个
}
```

**调用方迁移指南**（必须配合本改造同步）：

| 原调用 | 迁移目标 |
|--------|---------|
| `schema.get(name)` 用于已知非歧义场景（base / 派生单源） | 继续用 `get(name)`（fail-fast 行为对该场景透明） |
| `schema.get(name)` 用于可能歧义场景（join 后派生层） | 迁移到 `requireUnique(name)`（语义显式）或 `getAll(name)` + 业务侧消歧 |
| `schema.indexOf(name)` 等基于单 index 的隐式假设 | 全部审计；改用 `getAll(name)` 取所有索引 |

**新增错误码**：

| 错误码 | 触发条件 |
|--------|---------|
| `OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP` | `requireUnique(name)` 或 `get(name)` 命中歧义列；错误消息列出所有候选列的 plan provenance |

### 3.4 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| 现有依赖"OutputSchema 无重复"的代码失效 | `SchemaDerivationTest.java:80-86` 等会失败 | 测试期改为期望 `isAmbiguous=true` 而非异常 |
| 派生层引用歧义列但未消歧 | SQL 编译时生成歧义查询 | 由 #3 在编译期捕获并抛 `JOIN_AMBIGUOUS_COLUMN`（fail-fast） |
| 用户行为变化 | 原本 join 阶段 fail 的查询，现在到下游才 fail | 错误信息改进：fail 时明确提示"上游 join 在列 X 处歧义，请用 plan-qualified 消歧" |

### 3.5 测试面

- 新增：`SchemaDerivationTest::testAmbiguousColumnMarkedNotThrown()` —— 验证 join 不再抛异常
- 新增：`OutputSchemaTest::testAllowsAmbiguousColumnsWhenMarked()` —— 验证 OutputSchema 容许歧义列
- 集成：join 后下游 `dsl({columns: ["name"]})` 仍能在编译时正确报 `JOIN_AMBIGUOUS_COLUMN`

## 4. 改造 #2：OutputSchema / ColumnSpec 保留 plan provenance

### 4.1 当前状态

`SchemaDerivation.java:225-234`：

```java
for (ColumnSpec c : leftSchema.columns()) {
  merged.add(withSourceModelCleared(c));   // ← 清除 sourceModel
}

private static ColumnSpec withSourceModelCleared(ColumnSpec c) {
  if (c.sourceModel() == null) return c;
  return ColumnSpec.builder().sourceModel(null)...build();
}
```

`ColumnSpec.sourceModel` 仅记 QM 名（String）。`PlanColumnRef.plan()` 在 plan 树中存在但从不进入 OutputSchema。

### 4.2 目标行为

`ColumnSpec` 增加 **plan-level** provenance（不是 model 名）：

- join 时**保留**两侧 plan 引用（不调用 `withSourceModelCleared`）
- 派生层可通过 `column.planProvenance()` 反向定位到产生该列的 plan

### 4.3 API 设计

```java
public final class ColumnSpec {
  // 保留现有
  private final String sourceModel;
  
  // G10 #2 新增（注意：不直接持 QueryPlan 引用，避免序列化与 GC 风险，详见 §4.4）
  private final PlanId planProvenance;   // opaque ID
  
  public PlanId planProvenance() { return planProvenance; }
}

// 新增 PlanId —— 单次编译会话内的 transient identity key
public final class PlanId {
  private final int identityHash;       // 仅用于 hash 桶分配
  private final WeakReference<QueryPlan> ref;
  
  public static PlanId of(QueryPlan plan) { ... }
  public QueryPlan resolve() { return ref.get(); }   // 可能为 null（已 GC）
  
  /**
   * 严格按 referent identity 比较 —— equals 不依赖 identityHash。
   * 极低概率 hash 冲突时，equals 仍能正确区分两个不同 plan 对象。
   */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PlanId other)) return false;
    QueryPlan a = this.ref.get();
    QueryPlan b = other.ref.get();
    return a != null && a == b;   // identity equality on the referent
  }
  
  @Override
  public int hashCode() { return identityHash; }
}
```

**PlanId 设计契约**（v2 patch 收紧）：

| 维度 | 规则 |
|------|------|
| **equals** | 严格 `ref.get() == other.ref.get()`（按 referent 对象身份）；**不**用 `identityHash` 决定相等性 |
| **hashCode** | 仅返回 `identityHash`（`System.identityHashCode(plan)`），用于 hash 桶分配 |
| **transient 语义** | 仅在**单次编译会话**内有效；**不可序列化**、**不可跨请求复用**、**不可进入持久 hash / cache key** |
| **GC 行为** | `ref.get()` 返回 null 时（plan 已 GC），equals 必然返回 false；下游做 fail-closed 处理 |

::: warning 为什么 equals 不能依赖 identityHash
`System.identityHashCode(obj)` 在罕见情况下可能两个不同对象产生相同 hash。如果 `equals` 用 `identityHash == other.identityHash` 判定，就会把两个不同 plan 误认为相等 → 路由到同一 alias / binding，产生悄无声息的语义错误。

`equals` 必须按 **referent identity** 判定，identityHash 只参与 hash 表桶定位（即使 hash 撞，equals 仍能正确区分）。
:::

`SchemaDerivation.deriveJoin()` 修改：

```java
// 改造前
merged.add(withSourceModelCleared(c));

// 改造后（接收上下文中的 plan 引用）
merged.add(c.toBuilder()
  .planProvenance(PlanId.of(leftPlan))   // ← 保留 plan 身份
  .build());
```

要求 `deriveJoin()` 签名携带左右 plan 引用：

```java
// 改造前
public OutputSchema deriveJoin(JoinPlan plan, String path) { ... }

// 改造后
public OutputSchema deriveJoin(JoinPlan plan, String path) {
  QueryPlan leftPlan = plan.left();   // 已可访问
  QueryPlan rightPlan = plan.right(); // 已可访问
  // ...
}
```

### 4.4 风险

| 风险 | 缓解 |
|------|------|
| QueryPlan 引用导致序列化失败 | 用 `PlanId`（opaque + weak ref），不直接持 QueryPlan |
| 长生命周期 plan tree 阻 GC | `PlanId` 使用 `WeakReference`；plan 失效时 `resolve()` 返回 null，下游做 fail-closed |
| 现有 `sourceModel == null` 语义模糊 | 改造后 `sourceModel` 仍保留为 QM 名（可能为 null），`planProvenance` 是新维度，不冲突 |
| 反序列化 ColumnSpec 时 PlanId 无法恢复 | 文档化：`PlanId` 不持久化，仅在单次编译会话内有效；ColumnSpec 序列化时 `planProvenance` 字段 transient |

### 4.5 测试面

- `ColumnSpecTest::testPlanProvenancePreserved()` —— 构造 ColumnSpec 后 planProvenance 可访问
- `SchemaDerivationTest::testJoinPreservesPlanProvenance()` —— join 后 schema 列携带 PlanId
- `PlanIdTest::testWeakReferenceBehavior()` —— GC 后 resolve() 返回 null

## 5. 改造 #3：ComposePlanner plan-aware 编译

### 5.1 当前状态

`ComposePlanner.java:255-260`：

```java
if (expr instanceof PlanColumnRef ref) {
  return needsQuoting(ref.name(), dialect)
    ? quoteIdent(ref.name(), dialect)
    : ref.name();   // ← 丢弃 ref.plan()
}
```

→ 即使 PlanColumnRef 携带 plan 引用，编译时只用列名，无 table alias 限定。

### 5.2 目标行为

- 编译期建立 `Map<PlanId, String>` `planToAlias` 映射（plan → 它对应的 CTE / 子查询别名）
- `compileExpression()` 接收 `planToAlias`，PlanColumnRef 编译为 `<alias>.<column>`
- ColumnSpec 标记 `isAmbiguous=true` 但下游引用未 plan-qualified 时，编译期抛 `JOIN_AMBIGUOUS_COLUMN`（fail-fast）

### 5.3 API 设计

`CompileState` 增加：

```java
static final class CompileState {
  // 现有字段保留
  
  // G10 #3 新增
  final Map<PlanId, String> planAliasMap = new HashMap<>();
  // 例：customers plan → "cte_0"，orders plan → "cte_1"
}
```

`compileExpression()` 签名扩展：

```java
public static String compileExpression(
    Object expr,
    String dialect,
    Map<PlanId, String> planAliasMap   // ← 新参数
) {
  if (expr instanceof PlanColumnRef ref) {
    PlanId pid = PlanId.of(ref.plan());
    if (pid != null && planAliasMap.containsKey(pid)) {
      String alias = planAliasMap.get(pid);
      return alias + "." + quoteIdent(ref.name(), dialect);
    }
    // fallback：plan 引用无映射 → 走原逻辑（兼容单 base 场景）
    return needsQuoting(ref.name(), dialect)
      ? quoteIdent(ref.name(), dialect)
      : ref.name();
  }
  // 其他 PlanExpression 子类型递归
}
```

`planAliasMap` 构建时机：在 CTE 单元 (`CteUnit`) 编译开始前，由 `ComposePlanner` 遍历 plan tree 自上而下注册。

### 5.4 风险

| 风险 | 缓解 |
|------|------|
| 编译递归签名变化影响所有调用方 | 一次性更新所有 `compileExpression` 调用；用编译错误强制覆盖 |
| `planAliasMap` 构建顺序错误 | 编译前完整遍历 plan tree 注册；进入 `compileExpression` 时已稳定 |
| 单 base 场景的兼容 | fallback 路径保持原逻辑；不破坏现有测试 |

### 5.5 测试面

- 单元：`ComposePlannerTest::testPlanColumnRefWithAliasMap()` —— 验证 ref.plan() 在 planAliasMap 中有映射时生成 `alias.col`
- 集成：join 歧义列编译产物对比 —— `SELECT cte_0.name AS customer_name, cte_1.name AS order_number FROM cte_0 INNER JOIN cte_1 ON ...`
- 集成：派生层引用 plan-qualified 列的 SQL 编译

## 6. 改造 #4：Compose plan-aware 权限校验子层（v2 patch · 重构落点）

::: warning v1 → v2 修订动机
v1 把 #4 写成"修改 `FieldAccessPermissionStep`"，但该 step 本质是**单 QM 请求路径的全局 step**（`@Order(-25)`），处理平面 `fieldAccess`。把多 plan compose 的 plan-routed 校验直接塞进这个全局 step，会破坏其原有契约（"我假设一个 fieldAccess 集合"）。

v2 改为：**新增 Compose 层独立的 plan-aware 权限校验子层**，放在 ComposePlanner / PerBaseCompiler 路径中；**复用** `FieldAccessPermissionStep` 的字段依赖提取逻辑（pure function），但**不修改**该 step 本身的行为。
:::

### 6.1 当前状态

**单 QM 路径（不变）**：`FieldAccessPermissionStep.java:44-83`

```java
@Order(-25)
public class FieldAccessPermissionStep implements DataSetResultStep {
  @Override
  public int beforeQuery(ModelResultContext ctx) {
    Set<String> fieldAccess = ctx.getFieldAccess();   // 平面白名单
    validateColumns(request.getColumns(), fieldAccess, ...);
    validateSlice(request.getSlice(), fieldAccess, ...);
  }
}
```

**Compose 路径（待补）**：当前 ComposePlanner / PerBaseCompiler 中**没有** plan-aware 权限校验入口。多 plan compose 中各 BaseModelPlan 的 ModelBinding 已通过 `PerBaseCompiler.buildContext()` (`:215-225`) 独立构建，但**没有跨 plan 的 plan-routed 校验逻辑**。

### 6.2 目标行为

- **单 QM 路径**：`FieldAccessPermissionStep` 行为完全不变，与今日一致
- **Compose 路径**：新增 `ComposePlanAwarePermissionValidator`（Java 类）作为 ComposePlanner pre-compile 阶段的子层
  - 输入：plan tree + `PlanFieldAccessContext`（per-plan 绑定信息）
  - 输出：通过 / 抛 `FIELD_ACCESS_DENIED` / `COLUMN_PLAN_NOT_BOUND`
  - 复用：`FieldAccessPermissionStep.extractColumnReferences()` / `resolveBaseColumnReferences()` 等纯函数

### 6.3 API 设计

新增 `PlanFieldAccessContext`：

```java
public class PlanFieldAccessContext {
  // plan → per-plan fieldAccess 白名单
  Map<PlanId, Set<String>> perPlanFieldAccess;
  
  // plan → ModelBinding（含 fieldAccess / deniedColumns / systemSlice）
  Map<PlanId, ModelBinding> planBindings;
  
  /**
   * 根据 PlanColumnRef 的 plan 引用，查找该 plan 对应的 fieldAccess 白名单。
   * @return 该 plan 的 fieldAccess；若无 plan 映射，返回 null（调用方 fail-closed）
   */
  public Set<String> resolveFieldAccess(PlanId planId) { ... }
}
```

新增 `ComposePlanAwarePermissionValidator`（Compose 层独立类）：

```java
public class ComposePlanAwarePermissionValidator {
  /**
   * Compose plan tree 的 plan-aware 权限校验。在 ComposePlanner.compilePlanToSql() 中
   * 在 schema derivation 之后、SQL emission 之前调用。
   *
   * @param plan       根 plan（ComposePlanner 当前编译的 QueryPlan）
   * @param schema     已派生的 OutputSchema（含 isAmbiguous / planProvenance）
   * @param planCtx    PlanFieldAccessContext（perPlanFieldAccess + planBindings）
   * @throws ComposeValidationException FIELD_ACCESS_DENIED / COLUMN_PLAN_NOT_BOUND / JOIN_AMBIGUOUS_COLUMN / COLUMN_FIELD_NOT_FOUND
   */
  public void validate(QueryPlan plan, OutputSchema schema, PlanFieldAccessContext planCtx) {
    for (Object column : extractTopLevelColumns(plan)) {
      PlanColumnRef ref = extractPlanRef(column);   // 复用 FieldAccessPermissionStep 的 parser
      
      if (ref != null && ref.plan() != null) {
        // F5: plan-qualified — 路由到指定 plan 的 binding
        validatePlanQualified(ref, planCtx);
      } else {
        // bare field — 见 §6.4 新规则
        validateBareField(column, schema, planCtx);
      }
    }
  }
  
  private void validatePlanQualified(PlanColumnRef ref, PlanFieldAccessContext planCtx) {
    PlanId pid = PlanId.of(ref.plan());
    Set<String> fa = planCtx.resolveFieldAccess(pid);
    if (fa == null) throw fail("COLUMN_PLAN_NOT_BOUND", ref.plan());
    if (!fa.contains(ref.name())) throw fail("FIELD_ACCESS_DENIED", ref);
  }
}
```

入口位置（ComposePlanner pre-compile 阶段）：

```java
// ComposePlanner.compilePlanToSql() 内
OutputSchema schema = schemaDerivation.derive(plan);
if (foggy.compose.g10.enabled && planCtx != null) {
  planAwareValidator.validate(plan, schema, planCtx);   // ← G10 新增入口
}
// 继续 SQL emission ...
```

### 6.4 bare field 校验规则（v2 patch · 重写）

::: warning v1 → v2 规则修订
v1 规则是"所有 plan 的 fieldAccess 都允许才通过"。v2 修订为**先 schema 唯一解析，再权限校验**——把"歧义"和"权限"分开判定，避免误放行。
:::

bare field（即 `dsl({columns: ["name"]})` 中的字符串列项，未带 plan 引用）的校验顺序：

1. **schema 不存在**：`name` 在当前 plan 的 OutputSchema 中找不到 → 抛 `COLUMN_FIELD_NOT_FOUND`
2. **schema 歧义**：`name` 在 OutputSchema 中 `isAmbiguous(name) == true`（即多个 plan 都有同名列）→ 抛 `JOIN_AMBIGUOUS_COLUMN`，错误消息建议用户用 F5 plan-qualified 形态消歧
3. **schema 唯一**：`name` 在 OutputSchema 中唯一存在 → 通过 `column.planProvenance()` 路由到对应 plan 的 binding，按该 binding 的 `fieldAccess` 校验：
   - 在白名单内：通过
   - 不在：抛 `FIELD_ACCESS_DENIED`

伪代码：

```java
private void validateBareField(Object column, OutputSchema schema, PlanFieldAccessContext planCtx) {
  String fieldName = extractFieldName(column);
  
  // 步骤 1：schema 存在性
  List<ColumnSpec> matches = schema.getAll(fieldName);
  if (matches.isEmpty()) throw fail("COLUMN_FIELD_NOT_FOUND", fieldName);
  
  // 步骤 2：schema 歧义检查
  if (schema.isAmbiguous(fieldName)) {
    throw fail("JOIN_AMBIGUOUS_COLUMN", fieldName, matches);   // 含候选 plan 列表
  }
  
  // 步骤 3：唯一解析后路由权限
  ColumnSpec col = matches.get(0);
  PlanId pid = col.planProvenance();
  if (pid == null) {
    // 单 base 场景没有 provenance —— 走 legacy 平面白名单
    return;   // 由 FieldAccessPermissionStep 在外层校验
  }
  Set<String> fa = planCtx.resolveFieldAccess(pid);
  if (fa == null) throw fail("COLUMN_PLAN_NOT_BOUND", pid);
  if (!fa.contains(fieldName)) throw fail("FIELD_ACCESS_DENIED", fieldName);
}
```

**关键差异（vs v1）**：

| 场景 | v1 行为 | v2 行为 |
|------|---------|---------|
| `name` 在 a.fa 允许 / b.fa 拒绝（歧义+混合） | 报"权限不允许" | 报 `JOIN_AMBIGUOUS_COLUMN`（要求 F5） |
| `name` 在 a.fa 允许 / b.fa 也允许（歧义+全允许） | 通过（**误放行**——schema 仍是歧义） | 报 `JOIN_AMBIGUOUS_COLUMN` |
| `name` 唯一在 a / a.fa 允许 | 通过 | 通过 |
| `name` 唯一在 a / a.fa 拒绝 | 报"权限不允许" | 报 `FIELD_ACCESS_DENIED` |
| `name` 不存在 | 报"权限不允许"（误导） | 报 `COLUMN_FIELD_NOT_FOUND` |

### 6.5 风险

| 风险 | 缓解 |
|------|------|
| Parser 复杂度：从 columns 中提取 PlanColumnRef | 与 G5 F5 共享 parser 工具方法；G5 Phase 2 落地前 G10 内提供基础实现 |
| 性能：每次 column 校验都需 plan→binding 查找 | 用 IdentityHashMap，O(1) 查找；plan tree 一般 ≤10 个 plan，可忽略 |
| 现有 `FieldAccessPermissionStep` 不变 | Compose 路径走新 validator，全局 step 路径走 legacy；零回归 |
| `PlanFieldAccessContext` 在 `ModelResultContext` 上的传递 | 新增 `ModelResultContext.getPlanFieldAccessContext()`，默认 null；Compose path 由 PerBaseCompiler 注入；其他路径保持 null |

### 6.6 测试面

- 单元：`ComposePlanAwarePermissionValidatorTest::testPlanQualifiedRouting()` —— F5 引用按 plan 路由 binding 校验
- 单元：`...testBareFieldUniqueResolution()` —— bare field 唯一时 plan-routed 通过 / 拒绝
- 单元：`...testBareFieldAmbiguousRejected()` —— bare field 歧义时报 `JOIN_AMBIGUOUS_COLUMN`，**不**因权限混合状态影响
- 单元：`...testFailClosedOnUnboundPlan()` —— plan 无 binding 时 fail
- 集成：customers.name (whitelisted) + orders.name (denied) 在同一 join 中，bare `"name"` 走 §6.4 规则
- 集成：legacyBeforeQuery 单 base 场景零回归（`FieldAccessPermissionStep` 行为不变）

## 7. Feature flag 策略

### 7.1 flag 名称与默认值

```yaml
foggy:
  compose:
    g10:
      enabled: false   # 默认关闭
```

也支持启动参数：`--foggy.compose.g10.enabled=true`

### 7.2 切换路径

| 阶段 | flag 状态 | 期望行为 |
|------|----------|---------|
| G10 spec 评审通过 | false（默认） | 现状不变 |
| G10 4 项改造代码合并 | false（默认） | 旧路径仍走 legacy；G10 路径只在 flag=true 时激活；CI 应同时跑 flag=true / false 两个矩阵 |
| 测试矩阵充分验证 | false（默认） | 内部环境切 true 灰度运行；外部用户保持 false |
| 正式开放 | true（默认翻转） | G10 新路径成默认；G5 Phase 2 / G11 / G12 进入实施 |

### 7.3 迁移时间表

| 里程碑 | 时间 |
|-------|------|
| G10 spec 评审通过 | 当前 + 1-2 工作日 |
| 改造 #2（provenance）实施 | spec 通过后 2-3 工作日 |
| 改造 #1（歧义列）实施 | #2 完成后 1-2 工作日 |
| 改造 #3 + #4 并行实施 | #1 完成后 3-5 工作日 |
| 测试矩阵 + 内部灰度 | 全部改造完成后 1-2 周 |
| 默认翻转 + 下游解锁 | 灰度通过后立即 |

总计预估：**10-15 工程日**（v2 patch 重估，不含灰度观察期）。

::: tip v2 工程日重估理由
v1 估 8-12 偏乐观；v2 评审反馈"按双端 + 测试矩阵更像 10-15 工程日"。重估包含：
- OutputSchema lookup API 升级（`getAll` / `isAmbiguous` / `requireUnique`）+ 调用方迁移审计：约 +1-2 工程日
- Compose plan-aware 权限校验子层独立类（不再是 step 改造）：约 +1 工程日
- 双端 parity（Python sync）+ 完整测试矩阵：约 +1 工程日
:::

## 8. 不在 G10 范围

| 类别 | 推迟原因 |
|------|---------|
| **跨数据源 provenance** | 不同 plan 来自不同 DataSource 时，cross-source join 本身在 G2 §3.5 就被拒。G10 仅限单数据源 |
| **动态 plan 发现** | 编译时若 plan 还未注册，planAliasMap 缺失。G10 require plan DAG 在编译前完整 |
| **计算字段表达式内的 plan ref** | `expression: "SUM(left.amount)"` 这种表达式内 plan-qualified，当前 parser 不识别。G10 v1 不处理 |
| **UNION 中的 ambiguous 列** | UNION 设计上是按位置对齐 + sourceModel 清除（`SchemaDerivation.java:168`）。G10 仅在 JOIN 路径开启 ambiguous，UNION 保持现有语义 |
| **Group By / Order By 中的 plan-qualified（即 G12）** | C4 是类型迁移问题，由 G12 单独承担 |

## 9. 验收标准

| 项 | 标准 |
|----|------|
| **现有功能零回归** | 所有现有测试脚本（`derived_query_scenario.js` / `join_scenario.js` / `union_scenario.js` / `real_sql_*_scenario.js`）在 flag=true 和 flag=false 下都继续通过 |
| **改造 #1 单元覆盖** | `SchemaDerivationTest::testAmbiguousColumnMarkedNotThrown` + `OutputSchemaTest::testAllowsAmbiguousColumns` 全绿 |
| **改造 #2 单元覆盖** | `ColumnSpecTest::testPlanProvenancePreserved` + `PlanIdTest::testWeakReferenceBehavior` 全绿 |
| **改造 #3 单元覆盖** | `ComposePlannerTest::testPlanColumnRefWithAliasMap` 全绿 |
| **改造 #4 单元覆盖** | `FieldAccessPermissionStepTest::testPlanRoutedValidation` + `testFailClosedOnUnboundPlan` 全绿 |
| **plan-aware 编译真实 SQL 数据比对** | ≥3 集成测试用例：(1) join 歧义列经 `{plan, field, as}` 消歧后真实查询结果 vs 等价原生 `SELECT a.name, b.name` SQL；(2) 派生层 plan-qualified 引用的真实结果；(3) 多 join 嵌套场景。每个用例通过 `queryFacade.queryModelData()` 真实查询 + 等价原生 SQL 基线 + 逐行比对（CLAUDE.md 集成测试规范） |
| **plan-routed 权限真实 SQL 数据比对** | ≥2 集成测试用例：(1) 正向场景——customers.name (whitelisted) 通过；orders.name 同名但 deny → 真实查询拒绝；(2) bare field 跨 plan 场景。真实结果验证（不能只检查 SQL 字符串） |
| **Java / Python 双端 parity** | 4 项改造在两端测试覆盖一致；plan-aware 编译产出 SQL 双端对等 |
| **Feature flag 行为** | flag=false 时所有路径走 legacy；flag=true 时 G10 路径生效；CI 矩阵覆盖两种状态 |

## 10. 实施落点

### 10.1 工作量估算

| 改造 | 工程日（v2 重估） |
|------|------------------|
| #2 保留 plan provenance（含 PlanId equals/hashCode 契约） | 2-3 |
| #1 允许歧义列 + OutputSchema lookup API 升级 + 调用方迁移审计 | 2-3 |
| #3 plan-aware 编译 | 2-3 |
| #4 Compose plan-aware 权限校验子层（独立类，不改全局 step） | 2-3 |
| 双端 parity（Python sync） | 1-2 |
| 测试矩阵 + 集成测试 | 2-3 |
| **总计** | **10-15 工程日** |

### 10.2 PR 拆分建议

由于 4 项改造 all-or-nothing 落地，代码评审分批：

- **PR 1（v2 修正 · 真零行为变化）**：仅添加类型与字段
  - 新增 `PlanId` 类（含 equals/hashCode 契约）
  - `ColumnSpec` 新增 `planProvenance` 字段（默认 null）+ `isAmbiguous` 字段（默认 false）
  - **不修改 `SchemaDerivation` 任何行为**（`withSourceModelCleared` 继续运行）
  - **不修改 `OutputSchema` 任何行为**（不加 lookup API）
  - 零下游消费者，零行为变化，CI 完全绿
- **PR 2**：`SchemaDerivation` 行为变化（flag-gated）
  - flag=true 路径：不调用 `withSourceModelCleared`，设置 `planProvenance` / `isAmbiguous`
  - flag=false 路径：走原逻辑
  - `OutputSchema` 添加 lookup API（`getAll` / `isAmbiguous` / `requireUnique`）；`get(name)` 行为变化（歧义时 fail-fast）
  - 调用方迁移审计：所有 `schema.get(name)` 调用点扫一遍，可能歧义场景迁移到 `requireUnique` / `getAll`
- **PR 3**：改造 #3（`ComposePlanner` plan-aware 编译 + `planAliasMap`）—— flag-gated
- **PR 4**：改造 #4（`PlanFieldAccessContext` + 新增 `ComposePlanAwarePermissionValidator` Compose 层独立类）—— flag-gated；**不修改** `FieldAccessPermissionStep`
- **PR 5**：测试矩阵 + Python sync + 集成测试

5 个 PR 都合并后，flag 翻转 PR 单独提交（生产配置变更）。

### 10.3 与 G5 Phase 2 的交接

G10 完成（flag=true 默认）后：

1. G5 spec 中"🚧 Phase 2 等 G10 收口"的占位段去除
2. G5 Phase 2 (F5 columns) 实施可立即启动
3. G5 Phase 2 完成后，Manual A §3.6 中"🚧 G5 收口后可用"的后置消歧示例转为正稿
4. G11 / G12 按各自优先级排期

## 11. 评审 Checklist

- [ ] **决策 D1**：4 项改造采用 **all-or-nothing + feature flag** 落地策略 —— 是否合适？还是有更细粒度的渐进路径？
- [ ] **决策 D2**（v2 修订）：`PlanId` equals 严格按 referent identity（`ref.get() == other.ref.get()`），identityHash 仅用于 hash 桶分配；transient 语义（不可序列化、不可跨请求复用、不可进入持久 hash/cache key）—— 是否合适？
- [ ] **决策 D3**（v2 修订）：bare field 校验改为"先 schema 唯一解析，再权限校验"——歧义时直接报 `JOIN_AMBIGUOUS_COLUMN`，不等到权限层；非歧义时按 plan provenance 路由 binding 校验 —— 是否合适？
- [ ] **决策 D4**：UNION 路径 G10 不开启 ambiguous（保持 sourceModel 清除）—— 是否合适？还是应同时支持 UNION ambiguous？
- [ ] **决策 D5**：feature flag `foggy.compose.g10.enabled` 默认 false，灰度通过后翻转 —— 命名与默认值是否合适？
- [ ] **决策 D6**：跨数据源 provenance / 动态 plan 发现 / 表达式内 plan ref 全部推迟 —— 是否有 P0 业务场景被遗漏？
- [ ] **决策 D7**：PR 拆分为 5 个但 all-or-nothing 行为生效 —— 是否合适？还是要单 PR mega-merge？
- [ ] **决策 D8**（v2 修订）：10-15 工程日预估（v1 8-12 偏乐观，v2 重估含 OutputSchema lookup API 升级 + Compose 权限子层独立类 + 双端 parity）—— 是否合理？

## 维护记录

| 日期 | 操作 | 备注 |
|------|------|------|
| 2026-04-27 | 创建 Draft v1 | 基于 G5 v1 评审中暴露的 5 项代码事实 + 架构勘察 agent 的全链路分析；4 项改造覆盖 schema / provenance / 编译 / 权限路由全链路；feature flag + all-or-nothing 落地策略；8-12 工程日预估 |
| 2026-04-27 | 修订 Draft v2 | v1 收到 ⚠️ 条件通过（D1/D4/D5/D6/D7 ✅，D2/D3/D8 修订，#4 落点重构）：(a) §4.3 PlanId 收紧 equals 契约（按 referent identity，identityHash 仅 hash 桶；transient 语义）；(b) §3.3 OutputSchema 补 lookup API 升级（`getAll` / `isAmbiguous` / `requireUnique`）+ 调用方迁移指南 + `OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP` 错误码；(c) §6 改造 #4 重构落点 —— 从"修改 `FieldAccessPermissionStep` 全局 step"改为"新增 Compose 层独立 `ComposePlanAwarePermissionValidator`"，复用字段依赖提取纯函数但不改 step；(d) §6.4 bare field 规则重写 —— 改为"先 schema 唯一解析，再权限校验"，歧义直接报 `JOIN_AMBIGUOUS_COLUMN`，不与权限混合判定；(e) §10.2 PR1 修正为"真零行为变化"（仅加字段类型，不动 SchemaDerivation/OutputSchema 行为）；(f) §10.1 工程日 8-12 → 10-15 重估；(g) §11 D2/D3/D8 文本更新 |
