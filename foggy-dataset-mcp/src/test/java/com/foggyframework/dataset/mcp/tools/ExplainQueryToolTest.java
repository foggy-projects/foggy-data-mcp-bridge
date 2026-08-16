package com.foggyframework.dataset.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainRequest;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainResponse;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainService;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExplainQueryToolTest {

    @Mock
    private SemanticExplainService semanticExplainService;

    @Test
    void definitionRequestUsesDescribeContext() {
        ExplainQueryTool tool = new ExplainQueryTool(semanticExplainService, new ObjectMapper());
        ToolExecutionContext executionContext = ToolExecutionContext.builder()
                .traceId("trace-1")
                .namespace("wwi")
                .authorization("Bearer demo")
                .build();

        tool.execute(Map.of(
                "model", "wwi_oltp_invoice_lines",
                "fields", List.of("totalIncludingTax"),
                "includePhysicalNames", true), executionContext);

        ArgumentCaptor<SemanticExplainRequest> request = ArgumentCaptor.forClass(SemanticExplainRequest.class);
        ArgumentCaptor<SemanticRequestContext> context = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticExplainService).explain(eq("wwi_oltp_invoice_lines"), request.capture(), context.capture());
        assertEquals(List.of("totalIncludingTax"), request.getValue().getFields());
        assertNull(request.getValue().getPayload());
        assertEquals(SemanticExplainRequest.Depth.STANDARD, request.getValue().getDepth());
        assertEquals("wwi", context.getValue().getNamespace());
        assertEquals("Bearer demo", context.getValue().getAuthorization());
        assertEquals(PermissionAction.DESCRIBE, context.getValue().getPermissionAction());
    }

    @Test
    void payloadRequestUsesExecuteContextAndMapsOptions() {
        ExplainQueryTool tool = new ExplainQueryTool(semanticExplainService, new ObjectMapper());

        tool.execute(Map.of(
                "model", "sales_summary",
                "payload", Map.of("columns", List.of("salesTerritory", "amount")),
                "depth", "DETAILED",
                "includeSql", true), ToolExecutionContext.of("trace-2", "Bearer token"));

        ArgumentCaptor<SemanticExplainRequest> request = ArgumentCaptor.forClass(SemanticExplainRequest.class);
        ArgumentCaptor<SemanticRequestContext> context = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticExplainService).explain(eq("sales_summary"), request.capture(), context.capture());
        assertNotNull(request.getValue().getPayload());
        assertEquals(List.of("salesTerritory", "amount"), request.getValue().getPayload().getColumns());
        assertEquals(SemanticExplainRequest.Depth.DETAILED, request.getValue().getDepth());
        assertEquals(PermissionAction.EXECUTE, context.getValue().getPermissionAction());
    }

    @Test
    void modelIsRequired() {
        ExplainQueryTool tool = new ExplainQueryTool(semanticExplainService, new ObjectMapper());
        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(Map.of(), ToolExecutionContext.of("trace-3", null)));
    }

    @Test
    void responseIsReturnedAsVersionedJsonInsteadOfRecordToString() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ExplainQueryTool tool = new ExplainQueryTool(semanticExplainService, objectMapper);
        when(semanticExplainService.explain(eq("sales"), any(), any()))
                .thenReturn(response(SemanticExplainResponse.Basis.DEFINITION));

        Object result = tool.execute(Map.of("model", "sales"),
                ToolExecutionContext.of("trace-4", null));

        JsonNode json = assertInstanceOf(JsonNode.class, result);
        assertEquals(SemanticExplainResponse.SCHEMA_VERSION, json.path("schemaVersion").asText());
        assertEquals("DEFINITION", json.path("basis").asText());
        assertEquals("sales", json.path("definitionTrace").path("queryModel").asText());
    }

    private SemanticExplainResponse response(SemanticExplainResponse.Basis basis) {
        return new SemanticExplainResponse(
                SemanticExplainResponse.SCHEMA_VERSION,
                basis,
                new SemanticExplainResponse.DefinitionTrace("sales", null, List.of(), List.of()),
                new SemanticExplainResponse.CompilationTrace(
                        null, null, List.of(), List.of(), null, List.of()),
                null,
                null,
                null,
                null,
                List.of());
    }
}
