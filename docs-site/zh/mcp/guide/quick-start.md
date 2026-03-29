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


### 3. 启动服务

```bash
# 一键启动（首次需要构建镜像，约 3-5 分钟）
docker-compose up -d

# 查看启动日志
docker-compose logs -f mcp
```

### 3.1 启用图表功能（可选）

如需使用 `export_with_chart` 工具生成可视化图表，需额外部署图表渲染服务：

```bash
# 进入图表服务目录
cd docker/demo/chart

# 启动图表渲染服务
docker compose up -d

# 验证服务
curl http://localhost:3000/healthz
```

> 详细配置请参考 [图表渲染服务](./chart-render-service.md)

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

### 快速体验 Demo（可选）

如果你只想快速体验功能，可以直接运行内置的演示模块：

```bash
# 克隆项目
git clone https://github.com/foggy-projects/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge

# 启动演示数据库（MySQL + 预置电商数据）
cd foggy-dataset-demo/docker
docker-compose up -d mysql

# 回到项目根目录，运行启动器
cd ../..
mvn spring-boot:run -pl foggy-mcp-launcher
```

启动后访问 http://localhost:7108/actuator/health 验证服务状态，然后跳转到 [连接 AI 客户端](#connect-ai-clients)。

> **提示**：Demo 模块默认启用。如需禁用，添加启动参数 `--foggy.demo.enabled=false`。

---

如果你需要将 MCP 服务集成到自己的项目中，请继续下面的步骤：

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
        <version>8.1.9.beta</version>
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
    <version>8.1.9.beta</version>
</dependency>
```

#### 1.3 使用演示语义层（可选）

如果暂时没有自己的语义层定义，可以添加演示模块快速体验：

```xml
<!-- 演示语义层（电商场景） -->
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-demo</artifactId>
    <version>8.1.9.beta</version>
</dependency>
```

演示模块需要配合 `foggy-dataset-demo/docker` 中的 MySQL 数据库使用：

```bash
# 启动演示数据库
cd foggy-dataset-demo/docker
docker-compose up -d mysql
```

数据源配置：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:13306/foggy_test
    username: foggy
    password: foggy_test_123
```

> **提示**：演示模块默认启用。生产环境可通过 `foggy.demo.enabled=false` 禁用。

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

### 4. 定义语义层

Foggy 使用 TM（Table Model）和 QM（Query Model）文件定义语义层，让 AI 理解业务含义而非底层表结构：

- **TM 文件**：定义维度、属性和度量，将数据库字段映射为业务概念（如"客户"、"销售额"）
- **QM 文件**：定义查询视图、可用字段和访问权限

语义层文件放置在 `src/main/resources/foggy/templates/` 目录下。

> **提示**：如果你只想快速体验，可以先使用演示模块（见上文 1.3 节），无需自己定义语义层。
>
> 需要创建自定义语义层时，请参考 [TM 语法手册](/zh/dataset-model/tm-qm/tm-syntax) 和 [QM 语法手册](/zh/dataset-model/tm-qm/qm-syntax)。

### 5. 启动服务

```bash
# Maven 启动
mvn spring-boot:run

# 或在 IDE 中运行主应用类
```

### 5.1 启用图表功能（可选）

如需使用 `export_with_chart` 工具，需部署图表渲染服务：

```bash
docker run -d --name chart-render \
  -p 3000:3000 \
  -e RENDER_TOKEN=default-render-token \
  foggysource/chart-render-service:latest
```

> 详细配置请参考 [图表渲染服务](./chart-render-service.md)

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

服务启动成功后，选择你常用的 AI 工具进行配置：

| AI 工具 | 配置方式 | 详细指南 |
|---------|----------|----------|
| Trae CN | JSON 配置 | [Trae 集成指南](../integration/trae.md) |
| Cursor | 设置界面 / JSON | [Cursor 集成指南](../integration/cursor.md) |
| Claude Desktop | 配置文件 | [Claude Desktop 集成指南](../integration/claude-desktop.md) |

### Trae CN（推荐国内用户）

1. 打开 Trae IDE → **设置** → **MCP** → **原始配置（JSON）**
2. 添加配置：

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

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
"2024年12月各品类的销售额"
"2024年Q4销售额前10的商品"
"各门店的订单数量对比"
"查看2024年12月的订单明细,并提供数据预览链接"
```

::: tip 图表功能
如需生成可视化图表，请先部署[图表渲染服务](./chart-render-service.md)。
:::

---

## 预置语义层（Docker 环境）

Docker 演示环境包含电商场景的语义层模型：

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
- [图表渲染服务](./chart-render-service.md) - 启用数据可视化功能
- [Claude Desktop 集成](../integration/claude-desktop.md) - 详细配置指南
- [TM/QM 建模](/zh/dataset-model/guide/quick-start) - 创建自定义语义层
