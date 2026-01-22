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

### Namespace支持（命名空间隔离）
通过HTTP Header `X-NS` 传递命名空间，支持同一服务下多环境模型隔离：
- 未传或传空字符串：使用默认命名空间
- 传 `dev`：查询dev命名空间下的模型（如 `dev:OrderModel`）
- 传 `test`：查询test命名空间下的模型（如 `test:OrderModel`）

配置示例：
```yaml
foggy:
  bundle:
    external:
      enabled: true
      bundles:
        - name: ecommerce-dev
          namespace: dev        # 开发环境
          path: /data/ecommerce-dev
          watch: true

        - name: ecommerce-test
          namespace: test       # 测试环境
          path: /data/ecommerce-test
          watch: false
```

Java配置示例：
```java
@EnableFoggyFramework(
    bundleName = "my-models",
    namespace = "dev"
)
public class MyModelsConfig { }
```

### 动态Bundle管理
支持运行时动态添加/移除外部Bundle，无需重启服务。

REST API：
- `GET /api/bundles/list` - 列出所有外部Bundle
- `POST /api/bundles/add` - 添加外部Bundle
- `DELETE /api/bundles/remove/{bundleName}` - 移除外部Bundle
- `GET /api/bundles/exists/{bundleName}` - 检查Bundle是否存在

添加Bundle示例：
```bash
curl -X POST http://localhost:8080/api/bundles/add \
  -H "Content-Type: application/json" \
  -d '{
    "name": "dynamic-models",
    "namespace": "dev",
    "path": "/data/dynamic-models",
    "watch": true
  }'
```

移除Bundle示例：
```bash
curl -X DELETE http://localhost:8080/api/bundles/remove/dynamic-models
```

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
