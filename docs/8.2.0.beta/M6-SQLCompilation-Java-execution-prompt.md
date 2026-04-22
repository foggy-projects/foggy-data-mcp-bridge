---
type: execution-prompt
version: 8.2.0.beta
milestone: M6
target_repo: foggy-data-mcp-bridge (worktree: foggy-data-mcp-bridge-wt-dev-compose)
target_module: foggy-dataset-model
req_id: M6-SQLCompilation-Java
parent_req: P0-ComposeQuery-QueryPlan派生查询与关系复用规范
status: draft-ahead-of-python
drafted_at: 2026-04-22
python_reference_landing: TBD — waits for foggy-data-mcp-bridge-python compose.compilation subpackage merge
java_baseline_before: 1399 passed / 0 failures (M5 baseline)
java_new_tests_target: ≥ 82 (mirror Python r3 target; exact count post-Python)
java_new_source_files_target: ~7 (compilation subpackage, mirror Python r3)
---

# Java M6 · Compose Query SQL 编译器 开工提示词（`draft-ahead-of-python`）

## ⚠️ 本提示词的 draft-ahead 约定（读在最前）

本提示词在 Python 侧 M6 正式落地**之前**起草，目的是把 Java 镜像开工所需的框架先搭好，避免"Python 一落就被串行阻塞"。

- **状态**：`draft-ahead-of-python` — 框架、架构、命名惯例可以 review；但示例 SQL 文本、错误消息精确文本、部分 Python 源码引用路径等**逐字对齐字段**都是占位符
- **升级为 `ready-to-execute` 的触发**：Python 侧 `foggy.dataset_model.engine.compose.compilation` 子包 tests 绿 + 提交 + push 之后，我基于 Python 源码把所有 `🔄 FILL-AFTER-PYTHON: ...` 占位符替换为真实值，状态改为 `ready-to-execute`
- **本期不启动实现**：即便本文档看起来完整，也不得在 Python M6 未落地前动 Java 实现本体代码 —— 2026-04-22 progress.md 决策记录第 6 条约束仍在
- **合规的并行动作**（允许本期就做）：
  - 读懂本提示词框架
  - 读懂 Python r3 prompt（`M6-SQLCompilation-Python-execution-prompt.md`）
  - 读懂 M1/M2/M3/M4/M5 既有 Java 源码范本
  - 检查 Java 侧现有 `CteComposer / CteUnit / JoinSpec` 是否可直接复用（不改写）

## 执行位置（Python M6 落地后解封）

- **实际工作目录**：`D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge-wt-dev-compose`
- **逻辑仓**：`foggy-data-mcp-bridge`（Compose Query 分支最终会合回 mainline）
- **本文档里所有 `foggy-data-mcp-bridge/...` 形式的路径**，物理上都定位到 `foggy-data-mcp-bridge-wt-dev-compose/...`
- **Maven 命令**在 worktree 根目录执行：`mvn test -pl foggy-dataset-model ...`

Python 侧参考实现位于 `foggy-data-mcp-bridge-python`（独立仓，非 worktree）。M6 Java 与 Python 的对等关系 100% 延续 M1–M5 节奏（Python 是事实来源，Java 字面镜像）。

## 角色与语境

你是 `foggy-data-mcp-bridge` worktree 下 `foggy-dataset-model` 模块的维护者。M6 是 Compose Query 首个跨 `BaseModelPlan` 组合 SQL 的里程碑：把 M2 `QueryPlan` 树 + M5 `Map<String, ModelBinding>` → 方言感知的 CTE / 子查询 SQL。

**核心原则**（与 Python r3 提示词一致，不重复论证）：

1. `deniedColumns` / `systemSlice` / `PhysicalColumnMapping` 完全复用 v1.3 既有链路（Java 侧是 `PhysicalColumnPermissionStep` order=1100 的 `QueryExecutionStep`）
2. `fieldAccess` 在 M5 `FieldAccessApplier` 已覆盖 declared schema；M6 只负责把 `ModelBinding.fieldAccess()` 注入 per-base 请求给 v1.3 engine
3. 底层 CTE / 子查询拼装复用 Java 既有的 `com.foggyframework.dataset.db.model.engine.compose.CteComposer`（与 Python 同名同能力）—— 不重写

## 必读前置

严格按顺序读完再动手：

1. **Python r3 提示词（事实来源）**：`docs/8.2.0.beta/M6-SQLCompilation-Python-execution-prompt.md`
   - 读全文；重点 §r3 修订说明 / §流程图 6 张 / §6 阶段拆分 / §决策落地
2. **主需求**：`docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`
3. **实现规划**：`docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-实现规划.md`
4. **progress.md 决策记录**：`docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-progress.md`
   - 2026-04-22 共 6 条 M6 相关决策（v1.3 复用 / Python 先落 / compilation 改名 / `_build_query` 内部依赖 / MAX_PLAN_DEPTH / 不加 bindings 缓存）
   - §Follow-ups F-7（`CROSS_DATASOURCE_REJECTED` 推后）
5. **Python M6 源码落地后的路径**（🔄 FILL-AFTER-PYTHON）：
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/compilation/compiler.py`
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/compilation/per_base.py`
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/compilation/plan_hash.py`
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/compilation/compose_planner.py`
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/compilation/error_codes.py`
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/compilation/errors.py`
6. **Python M6 测试参考**（🔄 FILL-AFTER-PYTHON）：
   - `foggy-data-mcp-bridge-python/tests/compose/compilation/*.py`（具体文件名以 Python 落地为准）
7. **M1-M5 Java 落地范本**（同模块、同风格）：
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/.../engine/compose/security/*.java`（M1 错误码 / SPI）
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/.../engine/compose/plan/*.java`（M2 QueryPlan 家族）
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/.../engine/compose/sandbox/*.java`（M3 错误码 / 异常类范本）
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/.../engine/compose/schema/*.java`（M4 显式 Builder + 工具类）
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/.../engine/compose/authority/*.java`（M5 静态工具类范本；M6 形态最接近）
8. **Java 既有可复用资产（v1.3 挂点 + CteComposer）**：
   - `foggy-dataset-model/src/main/java/.../engine/compose/{CteComposer, CteUnit, JoinSpec, ComposedSql}.java` · 与 Python 同源等价（8.2.0.beta 之前就存在），**直接复用**
   - `foggy-dataset-model/src/main/java/.../semantic/domain/DeniedPhysicalColumn.java` · v1.3 物理列黑名单值类型
   - `foggy-dataset-model/src/main/java/.../semantic/SemanticRequestContext.java` · v1.3 请求上下文（比 Python `SemanticQueryRequest` 更贴近上下文语义；具体 M6 用哪个 Java 入口见 🔄 FILL-AFTER-PYTHON）
   - `foggy-dataset-model/src/main/java/.../engine/query/PhysicalColumnPermissionStep.java` · v1.3 物理列权限步骤 order=1100

## 对齐原则（硬要求）

1. **Python 是事实来源**：API 签名、错误码字符串、错误消息模板（逐字对齐）、常量值（如 `MAX_PLAN_DEPTH = 32`）、fail-closed 分支顺序必须与 Python r3 逐字符对齐
2. **延续 M1/M2/M4/M5 显式 Builder + final 字段风格**：不用 Lombok / Record；工具类用 `public final` + `public static` 方法
3. **错误码 100% 复用 M1 + M6 新增**：
   - M5 `AuthorityResolutionException` 原样透传（来自 resolver）
   - M6 新增 4 个 code + 1 NAMESPACE 常量（见下表）
4. **命名对齐**：Java 包 `com.foggyframework.dataset.db.model.engine.compose.compilation`（与 Python `compose.compilation` 一致；不选 `sqlcompile` 以减少跨仓名词分歧）
5. **不改 M2 `QueryPlan` / M4 `OutputSchema` / M5 `ModelBinding`**：M6 只消费 M1/M2/M4/M5 冻结契约
6. **不改 v1.3 任何 Java 类**：`PhysicalColumnPermissionStep / PhysicalColumnMapping / SemanticRequestContext` 全部原样使用

## 交付清单

### 源码（~7 类，全部 `public final` 或 `public interface`）

包 `com.foggyframework.dataset.db.model.engine.compose.compilation`：

```
compilation/
├── ComposeCompileException.java      extends RuntimeException (Java 习惯；Python 侧是 ComposeCompileError)
├── ComposeCompileErrorCodes.java     4 frozen code + NAMESPACE + ALL_CODES + VALID_PHASES
├── ComposeSqlCompiler.java           public static Map<…> compilePlanToSql(…) 入口
├── BaseModelPlanCompiler.java        每个 BaseModelPlan → CteUnit；v1.3 engine 桥接
├── DerivedPlanLowering.java          DerivedQueryPlan 递归降级 SELECT … FROM (inner) AS alias
├── ComposePlanner.java               QueryPlan → (List<CteUnit>, List<JoinSpec>) · MAX_PLAN_DEPTH guard
└── PlanHash.java                     plan 结构 hash · MVP id-based + Full canonical-tuple
```

文件数 / 命名可以在 Python M6 落地后微调；保留与 Python 1:1 对齐的可行性。

### 核心入口签名（🔄 可能微调 — 以 Python r3 `compile_plan_to_sql` 实际落地为准）

```java
public static ComposedSql compilePlanToSql(
        QueryPlan plan,
        ComposeQueryContext context,
        CompileOptions opts) {
    // opts.semanticService   ★ required
    // opts.bindings           optional — null 触发内部 M5 resolve
    // opts.modelInfoProvider  optional
    // opts.dialect            default "mysql"
}

public static final class CompileOptions {
    private final SemanticQueryService semanticService;   // ★ required · D2 决策
    private final Map<String, ModelBinding> bindings;     // nullable
    private final ModelInfoProvider modelInfoProvider;    // nullable
    private final String dialect;                          // default "mysql"

    // ... Builder ...
}
```

**设计动因**（来自 Python r3 Q2 / Q4）：
- `semanticService` 显式 kw-arg 注入（Python 侧同理），不动 `ComposeQueryContext` M1 冻结契约
- `bindings` 可选：caller 已有 bindings 就跳过 M5 re-resolve（M7 script runner 典型路径）
- **Java 版 Javadoc 必须补同样的 caller-side 外部缓存指引**（Python r3 Q2 落盘点）

Java vs Python 签名差异：Python `compile_plan_to_sql(plan, context, *, semantic_service, bindings, model_info_provider, dialect)` kw-only 参数 → Java 用 `CompileOptions` Builder 模拟 kw-only 语义。

### 4 个错误码 + 1 NAMESPACE（`ComposeCompileErrorCodes`）

🔄 **全部 4 条错误码字符串 + NAMESPACE 字符串以 Python `error_codes.py` 为唯一事实来源；`mvn test` parity test 会做跨仓比对**

| Python 常量 | Java 常量 | 字符串（Python 落地后逐字对齐） |
|---|---|---|
| `NAMESPACE` | `NAMESPACE` | `compose-compile-error` |
| `UNSUPPORTED_PLAN_SHAPE` | `UNSUPPORTED_PLAN_SHAPE` | `compose-compile-error/unsupported-plan-shape` |
| `CROSS_DATASOURCE_REJECTED` | `CROSS_DATASOURCE_REJECTED` | `compose-compile-error/cross-datasource-rejected` |
| `MISSING_BINDING` | `MISSING_BINDING` | `compose-compile-error/missing-binding` |
| `PER_BASE_COMPILE_FAILED` | `PER_BASE_COMPILE_FAILED` | `compose-compile-error/per-base-compile-failed` |

两个 phase：`"compile"` / `"plan-lower"`。

**不新增**：compose-authority-resolve / compose-schema-error / compose-sandbox-violation。

### 测试（JUnit5 · `@DisplayName` 中文 · 目标 ≥ 82 tests · 镜像 Python r3 结构）

包 `com.foggyframework.dataset.db.model.engine.compose.compilation`（test 源码根）：

```
BaseModelPlanCompilerTest.java       ~20 tests · 6.1 (Python 落地后具体 test 名对齐)
DerivedPlanLoweringTest.java          ~8 tests · 6.1 链式降级
ComposeSqlCompilerUnionTest.java     ~12 tests · 6.2
ComposeSqlCompilerJoinTest.java      ~12 tests · 6.3
BindingInjectionTest.java             ~20 tests · 6.4 ★核心权限注入
DialectFallbackTest.java              ~14 tests · 6.5 · 含 derived-chain × 4 方言 snapshot (r3 Q3)
PlanHashTest.java                     ~10 tests · 6.6 · 含 MAX_PLAN_DEPTH boundary (r3 Q5)
ComposeCompileErrorCodesTest.java     parity 断言（4 code + NAMESPACE，跨仓字面对齐）
```

### 反射校验（推荐）

在 `ComposeSqlCompilerTest.java` 或类似测试加一条：

```java
@Test
@DisplayName("ComposeSqlCompiler 只暴露 compilePlanToSql 静态方法，不暴露可变状态")
void compilerSurfaceIsStaticOnly() {
    for (Field f : ComposeSqlCompiler.class.getDeclaredFields()) {
        assertTrue(Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers()),
                () -> "Compiler 不得暴露实例字段 " + f.getName());
    }
    for (Constructor<?> c : ComposeSqlCompiler.class.getDeclaredConstructors()) {
        assertFalse(Modifier.isPublic(c.getModifiers()),
                "Compiler 不得暴露 public ctor");
    }
}
```

## 6 阶段拆分（高层 · 细节对 Python r3 一致 · 🔄 落地后 refined with Python）

### 6.1 · `BaseModelPlan + DerivedQueryPlan` 编译

Java 实现要点（对齐 Python D1/D3/D4 决策）：

- **D1**：per-base 编译走 `SemanticQueryService._build_query(...)` 的 Java 等价入口（🔄 FILL-AFTER-PYTHON · 确认 Python 侧用 `_build_query` 后，查 Java 侧对等方法；可能是 `SemanticQueryServiceImpl.buildQueryOnly(...)` 或 `query(VALIDATE)` — 以能保留 exception `cause` 为硬性约束）
- **D3**：先 `semanticService.getModel(plan.model())` 取 `DbTableModelImpl`
- **D4**：`DerivedQueryPlan` 线性降级为 `SELECT ... FROM (inner) AS alias` 字符串拼装，不走 `CteComposer` outer 包装

测试聚焦（~20）：**见 Python r3 6.1 测试列表，Java 1:1 对等**。

### 6.2 · UnionPlan 编译

SQL-level `UNION` / `UNION ALL`（不走 `JoinSpec`）。`CROSS_DATASOURCE_REJECTED` 本期 **xfail 占位**（对齐 Python D5 决策；`@Disabled` + comment 或 JUnit5 `@DisabledIf`）。

测试聚焦（~12）。

### 6.3 · JoinPlan 编译

使用 `CteComposer.compose(units, joinSpecs, useCte=<by dialect>)`。`full outer join` + SQLite → `UNSUPPORTED_PLAN_SHAPE`。

测试聚焦（~12）。

### 6.4 · `Map<String, ModelBinding>` 按 BaseModelPlan 注入 v1.3 挂点 ★核心

Java 侧注入路径和 Python 不完全同形（见 §对齐原则 #6 对 v1.3 Java 挂点的说明）：

- Python：注入 `SemanticQueryRequest.{field_access, system_slice, denied_columns}` 三字段
- Java：🔄 FILL-AFTER-PYTHON — 具体 Java 挂点由 Python 侧决定是走 `SemanticRequestContext` 还是 `DbQueryRequestDef` 或其他。Java 镜像时以 Python 最终落地的请求构造入口为事实来源，找 Java 等价字段 / Builder method

测试聚焦（~20）：镜像 Python r3 6.4 20 条 test，Java 等价。

### 6.5 · CTE vs 子查询方言回退

4 方言 × (single / union / join) 的 SQL snapshot + **4 方言 × derived-chain snapshot**（r3 Q3 新增维度）。

snapshot 归一化复用 v1.4 已有的 `SqlNormalizer.java`（M5 parity infrastructure），**不新建一套**。

测试聚焦（~14）。

### 6.6 · plan-hash 子树去重 + MAX_PLAN_DEPTH guard

- MVP 档：`IdentityHashMap<QueryPlan, CteUnit>` 或等价 id-based 去重（Java 侧）
- Full 档：`PlanHash` 类实现 `canonicalTuple(plan)` 递归 → `List<?>` → immutable equivalent（Java 侧无 Python tuple；用 `List.of` 或自定义 hashable 包装）
- **MAX_PLAN_DEPTH = 32** 常量（硬对齐 Python r3 Q5）+ 33 层嵌套抛 `UNSUPPORTED_PLAN_SHAPE` 的 boundary test

测试聚焦（~10 + 1 MAX_PLAN_DEPTH boundary）。

## 非目标（禁止做）

全部对齐 Python r3 §非目标：
- 不做跨数据源 union/join（本期 xfail）
- 不做窗口 / exists / lateral / recursive（v1.3 engine 处理）
- 不做内存加工
- 不改 `SemanticQueryRequest` / `PhysicalColumnMapping` / 物理列拦截 step
- 不改 `CteComposer`
- 不做 `toSql() / execute()` 绑定（M7 scope）
- 不做 MCP tool 入口（M7 scope）
- 不实装 M9 沙箱 validator
- 不新增 authority-resolve / schema-error 错误码

## 验收硬门槛（Java 版）

1. `mvn test -pl foggy-dataset-model -Dtest='BaseModelPlanCompilerTest,DerivedPlanLoweringTest,ComposeSqlCompilerUnionTest,ComposeSqlCompilerJoinTest,BindingInjectionTest,DialectFallbackTest,PlanHashTest,ComposeCompileErrorCodesTest' -Dspring.profiles.active=sqlite -P!multi-db` 全绿
2. `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db` 全回归，从 1399 基线推进到 1399+N（N ≥ 82），**0 failures**
3. 4 个错误码 + NAMESPACE 字符串与 Python `compose.compilation.error_codes` 字面对齐 · parity test 在 `ComposeCompileErrorCodesTest.java` 硬断言
4. 4 方言 × single/union/join + derived-chain snapshot 归一化复用 `SqlNormalizer.java`，与 Python 对应 snapshot 结构同构
5. `compilePlanToSql` signature 精确匹配本文档 §核心入口签名（含 `semanticService` 必填）
6. `MAX_PLAN_DEPTH == 32` 常量与 Python 严格一致；33 层 boundary test 报错消息包含 `MAX_PLAN_DEPTH=32` 字样
7. progress.md M6 行：`python-ready-for-review / java-pending` → `ready-for-review`，追加 Java 基线数字（1399 → 1399+N）
8. 本提示词 `status: ready-to-execute` → `status: done`，填写 `completed_at` + `java_baseline_after`
9. changelog 条目

## 停止条件

- Python r3 的任何决策（包名 / 错误码字符串 / MAX_PLAN_DEPTH 值 / `_build_query` 等价 Java 入口等）在 Python 落地时被推翻 → 立即停 · 回到 progress.md 决策记录同步变更，再复启 Java 实现
- 既有 M1–M5 Java 测试从绿变红 → 立即停，0 regression 是硬门槛
- `CteComposer` 在某条用例下产出的 SQL 在 4 方言任一上语法错 → xfail + TODO，不急着在 M6 里修 `CteComposer`

## 预估规模

- 源码：~7 类 · ~800 LOC（镜像 Python r3 ~750 LOC + Java Builder / 类型声明额外体量 5–10%）
- 测试：~1800 LOC · 82+ tests
- 总量：**2 – 2.5 人日**（比 Python r3 的 2.5–3.5 PD 少，因为 Python 已经做掉了所有设计决策；Java 只做 translation + adapter）

**工时分配参考**（🔄 Python 落地后再 refine）：

| 阶段 | 估算 | 备注 |
|---|---|---|
| 6.1 base + derived compile | 0.4 PD | D1/D3/D4 Java 等价入口接入 |
| 6.2 union compile + xfail | 0.2 PD | |
| 6.3 join compile | 0.3 PD | |
| 6.4 bindings 注入 v1.3 Java 挂点 | 0.4 PD | Java 挂点形态可能和 Python 不完全对等 |
| 6.5 4 方言 snapshot | 0.3 PD | 直接复用 M5 `SqlNormalizer` |
| 6.6 plan-hash + MAX_PLAN_DEPTH | 0.3 PD | Java 无 tuple，canonical 表示稍麻烦 |
| progress.md + CLAUDE.md 回填 | 0.1 PD | |
| **合计** | **2.0 PD** | |

## 🔄 FILL-AFTER-PYTHON 占位符清单

这些在 Python M6 落地 + push 后，由 Java 镜像开工的 agent 或我本人按 Python 源码回填；回填完后把 `status` 改成 `ready-to-execute`，删除本段：

- [ ] §必读前置 5 Python 源码 6 个文件的具体内容（尤其是 compiler.py 的 signature 最终形态）
- [ ] §必读前置 6 Python 测试目录完整文件列表
- [ ] §核心入口签名中 `CompileOptions` 字段集合的 Python 对等关系（是否所有字段都能一一对应）
- [ ] §4 个错误码的**错误消息模板**（Python 侧 raise ComposeCompileError 里的 message 模板 literal，逐字符对齐）
- [ ] §6.1 D1 `_build_query` Java 等价入口的精确方法名（在 `SemanticQueryServiceImpl` 或同类 Java 文件内查找）
- [ ] §6.4 Java 侧 v1.3 挂点的最终选择（`SemanticRequestContext` 还是 `DbQueryRequestDef`）
- [ ] §6.5 每个方言每种组合的 **golden SQL snapshot 文本**（从 Python test fixture 中导出）
- [ ] §6.6 Python 侧 `plan_hash` 的 canonical_tuple 递归规则 Java 等价实现细节
- [ ] §验收硬门槛 4 `SqlNormalizer.java` 是否存在于 Java worktree（是：已存在；无需新建）
- [ ] §测试文件命名 8 个测试类对 Python 侧 pytest 文件的精确映射

## 完成后需要更新的文档

1. 8.2.0.beta `progress.md` 的 M6 行：`python-ready-for-review / java-pending` → `ready-for-review`（与 Python 行合并为双端完成）
2. 本提示词 `status: draft-ahead-of-python` → `ready-to-execute`（Python 落地时）→ `done`（Java 实现完成时）
3. root `CLAUDE.md` 的 "Compose Query M5 Authority 绑定管线" 段之后新增 "Compose Query M6 SQL 编译器" 段（Python + Java 双端一段式；等 Python 段先写，Java 段在本提示词升级为 `done` 时追加）
