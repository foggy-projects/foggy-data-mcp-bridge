---
doc_role: architecture
status: canonical
baseline: main-after-9.5.2-runtime-console
last_reviewed: 2026-07-29
---

# 模块边界

## 1. Model 模块

| 模块 | 当前职责 | 边界 |
|---|---|---|
| `foggy-dataset-model-api` | JDK-only QueryFacade/DTO 与 backend SPI v2 | 不依赖 Spring、JDBC 或 engine 内部类型 |
| `foggy-dataset-model-core` | 不可变 provider catalog、发现与 fail-closed 错误 | 只建立在 model-api 上 |
| `foggy-dataset-model-jdbc` | 最小 JDBC QUERY adapter | 只声明实际实现的 QUERY |
| `foggy-dataset-model-starter` | Spring 自动装配与默认 provider/catalog 组装 | 不承载语义引擎实现 |
| `foggy-dataset-model-web` | provider/catalog 只读诊断面 | 不成为查询旁路 |
| `foggy-dataset-model-tck` | provider capability/role 契约测试 | 测试边界；生产模块不得把它作为运行时能力 |
| `foggy-dataset-model-engine` | TM/QM、loader、semantic、compose、pivot、refresh、SQL 与执行 | engine 内部高级类型不进入 model-api |

Model 依赖方向：

```text
model-api
   ↑
model-core
   ↑
model-jdbc
   ↑
model-starter

model-web ──→ model-core
model-tck ──→ model-core       (test contract)
model-engine ──→ api/core/jdbc/starter/web + dataset/fsscript
```

这是治理方向，不代表图中遗漏的构建或测试依赖不存在。精确依赖以 POM 为准。

### 必须维持的不变量

- 不恢复已删除的 `foggy-dataset-model` 聚合坐标。
- 不恢复 `com.foggyframework.dataset.db.model.*` 双包兼容层。
- 公共稳定查询调用只依赖 model-api 的 QueryFacade 与 DTO。
- engine 的 loader、semantic、pivot、compose、SQL AST 和生命周期实现保持物理归位。
- controller、runtime 和 addon 不得新增直接 `loader + model.query()` 路径。
- TCK 只验证已经实现并声明的能力，不能用测试替代真实实现。

## 2. 基础模块

| 模块 | 职责 |
|---|---|
| `foggy-bean-copy` | Bean 映射与复制基础 |
| `foggy-core` | 通用基础类与工具 |
| `foggy-dataset` | JDBC 数据访问、方言、查询对象与执行基础 |
| `foggy-fsscript` | TM/QM 使用的 FSScript 解析和运行 |
| `foggy-dataset-demo` | 示例数据、演示模型和测试 fixture |
| `foggy-mcp-spi` | MCP tool、tool category、执行上下文和进度事件契约 |

基础模块不能反向依赖 launcher 或具体 controller。

## 3. 接入、运行时与装配

| 模块 | 职责 |
|---|---|
| `foggy-dataset-mcp` | MCP 协议、角色 controller、工具注册/调度、审计、语义查询工具 |
| `foggy-runtime-api` | 稳定运行时管理 API、DTO、数据源/Bundle/模型操作、access check 与 auth-code scope 保护 |
| `foggy-analytics-*` | 产品无关的 Analytics Definition、Runtime、Function SDK、HTTP/FAP adapter 与独立 Analytics Runtime API |
| `foggy-mcp-launcher` | 可执行 Spring Boot JAR 和产品装配 |
| `foggy-dataset-memory-grid-bridge` | 语义查询与 memory-grid 的桥接边界 |
| `foggy-dataset-memory-grid-duckdb` | DuckDB memory-grid 实现 |

`foggy-mcp-launcher` 是装配根，不是放置业务逻辑的通用模块。接入模块负责协议与上下文适配，
语义规则和模型生命周期仍由 engine/runtime 服务负责。

## 4. Addons

| 模块 | 职责 |
|---|---|
| `addons/foggy-fsscript-client` | FSScript 远程客户端 |
| `addons/foggy-dataset-client` | 远程 Dataset 访问客户端 |
| `addons/foggy-dataset-mongo` | Mongo 数据层 |
| `addons/foggy-dataset-model-mongo` | Mongo 模型实现 |
| `addons/foggy-dataset-model-cache` | 查询缓存及 CACHE_INVALIDATION provider |
| `addons/foggy-dataset-model-vector` | Model 层 vector 能力 |
| `addons/foggy-dataset-vector` | Vector 数据访问 |
| `addons/foggy-dataset-model-preagg` | 预聚合实现 |
| `addons/foggy-dataset-graphql` | GraphQL 接入 |
| `addons/foggy-data-viewer` | 数据浏览 UI/资源 |
| `addons/foggy-runtime-console` | 可选的同源 Runtime 管理 SPA、静态资源交付与安全启用检查 |
| `addons/foggy-analytics-console` | 独立 Analytics 产品 SPA、owner/目录/展示 ACL、管理员设计发布与 FAP Ask BFF |
| `addons/foggy-chart-storage-cloud` | 图表资源云存储 |
| `addons/foggy-odoo-bridge-java` | launcher 网关模式使用的 Odoo Java TM/QM 模型 |

Addon 必须保持可选：核心查询链不能因为 classpath 缺少某个 addon 而失败。Addon 若参与
Model SPI，必须提供唯一 identity、精确 capability、对应 port 实现和聚焦契约测试。

Runtime Console 只拥有 Vue 页面、浏览器 session、相对同源 API adapter 和 `/console/` 静态资源
交付。`X-Foggy-Runtime-Code` 校验、`management-all` 路径策略、RuntimeEnvelope 和业务 Controller
仍归 `foggy-runtime-api`；`foggy-mcp-launcher` 的 `runtime-console` profile 只装配
`foggy-runtime-api + foggy-runtime-console`，默认 launcher 不包含该 Console。

Analytics Console 与 Runtime Console 是两个产品。Analytics Console 使用 `/analytics-console/` 和
`/analytics-console/api/v1/**`，自主管理 owner、目录和展示 ACL；Java Analytics Runtime 只管理定义、
校验和执行。Console 的 FAP 接入经服务端 gateway 与 product-neutral Function SDK 边界完成，浏览器不持有
FAP 或 Runtime 管理凭据。TMS 不依赖该 addon，也不读取 Console 的目录、owner 或 ACL；两者只独立消费
相同的 Analytics Definition/Function 契约。

Odoo Python 插件属于独立项目，本仓库只维护 Java 模型与网关侧集成。

## 5. 构建支持

- `build-support/foggy-coverage-report` 只参与聚合覆盖率，不属于产品运行时。
- `foggy-benchmark-spider2` 当前未进入根 reactor，是 WIP/benchmark 边界。
- `docs-site/` 仅保留文档站迁移提示；用户文档源位于独立
  `foggy-data-mcp-docs` 仓库。

## 6. 变更放置判断

| 变更类型 | 首选位置 |
|---|---|
| 新稳定查询字段或 backend port | model-api，先评估兼容性 |
| provider 发现、identity、capability 校验 | model-core |
| JDBC facade adapter | model-jdbc |
| Spring bean/auto-configuration | model-starter |
| 只读 provider diagnostics | model-web |
| provider 契约验证 | model-tck 或 provider 自身测试 |
| TM/QM、loader、semantic、compose、pivot、刷新 | model-engine |
| MCP 协议与 tool 调度 | dataset-mcp / mcp-spi |
| 数据源、Bundle、模型管理 API | runtime-api |
| Runtime Web 管理页面与静态交付 | runtime-console addon |
| 最终可执行装配 | mcp-launcher |
| backend 特有实现 | 对应 addon |
