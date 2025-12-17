# IDE 本地开发指南

使用 Java IDE（IntelliJ IDEA / Eclipse / VS Code）本地运行 MCP 服务，适合二次开发和调试。

## 前置条件

- Java 17+
- Maven 3.6+
- Docker（用于运行测试数据库）
- IDE：IntelliJ IDEA（推荐）/ Eclipse / VS Code

## 快速开始

### 1. 启动测试数据库

```bash
cd foggy-dataset-demo/docker

# 启动 MySQL（默认）
docker compose up -d mysql

# 或启动其他数据库
docker compose up -d postgres    # PostgreSQL
docker compose up -d sqlserver   # SQL Server
```

等待数据库健康检查通过：

```bash
docker compose ps
# 确认状态为 healthy
```

### 2. 导入项目到 IDE

#### IntelliJ IDEA

1. `File` → `Open` → 选择 `foggy-data-mcp-bridge` 根目录
2. 等待 Maven 索引完成
3. 确认 JDK 版本为 17+：`File` → `Project Structure` → `Project SDK`

#### Eclipse

1. `File` → `Import` → `Maven` → `Existing Maven Projects`
2. 选择 `foggy-data-mcp-bridge` 根目录
3. 选中所有模块，点击 `Finish`

#### VS Code

1. 安装 `Extension Pack for Java` 扩展
2. `File` → `Open Folder` → 选择 `foggy-data-mcp-bridge` 根目录
3. 等待 Java 项目加载完成

### 3. 配置环境变量

在 IDE 中配置 MCP 服务的启动参数。

#### IntelliJ IDEA

1. 打开 `McpDataModelApplication.java`
2. 右键 → `Modify Run Configuration`
3. 在 `Environment variables` 中添加：

```
OPENAI_API_KEY=sk-your-api-key
OPENAI_BASE_URL=https://api.openai.com
OPENAI_MODEL=gpt-4o-mini
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:13306/foggy_test?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=foggy
SPRING_DATASOURCE_PASSWORD=foggy_test_123
```

#### Eclipse

1. `Run` → `Run Configurations`
2. 选择 `McpDataModelApplication`
3. 切换到 `Environment` 标签
4. 添加上述环境变量

#### VS Code

创建 `.vscode/launch.json`：

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "MCP Service",
      "request": "launch",
      "mainClass": "com.foggyframework.dataset.mcp.McpDataModelApplication",
      "projectName": "foggy-dataset-mcp",
      "env": {
        "OPENAI_API_KEY": "sk-your-api-key",
        "OPENAI_BASE_URL": "https://api.openai.com",
        "OPENAI_MODEL": "gpt-4o-mini",
        "SPRING_DATASOURCE_URL": "jdbc:mysql://localhost:13306/foggy_test?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
        "SPRING_DATASOURCE_USERNAME": "foggy",
        "SPRING_DATASOURCE_PASSWORD": "foggy_test_123"
      }
    }
  ]
}
```

### 4. 启动服务

运行主类：

```
foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/McpDataModelApplication.java
```

启动成功后可以看到：

```
Started McpDataModelApplication in X.XXX seconds
```

### 5. 验证服务

```bash
# 健康检查
curl http://localhost:7108/actuator/health

# 获取工具列表
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}'
```

## 数据库连接信息

| 数据库 | 端口 | URL | 用户名 | 密码 |
|--------|------|-----|--------|------|
| MySQL | 13306 | jdbc:mysql://localhost:13306/foggy_test | foggy | foggy_test_123 |
| PostgreSQL | 15432 | jdbc:postgresql://localhost:15432/foggy_test | foggy | foggy_test_123 |
| SQL Server | 11433 | jdbc:sqlserver://localhost:11433;databaseName=foggy_test | sa | Foggy_Test_123! |

## AI 服务配置

| 服务 | OPENAI_BASE_URL | 模型示例 |
|------|-----------------|----------|
| OpenAI | https://api.openai.com | gpt-4o-mini, gpt-4o |
| 阿里云百炼 | https://dashscope.aliyuncs.com/compatible-mode/v1 | qwen-plus, qwen-turbo |
| Ollama 本地 | http://localhost:11434/v1 | qwen2:7b, llama3:8b |

## 连接 AI 客户端

在 Claude Desktop 或 Cursor 中配置：

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

## 调试技巧

### 查看生成的 SQL

设置日志级别为 DEBUG：

```properties
# application.yml 或环境变量
logging.level.com.foggyframework.dataset=DEBUG
```

### 热重载

IntelliJ IDEA 支持热重载：
1. `Settings` → `Build, Execution, Deployment` → `Compiler`
2. 勾选 `Build project automatically`
3. 修改代码后按 `Ctrl+Shift+F9` 重新编译

### 断点调试

在以下位置设置断点可以跟踪查询流程：
- `QueryModelTool.execute()` - 查询入口
- `SemanticQueryService.query()` - 语义查询处理
- `JdbcModelLoaderImpl.load()` - 模型加载

## 目录结构

```
foggy-dataset-mcp/
├── src/main/java/com/foggyframework/dataset/mcp/
│   ├── McpDataModelApplication.java   # 启动入口
│   ├── tools/                         # MCP 工具实现
│   ├── service/                       # 服务层
│   └── config/                        # 配置类
└── src/main/resources/
    ├── application.yml                # 默认配置
    └── schemas/                       # 工具描述和 Schema
```

## 常见问题

### Q: 数据库连接失败？

1. 确认 Docker 容器正在运行：`docker compose ps`
2. 检查端口是否被占用：`netstat -an | grep 13306`
3. 验证连接信息是否正确

### Q: AI API 调用失败？

1. 检查 API Key 是否有效
2. 确认 BASE_URL 配置正确
3. 查看日志中的错误信息

### Q: 找不到模型？

1. 确认 `foggy-dataset-demo` 模块已编译
2. 检查 `MCP_SEMANTIC_MODEL_LIST` 环境变量
3. 查看启动日志中的模型加载信息
