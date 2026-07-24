package com.foggyframework.dataset.mcp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.mcp.service.ModelCatalogService;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ListModelsCatalogController 单元测试")
@ExtendWith(MockitoExtension.class)
class ListModelsCatalogControllerTest {

    @Mock
    private SemanticServiceResolver semanticServiceResolver;

    @Mock
    private QueryModelLoader queryModelLoader;

    private ListModelsCatalogController controller;

    @BeforeEach
    void setUp() {
        ModelCatalogService service = new ModelCatalogService(
                semanticServiceResolver,
                queryModelLoader,
                new ObjectMapper()
        );
        controller = new ListModelsCatalogController(service);
    }

    @Test
    @DisplayName("POST /semantic/v3/list-models 应接受 host 参数并返回 markdown")
    void shouldAcceptHostArgumentsAndReturnMarkdown() {
        QueryModel qm = mockQueryModel("销售明细查询", "销售额、销量分析");
        when(queryModelLoader.getJdbcQueryModel(eq("FactSalesQueryModel"), eq("odoo"))).thenReturn(qm);
        SemanticMetadataResponse metadata = metadataWithOrderId();
        when(semanticServiceResolver.getMetadata(any(), eq("json"), any(SemanticRequestContext.class)))
                .thenReturn(metadata);

        Map<String, Object> response = controller.listModels(
                Map.of(
                        "format", "markdown",
                        "modelNames", List.of("FactSalesQueryModel"),
                        "visibleFields", List.of("orderId"),
                        "deniedColumns", List.of(Map.of(
                                "table", "fact_sales",
                                "columns", List.of("secret_amount")
                        )),
                        "fieldLimit", 0
                ),
                "Bearer test-token",
                "odoo"
        );

        assertEquals("markdown", response.get("format"));
        assertTrue(((String) response.get("content")).contains("FactSalesQueryModel"));
        assertFalse(response.containsKey("data"));
        assertFalse(response.containsKey("items"));

        ArgumentCaptor<SemanticRequestContext> contextCaptor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceResolver).getMetadata(any(), eq("json"), contextCaptor.capture());
        SemanticRequestContext context = contextCaptor.getValue();
        assertEquals("odoo", context.getNamespace());
        assertEquals("Bearer test-token", context.getAuthorization());
        assertEquals(java.util.Set.of("orderId"), context.getFieldAccess());
        List<DeniedPhysicalColumn> deniedColumns = context.getDeniedColumns();
        assertNotNull(deniedColumns);
        assertEquals("fact_sales", deniedColumns.get(0).getTable());
        assertEquals("secret_amount", deniedColumns.get(0).getColumn());
        verify(semanticServiceResolver, never()).getAllModelNames();
    }

    @Test
    @DisplayName("format=json 且 fieldLimit=0 时不返回字段级 catalog")
    void jsonFieldLimitZeroShouldOmitFieldDetails() {
        QueryModel qm = mockQueryModel("销售明细查询", "销售额、销量分析");
        when(queryModelLoader.getJdbcQueryModel(eq("FactSalesQueryModel"), eq("odoo"))).thenReturn(qm);
        when(semanticServiceResolver.getMetadata(any(), eq("json"), any(SemanticRequestContext.class)))
                .thenReturn(metadataWithOrderId());

        Map<String, Object> response = controller.listModels(
                Map.of(
                        "format", "json",
                        "modelNames", List.of("FactSalesQueryModel"),
                        "fieldLimit", 0
                ),
                null,
                "odoo"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> catalog = (Map<String, Object>) response.get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) catalog.get("items");
        Map<String, Object> item = items.get(0);
        assertFalse(item.containsKey("fields"));
        assertFalse(item.containsKey("fieldPreview"));
        assertFalse(item.containsKey("fieldCount"));
        assertFalse(item.containsKey("primaryTimeField"));
    }

    @Test
    @DisplayName("format=all 返回 markdown content 和 JSON catalog")
    void allShouldReturnMarkdownAndCatalog() {
        QueryModel qm = mockQueryModel("销售明细查询", "销售额、销量分析");
        when(queryModelLoader.getJdbcQueryModel(eq("FactSalesQueryModel"), eq("odoo"))).thenReturn(qm);
        when(semanticServiceResolver.getMetadata(any(), eq("json"), any(SemanticRequestContext.class)))
                .thenReturn(metadataWithOrderId());

        Map<String, Object> response = controller.listModels(
                Map.of(
                        "format", "all",
                        "modelNames", List.of("FactSalesQueryModel"),
                        "fieldLimit", 0
                ),
                null,
                "odoo"
        );

        assertEquals("all", response.get("format"));
        assertTrue(((String) response.get("content")).contains("FactSalesQueryModel"));
        @SuppressWarnings("unchecked")
        Map<String, Object> catalog = (Map<String, Object>) response.get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) catalog.get("items");
        Map<String, Object> item = items.get(0);
        assertEquals("FactSalesQueryModel", item.get("model"));
        assertFalse(item.containsKey("fields"));
        assertFalse(item.containsKey("fieldPreview"));
        assertFalse(item.containsKey("fieldCount"));
        assertFalse(item.containsKey("primaryTimeField"));
    }

    @Test
    @DisplayName("空 body 应默认返回 JSON content 和 canonical data")
    void shouldDefaultToJson() {
        when(semanticServiceResolver.getAllModelNames()).thenReturn(List.of("FactSalesQueryModel"));
        QueryModel qm = mockQueryModel("销售明细查询", "销售额、销量分析");
        when(queryModelLoader.getJdbcQueryModel(eq("FactSalesQueryModel"), isNull())).thenReturn(qm);
        when(semanticServiceResolver.getMetadata(any(), eq("json"), any(SemanticRequestContext.class)))
                .thenReturn(metadataWithOrderId());

        Map<String, Object> response = controller.listModels(null, null, null);

        assertEquals("json", response.get("format"));
        assertTrue(((String) response.get("content")).contains("FactSalesQueryModel"));
        @SuppressWarnings("unchecked")
        Map<String, Object> catalog = (Map<String, Object>) response.get("data");
        assertEquals(List.of("FactSalesQueryModel"), catalog.get("models"));
        assertEquals(1, catalog.get("count"));
    }

    private static SemanticMetadataResponse metadataWithOrderId() {
        SemanticMetadataResponse metadata = new SemanticMetadataResponse();
        Map<String, Object> fieldInfo = new java.util.LinkedHashMap<>();
        fieldInfo.put("models", Map.of("FactSalesQueryModel", Map.of("description", "订单号")));
        metadata.setData(Map.of(
                "fields", Map.of("orderId", fieldInfo),
                "models", Map.of("FactSalesQueryModel", Map.of("name", "销售明细查询"))
        ));
        return metadata;
    }

    private static QueryModel mockQueryModel(String caption, String description) {
        QueryModel qm = mock(QueryModel.class);
        when(qm.getCaption()).thenReturn(caption);
        lenient().when(qm.getDescription()).thenReturn(description);
        lenient().when(qm.getAi()).thenReturn(null);
        lenient().when(qm.getQueryDimensions()).thenReturn(List.of());
        return qm;
    }
}
