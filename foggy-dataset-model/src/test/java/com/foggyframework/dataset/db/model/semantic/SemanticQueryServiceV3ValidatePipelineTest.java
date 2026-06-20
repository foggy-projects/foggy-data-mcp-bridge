package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticQueryServiceV3ValidatePipelineTest {

    private SemanticQueryServiceV3Impl service;
    private QueryFacade queryFacade;

    @BeforeEach
    void setUp() {
        service = new SemanticQueryServiceV3Impl();
        QueryModelLoader loader = mock(QueryModelLoader.class);
        QueryModel saleOrder = queryModel("SaleOrder", "orderId");
        when(loader.getJdbcQueryModel("SaleOrder", "tenantA")).thenReturn(saleOrder);
        queryFacade = mock(QueryFacade.class);
        when(queryFacade.buildSqlOnly(any(ModelResultContext.class))).thenAnswer(invocation -> {
            ModelResultContext context = invocation.getArgument(0);
            context.getExtData().put("engineWarnings", List.of("engine-warning"));
            return new SqlGenerationResult("SELECT order_id FROM sale_order", List.of(), null);
        });
        ReflectionTestUtils.setField(service, "queryModelLoader", loader);
        ReflectionTestUtils.setField(service, "queryFacade", queryFacade);
    }

    @Test
    @DisplayName("validateQuery reuses SQL-only facade pipeline for normal semantic requests")
    void validateQueryUsesSqlOnlyPipeline() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("orderId"));
        request.setLimit(20);
        request.setHints(Map.of("traceId", "t-1"));
        request.setExtData(Map.of("caller", "unit"));

        ModelResultContext.SecurityContext securityContext =
                ModelResultContext.SecurityContext.builder().userId("u1").tenantId("tenantA").build();
        SemanticRequestContext requestContext = SemanticRequestContext.of(
                "tenantA", securityContext, Set.of("orderId"));

        SemanticQueryResponse response = service.validateQuery("SaleOrder", request, requestContext);

        ArgumentCaptor<ModelResultContext> captor = ArgumentCaptor.forClass(ModelResultContext.class);
        verify(queryFacade).buildSqlOnly(captor.capture());
        ModelResultContext resultContext = captor.getValue();
        assertEquals(ModelResultContext.QueryType.SEMANTIC, resultContext.getQueryType());
        assertEquals("tenantA", resultContext.getNamespace());
        assertSame(securityContext, resultContext.getSecurityContext());
        assertEquals(Set.of("orderId"), resultContext.getFieldAccess());
        assertEquals(List.of("orderId"), resultContext.getRequest().getParam().getColumns());
        assertEquals(20, resultContext.getRequest().getPageSize());
        assertEquals("t-1", resultContext.getExtData().get("traceId"));
        assertEquals("unit", resultContext.getExtData().get("caller"));
        assertTrue(response.getWarnings().contains("engine-warning"));
    }

    private QueryModel queryModel(String name, String... fields) {
        QueryModel qm = mock(QueryModel.class);
        List<DbQueryColumn> columns = List.of(fields).stream()
                .map(this::column)
                .toList();
        when(qm.getName()).thenReturn(name);
        when(qm.getShortAlias()).thenReturn(name);
        when(qm.getJdbcQueryColumns()).thenReturn(columns);
        when(qm.getPredefinedCalculatedFields()).thenReturn(List.of());
        return qm;
    }

    private DbQueryColumn column(String name) {
        DbQueryColumn column = mock(DbQueryColumn.class);
        when(column.getName()).thenReturn(name);
        return column;
    }
}
