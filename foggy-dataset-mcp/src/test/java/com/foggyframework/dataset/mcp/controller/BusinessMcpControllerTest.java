package com.foggyframework.dataset.mcp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.enums.UserRole;
import com.foggyframework.dataset.mcp.schema.McpError;
import com.foggyframework.dataset.mcp.schema.McpRequest;
import com.foggyframework.dataset.mcp.schema.McpResponse;
import com.foggyframework.dataset.mcp.service.McpService;
import com.foggyframework.dataset.mcp.service.McpToolDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BusinessMcpController 集成测试
 *
 * 验证 Business 角色只能访问 NL 工具
 */
@WebMvcTest(BusinessMcpController.class)
@ActiveProfiles("test")
@DisplayName("BusinessMcpController 集成测试")
class BusinessMcpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private McpService mcpService;

    @MockBean
    private McpToolDispatcher toolDispatcher;

    // ==================== tools/list 测试 ====================

    @Nested
    @DisplayName("POST /mcp/business/rpc - tools/list")
    class ToolsListTest {

        @Test
        @DisplayName("Business 角色只应看到 NL 工具")
        void business_shouldOnlySeeNLTools() throws Exception {
            // Business 角色只能看到 NL 工具
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "tools", List.of(
                            Map.of("name", "dataset_nl.query", "description", "智能自然语言查询")
                    )
            ));

            when(mcpService.handleToolsList(any(McpRequest.class), eq(UserRole.BUSINESS)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/business/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.tools").isArray())
                    .andExpect(jsonPath("$.result.tools", hasSize(1)))
                    .andExpect(jsonPath("$.result.tools[0].name").value("dataset_nl.query"));

            verify(mcpService).handleToolsList(any(McpRequest.class), eq(UserRole.BUSINESS));
        }
    }

    // ==================== tools/call 测试 ====================

    @Nested
    @DisplayName("POST /mcp/business/rpc - tools/call")
    class ToolsCallTest {

        @Test
        @DisplayName("Business 调用 NL 工具应成功")
        void business_shouldAccessNLTool() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "{\"result\":\"查询结果\"}"
                    ))
            ));

            when(mcpService.handleToolsCall(any(McpRequest.class), eq(UserRole.BUSINESS), any(), any(), any()))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/business/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "jsonrpc":"2.0",
                                      "id":"1",
                                      "method":"tools/call",
                                      "params":{
                                        "name":"dataset_nl.query",
                                        "arguments":{"query":"最近一周的销售数据"}
                                      }
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.content").isArray())
                    .andExpect(jsonPath("$.error").doesNotExist());

            verify(mcpService).handleToolsCall(any(McpRequest.class), eq(UserRole.BUSINESS), any(), any(), any());
        }

        @Test
        @DisplayName("Business 调用 Query 工具应被拒绝")
        void business_shouldNotAccessQueryTool() throws Exception {
            // 模拟权限被拒绝
            McpResponse mockResponse = McpResponse.error("1", McpError.METHOD_NOT_FOUND,
                    "Tool not found or access denied: dataset.query_model_v2");

            when(mcpService.handleToolsCall(any(McpRequest.class), eq(UserRole.BUSINESS), any(), any(), any()))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/business/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "jsonrpc":"2.0",
                                      "id":"1",
                                      "method":"tools/call",
                                      "params":{
                                        "name":"dataset.query_model_v2",
                                        "arguments":{}
                                      }
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.error.code").value(McpError.METHOD_NOT_FOUND))
                    .andExpect(jsonPath("$.error.message").value(containsString("access denied")));
        }

        @Test
        @DisplayName("Business 调用 Metadata 工具应被拒绝")
        void business_shouldNotAccessMetadataTool() throws Exception {
            McpResponse mockResponse = McpResponse.error("1", McpError.METHOD_NOT_FOUND,
                    "Tool not found or access denied: dataset.get_metadata");

            when(mcpService.handleToolsCall(any(McpRequest.class), eq(UserRole.BUSINESS), any(), any(), any()))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/business/rpc")
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
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.error.code").value(McpError.METHOD_NOT_FOUND));
        }

        @Test
        @DisplayName("Business 调用 Chart 工具应被拒绝")
        void business_shouldNotAccessChartTool() throws Exception {
            McpResponse mockResponse = McpResponse.error("1", McpError.METHOD_NOT_FOUND,
                    "Tool not found or access denied: chart.generate");

            when(mcpService.handleToolsCall(any(McpRequest.class), eq(UserRole.BUSINESS), any(), any(), any()))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/business/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "jsonrpc":"2.0",
                                      "id":"1",
                                      "method":"tools/call",
                                      "params":{
                                        "name":"chart.generate",
                                        "arguments":{}
                                      }
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.error.code").value(McpError.METHOD_NOT_FOUND));
        }
    }

    // ==================== initialize 测试 ====================

    @Nested
    @DisplayName("POST /mcp/business/rpc - initialize")
    class InitializeTest {

        @Test
        @DisplayName("初始化应返回 BUSINESS 角色信息")
        void shouldReturnBusinessRoleInfo() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "protocolVersion", "2024-11-05",
                    "serverInfo", Map.of(
                            "name", "mcp-data-model-java",
                            "userRole", "BUSINESS",
                            "roleDescription", "适合使用自然语言进行数据查询的普通用户"
                    )
            ));

            when(mcpService.handleInitialize(any(McpRequest.class), eq(UserRole.BUSINESS)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/business/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jsonrpc":"2.0","id":"1","method":"initialize","params":{}}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.serverInfo.userRole").value("BUSINESS"));
        }
    }
}
