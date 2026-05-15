package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
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
