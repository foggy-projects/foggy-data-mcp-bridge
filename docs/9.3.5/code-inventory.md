---
doc_role: read_only_preexecution_inventory
version: 9.3.5
status: baseline-only / no-implementation-authority
baseline_commit: 26081a3b4853914de8e6effe9a21b1353d590917
recorded_at: 2026-07-20
---

# 9.3.5 引擎阶段与公共 API 代码基线

## 观察范围

本记录只扫描 `src/main/java`，不读取或使用历史运行日志、raw artifact 或测试结果作为生产设计依据。
数字和路径是 baseline commit 上的静态盘点，供后续 approved implementation 复算；它们不等于任何
调用路径已获批准或已迁移。

## 现有阶段模型：必须保留一套语义

| Type | Current values | Main source surface | Later rule |
|---|---|---|---|
| `QueryExecutionPhase` | `NORMAL_QUERY`, `PREPARE_MANAGED_RELATION`, `EXECUTE_MANAGED_RELATION` | 7 main files: `JdbcQueryModelImpl` 与 `plugins/query_execution` | 作为查询执行生命周期的唯一 phase 枚举；不得复制或并行新增 |
| `QueryStageType` | `ROW_STAGE`, `AGGREGATE_STAGE`, `POST_AGGREGATE_STAGE`, `WINDOW_RESULT_STAGE`, `FINAL_STAGE` | 3 main files: `QueryStagePlan`、`QueryStagePlanner`、自身 | 保持为 planner 的 stage 语义；不得与 execution phase 混用 |

后续实现必须先写阶段轨迹回归，证明同一请求不会在两个概念之间发生隐式翻译或漏执行。

## QueryFacade 公共面现状

`QueryFacade` 当前有 9 个接口方法。除四种 `queryModelData` 与两种 `queryModelResult` 入口外，公开面
还直接出现以下 implementation-oriented 类型：

- `DbQueryRequestDef`、`PagingRequest`、`PagingResultImpl`；
- `ModelResultContext`、`DbQueryResult`、`SqlGenerationResult`；
- `ManagedRelationOptions`、`ManagedSqlRelation`。

这说明当前门面尚不是 roadmap 所要求的稳定 DTO-only public surface。后续 9.3.5 必须先定义兼容期、
public/internal/advanced port 边界和二进制兼容策略，不能直接删除或重签名。

静态扫描发现 17 个 main-source `QueryFacade` 引用文件，其中模型模块外的已知 consumer 为：

- `addons/foggy-data-viewer`：`DataViewerAutoConfiguration`、`ViewerApiController`、
  `CachedQueryContext`、`MemberQueryService`；
- `addons/foggy-dataset-graphql`：`GraphqlAddonAutoConfiguration`、`GraphqlEndpointController`。

这些是兼容性基线，不代表它们已满足未来 public port 约束。

## Loader / direct-query 候选面

以下 main-source 文件直接引用 `QueryModelLoader`，是后续 bypass 分类的起点：

- `foggy-dataset-mcp`: `ModelCatalogService`、`ToolConfigLoader`、
  `spi/impl/SemanticServiceResolverImpl`、`validation/SemanticLayerValidationService`；
- `foggy-runtime-api`: `RuntimeDetachedModelValidator`、`RuntimeModelOperations`。

以下 main-source 文件包含 `.query(` 调用，必须由后续 implementation 逐一分类为 facade path、internal
advanced port 或禁止 bypass；本基线不把文本匹配直接判定为违规：

- `addons/foggy-data-viewer`: `ViewerApiController`、`ListPresetService`；
- `addons/foggy-dataset-client`: `DatasetClientProxy`；
- `addons/foggy-dataset-vector`: `VectorFscriptDataSetModel`；
- `foggy-dataset-mcp`: `JdbcExperienceRecipeRegistryStore`、`NaturalLanguageQueryTool`；
- `foggy-runtime-api`: `RuntimeModelOperations`。

## Gate 0 分类债务

`DEBT-unit-mysql57-fixture-classification-migration` remains `open / accepted-for-9.3.4-only`.
Its current reviewed lower bound is at least 7 suites / 12 testcase nodes. 9.3.5 must choose exactly one
closure path before version acceptance:

1. migrate every actual DB consumer into a governed database lane; or
2. remove its external database dependency so `none/hermetic/step=2` becomes provable again.

Required closure evidence is updated inventory, a fresh fail-closed run, exact report/testcase mapping,
negative cases, and a formal acceptance record. No frozen execution key may be silently added or removed.

## Reproduction commands

```bash
rg -l --glob '*.java' --glob '!**/src/test/**' 'QueryExecutionPhase' foggy-dataset-model addons foggy-dataset-mcp foggy-runtime-api foggy-mcp-launcher
rg -l --glob '*.java' --glob '!**/src/test/**' 'QueryStageType' foggy-dataset-model addons foggy-dataset-mcp foggy-runtime-api foggy-mcp-launcher
rg -l --glob '*.java' --glob '!**/src/test/**' 'QueryFacade' foggy-dataset-model addons foggy-dataset-mcp foggy-runtime-api foggy-mcp-launcher
rg -l --glob '*.java' --glob '!**/src/test/**' 'QueryModelLoader' addons foggy-dataset-mcp foggy-runtime-api foggy-mcp-launcher
```

Run these only against the tested commit and compare the complete path sets before making any public API claim.
