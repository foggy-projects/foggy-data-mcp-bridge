package com.foggyframework.dataset.mcp.spi.impl;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LocalDatasetAccessor 治理契约测试")
@ExtendWith(MockitoExtension.class)
class LocalDatasetAccessorGovernanceTest {

    @Mock
    private SemanticServiceResolver semanticServiceResolver;

    private LocalDatasetAccessor accessor;

    @BeforeEach
    void setUp() {
        accessor = new LocalDatasetAccessor(semanticServiceResolver, createMcpProperties());
    }

    @Test
    @DisplayName("metadata 场景应将 grouped deniedColumns 展开并写入 SemanticRequestContext")
    void metadataShouldExpandGroupedDeniedColumnsIntoContext() {
        when(semanticServiceResolver.getAllModelNames()).thenReturn(List.of("OdooHrEmployeeQueryModel"));
        when(semanticServiceResolver.getMetadata(any(SemanticMetadataRequest.class), eq("markdown"), any(SemanticRequestContext.class)))
                .thenReturn(new SemanticMetadataResponse());

        Map<String, Object> options = Map.of(
                "deniedColumns",
                List.of(Map.of("table", "hr_employee", "columns", List.of("gender", "marital")))
        );

        RX<SemanticMetadataResponse> result = accessor.getMetadata("trace-metadata", null, "odoo", options);

        assertNotNull(result.getData());
        ArgumentCaptor<SemanticRequestContext> contextCaptor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceResolver).getMetadata(any(SemanticMetadataRequest.class), eq("markdown"), contextCaptor.capture());

        SemanticRequestContext context = contextCaptor.getValue();
        assertEquals("odoo", context.getNamespace());
        assertDeniedColumns(
                context.getDeniedColumns(),
                new String[][]{
                        {null, "hr_employee", "gender"},
                        {null, "hr_employee", "marital"}
                }
        );
        assertNull(context.getSystemSlice());
        assertNull(context.getSecurityContext());
    }

    @Test
    @DisplayName("describe 场景应兼容 flat deniedColumns 并保留 authorization")
    void describeShouldAcceptFlatDeniedColumnsIntoContext() {
        when(semanticServiceResolver.getMetadata(any(SemanticMetadataRequest.class), eq("json"), any(SemanticRequestContext.class)))
                .thenReturn(new SemanticMetadataResponse());

        Map<String, Object> flatDeniedColumn = new LinkedHashMap<>();
        flatDeniedColumn.put("schema", null);
        flatDeniedColumn.put("table", "hr_employee");
        flatDeniedColumn.put("column", "gender");

        Map<String, Object> options = Map.of(
                "deniedColumns",
                List.of(flatDeniedColumn)
        );

        RX<SemanticMetadataResponse> result = accessor.describeModel(
                "OdooHrEmployeeQueryModel",
                "json",
                "trace-describe",
                "Bearer governance-token",
                "odoo",
                options
        );

        assertNotNull(result.getData());
        ArgumentCaptor<SemanticRequestContext> contextCaptor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceResolver).getMetadata(any(SemanticMetadataRequest.class), eq("json"), contextCaptor.capture());

        SemanticRequestContext context = contextCaptor.getValue();
        assertEquals("odoo", context.getNamespace());
        assertEquals("Bearer governance-token", context.getAuthorization());
        assertDeniedColumns(
                context.getDeniedColumns(),
                new String[][]{{null, "hr_employee", "gender"}}
        );
        assertNull(context.getSystemSlice());
    }

    @Test
    @DisplayName("query 场景应将 deniedColumns 和 systemSlice 一并写入 SemanticRequestContext")
    void queryShouldWriteDeniedColumnsAndSystemSliceIntoContext() {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of());
        response.setHasNext(false);
        when(semanticServiceResolver.queryModel(anyString(), any(SemanticQueryRequest.class), eq("execute"), any(SemanticRequestContext.class)))
                .thenReturn(response);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("deniedColumns", List.of(
                Map.of("table", "hr_employee", "columns", List.of("gender", "marital"))
        ));
        options.put("systemSlice", List.of(
                Map.of("field", "company_id", "op", "=", "value", 1),
                Map.of("$or", List.of(
                        Map.of("department_id", 10),
                        Map.of("$and", List.of(
                                Map.of("field", "active", "op", "=", "value", true),
                                Map.of("field", "work_email", "op", "like", "value", "@foggy.com")
                        ))
                ))
        ));

        RX<SemanticQueryResponse> result = accessor.queryModel(
                "OdooHrEmployeeQueryModel",
                Map.of("columns", List.of("name")),
                "execute",
                "trace-query",
                "Bearer query-token",
                "odoo",
                options
        );

        assertNotNull(result.getData());
        ArgumentCaptor<SemanticRequestContext> contextCaptor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceResolver).queryModel(
                eq("OdooHrEmployeeQueryModel"),
                any(SemanticQueryRequest.class),
                eq("execute"),
                contextCaptor.capture()
        );

        SemanticRequestContext context = contextCaptor.getValue();
        assertEquals("odoo", context.getNamespace());
        assertEquals("Bearer query-token", context.getAuthorization());
        assertDeniedColumns(
                context.getDeniedColumns(),
                new String[][]{
                        {null, "hr_employee", "gender"},
                        {null, "hr_employee", "marital"}
                }
        );

        List<SliceRequestDef> systemSlice = context.getSystemSlice();
        assertNotNull(systemSlice);
        assertEquals(2, systemSlice.size());

        SliceRequestDef companySlice = systemSlice.get(0);
        assertEquals("company_id", companySlice.getField());
        assertEquals("=", companySlice.getOp());
        assertEquals(1, companySlice.getValue());

        SliceRequestDef orGroup = systemSlice.get(1);
        assertNotNull(orGroup.getOr());
        assertEquals(2, orGroup.getOr().size());
        assertCondition(orGroup.getOr().get(0), "department_id", "=", 10);

        CondRequestDef nestedAnd = orGroup.getOr().get(1);
        assertNotNull(nestedAnd.getAnd());
        assertEquals(2, nestedAnd.getAnd().size());
        assertCondition(nestedAnd.getAnd().get(0), "active", "=", true);
        assertCondition(nestedAnd.getAnd().get(1), "work_email", "like", "@foggy.com");
    }

    @Test
    @DisplayName("旧调用无 options 时应保持兼容")
    void shouldRemainBackwardCompatibleWithoutOptions() {
        when(semanticServiceResolver.getAllModelNames()).thenReturn(List.of("LegacyModel"));
        when(semanticServiceResolver.getMetadata(any(SemanticMetadataRequest.class), eq("markdown"), any(SemanticRequestContext.class)))
                .thenReturn(new SemanticMetadataResponse());
        when(semanticServiceResolver.queryModel(anyString(), any(SemanticQueryRequest.class), eq("execute"), any(SemanticRequestContext.class)))
                .thenReturn(new SemanticQueryResponse());

        RX<SemanticMetadataResponse> metadataResult = accessor.getMetadata("trace-legacy-metadata", null, null);
        RX<SemanticQueryResponse> queryResult = accessor.queryModel(
                "LegacyModel",
                Map.of("columns", List.of("name")),
                "execute",
                "trace-legacy-query",
                null,
                null
        );

        assertNotNull(metadataResult.getData());
        assertNotNull(queryResult.getData());

        ArgumentCaptor<SemanticRequestContext> metadataContextCaptor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceResolver).getMetadata(any(SemanticMetadataRequest.class), eq("markdown"), metadataContextCaptor.capture());
        SemanticRequestContext metadataContext = metadataContextCaptor.getValue();
        assertNull(metadataContext.getNamespace());
        assertNull(metadataContext.getDeniedColumns());
        assertNull(metadataContext.getSystemSlice());

        ArgumentCaptor<SemanticRequestContext> queryContextCaptor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceResolver).queryModel(anyString(), any(SemanticQueryRequest.class), eq("execute"), queryContextCaptor.capture());
        SemanticRequestContext queryContext = queryContextCaptor.getValue();
        assertNull(queryContext.getNamespace());
        assertNull(queryContext.getDeniedColumns());
        assertNull(queryContext.getSystemSlice());
        assertNull(queryContext.getSecurityContext());
    }

    @Test
    @DisplayName("未传 namespace 时应使用 request.defaultNamespace")
    void missingNamespaceShouldUseRequestDefaultNamespace() {
        DatasetProperties datasetProperties = new DatasetProperties();
        datasetProperties.getRequest().setDefaultNamespace("tms-ai");
        accessor = new LocalDatasetAccessor(semanticServiceResolver, createMcpProperties(), datasetProperties);

        when(semanticServiceResolver.getAllModelNames()).thenReturn(List.of("SemanticModel"));
        when(semanticServiceResolver.getMetadata(any(SemanticMetadataRequest.class), eq("markdown"), any(SemanticRequestContext.class)))
                .thenReturn(new SemanticMetadataResponse());

        RX<SemanticMetadataResponse> result = accessor.getMetadata("trace-default-ns", null, null);

        assertNotNull(result.getData());
        ArgumentCaptor<SemanticRequestContext> contextCaptor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceResolver).getMetadata(any(SemanticMetadataRequest.class), eq("markdown"), contextCaptor.capture());
        assertEquals("tms-ai", contextCaptor.getValue().getNamespace());
    }

    @Test
    @DisplayName("query 请求自身的 hints 应保持 MCP 标记")
    void queryShouldStillMarkRequestAsFromMcp() {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of());
        when(semanticServiceResolver.queryModel(anyString(), any(SemanticQueryRequest.class), eq("execute"), any(SemanticRequestContext.class)))
                .thenReturn(response);

        accessor.queryModel(
                "HintModel",
                Map.of("columns", List.of("name")),
                "execute",
                "trace-hints",
                null,
                null,
                Map.of("systemSlice", List.of(Map.of("company_id", 1)))
        );

        ArgumentCaptor<SemanticQueryRequest> requestCaptor = ArgumentCaptor.forClass(SemanticQueryRequest.class);
        verify(semanticServiceResolver).queryModel(eq("HintModel"), requestCaptor.capture(), eq("execute"), any(SemanticRequestContext.class));
        assertEquals(Boolean.TRUE, requestCaptor.getValue().getHints().get("fromMcp"));
    }

    private McpProperties createMcpProperties() {
        McpProperties properties = new McpProperties();
        properties.getSemantic().setUseAllModels(true);
        properties.getSemantic().getMetadata().setDefaultLevels(List.of(1));
        properties.getSemantic().getInternal().setDefaultLevels(List.of(1));
        return properties;
    }

    private void assertDeniedColumns(List<DeniedPhysicalColumn> actual, String[][] expected) {
        assertNotNull(actual);
        assertEquals(expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            DeniedPhysicalColumn item = actual.get(i);
            assertEquals(expected[i][0], item.getSchema());
            assertEquals(expected[i][1], item.getTable());
            assertEquals(expected[i][2], item.getColumn());
        }
    }

    private void assertCondition(CondRequestDef actual, String expectedField, String expectedOp, Object expectedValue) {
        assertEquals(expectedField, actual.getField());
        assertEquals(expectedOp, actual.getOp());
        assertEquals(expectedValue, actual.getValue());
        assertFalse(actual._isLogicalGroup());
    }
}
