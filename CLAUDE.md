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
  - `chart-render-service/` - 图表渲染服务
  - `foggy-benchmark-spider2/` - Spider2基准测试
  - `foggy-dataset-client/` - 数据集客户端
  - `foggy-dataset-model-mongo/` - MongoDB模型支持
  - `foggy-dataset-mongo/` - MongoDB数据层
  - `foggy-fsscript-client/` - FSScript客户端

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

## 帮助手册 (docs-site/)
基于 VitePress 构建的帮助手册，支持中英双语。
```
docs-site/
├── zh/                           # 中文文档
│   ├── dataset-model/            # 数据模型（TM/QM）
│   │   ├── guide/                # 入门指南
│   │   │   ├── introduction.md
│   │   │   ├── quick-start.md
│   │   │   └── concepts.md
│   │   ├── tm-qm/                # TM/QM 语法
│   │   │   ├── tm-syntax.md
│   │   │   ├── qm-syntax.md
│   │   │   ├── query-dsl.md
│   │   │   ├── calculated-fields.md
│   │   │   └── parent-child.md
│   │   └── api/                  # API 参考
│   │       ├── query-api.md
│   │       └── authorization.md
│   ├── dataset-query/            # 数据库查询层
│   │   ├── guide/
│   │   └── api/
│   ├── fsscript/                 # FSScript 脚本引擎
│   │   ├── guide/
│   │   ├── syntax/
│   │   └── java/
│   └── mcp/                      # MCP 服务
│       ├── guide/
│       └── integration/
└── en/                           # English docs (same structure)
```

## TM/QM 模型文件
- 位置：`foggy-dataset-demo/src/main/resources/foggy/templates/`
- TM 文件：`.tm` 后缀，定义表模型（维度、属性、度量）
- QM 文件：`.qm` 后缀，定义查询模型（列组、权限、排序）
- 使用 FSScript 语法（类 ES6/JavaScript）

## JdbcColumnType 类型映射
支持的类型及别名（在TM文件中使用）：
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
