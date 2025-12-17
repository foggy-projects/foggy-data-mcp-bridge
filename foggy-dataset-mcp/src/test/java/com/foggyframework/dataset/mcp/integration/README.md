# MCP Integration Test - 集成测试说明

## 概述

本集成测试套件用于在真实数据库环境下测试 MCP 工具的功能。与单元测试不同，集成测试直接调用 `SemanticService` 和 `SemanticQueryService`，验证 MCP 工具与底层数据服务的完整集成。

## 测试架构

```
┌──────────────────────────────────────────────────────────────────┐
│                    Integration Test                               │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌─────────────────────┐    ┌─────────────────────────────────┐  │
│  │     MCP Tools       │    │    LocalDatasetWebClient        │  │
│  │ - MetadataTool      │───▶│  (替代真实 HTTP 调用)           │  │
│  │ - QueryModelTool    │    │                                 │  │
│  │ - DescriptionTool   │    └─────────────┬───────────────────┘  │
│  └─────────────────────┘                  │                      │
│                                           ▼                      │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              foggy-dataset-model Services                  │  │
│  │  - SemanticService (元数据、模型描述)                       │  │
│  │  - SemanticQueryService (数据查询)                         │  │
│  │  - JdbcModelLoader (模型加载)                              │  │
│  └─────────────────────────┬──────────────────────────────────┘  │
│                            │                                     │
│                            ▼                                     │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                   MySQL Database                            │  │
│  │           (Docker: localhost:13306)                         │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

## 前置条件

### 1. 启动 Docker 数据库环境

```bash
cd foggy-dataset-model/docker
docker-compose -f docker-compose.test.yml up -d
```

这将启动：
- MySQL 5.7 (端口 13306)
- MongoDB 6.0 (端口 17017)
- Redis 7 (端口 16379)
- Adminer (端口 18080) - 可选的数据库管理界面

### 2. 验证数据库连接

```bash
# MySQL
mysql -h localhost -P 13306 -u foggy -pfoggy_test_123 foggy_test

# 或使用 Adminer
# 浏览器打开 http://localhost:18080
# 系统: MySQL, 服务器: mysql, 用户名: foggy, 密码: foggy_test_123, 数据库: foggy_test
```

### 3. 确认测试数据已加载

测试数据通过 Docker 初始化脚本自动加载。验证关键表：

```sql
SELECT COUNT(*) FROM dim_product;  -- 应该 > 0
SELECT COUNT(*) FROM dim_customer; -- 应该 > 0
SELECT COUNT(*) FROM fact_sales;   -- 应该 > 0
```

## 运行测试

### 方法 1: 使用 Maven

```bash
# 运行所有集成测试
cd foggy-dataset-mcp
mvn test -Dtest=*IntegrationTest -Dspring.profiles.active=integration

# 运行特定测试类
mvn test -Dtest=McpToolsIntegrationTest -Dspring.profiles.active=integration

# 运行特定测试方法
mvn test -Dtest=McpToolsIntegrationTest#verifyDatabaseAndTestData -Dspring.profiles.active=integration
```

### 方法 2: 使用 IDE

1. 打开 `McpToolsIntegrationTest.java`
2. 设置 Active Profiles 为 `integration`
3. 运行测试

## 测试覆盖范围

### 1. 环境验证测试
- 数据库连接验证
- 测试数据完整性检查
- MCP 工具注册验证

### 2. MetadataTool 测试
- 获取元数据列表
- 验证返回的模型信息

### 3. QueryModelTool 测试
- 基本查询（FactSalesQueryModel）
- 带条件查询
- 带聚合查询
- 订单查询（FactOrderQueryModel）
- 验证模式测试
- 错误处理测试

### 4. DescriptionModelTool 测试
- 模型字段描述获取
- 多模型描述测试

### 5. 端到端场景测试
- 完整查询流程：元数据 -> 模型描述 -> 数据查询
- 多维度销售分析场景

## 配置说明

### application-integration.yml

关键配置项：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:13306/foggy_test
    username: foggy
    password: foggy_test_123

  data:
    mongodb:
      uri: mongodb://localhost:17017/foggy_test

foggy:
  dataset:
    templates-path: classpath:/foggy/templates/
```

### LocalDatasetWebClient

`McpIntegrationTestConfig` 中的 `LocalDatasetWebClient` 拦截所有 HTTP 请求，并将其转发到本地服务：

- `/dataset/v1/metadata` → `SemanticService.getMetadata()`
- `/dataset/v1/query` → `SemanticQueryService.queryModel()`
- `/semantic/v1/description-model-internal/{model}` → `SemanticService.getMetadata()`

## 故障排除

### 数据库连接失败

```
org.springframework.jdbc.CannotGetJdbcConnectionException
```

解决方案：
1. 确认 Docker 容器正在运行: `docker ps`
2. 检查端口是否正确: `13306`
3. 验证凭据

### 模型加载失败

```
未能在[xxx]中找到[yyy]的定义
```

解决方案：
1. 确认模型文件存在于 `src/test/resources/foggy/templates/ecommerce/`
2. 检查文件名是否正确

### MongoDB 连接失败

如果不需要 MongoDB，可以在测试中添加排除：

```java
@SpringBootTest(classes = McpIntegrationTestApplication.class,
    properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration")
```

## 扩展测试

### 添加新的测试场景

1. 在 `McpToolsIntegrationTest` 中添加新的 `@Nested` 类
2. 使用 `executeTool()` 辅助方法执行工具
3. 使用 `printJson()` 查看结果

### 测试其他工具

```java
@Test
void testNewTool() {
    Object result = executeTool("new.tool.name", Map.of(
        "param1", "value1",
        "param2", "value2"
    ));
    assertNotNull(result);
    printJson(result, "New Tool Result");
}
```

## 测试数据

测试使用的是电商场景数据，包括：

- **维度表**：商品、客户、门店、渠道、促销、日期
- **事实表**：销售、订单、支付、退货、库存
- **层次结构**：团队（父子维度）、嵌套维度

数据量适中，足够验证查询功能但不影响测试速度。
