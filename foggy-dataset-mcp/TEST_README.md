# foggy-dataset-mcp 测试文档

## 概述

本文档描述了 `foggy-dataset-mcp` 模块的测试框架设计和使用方法。测试框架采用分层设计，涵盖单元测试和集成测试。

> **注意**: Spider2 基准测试已迁移到独立模块 `foggy-benchmark-spider2`，详见该模块的 README。

## 测试架构

```
src/test/java/com/foggyframework/dataset/mcp/
├── base/                          # 测试基础设施
│   ├── BaseMcpTest.java          # 基础测试类
│   └── MockToolFactory.java      # Mock 工具工厂
├── controller/                    # 控制器集成测试
│   ├── AdminMcpControllerTest.java
│   ├── AnalystMcpControllerTest.java
│   └── BusinessMcpControllerTest.java
├── service/                       # 服务层单元测试
│   ├── McpServiceTest.java
│   ├── McpToolDispatcherTest.java
│   └── ToolFilterServiceTest.java
├── tools/                         # 工具实现测试
│   ├── MetadataToolTest.java
│   ├── QueryModelToolTest.java
│   ├── ChartToolTest.java
│   ├── NaturalLanguageQueryToolTest.java
│   └── ExportWithChartToolTest.java
├── integration/                   # 集成测试
│   ├── McpIntegrationTestApplication.java
│   ├── McpIntegrationTestSupport.java
│   └── McpToolsIntegrationTest.java
└── ai/                            # AI 集成测试
    ├── AiIntegrationTestSupport.java
    ├── AiToolsIntegrationTest.java
    ├── EcommerceTestCase.java
    ├── TestCaseLoader.java
    ├── ResultValidator.java
    └── SpringAiTestExecutor.java
```

## 测试分层

### 第一层：单元测试

#### 1. 服务层测试

**ToolFilterServiceTest.java**
- 测试基于角色的工具过滤逻辑
- 验证不同角色（ADMIN、BUSINESS、ANALYST）的工具访问权限
- 纯单元测试，无外部依赖

```java
@Test
void admin_shouldAccessAllTools() {
    assertTrue(toolFilterService.canAccessTool(metadataTool, UserRole.ADMIN));
    assertTrue(toolFilterService.canAccessTool(nlTool, UserRole.ADMIN));
}

@Test
void business_shouldOnlyAccessNLTools() {
    assertTrue(toolFilterService.canAccessTool(nlTool, UserRole.BUSINESS));
    assertFalse(toolFilterService.canAccessTool(queryTool, UserRole.BUSINESS));
}
```

**McpServiceTest.java**
- 使用 Mockito 模拟依赖
- 测试 MCP 协议处理逻辑
- 测试方法：`handleInitialize`、`handleToolsList`、`handleToolsCall`、`handlePing`

**McpToolDispatcherTest.java**
- 测试工具注册和分发
- 使用 StepVerifier 测试响应式流
- 测试同步执行和流式执行

```java
@Test
void executeWithProgress_shouldEmitProgressEvents() {
    Flux<ProgressEvent> flux = dispatcher.executeWithProgress(request, "trace-1");

    StepVerifier.create(flux)
        .expectNextMatches(e -> "progress".equals(e.getType()))
        .expectNextMatches(e -> "complete".equals(e.getType()))
        .verifyComplete();
}
```

### 第二层：集成测试

#### 2. 控制器集成测试

使用 `@WebMvcTest` 进行 Spring MVC 集成测试。

**AdminMcpControllerTest.java**
- 测试管理员端点 `/mcp/admin/rpc`
- 验证管理员可访问所有工具
- 测试错误处理和异常场景

**BusinessMcpControllerTest.java**
- 测试业务用户端点 `/mcp/business/rpc`
- 验证只能访问自然语言工具
- 验证拒绝访问专业工具

**AnalystMcpControllerTest.java**
- 测试分析师端点 `/mcp/analyst/rpc`
- 验证可访问专业工具（metadata、query、chart、export）
- 验证拒绝访问自然语言工具

```java
@WebMvcTest(AdminMcpController.class)
@ActiveProfiles("test")
class AdminMcpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private McpService mcpService;

    @Test
    void shouldInitializeSuccessfully() throws Exception {
        mockMvc.perform(post("/mcp/admin/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"initialize\",\"params\":{}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.serverInfo.userRole").value("ADMIN"));
    }
}
```

### 第三层：工具实现测试

#### 3. 使用 WireMock 的工具测试

使用 WireMock 模拟外部服务（dataset-query-service、chart-render-service）。

**MetadataToolTest.java**
- 模拟 `/dataset/v1/metadata` 端点
- 测试成功获取元数据
- 测试错误处理（500、404、超时）

**QueryModelToolTest.java**
- 模拟 `/dataset/v1/query` 端点
- 测试参数验证（缺少 model、payload）
- 测试各种查询场景（过滤、分组、排序、分页）

**ChartToolTest.java**
- 模拟 `/render/unified/stream` 端点
- 测试不同图表类型（柱图、线图、饼图）
- 测试流式进度事件

**NaturalLanguageQueryToolTest.java**
- 使用 Mockito 模拟 QueryExpertService
- 测试自然语言查询处理
- 测试参数构建（hints、session_id、format）

```java
@BeforeAll
static void setupWireMock() {
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();
}

@Test
void shouldFetchMetadataSuccessfully() throws Exception {
    stubFor(get(urlEqualTo("/dataset/v1/metadata"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"models\":[{\"name\":\"SalesModel\"}]}")));

    Object result = metadataTool.execute(Map.of(), "trace-123");

    assertNotNull(result);
    verify(getRequestedFor(urlEqualTo("/dataset/v1/metadata"))
        .withHeader("X-Request-Id", equalTo("trace-123")));
}
```

### 第四层：AI 集成测试

使用真实 AI 模型（阿里云通义千问）测试 MCP 工具链。

**AiToolsIntegrationTest.java**
- 测试 AI 模型调用 MCP 工具的完整流程
- 测试用例从 JSON 文件加载
- 支持多分类测试（元数据、简单查询、聚合、复杂查询）

## 运行测试

### 运行所有单元测试

```bash
cd foggy-dataset-mcp
mvn test
```

### 运行特定测试类

```bash
# 运行服务层测试
mvn test -Dtest=McpServiceTest

# 运行控制器测试
mvn test -Dtest=*ControllerTest

# 运行工具测试
mvn test -Dtest=*ToolTest

# 运行 AI 集成测试
mvn test -Dtest=AiToolsIntegrationTest
```

## 测试配置

**application-test.yml**

```yaml
spring:
  profiles:
    active: test

mcp:
  dataset-query-url: http://localhost:8080
  chart-render-url: http://localhost:3000

logging:
  level:
    com.foggyframework.dataset.mcp: DEBUG
```

## 测试依赖

```xml
<!-- WireMock HTTP 模拟 -->
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.3.1</version>
    <scope>test</scope>
</dependency>

<!-- 异步测试 -->
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>

<!-- Reactor Test -->
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-test</artifactId>
    <scope>test</scope>
</dependency>
```

## 最佳实践

1. **使用 @DisplayName** - 中文测试名称提高可读性
2. **使用 @Nested** - 按功能分组测试用例
3. **使用 WireMock** - 隔离外部服务依赖
4. **使用 StepVerifier** - 测试响应式流
5. **使用 @EnabledIf** - 条件执行需要特定环境的测试

## 常见问题

### Q: WireMock 端口冲突
A: 使用 `WireMockConfiguration.wireMockConfig().dynamicPort()` 动态分配端口

### Q: AI 测试失败
A: 确保配置了正确的 API Key（DASHSCOPE_API_KEY）

---

最后更新：2025-12-05
