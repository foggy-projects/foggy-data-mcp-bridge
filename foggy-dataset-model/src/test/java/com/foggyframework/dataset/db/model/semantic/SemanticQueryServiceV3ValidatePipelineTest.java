package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
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
        QueryModel saleOrder = queryModel(
                "SaleOrder",
                column("orderId"),
                measure("amount", DbAggregation.SUM),
                measure("quantity", null));
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

    @Test
    @DisplayName("validateQuery warns when raw measure-only columns omit groupBy and aggregate expressions")
    void validateQueryWarnsForRawMeasureOnlyColumnsWithoutGroupBy() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("amount", "quantity"));
        request.setLimit(1);

        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", request, SemanticRequestContext.ofNamespace("tenantA"));

        assertTrue(String.join("\n", response.getWarnings()).contains("RAW_MEASURE_SELECTION"));
        assertTrue(String.join("\n", response.getWarnings()).contains("sum(amount) as amount"));
    }

    @Test
    @DisplayName("validateQuery allows detail queries that include a non-measure anchor column")
    void validateQueryDoesNotWarnWhenDetailAnchorIsSelected() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("orderId", "amount"));
        request.setLimit(1);

        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", request, SemanticRequestContext.ofNamespace("tenantA"));

        assertTrue(response.getWarnings().contains("engine-warning"));
        assertTrue(response.getWarnings().stream().noneMatch(warning -> warning.contains("RAW_MEASURE_SELECTION")));
    }

    @Test
    @DisplayName("validateQuery allows explicit aggregate expressions without groupBy")
    void validateQueryDoesNotWarnWhenAggregatesAreExplicit() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("sum(amount) as amount", "countd(quantity) as quantity"));
        request.setLimit(1);

        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", request, SemanticRequestContext.ofNamespace("tenantA"));

        assertTrue(response.getWarnings().contains("engine-warning"));
        assertTrue(response.getWarnings().stream().noneMatch(warning -> warning.contains("RAW_MEASURE_SELECTION")));
    }

    private QueryModel queryModel(String name, DbQueryColumn... fields) {
        QueryModel qm = mock(QueryModel.class);
        List<DbQueryColumn> columns = List.of(fields);
        when(qm.getName()).thenReturn(name);
        when(qm.getShortAlias()).thenReturn(name);
        when(qm.getJdbcQueryColumns()).thenReturn(columns);
        when(qm.getPredefinedCalculatedFields()).thenReturn(List.of());
        for (DbQueryColumn column : columns) {
            when(qm.findJdbcQueryColumnByName(column.getName(), false)).thenReturn(column);
        }
        return qm;
    }

    private DbQueryColumn column(String name) {
        return column(name, false, null);
    }

    private DbQueryColumn measure(String name, DbAggregation aggregation) {
        return column(name, true, aggregation);
    }

    private DbQueryColumn column(String name, boolean measure, DbAggregation aggregation) {
        DbQueryColumn column = mock(DbQueryColumn.class);
        when(column.getName()).thenReturn(name);
        when(column.isMeasure()).thenReturn(measure);
        when(column.getAggregation()).thenReturn(aggregation);
        return column;
    }
}
