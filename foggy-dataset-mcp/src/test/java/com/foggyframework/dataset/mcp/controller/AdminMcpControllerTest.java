package com.foggyframework.dataset.mcp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.enums.UserRole;
import com.foggyframework.dataset.mcp.schema.McpError;
import com.foggyframework.dataset.mcp.schema.McpRequest;
import com.foggyframework.dataset.mcp.schema.McpResponse;
import com.foggyframework.dataset.mcp.service.McpService;
import com.foggyframework.dataset.mcp.service.McpToolDispatcher;
import com.foggyframework.dataset.mcp.service.ProgressEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminMcpController 集成测试
 */
@WebMvcTest(AdminMcpController.class)
@ActiveProfiles("test")
@DisplayName("AdminMcpController 集成测试")
class AdminMcpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private McpService mcpService;

    @MockBean
    private McpToolDispatcher toolDispatcher;

    // ==================== initialize 测试 ====================

    @Nested
    @DisplayName("POST /mcp/admin/rpc - initialize")
    class InitializeTest {

        @Test
        @DisplayName("应成功初始化并返回服务器信息")
        void shouldInitializeSuccessfully() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "protocolVersion", "2024-11-05",
                    "serverInfo", Map.of(
                            "name", "mcp-data-model-java",
                            "version", "1.0.0",
                            "userRole", "ADMIN"
                    ),
                    "capabilities", Map.of(
                            "tools", Map.of("listChanged", true),
                            "logging", Map.of()
                    )
            ));

            when(mcpService.handleInitialize(any(McpRequest.class), eq(UserRole.ADMIN)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/admin/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jsonrpc":"2.0","id":"1","method":"initialize","params":{}}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                    .andExpect(jsonPath("$.id").value("1"))
                    .andExpect(jsonPath("$.result.protocolVersion").value("2024-11-05"))
                    .andExpect(jsonPath("$.result.serverInfo.userRole").value("ADMIN"))
                    .andExpect(jsonPath("$.error").doesNotExist());

            verify(mcpService).handleInitialize(any(McpRequest.class), eq(UserRole.ADMIN));
        }
    }

    // ==================== tools/list 测试 ====================

    @Nested
    @DisplayName("POST /mcp/admin/rpc - tools/list")
    class ToolsListTest {

        @Test
        @DisplayName("Admin 应能看到所有工具")
        void admin_shouldSeeAllTools() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "tools", List.of(
                            Map.of("name", "dataset.get_metadata", "description", "Get metadata"),
                            Map.of("name", "dataset.query_model_v2", "description", "Query model"),
                            Map.of("name", "dataset_nl.query", "description", "NL Query")
                    )
            ));

            when(mcpService.handleToolsList(any(McpRequest.class), eq(UserRole.ADMIN)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/admin/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.tools").isArray())
                    .andExpect(jsonPath("$.result.tools", hasSize(3)))
                    .andExpect(jsonPath("$.result.tools[*].name",
                            containsInAnyOrder("dataset.get_metadata", "dataset.query_model_v2", "dataset_nl.query")));
        }
    }

    // ==================== tools/call 测试 ====================

    @Nested
    @DisplayName("POST /mcp/admin/rpc - tools/call")
    class ToolsCallTest {

        @Test
        @DisplayName("应成功执行工具调用")
        void shouldExecuteToolSuccessfully() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "{\"models\":[{\"name\":\"FactSalesModel\"}]}"
                    ))
            ));

            when(mcpService.handleToolsCall(any(McpRequest.class), eq(UserRole.ADMIN), any(), any(), any()))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/admin/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "jsonrpc":"2.0",
                                      "id":"1",
                                      "method":"tools/call",
                                      "params":{
                                        "name":"dataset.get_metadata",
                                        "arguments":{}
                                      }
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.content").isArray())
                    .andExpect(jsonPath("$.result.content[0].type").value("text"))
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("工具不存在应返回错误")
        void unknownTool_shouldReturnError() throws Exception {
            McpResponse mockResponse = McpResponse.error("1", McpError.METHOD_NOT_FOUND,
                    "Tool not found or access denied: unknown.tool");

            when(mcpService.handleToolsCall(any(McpRequest.class), eq(UserRole.ADMIN), any(), any(), any()))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/admin/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "jsonrpc":"2.0",
                                      "id":"1",
                                      "method":"tools/call",
                                      "params":{
                                        "name":"unknown.tool"
                                      }
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.error.code").value(McpError.METHOD_NOT_FOUND))
                    .andExpect(jsonPath("$.error.message").value(containsString("unknown.tool")));
        }
    }

    // ==================== ping 测试 ====================

    @Nested
    @DisplayName("POST /mcp/admin/rpc - ping")
    class PingTest {

        @Test
        @DisplayName("应返回 pong")
        void shouldReturnPong() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of("status", "pong"));

            when(mcpService.handlePing(any(McpRequest.class))).thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/admin/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jsonrpc":"2.0","id":"1","method":"ping","params":{}}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("pong"));
        }
    }

    // ==================== 无效方法测试 ====================

    @Nested
    @DisplayName("POST /mcp/admin/rpc - 无效方法")
    class InvalidMethodTest {

        @Test
        @DisplayName("无效方法应返回 METHOD_NOT_FOUND 错误")
        void invalidMethod_shouldReturnError() throws Exception {
            mockMvc.perform(post("/mcp/admin/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jsonrpc":"2.0","id":"1","method":"invalid/method","params":{}}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.error.code").value(McpError.METHOD_NOT_FOUND))
                    .andExpect(jsonPath("$.error.message").value(containsString("Method not found")));
        }

        @Test
        @DisplayName("缺少 method 字段应返回 INVALID_REQUEST 错误")
        void missingMethod_shouldReturnError() throws Exception {
            mockMvc.perform(post("/mcp/admin/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jsonrpc":"2.0","id":"1","params":{}}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.error.code").value(McpError.INVALID_REQUEST));
        }
    }

    // ==================== 直接工具调用测试 ====================

    @Nested
    @DisplayName("POST /mcp/admin/rpc - 直接工具调用")
    class DirectToolCallTest {

        @Test
        @DisplayName("方法名以 dataset 开头应作为工具调用处理")
        void datasetMethodPrefix_shouldBeHandledAsToolCall() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of("data", "result"));

            when(mcpService.handleDirectToolCall(any(McpRequest.class), eq(UserRole.ADMIN), any(), any(), any()))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/admin/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jsonrpc":"2.0","id":"1","method":"dataset.get_metadata","params":{}}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.data").value("result"));

            verify(mcpService).handleDirectToolCall(any(McpRequest.class), eq(UserRole.ADMIN), any(), any(), any());
        }
    }

    // ==================== 请求头测试 ====================

    @Nested
    @DisplayName("请求头处理")
    class RequestHeaderTest {

        @Test
        @DisplayName("应支持自定义 X-Request-Id")
        void shouldSupportCustomRequestId() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of("status", "pong"));
            when(mcpService.handlePing(any(McpRequest.class))).thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/admin/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", "custom-trace-id")
                            .content("""
                                    {"jsonrpc":"2.0","id":"1","method":"ping","params":{}}
                                    """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Authorization 头应是可选的")
        void authorization_shouldBeOptional() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of("status", "pong"));
            when(mcpService.handlePing(any(McpRequest.class))).thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/admin/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jsonrpc":"2.0","id":"1","method":"ping","params":{}}
                                    """))
                    .andExpect(status().isOk());
        }
    }

    // ==================== 异常处理测试 ====================

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("服务异常应返回 INTERNAL_ERROR")
        void serviceException_shouldReturnInternalError() throws Exception {
            when(mcpService.handlePing(any(McpRequest.class)))
                    .thenThrow(new RuntimeException("Unexpected error"));

            mockMvc.perform(post("/mcp/admin/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jsonrpc":"2.0","id":"1","method":"ping","params":{}}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.error.code").value(McpError.INTERNAL_ERROR))
                    .andExpect(jsonPath("$.error.message").value("Unexpected error"));
        }
    }
}
