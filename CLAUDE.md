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
  - `foggy-odoo-bridge/` - Odoo ERP 集成（MCP Gateway + 权限桥接）
  - `foggy-data-viewer/` - 数据浏览器组件
  - `chart-render-service/` - 图表渲染服务
  - `foggy-benchmark-spider2/` - Spider2基准测试
  - `foggy-dataset-client/` - 数据集客户端
  - `foggy-dataset-model-mongo/` - MongoDB模型支持
  - `foggy-dataset-mongo/` - MongoDB数据层
  - `foggy-fsscript-client/` - FSScript客户端

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
  - `foggy-odoo-bridge/` - Odoo ERP 集成（Python 插件 MCP Gateway）
  - `foggy-odoo-bridge-java/` - Odoo TM/QM 模型模块（内置模型，打包进 JAR）
  - `foggy-data-viewer/` - 数据浏览器组件
  - `chart-render-service/` - 图表渲染服务
  - `foggy-benchmark-spider2/` - Spider2基准测试
  - `foggy-dataset-client/` - 数据集客户端
  - `foggy-dataset-model-mongo/` - MongoDB模型支持
  - `foggy-dataset-mongo/` - MongoDB数据层
  - `foggy-fsscript-client/` - FSScript客户端

## Odoo Bridge

### 架构概览

```
AI Client ──MCP──→ Odoo MCP Gateway ──HTTP──→ Foggy MCP Server ──SQL──→ PostgreSQL
                   (Python addon)              (Java, TM/QM engine)
                   Auth + Permissions           Query building + execution
                   (payload.slice injection)    (DSL engine, pure query)
```

### 模块职责

| 模块 | 路径 | 职责 |
|------|------|------|
| **Odoo Python 插件** | `addons/foggy-odoo-bridge/foggy_mcp/` | MCP Gateway、权限桥接、Setup Wizard |
| **Odoo Java 模型** | `addons/foggy-odoo-bridge-java/` | TM/QM 模型（内置 9 个模型，打包进 JAR） |

### 核心功能

**Python 插件核心模块**：
- `foggy_mcp/controllers/mcp_controller.py` — MCP JSON-RPC 端点
- `foggy_mcp/services/permission_bridge.py` — ir.rule 域解析 → DSL slice 条件
- `foggy_mcp/services/tool_registry.py` — 工具按 ir.model.access 过滤
- `foggy_mcp/services/foggy_client.py` — Foggy MCP Server HTTP 客户端
- `foggy_mcp/wizard/foggy_setup_wizard.py` — 安装向导（动态 DataSource 配置）
- `foggy_mcp/models/foggy_api_key.py` — API Key 认证（`fmcp_` 前缀）

**权限桥接**（payload.slice 注入）：
- ir.model.access → 工具级过滤（tools/list 按用户权限裁剪）
- ir.rule → 行级过滤（域解析 → DSL slice 条件 → Foggy DSL 引擎处理）
- 失败关闭（fail-closed）：权限计算异常时拒绝访问

### 已支持 Odoo 模型（9 个）

| 模型 | 表名 | 说明 |
|------|------|------|
| OdooSaleOrderModel | sale_order | 销售订单 |
| OdooSaleOrderLineModel | sale_order_line | 销售订单行 |
| OdooPurchaseOrderModel | purchase_order | 采购订单 |
| OdooAccountMoveModel | account_move | 会计分录/发票 |
| OdooStockPickingModel | stock_picking | 库存调拨 |
| OdooHrEmployeeModel | hr_employee | 员工 |
| OdooResPartnerModel | res_partner | 合作伙伴 |
| OdooResCompanyModel | res_company | 公司 |
| OdooCrmLeadModel | crm_lead | CRM 线索/商机 |

### 安装流程

1. **Odoo Apps 安装 foggy_mcp 模块**
2. **Settings → Foggy MCP → Setup Wizard**
3. **向导步骤**：
   - 生成 Docker 命令 → 启动 Foggy MCP Server（模型已内置）
   - 测试连接
   - 配置数据源（通过 API 注册 Odoo PostgreSQL）
   - 初始化闭包表

### 动态 DataSource 配置

Java 侧用 SQLite 启动，Odoo 通过 REST API 注册自己的 PostgreSQL 数据源：

```
POST /api/v1/datasource
Authorization: Bearer {token}
{
  "name": "odoo",
  "host": "localhost",
  "port": 5432,
  "database": "odoo",
  "username": "odoo",
  "password": "odoo",
  "driver": "postgresql"
}
```

**持久化**：配置保存到 `~/.foggy/datasources/odoo.json`，重启后自动恢复。

**TM 模型引用**：`dataSourceName: ODOO_DATA_SOURCE_NAME`（常量 `'odoo'`）

### Bearer Token 认证

API 需要在 Authorization header 中携带 Bearer token：

```bash
# 配置 token（环境变量）
FOGGY_AUTH_TOKEN=your_token_here

# 或 application.yml
foggy:
  auth:
    token: your_token_here
```

### 测试

```bash
# 单元测试（无需 Odoo 运行时）
cd addons/foggy-odoo-bridge && python -m pytest tests/test_permission_bridge.py -v

# E2E 测试（需 Foggy MCP Server + Odoo 运行中）
cd addons/foggy-odoo-bridge && python -m pytest tests/e2e/ -v
```

### 闭包表层级集成

Odoo `child_of`/`parent_of` 映射到 Foggy 闭包表操作符：

| Odoo 操作符 | Foggy 操作符 | SQL 效果 |
|-------------|--------------|----------|
| `child_of` | `selfAndDescendantsOf` | `closure.parentKey = value` |
| `parent_of` | `selfAndAncestorsOf` | `closure.childKey = value` |

已映射层级维度：

| Odoo 字段 | Foggy 维度 | 闭包表 |
|-----------|------------|--------|
| `company_id` | `company$id` | `res_company_closure` |
| `department_id` | `department$id` | `hr_department_closure` |

### Odoo 17 JSONB 翻译字段

Odoo 17 可翻译字段存储为 JSONB（如 `{"en_US": "Sales"}`）。

辅助函数：`dicts.fsscript` 中的 `jsonbCaption(column, lang)` 生成 `captionDef`。

JSONB 字段（需 `jsonbCaption()`）：`hr_job.name`、`crm_team.name`、`product_pricelist.name` 等

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

项目有三套独立 Docker 环境，职责不同，不可合并：

| 环境 | 路径 | 用途 |
|---|---|---|
| **开发测试** | `foggy-dataset-demo/docker/` | 多方言测试基础设施（MySQL/PG/MSSQL/Mongo/Redis/Milvus） |
| **用户体验** | `docker/demo/` | 一键演示包（MySQL + Foggy MCP + AI），面向外部用户 |
| **Odoo 集成** | `addons/foggy-odoo-bridge/docker/` | Odoo 联调栈（PG + Odoo 17 + Foggy MCP lite） |

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

**Odoo 集成环境** — `addons/foggy-odoo-bridge/docker/`

Odoo E2E 联调专用，PG schema 为 Odoo 业务表，与开发测试环境不可共用。

| 服务 | 端口 |
|---|---|
| PostgreSQL 15 | `5432`（默认端口） |
| Odoo 17 | `8069` |
| Foggy MCP lite | `8080` |

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

## 文档生成原则
- 大部分文档，都是为LLM生成，所以保持简洁高效
- 避免token浪费及分散LLM注意力

## License
Apache License 2.0
