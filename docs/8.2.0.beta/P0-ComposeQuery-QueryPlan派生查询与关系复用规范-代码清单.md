# P0-ComposeQuery QueryPlan 派生查询与关系复用规范-代码清单

## 文档作用

- doc_type: code-inventory
- intended_for: execution-agent / reviewer
- purpose: 盘点 8.2.0.beta QueryPlan 派生查询与关系复用实现的主要代码触点、职责边界和预计改动方向

## 前置依赖（blocking）

| item | owner | 状态 | 对本清单的影响 |
|---|---|---|---|
| F-3 `_resolve_effective_visible` 跨模型 denied 泄漏修复 | `foggy-data-mcp-bridge-python` 维护方 | ✅ **accepted**（2026-04-21） | Python + Java 双端修复完成 + Odoo Pro vendored sync + xfail 已撤；多模型集成测试用例可提交 ready-for-review |

签收记录：`foggy-data-mcp-bridge-python/docs/v1.6/acceptance/REQ-P0-BUG-F3-acceptance.md` · decision `accepted`。详情参见 `需求.md §前置依赖`、`实现规划.md §前置依赖`、`foggy-odoo-bridge-pro/docs/prompts/v1.4/workitems/BUG-v14-metadata-v3-denied-columns-cross-model-leak.md`（status=`resolved`）。

## 关联规范文档

| path | 作用 |
|---|---|
| `docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md` | 需求主文档 |
| `docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-实现规划.md` | 实现规划主文档 |
| `docs/8.2.0.beta/P0-ComposeQuery-沙箱白名单错误码与防护用例清单.md` | 三层沙箱错误码表 + Layer A/B/C 最小防护用例集合（验收锚点） |
| `docs/8.2.0.beta/P0-ComposeQuery-固定Schema下业务分析能力对比评估.md` | 能力评估与对比（只读参考） |

## 模块职责

### `foggy-dataset-model`

- 承担 `QueryPlan` 对象模型、schema 推导、SQL 编译、权限 binding 注入

### `foggy-dataset-mcp`

- 承担 MCP `script` 工具入口、`ToolExecutionContext` 到权限协议输入的桥接

## 代码触点

| repo | path | role | expected change | notes |
|---|---|---|---|---|
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/proxy/DslQueryFunction.java` | `dsl()` / `from()` 入口桥接 | `update` | 从当前 DataSetResult/旧 compose 语义过渡到 `QueryPlan` 入口；同名对象同时以 `from` 和 `dsl` 两个 binding 注册（过渡别名） |
| `foggy-fsscript` | `src/main/java/.../parser/dialect/FsscriptDialect.java` | 方言基类 | `read-only-analysis` | 已具备 `isKeywordAsIdentifier(keywordSymbol, nextChar)` 钩子，本期直接复用 |
| `foggy-fsscript` | `src/main/java/.../parser/dialect/` (新建 `ComposeQueryDialect`) | Compose 专用方言 | `create` | `isKeywordAsIdentifier(FROM, '(') -> true`，让 `from(...)` 作为函数调用合法；其他保持默认 |
| `foggy-fsscript` | `src/main/java/.../parser/ElExpScanner.java` | Scanner | `read-only-analysis` | 已在 v1.4 咨询 `dialect.isKeywordAsIdentifier`，本期不改 |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/engine/compose/DataSetResult.java` | 当前 compose 结果包装 | `update` | 收缩为物化结果职责，移出派生查询主语义 |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/engine/compose/ComposedDataSetResult.java` | 当前 withJoin/组合执行包装 | `update` | 评估保留兼容层或迁移到新的 `QueryPlan` 编译链 |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/semantic/domain/SemanticRequestContext.java` | 请求上下文承载 | `update` | 明确与上游权限协议、`fieldAccess/deniedColumns/systemSlice` 绑定方式 |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticQueryServiceV3Impl.java` | 查询主服务 | `update` | 支撑 `toSql()`、计划节点执行、权限绑定后的查询执行 |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/engine/query_model/QueryModelSupport.java` | QueryModel / JoinGraph 支撑 | `read-only-analysis` | 用于确认 JoinGraph 到 tables 的抽取能力 |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/engine/join/JoinGraph.java` | 底层表关系图 | `read-only-analysis` | 用于表集合抽取规则设计，必要时补辅助方法 |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/SystemSliceMergeStep.java` | systemSlice 注入 | `read-only-analysis` | 确认 QueryPlan 场景下注入边界 |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/FieldAccessPermissionStep.java` | 字段权限裁剪 | `read-only-analysis` | 确认 schema 推导前后的字段白名单行为 |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/plugins/query_execution/PhysicalColumnPermissionStep.java` | deniedColumns 执行期约束 | `read-only-analysis` | 评估是否需前移部分能力以支持 schema 裁剪 |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/ModelResultContext.java` | 安全上下文/权限载体 | `read-only-analysis` | 与 `SemanticRequestContext`、模型级 binding 对齐 |
| `foggy-dataset-model` | `src/test/java/com/foggyframework/dataset/db/model/compose/DataSetResultTest.java` | 现有 compose 测试 | `update` | 调整预期，补 `QueryPlan` 新语义测试 |
| `foggy-dataset-model` | `src/test/java/com/foggyframework/dataset/db/model/semantic/domain/SemanticRequestContextTest.java` | 请求上下文测试 | `update` | 补权限协议与绑定后的上下文行为测试 |
| `foggy-dataset-mcp` | `src/main/java/com/foggyframework/dataset/mcp/tools/ComposeQueryTool.java` | 现有 compose 工具 | `update` | 收敛为新的 `script` 入口或兼容演进 |
| `foggy-dataset-mcp` | `src/main/resources/schemas/descriptions/compose_query.md` | 工具描述 | `update` | 改为 `script` 入口和 QueryPlan 语义说明 |
| `foggy-dataset-mcp` | `src/main/java/com/foggyframework/dataset/mcp/service/McpToolDispatcher.java` | MCP 上下文分发 | `read-only-analysis` | 确认 header/trace/namespace 的工具上下文传递 |
| `foggy-dataset-mcp` | `src/main/java/com/foggyframework/dataset/mcp/service/McpToolCallbackFactory.java` | ToolExecutionContext 构造 | `read-only-analysis` | 确认授权头与 trace 注入链路 |
| `foggy-dataset-mcp` | `src/test/java/com/foggyframework/dataset/mcp/tools/QueryModelToolTest.java` | MCP context 测试参考 | `read-only-analysis` | 复用 namespace/authorization 传递测试模式 |
| `foggy-dataset-mcp` | `src/test/java/com/foggyframework/dataset/mcp/integration/McpToolsIntegrationTest.java` | MCP 集成测试 | `update` | 增加 `script` 工具和权限回调场景 |

## 建议新增

| repo | path | role | expected change | notes |
|---|---|---|---|---|
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/engine/compose/queryplan/` | `QueryPlan` 节点与编译模型 | `create` | 集中承载 `QueryPlan / BaseModelPlan / DerivedQueryPlan / UnionPlan / JoinPlan`，每个类型只暴露 `query / union / join / execute / toSql` 五个方法（Layer C 白名单） |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/engine/compose/context/ComposeQueryContext.java` | 编排执行上下文 | `create` | 承载 `Principal / namespace / AuthorityResolver / TraceContext / params`；脚本不可见 |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/engine/compose/context/Principal.java` | 身份信息 | `create` | `userId / tenantId / roles / deptId / authorizationHint? / policySnapshotId?` |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/engine/compose/security/AuthorityResolver.java` | 权限解析 SPI | `create` | `resolve(AuthorityRequest) -> AuthorityResolution`；嵌入与远程模式共享签名 |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/engine/compose/security/AuthorityRequest.java` | 权限请求模型 | `create` | 固定批量形态 `models: [{name, tables}]` |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/engine/compose/security/AuthorityResolution.java` | 权限响应模型 | `create` | `bindings: Map<modelName, ModelBinding>` |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/engine/compose/security/ModelBinding.java` | 模型级 binding | `create` | `fieldAccess? / deniedColumns / systemSlice`，与 Odoo Pro 产出一致 |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/engine/compose/security/HttpAuthorityResolver.java` | 远程实现（签名冻结，实现可延后） | `create` | 第一版可先留 stub |
| `foggy-dataset-model` | `src/main/java/com/foggyframework/dataset/db/model/engine/compose/sandbox/` | Layer A 宿主白名单 | `create` | JS 全局裁剪、deny list、`compose-sandbox-violation` 错误统一抛出 |
| `foggy-fsscript` | `src/main/java/.../parser/dialect/ComposeQueryDialect.java` | Compose 专用方言 | `create` | 同上 |
| `foggy-dataset-mcp` | `src/main/java/com/foggyframework/dataset/mcp/tools/ScriptQueryTool.java` | 新 `script` 工具 | `create` | body 仅接收 `script` 文本；从 `ToolExecutionContext` 构造 `ComposeQueryContext`；不改动 `ComposeQueryTool` |
| `foggy-odoo-bridge-pro` | `foggy_mcp_pro/services/authority_resolver.py` | Odoo 嵌入 resolver | `create` | 实现 `AuthorityResolver` Protocol，内部循环调用 `compute_query_governance_with_result`；注入 `ComposeQueryContext` 在 `script` 工具入口 |
| `foggy-data-mcp-bridge-python` | `foggy/dataset_model/engine/compose/` | Python 对等对象模型 | `create` | `from_(...)` 顶层入口 + `QueryPlan` + `AuthorityResolver` Protocol + `ComposeQueryContext` dataclass |
| `foggy-dataset-model` | `src/test/java/com/foggyframework/dataset/db/model/compose/queryplan/` | QueryPlan 语义测试 | `create` | 聚焦 schema、union、join、权限绑定 |
| `foggy-dataset-model` | `src/test/java/com/foggyframework/dataset/db/model/compose/sandbox/` | 三层白名单防护测试 | `create` | Layer A/B/C 每层至少 5 条用例 |
| `foggy-dataset-mcp` | `src/test/java/com/foggyframework/dataset/mcp/tools/ScriptQueryToolTest.java` | 新工具测试 | `create` | header → ComposeQueryContext → resolver 注入的全链路 |
| `foggy-odoo-bridge-pro` | `tests/test_compose_query_embedded_resolver.py` | Odoo 嵌入集成测试 | `create` | `SaleOrderQM` + `CrmLeadQM` 组合脚本，验证两边 deniedColumns/systemSlice 独立注入不交叉 |

## 不建议触碰

| repo | path | role | expected change | notes |
|---|---|---|---|---|
| `foggy-dataset-model` | 现有窗口函数实现链路 | 当前已存在能力 | `do-not-touch` | 8.2.0 不扩 QueryPlan window 语义 |
| `foggy-dataset-model` | 内存加工链路 | 非本期范围 | `do-not-touch` | 不把 `memoryQuery/compute/filter/sort` 重新拉回主线 |
| `foggy-dataset-mcp` | 单 DSL 工具与现有调用方契约 | 存量入口 | `do-not-touch` | 保持原入口不变，仅新增 `script` 工具 |
| `foggy-odoo-bridge-pro` | `controllers/mcp_controller.py` 中 `_handle_tools_call` 现有 `apply_query_governance(arguments, ...)` 路径 | 单 DSL eager-push 权限注入 | `do-not-touch` | 存量 `dataset.query_model` 工具依然走该路径；仅在新 `script` 工具入口上改走 `ComposeQueryContext + AuthorityResolver` 拉取模式 |
| `foggy-odoo-bridge-pro` | `services/query_governance.py` `compute_query_governance_with_result` | 现有 per-model 治理产出 | `read-only-reuse` | `OdooEmbeddedAuthorityResolver` 内部直接循环调用这个函数，不改写它 |
