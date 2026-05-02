---
type: execution-prompt
version: 8.2.0.beta
milestone: M6
target_repo: foggy-data-mcp-bridge (worktree: foggy-data-mcp-bridge-wt-dev-compose)
target_module: foggy-dataset-model
req_id: M6-SQLCompilation-Java
parent_req: P0-ComposeQuery-QueryPlan派生查询与关系复用规范
status: done
drafted_at: 2026-04-22
promoted_at: 2026-04-22 (placeholders filled from Python M6 source)
completed_at: 2026-04-22 (Java M6 · java-ready-for-review)
python_reference_landed_at: 2026-04-22 (foggy-data-mcp-bridge-python compose.compilation subpackage)
python_baseline: 2874 passed / 1 skipped / 1 xfailed (M5 baseline 2709 + 165 M6 passed + 1 F-7 xfail)
java_baseline_before: 1399 passed / 0 failures (M5 baseline)
java_baseline_after: 1537 passed / 1 skipped / 0 failures (sqlite lane · 净增 138 passed + 1 skipped — F-7 占位)
java_new_tests_target: ≥ 100 (Python over-delivered 165 tests; Java mirror can match closer to ~120 but the hard floor is 100)
java_new_tests_actual: 138 passed + 1 skipped（目标 100 · 1.38× 覆盖）
java_new_source_files_target: 7 (compilation subpackage: __init__-equivalent package-info.java + 6 classes)
java_new_source_files_actual: 6（package-info.java + ComposeCompileErrorCodes + ComposeCompileException + PlanHash + PerBaseCompiler + ComposePlanner + ComposeSqlCompiler — 比原 target 少 1 类，CompileOptions 嵌在 ComposeSqlCompiler 内）
java_semantic_service_prereq: Reused existing SemanticQueryServiceV3.generateSql(model, request, context) — it already invokes QueryFacade.buildSqlOnly → beforeQuery pipeline (governance steps: field-access + physical-column + system-slice) and returns SqlGenerationResult. No new public method needed.
---

# Java M6 · Compose Query SQL 编译器 开工提示词（`ready-to-execute`）

## 变更日志

| 版本 | 时间 | 变化 |
|---|---|---|
| draft-ahead-of-python | 2026-04-22 | 初版起草，10 条 `🔄 FILL-AFTER-PYTHON` 占位符 |
| ready-to-execute | 2026-04-22 | Python M6 落地后填充所有占位符，额外发现 Python 侧新增 `build_query_with_governance` 公共方法 → Java 侧需同步加（新增 §Step 0 Prerequisite）|

## Python 参考实现（事实来源 · 已落地）

Python M6 源码已落地在 `foggy-data-mcp-bridge-python`（**注意**：本地 commit 尚未 push，状态为 `python-ready-for-review`；Java 实现启动时 Python 侧预计已 push）：

```
foggy-data-mcp-bridge-python/
├── src/foggy/dataset_model/engine/compose/compilation/
│   ├── __init__.py          — public: compile_plan_to_sql, ComposeCompileError, error_codes
│   ├── error_codes.py       — 4 codes + NAMESPACE + ALL_CODES + VALID_PHASES + is_valid_code/is_valid_phase
│   ├── errors.py            — ComposeCompileError(Exception) 构造期校验 code/phase
│   ├── compiler.py          — compile_plan_to_sql(plan, context, *, semantic_service, bindings=None, model_info_provider=None, dialect="mysql")
│   ├── compose_planner.py   — _compile_any + _compile_base/_derived/_union/_join + dialect_supports_cte + _CompileState (governance_cache + id_cache + hash_cache + depth guard)
│   ├── per_base.py          — compile_base_model(plan, binding, *, semantic_service, alias, governance_cache) 通过 build_query_with_governance
│   └── plan_hash.py         — MAX_PLAN_DEPTH=32 + canonical + plan_hash + plan_depth
├── src/foggy/dataset_model/semantic/service.py
│   └── + build_query_with_governance(model_name, request) → QueryBuildResult  ★ M6 新增公共方法
└── tests/compose/compilation/
    ├── conftest.py
    ├── test_binding_injection.py   — 18 tests
    ├── test_dedup.py                — 16 tests
    ├── test_derived.py              — 13 tests
    ├── test_dialect_fallback.py     — 16 tests
    ├── test_error_codes.py          — 15 tests
    ├── test_join.py                 — 12 tests
    ├── test_per_base.py             — 15 tests
    ├── test_plan_hash.py            — 31 tests
    └── test_union.py                — 12 tests (含 1 xfail for F-7)
```

测试总数：**148 pytest-collected + 参数化展开 ≈ 165 passed + 1 xfailed**（F-7 `CROSS_DATASOURCE_REJECTED` 真实检测延后占位）。

## ★ Step 0 Prerequisite · Java 侧 SemanticService 加公共方法

**这一步必须先完成**，否则 M6 Java 实现无处调用。

Python 侧 r3 原先的 Q4 决策是让 M6 compile 代码调私有 `_build_query`，并把"依赖内部签名"登记为决策记录。**Python 工程师在实现时选择了更优解**：在 `SemanticQueryService` 上新增一个公共方法 `build_query_with_governance(model_name, request) → QueryBuildResult`，内部依次跑 `get_model` → `_apply_query_governance` → `validate_query_fields` → `_build_query`，一次暴露整条 governance → build 链路。这个设计：

- 彻底避开下划线方法依赖（Q4 原来的隐忧消失）
- 对 M7 script runner 也能直接用，不是 M6 独占的小表面
- `ValueError` 分两路抛：`"Model not found: ..."` 路径由 M6 reclassify 成 `MISSING_BINDING(plan-lower)`，其他 `ValueError` 走 `PER_BASE_COMPILE_FAILED(compile)` with `__cause__` 保留；其余 `Exception` 也走 `PER_BASE_COMPILE_FAILED`

**Java 侧对应改造**（M6 的先决 patch，提交时放 M6 commit 里）：

查找 Java 侧 `SemanticService` / `SemanticServiceV3Impl`（**具体路径在 `foggy-dataset-model/src/main/java/.../semantic/service/`，启动时先 grep 一下**），在公开 API 面新增：

```java
public QueryBuildResult buildQueryWithGovernance(String modelName,
                                                  DbQueryRequestDef request) {
    DbQueryModelDef tableModel = getModel(modelName);
    if (tableModel == null) {
        throw new IllegalArgumentException("Model not found: " + modelName);
    }
    // 1. governance 过滤 denied_columns → denied_qm_fields、合并 system_slice、field_access 白名单检查
    GovernanceResult governance = applyQueryGovernance(modelName, request);
    if (governance.hasError()) {
        throw new IllegalArgumentException(governance.error().message());
    }
    request = governance.normalizedRequest();   // governance 可能改写 request
    // 2. 结构字段验证
    FieldValidationError invalid = validateQueryFields(tableModel, request);
    if (invalid != null) {
        throw new IllegalArgumentException(invalid.message());
    }
    // 3. 底层 build
    return buildQuery(tableModel, request);  // 或 _buildQuery / buildQueryInternal，按既有命名来
}
```

以上仅示意；**精确字段名、异常类名、返回类型**按 Java 既有 semantic service 实现对齐。核心要求：

- 公共方法 · 对 M6 compilation 子包可见
- 失败以 `IllegalArgumentException`（Java 惯例；对齐 Python `ValueError`）抛出，M6 compile 层 catch 后映射到 `ComposeCompileException`
- `"Model not found: "` 前缀保留 —— M6 compile 层用这个前缀区分 `MISSING_BINDING` vs `PER_BASE_COMPILE_FAILED`（Python 侧也这么做）

这一步**单独一个 mini-PR 或 M6 commit 的前几 commits**，完成后 Java baseline 仍是 1399（新方法没被调，不影响既有测试），然后才是 M6 compilation 本体。

## 执行位置

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

1. **Python r3 提示词（上游设计文档）**：`docs/8.2.0.beta/M6-SQLCompilation-Python-execution-prompt.md`
   - 读全文；重点 §r3 修订说明 / §流程图 6 张 / §6 阶段拆分 / §决策落地
2. **Python M6 实际落地源码（事实来源 · 逐字对齐）**：
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/compilation/compiler.py` — `compile_plan_to_sql` 入口的 kw-only 参数顺序、docstring 语义
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/compilation/compose_planner.py` — `_compile_any` 调度 / `_compile_base / _derived / _union / _join` / `dialect_supports_cte` / `_MYSQL_LEGACY_ALIASES` + `_MYSQL_MODERN_ALIASES`
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/compilation/per_base.py` — 注入 `field_access` 走 `FieldAccessDef(visible=...)`、`system_slice` 空 list → None、order_by 归一化为 `{field, dir}` dict
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/compilation/plan_hash.py` — `MAX_PLAN_DEPTH=32`, `canonical()` 规则, `plan_hash()` 4 个 plan 类型的 tuple shape
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/compilation/error_codes.py` — **4 codes + NAMESPACE + 2 phases, byte-align 目标**
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/compilation/errors.py` — `ComposeCompileError` 构造期校验 code/phase
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/semantic/service.py:572-638` — **新增** `build_query_with_governance(model_name, request) → QueryBuildResult` 的完整签名 + docstring + 3-step 实现（get_model / _apply_query_governance / validate_query_fields / _build_query）
3. **Python M6 测试参考**（按行为 1:1 镜像，test 命名可以本地化）：
   - `foggy-data-mcp-bridge-python/tests/compose/compilation/test_binding_injection.py` — 18 tests → Java `BindingInjectionTest`
   - `foggy-data-mcp-bridge-python/tests/compose/compilation/test_dedup.py` — 16 tests → Java `PlanDedupTest`（含 id-cache + hash-cache + governance-cache）
   - `foggy-data-mcp-bridge-python/tests/compose/compilation/test_derived.py` — 13 tests → Java `DerivedLoweringTest`
   - `foggy-data-mcp-bridge-python/tests/compose/compilation/test_dialect_fallback.py` — 16 tests → Java `DialectFallbackTest`
   - `foggy-data-mcp-bridge-python/tests/compose/compilation/test_error_codes.py` — 15 tests → Java `ComposeCompileErrorCodesTest`
   - `foggy-data-mcp-bridge-python/tests/compose/compilation/test_join.py` — 12 tests → Java `JoinCompileTest`
   - `foggy-data-mcp-bridge-python/tests/compose/compilation/test_per_base.py` — 15 tests → Java `PerBaseCompileTest`
   - `foggy-data-mcp-bridge-python/tests/compose/compilation/test_plan_hash.py` — 31 tests → Java `PlanHashTest`（含 `canonical` + `plan_depth` + MAX_PLAN_DEPTH boundary）
   - `foggy-data-mcp-bridge-python/tests/compose/compilation/test_union.py` — 12 tests（1 xfail for F-7）→ Java `UnionCompileTest`
   - `foggy-data-mcp-bridge-python/tests/compose/compilation/conftest.py` — fixture 复用模式
4. **主需求**：`docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`
5. **实现规划**：`docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-实现规划.md`
6. **progress.md 决策记录**：`docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-progress.md`
   - 2026-04-22 共 7+ 条 M6 相关决策
   - §Follow-ups F-7（`CROSS_DATASOURCE_REJECTED` 推后）
7. **M1-M5 Java 落地范本**（同模块、同风格）：
   - `foggy-dataset-model/src/main/java/.../engine/compose/security/*.java`（M1 错误码 / SPI）
   - `foggy-dataset-model/src/main/java/.../engine/compose/plan/*.java`（M2 QueryPlan 家族 · `BaseModelPlan.model()` / `columns()` / `slice()` / `groupBy()` / `orderBy()` / `limit()` / `start()` / `distinct()` 等 getter 签名）
   - `foggy-dataset-model/src/main/java/.../engine/compose/sandbox/*.java`（M3 错误码 / 异常类范本）
   - `foggy-dataset-model/src/main/java/.../engine/compose/schema/*.java`（M4 显式 Builder + 工具类）
   - `foggy-dataset-model/src/main/java/.../engine/compose/authority/*.java`（M5 静态工具类范本；M6 形态最接近）
8. **Java 既有可复用资产（v1.3 挂点 + CteComposer）**：
   - `foggy-dataset-model/src/main/java/.../engine/compose/{CteComposer, CteUnit, JoinSpec, ComposedSql}.java` · 与 Python 同源等价（8.2.0.beta 之前就存在），**直接复用**
   - `foggy-dataset-model/src/main/java/.../semantic/domain/DeniedPhysicalColumn.java` · v1.3 物理列黑名单值类型（与 Python `DeniedColumn` 等价）
   - `foggy-dataset-model/src/main/java/.../def/query/request/SliceRequestDef.java` · v1.3 系统 slice 条件
   - `foggy-dataset-model/src/main/java/.../semantic/service/` 下的 `SemanticService`（或 `SemanticServiceV3Impl`）· **在这里加 Step 0 的 `buildQueryWithGovernance` 公共方法**
   - `foggy-dataset-model/src/main/java/.../engine/query/steps/PhysicalColumnPermissionStep.java` · v1.3 物理列权限步骤 order=1100（M6 不改，让 Java semantic service 的 build 链路带动它）

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

### 源码（7 类，全部 `public final` 或 `public interface`）

包 `com.foggyframework.dataset.db.model.engine.compose.compilation`：

```
compilation/
├── ComposeCompileException.java      extends RuntimeException (Python 侧 ComposeCompileError extends Exception)
├── ComposeCompileErrorCodes.java     4 frozen code + NAMESPACE + ALL_CODES + VALID_PHASES + isValidCode + isValidPhase
├── ComposeSqlCompiler.java           public static ComposedSql compilePlanToSql(...) 入口 + CompileOptions Builder
├── ComposePlanner.java               内部状态机 CompileState (governance cache + id cache + hash cache + depth guard) + _compileAny 分派 + _compileBase/_derived/_union/_join + dialectSupportsCte + dialect assertion
├── PerBaseCompiler.java              compileBaseModel(plan, binding, service, alias, governanceCache) — 对齐 Python per_base.compile_base_model，调用 Step 0 的 buildQueryWithGovernance
├── PlanHash.java                     MAX_PLAN_DEPTH=32 + canonical(Object) + planHash(QueryPlan) + planDepth(QueryPlan) + CanonicalPlanTuple（可用 List<Object>）
└── package-info.java                 javadoc 对齐 Python __init__.py module docstring
```

**Python → Java 类型映射参考**：

| Python | Java |
|---|---|
| `Dict[str, ModelBinding]` | `Map<String, ModelBinding>` |
| `Optional[ModelInfoProvider]` | 直接 `ModelInfoProvider`（nullable 参数 + @Nullable javadoc） |
| `Dict[int, CteUnit]` (id-cache) | `IdentityHashMap<QueryPlan, CteUnit>` 或 `Map<Integer, CteUnit>`（用 `System.identityHashCode`） |
| `Dict[CanonicalPlanTuple, CteUnit]` (hash-cache) | `Map<List<Object>, CteUnit>` (List 的 equals/hashCode 对 immutable List 成立) |
| `Dict[Tuple[str, int], QueryBuildResult]` (governance-cache) | `Map<String, QueryBuildResult>`（key 合成 `model + ":" + System.identityHashCode(binding)`） |
| `Tuple[...]` | `List.of(...)` 或自建 record/final class |
| `frozenset` | `Set.of(...)` 或 `Collections.unmodifiableSet(...)` |

### 核心入口签名（对齐 Python `compiler.py` 的 `compile_plan_to_sql`）

```java
public final class ComposeSqlCompiler {

    private ComposeSqlCompiler() { /* utility */ }

    /**
     * Compile a QueryPlan tree to dialect-aware SQL + bind params.
     *
     * <p>Caller patterns:</p>
     * <ol>
     *   <li>One-shot — caller has no bindings yet; M6 calls
     *       {@link AuthorityResolutionPipeline#resolve(QueryPlan, ComposeQueryContext, ModelInfoProvider)}
     *       internally.</li>
     *   <li>Two-step — caller already resolved bindings (e.g. multi-dialect
     *       snapshot, M7 script runner). Passing bindings bypasses the
     *       internal resolve.</li>
     * </ol>
     *
     * <p><b>Caching note (r3 Q2).</b> M6 intentionally does NOT cache the
     * resolved bindings. Callers that invoke compile multiple times on the
     * same plan (e.g. snapshot across dialects) should resolve once
     * externally and pass the same bindings on each call.</p>
     *
     * @throws ComposeCompileException  UNSUPPORTED_PLAN_SHAPE /
     *                                   CROSS_DATASOURCE_REJECTED (test-only) /
     *                                   MISSING_BINDING / PER_BASE_COMPILE_FAILED
     * @throws AuthorityResolutionException  propagated verbatim from M5 when
     *                                        the internal-resolve path is taken
     */
    public static ComposedSql compilePlanToSql(
            QueryPlan plan,
            ComposeQueryContext context,
            CompileOptions opts) {
        // 1) opts.bindings == null → call M5 resolve(plan, context, provider)
        // 2) delegate to ComposePlanner.compileToComposedSql(plan, bindings, service, dialect)
    }

    /** Convenience overload — common one-shot path. */
    public static ComposedSql compilePlanToSql(
            QueryPlan plan,
            ComposeQueryContext context,
            SemanticService semanticService) {
        return compilePlanToSql(plan, context,
                CompileOptions.builder().semanticService(semanticService).build());
    }

    // ---------- CompileOptions (Builder mirrors Python kw-only params) ----------

    public static final class CompileOptions {
        private final SemanticService semanticService;       // ★ required
        private final Map<String, ModelBinding> bindings;     // nullable; null → internal M5 resolve
        private final ModelInfoProvider modelInfoProvider;    // nullable
        private final String dialect;                          // default "mysql" (conservative 5.7-compat)

        // ... Builder with explicit final fields + null guards ...
    }
}
```

**Java vs Python 精确差异（Python 是源头）**：
- Python kw-only `*, semantic_service, bindings=None, ...` → Java `CompileOptions` Builder；`semanticService` 在 Builder 中非空校验
- Python default `dialect="mysql"` → Java 默认 `"mysql"`（同样是 conservative MySQL 5.7-compat，对齐 Python `_MYSQL_LEGACY_ALIASES`）
- Python `Optional[Any]` (ModelInfoProvider) → Java 直接 nullable 参数，Builder 允许 `.modelInfoProvider(null)`
- Python 里 `compile_plan_to_sql` 在 `bindings is None` 时调 `resolve_authority_for_plan(plan, context, model_info_provider=...)` → Java 调 `AuthorityResolutionPipeline.resolve(plan, context, provider)`（M5 Java 已落地）

**重要**：Java semantic service 的具体类型以 worktree 里实际的接口 / 实现为准（可能是 `SemanticService` interface / `SemanticServiceV3Impl` 或其他）。实现前先 `grep -r "public class.*SemanticService\|public interface.*SemanticService" foggy-dataset-model/src/main/java` 确认类型名。

### 4 个错误码 + 1 NAMESPACE（`ComposeCompileErrorCodes`）—— byte-aligned 到 Python `error_codes.py`

| Python 常量 | Java 常量 | 字符串（逐字对齐 · parity test 硬断言） |
|---|---|---|
| `NAMESPACE` | `NAMESPACE` | `compose-compile-error` |
| `UNSUPPORTED_PLAN_SHAPE` | `UNSUPPORTED_PLAN_SHAPE` | `compose-compile-error/unsupported-plan-shape` |
| `CROSS_DATASOURCE_REJECTED` | `CROSS_DATASOURCE_REJECTED` | `compose-compile-error/cross-datasource-rejected` |
| `MISSING_BINDING` | `MISSING_BINDING` | `compose-compile-error/missing-binding` |
| `PER_BASE_COMPILE_FAILED` | `PER_BASE_COMPILE_FAILED` | `compose-compile-error/per-base-compile-failed` |

两个 phase：`"compile"` / `"plan-lower"`。

**不新增**：compose-authority-resolve / compose-schema-error / compose-sandbox-violation。

### 测试（JUnit5 · `@DisplayName` 中文 · 目标 ≥ 100 tests · 镜像 Python 9 个 test 文件）

包 `com.foggyframework.dataset.db.model.engine.compose.compilation`（test 源码根）：

```
PerBaseCompileTest.java               ~15 tests · 镜像 test_per_base.py
DerivedLoweringTest.java              ~13 tests · 镜像 test_derived.py
UnionCompileTest.java                 ~12 tests · 镜像 test_union.py（含 1 个 `@Disabled("F-7 · CROSS_DATASOURCE_REJECTED pending ModelBinding.datasourceId")`）
JoinCompileTest.java                  ~12 tests · 镜像 test_join.py
BindingInjectionTest.java             ~18 tests · 镜像 test_binding_injection.py ★核心权限注入
DialectFallbackTest.java              ~16 tests · 镜像 test_dialect_fallback.py · 含 4 方言 × (single/union/join/derived-chain) snapshot
PlanHashTest.java                     ~31 tests · 镜像 test_plan_hash.py · 含 canonical + planDepth + MAX_PLAN_DEPTH boundary + 4 plan 类型 hash
PlanDedupTest.java                    ~16 tests · 镜像 test_dedup.py · 含 id-cache + hash-cache + governance-cache
ComposeCompileErrorCodesTest.java     ~15 tests · 镜像 test_error_codes.py · parity 断言（4 code + NAMESPACE + 2 phase，跨仓字面对齐）
```

Python 侧 148 tests（参数化展开 165 passed + 1 xfail）；Java 镜像期望 ≥ 100 tests。Java 没有 pytest 参数化，要不要把 Python 的 parametrize 用例展开成独立 `@Test` 方法，按 implementation engineer 的 readability 判断；统一 `@ParameterizedTest` + `@ValueSource` 更接近 Python 结构。

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

## 6 阶段拆分（Java 实现路径 · 对齐 Python 实际落地）

### 6.1 · `BaseModelPlan + DerivedQueryPlan` 编译

**核心决策（Python 实现已确认）**：

- **Base 编译**：`PerBaseCompiler.compileBaseModel(plan, binding, service, alias, governanceCache)` 调 Step 0 的 `service.buildQueryWithGovernance(plan.model(), request) → QueryBuildResult` 一条龙拿 `(sql, params, columns)`，包成 `CteUnit`；governance_cache 按 `(modelName, identityHashCode(binding))` 去重
- **Request 构造**（Java `DbQueryRequestDef`）—— 从 `ModelBinding` 注入 3 字段：
  - `fieldAccess` ← 若 `binding.fieldAccess()` 非 null，包装为 `FieldAccessDef(visible=...)`；null 透传 null
  - `systemSlice` ← `binding.systemSlice()` 空 list 时设 null（Python 侧也是这么做），非空 list 直接设值
  - `deniedColumns` ← `binding.deniedColumns()` 直接设值（Python 侧永远给 `list`，不会 null）
- **Order-by 归一化**：Java 侧 `BaseModelPlan.orderBy()` 是 `List<String>`（"name" 或 "name:desc"）；v1.3 engine 期望 dict/map 形态（`{"field":..., "dir":...}`）。Java 镜像 Python `_to_order_entry(entry)` 逻辑
- **Derived 编译**：`ComposePlanner._compile_derived(plan, state)` 递归调 `_compile_any(plan.source(), state)` 拿到 inner；inner 如果是 `ComposedSql`（union/nested-join 产物）就先 wrap 成 `CteUnit`；outer 用自拼 `SELECT <cols> FROM (<inner_sql>) AS <inner_alias> [WHERE] [GROUP BY] [ORDER BY] [LIMIT] [OFFSET]` 字符串
- **Derived 的 WHERE/ORDER BY/LIMIT 规则**（对齐 Python `_render_outer_select` 和 `_render_slice`）：
  - slice 元素可为 `{field, op, value}` 或 `{field: value}` 快捷式，op 默认 `=`
  - order_by 可为 `"name"` / `"name:desc"` / `{field, dir}`，dir 默认 asc
  - limit/start 内联成整数（不走 parameter binding）—— v1.3 惯例

测试聚焦：15 (per_base) + 13 (derived) = **28 tests**，镜像 Python。

### 6.2 · UnionPlan 编译

- 不走 `CteComposer`；**自拼 SQL**：`"(" + leftSql + ")\nUNION [ALL]\n(" + rightSql + ")"`
- 列数一致性已由 M4 `SchemaDerivation` 校验；M6 不重复
- 参数 left → right 顺序拼接
- `CROSS_DATASOURCE_REJECTED` 本期 **`@Disabled` 占位测试**（一条；对齐 Python `@pytest.mark.xfail`）；真实检测挂 F-7

测试聚焦：**12 tests**（镜像 Python `test_union.py`，其中 1 个 `@Disabled` for F-7）。

### 6.3 · JoinPlan 编译

- 使用 `CteComposer.compose(units, joinSpecs, useCte=dialectSupportsCte(dialect))`
- `JoinOn` → `JoinSpec.onCondition`：`left_alias.left_field <op> right_alias.right_field`（多个 `JoinOn` 用 ` AND ` 连）
- `type` 转 SQL：`inner`→`INNER`, `left`→`LEFT`, `right`→`RIGHT`, `full`→`FULL OUTER`
- **`full` + `sqlite` → `UNSUPPORTED_PLAN_SHAPE`**（phase=plan-lower，消息固定 "JoinPlan(type='full') is not supported on SQLite dialect; use inner/left/right or switch dialects."）
- **Self-join dedup**：如果左右两侧的 `CteUnit.alias()` 相同（来自 id-cache / hash-cache 命中），只把一个 unit 传给 `CteComposer.compose`

测试聚焦：**12 tests**（镜像 Python `test_join.py`）。

### 6.4 · `Map<String, ModelBinding>` 按 BaseModelPlan 注入 v1.3 挂点 ★核心

Java 侧注入路径已**完全确定**：

- 挂点 = Java `DbQueryRequestDef`（v1.3 等价 Python `SemanticQueryRequest`）
- 入口 = Step 0 新增的 `SemanticService.buildQueryWithGovernance(modelName, request)` 公共方法
- `_compile_base` 拿到 `binding = state.bindings().get(plan.model())`，null → 抛 `MISSING_BINDING`
- `_apply_query_governance` + `validate_query_fields` + `_build_query` 这三步由 `buildQueryWithGovernance` 一次跑完（Step 0 的 Java 实现），不在 compile 层单独拆
- **不调 `apply_field_access_to_schema`**（那是 M5 声明 schema 层面，声明 schema 不进 SQL）
- **不在 compile 层做物理列 → QM 字段反查**（`PhysicalColumnMapping.toDeniedQmFields` 由 v1.3 engine 内 `_apply_query_governance` 自调）

**governance_cache 语义**：同一个 `(model_name, binding)` 对（self-join / self-union）只跑一次 governance → build 链路，在 `_CompileState` 里缓存 `QueryBuildResult`。Java 用 `Map<String, QueryBuildResult>` + key = `modelName + ":" + System.identityHashCode(binding)`。

测试聚焦：**18 tests**（镜像 Python `test_binding_injection.py`）。

### 6.5 · CTE vs 子查询方言回退

对齐 Python `dialect_supports_cte(dialect)` 的具体分派：

- `"mysql"` / `"mysql57"` → `useCte=false`（legacy MySQL 5.7 无 CTE）
- `"mysql8"` → `useCte=true`（显式 opt-in）
- `"postgres"` / `"postgresql"` / `"mssql"` / `"sqlserver"` / `"sqlite"` → 委托 Java 侧 `FDialect.supportsCte()`（通常返回 true；与 Python 同源）
- **未知 dialect** → `UNSUPPORTED_PLAN_SHAPE`（plan-lower）提前拒绝（对齐 Python `_assert_dialect`）

**不在 M6 修 `FDialect` 或 `CteComposer`**；只是把 dialect 字符串映射到 `boolean useCte`。

snapshot 归一化复用 Java 侧 `tests/.../parity/SqlNormalizer.java`（M5 parity infra），**不新建**。

测试聚焦：**16 tests**（镜像 Python `test_dialect_fallback.py`），覆盖 4 方言 × (single / union / join / derived-chain)。

### 6.6 · plan-hash 子树去重 + MAX_PLAN_DEPTH guard

- **MVP 档**（id-based 同实例去重）：`IdentityHashMap<QueryPlan, CteUnit>` —— Java 侧等价 Python `Dict[int(id()), CteUnit]`
- **Full 档**（结构等价去重）：`PlanHash.planHash(QueryPlan) → List<Object>`（Java 没有 tuple；用不可变 `List` 键，`List.equals` 递归判等，`List.hashCode` 递归合成）
  - `canonical(value)` 递归：Java `List` → `List.copyOf`；`Map` → 有序 entry list（按 key 排序）；Java 已有 tuple/record → 保持
  - `planHash(plan)` 按 Python 4 个 `(discriminator, ...)` shape 对齐：
    - `BaseModelPlan` → `List.of("base", model, canonical(columns), canonical(slice), canonical(groupBy), canonical(orderBy), limit, start, distinct)`
    - `DerivedQueryPlan` → `List.of("derived", planHash(source), canonical(columns), ...)`
    - `UnionPlan` → `List.of("union", all, planHash(left), planHash(right))`
    - `JoinPlan` → `List.of("join", type, planHash(left), planHash(right), List.of(on.left, on.op, on.right for each))`
  - 未知 `QueryPlan` 子类 → `IllegalArgumentException("planHash received unsupported plan type ...")`
- **`MAX_PLAN_DEPTH = 32`**：`_CompileState.enterDepth()` / `exitDepth()` 计数；进入时 `if depth > MAX_PLAN_DEPTH throw UNSUPPORTED_PLAN_SHAPE (plan-lower)`
- `planDepth(plan)` 辅助函数（测试用，对齐 Python `plan_depth`）：`BaseModelPlan → 1`；其余 `1 + max(children depth)`

测试聚焦：31 (plan_hash) + 16 (dedup) = **47 tests**（镜像 Python `test_plan_hash.py` + `test_dedup.py`）。

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

## 预估规模（r3 → ready-to-execute 更新）

- **Step 0 前置**：Java `SemanticService.buildQueryWithGovernance` 新增公共方法 · ~50 LOC + 5 tests · **0.3 PD**
- **M6 compilation 子包**：~7 类 · ~900 LOC（镜像 Python ~900 LOC + Java Builder / 类型声明略多）
- **测试**：~2000 LOC · **≥ 100 tests**（Python 165；Java 因 parametrize 展开差异大致可压到 100–130）
- **总量**：**2.3 – 2.8 人日**（比 draft-ahead 估算 +0.3，来自 Step 0 前置和 Python 实际超交付量）

**工时分配参考**：

| 阶段 | 估算 | 备注 |
|---|---|---|
| Step 0 · Java 侧 `buildQueryWithGovernance` 加公共方法 | 0.3 PD | **前置 patch**；含 5 条 shape + error 映射 guard test |
| 6.1 per-base + derived compile | 0.5 PD | 包括 order-by 归一化、slice 快捷式、outer SELECT 拼装 |
| 6.2 union compile + `@Disabled(F-7)` | 0.2 PD | 自拼 `(left)\nUNION [ALL]\n(right)` |
| 6.3 join compile | 0.3 PD | 调 `CteComposer.compose` + SQLite full-outer carve-out |
| 6.4 bindings 注入收口 | 0.3 PD | 多数逻辑在 Step 0 里；本阶段是 compile 层 catch + 错误映射 |
| 6.5 4 方言 × (single/union/join/derived-chain) snapshot | 0.3 PD | 复用 M5 `SqlNormalizer` |
| 6.6 plan-hash + MAX_PLAN_DEPTH | 0.3 PD | `List<Object>` 作 key 的 canonical 实现 |
| progress.md + CLAUDE.md 回填 | 0.1 PD | |
| buffer（decision review 往返 + Python parity 对齐 debug） | 0.3 PD | |
| **合计** | **2.6 PD** | |

## Python 落地后的关键事实速查（填充原 🔄 占位符）

原 `draft-ahead-of-python` 版本留的 10 条 `🔄 FILL-AFTER-PYTHON` 占位符，都由 Python 实际源码确认；摘要如下，细节回到 §必读前置 #2 看 Python 源码：

| # | 占位符 | 实际决定 |
|---|---|---|
| 1 | Python 6 个源文件内容 | 已见 §Python 参考实现 |
| 2 | Python 测试 9 个文件 | 已见 §必读前置 #3 — 148 pytest 函数，参数化展开 165 passed + 1 xfail |
| 3 | `CompileOptions` 字段对等 | 一一对应（semantic_service / bindings / model_info_provider / dialect 四字段） |
| 4 | 4 错误码的错误消息模板 | **逐字对齐 Python 源 raise 点的消息字符串**（在 `compose_planner.py` + `per_base.py` grep `raise ComposeCompileError` 看每条消息文本） |
| 5 | `_build_query` Java 等价入口 | **被上游改为新增公共方法 `buildQueryWithGovernance`** — 见 §Step 0 Prerequisite |
| 6 | v1.3 Java 挂点 | 用 `DbQueryRequestDef`（Java 侧等价 Python `SemanticQueryRequest`）+ 新方法 `buildQueryWithGovernance`；不用 `SemanticRequestContext` |
| 7 | 方言 × 组合 golden SQL | 见 Python `tests/compose/compilation/test_dialect_fallback.py` 里的 assert 字符串；Java snapshot 走相同 SQL 归一化后比对 |
| 8 | `plan_hash` canonical 递归 | 见 `plan_hash.py::canonical()`；Java 等价：`List`→`List.copyOf`，`Map`→`List<Map.Entry<?, ?>>` 按 key 排序 |
| 9 | Java `SqlNormalizer.java` 存在性 | ✅ 已存在于 `foggy-dataset-model/src/test/java/.../parity/SqlNormalizer.java`（M5 parity infra），M6 直接复用 |
| 10 | 测试类命名映射 | 见 §必读前置 #3 表格 |

## 完成后需要更新的文档

1. 8.2.0.beta `progress.md` 的 M6 行：`python-ready-for-review / java-pending` → `ready-for-review`（与 Python 行合并为双端完成）
2. 本提示词 `status: ready-to-execute` → `done`（Java 实现完成时），追加 `java_baseline_after`
3. root `CLAUDE.md` 的 "Compose Query M5 Authority 绑定管线" 段之后新增 "Compose Query M6 SQL 编译器" 段（Python + Java 双端一段式；Python 段在 Python M6 签收时先写，Java 段在本提示词升级为 `done` 时追加）
4. 新增决策记录：Java `SemanticService.buildQueryWithGovernance` 公共方法的引入（Step 0 前置）必须登记到 progress.md 决策记录段，说明它和 Python 侧 `SemanticQueryService.build_query_with_governance` 是成对的跨仓契约新增（非 M6 独占）
