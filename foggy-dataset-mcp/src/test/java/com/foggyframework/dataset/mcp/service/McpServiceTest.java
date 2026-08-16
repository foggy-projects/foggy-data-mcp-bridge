package com.foggyframework.dataset.mcp.service;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.mcp.base.BaseMcpTest;
import com.foggyframework.dataset.mcp.base.MockToolFactory;
import com.foggyframework.dataset.mcp.enums.UserRole;
import com.foggyframework.dataset.mcp.schema.McpError;
import com.foggyframework.dataset.mcp.schema.McpRequest;
import com.foggyframework.dataset.mcp.schema.McpRequestContext;
import com.foggyframework.dataset.mcp.schema.McpResponse;
import com.foggyframework.mcp.spi.McpTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * McpService 单元测试
 *
 * 使用 Mockito Mock 依赖进行测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("McpService 单元测试")
class McpServiceTest extends BaseMcpTest {

    @Mock
    private McpToolDispatcher toolDispatcher;

    @Mock
    private ToolFilterService toolFilterService;

    @Mock
    private NamespaceToolPolicyService namespaceToolPolicyService;

    @InjectMocks
    private McpService mcpService;

    @BeforeEach
    void allowNamespaceToolsByDefault() {
        when(namespaceToolPolicyService.resolveAvailableTools(
                anyCollection(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> new LinkedHashSet<>(invocation.getArgument(0)));
        when(namespaceToolPolicyService.isAvailable(
                anyString(), anyCollection(), any(), any(), any(), any(), any()))
                .thenReturn(true);
    }

    // ==================== handleInitialize 测试 ====================

    @Nested
    @DisplayName("handleInitialize - 初始化请求处理")
    class HandleInitializeTest {

        @Test
        @DisplayName("Admin 角色初始化应返回服务器信息")
        void admin_shouldReturnServerInfo() {
            McpRequest request = createInitializeRequest();

            McpResponse response = mcpService.handleInitialize(request, UserRole.ADMIN);

            assertResponseSuccess(response);
            Map<String, Object> result = extractResultMap(response);

            assertEquals("2024-11-05", result.get("protocolVersion"));
            assertNotNull(result.get("capabilities"));
            assertNotNull(result.get("serverInfo"));

            @SuppressWarnings("unchecked")
            Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
            assertEquals("ADMIN", serverInfo.get("userRole"));
        }

        @Test
        @DisplayName("Business 角色初始化应包含角色描述")
        void business_shouldIncludeRoleDescription() {
            McpRequest request = createInitializeRequest();

            McpResponse response = mcpService.handleInitialize(request, UserRole.BUSINESS);

            assertResponseSuccess(response);
            Map<String, Object> result = extractResultMap(response);

            @SuppressWarnings("unchecked")
            Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
            assertEquals("BUSINESS", serverInfo.get("userRole"));
            assertNotNull(serverInfo.get("roleDescription"));
        }

        @Test
        @DisplayName("响应应包含协议版本")
        void shouldIncludeProtocolVersion() {
            McpRequest request = createInitializeRequest();

            McpResponse response = mcpService.handleInitialize(request, UserRole.ANALYST);

            Map<String, Object> result = extractResultMap(response);
            assertEquals("2024-11-05", result.get("protocolVersion"));
        }

        @Test
        @DisplayName("响应应包含 capabilities")
        void shouldIncludeCapabilities() {
            McpRequest request = createInitializeRequest();

            McpResponse response = mcpService.handleInitialize(request, UserRole.ADMIN);

            Map<String, Object> result = extractResultMap(response);
            @SuppressWarnings("unchecked")
            Map<String, Object> capabilities = (Map<String, Object>) result.get("capabilities");

            assertNotNull(capabilities);
            assertNotNull(capabilities.get("tools"));
            assertNotNull(capabilities.get("logging"));
        }
    }

    // ==================== handleToolsList 测试 ====================

    @Nested
    @DisplayName("handleToolsList - 工具列表请求处理")
    class HandleToolsListTest {

        @Test
        @DisplayName("应根据角色过滤工具列表")
        void shouldFilterToolsByRole() {
            McpRequest request = createToolsListRequest();

            List<Map<String, Object>> allDefs = List.of(
                    Map.of("name", "dataset.get_metadata"),
                    Map.of("name", "dataset_nl.query"),
                    Map.of("name", "dataset.query_model")
            );
            List<McpTool> allTools = MockToolFactory.createAllStandardTools();
            List<Map<String, Object>> filteredDefs = List.of(
                    Map.of("name", "dataset.get_metadata"),
                    Map.of("name", "dataset.query_model")
            );

            when(toolDispatcher.getToolDefinitions()).thenReturn(allDefs);
            when(toolDispatcher.getAllTools()).thenReturn(allTools);
            when(toolFilterService.filterToolDefinitionsByRole(allDefs, allTools, UserRole.ANALYST))
                    .thenReturn(filteredDefs);

            McpResponse response = mcpService.handleToolsList(request, UserRole.ANALYST);

            assertResponseSuccess(response);
            List<Map<String, Object>> tools = extractToolsList(response);
            assertEquals(2, tools.size());

            verify(toolFilterService).filterToolDefinitionsByRole(allDefs, allTools, UserRole.ANALYST);
        }

        @Test
        @DisplayName("Admin 应看到所有工具")
        void admin_shouldSeeAllTools() {
            McpRequest request = createToolsListRequest();

            List<Map<String, Object>> allDefs = List.of(
                    Map.of("name", "dataset.get_metadata"),
                    Map.of("name", "dataset.query_model"),
                    Map.of("name", "dataset_nl.query")
            );
            List<McpTool> allTools = List.of(
                    MockToolFactory.createMetadataTool(),
                    MockToolFactory.createQueryModelTool(),
                    MockToolFactory.createNLQueryTool()
            );

            when(toolDispatcher.getToolDefinitions()).thenReturn(allDefs);
            when(toolDispatcher.getAllTools()).thenReturn(allTools);
            when(toolFilterService.filterToolDefinitionsByRole(allDefs, allTools, UserRole.ADMIN))
                    .thenReturn(allDefs);

            McpResponse response = mcpService.handleToolsList(request, UserRole.ADMIN);

            List<Map<String, Object>> tools = extractToolsList(response);
            assertEquals(3, tools.size());
        }

        @Test
        @DisplayName("Business 只应看到 NL 工具")
        void business_shouldOnlySeeNLTools() {
            McpRequest request = createToolsListRequest();

            List<Map<String, Object>> allDefs = List.of(
                    Map.of("name", "dataset.get_metadata"),
                    Map.of("name", "dataset_nl.query")
            );
            List<McpTool> allTools = MockToolFactory.createAllStandardTools();
            List<Map<String, Object>> nlOnlyDefs = List.of(
                    Map.of("name", "dataset_nl.query")
            );

            when(toolDispatcher.getToolDefinitions()).thenReturn(allDefs);
            when(toolDispatcher.getAllTools()).thenReturn(allTools);
            when(toolFilterService.filterToolDefinitionsByRole(allDefs, allTools, UserRole.BUSINESS))
                    .thenReturn(nlOnlyDefs);

            McpResponse response = mcpService.handleToolsList(request, UserRole.BUSINESS);

            List<Map<String, Object>> tools = extractToolsList(response);
            assertEquals(1, tools.size());
            assertEquals("dataset_nl.query", tools.get(0).get("name"));
        }

        @Test
        @DisplayName("namespace 策略应继续过滤角色可见工具")
        void namespacePolicy_shouldFilterRoleVisibleTools() {
            McpRequest request = createToolsListRequest();
            List<Map<String, Object>> definitions = List.of(
                    Map.of("name", "dataset.query_model"),
                    Map.of("name", "dataset.explain_query"));
            McpTool queryTool = MockToolFactory.createQueryModelTool();
            McpTool explainTool = mock(McpTool.class);
            when(explainTool.getName()).thenReturn("dataset.explain_query");
            when(toolDispatcher.getToolDefinitions()).thenReturn(definitions);
            when(toolDispatcher.getAllTools()).thenReturn(List.of(queryTool, explainTool));
            when(toolFilterService.filterToolDefinitionsByRole(
                    definitions, List.of(queryTool, explainTool), UserRole.ANALYST)).thenReturn(definitions);
            when(namespaceToolPolicyService.resolveAvailableTools(
                    anyCollection(), eq("wwi"), eq("Bearer token"), eq("ANALYST"), eq("trace-policy"), any()))
                    .thenReturn(new LinkedHashSet<>(List.of("dataset.explain_query")));

            McpResponse response = mcpService.handleToolsList(request, McpRequestContext.of(
                    "trace-policy", "request-policy", "Bearer token", UserRole.ANALYST, "wwi"));

            List<Map<String, Object>> tools = extractToolsList(response);
            assertEquals(1, tools.size());
            assertEquals("dataset.explain_query", tools.get(0).get("name"));
        }
    }

    // ==================== handleToolsCall 测试 ====================

    @Nested
    @DisplayName("handleToolsCall - 工具调用请求处理")
    class HandleToolsCallTest {

        @Test
        @DisplayName("缺少工具名称应返回 INVALID_PARAMS 错误")
        void missingToolName_shouldReturnInvalidParamsError() {
            McpRequest request = McpRequest.builder()
                    .id("1")
                    .method("tools/call")
                    .params(Map.of()) // 没有 name
                    .build();

            McpResponse response = mcpService.handleToolsCall(request, UserRole.ADMIN, "trace-1", "req-1", null, null);

            assertNotNull(response.getError());
            assertEquals(McpError.INVALID_PARAMS, response.getError().getCode());
            assertTrue(response.getError().getMessage().contains("Missing tool name"));
        }

        @Test
        @DisplayName("params 为 null 应返回 INVALID_PARAMS 错误")
        void nullParams_shouldReturnInvalidParamsError() {
            McpRequest request = McpRequest.builder()
                    .id("1")
                    .method("tools/call")
                    .params(null)
                    .build();

            McpResponse response = mcpService.handleToolsCall(request, UserRole.ADMIN, "trace-1", "req-1", null, null);

            assertNotNull(response.getError());
            assertEquals(McpError.INVALID_PARAMS, response.getError().getCode());
        }

        @Test
        @DisplayName("工具不存在应返回 METHOD_NOT_FOUND 错误")
        void unknownTool_shouldReturnMethodNotFoundError() {
            McpRequest request = createToolsCallRequest("unknown.tool", Map.of());

            when(toolDispatcher.hasTool("unknown.tool")).thenReturn(false);

            McpResponse response = mcpService.handleToolsCall(request, UserRole.ADMIN, "trace-1", "req-1", null, null);

            assertNotNull(response.getError());
            assertEquals(McpError.METHOD_NOT_FOUND, response.getError().getCode());
            assertTrue(response.getError().getMessage().contains("unknown.tool"));
        }

        @Test
        @DisplayName("权限不足应返回 METHOD_NOT_FOUND 错误")
        void accessDenied_shouldReturnMethodNotFoundError() {
            McpRequest request = createToolsCallRequest("dataset.query_model", Map.of());

            McpTool mockTool = MockToolFactory.createQueryModelTool();
            when(toolDispatcher.hasTool("dataset.query_model")).thenReturn(true);
            when(toolDispatcher.getTool("dataset.query_model")).thenReturn(mockTool);
            when(toolFilterService.canAccessTool(mockTool, UserRole.BUSINESS)).thenReturn(false);

            McpResponse response = mcpService.handleToolsCall(request, UserRole.BUSINESS, "trace-1", "req-1", null, null);

            assertNotNull(response.getError());
            assertEquals(McpError.METHOD_NOT_FOUND, response.getError().getCode());
            assertTrue(response.getError().getMessage().contains("access denied"));
        }

        @Test
        @DisplayName("namespace 策略拒绝时直接调用也应返回 METHOD_NOT_FOUND")
        void namespacePolicyDenied_shouldReturnMethodNotFound() {
            McpRequest request = createToolsCallRequest("dataset.explain_query", Map.of("model", "sales"));
            McpTool explainTool = mock(McpTool.class);
            when(explainTool.getName()).thenReturn("dataset.explain_query");
            when(toolDispatcher.hasTool("dataset.explain_query")).thenReturn(true);
            when(toolDispatcher.getTool("dataset.explain_query")).thenReturn(explainTool);
            when(toolDispatcher.getAllTools()).thenReturn(List.of(explainTool));
            when(namespaceToolPolicyService.isAvailable(
                    eq("dataset.explain_query"), anyCollection(), eq("wwi"), eq("Bearer token"),
                    eq("ADMIN"), eq("trace-policy"), any())).thenReturn(false);

            McpResponse response = mcpService.handleToolsCall(request, McpRequestContext.of(
                    "trace-policy", "request-policy", "Bearer token", UserRole.ADMIN, "wwi"));

            assertNotNull(response.getError());
            assertEquals(McpError.METHOD_NOT_FOUND, response.getError().getCode());
            verify(toolDispatcher, never()).executeTool(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Admin 访问任意工具应成功")
        void admin_shouldAccessAnyTool() {
            McpRequest request = createToolsCallRequest("dataset.get_metadata", Map.of());

            McpTool mockTool = MockToolFactory.createMetadataTool();
            Map<String, Object> expectedResult = Map.of("models", List.of());

            when(toolDispatcher.hasTool("dataset.get_metadata")).thenReturn(true);
            when(toolDispatcher.getTool("dataset.get_metadata")).thenReturn(mockTool);
            // Admin 直接跳过 canAccessTool 检查
            when(toolDispatcher.executeTool(eq("dataset.get_metadata"), any(), eq("trace-1"), any(), any(), any(), any(), any()))
                    .thenReturn(expectedResult);

            McpResponse response = mcpService.handleToolsCall(request, UserRole.ADMIN, "trace-1", "req-1", null, null);

            assertResponseSuccess(response);
            verify(toolDispatcher).executeTool(eq("dataset.get_metadata"), any(), eq("trace-1"), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("工具执行成功应返回 content 格式结果")
        void successfulExecution_shouldReturnContentFormat() {
            McpRequest request = createToolsCallRequest("dataset.get_metadata", Map.of());

            McpTool mockTool = MockToolFactory.createMetadataTool();
            Map<String, Object> toolResult = Map.of("status", "success", "data", "test");

            when(toolDispatcher.hasTool("dataset.get_metadata")).thenReturn(true);
            when(toolDispatcher.getTool("dataset.get_metadata")).thenReturn(mockTool);
            when(toolDispatcher.executeTool(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(toolResult);

            McpResponse response = mcpService.handleToolsCall(request, UserRole.ADMIN, "trace-1", "req-1", null, null);

            assertResponseSuccess(response);
            Map<String, Object> result = extractResultMap(response);
            assertFalse(result.containsKey("status"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            assertNotNull(content);
            assertFalse(content.isEmpty());
            assertEquals("text", content.get(0).get("type"));
        }

        @Test
        @DisplayName("query_model 成功时应返回 success 状态并保留 JSON 文本")
        void queryModelSuccess_shouldReturnSuccessStatus() {
            McpRequest request = createToolsCallRequest("dataset.query_model", Map.of(
                    "model", "SalesModel",
                    "payload", Map.of("columns", List.of("amount"))
            ));

            McpTool mockTool = MockToolFactory.createQueryModelTool();
            SemanticQueryResponse queryResponse = new SemanticQueryResponse();
            queryResponse.setItems(List.of(Map.of("amount", 100)));
            queryResponse.setTotal(1L);

            when(toolDispatcher.hasTool("dataset.query_model")).thenReturn(true);
            when(toolDispatcher.getTool("dataset.query_model")).thenReturn(mockTool);
            when(toolDispatcher.executeTool(eq("dataset.query_model"), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(RX.success(queryResponse));

            McpResponse response = mcpService.handleToolsCall(request, UserRole.ADMIN, "trace-1", "req-1", null, null);

            assertResponseSuccess(response);
            Map<String, Object> result = extractResultMap(response);
            assertEquals("success", result.get("status"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            String text = (String) content.get(0).get("text");
            assertNotNull(text);
            assertTrue(text.contains("\"code\":200"));
            assertTrue(text.contains("\"total\":1"));
        }

        @Test
        @DisplayName("query_model 业务失败时应返回 failed 状态和错误文案")
        void queryModelFailure_shouldReturnFailedStatus() {
            McpRequest request = createToolsCallRequest("dataset.query_model", Map.of(
                    "model", "SalesModel",
                    "payload", Map.of("columns", List.of("amount"))
            ));

            McpTool mockTool = MockToolFactory.createQueryModelTool();
            when(toolDispatcher.hasTool("dataset.query_model")).thenReturn(true);
            when(toolDispatcher.getTool("dataset.query_model")).thenReturn(mockTool);
            when(toolDispatcher.executeTool(eq("dataset.query_model"), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(RX.failB("查询被拒绝：column \"totalamount\" does not exist"));

            McpResponse response = mcpService.handleToolsCall(request, UserRole.ADMIN, "trace-1", "req-1", null, null);

            assertResponseSuccess(response);
            Map<String, Object> result = extractResultMap(response);
            assertEquals("failed", result.get("status"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            assertEquals("查询被拒绝：column \"totalamount\" does not exist", content.get(0).get("text"));
        }

        @Test
        @DisplayName("工具执行异常应返回 TOOL_EXECUTION_ERROR")
        void executionError_shouldReturnToolExecutionError() {
            McpRequest request = createToolsCallRequest("dataset.get_metadata", Map.of());

            McpTool mockTool = MockToolFactory.createMetadataTool();
            when(toolDispatcher.hasTool("dataset.get_metadata")).thenReturn(true);
            when(toolDispatcher.getTool("dataset.get_metadata")).thenReturn(mockTool);
            when(toolDispatcher.executeTool(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Service unavailable"));

            McpResponse response = mcpService.handleToolsCall(request, UserRole.ADMIN, "trace-1", "req-1", null, null);

            assertNotNull(response.getError());
            assertEquals(McpError.TOOL_EXECUTION_ERROR, response.getError().getCode());
            assertTrue(response.getError().getMessage().contains("Service unavailable"));
        }

        @Test
        @DisplayName("应正确传递 arguments")
        void shouldPassArgumentsCorrectly() {
            Map<String, Object> arguments = Map.of("model", "TestModel", "limit", 10);
            McpRequest request = createToolsCallRequest("dataset.query_model", arguments);

            McpTool mockTool = MockToolFactory.createQueryModelTool();
            when(toolDispatcher.hasTool("dataset.query_model")).thenReturn(true);
            when(toolDispatcher.getTool("dataset.query_model")).thenReturn(mockTool);
            when(toolDispatcher.executeTool(eq("dataset.query_model"), eq(arguments), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Map.of("success", true));

            mcpService.handleToolsCall(request, UserRole.ADMIN, "trace-1", "req-1", null, null);

            verify(toolDispatcher).executeTool(eq("dataset.query_model"), eq(arguments), any(), any(), any(), any(), any(), any());
        }
    }

    // ==================== handleDirectToolCall 测试 ====================

    @Nested
    @DisplayName("handleDirectToolCall - 直接工具调用处理")
    class HandleDirectToolCallTest {

        @Test
        @DisplayName("方法名即工具名应正确执行")
        void methodAsToolName_shouldExecuteCorrectly() {
            McpRequest request = McpRequest.builder()
                    .id("1")
                    .method("dataset.get_metadata")
                    .params(Map.of())
                    .build();

            McpTool mockTool = MockToolFactory.createMetadataTool();
            when(toolDispatcher.hasTool("dataset.get_metadata")).thenReturn(true);
            when(toolDispatcher.getTool("dataset.get_metadata")).thenReturn(mockTool);
            when(toolDispatcher.executeTool(eq("dataset.get_metadata"), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Map.of("result", "ok"));

            McpResponse response = mcpService.handleDirectToolCall(request, UserRole.ADMIN, "trace-1", null);

            assertResponseSuccess(response);
        }

        @Test
        @DisplayName("权限不足应返回错误")
        void accessDenied_shouldReturnError() {
            McpRequest request = McpRequest.builder()
                    .id("1")
                    .method("dataset.query_model")
                    .params(Map.of())
                    .build();

            McpTool mockTool = MockToolFactory.createQueryModelTool();
            when(toolDispatcher.hasTool("dataset.query_model")).thenReturn(true);
            when(toolDispatcher.getTool("dataset.query_model")).thenReturn(mockTool);
            when(toolFilterService.canAccessTool(mockTool, UserRole.BUSINESS)).thenReturn(false);

            McpResponse response = mcpService.handleDirectToolCall(request, UserRole.BUSINESS, "trace-1", null);

            assertNotNull(response.getError());
            assertEquals(McpError.METHOD_NOT_FOUND, response.getError().getCode());
        }
    }

    // ==================== handlePing 测试 ====================

    @Nested
    @DisplayName("handlePing - Ping 请求处理")
    class HandlePingTest {

        @Test
        @DisplayName("应返回 pong")
        void shouldReturnPong() {
            McpRequest request = createPingRequest();

            McpResponse response = mcpService.handlePing(request);

            assertResponseSuccess(response);
            Map<String, Object> result = extractResultMap(response);
            assertEquals("pong", result.get("status"));
        }
    }
}
