# Foggy MCP Demo - Docker 快速体验

一键启动 Foggy Framework MCP 服务演示环境。

## 前置条件

- Docker 20.10+
- Docker Compose 2.0+
- OpenAI API Key 或兼容的 AI 服务（阿里云百炼、Ollama 等）

## 快速开始

### 1. 配置 API Key

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env，设置你的 AI API Key
# 最简配置只需设置 OPENAI_API_KEY
```

### 2. 启动服务

```bash
# 一键启动（首次会构建镜像，需要几分钟）
docker-compose up -d

# 查看启动日志
docker-compose logs -f mcp
```

### 3. 验证服务

```bash
# 健康检查
curl http://localhost:7108/actuator/health

# 获取可用工具列表
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}'
```

## 连接 AI 客户端

### Claude Desktop / Cursor

在 Claude Desktop 或 Cursor 的 MCP 配置中添加：

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

### 测试查询

连接成功后，可以尝试自然语言查询：

```
"查询最近一周的销售数据，按商品分类汇总"
"上个月销售额最高的5个品牌"
"显示各门店的库存情况"
```

## 预置数据模型

演示环境包含电商场景的数据模型：

| 查询模型 | 说明 |
|---------|------|
| FactSalesQueryModel | 销售明细分析 |
| FactOrderQueryModel | 订单分析 |
| FactPaymentQueryModel | 支付分析 |
| FactReturnQueryModel | 退货分析 |
| FactInventorySnapshotQueryModel | 库存快照 |

## 数据库连接

如需直接访问数据库查看演示数据：

```bash
# 启动 Adminer（数据库管理工具）
docker-compose --profile tools up -d adminer

# 访问 http://localhost:18080
# 系统: MySQL
# 服务器: mysql
# 用户名: foggy
# 密码: foggy_demo_123
# 数据库: foggy_demo
```

## 配置说明

### AI 服务配置

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `OPENAI_API_KEY` | AI API Key（必填） | - |
| `OPENAI_BASE_URL` | AI 服务地址 | https://api.openai.com |
| `OPENAI_MODEL` | 模型名称 | gpt-4o-mini |

**支持的 AI 服务：**

- **OpenAI**: `OPENAI_BASE_URL=https://api.openai.com`
- **阿里云百炼**: `OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1`
- **Ollama**: `OPENAI_BASE_URL=http://host.docker.internal:11434/v1`

### 服务配置

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `MCP_PORT` | MCP 服务端口 | 7108 |
| `MCP_MODEL_LIST` | 可用模型列表 | 电商模型 |
| `LOG_LEVEL` | 日志级别 | INFO |

## 常用命令

```bash
# 停止服务
docker-compose down

# 重启服务
docker-compose restart mcp

# 查看日志
docker-compose logs -f

# 清空数据重新开始
docker-compose down -v
docker-compose up -d

# 仅重建 MCP 服务
docker-compose up -d --build mcp
```

## 目录结构

```
docker/demo/
├── docker-compose.yml   # 服务编排
├── .env.example         # 环境变量模板
├── custom-models/       # 自定义模型目录（需手动创建）
│   ├── model/           # TM 数据模型
│   ├── query/           # QM 查询模型
│   └── datasource.fsscript  # 数据源配置（可选）
└── README.md            # 本文档
```

## 自定义数据模型

体验演示后，你可以添加自己的数据模型来查询自己的数据库。

### 1. 创建模型目录

```bash
mkdir -p ./custom-models/model ./custom-models/query
```

### 2. 配置自定义数据源

创建 `./custom-models/datasource.fsscript`：

```javascript
// 使用 dataSourceFactory 创建自定义数据源
import bean dataSourceFactory;

// 方式1: 从环境变量读取配置（推荐）
export const myDataSource = dataSourceFactory.create({
    url: dataSourceFactory.env('CUSTOM_DB_URL'),
    username: dataSourceFactory.env('CUSTOM_DB_USER'),
    password: dataSourceFactory.env('CUSTOM_DB_PASS')
});

// 方式2: 直接写配置（仅用于测试）
// export const myDataSource = dataSourceFactory.create({
//     url: 'jdbc:mysql://host.docker.internal:3306/mydb',
//     username: 'root',
//     password: 'password'
// });
```

### 3. 创建数据模型 (TM)

创建 `./custom-models/model/MyModel.tm`：

```javascript
// 引入自定义数据源
import '../datasource.fsscript' as ds;

export const model = {
    name: 'MyModel',
    caption: '我的数据表',
    dataSource: ds.myDataSource,  // 使用自定义数据源
    tableName: 'my_table',
    idColumn: 'id',

    // 定义维度
    dimensions: [
        {
            name: 'category',
            tableName: 'dim_category',
            foreignKey: 'category_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: '分类'
        }
    ],

    // 定义度量
    measures: [
        { column: 'amount', caption: '金额', type: 'MONEY', aggregation: 'sum' },
        { column: 'quantity', caption: '数量', aggregation: 'sum' }
    ]
};
```

### 4. 创建查询模型 (QM)

创建 `./custom-models/query/MyQueryModel.qm`：

```javascript
export const queryModel = {
    name: 'MyQueryModel',
    model: 'MyModel',
    caption: '我的查询模型',

    columnGroups: [
        {
            caption: '维度',
            items: [
                { name: 'category$caption' }
            ]
        },
        {
            caption: '度量',
            items: [
                { name: 'amount' },
                { name: 'quantity' }
            ]
        }
    ]
};
```

### 5. 启用外部模型

编辑 `.env` 文件：

```bash
# 启用外部模型
EXTERNAL_MODELS_ENABLED=true

# 配置自定义数据库连接
CUSTOM_DB_URL=jdbc:mysql://host.docker.internal:3306/mydb
CUSTOM_DB_USER=root
CUSTOM_DB_PASS=password

# 将自定义模型添加到可用列表
MCP_MODEL_LIST=FactSalesQueryModel,FactOrderQueryModel,MyQueryModel
```

### 6. 重启服务

```bash
docker-compose restart mcp

# 查看日志确认模型加载
docker-compose logs mcp | grep -i "外部Bundle"
```

### DataSourceFactory API

`dataSourceFactory` 支持以下方法：

| 方法 | 说明 |
|------|------|
| `create(config)` | 使用 Map 配置创建数据源 |
| `create(url, username, password)` | 简化方式创建数据源 |
| `env(name)` | 获取环境变量 |
| `env(name, defaultValue)` | 获取环境变量，支持默认值 |

**config 参数说明：**

| 参数 | 必填 | 说明 |
|------|------|------|
| `url` | 是 | JDBC 连接 URL |
| `username` | 是 | 数据库用户名 |
| `password` | 是 | 数据库密码 |
| `driverClassName` | 否 | JDBC 驱动类名（自动检测） |
| `maximumPoolSize` | 否 | 最大连接数（默认 10） |
| `minimumIdle` | 否 | 最小空闲连接（默认 2） |

## 故障排查

### MCP 服务启动失败

1. 检查 MySQL 是否健康：`docker-compose ps`
2. 查看 MCP 日志：`docker-compose logs mcp`
3. 确认 API Key 配置正确

### 无法连接 AI 服务

1. 检查网络连接
2. 验证 API Key 是否有效
3. 如使用阿里云百炼，确认 BASE_URL 正确

### 查询返回空结果

1. 确认数据库已初始化：访问 Adminer 查看表数据
2. 检查 `MCP_MODEL_LIST` 配置是否包含所需模型
