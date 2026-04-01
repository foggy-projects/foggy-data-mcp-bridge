# Java Data MCP Bridge - Claude Memory

> **开源项目，请勿上传私有 key、账号密码、token 等敏感信息。**

## 项目结构
- `foggy-core/` - 核心工具类库
- `foggy-dataset/` - 数据库查询层（Dialect、DbUtils）
- `foggy-dataset-model/` - 核心数据模型模块（TM/QM引擎）
- `foggy-dataset-mcp/` - MCP服务模块（AI对接）
- `foggy-dataset-demo/` - 示例项目（电商演示数据）
- `foggy-fsscript/` - 脚本引擎（解析TM/QM文件）
- `foggy-bean-copy/` - Bean拷贝工具
- `docs-site/` - 帮助手册（VitePress，中英双语）
- `addons/` - 扩展模块
  - `foggy-odoo-bridge-java/` - Odoo TM/QM 模型模块（内置模型，打包进 JAR，网关模式用）
  - `foggy-data-viewer/` - 数据浏览器组件
  - `chart-render-service/` - 图表渲染服务
  - `foggy-benchmark-spider2/` - Spider2基准测试
  - `foggy-dataset-client/` - 数据集客户端
  - `foggy-dataset-model-mongo/` - MongoDB模型支持
  - `foggy-dataset-mongo/` - MongoDB数据层
  - `foggy-fsscript-client/` - FSScript客户端

## Odoo Bridge

> **Odoo Python 插件已独立立项** → [foggy-odoo-bridge](https://github.com/foggy-projects/foggy-odoo-bridge)
>
> 本仓库仅保留 `addons/foggy-odoo-bridge-java/`（Java TM/QM 模型，网关模式时打包进 JAR）。

### 架构概览

Odoo 插件支持双引擎模式：

```
模式 A（网关）：AI ──MCP──→ Odoo foggy_mcp ──HTTP──→ 本项目 Java Server ──SQL──→ PG
模式 B（内嵌）：AI ──MCP──→ Odoo foggy_mcp（Python 引擎进程内运行）──SQL──→ PG
```

### foggy-odoo-bridge-java（本仓库）

Java TM/QM 模型模块，网关模式下打包进 JAR，提供 9 个 Odoo 业务模型。

**位置**：`addons/foggy-odoo-bridge-java/`

**模型列表**：sale_order、sale_order_line、purchase_order、account_move、stock_picking、hr_employee、res_partner、res_company、crm_lead

## 闭包表引擎 (foggy-dataset-model)

**层级操作符**（`hierarchy/` 包）：

| 操作符 | 方向 | 含自身 | SQL 效果 |
|---|---|---|---|
| `selfAndDescendantsOf` | 向下 | ✓ | `closure.parent_id = X` |
| `descendantsOf` | 向下 | ✗ | `closure.parent_id = X AND distance > 0` |
| `childrenOf` | 向下 | ✗ | `closure.parent_id = X AND distance = 1` |
| `selfAndAncestorsOf` | 向上 | ✓ | `closure.child_id = X` |
| `ancestorsOf` | 向上 | ✗ | `closure.child_id = X AND distance > 0` |

**TM 维度闭包配置**：
```javascript
{
    name: 'company',
    closureTableName: 'res_company_closure',
    parentKey: 'parent_id',
    childKey: 'company_id'
}
```

**引擎 JOIN 方向**（`JdbcModelQueryEngine`）：
- 后代方向：`fact.FK = closure.childKey`，WHERE `closure.parentKey = value`
- 祖先方向（`isAncestorDirection()`）：`fact.FK = closure.parentKey`，WHERE `closure.childKey = value`

## 多数据库支持 (foggy-dataset)
已实现方言：MySQL 5.7+、PostgreSQL 12+、SQL Server 2012+、SQLite 3.30+

关键类：`FDialect`（方言基类）、`DbType`（数据库类型）、`DbUtils.getDialect()`（方言检测）

## MCP 端点 (foggy-dataset-mcp)
按角色区分：
- `/mcp/analyst/rpc` - 分析师（JSON-RPC，推荐）
- `/mcp/analyst/stream` - 分析师（SSE流式）
- `/mcp/admin/rpc` - 管理员（全部权限）
- `/mcp/business/rpc` - 业务用户（仅查询）

**Namespace 隔离**：通过 HTTP Header `X-NS` 传递命名空间，支持多环境模型隔离（详见 [Bundle & Namespace](docs/dev-guide/bundle-namespace.md)）

> **已修复 — Namespace 传递链**：`X-NS` header → `AnalystMcpController` → `McpRequestContext` → `QueryModelTool` → `DatasetAccessor` → `SemanticQueryServiceV3Impl` → `queryModelLoader.getJdbcQueryModel(model, namespace)`。Odoo 集成使用 `namespace: odoo`，通过 `FoggyClient` 的 `X-NS: odoo` header 传递。

**动态 Bundle**：支持运行时添加/移除外部 Bundle（详见 [Bundle & Namespace](docs/dev-guide/bundle-namespace.md)）

## TM/QM 模型文件
- 位置：`foggy-dataset-demo/src/main/resources/foggy/templates/`
- `.tm` - 表模型（维度、属性、度量）
- `.qm` - 查询模型（列组、权限、排序）
- 语法：FSScript（类 ES6/JavaScript）

## JdbcColumnType 类型映射
常用类型：`MONEY/NUMBER`（BigDecimal）、`TEXT/STRING`（String）、`INTEGER`（Integer）、`BIGINT`（Long）、`DAY`（Date）、`DATETIME`（Date）、`BOOL`（Boolean）、`DICT`（Integer/字典）

## API 开发规范
**必须使用 RX 统一返回**：所有 REST API 返回 `RX` 对象，禁止使用 `ResponseEntity`。详见 [API Standards](docs/dev-guide/api-standards.md)

### RX 快速参考
```java
// 成功
return RX.ok(data);

// 错误
return RX.failB("错误信息", errorData);
return RX.notFound().build();

// 前端解析（检查 code === 200）
if (response.data.code !== 200) throw new Error(response.data.msg)
const data = response.data.data
```

## MongoDB 可选架构
`foggy-dataset-mcp` 的 MongoDB 依赖为 `optional`，仅审计日志功能需要。

条件装配链路（`ToolAuditAutoConfiguration`）：
1. `@ConditionalOnClass` — classpath 无 MongoDB 则整个配置跳过
2. `@ConditionalOnProperty(foggy.mcp.audit.enabled=true)` — 默认关闭
3. `@ConditionalOnBean(MongoTemplate)` — 需已配置 MongoDB 连接

**极简模式**：`--spring.profiles.active=lite` 启动，排除 MongoDB 自动配置 + 关闭 data-viewer，仅保留核心 MCP + JDBC 能力。

**Odoo 集成启动示例**（lite + odoo profile，模型已内置）：
```bash
java -jar foggy-mcp-launcher.jar \
  --spring.profiles.active=lite,odoo \
  --foggy.auth.token=your_token_here
```

数据源通过 Setup Wizard 动态配置，无需启动参数。

## Docker 环境

项目有两套独立 Docker 环境（Odoo 环境已随插件独立立项迁出）：

| 环境 | 路径 | 用途 |
|---|---|---|
| **开发测试** | `foggy-dataset-demo/docker/` | 多方言测试基础设施（MySQL/PG/MSSQL/Mongo/Redis/Milvus） |
| **用户体验** | `docker/demo/` | 一键演示包（MySQL + Foggy MCP + AI），面向外部用户 |

**开发测试环境** — `foggy-dataset-demo/docker/`

多数据库方言验证，被 `foggy-dataset-model` 单元测试 profile 直接引用。

| 服务 | 端口 | 账号 |
|---|---|---|
| MySQL 5.7 | `13306` | foggy / foggy_test_123 |
| PostgreSQL 15 | `15432` | foggy / foggy_test_123 |
| SQL Server 2022 | `11433` | sa / Foggy_Test_123! |
| MongoDB 6.0 | `17017` | — |
| Redis 7 | `16379` | — |
| Milvus 2.4 | `19530` | — |
| Adminer | `18080` | — |

**用户体验环境** — `docker/demo/`

外部用户一键体验，自建 MySQL 镜像（init SQL 内置），含 Foggy MCP Java 服务 + AI 配置。

| 服务 | 端口 |
|---|---|
| MySQL 5.7（自建镜像） | `13306` |
| Foggy MCP 服务 | `7108` |
| Adminer（optional profile） | `18080` |

### 测试 Profile 与 Docker 端口对应

```bash
# SQLite（内存，无需 Docker）
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite

# MySQL（连 foggy-dataset-demo/docker 的 MySQL）
mvn test -pl foggy-dataset-model -Dspring.profiles.active=docker

# PostgreSQL
mvn test -pl foggy-dataset-model -Dspring.profiles.active=postgres

# SQL Server
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlserver

# 跳过多数据库测试（仅 SQLite）
mvn test -pl foggy-dataset-model -P!multi-db
```

## 开发约定
- 不需要运行单元测试（`-DskipTests`）
- i18n 资源：`foggy-dataset-model/src/main/resources/i18n/messages*.properties`（UTF-8）
- 帮助手册：`docs-site/`（VitePress 双语文档）

## 版本化需求管理
- 后续所有讨论中的新能力、需求、增强项、重构项，必须先明确目标版本，再进入设计、实现或验收。
- 需求文档统一放在 `docs/{版本号}/` 目录下跟踪；如果目录不存在，先创建版本目录。
- 需求文档命名规范：`docs/{版本号}/{需求等级}-${功能名称}-需求.md`
- 推荐需求等级使用：`P0`（阻塞/紧急）、`P1`（高优先级）、`P2`（正常优先级）、`P3`（低优先级）
- 如果某项需求尚未归属明确版本，默认视为“未进入实现阶段”，只能讨论，不进入开发。
- 实现过程中涉及范围变更、延期、降级或拆分时，优先更新对应版本目录下的需求文档，再继续讨论或编码。

## 文档生成原则
- 大部分文档，都是为LLM生成，所以保持简洁高效
- 避免token浪费及分散LLM注意力

## License
Apache License 2.0
