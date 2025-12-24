# 外部模型示例

本目录包含一个简单的外部模型示例，展示如何使用 `viewSql` 连接你自己的 MySQL 数据库。

## 快速开始

### 1. 配置数据库连接

编辑 `.env` 文件，设置你的 MySQL 连接信息：

```bash
# 启用外部模型
EXTERNAL_MODELS_ENABLED=true

# 配置你的 MySQL 连接
# 注意：Docker 容器内访问宿主机数据库，使用 host.docker.internal
CUSTOM_DB_URL=jdbc:mysql://host.docker.internal:3306/your_database?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
CUSTOM_DB_USER=your_username
CUSTOM_DB_PASS=your_password

# 将示例模型添加到可用列表
MCP_MODEL_LIST=FactSalesQueryModel,ExampleViewQueryModel
```

### 2. 修改 viewSql

编辑 `model/ExampleViewModel.tm`，将 `viewSql` 修改为你的查询语句：

```javascript
viewSql: `
    SELECT
        id,
        name,
        category,
        price,
        quantity,
        created_at
    FROM your_actual_table
    WHERE status = 'active'
`,
```

### 3. 重启服务

```bash
docker-compose restart mcp
```

### 4. 验证模型加载

```bash
docker-compose logs mcp | grep -i "ExampleViewModel"
```

## 文件说明

```
custom-models/
├── README.md                 # 本文档
├── datasource.fsscript       # 数据源配置（从环境变量读取）
├── model/
│   └── ExampleViewModel.tm   # 数据模型定义（使用 viewSql）
└── query/
    └── ExampleViewQueryModel.qm  # 查询模型定义
```

## viewSql 说明

使用 `viewSql` 的优势：
- **无需建表**：直接使用现有数据库中的表
- **灵活查询**：可以使用 JOIN、子查询等复杂 SQL
- **数据安全**：通过 SQL 条件限制可访问的数据范围

示例：

```javascript
// 简单查询
viewSql: `SELECT * FROM orders WHERE status = 'completed'`

// 多表 JOIN
viewSql: `
    SELECT
        o.id,
        o.order_no,
        c.name as customer_name,
        o.total_amount
    FROM orders o
    LEFT JOIN customers c ON o.customer_id = c.id
`

// 带条件筛选
viewSql: `
    SELECT * FROM sales_data
    WHERE year >= 2024
      AND deleted = 0
`
```

## 常见问题

### Q: 连接数据库失败？

1. 确认宿主机 MySQL 允许远程连接
2. Docker 容器访问宿主机使用 `host.docker.internal`
3. 检查防火墙设置

### Q: 模型未加载？

1. 确认 `EXTERNAL_MODELS_ENABLED=true`
2. 确认模型名称已添加到 `MCP_MODEL_LIST`
3. 查看日志：`docker-compose logs mcp`

### Q: viewSql 语法错误？

1. 先在 MySQL 客户端验证 SQL 正确性
2. 注意 SQL 中不要有尾随分号
3. 字符串使用单引号
