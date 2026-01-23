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
  - `foggy-data-viewer/` - 数据浏览器组件
  - `chart-render-service/` - 图表渲染服务
  - `foggy-benchmark-spider2/` - Spider2基准测试
  - `foggy-dataset-client/` - 数据集客户端
  - `foggy-dataset-model-mongo/` - MongoDB模型支持
  - `foggy-dataset-mongo/` - MongoDB数据层
  - `foggy-fsscript-client/` - FSScript客户端

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

## 开发约定
- 不需要运行单元测试（`-DskipTests`）
- i18n 资源：`foggy-dataset-model/src/main/resources/i18n/messages*.properties`（UTF-8）
- 帮助手册：`docs-site/`（VitePress 双语文档）

## License
Apache License 2.0
