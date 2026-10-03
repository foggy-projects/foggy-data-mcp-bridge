package com.foggyframework.dataset.mcp.service;

import com.foggyframework.dataset.mcp.enums.UserRole;
import com.foggyframework.dataset.mcp.schema.McpRequest;
import com.foggyframework.dataset.mcp.schema.McpRequestContext;
import com.foggyframework.dataset.mcp.tools.QueryImageExportTool;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueryImageMcpContentTest {
    @Test void pngIsNativeContentOnlyOnceAndFailureSetsIsError() {
        var dispatcher = mock(McpToolDispatcher.class);
        var filter = mock(ToolFilterService.class);
        var policy = mock(NamespaceToolPolicyService.class);
        var tool = mock(QueryImageExportTool.class);
        when(tool.getName()).thenReturn("dataset.export_image");
        when(dispatcher.hasTool("dataset.export_image")).thenReturn(true);
        when(dispatcher.getTool("dataset.export_image")).thenReturn(tool);
        when(dispatcher.getAllTools()).thenReturn(List.of(tool));
        when(filter.canAccessTool(tool, UserRole.ANALYST)).thenReturn(true);
        when(policy.isAvailable(anyString(), anyCollection(), any(), any(), any(), any(), any())).thenReturn(true);
        var service = new McpService(dispatcher, filter, policy);
        var request = new McpRequest(); request.setId(1); request.setMethod("tools/call");
        request.setParams(Map.of("name", "dataset.export_image", "arguments", Map.of("model", "Sales", "payload", Map.of())));
        var context = McpRequestContext.of("trace", null, null, UserRole.ANALYST, "tenant-a");
        when(dispatcher.executeTool(anyString(), anyMap(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("success", true, "image", Map.of("mimeType", "image/png", "data", "png-base64-marker", "width", 1200)))
                .thenReturn(Map.of("success", false, "error", Map.of("code", "QUERY_FAILED", "message", "查询被拒绝")));
        Map<?,?> result = (Map<?,?>) service.handleToolsCall(request, context).getResult();
        assertEquals(false, result.get("isError"));
        List<?> content = (List<?>) result.get("content"); assertEquals(2, content.size());
        assertEquals("image", ((Map<?,?>) content.get(1)).get("type"));
        assertEquals("png-base64-marker", ((Map<?,?>) content.get(1)).get("data"));
        assertFalse(content.get(0).toString().contains("png-base64-marker"));
        assertFalse(result.get("structuredContent").toString().contains("png-base64-marker"));
        Map<?,?> failed = (Map<?,?>) service.handleToolsCall(request, context).getResult();
        assertEquals(true, failed.get("isError")); assertEquals(1, ((List<?>) failed.get("content")).size());
    }
}
