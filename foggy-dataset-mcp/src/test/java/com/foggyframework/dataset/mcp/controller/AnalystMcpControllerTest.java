package com.foggyframework.dataset.mcp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.enums.UserRole;
import com.foggyframework.dataset.mcp.schema.McpError;
import com.foggyframework.dataset.mcp.schema.McpRequest;
import com.foggyframework.dataset.mcp.schema.McpRequestContext;
import com.foggyframework.dataset.mcp.schema.McpResponse;
import com.foggyframework.dataset.mcp.service.McpService;
import com.foggyframework.dataset.mcp.service.McpToolDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
                            Map.of("name", "dataset.query_model", "description", "查询模型"),
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

        @Test
        @DisplayName("tools/list 应包含 dataset.list_models")
        void toolsList_shouldContainListModels() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "tools", List.of(
                            Map.of("name", "dataset.list_models", "description", "发现所有可用模型"),
                            Map.of("name", "dataset.get_metadata", "description", "获取元数据"),
                            Map.of("name", "dataset.query_model", "description", "查询模型")
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
                    .andExpect(jsonPath("$.result.tools[*].name",
                            hasItem("dataset.list_models")));
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

            when(mcpService.handleToolsCall(any(McpRequest.class), any(McpRequestContext.class)))
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
        @DisplayName("Analyst 调用 list_models 工具应成功")
        void analyst_shouldAccessListModelsTool() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "{\"code\":200,\"data\":{\"format\":\"markdown\",\"content\":\"# 数据模型列表\"}}"
                    ))
            ));

            when(mcpService.handleToolsCall(any(McpRequest.class), any(McpRequestContext.class)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/analyst/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "jsonrpc":"2.0",
                                      "id":"1",
                                      "method":"tools/call",
                                      "params":{
                                        "name":"dataset.list_models",
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

            when(mcpService.handleToolsCall(any(McpRequest.class), any(McpRequestContext.class)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/analyst/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "jsonrpc":"2.0",
                                      "id":"1",
                                      "method":"tools/call",
                                      "params":{
                                        "name":"dataset.query_model",
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
        @DisplayName("remote compose 应透传 request-scoped authority headers")
        void remoteCompose_shouldForwardAuthorityHeaders() throws Exception {
            McpResponse mockResponse = McpResponse.success("1", Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "{\"status\":\"success\"}"
                    ))
            ));
            ArgumentCaptor<McpRequestContext> contextCaptor = ArgumentCaptor.forClass(McpRequestContext.class);

            when(mcpService.handleToolsCall(any(McpRequest.class), any(McpRequestContext.class)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/analyst/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer token")
                            .header("X-Trace-Id", "trace-1")
                            .header("X-NS", "odoo")
                            .header("X-Foggy-Remote-Compose", "1")
                            .header("X-User-Id", "7")
                            .header("X-Namespace", "odoo")
                            .header("X-Roles", "Sales")
                            .header("X-Dept-Id", "3")
                            .content("""
                                    {
                                      "jsonrpc":"2.0",
                                      "id":"1",
                                      "method":"tools/call",
                                      "params":{
                                        "name":"dataset.compose_script",
                                        "arguments":{"script":"return 1;"}
                                      }
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.content").isArray());

            verify(mcpService).handleToolsCall(any(McpRequest.class), contextCaptor.capture());
            McpRequestContext context = contextCaptor.getValue();
            assertEquals("1", context.getHeaders().get("X-Foggy-Remote-Compose"));
            assertEquals("7", context.getHeaders().get("X-User-Id"));
            assertEquals("odoo", context.getHeaders().get("X-Namespace"));
            assertEquals("odoo", context.getHeaders().get("X-NS"));
            assertEquals("Sales", context.getHeaders().get("X-Roles"));
            assertEquals("3", context.getHeaders().get("X-Dept-Id"));
            assertEquals("trace-1", context.getHeaders().get("X-Trace-Id"));
            assertEquals("Bearer token", context.getHeaders().get("Authorization"));
        }

        @Test
        @DisplayName("Analyst 调用 Query 工具包含非法的 pivot 参数应返回 JSON-RPC 错误")
        void analyst_shouldReturnJsonRpcErrorOnInvalidPivot() throws Exception {
            McpResponse mockResponse = McpResponse.error("1", McpError.INVALID_PARAMS,
                    "Invalid pivot request: tree hierarchy mode is not compatible with subtotals");

            when(mcpService.handleToolsCall(any(McpRequest.class), any(McpRequestContext.class)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/mcp/analyst/rpc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "jsonrpc":"2.0",
                                      "id":"1",
                                      "method":"tools/call",
                                      "params":{
                                        "name":"dataset.query_model",
                                        "arguments":{
                                          "model":"FactSalesModel",
                                          "payload":{
                                            "columns":["product$caption"],
                                            "pivot": {
                                               "rows": [{"field": "region", "hierarchyMode": "tree"}],
                                               "options": {"withSubtotals": true}
                                            }
                                          }
                                        }
                                      }
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.error.code").value(McpError.INVALID_PARAMS))
                    .andExpect(jsonPath("$.error.message").value(containsString("tree hierarchy mode")));
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

            when(mcpService.handleToolsCall(any(McpRequest.class), any(McpRequestContext.class)))
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

            when(mcpService.handleToolsCall(any(McpRequest.class), any(McpRequestContext.class)))
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

            when(mcpService.handleToolsCall(any(McpRequest.class), any(McpRequestContext.class)))
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
