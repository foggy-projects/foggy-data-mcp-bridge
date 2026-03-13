---
name: foggy-mcp-integration
description: 指导 Java 项目集成 foggy-dataset-mcp，部署 MCP 服务给 AI 使用。覆盖最小集成、可选模块选择、lite/标准/完整三种模式。当用户需要在 Java/Spring Boot 项目中部署 MCP 数据服务、集成 foggy-mcp 时使用。
---

# Foggy MCP 服务集成指南

帮助用户在 Java 项目中集成 `foggy-dataset-mcp`，部署 MCP 数据服务端点供 AI 调用。

## 与 foggy-java-integration 的区别

| Skill | 目标 | 核心模块 |
|-------|------|----------|
| `foggy-java-integration` | 在 Java 项目中用 Foggy 查数据 | `foggy-dataset-model` |
| **`foggy-mcp-integration`** | 部署 MCP 服务给 AI 用 | `foggy-dataset-mcp` |

如果用户只需要数据查询能力（不需要 MCP 端点），应引导使用 `foggy-java-integration`。

## 模块选择矩阵

```
┌──────────────────────────┬──────────┬─────────────────────────┬───────────┐
│ 模块                      │ 必需/可选 │ 用途                     │ 需要 Mongo │
├──────────────────────────┼──────────┼─────────────────────────┼───────────┤
│ foggy-dataset-mcp        │ 必需     │ MCP 端点 + Tool 分发      │ 否        │
│ foggy-dataset-model      │ 传递依赖 │ TM/QM 引擎               │ 否        │
│ foggy-dataset-demo       │ 可选     │ 电商演示数据              │ 否        │
│ foggy-data-viewer        │ 可选     │ 数据浏览器（缓存查询结果）  │ 是        │
│ foggy-chart-storage-cloud│ 可选     │ 图表云存储（OSS/S3/OBS）  │ 否        │
│ foggy-dataset-model-mongo│ 可选     │ MongoDB 数据源 TM/QM 支持 │ 是        │
│ foggy-dataset-mongo      │ 可选     │ MongoDB fsscript 支持    │ 是        │
└──────────────────────────┴──────────┴─────────────────────────┴───────────┘
```

## 三种集成模式

### Lite 模式（无 MongoDB）
最小依赖，纯 JDBC 数据源 + MCP 端点。

```xml
<dependencies>
    <dependency>
        <groupId>com.foggysource</groupId>
        <artifactId>foggy-dataset-mcp</artifactId>
        <version>${foggy.version}</version>
    </dependency>
    <!-- JDBC 驱动 -->
</dependencies>
```

启动时激活 lite profile 或手动排除 MongoDB 自动配置：
```yaml
spring:
  profiles:
    active: lite
```

### 标准模式（含 Data Viewer）
包含数据浏览器，需要 MongoDB。

```xml
<dependencies>
    <dependency>
        <groupId>com.foggysource</groupId>
        <artifactId>foggy-dataset-mcp</artifactId>
        <version>${foggy.version}</version>
    </dependency>
    <dependency>
        <groupId>com.foggysource</groupId>
        <artifactId>foggy-data-viewer</artifactId>
        <version>${foggy.version}</version>
    </dependency>
    <!-- JDBC 驱动 + MongoDB 配置 -->
</dependencies>
```

### 完整模式（全部 addon）
包含所有可选模块。

```xml
<dependencies>
    <dependency>
        <groupId>com.foggysource</groupId>
        <artifactId>foggy-dataset-mcp</artifactId>
        <version>${foggy.version}</version>
    </dependency>
    <dependency>
        <groupId>com.foggysource</groupId>
        <artifactId>foggy-data-viewer</artifactId>
        <version>${foggy.version}</version>
    </dependency>
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
    <!-- JDBC 驱动 + MongoDB 配置 -->
</dependencies>
```

## 执行流程

### 1. 判断项目现状

读取项目 `pom.xml`，检查：
- 是否为 Spring Boot 3.x 项目（JDK 17+）
- 是否已有 `foggy-dataset-model` 或 `foggy-dataset-mcp` 依赖
- 是否已有数据源配置
- 是否有 MongoDB 依赖

### 2. 询问集成模式

向用户确认：
- **模式选择**：Lite / 标准 / 完整
- **数据库类型**：MySQL / PostgreSQL / SQLite
- **是否需要演示数据**：引入 `foggy-dataset-demo`

### 3. 添加依赖

**版本属性**：
```xml
<properties>
    <foggy.version>8.1.7.beta</foggy.version>
</properties>
```

根据用户选择的模式添加对应依赖（见上文三种模式）。

### 4. 配置主应用类

```java
import com.foggyframework.core.annotates.EnableFoggyFramework;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableFoggyFramework(bundleName = "your-project-name")
@EnableAsync  // MCP 审计日志需要异步支持
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

`bundleName` 命名规范：小写中划线格式（如 `order-service`），建议与 artifactId 一致。

### 5. 配置 application.yml

#### 5.1 MCP 核心配置（所有模式必需）

```yaml
foggy:
  mcp:
    dataset:
      access-mode: local  # local=单体 / remote=微服务

    semantic:
      model-list:         # AI 可访问的 QM 模型列表
        - YourQueryModel
      metadata:
        default-levels: [1]
      internal:
        default-levels: [1]

    service:
      m1-port: 7108
      m2-port: 7109
      enabled-layer: both

    # MCP 工具配置（按需启用/禁用）
    tools:
      - name: "dataset.get_metadata"
        enabled: true
        descriptionFile: "classpath:/schemas/descriptions/get_metadata.md"
        schemaFile: "classpath:/schemas/get_metadata_schema.json"
        category: METADATA
      - name: "dataset.describe_model_internal"
        enabled: true
        descriptionFile: "classpath:/schemas/descriptions/describe_model_internal.md"
        schemaFile: "classpath:/schemas/describe_model_internal_schema.json"
        category: METADATA
      - name: "dataset.query_model"
        enabled: true
        descriptionFile: "classpath:/schemas/descriptions/query_model_v3.md"
        schemaFile: "classpath:/schemas/query_model_v3_schema.json"
        category: QUERY
```

#### 5.2 Lite 模式额外配置

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
  mcp:
    audit:
      enabled: false
    tools:
      - name: "dataset.open_in_viewer"
        enabled: false
```

或者直接使用 `application-lite.yml` profile（如果从 foggy-mcp-launcher 复制配置）。

#### 5.3 标准/完整模式 MongoDB 配置

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/your_database
      auto-index-creation: true

foggy:
  data-viewer:
    enabled: true
  mcp:
    audit:
      enabled: false  # 按需开启
```

### 6. 创建模型目录

```
src/main/resources/foggy/templates/
```

如果用户选择了演示数据（`foggy-dataset-demo`），此目录自动由 demo 模块提供。

### 7. 验证配置（自检清单）

| # | 检查项 | 检查方法 |
|---|--------|----------|
| 1 | foggy-dataset-mcp 依赖 | Grep pom.xml 中 `foggy-dataset-mcp` |
| 2 | 版本属性 | Grep pom.xml 中 `<foggy.version>` |
| 3 | @EnableFoggyFramework | Grep `**/*.java` 中 `@EnableFoggyFramework` |
| 4 | @EnableAsync | Grep `**/*.java` 中 `@EnableAsync` |
| 5 | bundleName 非空 | 确认注解中 `bundleName = "..."` 不为空 |
| 6 | 数据源配置 | Grep `application*.yml` 中 `spring.datasource` |
| 7 | MCP 配置 | Grep `application*.yml` 中 `foggy.mcp` |
| 8 | 模型目录 | 检查 `foggy/templates/` 目录存在 |
| 9 | Lite: MongoDB 排除 | 如 Lite 模式，确认 `spring.autoconfigure.exclude` |
| 10 | 标准+: MongoDB 配置 | 如标准/完整模式，确认 `spring.data.mongodb` |

## MCP 端点说明

集成完成后可用的 MCP 端点：

| 端点 | 角色 | 协议 |
|------|------|------|
| `/mcp/analyst/rpc` | 分析师 | JSON-RPC（推荐） |
| `/mcp/analyst/stream` | 分析师 | SSE 流式 |
| `/mcp/admin/rpc` | 管理员（全部权限） | JSON-RPC |
| `/mcp/business/rpc` | 业务用户（仅查询） | JSON-RPC |

Namespace 隔离：通过 HTTP Header `X-NS` 传递。

## MongoDB 可选架构说明

`foggy-dataset-mcp` 的 MongoDB 依赖为 `optional`，仅审计日志功能需要。

条件装配链路（`ToolAuditAutoConfiguration`）：
1. `@ConditionalOnClass` — classpath 无 MongoDB 则整个配置跳过
2. `@ConditionalOnProperty(foggy.mcp.audit.enabled=true)` — 默认关闭
3. `@ConditionalOnBean(MongoTemplate)` — 需已配置 MongoDB 连接

Lite 模式下审计相关类**完全不加载**，零影响。

## 输出格式

```
foggy-dataset-mcp 集成完成（{模式}模式）

已完成配置：
- 添加 Maven 依赖（foggy-dataset-mcp + {可选模块}）
- 配置 @EnableFoggyFramework(bundleName = "xxx") + @EnableAsync
- 配置 MCP 端点和语义模型
- {Lite: 排除 MongoDB 自动配置}
- {标准+: 配置 MongoDB 连接}
- 创建模型目录 foggy/templates/

MCP 端点：
- JSON-RPC: http://localhost:{port}/mcp/analyst/rpc
- SSE:      http://localhost:{port}/mcp/analyst/stream

下一步：
1. 创建 TM/QM 模型（可使用 /foggy-model-workflow）
2. 启动项目：mvn spring-boot:run
3. 在 AI 客户端配置 MCP 端点地址
```

## 决策规则

- 用户不确定模式 → 推荐 Lite 模式（最简单）
- 用户需要数据浏览器 → 标准模式
- 用户需要 MongoDB 数据源 → 完整模式（含 foggy-dataset-model-mongo）
- 已有 foggy-dataset-model → 只需追加 foggy-dataset-mcp + MCP 配置
- 已有 foggy-dataset-mcp → 检查版本，提示是否需要升级或调整模式
- 非 Maven / 非 Spring Boot → 提示暂不支持

## 约束条件

- JDK 17+、Spring Boot 3.x
- 版本属性统一用 `<foggy.version>`
- bundleName 小写中划线格式
- 模型文件目录固定 `src/main/resources/foggy/templates/`
