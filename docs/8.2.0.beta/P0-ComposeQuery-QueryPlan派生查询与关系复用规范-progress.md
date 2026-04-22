---
type: progress
version: 8.2.0.beta
req_id: P0-ComposeQuery
status: in-progress
priority: P0
prerequisite_status: f3-resolved (python + java) · vendored-sync-completed · odoo-pro-m3m4-ready
last_updated: 2026-04-22
---

# 8.2.0.beta Compose Query — Progress

> 状态口径：`not-started` / `in-design` / `in-progress` / `blocked` / `ready-for-review` / `accepted` / `rejected`

## 关联规范文档

- 需求：`P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`
- 实现规划：`P0-ComposeQuery-QueryPlan派生查询与关系复用规范-实现规划.md`
- 代码清单：`P0-ComposeQuery-QueryPlan派生查询与关系复用规范-代码清单.md`
- 沙箱错误码与用例：`P0-ComposeQuery-沙箱白名单错误码与防护用例清单.md`
- 能力评估（只读参考）：`P0-ComposeQuery-固定Schema下业务分析能力对比评估.md`
- **M1 SPI 签名冻结**：`M1-AuthorityResolver-SPI签名冻结-需求.md`
- **M9 三层沙箱防护测试脚手架**：`M9-三层沙箱防护测试脚手架.md`

## 前置依赖清单

| item | 仓库 | 跟踪文档 | 状态 | 阻断的里程碑 |
|---|---|---|---|---|
| F-3 Python 侧修复 | `foggy-data-mcp-bridge-python` v1.6 | `docs/v1.6/acceptance/REQ-P0-BUG-F3-acceptance.md` | ✅ **`accepted`** (2026-04-21 · 2430 passed) | — |
| F-3 Java 侧同步修复 | `foggy-data-mcp-bridge-wt-dev-compose` | 同上 acceptance · `SemanticServiceV3Impl.mergeFieldInfo` + `SemanticServiceV3MultiModelGovernanceTest` 7 tests | ✅ **`accepted`** (2026-04-21 · `foggy-dataset-model` sqlite lane 1246 passed / 0 failures) | — |
| Odoo Pro vendored lib 重新同步 | `foggy-odoo-bridge-pro` | — | ✅ `sync_foggy_vendored.py --check` exit 0（2026-04-21） | — |
| Odoo Pro xfail 撤除 | `foggy-odoo-bridge-pro` | `tests/test_v13_semantic_service_regression.py::test_metadata_keeps_shared_field_for_visible_models` | ✅ regular pass (无 xfail marker) · fast lane 570 passed | — |

## 里程碑追踪

| # | 阶段 | 状态 | 开工日期 | 完成日期 | 阻断原因 | 备注 |
|---|------|------|---------|---------|---------|------|
| M1 | 对象模型与接口签名冻结（`ComposeQueryContext / Principal / AuthorityResolver / AuthorityRequest / AuthorityResolution / ModelBinding` 等 Java+Python 对等） | **`ready-for-review`** | 2026-04-21 | 2026-04-21 | — | **Python + Java 双侧均已落地**。Python `foggy.dataset_model.engine.compose.{context,security}` 包 + 61 个合规测试全绿；Java `com.foggyframework.dataset.db.model.engine.compose.{context,security}` 包 + 49 个 JUnit5 合规测试全绿（sqlite / mysql / postgres 三 lane）；全仓基线 Python **2491 passed / 1 skipped**，Java `foggy-dataset-model` sqlite lane **1134 passed / 0 failures**。复用 v1.3 `DeniedColumn / DeniedPhysicalColumn` 与 `SliceRequestDef`，不新建 compose 专用重复定义 |
| M2 | `QueryPlan` 对象模型（`BaseModelPlan / DerivedQueryPlan / UnionPlan / JoinPlan`）与最小 API（`from / query / union / join / execute / toSql`） | **`ready-for-review`** | 2026-04-21 | 2026-04-21 | — | **Python + Java 双侧均已落地**。Python `foggy.dataset_model.engine.compose.plan` 子包（9 公开类型 + `from_()` 入口）73 tests 全绿，全仓 **2564 passed / 1 skipped**。Java `com.foggyframework.dataset.db.model.engine.compose.plan` 子包（11 个 public 类型：`QueryPlan` 抽象基 + `BaseModelPlan / DerivedQueryPlan / UnionPlan / JoinPlan` + `JoinOn` + `JoinType` + `SqlPreview` + `UnsupportedInM2Exception` + `Dsl.from(FromOptions)` 入口 + `QueryOptions` 链式糖）显式 Builder + final · 6 份 JUnit5 测试共 **76 tests 全绿** · `foggy-dataset-model` sqlite lane **1246 passed / 0 failures**（M1 基线 1134 + 112，0 failed）· Layer-C 白名单在测试中硬断言 forbidden 面缺席 · `execute()/toSql()` 抛 `UnsupportedInM2Exception` 等 M6/M7 |
| M3 | foggy-fsscript `ComposeQueryDialect`（`isKeywordAsIdentifier(FROM, '(')`）+ Layer A 宿主沙箱 | **`ready-for-review`** | 2026-04-21 | 2026-04-21 | — | **Python + Java 双侧均已落地**。Python `COMPOSE_QUERY_DIALECT` 落地 `foggy.fsscript.parser.dialect`（移除 `from` 保留字；`if / return / const` 等保留字不受影响）· `foggy.dataset_model.engine.compose.sandbox` 包含 14 个 sandbox-violation 错误码（Layer A 8 + B 3 + C 3）+ `ComposeSandboxViolationError`（构造期校验 code/phase，暴露 layer/kind/script_location）· 27 tests 全绿（12 dialect + 15 sandbox）· 全仓 **2591 passed / 1 skipped**。Java `com.foggyframework.fsscript.parser.ComposeQueryDialect`（Scanner 钩子 · 只对 `(FROM, '(')` 二字节序列降级为 IDENTIFIER；FROM 后跟空格 / 换行等其他字符保持保留字语义 · 使用现有 `FsscriptDialect` 基类子类模式 · `getName()` = `"compose-query"`）+ `com.foggyframework.dataset.db.model.engine.compose.sandbox` 子包 2 个 public 类型（`ComposeSandboxErrorCodes` 14 frozen code + 7 phase + `ALL_CODES / VALID_PHASES` + `layerOf / kindOf` 静态工具 + `IllegalArgumentException` fail-closed · Python `ValueError` 对等 + `ComposeSandboxViolationException extends RuntimeException` 构造期校验 code+phase + 派生 layer/kind + optional scriptLocation + 3 重载构造器 + cause 传递 + toString 诊断）· 14 个 code 字符串 + 7 个 phase 字符串 + layer/kind 派生逻辑与 Python 逐字节对齐 · 3 份 JUnit5 测试共 **32 tests 全绿**（`ComposeQueryDialectTest` 8 + `ComposeSandboxErrorCodesTest` 13 + `ComposeSandboxViolationExceptionTest` 11）· `foggy-dataset-model` sqlite lane **1348 passed / 0 failures**（M2 基线 1246 + M4 78 + M3 24 = 1348，0 failed；foggy-fsscript 另 +8 tests 独立 lane）· Layer A/B/C 验证器的静态 AST 扫描属于 M9 真正落地范围，本期仅交付契约 |
| M4 | Schema 推导与别名 / 冲突校验 | **`ready-for-review`** | 2026-04-21 | 2026-04-21 | — | **Python + Java 双侧均已落地**。Python `foggy.dataset_model.engine.compose.schema` 子包（`alias.py` / `output_schema.py` / `derive.py` / `errors.py` / `error_codes.py`）67 tests 全绿 · 全仓 **2658 passed / 1 skipped**（M3 基线 2591 + 67，0 failed）。Java `com.foggyframework.dataset.db.model.engine.compose.schema` 子包 7 个 public 类型（`ColumnAliasParts` 值对象 + `AliasExtractor` 静态工具 + `ColumnSpec` 显式 Builder + `OutputSchema` ordered + `ComposeSchemaErrorCodes` 7 frozen code + 2 phase + `ALL_CODES / VALID_PHASES` + `ComposeSchemaException extends RuntimeException` + `SchemaDerivation.derive(QueryPlan)` 静态方法按 4 种 plan 分派）· 7 个 code 字符串、2 个 phase 字符串、28 个保留 token、别名正则、4 种 plan 校验行为与 Python 逐字符对齐 · 4 份 JUnit5 测试共 **78 tests 全绿**（AliasExtractorTest 20 + OutputSchemaTest 16 + SchemaDerivationTest 31 + ComposeSchemaExceptionTest 11）· `foggy-dataset-model` sqlite lane **1324 passed / 0 failures**（M2 基线 1246 + 78，0 failed）· 覆盖 spec §典型示例 1（两段聚合）+ §典型示例 3（join 后派生 + alias 消歧）端到端 derive 成功 · Declared schema only（authority 绑定是 M5；SQL 类型推断是 M6）|
| M5 | BaseModelPlan 首次使用 hook + `authorityResolver.resolve` 链路 + 请求去重 | **`ready-for-review`** | 2026-04-21 | 2026-04-22 | — | **Python + Java 双侧均已落地**。Python `foggy.dataset_model.engine.compose.authority` 子包（`model_info.py` / `collector.py` / `resolver.py` / `apply.py`）· 公开 API 5 项：`ModelInfoProvider` Protocol + `NullModelInfoProvider` 降级 + `collect_base_models` 首次使用去重 + `resolve_authority_for_plan` 批量 resolve + `apply_field_access_to_schema` 白名单过滤 · 5 个 fail-closed 分支（`RESOLVER_NOT_AVAILABLE` / `UPSTREAM_FAILURE` 保留 `__cause__` / `INVALID_RESPONSE` 非 `AuthorityResolution` / `MODEL_BINDING_MISSING` 请求顺序决定性 / `INVALID_RESPONSE` 多 key 或 value 非 `ModelBinding`）· 全仓 **2709 passed / 1 skipped**（M4 基线 2658 + 51 = 2709，0 failed）。Java `com.foggyframework.dataset.db.model.engine.compose.authority` 子包 5 个 public 类型（`ModelInfoProvider` interface `@FunctionalInterface` `Optional<List<String>> getTablesForModel(String, String)` + `NullModelInfoProvider` final 永远返回 `Optional.of(List.of())` + `BaseModelPlanCollector` 静态工具 `collect(QueryPlan) → List<BaseModelPlan>` 按 model 字符串首次出现去重 + `AuthorityResolutionPipeline` 静态工具 `resolve(plan, context)` 与 `resolve(plan, context, provider)` 重载 + `FieldAccessApplier` 静态工具 `apply(OutputSchema, ModelBinding)`）· M5 **不新增错误码**，全部复用 M1 `AuthorityErrorCodes` 的 `RESOLVER_NOT_AVAILABLE / UPSTREAM_FAILURE / INVALID_RESPONSE / MODEL_BINDING_MISSING` · 5 个 fail-closed 分支、请求级去重规则、`NullModelInfoProvider` 降级、`field_access` 三态（null/[]/names）与 Python 逐字节对齐 · 附带修改：`QueryPlan.baseModelPlans()` 从 package-private 升级为 public（M5 管线位于 sibling 包；Layer-C 仍由 JS sandbox 反射白名单在 M9 拦截）· 5 份 JUnit5 测试共 **51 tests 全绿**（`AuthorityResolutionPipelineTest` 23 含反射形态校验 + `BaseModelPlanCollectorTest` 10 + `FieldAccessApplierTest` 15 + `ModelInfoProviderSmokeTest` 3 + `AuthorityTestDoubles` test helpers）· `foggy-dataset-model` sqlite lane **1399 passed / 0 failures**（M3 基线 1348 + 51，0 failed） |
| M6 | SQL 编译器：`query / union / join` 支持 + CTE / 子查询方言回退 | **`python-ready-for-review / java-pending`** | 2026-04-22 | 2026-04-22 | — | **Python 侧已落地**。`foggy.dataset_model.engine.compose.compilation` 子包 7 文件（`__init__` / `errors` / `error_codes` / `plan_hash` / `per_base` / `compose_planner` / `compiler`）· 公开 API 3 项：`compile_plan_to_sql(plan, ctx, *, semantic_service, bindings=None, model_info_provider=None, dialect='mysql') → ComposedSql` + `ComposeCompileError` + `error_codes` 模块 · 错误码 4 + 1 NAMESPACE（`compose-compile-error` · `UNSUPPORTED_PLAN_SHAPE / CROSS_DATASOURCE_REJECTED / MISSING_BINDING / PER_BASE_COMPILE_FAILED`）· Phase 2 种（`plan-lower` / `compile`）· `_build_query` 调用前先调 v1.3 `_apply_query_governance` + `validate_query_fields` 让 binding 三字段（`field_access / system_slice / denied_columns`）真正通过 v1.3 引擎生效 · `DerivedQueryPlan` 递归降级为 `SELECT ... FROM (<inner>) AS <alias>` 线性字符串（不走 CteComposer outer 包装）· 4 方言 CTE vs subquery 回退（mysql 5.7 / mysql8 / postgres / mssql / sqlite）· `MAX_PLAN_DEPTH=32` DOS guard 超限抛 `UNSUPPORTED_PLAN_SHAPE` · MVP id-based + Full 结构性 plan_hash 两档 dedup · 166 tests（164 passed + 2 xfailed）：6.1 base+derived 28 / 6.2 union 12 含 F-7 xfail / 6.3 join 12 / 6.4 binding injection 18 / 6.5 dialect 29 含 4 方言 × derived-chain / 6.6 dedup+depth 16 含 canonical_tuple list guard + Full 档 P1 xfail + error_codes 15 + plan_hash 23 + derived 13 · 全仓 **2873 passed / 1 skipped / 2 xfailed**（M5 基线 2709 + 164 + 2 xfail，0 failed，0 regression）· F-7 `CROSS_DATASOURCE_REJECTED` 真实检测推迟（已登记于 §Follow-ups） |
| M7 | MCP `script` 工具入口（body 仅 `script` 文本，ToolExecutionContext → ComposeQueryContext） | `not-started` | — | — | — | |
| M8 | Odoo Pro `OdooEmbeddedAuthorityResolver` 嵌入接入示范与集成测试 | `partial` | 2026-04-21 | — | Odoo Pro 侧 M3/M4 已 ready-for-review（14+15 tests）；M5 真实 SQL 比对仍等 M6 SQL 编译器落地 | Odoo Pro v1.6 REQ-001 三个里程碑 M1/M2/M3/M4/M7 均 completed |
| M9 | 三层白名单防护测试集（Layer A/B/C 24 条用例，Java+Python 两仓同名落地） | `not-started` | — | — | — | 先 fail-first 落盘，实现补齐后转绿 |
| M10 | 集成测试 + 文档回写 + 签收 | `not-started` | — | — | — | 包含 `docs/8.2.0.beta/acceptance/` 签收记录 |

## 并行度视图

F-3 已于 2026-04-21 accepted · 全部里程碑均已 unblock。M5（集成测试）/ M8（Odoo Pro 嵌入多模型验收）可按节奏推进。

```
                          [M1 SPI 冻结]
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                 │
         [M2 QueryPlan]   [M3 Dialect+A]    [M4 Schema 推导]
              │                 │                 │
              └────────┬────────┴────────┬────────┘
                       │                 │
                 [M6 SQL 编译]     [M9 沙箱测试]
                       │
                  [M7 script 工具]
                       │
         ┌──────────────┴─────────────┐
         │                            │
   [M5 集成测试]                 [M8 Odoo Pro 验收]
   (等 F-3)                     (等 F-3 + vendored sync)
         │                            │
         └──────────────┬─────────────┘
                        │
                   [M10 签收]
```

## 对外可观察交付

| 交付物 | 对应里程碑 | 对外可见时机 |
|--------|----------|-------------|
| `AuthorityResolver` SPI 接口（Java interface + Python Protocol） | M1 | M1 完成即可冻结并发布给下游 |
| `ComposeQueryContext` 对象 | M1 | 同上 |
| `from(...)` 顶层入口 + `QueryPlan` 最小 API | M2 | 技术预览内部可用 |
| `script` MCP 工具（单模型） | M2 + M3 + M4 + M6 + M7 | 可先对外发布单模型功能 |
| `script` MCP 工具（多模型权限安全） | 上述 + M5 + M8 | F-3 已于 2026-04-21 accepted；剩余仅 M5/M8 落地 |
| 三层沙箱防护测试 | M9 | M9 完成即标记 sandbox hardened |
| Odoo Pro 嵌入接入示范 | M8 | F-3 + vendored sync 均已 accepted（2026-04-21），M8 完成即可发布 |

## 风险记录

- ~~R1 F-3 修复超期 → 影响 M5/M8 里程碑；M1-M4/M6/M7/M9 可先行推进以避免线性等待~~（2026-04-21 F-3 已 accepted · 风险解除）
- R2 Java / Python parity 漂移 → M1 SPI 冻结时两仓同 PR 落地；M6 编译器结果用 `FormulaParitySnapshotTest` 做基线
- R3 foggy-fsscript `ComposeQueryDialect` 与 v1.4 已有 `SqlExpressionDialect` 交互冲突 → M3 先对 `ElExpScanner` 改动做最小化验证（仅当 nextChar 为 `(` 时才降级 FROM 关键字）
- R4 脚本沙箱被绕过 → M9 sandbox 测试作为硬验收；不过 layer A 发现的绕过必须在 M10 签收前修复
- R5 Odoo Pro 现有 eager-push 路径回归 → M7/M8 执行时跑 Odoo Pro 475 passed 基线；任何回归阻断 M10

## 决策记录

- 2026-04-21 确定 `from(...)` 作为顶层入口（实现层保留 `dsl(...)` 别名）；DSL body 中 `from:` 字段更名为 `source:`
- 2026-04-21 确定 `AuthorityResolver` SPI 为 batch 协议（`models: [...]`），单模型查询也以长度为 1 的数组下发
- 2026-04-21 确定 `ComposeQueryContext` 为脚本执行唯一上下文入口，脚本不可见 Principal / authorityResolver
- 2026-04-21 确定三层白名单 A/B/C 硬性落地，`compose-sandbox-violation` 统一错误码
- 2026-04-21 确定 `policySnapshotId` 第一版允许空，仅审计用
- 2026-04-21 确定本期不做 Foggy 侧跨请求权限缓存，由宿主 resolver 自决
- 2026-04-21 F-3 从 v1.4 签收 follow-up 升格为 8.2.0.beta blocking 前置依赖
- 2026-04-21 远程 HTTP `HttpAuthorityResolver` 实现延后到 8.3.0，但签名本期冻结
- 2026-04-22 **M6 节奏沿用 M1–M5 惯例**：Python 侧 `compose/compile/`（或同类子包）先落地 + tests 绿 + 提交后，Java 侧才基于 Python 源码写 M6 execution prompt 并镜像实现。本期不提前在 Java 侧开工 M6
- 2026-04-22 **M6 `deniedColumns` / `systemSlice` 复用 v1.3 既有链路，不另起炉灶**：
  - `DeniedPhysicalColumn` 值类型 · `PhysicalColumnPermissionStep`（Java order=1100 beforeExecute）· `PhysicalColumnMapping`（物理列 ↔ QM 字段反查 + error sanitizer）· `SemanticRequestContext.deniedColumns / systemSlice` 均**原样复用**
  - M6 职责收窄为"把 `Map<String, ModelBinding>` 按 `BaseModelPlan` 逐节点拆解，注入每个 base-plan 的 `SemanticRequestContext`，让 v1.3 已有的 step 照常拦截"
  - **M6 不**在 compose 层重新实现物理列黑名单 SQL 改写 / 物理列反查 QM 字段的 sanitizer / 行级谓词合并到 WHERE 等能力
  - M6 真正的复杂度落在 `query / union / join` 的 SQL 组合（CTE vs 子查询方言回退，4 方言）、plan-hash 子树去重、跨数据源 join/union 拒绝、以及把每个 `BaseModelPlan` 的 compile 路径接到既有 `SemanticRequestContext` 挂点
- 2026-04-22 **M6 Python 子包改名 `compile/` → `compilation/`**（r3 评审确认）：避免遮蔽 Python builtin `compile()`；Java 镜像时也选 `engine.compose.compilation` / `engine.compose.sqlcompile` 任一（不选 `compile` 避免撞 `java.lang.Compiler` 习惯）
- 2026-04-22 **M6 Python 实现依赖 `SemanticQueryService._build_query(table_model, request) → QueryBuildResult(sql, params, warnings, columns)` 的内部签名**（r3 Q4）：
  - 选择它的理由是保留 exception `__cause__` 链（相比 `query_model(VALIDATE)` 的 `_error` 字符串化，后者会断链）
  - 本期**不**把 `_build_query` 提升为公开 API —— YAGNI，避免给 M6 单一用例开新公共表面；当 M7 script runner 成为第 2 个 caller 时再议
  - 风险：`_build_query` 签名改动将静默破坏 M6 编译层 —— M6 测试必须覆盖 `QueryBuildResult` 四字段（`sql / params / warnings / columns`）shape 断言
- 2026-04-22 **M6 `MAX_PLAN_DEPTH = 32` DOS guard**（r3 Q5）：`compose_planner.py` 对 `_compile_any` 递归深度加 guard，超过 32 层抛 `UNSUPPORTED_PLAN_SHAPE`。防 M7 script runner 被用户递归 `.query().query()...` 耗尽 executor 线程。实际 Compose Query 典型深度 3–5，32 是充足余量。
- 2026-04-22 **M6 跨请求 binding 缓存维持 2026-04-21 "不做"决策**（r3 Q2 延续）：`compile_plan_to_sql` 不内部缓存 `bindings`；caller 若需要多次 compile 同一 plan（explain + execute / 多方言 compile），自行 `resolve_authority_for_plan()` 一次后传 `bindings=...` 复用。docstring 必须写明使用指引。
- 2026-04-22 **M6 Java 提示词准许 `draft-ahead-of-python` 小度并行**：Java 侧开工提示词 `M6-SQLCompilation-Java-execution-prompt.md` 允许在 Python M6 落地前先起草（框架 / 架构 / 命名惯例对齐 Python r3），状态标 `draft-ahead-of-python`。Python M6 push 后由 Java 镜像 agent 填充 `🔄 FILL-AFTER-PYTHON` 占位符（错误消息模板、snapshot 文本、`_build_query` Java 等价入口、v1.3 Java 挂点选择等），状态升为 `ready-to-execute`。**Java 实现本体仍严格等 Python M6 push 后才启动**——该条既不推翻也不弱化 2026-04-22 第 6 条决策（"本期不提前在 Java 侧开工 M6"），只是把"开工"收窄到"实现代码"，把"写提示词"单独允许并行。
- 2026-04-22 **`SemanticQueryService.build_query_with_governance` 升格为公共方法**（Python 侧已实现 · Java 侧 M6 前置）：r3 Q4 原决策是 M6 调私有 `_build_query`，并把"依赖内部签名"登记到决策记录。Python 实现时选择了更优解——在 `SemanticQueryService` 新增公共方法 `build_query_with_governance(model_name, request) → QueryBuildResult`，内部依次跑 `get_model` → `_apply_query_governance` → `validate_query_fields` → `_build_query`，一次暴露整条 governance → build 链路：
  - 好处：彻底避开 Q4 原先的"依赖私有方法"隐忧；对 M7 script runner 也能直接用，不是 M6 独占
  - Python 失败语义：`ValueError("Model not found: ...")` 路径由 M6 reclassify 成 `MISSING_BINDING`（plan-lower）；其他 `ValueError` 或 `Exception` 包装为 `PER_BASE_COMPILE_FAILED`（compile）并保留 `__cause__`
  - **Java 侧对等动作**：Java `SemanticService`（或等价 interface / impl）必须同步新增 `QueryBuildResult buildQueryWithGovernance(String modelName, DbQueryRequestDef request)` 公共方法，内部依次调 `getModel` → `applyQueryGovernance` → `validateQueryFields` → `buildQuery`；失败以 `IllegalArgumentException` 抛出（对齐 Python `ValueError`），M6 compile 层 catch 后映射到 `ComposeCompileException`
  - 该公共方法是 M6 前置 Step 0，不属于 M6 compile 子包本身——登记到决策记录而非 M6 内部设计，是因为它不是 compose-only 的 API（M7 / 未来其他 compile 链路都会复用）

## Follow-ups

- **F-7 · `CROSS_DATASOURCE_REJECTED` 真实检测**（post-M6 · 非阻断 · r3 Q6）
  当前 M6 因 `ModelBinding` / `ModelInfoProvider` 契约都没有 datasource identifier 字段，无法在 compile 时主动拒绝跨数据源 union/join；错误码定义在，但运行时只能通过 mock 触发，真实 plan 走 xfail。
  - **用户影响**：跨数据源查询会在 DB driver 层报 "table not found"，不是 compile 层结构化错误 —— UX 降级，不是安全问题（DB 最终会拒绝，不泄漏数据）
  - **解决路径**（二选一）：
    - (A) `ModelBinding` 增 `datasource_id` 字段（触动 M1/M5 冻结契约，需 Python + Java + Odoo Pro 同步签字）
    - (B) `ModelInfoProvider` 增 `get_datasource_id(model_name)` 方法（只触动 M5 契约）
  - **优先级**：P2 · 等 M7 script runner 真实场景或 M8 Odoo Pro 多 datasource 集成时再议
  - **M7 script 工具用户文档须加提示**："cross-datasource queries may surface as driver-level errors in the current release; planned to be caught at compile time in a future version"

## 下次回写触发点

- F-3 Python 侧已修（2026-04-21）· **Java 侧 F-3 修复 PR 合并后 → 更新 M5/M8 状态、解除 `blocked` 标志**
- M1 SPI 冻结完成 → 通知 Odoo Pro v1.6 REQ-001 可启动 M3
- 任一沙箱绕过被发现 → 立即升级为 blocker，不等 M10 统一处理

## 变更日志

### 2026-04-22
- **Java M6 execution prompt 升级为 `ready-to-execute`**：读完 Python M6 实际源码后，Java prompt 10 条 `🔄 FILL-AFTER-PYTHON` 占位符全部填实。关键更新：
  - 错误码表（4 + NAMESPACE）逐字对齐；每条消息文本锚定到 Python `compose_planner.py` / `per_base.py` 的 `raise ComposeCompileError` 点
  - Python 测试 9 个文件 → Java 9 个测试类一一映射（命名以 Java 惯例微调；tests 结构保留 Python parametrize 风格）
  - **最大发现 · r3 Q4 原决策被 Python 实现改写**：Python 选择在 `SemanticQueryService` 新增公共方法 `build_query_with_governance` 而不是调私有 `_build_query`。Java 侧 M6 前置 Step 0 必须同步加 `buildQueryWithGovernance` 公共方法 —— 见决策记录 2026-04-22 第 8 条
  - Java prompt frontmatter `status: draft-ahead-of-python` → `ready-to-execute`；`java_new_tests_target` ≥82 → ≥100（Python 超交付至 165 tests，Java 镜像目标相应上调）；工时估算 2.0 PD → 2.6 PD（+0.3 Step 0 前置 / +0.3 decision review buffer）
  - Java 侧仍未启动实现，等 Step 0 + compilation 子包开工
- **M6 Python 侧 SQL 编译器落地** · `python-ready-for-review`：新建 `foggy.dataset_model.engine.compose.compilation` 子包 7 文件（`__init__ / errors / error_codes / plan_hash / per_base / compose_planner / compiler`）· 公开 API 3 项（`compile_plan_to_sql` + `ComposeCompileError` + `error_codes`）· 4 错误码 + 1 NAMESPACE（`compose-compile-error/{unsupported-plan-shape,cross-datasource-rejected,missing-binding,per-base-compile-failed}`）· 2 phase（`plan-lower / compile`）· 核心入口签名 `compile_plan_to_sql(plan, ctx, *, semantic_service, bindings=None, model_info_provider=None, dialect='mysql') → ComposedSql` · per-base 编译先调 v1.3 `_apply_query_governance` + `validate_query_fields` 让 binding 三字段（`field_access / system_slice / denied_columns`）经 v1.3 engine 生效，再调 `_build_query` 拿 SQL + params（D1 preserve `__cause__`）· `DerivedQueryPlan` 递归降级为线性 `SELECT ... FROM (<inner>) AS alias` 字符串（不走 CteComposer outer 包装 · D4）· 4 方言 CTE vs subquery fallback（`mysql / mysql57 → use_cte=False`; `mysql8 / postgres / postgresql / mssql / sqlserver / sqlite → use_cte=True`；未知方言抛 `UNSUPPORTED_PLAN_SHAPE`）· `MAX_PLAN_DEPTH=32` DOS guard（超限抛 `UNSUPPORTED_PLAN_SHAPE`，消息含 `MAX_PLAN_DEPTH=32` 字样）· 两档 dedup（MVP `Dict[id(plan), CteUnit]` + Full `Dict[plan_hash(plan), CteUnit]`）· `plan_hash` 手写 `canonical_tuple` 递归把 `List/Dict` 转 `tuple/frozenset` 避开 dataclass 自动 `__hash__` 对 `List` 的 `TypeError` · SQLite `full outer join` 早期拒绝 · 166 tests 共 **164 passed + 2 xfailed**：6.1 base+derived 28 + derived 13（共 41）/ 6.2 union 11 + F-7 xfail 1 / 6.3 join 12 / 6.4 binding 18 / 6.5 dialect 29 含 4 方言 × derived-chain × (single/union/join) + param 跨方言一致性守卫 / 6.6 dedup+depth 15 + Full-dedup P1 xfail 1 / 支持测试 error_codes 15 + plan_hash 23 · 全仓 **2873 passed / 1 skipped / 2 xfailed**（M5 基线 2709 + 164 + 2 xfail，0 failed，0 regression）· F-7 `CROSS_DATASOURCE_REJECTED` 真实检测推迟至 post-M6（已登记 §Follow-ups）· 下游解锁：Java 镜像 M6 可基于 Python 侧形状开工（execution prompt `draft-ahead-of-python` → 可升级为 `ready-to-execute`）/ M8 Odoo Pro 嵌入集成测试多模型 SQL 比对阻断解除
- **Java M6 execution prompt `draft-ahead-of-python` 落地**：`docs/8.2.0.beta/M6-SQLCompilation-Java-execution-prompt.md` 先行起草（status: `draft-ahead-of-python`）。框架 / 架构 / 5 类源码 + 8 测试类命名 / 4 错误码常量对齐 / 6 阶段拆分 / 验收硬门槛已齐，空缺处标 `🔄 FILL-AFTER-PYTHON` 占位符（10 条），待 Python M6 落地 + push 后由 Java 镜像 agent 回填、状态升级 `ready-to-execute`。决策记录已同步（见上 2026-04-22 第 7 条）。Java 实现本体仍不启动。
- **Python M6 execution prompt r3 落地**（从 r2 吸收 plan-evaluator · 本轮吸收 6+2 评审确认）：
  - Q1 M6 scope 止于 `ComposedSql`（保持，与 M7 执行层分割线对齐）
  - Q2 `bindings=None` 惰性 resolve 不加缓存（延续 2026-04-21 "不做跨请求缓存"决策），docstring 补 caller 侧使用指引
  - Q3 6.5 4 方言 SQL snapshot 扩入 **derived-chain** 维度，覆盖 `FROM (inner) AS alias` 唯一的自拼路径
  - Q4 `_build_query` 内部签名依赖写入决策记录（见上），不提升为公开 API
  - Q5 新增 `MAX_PLAN_DEPTH = 32` 递归深度 guard（DOS 防线，超限抛 `UNSUPPORTED_PLAN_SHAPE`）
  - Q6 `CROSS_DATASOURCE_REJECTED` 真实检测登记为 F-7 follow-up（见上），本期维持 xfail
  - Extra-1 子包改名 `compile/` → `compilation/`（避免遮蔽 Python builtin `compile()`）
  - Extra-2 错误码措辞改为"4 个错误码 + 1 个 NAMESPACE 常量"，消除"5 个"与表格 4 行的表述歧义
  - 测试目标从 ≥80 调整为 ≥82（MAX_PLAN_DEPTH guard 1 条 + derived-chain snapshot 4 条 − 原 6.5 部分归并）
  - 预估规模 2.5–3.5 PD 不变
- **M5 Java 侧 Authority 绑定管线落地**：新建 `com.foggyframework.dataset.db.model.engine.compose.authority` 子包，共 5 个 public 类型：`ModelInfoProvider` `@FunctionalInterface` interface（`Optional<List<String>> getTablesForModel(String modelName, String namespace)` — Python `Optional[List[str]]` 的强类型版本，`Optional.empty()` 与 `Optional.of(List.of())` 在 pipeline 内都 coerce 为 `List.of()`）+ `NullModelInfoProvider` final · 永远返回 `Optional.of(List.of())` · 无状态线程安全 + `BaseModelPlanCollector` 静态工具 `collect(QueryPlan) → List<BaseModelPlan>` 左右前序 · 按 `model()` 字符串首次出现去重 · 输入 null 抛 `IllegalArgumentException`（对 Python `TypeError`）+ `AuthorityResolutionPipeline` 静态工具 `resolve(plan, context)` + `resolve(plan, context, provider)` 重载 · 5 分支 fail-closed 校验（`RESOLVER_NOT_AVAILABLE` / `AuthorityResolutionException` 原样透传 / 普通 `RuntimeException` 包装为 `UPSTREAM_FAILURE` 保留 `cause` / 非 `AuthorityResolution` 或 null 响应 → `INVALID_RESPONSE` / 缺 key → `MODEL_BINDING_MISSING` 按请求顺序 / 多 key 或值非 `ModelBinding` → `INVALID_RESPONSE`）· 返回 `Map.copyOf` 不可变映射 + `FieldAccessApplier` 静态工具 `apply(OutputSchema, ModelBinding)` · `fieldAccess == null` no-op 同引用返回 / 空 list 返回 `OutputSchema.empty()` / 非空 whitelist 保序过滤；**不**处理 `deniedColumns` / `systemSlice`（M6 范围）· M5 **不新增错误码**：全部复用 M1 `AuthorityErrorCodes.{RESOLVER_NOT_AVAILABLE,UPSTREAM_FAILURE,INVALID_RESPONSE,MODEL_BINDING_MISSING}` · 附带修改：`QueryPlan.baseModelPlans()` 从 package-private 升级为 public（M5 管线位于 sibling 包；Layer-C 仍由 JS sandbox 反射白名单在 M9 拦截；`PlanCompositionTest` Layer-C `allowed` 注释同步更新，`forbidden` 列表未动，现有测试全部保持绿）· 5 份 JUnit5 测试共 **51 tests 全绿**：`AuthorityResolutionPipelineTest` 23（含 `StaticShape` 反射形态硬断言，验证工具类只暴露静态方法、私有 ctor）+ `BaseModelPlanCollectorTest` 10 + `FieldAccessApplierTest` 15（覆盖 `DeniedPhysicalColumn` / `SliceRequestDef` 不交互断言）+ `ModelInfoProviderSmokeTest` 3（Java 版 `@runtime_checkable Protocol` 等价）+ `AuthorityTestDoubles` 内部 test helper（7 种 resolver 假实现 + 2 种 provider 假实现，`BadValueResolver` 经反射注入非 `ModelBinding` 值）· `foggy-dataset-model` sqlite lane **1399 passed / 0 failures**（M3 基线 1348 + 51 = 1399，0 regressed）· **M5 正式 `ready-for-review`**，Python + Java 双端对齐冻结 · 下游解锁：M6 SQL 编译器可直接消费 `Map<String, ModelBinding>` / M8 Odoo Pro 嵌入集成测试仅差 M6 SQL 编译

### 2026-04-21
- **M5 Python 侧 Authority 绑定管线落地**：新建 `foggy.dataset_model.engine.compose.authority` 子包（`model_info.py` / `collector.py` / `resolver.py` / `apply.py` + `__init__.py`）· 5 项公开 API：`ModelInfoProvider` `@runtime_checkable` Protocol（host 注入的 QM → physical tables hook，默认降级 `NullModelInfoProvider` 返回 `[]`）+ `collect_base_models(plan)` 左右前序 + 按 `.model` 首次出现去重 + `resolve_authority_for_plan(plan, context, *, model_info_provider=None)` 批量 resolve 整棵 plan 树 + `apply_field_access_to_schema(schema, binding)` · 一次调用仅触发一次 resolver（请求级去重，不做跨请求缓存）· 五个 fail-closed 分支：空 resolver → `RESOLVER_NOT_AVAILABLE` / 非 `AuthorityResolutionError` 异常 → `UPSTREAM_FAILURE`（`__cause__` 保留）/ 返回非 `AuthorityResolution` → `INVALID_RESPONSE` / `bindings` 缺 key → `MODEL_BINDING_MISSING`（按请求顺序决定性报第一个缺失）/ `bindings` 多 key 或 value 非 `ModelBinding` → `INVALID_RESPONSE` · `apply_field_access_to_schema`：`field_access=None` → no-op（返回原 schema 实例）；`field_access=[]` → 空 `OutputSchema`；`field_access=[names]` → 保序白名单过滤 · **不**处理 `denied_columns`（物理列解析需要 v1.3 `PhysicalColumnMapping` → M6 SQL 编译器范围）· 4 份测试共 **51 tests 全绿**（`test_collect_base_models.py` 10 + `test_resolve_authority_for_plan.py` 28 + `test_apply_field_access.py` 17 + `test_public_api.py` 3 + hook 构造的非 `ModelBinding` 边界用例）· 全仓 **2709 passed / 1 skipped**（M4 基线 2658 + 51 = 2709，0 failed）· Java 侧 M5 开工提示词：`docs/8.2.0.beta/M5-AuthorityBinding-Java-execution-prompt.md`
- **F-3 Java 侧同步修复完成**：`SemanticServiceV3Impl` 新增 `mergeFieldInfo(fields, key, freshInfo)` 辅助；`processModelFieldsV3` 6 处 `fields.put` 改为 `mergeFieldInfo`；首次写入保留顶层 fieldInfo，后续同名字段仅合并 `models` 子 map · 新增 `SemanticServiceV3MultiModelGovernanceTest` 7 tests · `foggy-dataset-model` sqlite lane **1246 passed / 0 failures** · F-3 Java lane 阻断解除
- **M3 Python 侧落地**：`COMPOSE_QUERY_DIALECT` 加入 `foggy.fsscript.parser.dialect`（与 `SQL_EXPRESSION_DIALECT` 并列，只移除 `from` 保留字）· 新建 `foggy.dataset_model.engine.compose.sandbox` 包（`error_codes.py` 14 个 violation code + 7 个 phase 枚举 + `layer_of` / `kind_of` 辅助；`exceptions.py` `ComposeSandboxViolationError` 构造期校验 code/phase）· 27 tests 全绿 · 全仓基线 **2591 passed / 1 skipped**（M2 基线 2564 + 27 = 2591）· Java 侧 M3 开工提示词：`docs/8.2.0.beta/M3-Dialect-and-SandboxErrors-Java-execution-prompt.md`
- **M4 Python 侧落地**：`foggy.dataset_model.engine.compose.schema` 子包（`alias.py` / `output_schema.py` / `derive.py` / `errors.py` / `error_codes.py`）· `derive_schema(plan)` 按 BaseModelPlan / DerivedQueryPlan / UnionPlan / JoinPlan 四种类型分派；别名解析（`SUM(x) AS total` → output name `total`）；派生层字段可见性、union 列数、join on 字段、join 输出列冲突、duplicate output、group_by/order_by 引用当前层输出等 7 种结构性错误有独立错误码；**不**做 authority 绑定（M5）或 SQL 类型推断（M6）· 67 tests 全绿 · 全仓基线 **2658 passed / 1 skipped**（M3 基线 2591 + 67 = 2658）· Java 侧 M4 开工提示词：`docs/8.2.0.beta/M4-SchemaDerivation-Java-execution-prompt.md`
- F-3 Python 侧修复完成：`_resolve_effective_visible` 从 `Optional[Set]` 升级为 `Optional[Dict[str, Set]]`；`get_metadata_v3` 与 `_build_multi_model_markdown` 调用点同步改造；新增 `tests/test_metadata_v3_cross_model_governance.py` 7 tests；全仓回归 **2430 passed / 1 skipped**（v1.5 基线 2420，净增 10，0 failed）
- M6 判定完成：Java 侧有同类 bug（形态不同 —— `SemanticServiceV3Impl.processModelFieldsV3` 用 `fields.put(key, freshInfo)` 直接覆盖，而非 merge-into-existing 的 `models` 子 map），`java_sync_required = yes`
- Java 侧修复作为独立工作项，不阻断本仓 M1 / M2 / M3 / M4 / M6 / M7 / M9 的并行推进
- **M1 Python 侧 SPI 落地**：新建 `foggy.dataset_model.engine.compose.{context,security}` 两个子包，共 7 份源码（`principal.py / compose_query_context.py / authority_resolver.py / models.py / exceptions.py / error_codes.py` + `__init__.py`），冻结 7 个错误码（`compose-authority-resolve/<kind>`）；6 份合规测试共 61 tests 全绿；全仓回归 **2491 passed / 1 skipped**（F-3 基线 2430，净增 61，0 failed）
- **M1 Java 侧 SPI 落地**：新建 `com.foggyframework.dataset.db.model.engine.compose.{context,security}` 两个子包，共 9 个类（`Principal / ComposeQueryContext / AuthorityResolver / AuthorityRequest / ModelQuery / AuthorityResolution / ModelBinding / AuthorityResolutionException / AuthorityErrorCodes`），显式 Builder + final 字段风格；复用 v1.3 `DeniedPhysicalColumn` + `SliceRequestDef`；6 份 JUnit5 合规测试共 **49 tests 全绿**（sqlite / mysql / postgres 三 lane），`foggy-dataset-model` 全仓 sqlite lane **1134 passed / 0 failures**。**M1 正式 `frozen`**，下游 Odoo Pro v1.6 REQ-001 M3 可开工（仅待 vendored sync）
- **M2 Python 侧 QueryPlan 对象模型落地**：新建 `foggy.dataset_model.engine.compose.plan` 子包 · 5 个 public 类型 `QueryPlan` + `BaseModelPlan / DerivedQueryPlan / UnionPlan / JoinPlan` + `JoinOn` + `SqlPreview` + `UnsupportedInM2Error` + `from_()` 入口 · `execute()/to_sql()` 明确抛 `UnsupportedInM2Error`（等 M6/M7）· Layer-C 白名单（只开 5 个方法：`query / union / join / execute / to_sql`）在测试中硬断言 forbidden 面缺席 · 5 份测试共 **73 passed** · 全仓 **2564 passed / 1 skipped**（M1 基线 2491 + 73，0 failed）· Java 侧 M2 开工提示词：`docs/8.2.0.beta/M2-QueryPlan-Java-execution-prompt.md`
- **M2 Java 侧 QueryPlan 对象模型落地**：新建 `com.foggyframework.dataset.db.model.engine.compose.plan` 子包 · 11 个 public 类型（`QueryPlan` 抽象基 + 4 个 final 具体子类 `BaseModelPlan / DerivedQueryPlan / UnionPlan / JoinPlan` + `JoinOn` + `JoinType` 枚举 + `SqlPreview` 占位 + `UnsupportedInM2Exception` + `Dsl.from(FromOptions)` 入口 + `QueryOptions` 链式糖）· 继续 M1 的显式 Builder + final 风格，不引入 Lombok / Record · 支持 `JoinOn` 与 `Map<String, ?>` 两种 on 入参（后者经 `JoinOn.fromMap` coerce）· 支持 `JoinType` 枚举与大小写不敏感字符串双入参 · `execute()/toSql()` 抛 `UnsupportedInM2Exception`（等 M6/M7）· Layer-C 反射白名单硬断言落在 `PlanCompositionTest`（`raw / rawSql / memoryFilter / forEach / items / rows / toArray / iterator` 全员缺席）· 6 份 JUnit5 测试共 **76 tests 全绿**（BaseModelPlanTest 13 + DerivedQueryPlanTest 11 + UnionPlanTest 11 + JoinPlanTest 21 + FromEntryTest 13 + PlanCompositionTest 7）· `foggy-dataset-model` sqlite lane **1246 passed / 0 failures**（M1 基线 1134 + 112，0 failed）· **M2 正式 `ready-for-review`**，Python + Java 双端对齐冻结
- **M4 Java 侧 Schema 推导落地**：新建 `com.foggyframework.dataset.db.model.engine.compose.schema` 子包 · 7 个 public 类型（`ColumnAliasParts` 值对象 + `AliasExtractor` 静态工具 + `ColumnSpec` 显式 Builder · 不可变 · 构造期校验 non-empty + `OutputSchema` ordered + 不可变 list · 构造期去重校验 + `ComposeSchemaErrorCodes` 7 frozen code + 2 phase + `ALL_CODES / VALID_PHASES` + `ComposeSchemaException extends RuntimeException` 含 code / phase / planPath / offendingField / cause + `SchemaDerivation.derive(QueryPlan)` 静态方法按 4 种 plan 分派）· 命名差异：Python `ComposeSchemaError` → Java `ComposeSchemaException`（与 M1 `AuthorityResolutionException` 风格一致）· 7 个 code 字符串、2 个 phase 字符串、28 个保留 token、别名正则（`\\s+AS\\s+` 大小写不敏感 · alias 必须是 `[A-Za-z_][A-Za-z0-9_$]*` · 非法回退整串为 expression · 字符串字面量字符级 mask）、4 种 plan 的校验规则（派生层 unknown field / union 列数 / join on 左右字段 / join 输出列冲突 / duplicate output / group_by/order_by 引用当前层输出）与 Python 逐字符对齐 · 4 份 JUnit5 测试 **78 tests 全绿**（AliasExtractorTest 20 + OutputSchemaTest 16 · 含反射层白名单硬断言 + SchemaDerivationTest 31 · 覆盖 spec §典型示例 1 两段聚合 + §典型示例 3 join + alias 消歧 + ComposeSchemaExceptionTest 11）· `foggy-dataset-model` sqlite lane **1324 passed / 0 failures**（M2 基线 1246 + 78，0 failed）· **M4 正式 `ready-for-review`**，Python + Java 双端对齐冻结
