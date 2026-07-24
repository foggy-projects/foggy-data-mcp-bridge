package com.foggyframework.dataset.model.semantic;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.spi.DbQueryColumn;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticSqlToDslMapperTest {

    private SemanticQueryServiceV3Impl service;

    @BeforeEach
    void setUp() {
        service = new SemanticQueryServiceV3Impl();
        QueryModelLoader loader = mock(QueryModelLoader.class);
        QueryModel saleOrder = queryModel(
                "SaleOrder",
                "orderId", "amount", "customer.name", "orderDate", "shipDate", "status"
        );
        QueryModel movie = queryModel(
                "Movie",
                "movieId", "title", "cast.actorName"
        );
        when(loader.getJdbcQueryModel("SaleOrder", null)).thenReturn(saleOrder);
        when(loader.getJdbcQueryModel("Movie", null)).thenReturn(movie);
        ReflectionTestUtils.setField(service, "queryModelLoader", loader);
    }

    @Test
    @DisplayName("SEMANTIC_SQL maps simple projections and field predicates to DSL evidence")
    void mapsSimpleProjectionAndPredicates() {
        SemanticQueryRequest request = semanticSql("""
                SELECT orderId, amount, customer.name, orderDate, shipDate
                FROM SaleOrder
                WHERE amount IS NULL OR customer.name IS NULL OR orderDate > shipDate
                """);

        SemanticQueryResponse response = service.validateQuery("SaleOrder", request, SemanticRequestContext.empty());

        Map<String, Object> plan = response.getExecution().getSemanticSqlDslPlan();
        assertNotNull(plan);
        assertEquals("MAPPED", plan.get("mapping_status"));
        assertEquals("SaleOrder", plan.get("from"));
        assertEquals(false, plan.get("execution_enabled"));

        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) plan.get("columns");
        assertTrue(columns.containsAll(List.of("orderId", "amount", "customer.name", "orderDate", "shipDate")));
        assertTrue(plan.get("slice").toString().contains("fieldRef=shipDate"));
        assertEquals("BRIDGE_READY", plan.get("dsl_bridge_status"));
        assertNotNull(plan.get("dsl_request"));
    }

    @Test
    @DisplayName("SEMANTIC_SQL maps aggregate group/having/order/limit to DSL evidence")
    void mapsAggregatePlanEvidence() {
        SemanticQueryRequest request = semanticSql("""
                SELECT status, SUM(amount) AS totalAmount
                FROM SaleOrder
                WHERE status = 'shipped'
                GROUP BY status
                HAVING SUM(amount) > 10000
                ORDER BY status DESC
                LIMIT 10
                """);

        SemanticQueryResponse response = service.validateQuery("SaleOrder", request, SemanticRequestContext.empty());

        Map<String, Object> plan = response.getExecution().getSemanticSqlDslPlan();
        assertEquals("MAPPED", plan.get("mapping_status"));
        assertEquals(List.of("status"), plan.get("groupBy"));
        assertEquals(10, plan.get("limit"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) plan.get("metrics");
        assertTrue(metrics.stream().anyMatch(metric ->
                "SUM".equals(metric.get("agg"))
                        && "amount".equals(metric.get("field"))
                        && "totalAmount".equals(metric.get("alias"))));
        assertTrue(plan.get("having").toString().contains("SUM(amount)"));
        assertTrue(plan.get("orderBy").toString().contains("dir=desc"));
        assertEquals("BRIDGE_DEFERRED", plan.get("dsl_bridge_status"));
    }

    @Test
    @DisplayName("SEMANTIC_SQL defers controlled M:N relation predicates instead of treating them as free joins")
    void defersControlledRelationPredicate() {
        SemanticQueryRequest request = semanticSql("""
                SELECT movieId, title FROM Movie
                WHERE cast.actorName IN ('演员 A','演员 B')
                GROUP BY movieId, title
                HAVING COUNT(DISTINCT cast.actorName) = 2
                """);

        SemanticQueryResponse response = service.validateQuery("Movie", request, SemanticRequestContext.empty());

        Map<String, Object> plan = response.getExecution().getSemanticSqlDslPlan();
        assertEquals("DEFERRED", plan.get("mapping_status"));
        assertEquals(true, plan.get("requires_declared_relation"));
        assertTrue(plan.get("relation_control_reasons").toString().contains("relation_membership"));
    }

    @Test
    @DisplayName("SEMANTIC_SQL generateSql can opt in to DSL bridge for executable v1 subset")
    void generateSqlOptInUsesDslBridge() {
        AdvancedQueryFacade queryFacade = mock(AdvancedQueryFacade.class);
        when(queryFacade.buildSqlOnly(any(ModelResultContext.class)))
                .thenReturn(new SqlGenerationResult("SELECT order_id FROM sale_order WHERE status = ?", List.of("shipped"), null));
        ReflectionTestUtils.setField(service, "queryFacade", queryFacade);

        SemanticQueryRequest request = semanticSql("SELECT orderId FROM SaleOrder WHERE status = 'shipped' LIMIT 5");
        request.setHints(Map.of("semanticSqlCompileToDsl", true));

        SqlGenerationResult result = service.generateSql("SaleOrder", request, SemanticRequestContext.empty());

        assertEquals("SELECT order_id FROM sale_order WHERE status = ?", result.getSql());
        org.mockito.ArgumentCaptor<ModelResultContext> captor = org.mockito.ArgumentCaptor.forClass(ModelResultContext.class);
        verify(queryFacade).buildSqlOnly(captor.capture());
        assertEquals(List.of("orderId"), captor.getValue().getRequest().getParam().getColumns());
        assertEquals(5, captor.getValue().getRequest().getPageSize());
    }

    @Test
    @DisplayName("SEMANTIC_SQL generateSql opt-in still fails closed for non-bridgeable expressions")
    void generateSqlOptInDefersExpressionPredicates() {
        SemanticQueryRequest request = semanticSql("""
                SELECT orderId FROM SaleOrder
                WHERE DATE_DIFF('day', orderDate, shipDate) > 10
                """);
        request.setHints(Map.of("semanticSqlCompileToDsl", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.generateSql("SaleOrder", request, SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("SEMANTIC_SQL_DSL_BRIDGE_NOT_SUPPORTED"));
    }

    @Test
    @DisplayName("SEMANTIC_SQL mapping evidence does not enable SQL generation")
    void keepsGenerateSqlFailClosed() {
        SemanticQueryRequest request = semanticSql("SELECT orderId FROM SaleOrder WHERE status = 'shipped'");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.generateSql("SaleOrder", request, SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("SEMANTIC_SQL_EXECUTION_NOT_IMPLEMENTED"));
    }

    private SemanticQueryRequest semanticSql(String sql) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("SEMANTIC_SQL");
        request.setSemanticSql(sql);
        return request;
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
        DbQueryColumn col = mock(DbQueryColumn.class);
        when(col.getName()).thenReturn(name);
        return col;
    }
}
