package com.foggyframework.dataset.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.core.ex.RX;
import com.foggyframework.mcp.spi.McpTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("McpToolCallbackFactory")
class McpToolCallbackFactoryTest {

    @AfterEach
    void tearDown() {
        QueryExpertService.clearCapture();
    }

    @Test
    @DisplayName("query_model 返回失败 RX 时应记录为工具失败并且不捕获结构化查询结果")
    @SuppressWarnings("unchecked")
    void queryModelFailedRx_shouldRecordToolFailure() throws Exception {
        ToolConfigLoader toolConfigLoader = mock(ToolConfigLoader.class);
        when(toolConfigLoader.getDescription("dataset.query_model")).thenReturn("query model");
        when(toolConfigLoader.getSchema("dataset.query_model")).thenReturn(Map.of("type", "object"));

        McpTool tool = mock(McpTool.class);
        when(tool.getName()).thenReturn("dataset.query_model");
        when(tool.execute(any(), any())).thenReturn(RX.failB("Table \"FACT_ORDER\" not found"));

        ToolCallCollector collector = new ToolCallCollector("session-1");
        McpToolCallbackFactory factory = new McpToolCallbackFactory(toolConfigLoader, new ObjectMapper());
        ToolCallback callback = factory.createToolCallback(tool, "trace-1", null, collector);

        String response = callback.call("""
                {"model":"FactOrderQueryModel","payload":{"columns":["orderId"]}}
                """);

        assertTrue(response.contains("FACT_ORDER"));
        Map<String, Object> payload = new ObjectMapper().readValue(response, Map.class);
        assertEquals(true, payload.get("error"));
        assertEquals("query_model_failed", payload.get("error_type"));
        assertEquals("repair_or_stop", ((Map<String, Object>) payload.get("retry_guidance")).get("action"));
        assertNull(QueryExpertService.LAST_QUERY_RESULT.get());

        List<ToolCallCollector.ToolCallRecord> calls = collector.getToolCalls();
        assertEquals(1, calls.size());
        ToolCallCollector.ToolCallRecord call = calls.get(0);
        assertEquals("dataset.query_model", call.getToolName());
        assertFalse(call.isSuccess());
        assertTrue(call.getError().contains("QUERY_MODEL_FAILED"));
        assertTrue(call.getError().contains("FACT_ORDER"));
    }

    @Test
    @DisplayName("query_model 缺失字段失败应给出使用现有模型字段的重试建议")
    @SuppressWarnings("unchecked")
    void queryModelMissingField_shouldReturnSchemaRetryGuidance() throws Exception {
        McpTool tool = mockQueryModelTool(RX.failB(
                "查询执行失败: Field 'product$categoryName' not found in model 'FactOrderQueryModel'."));

        String response = createCallback(tool).call("""
                {"model":"FactOrderQueryModel","payload":{"columns":["product$categoryName"]}}
                """);

        Map<String, Object> payload = new ObjectMapper().readValue(response, Map.class);
        Map<String, Object> guidance = (Map<String, Object>) payload.get("retry_guidance");
        assertEquals("use_existing_model_fields", guidance.get("action"));
        assertTrue(String.valueOf(guidance.get("instruction")).contains("describe_model_internal"));
    }

    @Test
    @DisplayName("query_model 聚合别名自由公式失败应建议改用受控 postAggregateCalculations")
    @SuppressWarnings("unchecked")
    void queryModelAggregateAliasFailure_shouldReturnPostAggregateRetryGuidance() throws Exception {
        McpTool tool = mockQueryModelTool(RX.failB(
                "查询执行失败: CALCULATED_FIELD_EXPRESSION_INVALID: 编译计算字段表达式失败 [share]: 未能在查询模型FactOrderQueryModel中找到列totalSales"));

        String response = createCallback(tool).call("""
                {"model":"FactOrderQueryModel","payload":{"columns":["sum(amount) as totalSales","share"]}}
                """);

        Map<String, Object> payload = new ObjectMapper().readValue(response, Map.class);
        Map<String, Object> guidance = (Map<String, Object>) payload.get("retry_guidance");
        assertEquals("avoid_free_form_aggregate_alias_expression", guidance.get("action"));
        assertTrue(String.valueOf(guidance.get("instruction")).contains("postAggregateCalculations"));
    }

    private ToolCallback createCallback(McpTool tool) {
        ToolConfigLoader toolConfigLoader = mock(ToolConfigLoader.class);
        when(toolConfigLoader.getDescription("dataset.query_model")).thenReturn("query model");
        when(toolConfigLoader.getSchema("dataset.query_model")).thenReturn(Map.of("type", "object"));

        ToolCallCollector collector = new ToolCallCollector("session-1");
        McpToolCallbackFactory factory = new McpToolCallbackFactory(toolConfigLoader, new ObjectMapper());
        return factory.createToolCallback(tool, "trace-1", null, collector);
    }

    private McpTool mockQueryModelTool(Object result) {
        McpTool tool = mock(McpTool.class);
        when(tool.getName()).thenReturn("dataset.query_model");
        when(tool.execute(any(), any())).thenReturn(result);
        return tool;
    }
}
