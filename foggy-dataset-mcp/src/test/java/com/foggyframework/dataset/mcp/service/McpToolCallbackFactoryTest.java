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
    void queryModelFailedRx_shouldRecordToolFailure() {
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
        assertNull(QueryExpertService.LAST_QUERY_RESULT.get());

        List<ToolCallCollector.ToolCallRecord> calls = collector.getToolCalls();
        assertEquals(1, calls.size());
        ToolCallCollector.ToolCallRecord call = calls.get(0);
        assertEquals("dataset.query_model", call.getToolName());
        assertFalse(call.isSuccess());
        assertTrue(call.getError().contains("QUERY_MODEL_FAILED"));
        assertTrue(call.getError().contains("FACT_ORDER"));
    }
}
