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

## Odoo Bridge (addons/foggy-odoo-bridge)
Odoo Python 插件，作为 MCP Gateway 桥接 AI 客户端与 Foggy MCP Server。

**架构**：`AI Client ──MCP──→ Odoo MCP Gateway ──HTTP──→ Foggy MCP Server ──SQL──→ PostgreSQL`

**核心模块**：
- `foggy_mcp/controllers/mcp_controller.py` — MCP JSON-RPC 端点 + `/foggy-mcp/health` 诊断
- `foggy_mcp/services/permission_bridge.py` — ir.rule 域解析（波兰表示法 AST → DSL slice 条件）
- `foggy_mcp/services/tool_registry.py` — 从 Foggy 加载工具并按用户 ir.model.access 过滤
- `foggy_mcp/services/foggy_client.py` — Foggy MCP Server HTTP 客户端
- `foggy_mcp/models/foggy_api_key.py` — API Key 认证（`fmcp_` 前缀）
- `foggy-models/` — Odoo 表的 TM/QM 模型文件

**权限桥接**（payload.slice 注入方式）：
- ir.model.access → 工具级过滤（tools/list 按用户权限裁剪）
- ir.rule → 行级过滤（域解析 → DSL slice 条件 → 注入 payload.slice → Foggy DSL 引擎原生处理）
- 支持：AND/OR/NOT、De Morgan 定律、`$or`/`$and` 嵌套、child_of/parent_of 层级展开、null 检查
- 失败关闭（fail-closed）：权限计算异常时拒绝访问
- Foggy Java 侧作为纯查询引擎，不直接对外暴露；用户统一通过 Odoo MCP 端点访问

**已支持 Odoo 模型**：sale.order, sale.order.line, purchase.order, account.move, stock.picking, hr.employee, res.partner

**测试**：`cd addons/foggy-odoo-bridge && python -m pytest tests/ -v`（45 tests，无需 Odoo 运行时）

**Docker**：`cd addons/foggy-odoo-bridge/docker && docker-compose up -d`（PostgreSQL + Odoo 17 + Foggy MCP）

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

## 开发约定
- 不需要运行单元测试（`-DskipTests`）
- i18n 资源：`foggy-dataset-model/src/main/resources/i18n/messages*.properties`（UTF-8）
- 帮助手册：`docs-site/`（VitePress 双语文档）

## 文档生成原则
- 大部分文档，都是为LLM生成，所以保持简洁高效
- 避免token浪费及分散LLM注意力

## License
Apache License 2.0
