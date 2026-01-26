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
- 是否为 Maven 项目
- 是否为 Spring Boot 项目（检查 parent 或 spring-boot-starter 依赖）
- 是否已有数据源配置（检查 spring-boot-starter-jdbc 或 spring-boot-starter-data-jpa）
- 是否已引入 foggy-dataset-model

### 2. 添加依赖

**在 `<properties>` 中定义版本**：

```xml
<properties>
    <foggy-model.version>8.1.2.beta</foggy-model.version>
</properties>
```

**添加依赖**：

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-model</artifactId>
    <version>${foggy-model.version}</version>
</dependency>
```

**新建项目完整配置**：

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
</parent>

<properties>
    <java.version>17</java.version>
    <foggy-model.version>8.1.2.beta</foggy-model.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>com.foggysource</groupId>
        <artifactId>foggy-dataset-model</artifactId>
        <version>${foggy-model.version}</version>
    </dependency>
    <!-- 数据库驱动：根据实际选择 -->
</dependencies>
```

### 3. 配置主应用类

#### 3.1 导入必要的类

```java
import com.foggyframework.core.annotates.EnableFoggyFramework;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
```

#### 3.2 添加 @EnableFoggyFramework 注解

```java
@SpringBootApplication
@EnableFoggyFramework(bundleName = "your-project-name")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

#### 3.3 注解参数说明

| 参数 | 类型 | 必需 | 说明 | 有效值示例 |
|------|------|------|------|-----------|
| bundleName | String | 是 | 项目唯一标识，使用小写中划线格式 | `"fleetsync"`<br>`"order-service"`<br>`"user-center"` |

**bundleName 命名规范**:
- ✅ 使用小写字母
- ✅ 单词之间用中划线 `-` 连接
- ✅ 建议与项目 artifactId 保持一致
- ❌ 不要使用驼峰命名
- ❌ 不要使用下划线 `_`
- ❌ 不要使用大写字母

#### 3.4 依赖检查

确保 `pom.xml` 中包含以下依赖：

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-model</artifactId>
    <version>8.1.2.beta</version>
</dependency>
```

**重要提示**: `@EnableFoggyFramework` 注解包含在 `foggy-dataset-model` 依赖中，不需要额外引入其他 jar 包。

#### 3.5 常见问题

**Q: IDE 提示 "Cannot resolve symbol 'EnableFoggyFramework'"？**

A: 检查以下几点：
1. `foggy-dataset-model` 依赖是否已正确添加到 pom.xml
2. Maven 依赖是否已刷新（右键项目 → Maven → Reload Project）
3. IDE 是否正确识别了项目的 JDK 版本（需要 JDK 17+）

**Q: 启动时报错 "bundleName cannot be empty"？**

A: 确保 `bundleName` 参数已正确设置，例如：
```java
@EnableFoggyFramework(bundleName = "fleetsync")  // ✅ 正确
@EnableFoggyFramework                             // ❌ 错误：缺少参数
```

### 4. 配置数据源

检查 `src/main/resources/application.yml` 或 `application.properties`：
- 如已有数据源配置 → 跳过
- 如无数据源配置 → 询问用户数据库类型，添加配置模板

### 5. 创建模型目录

创建 `src/main/resources/foggy/templates/` 目录（如不存在）

### 6. 验证配置

提示用户启动项目验证配置是否正确

## 输入要求

用户需提供：
- 项目路径（如未提供则使用当前工作目录）
- 数据库类型：MySQL / PostgreSQL / SQLite（如未配置数据源）
- bundleName（如未提供则从 artifactId 推导）

## 输出格式

完成后输出：

```
foggy-dataset-model 集成完成

已完成配置：
- 添加 Maven 依赖（版本 ${foggy-model.version}）
- 配置 @EnableFoggyFramework(bundleName = "xxx")
- 配置数据源（如适用）
- 创建模型目录 foggy/templates/

下一步：
1. 创建 TM 模型：foggy/templates/XxxModel.tm
2. 创建 QM 模型：foggy/templates/XxxQueryModel.qm
3. 启动项目：mvn spring-boot:run
4. 测试查询：POST /jdbc-model/query-model/v2/{QueryModelName}
```

## 约束条件

- 使用 Maven 属性管理版本：`<foggy-model.version>8.1.2.beta</foggy-model.version>`
- bundleName 使用小写中划线格式（如 `my-project`）
- 模型文件目录固定为 `src/main/resources/foggy/templates/`
- 需要 JDK 17+、Spring Boot 3.x

## 决策规则

- 如果 pom.xml 已包含 foggy-dataset-model → 检查版本，提示用户是否需要升级或其他帮助
- 如果已有 `<properties>` → 在其中添加 `foggy-model.version`
- 如果无 `<properties>` → 创建 `<properties>` 节点
- 如果不是 Maven 项目 → 提示暂不支持 Gradle，建议手动添加依赖
- 如果无 Spring Boot → 提示需要 Spring Boot 3.x 环境
- 如果用户选择 SQLite → 额外添加 sqlite-jdbc 依赖并使用文件路径配置
- 如果检测到 JPA/MyBatis → 提示 Foggy 可与其共存，无需额外配置
- 如果找不到主应用类 → 搜索 `@SpringBootApplication` 注解的类

## 数据库驱动参考

| 数据库 | groupId | artifactId | scope |
|--------|---------|------------|-------|
| MySQL | com.mysql | mysql-connector-j | runtime |
| PostgreSQL | org.postgresql | postgresql | runtime |
| SQLite | org.xerial | sqlite-jdbc | runtime |

## 数据源配置模板

**MySQL**：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

**PostgreSQL**：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/your_database
    username: postgres
    password: your_password
    driver-class-name: org.postgresql.Driver
```

**SQLite**：
```yaml
spring:
  datasource:
    url: jdbc:sqlite:./data/app.db
    driver-class-name: org.sqlite.JDBC
```

## Foggy 可选配置

```yaml
foggy:
  dataset:
    show-sql: true               # 打印 SQL（开发环境建议开启）
    sql-format: false            # SQL 格式化（true=多行）
    sql-log-level: DEBUG         # 日志级别
    show-sql-parameters: true    # 显示参数值
    show-execution-time: true    # 显示执行时间
```
