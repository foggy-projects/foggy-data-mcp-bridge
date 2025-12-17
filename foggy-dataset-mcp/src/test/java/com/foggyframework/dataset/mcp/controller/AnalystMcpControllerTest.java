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
 * AnalystMcpController 集成测试
 *
 * 验证 Analyst 角色可以访问专业工具但不能访问 NL 工具
 */
@WebMvcTest(AnalystMcpController.class)
@ActiveProfiles("test")
@DisplayName("AnalystMcpController 集成测试")
class AnalystMcpControllerTest {

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
    @DisplayName("POST /mcp/analyst/rpc - tools/list")
    class ToolsListTest {

        @Test
        @DisplayName("Analyst 应看到专业工具但不包含 NL 工具")
        void analyst_shouldSeeProToolsExcludingNL() throws Exception {
            // Analyst 角色可以看到 metadata, query, visualization, export 工具，但不包含 NL
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "tools", List.of(
                            Map.of("name", "dataset.get_metadata", "description", "获取元数据"),
                            Map.of("name", "dataset.description_model_internal", "description", "模型描述"),
                            Map.of("name", "dataset.query_model_v2", "description", "查询模型"),
                            Map.of("name", "chart.generate", "description", "生成图表"),
                            Map.of("name", "dataset.export_with_chart", "description", "导出图表")
                    )
            ));

            when(mcpService.handleToolsList(any(McpRequest.class), eq(UserRole.ANALYST)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/analyst/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.tools").isArray())
                    .andExpect(jsonPath("$.result.tools", hasSize(5)))
                    .andExpect(jsonPath("$.result.tools[*].name",
                            not(hasItem("dataset_nl.query"))));

            verify(mcpService).handleToolsList(any(McpRequest.class), eq(UserRole.ANALYST));
        }
    }

    // ==================== tools/call 测试 ====================

    @Nested
    @DisplayName("POST /mcp/analyst/rpc - tools/call")
    class ToolsCallTest {

        @Test
        @DisplayName("Analyst 调用 Metadata 工具应成功")
        void analyst_shouldAccessMetadataTool() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "{\"models\":[{\"name\":\"FactSalesModel\"}]}"
                    ))
            ));

            when(mcpService.handleToolsCall(any(McpRequest.class), eq(UserRole.ANALYST), any(), any(), any()))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/analyst/rpc")
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
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("Analyst 调用 Query 工具应成功")
        void analyst_shouldAccessQueryTool() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "{\"items\":[],\"total\":0}"
                    ))
            ));

            when(mcpService.handleToolsCall(any(McpRequest.class), eq(UserRole.ANALYST), any(), any(), any()))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/analyst/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "jsonrpc":"2.0",
                                      "id":"1",
                                      "method":"tools/call",
                                      "params":{
                                        "name":"dataset.query_model_v2",
                                        "arguments":{
                                          "model":"FactSalesModel",
                                          "payload":{"columns":["product$caption"]}
                                        }
                                      }
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.content").isArray())
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("Analyst 调用 Chart 工具应成功")
        void analyst_shouldAccessChartTool() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "{\"chartUrl\":\"/charts/abc123.png\"}"
                    ))
            ));

            when(mcpService.handleToolsCall(any(McpRequest.class), eq(UserRole.ANALYST), any(), any(), any()))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/analyst/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "jsonrpc":"2.0",
                                      "id":"1",
                                      "method":"tools/call",
                                      "params":{
                                        "name":"chart.generate",
                                        "arguments":{
                                          "type":"bar",
                                          "data":[{"x":"A","y":10}]
                                        }
                                      }
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.content").isArray())
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("Analyst 调用 NL 工具应被拒绝")
        void analyst_shouldNotAccessNLTool() throws Exception {
            McpResponse mockResponse = McpResponse.error("1", McpError.METHOD_NOT_FOUND,
                    "Tool not found or access denied: dataset_nl.query");

            when(mcpService.handleToolsCall(any(McpRequest.class), eq(UserRole.ANALYST), any(), any(), any()))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/analyst/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "jsonrpc":"2.0",
                                      "id":"1",
                                      "method":"tools/call",
                                      "params":{
                                        "name":"dataset_nl.query",
                                        "arguments":{"query":"最近销售数据"}
                                      }
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.error.code").value(McpError.METHOD_NOT_FOUND))
                    .andExpect(jsonPath("$.error.message").value(containsString("access denied")));
        }
    }

    // ==================== initialize 测试 ====================

    @Nested
    @DisplayName("POST /mcp/analyst/rpc - initialize")
    class InitializeTest {

        @Test
        @DisplayName("初始化应返回 ANALYST 角色信息")
        void shouldReturnAnalystRoleInfo() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "protocolVersion", "2024-11-05",
                    "serverInfo", Map.of(
                            "name", "mcp-data-model-java",
                            "userRole", "ANALYST",
                            "roleDescription", "专业数据处理人员，使用结构化查询和高级分析工具"
                    )
            ));

            when(mcpService.handleInitialize(any(McpRequest.class), eq(UserRole.ANALYST)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/analyst/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jsonrpc":"2.0","id":"1","method":"initialize","params":{}}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.serverInfo.userRole").value("ANALYST"));
        }
    }

    // ==================== Export 工具测试 ====================

    @Nested
    @DisplayName("POST /mcp/analyst/rpc - Export 工具")
    class ExportToolTest {

        @Test
        @DisplayName("Analyst 调用 export_with_chart 应成功")
        void analyst_shouldAccessExportWithChartTool() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "{\"queryResult\":{},\"chartUrl\":\"/charts/123.png\"}"
                    ))
            ));

            when(mcpService.handleToolsCall(any(McpRequest.class), eq(UserRole.ANALYST), any(), any(), any()))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/analyst/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "jsonrpc":"2.0",
                                      "id":"1",
                                      "method":"tools/call",
                                      "params":{
                                        "name":"dataset.export_with_chart",
                                        "arguments":{
                                          "model":"FactSalesModel",
                                          "payload":{},
                                          "chartType":"bar"
                                        }
                                      }
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.content").isArray())
                    .andExpect(jsonPath("$.error").doesNotExist());
        }
    }
}
