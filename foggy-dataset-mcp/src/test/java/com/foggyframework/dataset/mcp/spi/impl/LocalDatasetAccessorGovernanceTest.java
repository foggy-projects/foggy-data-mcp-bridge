package com.foggyframework.dataset.mcp.spi.impl;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.support.UnknownQueryPropertyPolicy;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(semanticServiceResolver.getAllModelNames("odoo")).thenReturn(List.of("OdooHrEmployeeQueryModel"));
        when(semanticServiceResolver.getMetadata(any(SemanticMetadataRequest.class), eq("markdown"), any(SemanticRequestContext.class)))
                .thenReturn(new SemanticMetadataResponse());

        Map<String, Object> options = Map.of(
                "deniedColumns",
                List.of(Map.of("table", "hr_employee", "columns", List.of("gender", "marital")))
        );

        RX<SemanticMetadataResponse> result = accessor.getMetadata("trace-metadata", null, "odoo", options);

        assertNotNull(result.getData());
        ArgumentCaptor<SemanticMetadataRequest> requestCaptor = ArgumentCaptor.forClass(SemanticMetadataRequest.class);
        ArgumentCaptor<SemanticRequestContext> contextCaptor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceResolver).getMetadata(requestCaptor.capture(), eq("markdown"), contextCaptor.capture());

        assertTrue(requestCaptor.getValue().isTolerateModelLoadErrors());
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
        ArgumentCaptor<SemanticMetadataRequest> requestCaptor = ArgumentCaptor.forClass(SemanticMetadataRequest.class);
        ArgumentCaptor<SemanticRequestContext> contextCaptor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceResolver).getMetadata(requestCaptor.capture(), eq("json"), contextCaptor.capture());

        assertFalse(requestCaptor.getValue().isTolerateModelLoadErrors());
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

        when(semanticServiceResolver.getAllModelNames("tms-ai")).thenReturn(List.of("SemanticModel"));
        when(semanticServiceResolver.getMetadata(any(SemanticMetadataRequest.class), eq("markdown"), any(SemanticRequestContext.class)))
                .thenReturn(new SemanticMetadataResponse());

        RX<SemanticMetadataResponse> result = accessor.getMetadata("trace-default-ns", null, null);

        assertNotNull(result.getData());
        ArgumentCaptor<SemanticRequestContext> contextCaptor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceResolver).getMetadata(any(SemanticMetadataRequest.class), eq("markdown"), contextCaptor.capture());
        assertEquals("tms-ai", contextCaptor.getValue().getNamespace());
    }

    @Test
    @DisplayName("metadata 显式 namespace 应使用 namespace 专属 model-list 覆盖全局静态列表")
    void metadataExplicitNamespaceShouldUseNamespaceModelList() {
        McpProperties properties = new McpProperties();
        properties.getSemantic().setModelList(List.of(
                "FactOrderQueryModel",
                "CrmLead",
                "CustomerOrderLifecycleQueryModel",
                "ServiceTicketQueryModel"
        ));
        McpProperties.NamespaceSemanticConfig salesdropConfig = new McpProperties.NamespaceSemanticConfig();
        salesdropConfig.setModelList(List.of("SalesDropDailyQueryModel"));
        properties.getSemantic().setNamespaces(Map.of("salesdrop", salesdropConfig));
        accessor = new LocalDatasetAccessor(semanticServiceResolver, properties);

        when(semanticServiceResolver.getMetadata(any(SemanticMetadataRequest.class), eq("markdown"), any(SemanticRequestContext.class)))
                .thenReturn(new SemanticMetadataResponse());

        RX<SemanticMetadataResponse> result = accessor.getMetadata("trace-salesdrop-metadata", null, "salesdrop");

        assertNotNull(result.getData());
        ArgumentCaptor<SemanticMetadataRequest> requestCaptor = ArgumentCaptor.forClass(SemanticMetadataRequest.class);
        ArgumentCaptor<SemanticRequestContext> contextCaptor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceResolver).getMetadata(requestCaptor.capture(), eq("markdown"), contextCaptor.capture());
        assertEquals(List.of("SalesDropDailyQueryModel"), requestCaptor.getValue().getQmModels());
        assertEquals("salesdrop", contextCaptor.getValue().getNamespace());
        verify(semanticServiceResolver, never()).getAllModelNames();
    }

    @Test
    @DisplayName("metadata 显式 namespace 不应回退到全局 legacy model-list")
    void metadataExplicitNamespaceShouldNotUseGlobalLegacyModelList() {
        McpProperties properties = new McpProperties();
        properties.getSemantic().setModelList(List.of(
                "FactOrderQueryModel",
                "CrmLead",
                "CustomerOrderLifecycleQueryModel",
                "ServiceTicketQueryModel"
        ));
        accessor = new LocalDatasetAccessor(semanticServiceResolver, properties);

        when(semanticServiceResolver.getAllModelNames("salesdrop"))
                .thenReturn(List.of("SalesDropDailyQueryModel"));
        when(semanticServiceResolver.getMetadata(any(SemanticMetadataRequest.class), eq("markdown"), any(SemanticRequestContext.class)))
                .thenReturn(new SemanticMetadataResponse());

        RX<SemanticMetadataResponse> result = accessor.getMetadata("trace-salesdrop-dynamic", null, "salesdrop");

        assertNotNull(result.getData());
        ArgumentCaptor<SemanticMetadataRequest> requestCaptor = ArgumentCaptor.forClass(SemanticMetadataRequest.class);
        verify(semanticServiceResolver).getMetadata(requestCaptor.capture(), eq("markdown"), any(SemanticRequestContext.class));
        assertEquals(List.of("SalesDropDailyQueryModel"), requestCaptor.getValue().getQmModels());
        verify(semanticServiceResolver).getAllModelNames("salesdrop");
        verify(semanticServiceResolver, never()).getAllModelNames();
    }

    @Test
    @DisplayName("metadata 未传 namespace 时保留默认路径全局 legacy model-list")
    void metadataDefaultNamespacePathShouldKeepGlobalLegacyModelList() {
        McpProperties properties = new McpProperties();
        properties.getSemantic().setModelList(List.of("FactOrderQueryModel"));
        accessor = new LocalDatasetAccessor(semanticServiceResolver, properties);

        when(semanticServiceResolver.getMetadata(any(SemanticMetadataRequest.class), eq("markdown"), any(SemanticRequestContext.class)))
                .thenReturn(new SemanticMetadataResponse());

        RX<SemanticMetadataResponse> result = accessor.getMetadata("trace-default-legacy", null, null);

        assertNotNull(result.getData());
        ArgumentCaptor<SemanticMetadataRequest> requestCaptor = ArgumentCaptor.forClass(SemanticMetadataRequest.class);
        verify(semanticServiceResolver).getMetadata(requestCaptor.capture(), eq("markdown"), any(SemanticRequestContext.class));
        assertEquals(List.of("FactOrderQueryModel"), requestCaptor.getValue().getQmModels());
        verify(semanticServiceResolver, never()).getAllModelNames();
        verify(semanticServiceResolver, never()).getAllModelNames(anyString());
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

    @Test
    @DisplayName("DSL_CTE query payload 应自动启用受控 DSL bridge 执行")
    void dslCteQueryShouldEnableCompileBridgeHint() {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of());
        when(semanticServiceResolver.queryModel(anyString(), any(SemanticQueryRequest.class), eq("execute"), any(SemanticRequestContext.class)))
                .thenReturn(response);

        Map<String, Object> ctePlan = Map.of(
                "stages", List.of(
                        Map.of(
                                "name", "ticket_scope",
                                "type", "derive",
                                "input", Map.of("model", "ServiceTicketQueryModel"),
                                "derived", List.of(
                                        Map.of("name", "firstResponseHours",
                                                "expr", "hours_between(createdAt, firstResponseAt)"),
                                        Map.of("name", "slaHit",
                                                "expr", "firstResponseAt is not null and firstResponseHours <= 48")
                                )
                        ),
                        Map.of(
                                "name", "team_sla",
                                "type", "aggregate",
                                "inputs", List.of("ticket_scope"),
                                "groupBy", List.of("team$caption"),
                                "metrics", List.of(
                                        Map.of("name", "ticketCount", "expr", "count(*)"),
                                        Map.of("name", "slaHitCount", "expr", "sum(slaHit)")
                                )
                        )
                ),
                "output", List.of("team$caption", "ticketCount", "slaHitCount")
        );

        accessor.queryModel(
                "ServiceTicketQueryModel",
                Map.of(
                        "route", "DSL_CTE",
                        "executable_plan", Map.of("cte_plan", ctePlan)
                ),
                "execute",
                "trace-dsl-cte",
                null,
                null
        );

        ArgumentCaptor<SemanticQueryRequest> requestCaptor = ArgumentCaptor.forClass(SemanticQueryRequest.class);
        verify(semanticServiceResolver).queryModel(
                eq("ServiceTicketQueryModel"),
                requestCaptor.capture(),
                eq("execute"),
                any(SemanticRequestContext.class)
        );

        SemanticQueryRequest request = requestCaptor.getValue();
        assertEquals("DSL_CTE", request.getRoute());
        assertNotNull(request.getExecutablePlan());
        assertEquals(Boolean.TRUE, request.getHints().get("fromMcp"));
        assertEquals(Boolean.TRUE, request.getHints().get("dslCteCompileToDsl"));
    }

    @Test
    @DisplayName("query payload 应透传 display-only outputFormatting")
    void queryShouldMapOutputFormattingIntoRequest() {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of());
        when(semanticServiceResolver.queryModel(anyString(), any(SemanticQueryRequest.class), eq("execute"), any(SemanticRequestContext.class)))
                .thenReturn(response);

        accessor.queryModel(
                "DisplayFormatModel",
                Map.of(
                        "columns", List.of("collectionRate"),
                        "outputFormatting", List.of(Map.of(
                                "field", "collectionRate",
                                "kind", "decimal",
                                "scale", 1,
                                "mode", "HALF_UP",
                                "scope", "display_only"
                        ))
                ),
                "execute",
                "trace-output-formatting",
                null,
                null
        );

        ArgumentCaptor<SemanticQueryRequest> requestCaptor = ArgumentCaptor.forClass(SemanticQueryRequest.class);
        verify(semanticServiceResolver).queryModel(eq("DisplayFormatModel"), requestCaptor.capture(), eq("execute"), any(SemanticRequestContext.class));

        List<SemanticQueryRequest.OutputFormattingItem> outputFormatting = requestCaptor.getValue().getOutputFormatting();
        assertNotNull(outputFormatting);
        assertEquals(1, outputFormatting.size());
        SemanticQueryRequest.OutputFormattingItem item = outputFormatting.get(0);
        assertEquals("collectionRate", item.getField());
        assertEquals("decimal", item.getKind());
        assertEquals(1, item.getScale());
        assertEquals("HALF_UP", item.getMode());
        assertEquals("display_only", item.getScope());
    }

    @Test
    @DisplayName("MCP warn 模式应返回结构化告警且仍按支持的 DSL 执行")
    void queryShouldPropagateUnknownPropertyWarning() {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of());
        when(semanticServiceResolver.queryModel(
                anyString(), any(SemanticQueryRequest.class), eq("execute"), any(SemanticRequestContext.class)))
                .thenReturn(response);

        RX<SemanticQueryResponse> result = accessor.queryModel(
                "FactOrderQueryModel",
                Map.of("groupBy", List.of(Map.of(
                        "field", "orderDate$month",
                        "grain", "month"))),
                "execute",
                "trace-query-warning",
                null,
                null);

        assertNotNull(result.getData());
        assertEquals(1, result.getData().getQueryInputWarnings().size());
        assertEquals("UNKNOWN_QUERY_PROPERTY_IGNORED",
                result.getData().getQueryInputWarnings().get(0).code());
        assertEquals("$.groupBy[0].grain",
                result.getData().getQueryInputWarnings().get(0).path());

        ArgumentCaptor<SemanticQueryRequest> requestCaptor =
                ArgumentCaptor.forClass(SemanticQueryRequest.class);
        verify(semanticServiceResolver).queryModel(
                eq("FactOrderQueryModel"), requestCaptor.capture(), eq("execute"),
                any(SemanticRequestContext.class));
        assertEquals("orderDate$month", requestCaptor.getValue().getGroupBy().get(0).getField());
    }

    @Test
    @DisplayName("MCP strict 模式应在执行前返回全部未知属性违规")
    void queryShouldRejectUnknownPropertiesBeforeExecutionInStrictMode() {
        DatasetProperties strictProperties = new DatasetProperties();
        strictProperties.getQuery().setUnknownPropertyPolicy(UnknownQueryPropertyPolicy.STRICT);
        accessor = new LocalDatasetAccessor(
                semanticServiceResolver, createMcpProperties(), strictProperties);

        RX<SemanticQueryResponse> result = accessor.queryModel(
                "FactOrderQueryModel",
                Map.of(
                        "groupBy", List.of(Map.of("field", "orderDate", "grain", "month")),
                        "orderBy", List.of(Map.of("field", "amount", "descending", true))),
                "execute",
                "trace-query-strict",
                null,
                null);

        assertEquals(400, result.getCode());
        assertNull(result.getData());
        assertTrue(result.getEt() instanceof Map<?, ?>);
        Map<?, ?> error = (Map<?, ?>) result.getEt();
        assertEquals("UNKNOWN_QUERY_PROPERTY", error.get("code"));
        assertEquals(2, ((List<?>) error.get("violations")).size());
        verifyNoInteractions(semanticServiceResolver);
    }

    @Test
    @DisplayName("query payload 应透传 Pivot 并支持字符串轴字段简写")
    void queryShouldMapPivotStringAxisShorthandIntoRequest() {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of());
        when(semanticServiceResolver.queryModel(anyString(), any(SemanticQueryRequest.class), eq("execute"), any(SemanticRequestContext.class)))
                .thenReturn(response);

        accessor.queryModel(
                "FactOrderQueryModel",
                Map.of("pivot", Map.of(
                        "rows", List.of("orderStatus"),
                        "columns", List.of(),
                        "metrics", List.of("payAmount"),
                        "outputFormat", "flat"
                )),
                "execute",
                "trace-pivot",
                null,
                null
        );

        ArgumentCaptor<SemanticQueryRequest> requestCaptor = ArgumentCaptor.forClass(SemanticQueryRequest.class);
        verify(semanticServiceResolver).queryModel(
                eq("FactOrderQueryModel"),
                requestCaptor.capture(),
                eq("execute"),
                any(SemanticRequestContext.class)
        );

        assertNotNull(requestCaptor.getValue().getPivot());
        assertEquals("orderStatus", requestCaptor.getValue().getPivot().getRows().get(0).getField());
        assertEquals(List.of("payAmount"), requestCaptor.getValue().getPivot().getMetrics());
        assertEquals("flat", requestCaptor.getValue().getPivot().getOutputFormat());
    }

    @Test
    @DisplayName("query payload 中非法 slice 项应 fail-closed")
    void queryShouldFailClosedForInvalidSliceItem() {
        RX<SemanticQueryResponse> result = accessor.queryModel(
                "BadSliceModel",
                Map.of("slice", List.of(Boolean.TRUE)),
                "execute",
                "trace-invalid-slice",
                null,
                null
        );

        assertNull(result.getData());
        assertNotNull(result.getMsg());
        assertTrue(result.getMsg().contains("QUERY_MODEL_SLICE_CONTRACT_INVALID"), result.getMsg());
        assertTrue(result.getMsg().contains("payload.slice[0]"), result.getMsg());
        verifyNoInteractions(semanticServiceResolver);
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
