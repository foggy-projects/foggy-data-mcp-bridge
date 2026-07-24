---
doc_role: model-spi-v2-adoption-inventory
version: 9.4.1
status: READY_FOR_SIGNOFF
recorded_at: 2026-07-24
baseline: 15-direct-dependencies-12-modules-114-files
current: 14-direct-dependencies-11-modules-110-files
---

# Model SPI v2 Adoption Inventory

## 盘点口径与结果

“直接依赖”只统计第一方 POM 的直接 `com.foggysource:foggy-dataset-model` dependency，不把父 POM、
dependencyManagement 或传递依赖计入。“旧包导入”只统计兼容聚合之外 `src/main/java` 中实际导入
`com.foggyframework.dataset.db.model.*` 的 Java 文件。

| 指标 | 9.4.1 起点 | 当前候选 | 结论 |
|---|---:|---:|---|
| 直接 legacy 聚合 POM | 15 | 14 | Odoo 冗余直接依赖移除 |
| 旧包生产导入模块 | 12 | 11 | Odoo 退出旧包导入集合 |
| 旧包生产导入文件 | 114 | 110 | GraphQL/DataViewer/MCP 自动配置硬引用减少 |
| governed direct `model.query()` | 1 | 1 | 唯一批准项，未新增 |
| 未批准 query bypass | 0 | 0 | architecture guard 强制 |

## 直接 legacy 聚合依赖 allowlist

| POM | 当前暂留原因 | 9.5.0 目标边界 |
|---|---|---|
| `build-support/foggy-coverage-report` | 聚合全部生产模块生成统一覆盖率报告 | 报告依赖改列物理模块，不作为运行时兼容理由 |
| `foggy-dataset-memory-grid-bridge` | 深度依赖 semantic planning/execution 类型 | semantic port 与实现物理归位后切换 |
| `foggy-runtime-api` | 暴露 lifecycle、semantic、validation 兼容类型 | 稳定 runtime ports/DTO 与 legacy 类型拆开 |
| `foggy-dataset-memory-grid-duckdb` | 使用 memory-grid semantic execution 类型 | 随 memory-grid/semantic adapter 物理归位 |
| `foggy-dataset-mcp` | 模型加载、semantic、compose 与生命周期编排 | loader/catalog/semantic runtime ports 独立构件化 |
| `addons/foggy-dataset-pivot` | pivot/compose 深层模型对象 | pivot plan/result 公共边界独立后迁移 |
| `addons/foggy-dataset-client` | 旧 client proxy 仍含唯一批准 direct-query 兼容路径 | 用稳定 facade/result 取代 proxy 内部 model query |
| `addons/foggy-dataset-model-cache` | query lifecycle、fingerprint、旧 cache SPI | 缓存执行边界与失效 port 分离；当前仅失效已采用 v2 |
| `addons/foggy-dataset-graphql` | 保留公开 legacy request converter 的源码/二进制兼容 | 兼容 API 移除获批后只依赖 model-api |
| `addons/foggy-dataset-model-vector` | 自定义 engine/model/column SPI 实现 | backend query/load 角色和 engine SPI 物理归位 |
| `addons/foggy-dataset-model-mongo` | Mongo loader、expression、engine 与 model SPI | backend load/query port 完整后拆出 adapter |
| `addons/foggy-dataset-model-preagg` | refresh lifecycle、engine 与 preagg SPI | atomic refresh/plan contracts 完整后拆出 adapter |
| `addons/foggy-benchmark-spider2` | benchmark 直接构造 legacy model/MCP runtime | benchmark 改用发布后的稳定 facade/runtime 组合 |
| `addons/foggy-data-viewer` | 元数据、semantic、旧公开 request/model 类型 | viewer metadata DTO/semantic read port 稳定后迁移 |

移除的第 15 项是 `addons/foggy-odoo-bridge-java`。其生产代码只需自动配置排序，改用 `afterName` 后
不再需要直接 legacy 聚合；经 `foggy-dataset-mcp` 保留的兼容运行时仍可用，因此无行为破坏。

## 旧包生产导入 allowlist

| 模块 | 文件数 | 主要类型族 | 暂留原因 / 9.5.0 去向 |
|---|---:|---|---|
| `addons/foggy-data-viewer` | 15 | def、semantic、SPI | metadata/semantic read DTO 尚未独立 |
| `addons/foggy-dataset-graphql` | 3 | query request def | 保留公开 legacy converter；执行路径已采用稳定 DTO |
| `addons/foggy-dataset-model-cache` | 12 | cache、lifecycle、SPI | query-cache 业务实现仍属旧 engine；v2 只承诺 invalidation |
| `addons/foggy-dataset-model-mongo` | 18 | loader、expression、engine、SPI | 需要 backend load/query 与 engine 物理拆分 |
| `addons/foggy-dataset-model-preagg` | 9 | refresh、engine、preagg SPI | 需要 atomic refresh contract 与实现拆分 |
| `addons/foggy-dataset-model-vector` | 6 | engine、model、SPI | 需要 vector backend adapter 边界 |
| `foggy-dataset-mcp` | 18 | lifecycle、semantic、engine、validation | 需要 loader/catalog/semantic runtime ports |
| `foggy-dataset-memory-grid-bridge` | 14 | semantic | 需要 semantic planning/execution 物理归位 |
| `foggy-dataset-memory-grid-duckdb` | 1 | semantic | 随 memory-grid semantic adapter 迁移 |
| `foggy-mcp-launcher` | 3 | engine、semantic、SPI | launcher assembly 仍装配 legacy runtime 类型 |
| `foggy-runtime-api` | 11 | lifecycle、semantic、validation、SPI | runtime 公共边界尚未完全 DTO 化 |

Architecture guard 使用以上模块和文件数作为只减不增 ceiling；数量减少无需更新 allowlist，增加或新模块
必须先更新 canonical 决策并说明兼容理由。

## Query adoption 与 bypass

- GraphQL endpoint：GraphQL AST 继续由兼容 converter 生成旧 request def，但执行前转换为稳定、快照化的
  `QueryFacadeRequest`；公开 `convert(...)` 签名不变。
- DataViewer controller/service：保留现有 request 类型作为外部兼容边界，内部统一由
  `StableQueryFacadeRequestMapper` 转换为 stable DTO，显式保留 namespace、authorization 和 pagination。
- 生产代码中不再导入 `LegacyQueryFacadeAdapter`。
- 唯一批准 direct-query 文件仍是
  `addons/foggy-dataset-client/.../DatasetClientProxy.java`。它是旧 client proxy 的兼容实现，不得复制到
  controller/runtime/addon 新路径；9.5.0 应以稳定 facade/result 取代。

## Provider/TCK 能力矩阵

| 能力/契约 | Provider/实现 | TCK/错误覆盖 | 9.4.1 状态 |
|---|---|---|---|
| model query | JDBC `QueryBackendProvider` | JDBC TCK：identity、精确/不可变 capability、catalog route、真实 port | covered |
| model load | 无 v2 provider | 无 TCK | not adopted；不得声明 |
| namespace isolation | 无独立 capability/port | 无 TCK | not adopted；沿用既有 facade/runtime 语义 |
| atomic refresh | 无 v2 provider | 无 TCK | not adopted；preagg 继续 legacy |
| cache invalidation | query-cache `CacheInvalidationBackendProvider` | cache TCK + delegation/context tests | covered |
| error contract | immutable catalog snapshot | duplicate、missing、unsupported、type mismatch | covered for catalog |

Catalog discovery 对已经存在小型角色的 `QUERY` 与 `CACHE_INVALIDATION` 执行 capability-role 一致性检查。
未实现的小型角色不被“推断”出来，因此没有为 `MODEL_LOAD`、namespace isolation 或 `ATOMIC_REFRESH`
制造空接口、假 provider 或虚假 TCK。

## 9.5.0 前置判断

当前盘点仍有 14 个直接聚合依赖、110 个旧包生产导入文件和 1 个批准 bypass，因此不能只因一个兼容
周期已结束就机械删除桥接层。进入 legacy exit 前至少需要：

1. 冻结 loader/engine/semantic/pivot/compose 的目标物理模块和包移动方案；
2. 补齐 model load、namespace isolation、atomic refresh 的真实 provider/contract/TCK，或明确它们不属于 v2；
3. 清理唯一 client proxy bypass，并给外部消费者提供源码/二进制迁移窗口；
4. 对将删除的聚合、deprecated facade/高级方法/构造器/SPI 形成明确 breaking-change 授权；
5. 在最终 legacy-exit 候选上执行一次完整 release-governance authority。
