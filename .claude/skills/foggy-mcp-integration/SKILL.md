---
name: foggy-mcp-integration
description: 指导 Java 项目集成 foggy-dataset-mcp，部署 MCP 服务给 AI 使用。覆盖最小集成、可选模块选择、lite/标准/完整三种模式。当用户需要在 Java/Spring Boot 项目中部署 MCP 数据服务、集成 foggy-mcp 时使用。
---

# Foggy MCP 服务集成指南

在 Java 项目中集成 `foggy-dataset-mcp`，部署 MCP 端点供 AI 调用。

> 如果用户只需数据查询（不需要 MCP 端点），引导使用 `foggy-java-integration`。

## 模块选择矩阵

| 模块 | 必需/可选 | 用途 | 需 Mongo |
|------|----------|------|---------|
| foggy-dataset-mcp | 必需 | MCP 端点 + Tool 分发 | 否 |
| foggy-dataset-model | 传递依赖 | TM/QM 引擎 | 否 |
| foggy-dataset-demo | 可选 | 电商演示数据 | 否 |
| foggy-data-viewer | 可选 | 数据浏览器 | **是** |
| foggy-chart-storage-cloud | 可选 | 图表云存储（OSS/S3） | 否 |
| foggy-dataset-model-mongo | 可选 | MongoDB TM/QM 支持 | **是** |

## 三种集成模式

### Lite（无 MongoDB，推荐起步）

```xml
<properties>
    <foggy.version>8.1.8.beta</foggy.version>
</properties>

<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-mcp</artifactId>
    <version>${foggy.version}</version>
</dependency>
<!-- + JDBC 驱动 -->
```

### 标准（含 Data Viewer）

Lite 基础上追加：
```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-data-viewer</artifactId>
    <version>${foggy.version}</version>
</dependency>
<!-- + MongoDB 配置 -->
```

### 完整

标准基础上追加：
```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-chart-storage-cloud</artifactId>
    <version>${foggy.version}</version>
</dependency>
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-model-mongo</artifactId>
    <version>${foggy.version}</version>
</dependency>
```

## 执行流程

### 1. 判断项目现状

读取 `pom.xml`，检查是否为 Spring Boot 3.x（JDK 17+）、是否已有 foggy 依赖、是否有数据源。

### 2. 询问集成模式

确认：模式（Lite/标准/完整）、数据库类型、是否需要演示数据（`foggy-dataset-demo`）。

### 3. 添加依赖

按上文"三种集成模式"对应的 pom 片段添加。

### 4. 配置主应用类

按 `foggy-java-integration` skill 的步骤 3 配置 `@EnableFoggyFramework(bundleName = "xxx")`，额外加 `@EnableAsync`。

### 5. 配置 application.yml

**MCP 核心配置（所有模式）**：

```yaml
foggy:
  mcp:
    dataset:
      access-mode: local    # local=单体 / remote=微服务
    semantic:
      model-list:           # AI 可访问的 QM 模型
        - YourQueryModel
```

> Tools 配置无需手动声明 —— 模块内置了默认的 description/schema，仅在需要覆盖或禁用时才配置：
> ```yaml
> foggy.mcp.tools:
>   - name: "dataset.open_in_viewer"
>     enabled: false
> ```

**Lite 模式额外配置**（排除 MongoDB 自动配置）：

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
      - org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration
      - org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration
foggy:
  data-viewer:
    enabled: false
```

或直接 `--spring.profiles.active=lite`（如果项目提供了 `application-lite.yml`）。

**标准/完整模式 MongoDB 配置**：

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/your_database
      auto-index-creation: true
```

### 6. 创建模型目录

`src/main/resources/foggy/templates/`（引入 `foggy-dataset-demo` 则自动提供）。

### 7. 自检清单

| # | 检查项 | 检查方法 |
|---|--------|----------|
| 1 | foggy-dataset-mcp 依赖 | Grep pom.xml |
| 2 | @EnableFoggyFramework + @EnableAsync | Grep `**/*.java` |
| 3 | 数据源配置 | Grep `application*.yml` 中 `spring.datasource` |
| 4 | foggy.mcp 配置 | Grep `application*.yml` 中 `foggy.mcp` |
| 5 | 模型目录存在 | 检查 `foggy/templates/` |
| 6 | 模式对应配置 | Lite→exclude MongoDB；标准+→`spring.data.mongodb` |

## MCP 端点

| 端点 | 角色 | 协议 |
|------|------|------|
| `/mcp/analyst/rpc` | 分析师 | JSON-RPC（推荐） |
| `/mcp/analyst/stream` | 分析师 | SSE 流式 |
| `/mcp/admin/rpc` | 管理员（全部权限） | JSON-RPC |
| `/mcp/business/rpc` | 业务用户（仅查询） | JSON-RPC |

Namespace 隔离：通过 HTTP Header `X-NS` 传递。

## MongoDB 可选说明

`foggy-dataset-mcp` 的 MongoDB 依赖为 `optional`，仅审计日志需要。条件装配链路（`ToolAuditAutoConfiguration`）：`@ConditionalOnClass` → `@ConditionalOnProperty(foggy.mcp.audit.enabled=true)` → `@ConditionalOnBean(MongoTemplate)`。Lite 模式下审计类完全不加载。

## 输出格式

```
foggy-dataset-mcp 集成完成（{模式}模式）

已完成：依赖 + @EnableFoggyFramework + MCP 配置 + 模型目录

MCP 端点：
- JSON-RPC: http://localhost:{port}/mcp/analyst/rpc
- SSE:      http://localhost:{port}/mcp/analyst/stream

下一步：
1. 创建 TM/QM 模型（可使用 /foggy-model-workflow）
2. mvn spring-boot:run
3. AI 客户端配置 MCP 端点
```

## 决策规则

- 不确定模式 → 推荐 Lite
- 需要数据浏览器 → 标准
- 需要 MongoDB 数据源 → 完整
- 已有 foggy-dataset-model → 追加 foggy-dataset-mcp + MCP 配置
- 已有 foggy-dataset-mcp → 检查版本，提示升级或调整模式
- 非 Maven / 非 Spring Boot → 提示暂不支持

## 约束条件

- JDK 17+、Spring Boot 3.x
- 版本属性统一 `<foggy.version>`
- bundleName / 模型目录规范同 `foggy-java-integration`
