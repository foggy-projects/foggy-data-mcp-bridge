---
type: execution-prompt
version: 8.2.0.beta
milestone: M5
target_repo: foggy-data-mcp-bridge (worktree: foggy-data-mcp-bridge-wt-dev-compose)
target_module: foggy-dataset-model
req_id: M5-AuthorityBinding-Java
parent_req: P0-ComposeQuery-QueryPlan派生查询与关系复用规范
status: done
completed_at: 2026-04-22
python_reference_landed_at: 2026-04-21
python_baseline: 2709 passed / 1 skipped
java_baseline_before: 1348 passed / 0 failures (M2+M3+M4 baseline; execution prompt's 1324 was pre-M3)
java_baseline_after: 1399 passed / 0 failures (1348 + 51 new M5 tests, zero regression)
java_new_tests: 51 (AuthorityResolutionPipelineTest 23 + BaseModelPlanCollectorTest 10 + FieldAccessApplierTest 15 + ModelInfoProviderSmokeTest 3)
java_new_source_files: 5 (ModelInfoProvider interface + NullModelInfoProvider + BaseModelPlanCollector + AuthorityResolutionPipeline + FieldAccessApplier)
java_queryplan_visibility_change: baseModelPlans() package-private → public across QueryPlan + 4 subclasses (M5 pipeline lives in a sibling package; Layer-C still enforced by JS sandbox reflective allowlist at M9)
---

# Java M5 · BaseModelPlan 首次使用 hook + authorityResolver.resolve 链路 + 请求级去重 开工提示词

## 执行位置（读在最前）

- **实际工作目录**：`D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge-wt-dev-compose`
- **逻辑仓**：`foggy-data-mcp-bridge`（Compose Query 分支最终会合回 mainline）
- **目前阶段**：8.2.0.beta 所有改动都只在 worktree 里；mainline `foggy-data-mcp-bridge/` 目录 HEAD 还**没有** `engine/compose/authority/` 新包或本提示词
- **本文档里所有 `foggy-data-mcp-bridge/...` 形式的路径**，物理上都定位到 `foggy-data-mcp-bridge-wt-dev-compose/...`
- **Maven 命令**在 worktree 根目录执行：`mvn test -pl foggy-dataset-model ...`

Python 侧参考实现位于 `foggy-data-mcp-bridge-python`（独立仓，非 worktree，路径字面有效）。

## 角色与语境设定

你是 `foggy-data-mcp-bridge` worktree 下 `foggy-dataset-model` 模块的维护者。你要在 Java 侧镜像 Python 已落地的 M5 —— 在 M1 冻结的 `AuthorityResolver` SPI 与 M2 冻结的 `QueryPlan` 对象模型之间，搭起 "BaseModelPlan 首次使用 → 批量 resolve → 请求级去重 → fail-closed" 这一管线。

**重要边界**：

- **不做** SQL 编译（M6 scope）—— 不把 binding 翻译成 SQL / CTE，不处理 `denied_columns`
- **不做** 脚本入口（M7 scope）—— 不构造 `ComposeQueryContext`，不解析 MCP 请求
- **不做** 跨请求缓存（本期显式不做，需求文档明确延后）—— 只做"一个 plan 树一次 resolve"的请求级去重
- **只做** 绑定：`Map<String, ModelBinding>`（键是 QM model name）

## 必读前置

严格按顺序读完再动手：

1. **主需求**：`foggy-data-mcp-bridge/docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`
   - 重点：`§AuthorityResolver SPI`、`§BaseModelPlan 首次使用 hook`、`§请求去重`、`§失败语义`
2. **实现规划**：`foggy-data-mcp-bridge/docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-实现规划.md`
   - 重点：`§M5 交付顺序`、`§FailClosed`
3. **Python 对等实现（事实来源）**：
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/authority/model_info.py` —— `ModelInfoProvider` Protocol + `NullModelInfoProvider` 降级
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/authority/collector.py` —— `collect_base_models(plan)` 首次出现去重
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/authority/resolver.py` —— `resolve_authority_for_plan(plan, context, *, model_info_provider=None)` 核心管线
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/authority/apply.py` —— `apply_field_access_to_schema(schema, binding)` 白名单过滤
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/authority/__init__.py` —— 5 项公开 API
4. **Python 对等测试**（4 个文件 / 51 tests · 你的行为事实来源）：
   - `foggy-data-mcp-bridge-python/tests/compose/authority/test_collect_base_models.py` (10)
   - `foggy-data-mcp-bridge-python/tests/compose/authority/test_resolve_authority_for_plan.py` (28)
   - `foggy-data-mcp-bridge-python/tests/compose/authority/test_apply_field_access.py` (17)
   - `foggy-data-mcp-bridge-python/tests/compose/authority/test_public_api.py` (3)
5. **M1-M4 Java 落地范本**（同模块、同风格）：
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/security/AuthorityResolver.java`（M1 · SPI 接口范本）
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/security/AuthorityResolutionException.java`（M1 · 异常类范本）
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/security/AuthorityErrorCodes.java`（M1 · 7 个 code 常量）
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/plan/*.java`（M2 · QueryPlan 族）
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/schema/*.java`（M4 · 显式 Builder 最近范本）

## 对齐原则（硬要求）

1. **Python 是事实来源**：API 签名、fail-closed 分支、去重规则、`NullModelInfoProvider` 降级行为必须与 Python 逐字符对齐；若 Python 返回 `None`/`Optional[List[str]]`，Java 侧对应返回 `Optional<List<String>>` 或 `List<String>` + javadoc 约定"null 视为空"
2. **延续 M1/M2/M4 显式 Builder + final 字段风格**：不用 Lombok / Record；工具类用 `public static` 方法
3. **错误码 100% 复用 M1**：全部错误码字符串来自 `AuthorityErrorCodes` —— M5 **不**新增错误码；仅**使用** `RESOLVER_NOT_AVAILABLE / UPSTREAM_FAILURE / INVALID_RESPONSE / MODEL_BINDING_MISSING`
4. **不做 SQL 编译**：不调用 `buildSqlOnly` / `JdbcQuery` / `CTE composer`
5. **不碰 `ComposeQueryContext` 的 ctor 约束**：M1 已对 `authorityResolver == null` 做早失败；你在 resolver 管线入口再做一次防御性检查，覆盖调用方绕过 ctor 的场景（比如 Mockito fake）
6. **请求级去重规则**：按 `BaseModelPlan.getModel()` 字符串在整棵树里首次出现去重；左右前序遍历（`QueryPlan.baseModelPlans()` 已保证这一语义）；**不** 考虑 columns / slice / limit 差异
7. **`apply_field_access_to_schema` 只摸 `field_access` 白名单**：`field_access == null` → no-op 返回原 `OutputSchema`；`field_access.isEmpty()` → 空 `OutputSchema`；`field_access = [names]` → 保序过滤；**不** 处理 `deniedColumns`（M6）

## 交付清单

### 源码（5 个 public 类型，全部 `public final` 或 `public interface`）

包 `com.foggyframework.dataset.db.model.engine.compose.authority`：

```
authority/
├── ModelInfoProvider.java          interface · 单方法 Optional<List<String>> getTablesForModel(String modelName, String namespace)
├── NullModelInfoProvider.java      public final · 实现 ModelInfoProvider · 永远返回 Optional.of(List.of())
├── BaseModelPlanCollector.java     public final util · static List<BaseModelPlan> collect(QueryPlan plan)
├── AuthorityResolutionPipeline.java public final util · static Map<String, ModelBinding> resolve(QueryPlan plan, ComposeQueryContext context, ModelInfoProvider provider)
└── FieldAccessApplier.java         public final util · static OutputSchema apply(OutputSchema schema, ModelBinding binding)
```

命名差异速查：

| Python | Java |
|--------|------|
| `collect_base_models(plan)` | `BaseModelPlanCollector.collect(plan)` |
| `resolve_authority_for_plan(plan, context, *, model_info_provider=None)` | `AuthorityResolutionPipeline.resolve(plan, context)` + 3-arg overload with `ModelInfoProvider` |
| `apply_field_access_to_schema(schema, binding)` | `FieldAccessApplier.apply(schema, binding)` |
| `NullModelInfoProvider` | `NullModelInfoProvider` (同名) |
| `ModelInfoProvider` Protocol | `ModelInfoProvider` interface |

### 测试（JUnit5 + `@DisplayName` 中文，镜像 Python 51 tests 一一对应，可合并能合并的）

包 `com.foggyframework.dataset.db.model.engine.compose.authority`（test 源码根）：

```
BaseModelPlanCollectorTest.java          ~10 tests · 单 base / derived 递归 / union 左右序 / 同 QM 去重（union / cross-branch）/ 深树 / TypeError 兜底
AuthorityResolutionPipelineTest.java     ~24 tests · 单模型 / 多模型 join / 去重 / ModelInfoProvider 四种（自定义 / null-returning / NullProvider / 默认）/ fail-closed 五分支（RESOLVER_NOT_AVAILABLE / UPSTREAM_FAILURE 保 cause / AuthorityResolutionException 原样透传 / INVALID_RESPONSE 非 AuthorityResolution / INVALID_RESPONSE 多 key / INVALID_RESPONSE 非 ModelBinding value / MODEL_BINDING_MISSING 按请求顺序）/ phase tag
FieldAccessApplierTest.java              ~8 tests · null → no-op / [] → 空 / 保序 / 未知名忽略 / 重复无害 / ColumnSpec 全字段保留 / denied+slice 不影响 / bad-input NPE/IllegalArgument
ModelInfoProviderSmokeTest.java          ~3 tests · interface 静态断言 / NullProvider 实现 / Java 没 runtime_checkable，用 assignment + method-presence 替代
```

**硬指标**：Java 测试集合 ≥ 40 tests 全绿。

### 反射校验（推荐加一条）

在 `AuthorityResolutionPipelineTest.java` 里写一条反射测试：

```java
@Test
@DisplayName("AuthorityResolutionPipeline 只暴露 resolve 静态方法，不暴露可变状态")
void pipelineSurfaceIsStaticOnly() {
    // 无实例字段 + 无 public ctor
    for (Field f : AuthorityResolutionPipeline.class.getDeclaredFields()) {
        assertTrue(Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers()),
                () -> "Pipeline 不得暴露实例字段 " + f.getName());
    }
    for (Constructor<?> c : AuthorityResolutionPipeline.class.getDeclaredConstructors()) {
        assertFalse(Modifier.isPublic(c.getModifiers()),
                "Pipeline 不得暴露 public ctor —— 它是纯静态工具类");
    }
}
```

## 跨仓错误码 parity 硬对齐表

M5 **不** 新增错误码。使用的 M1 常量：

| Python 常量（来自 `security.error_codes`） | Java 常量（来自 `AuthorityErrorCodes`） | 字符串 |
|------------|-----------|--------|
| `RESOLVER_NOT_AVAILABLE` | `RESOLVER_NOT_AVAILABLE` | `compose-authority-resolve/resolver-not-available` |
| `UPSTREAM_FAILURE` | `UPSTREAM_FAILURE` | `compose-authority-resolve/upstream-failure` |
| `INVALID_RESPONSE` | `INVALID_RESPONSE` | `compose-authority-resolve/invalid-response` |
| `MODEL_BINDING_MISSING` | `MODEL_BINDING_MISSING` | `compose-authority-resolve/model-binding-missing` |
| `IR_RULE_UNMAPPED_FIELD` | `IR_RULE_UNMAPPED_FIELD` | `compose-authority-resolve/ir-rule-unmapped-field` |

所有 M5 抛出的 `AuthorityResolutionException` 都用 `PHASE_AUTHORITY_RESOLVE` = `"authority-resolve"`。

## 行为对齐速查

### `BaseModelPlanCollector.collect(plan)`

- 输入 null 或非 `QueryPlan` → `IllegalArgumentException`（Python 抛 TypeError；Java 习惯 IAE）
- 调用 `plan.baseModelPlans()`（M2 已实现，左右前序），按 `model` 字符串首次出现去重
- 返回 `List<BaseModelPlan>`（不可变 or unmodifiable）
- `baseModelPlans()` 若漏返非 `BaseModelPlan` → `IllegalStateException`（防御性）

### `AuthorityResolutionPipeline.resolve(plan, context)` / `.resolve(plan, context, provider)`

顺序：

1. 若 `context == null` 或 `context.getAuthorityResolver() == null` → 抛 `AuthorityResolutionException(RESOLVER_NOT_AVAILABLE, PHASE_AUTHORITY_RESOLVE, ...)`
2. `List<BaseModelPlan> basePlans = BaseModelPlanCollector.collect(plan)`
3. 若空 → 返回 `Collections.emptyMap()`（防御性；实际 plan 树底一定是 base）
4. `ModelInfoProvider p = (provider != null) ? provider : new NullModelInfoProvider()`
5. 构造 `List<ModelQuery> mqs`：每个 base 查 `p.getTablesForModel(name, ns)`；`Optional.empty()` 或 null 视为 `List.of()`
6. 构造 `AuthorityRequest` 用 `context.getPrincipal() / getNamespace() / getTraceId()`
7. `try { resolution = context.getAuthorityResolver().resolve(request); }`
   - 捕 `AuthorityResolutionException` → 原样 `throw`（不包装）
   - 捕其他 `Exception` → `throw new AuthorityResolutionException(UPSTREAM_FAILURE, message="AuthorityResolver.resolve raised an unexpected exception; see cause", cause=exc, phase=PHASE_AUTHORITY_RESOLVE)`
8. 校验 `resolution instanceof AuthorityResolution` 否则 `INVALID_RESPONSE`
9. 按请求顺序检查每个 model 都在 bindings 中 —— 首个缺失 → `MODEL_BINDING_MISSING(modelInvolved=name)`
10. 检查无多余 key（对 extra key 排序后列进 message）—— 有 → `INVALID_RESPONSE`
11. 检查 `bindings.values()` 全是 `ModelBinding` 实例 —— 否 → `INVALID_RESPONSE(modelInvolved=bad_key)`
12. 返回 `new HashMap<>(resolution.getBindings())` 或 `Map.copyOf(...)` 的不可变拷贝

### `FieldAccessApplier.apply(schema, binding)`

- `schema == null` 或 `binding == null` → `NullPointerException` with clear message（或 IllegalArgumentException —— 任选，Python 抛 TypeError）
- `binding.getFieldAccess() == null` → 原样返回 `schema`（同引用）
- `binding.getFieldAccess().isEmpty()` → 返回 `OutputSchema.of(List.of())`
- 否则按 schema 原序保留 name ∈ `Set.copyOf(fieldAccess)` 的 `ColumnSpec`
- 保留 `ColumnSpec` 的全部字段（`expression` / `sourceModel` / `dataType` / `hasExplicitAlias`）

### `NullModelInfoProvider`

- 无参 public ctor
- `getTablesForModel(name, ns)` 永远返回 `Optional.of(Collections.emptyList())`（或等价 `List.of()`）
- 无可变状态；多次调用安全

### `ModelInfoProvider` interface

```java
public interface ModelInfoProvider {
    /**
     * @return Optional.empty() when model is unknown; Optional.of(list) with
     *         list possibly empty when the model is known but has no
     *         discoverable tables. Callers coerce empty/unknown into List.of().
     */
    Optional<List<String>> getTablesForModel(String modelName, String namespace);
}
```

**注意**：Python 用 `Optional[List[str]]`（`None` 合法，`[]` 合法，两者语义区分弱，都被 coerce 为 `[]`）。Java 为了强类型，用 `Optional<List<String>>`，`Optional.empty()` 与 `Optional.of(List.of())` 在 M5 pipeline 里同样 coerce 为 `List.of()`；host 若希望精确表达差异，可在 M6/M7 扩展——但本期语义等价。

## 测试 fake 速查（建议基础构件）

在测试包内建 `AuthorityTestDoubles.java`，放进：

```java
class EchoResolver implements AuthorityResolver { ... }         // 对每个模型返回空 ModelBinding
class RaisingResolver implements AuthorityResolver { ... }     // 抛 AuthorityResolutionException
class BoomResolver implements AuthorityResolver { ... }         // 抛 RuntimeException
class NonAuthorityResponseResolver implements AuthorityResolver { ... }  // 返回非 AuthorityResolution（需用 raw type / cast hack）
class ExtraKeyResolver implements AuthorityResolver { ... }     // 返回多 key bindings
class MissingKeyResolver implements AuthorityResolver { ... }   // 返回缺 key bindings
class StaticTableProvider implements ModelInfoProvider { ... }   // 预注入 model->tables
class NoneReturningProvider implements ModelInfoProvider { ... } // 永远返回 Optional.empty()
```

## 非目标（禁止做）

- 不做 SQL 编译（M6）
- 不改 `AuthorityResolver` SPI 或 `ModelBinding` 结构（M1 已冻结）
- 不做跨请求 / session 缓存（需求明确延后）
- 不处理 `deniedColumns`（M6，与 `PhysicalColumnMapping` 联动）
- 不改 `QueryPlan` 类家族（M2 已冻结）
- 不改 `OutputSchema` / `ColumnSpec`（M4 已冻结）
- 不新增错误码 —— 复用 M1 `AuthorityErrorCodes`

## 验收硬门槛

1. `mvn test -pl foggy-dataset-model -Dtest='BaseModelPlanCollectorTest,AuthorityResolutionPipelineTest,FieldAccessApplierTest,ModelInfoProviderSmokeTest' -Dspring.profiles.active=sqlite -P!multi-db` 全绿
2. `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db` 全回归，从 1324 基线推进到 1324+N（N ≥ 40），**0 failures**
3. 使用的全部错误码字符串与 `AuthorityErrorCodes` 静态常量一致，**且与 Python `security.error_codes` 字面对齐**
4. 覆盖 5 个 fail-closed 分支（pipeline 里每一条 throw 路径至少一条正向测试）
5. `NullModelInfoProvider` 被显式断言为 `ModelInfoProvider` 实例（Java 版等效 runtime_checkable）
6. 完成后把 8.2.0.beta progress.md 的 M5 行 `python-ready-for-review / java-pending` 更新为 `ready-for-review`，追加 Java 测试基线数字（1324 + N）
7. 本提示词 `status: ready-to-execute` → `status: done` 并填写 `completed_at` + `java_baseline_after`

## 停止条件

- Python 常量 / 校验规则与提示词表不符 → 以 Python 源码为准
- 任何既有测试从绿变红 → 立即停 · 不提交 PR
- 发现 M1 `AuthorityErrorCodes` 常量缺失或拼写不匹配 → 升级为 blocker，不自己补常量（向上报，由 M1 修订流程处理）
- 若发现 `QueryPlan.baseModelPlans()` 在某 plan 子类上语义偏离左右前序 → 停下反推到 M2 修订（不在 M5 里绕过）

## 预估规模

- 源码：5 类 · ~350 LOC（工具类主体 + 2 个薄 interface/impl）
- 测试：~750 LOC · 40+ tests
- 总量：0.75 人日

## 完成后需要更新的文档

1. 8.2.0.beta progress.md 的 M5 行：`ready-for-review`，追加 Java 基线数字
2. 本提示词 `status: ready-to-execute` → `status: done`，填写完成日期 + `java_baseline_after`
3. root CLAUDE.md 的 "Compose Query M4 Schema 推导" 段之后新增 "Compose Query M5 Authority 绑定管线" 段（Python + Java 双侧一段式）
