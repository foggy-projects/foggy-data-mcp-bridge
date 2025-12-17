# Java Data MCP Bridge - Claude Memory

> **开源项目，请勿上传私有 key、账号密码、token 等敏感信息。**

## 项目结构
- `foggy-core/` - 核心工具类库
- `foggy-dataset/` - 数据库基础层（Dialect、DbUtils）
- `foggy-dataset-model/` - 核心数据模型模块（JM/QM引擎）
- `foggy-dataset-mcp/` - MCP服务模块（AI对接）
- `foggy-dataset-demo/` - 示例项目（电商演示数据）
- `foggy-fsscript/` - 脚本引擎（解析JM/QM文件）
- `foggy-fsscript-client/` - 脚本引擎客户端
- `foggy-bean-copy/` - Bean拷贝工具
- `foggy-benchmark-spider2/` - Spider2基准测试

## 多数据库支持 (foggy-dataset)
已实现方言：
- `MysqlDialect` - MySQL 5.7+
- `PostgresDialect` - PostgreSQL 12+
- `SqlServerDialect` - SQL Server 2012+
- `SqliteDialect` - SQLite 3.30+

关键类：
- `FDialect` - 方言抽象基类
- `DbType` - 数据库类型常量
- `DbUtils.getDialect()` - 方言自动检测

## MCP 端点 (foggy-dataset-mcp)
按角色区分的端点：
- `/mcp/analyst/rpc` - JSON-RPC（推荐，用于Claude Desktop/Cursor）
- `/mcp/analyst/stream` - SSE流式响应（Web前端使用）
- `/mcp/admin/rpc` - 管理员（全部工具权限）
- `/mcp/business/rpc` - 业务用户（仅自然语言查询）

## 文档 (foggy-dataset-model/docs/)
```
docs/
├── README.md              # 文档导航
├── quick-start.md         # 快速入门
├── guide/                 # 核心指南
│   ├── JM-QM-Syntax-Manual.md
│   ├── API-Reference.md
│   └── Parent-Child-Dimension.md
├── security/              # 权限控制
│   ├── Authorization-Control.md
│   └── QueryModel-Accesses-Control.md
└── dev/                   # 开发者文档
    ├── TEST_DATA_MODEL.md
    └── MULTI_DATABASE_TESTING.md
```

## JdbcColumnType 类型映射
支持的类型及别名（在JM文件中使用）：
- `MONEY` / `NUMBER` - BigDecimal（金额、数值）
- `TEXT` / `STRING` - String（文本）
- `INTEGER` - Integer（整数）
- `BIGINT` - Long（长整数）
- `DAY` - Date（日期）
- `DATETIME` - Date（日期时间）
- `BOOL` - Boolean（布尔）
- `DICT` - Integer（字典）

别名支持：BigDecimal→NUMBER, Long→BIGINT, Integer→INTEGER, String→TEXT, Boolean→BOOL

## 不需要运行单元测试

## i18n
- 资源文件：`foggy-dataset-model/src/main/resources/i18n/messages*.properties`
- 工具类：`DatasetMessages.java`
- 编码：UTF-8

## License
Apache License 2.0
