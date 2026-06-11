package com.foggyframework.dataset.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
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
    @DisplayName("query_model 成功 RX 应通过真实 callback 捕获 debug.extra 诊断")
    @SuppressWarnings("unchecked")
    void queryModelSuccessRx_shouldCaptureDebugExtraDiagnostics() throws Exception {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of(Map.of("orderId", "SO-1")));
        response.setTotal(1L);
        response.setHasNext(false);
        SemanticQueryResponse.DebugInfo debugInfo = new SemanticQueryResponse.DebugInfo();
        debugInfo.setExtra(Map.of(
                "aggregateRelationDiagnostics", List.of(Map.of(
                        "decision", "retained",
                        "reasonCode", "OR_CONDITION_OUTER_ONLY",
                        "field", "salesAmount",
                        "op", ">",
                        "target", "outer"
                ))
        ));
        response.setDebug(debugInfo);

        McpTool tool = mockQueryModelTool(RX.ok(response));

        String rawResponse = createCallback(tool).call("""
                {"model":"OrderSalesAggregateRelationQueryModel","payload":{"columns":["orderId","salesAmount"]}}
                """);

        Map<String, Object> payload = new ObjectMapper().readValue(rawResponse, Map.class);
        assertEquals(200, payload.get("code"));

        Map<String, Object> captured = QueryExpertService.LAST_QUERY_RESULT.get();
        assertNotNull(captured);
        assertEquals(1L, captured.get("total"));
        Map<String, Object> debug = (Map<String, Object>) captured.get("debug");
        Map<String, Object> extra = (Map<String, Object>) debug.get("extra");
        List<Map<String, Object>> diagnostics =
                (List<Map<String, Object>>) extra.get("aggregateRelationDiagnostics");
        assertEquals("retained", diagnostics.get(0).get("decision"));
        assertEquals("OR_CONDITION_OUTER_ONLY", diagnostics.get(0).get("reasonCode"));
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
        assertInstanceOf(Map.class, call.getResult());
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
    @DisplayName("query_model 空过滤字段失败应建议修复具体过滤字段或停止")
    @SuppressWarnings("unchecked")
    void queryModelEmptyFilterField_shouldReturnFilterRetryGuidance() throws Exception {
        McpTool tool = mockQueryModelTool(RX.failB("查询执行失败: 查询条件第1项的field字段不能为空"));

        String response = createCallback(tool).call("""
                {"model":"FactOrderQueryModel","payload":{"columns":["orderId"],"slice":[{"field":"","op":"isNull"}]}}
                """);

        Map<String, Object> payload = new ObjectMapper().readValue(response, Map.class);
        Map<String, Object> guidance = (Map<String, Object>) payload.get("retry_guidance");
        assertEquals("repair_filter_field_or_stop", guidance.get("action"));
        assertTrue(String.valueOf(guidance.get("instruction")).contains("concrete model field"));
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

    @Test
    @DisplayName("query_model post-aggregate alias unsupported 应返回聚合别名重试建议")
    @SuppressWarnings("unchecked")
    void queryModelPostAggregateUnsupported_shouldReturnPostAggregateRetryGuidance() throws Exception {
        McpTool tool = mockQueryModelTool(RX.failB(
                "查询执行失败: POST_AGGREGATE_CALCULATED_FIELD_UNSUPPORTED: query_model calculatedFields entry 'totalShare' references selected aggregate alias [amount] from the same grouped query. Free-form post-aggregate expressions are not supported in v1.6."));

        String response = createCallback(tool).call("""
                {"model":"FactOrderQueryModel","payload":{"columns":["sum(amount) as amount","totalShare"]}}
                """);

        Map<String, Object> payload = new ObjectMapper().readValue(response, Map.class);
        Map<String, Object> guidance = (Map<String, Object>) payload.get("retry_guidance");
        assertEquals("avoid_free_form_aggregate_alias_expression", guidance.get("action"));
        assertTrue(String.valueOf(guidance.get("instruction")).contains("postAggregateCalculations"));
    }

    @Test
    @DisplayName("query_model 同一失败签名重复达到阈值时应中止工具循环并保留失败响应摘要")
    @SuppressWarnings("unchecked")
    void repeatedQueryModelFailureSignature_shouldStopToolLoop() throws Exception {
        String failureMessage = "查询执行失败: POST_AGGREGATE_CALCULATED_FIELD_UNSUPPORTED: "
                + "query_model calculatedFields entry 'totalShare' references selected aggregate alias [amount] from the same grouped query. "
                + "Free-form post-aggregate expressions are not supported in v1.6.";
        McpTool tool = mockQueryModelTool(RX.failB(failureMessage));
        ToolCallCollector collector = new ToolCallCollector("session-1");
        ToolCallback callback = createCallback(tool, collector);

        String firstResponse = callback.call("""
                {"model":"FactOrderQueryModel","payload":{"columns":["sum(amount) as amount","totalShare"]}}
                """);
        String secondResponse = callback.call("""
                {"model":"FactOrderQueryModel","payload":{"columns":["sum(amount) as amount","totalShare"]}}
                """);

        Map<String, Object> firstPayload = new ObjectMapper().readValue(firstResponse, Map.class);
        Map<String, Object> secondPayload = new ObjectMapper().readValue(secondResponse, Map.class);
        assertEquals(1, firstPayload.get("failure_signature_repeat_count"));
        assertEquals(2, secondPayload.get("failure_signature_repeat_count"));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> callback.call("""
                {"model":"FactOrderQueryModel","payload":{"columns":["sum(amount) as amount","totalShare"]}}
                """));
        assertTrue(thrown.getMessage().contains(McpToolCallbackFactory.TOOL_FAILURE_BUDGET_EXCEEDED_MARKER));
        assertEquals(3, collector.getToolCalls().size());
        ToolCallCollector.ToolCallRecord lastCall = collector.getLastCall();
        assertFalse(lastCall.isSuccess());
        assertInstanceOf(Map.class, lastCall.getResult());
        Map<String, Object> lastPayload = (Map<String, Object>) lastCall.getResult();
        assertEquals(3, lastPayload.get("failure_signature_repeat_count"));
        assertTrue(String.valueOf(lastPayload.get("failure_signature")).contains("avoid_free_form_aggregate_alias_expression"));
    }

    private ToolCallback createCallback(McpTool tool) {
        return createCallback(tool, new ToolCallCollector("session-1"));
    }

    private ToolCallback createCallback(McpTool tool, ToolCallCollector collector) {
        ToolConfigLoader toolConfigLoader = mock(ToolConfigLoader.class);
        when(toolConfigLoader.getDescription("dataset.query_model")).thenReturn("query model");
        when(toolConfigLoader.getSchema("dataset.query_model")).thenReturn(Map.of("type", "object"));

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
