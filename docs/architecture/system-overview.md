---
doc_role: architecture
status: canonical
baseline: main-after-9.5.0
last_reviewed: 2026-07-24
---

# 系统总览

## 1. 系统定位

Foggy Data MCP Bridge 把数据源、TM/QM 语义模型和查询引擎组合为可被 AI、MCP 客户端与
应用系统调用的语义数据服务。系统负责：

- 管理数据源、namespace、Bundle 与模型生命周期；
- 把稳定 DTO 查询或 MCP 工具调用转换为语义查询；
- 完成权限校验、SQL/执行计划生成、查询执行与结果整形；
- 通过小型 SPI 接入 backend、缓存和其他可选能力；
- 以可执行 Spring Boot launcher 或嵌入式模块方式交付。

它不是客户业务授权系统。Runtime API 的 auth code 是管理接口保护层，调用方仍需在上游
完成租户、用户和业务权限决策，并把允许的字段或禁止的物理列传入查询上下文。

## 2. 逻辑分层

| 层 | 主要模块 | 职责 |
|---|---|---|
| 接入与装配 | `foggy-mcp-launcher` | 可执行 JAR、Spring Boot 装配、交付边界 |
| MCP 接入 | `foggy-dataset-mcp`, `foggy-mcp-spi` | JSON-RPC/SSE、工具发现与调度、审计、面向角色的工具面 |
| Runtime 管理 | `foggy-runtime-api` | 数据源、namespace、Bundle、资源、模型、查询与 compose 管理 API |
| 稳定模型契约 | `foggy-dataset-model-api` | QueryFacade/DTO、backend identity/capability/port |
| provider 治理 | `foggy-dataset-model-core`, `-starter`, `-web`, `-tck` | catalog、自动装配、诊断、契约测试 |
| 语义引擎 | `foggy-dataset-model-engine` | TM/QM 加载、语义规划、权限、compose、pivot、刷新与执行 |
| 数据与脚本 | `foggy-dataset`, `foggy-fsscript` | JDBC 方言/执行基础、TM/QM 脚本解析 |
| 可选扩展 | `addons/*`, memory-grid 模块 | Mongo、cache、vector、preagg、GraphQL、viewer、Odoo 等 |

接入层只能通过稳定契约或明确的 engine 内部边界调用模型能力，不应新建
`loader + model.query()` 旁路。

## 3. 部署形态

### 3.1 独立 launcher

`foggy-mcp-launcher` 组装 MCP、Runtime API、语义引擎、示例资源及选配 addon，产出可执行
Spring Boot JAR。外部客户端通过 HTTP/MCP 调用，数据访问由运行时管理的数据源完成。

### 3.2 嵌入式 Spring Boot

宿主应用可以按需依赖 model API、engine、Runtime API 或 MCP 模块，并由 Spring
auto-configuration 装配。嵌入式方式仍必须遵守相同的 namespace、provider catalog、
模型发布和权限边界，不能因为同进程部署而绕开这些契约。

### 3.3 外部/可选 backend

Mongo、vector、pre-aggregation、cache、memory-grid 及远程 dataset client 以独立模块接入。
模块存在不等于它已经声明 Model SPI v2 capability；只有实际实现对应 port 并通过契约验证的
provider 才能发布 capability。

## 4. 对外接口面

### MCP

- `/mcp/analyst/rpc`、`/mcp/analyst/stream`
- `/mcp/admin/rpc`、`/mcp/admin/stream`
- `/mcp/business/rpc`、`/mcp/business/stream`

角色端点决定可发现和可执行的工具集合。MCP controller 负责协议适配和上下文建立，查询语义
由下层服务与 QueryFacade/engine 完成。

### Runtime API

统一前缀为 `/api/v1`，覆盖：

- capabilities；
- datasources 与 namespace-datasource binding；
- bundles 与 resources；
- models 的 list/describe/validate/refresh；
- query validate/execute；
- tables inspect、受控 SQL query；
- compose validate/preview/execute；
- FSScript execute。

Runtime API 使用自身的稳定 envelope 与错误码，不应机械套用其他 controller 的 `RX` 返回约定。

### 健康与辅助接口

MCP 模块暴露 `/healthz`、`/readyz` 和 `/info`。开发、图表及兼容接口属于辅助面，不能被当作
稳定 Model SPI。

## 5. 主查询流

```mermaid
sequenceDiagram
    participant C as Client
    participant A as MCP/Runtime Adapter
    participant Q as QueryFacade
    participant P as BackendProviderCatalog
    participant E as Semantic Engine
    participant D as Datasource

    C->>A: request + namespace + security context
    A->>A: authenticate / normalize / validate
    A->>Q: immutable request DTO
    Q->>P: resolve provider identity + QUERY
    P-->>Q: typed QueryBackendProvider
    Q->>E: semantic query
    E->>E: fieldAccess + model rules + planning
    E->>E: build SQL / execution plan
    E->>E: deniedColumns check
    E->>D: execute
    D-->>E: rows
    E-->>Q: shaped result
    Q-->>A: immutable result DTO
    A-->>C: protocol response
```

任一 identity、capability、namespace、模型或权限前置条件无法确定时，应拒绝请求而不是猜测或
回退到权限更宽的路径。

## 6. 信任与安全边界

- 不在仓库、配置示例、测试收据或日志中提交真实 key、密码和 token。
- Runtime auth code 保护管理接口，但不替代客户 IAM。
- namespace 必须由可信接入层确定并贯穿请求；不能由不可信模型内容提升或跨越。
- `fieldAccess` 控制可引用的语义字段，覆盖列、计算字段、过滤、分组和排序依赖。
- `deniedColumns` 在物理计划形成后检查实际表列，作为第二道边界。
- provider discovery、capability 解析、Bundle 冲突检查和模型 admission 均 fail closed。
- diagnostics 只暴露运行所需的只读状态，不泄露连接凭据或敏感模型内容。

Namespace 与 Bundle 的接口细节见
[Bundle & Namespace 开发说明](../dev-guide/bundle-namespace.md)。
