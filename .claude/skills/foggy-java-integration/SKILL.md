---
name: foggy-java-integration
description: 指导 Java 项目引入 foggy-dataset-model 依赖并完成基础配置。当用户需要在 Java/Spring Boot 项目中集成 Foggy Dataset Model、配置 TM/QM 模型时使用。
---

# Foggy Dataset Model Java 集成指南

帮助用户在 Java 项目中引入 foggy-dataset-model 依赖，完成必要配置，创建示例 TM/QM 模型。

## 使用场景

当用户需要以下操作时使用：
- 在现有 Java/Spring Boot 项目中引入 foggy-dataset-model
- 从零创建包含 foggy-dataset-model 的新项目
- 配置数据源和 Foggy 框架
- 创建 TM/QM 模型文件

## 执行流程

### 1. 判断项目类型

读取项目根目录的 `pom.xml` 文件，判断：
- 是否为 Spring Boot 项目（检查 parent 或 spring-boot-starter 依赖）
- 是否已有数据源配置（检查 spring-boot-starter-jdbc 或 spring-boot-starter-data-jpa）
- 是否已引入 foggy-dataset-model

### 2. 添加依赖

**已有 Spring Boot 项目**：仅添加核心依赖：

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-model</artifactId>
    <version>8.1.2.beta</version>
</dependency>
```

**新建项目**：添加完整依赖（Spring Boot parent + Web + JDBC + foggy-dataset-model + 数据库驱动）

### 3. 配置主应用类

在 `@SpringBootApplication` 注解的类上添加 `@EnableFoggyFramework`：

```java
@SpringBootApplication
@EnableFoggyFramework(bundleName = "{项目名称}")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 4. 配置数据源

检查 `src/main/resources/application.yml` 或 `application.properties`：
- 如已有数据源配置 → 跳过
- 如无数据源配置 → 询问用户数据库类型，添加配置模板

### 5. 创建模型目录

创建 `src/main/resources/foggy/templates/` 目录（如不存在）

### 6. 可选：创建示例 TM/QM

询问用户是否需要创建示例模型文件

## 输入要求

用户需提供：
- 项目路径（如未提供则使用当前工作目录）
- 数据库类型：MySQL / PostgreSQL / SQLite（如未配置数据源）
- bundleName（如未提供则从项目名推导）

## 输出格式

完成后输出：

```
✓ foggy-dataset-model 集成完成

已完成配置：
- [x] 添加 Maven 依赖
- [x] 配置 @EnableFoggyFramework
- [x] 配置数据源
- [x] 创建模型目录

下一步：
1. 在 foggy/templates/ 下创建 .tm 和 .qm 文件
2. 启动项目：mvn spring-boot:run
3. 测试查询：POST /jdbc-model/query-model/v2/{QueryModelName}
```

## 约束条件

- 版本固定使用 `8.1.2.beta`
- bundleName 必须符合 Java 标识符规范
- 模型文件目录固定为 `src/main/resources/foggy/templates/`
- 不自动创建数据库表结构，仅提供配置

## 决策规则

- 如果 pom.xml 已包含 foggy-dataset-model → 提示用户已集成，询问是否需要其他帮助
- 如果不是 Maven 项目 → 提示暂不支持 Gradle，建议手动添加依赖
- 如果无 Spring Boot → 提示需要 Spring Boot 3.x 环境
- 如果用户选择 SQLite → 额外添加 sqlite-jdbc 依赖并配置文件路径
- 如果检测到 JPA 依赖 → 提示 Foggy 可与 JPA 共存，无需额外配置

## 数据库驱动参考

| 数据库 | 依赖 |
|--------|------|
| MySQL | `com.mysql:mysql-connector-j` |
| PostgreSQL | `org.postgresql:postgresql` |
| SQLite | `org.xerial:sqlite-jdbc:3.44.1.0` |

## Foggy 可选配置

```yaml
foggy:
  dataset:
    show-sql: true               # 打印 SQL（开发环境）
    sql-format: false            # SQL 格式化
    sql-log-level: DEBUG         # 日志级别
    show-sql-parameters: true    # 显示参数值
    show-execution-time: true    # 显示执行时间
```
