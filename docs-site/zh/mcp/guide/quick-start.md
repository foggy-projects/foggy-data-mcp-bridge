# 快速开始

本指南帮助你快速启动 Foggy MCP 服务并连接 AI 客户端。

## 选择你的方式

根据你的背景选择合适的入门路径：

| 方式 | 适合人群 | 时间 | 特点 |
|------|---------|------|------|
| [🐳 Docker 快速体验](#docker-quick-start) | 非 Java 开发者、快速验证 | 5 分钟 | 开箱即用，无需开发环境 |
| [☕ Java 项目集成](#java-integration) | Java 开发者、生产部署 | 15 分钟 | 完整控制，可深度定制 |

---

## 🐳 Docker 快速体验 {#docker-quick-start}

适合：快速体验功能、验证概念、对接 AI 客户端测试

### 前置条件

- Docker 20.10+
- Docker Compose 2.0+
- AI 服务 API Key（可选，用于自然语言查询）

### 1. 克隆项目

```bash
git clone https://github.com/foggy-projects/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/docker/demo
```

### 2. 配置 AI 服务（可选）

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 设置 API Key（用于自然语言查询功能）
```

最小配置只需设置一个变量：

```bash
# .env
OPENAI_API_KEY=sk-your-api-key-here
```

**支持的 AI 服务：**

| 服务 | BASE_URL | 模型示例 |
|------|----------|----------|
| OpenAI | `https://api.openai.com` | gpt-4o-mini |
| 阿里云百炼 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | qwen-plus |
| Ollama | `http://host.docker.internal:11434/v1` | qwen2:7b |

### 3. 启动服务

```bash
# 一键启动（首次需要构建镜像，约 3-5 分钟）
docker-compose up -d

# 查看启动日志
docker-compose logs -f mcp
```

### 4. 验证服务

```bash
# 健康检查
curl http://localhost:7108/actuator/health

# 获取可用工具列表
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}'
```

### 5. 连接 AI 客户端

服务启动后，跳转到 [连接 AI 客户端](#connect-ai-clients) 章节完成配置。

---

## ☕ Java 项目集成 {#java-integration}

适合：Java 开发者、与现有项目集成、生产环境部署、深度定制

### 前置条件

- JDK 17+
- Maven 3.6+
- 任意 IDE（IntelliJ IDEA、VS Code 等）
- MySQL / PostgreSQL / SQLite 数据库

### 1. 添加依赖

#### 1.1 新建项目

如果创建新项目，在 `pom.xml` 中添加：

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
</parent>

<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Foggy MCP 服务（包含 dataset-model） -->
    <dependency>
        <groupId>com.foggysource</groupId>
        <artifactId>foggy-dataset-mcp</artifactId>
        <version>8.0.1-beta</version>
    </dependency>

    <!-- 数据库驱动（根据实际情况选择） -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

#### 1.2 已有项目

如果项目已有 Spring Boot 和数据源配置，只需添加：

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-mcp</artifactId>
    <version>8.0.1-beta</version>
</dependency>
```

### 2. 配置主应用类

```java
@SpringBootApplication
@EnableFoggyFramework(bundleName = "my-mcp-server")
public class MyMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyMcpApplication.class, args);
    }
}
```

### 3. 配置文件

创建或编辑 `src/main/resources/application.yml`：

```yaml
server:
  port: 7108

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

# Foggy 配置
foggy:
  dataset:
    show-sql: true              # 开发时打印 SQL
    show-sql-parameters: true   # 显示 SQL 参数
    show-execution-time: true   # 显示执行时间

  # MCP 配置
  mcp:
    # AI 服务配置（用于自然语言查询）
    ai:
      api-key: ${OPENAI_API_KEY:}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      model: ${OPENAI_MODEL:gpt-4o-mini}
```

### 4. 创建数据模型

创建 TM/QM 模型文件目录：

```
src/main/resources/
└── foggy/
    └── templates/
        ├── FactOrderModel.tm      # 表模型
        └── FactOrderQueryModel.qm # 查询模型
```

**示例 TM 文件** `FactOrderModel.tm`：

```javascript
export const model = {
    name: 'FactOrderModel',
    caption: '订单事实表',
    tableName: 'fact_order',
    idColumn: 'order_id',

    dimensions: [
        {
            name: 'customer',
            caption: '客户',
            tableName: 'dim_customer',
            foreignKey: 'customer_id',
            primaryKey: 'customer_id',
            captionColumn: 'customer_name',
            properties: [
                { column: 'customer_id', caption: '客户ID' },
                { column: 'customer_name', caption: '客户名称' }
            ]
        }
    ],

    properties: [
        { column: 'order_id', caption: '订单ID', type: 'STRING' },
        { column: 'order_status', caption: '订单状态', type: 'STRING' }
    ],

    measures: [
        { column: 'amount', caption: '订单金额', type: 'MONEY', aggregation: 'sum' }
    ]
};
```

**示例 QM 文件** `FactOrderQueryModel.qm`：

```javascript
export const queryModel = {
    name: 'FactOrderQueryModel',
    caption: '订单查询',
    model: 'FactOrderModel',

    columnGroups: [
        {
            caption: '订单信息',
            items: [
                { name: 'orderId' },
                { name: 'orderStatus' },
                { name: 'customer$caption' },
                { name: 'amount' }
            ]
        }
    ]
};
```

> **提示**：完整的 TM/QM 语法请参考 [TM 语法手册](/zh/dataset-model/tm-qm/tm-syntax) 和 [QM 语法手册](/zh/dataset-model/tm-qm/qm-syntax)。
>
> 如果你需要更详细的数据模型创建指南，请参考 [Dataset Model 快速开始](/zh/dataset-model/guide/quick-start)。

### 5. 启动服务

```bash
# Maven 启动
mvn spring-boot:run

# 或在 IDE 中运行主应用类
```

### 6. 验证服务

```bash
# 健康检查
curl http://localhost:7108/actuator/health

# 获取可用工具列表
curl -X POST http://localhost:7108/mcp/analyst/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}'
```

---

## 连接 AI 客户端 {#connect-ai-clients}

### Claude Desktop

编辑 Claude Desktop 配置文件：

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

重启 Claude Desktop 后即可使用。

### Cursor

在 Cursor 设置中添加 MCP 服务器：

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

详细配置请参考 [Cursor 集成指南](../integration/cursor.md)。

---

## 测试查询

连接成功后，可以在 AI 客户端中尝试：

```
"查询最近一周的销售数据"
"按商品分类统计销售额"
"上个月销售额前 10 的商品"
"各门店的订单数量对比"
```

---

## 预置数据模型（Docker 环境）

Docker 演示环境包含电商场景的数据模型：

| 查询模型 | 说明 | 主要字段 |
|---------|------|----------|
| FactSalesQueryModel | 销售分析 | 商品、分类、销售额、数量 |
| FactOrderQueryModel | 订单分析 | 订单号、客户、日期、金额 |
| FactPaymentQueryModel | 支付分析 | 支付方式、金额、状态 |
| FactReturnQueryModel | 退货分析 | 退货原因、金额、处理状态 |
| FactInventorySnapshotQueryModel | 库存快照 | 商品、仓库、库存量 |

---

## MCP 端点说明

| 端点 | 用途 | 适用场景 |
|------|------|----------|
| `/mcp/admin/rpc` | 管理员端点 | 全部工具权限，开发调试 |
| `/mcp/analyst/rpc` | 分析师端点 | 专业数据分析工具 |
| `/mcp/business/rpc` | 业务端点 | 仅自然语言查询 |
| `/actuator/health` | 健康检查 | 服务监控 |

---

## 常用命令（Docker）

```bash
# 停止服务
docker-compose down

# 重启 MCP 服务
docker-compose restart mcp

# 查看实时日志
docker-compose logs -f mcp

# 清空数据重新开始
docker-compose down -v
docker-compose up -d
```

---

## 访问数据库（Docker）

如需查看演示数据：

```bash
# 启动 Adminer（数据库管理工具）
docker-compose --profile tools up -d adminer
```

访问 http://localhost:18080：
- **系统**: MySQL
- **服务器**: mysql
- **用户名**: foggy
- **密码**: foggy_test_123
- **数据库**: foggy_test

---

## 故障排查

### 服务启动失败

```bash
# Docker 环境：检查各服务状态
docker-compose ps
docker-compose logs mcp

# Java 环境：检查日志
# 确认数据源配置正确
# 确认 TM/QM 文件语法正确
```

### 无法连接 AI 服务

1. 检查 `OPENAI_API_KEY` 是否正确
2. 检查网络连接
3. 如使用阿里云，确认 `OPENAI_BASE_URL` 包含 `/v1`

### Claude Desktop 无法连接

1. 确认配置文件路径正确
2. 检查 JSON 格式是否有效
3. 完全退出并重启 Claude Desktop
4. 确认 MCP 服务已启动且端口可访问

---

## 下一步

- [架构概述](./architecture.md) - 了解 MCP 服务架构
- [工具列表](../tools/overview.md) - 查看所有可用工具
- [Claude Desktop 集成](../integration/claude-desktop.md) - 详细配置指南
- [TM/QM 建模](/zh/dataset-model/guide/quick-start) - 创建自定义数据模型
