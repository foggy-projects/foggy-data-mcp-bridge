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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticSqlWhitelistValidatorTest {

    private SemanticQueryServiceV3Impl service;
    private QueryModel queryModel;

    @BeforeEach
    void setUp() {
        service = new SemanticQueryServiceV3Impl();
        QueryModelLoader loader = mock(QueryModelLoader.class);
        queryModel = queryModel(
                "SaleOrder",
                "orderId", "amount", "customer.name", "orderDate", "shipDate", "status",
                "phone"
        );
        when(loader.getJdbcQueryModel("SaleOrder", null)).thenReturn(queryModel);
        ReflectionTestUtils.setField(service, "queryModelLoader", loader);
    }

    @Test
    @DisplayName("SEMANTIC_SQL accepts declared virtual model fields and whitelisted functions")
    void acceptsVirtualModelFieldsAndAllowedFunctions() {
        SemanticQueryRequest request = semanticSql("""
                SELECT orderId, orderDate, shipDate
                FROM SaleOrder
                WHERE status = 'shipped'
                  AND DATE_DIFF('day', orderDate, shipDate) > 10
                """);

        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", request, SemanticRequestContext.empty());

        assertEquals("SEMANTIC_SQL", response.getExecution().getRoute());
        assertEquals("PLAN_READY", response.getExecution().getStatus());
        assertEquals(request.getSemanticSql(), response.getExecution().getSemanticSql());
        assertNotNull(response.getExecution().getAstValidation());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) response.getExecution()
                .getAstValidation().get("fields");
        assertTrue(fields.stream().anyMatch(field -> "orderDate".equals(field.get("name"))));
        assertTrue(fields.stream().anyMatch(field -> "shipDate".equals(field.get("name"))));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> functions = (List<Map<String, Object>>) response.getExecution()
                .getAstValidation().get("functions");
        assertTrue(functions.stream().anyMatch(function -> "DATE_DIFF".equals(function.get("name"))));
    }

    @Test
    @DisplayName("SEMANTIC_SQL rejects physical table names in FROM")
    void rejectsPhysicalTableFrom() {
        SemanticQueryRequest request = semanticSql("SELECT orderId FROM sales_order WHERE amount > 0");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", request, SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("SEMANTIC_SQL_PHYSICAL_TABLE_DENIED"));
    }

    @Test
    @DisplayName("SEMANTIC_SQL rejects undeclared fields")
    void rejectsUndeclaredField() {
        SemanticQueryRequest request = semanticSql("SELECT orderId, internalCost FROM SaleOrder");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", request, SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("SEMANTIC_SQL_FIELD_NOT_DECLARED"));
    }

    @Test
    @DisplayName("SEMANTIC_SQL rejects free SQL joins")
    void rejectsFreeJoin() {
        SemanticQueryRequest request = semanticSql("""
                SELECT orderId
                FROM SaleOrder JOIN Customer ON SaleOrder.customerId = Customer.id
                """);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", request, SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("SEMANTIC_SQL_JOIN_NOT_DECLARED"));
    }

    @Test
    @DisplayName("SEMANTIC_SQL rejects functions outside the whitelist")
    void rejectsUnsupportedFunction() {
        SemanticQueryRequest request = semanticSql("SELECT orderId FROM SaleOrder WHERE RANDOM() > 0.5");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", request, SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("SEMANTIC_SQL_FUNCTION_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("SEMANTIC_SQL rejects wildcard projections")
    void rejectsWildcardProjection() {
        SemanticQueryRequest request = semanticSql("SELECT * FROM SaleOrder");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", request, SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("SEMANTIC_SQL_FIELD_NOT_DECLARED"));
    }

    @Test
    @DisplayName("SEMANTIC_SQL allows COUNT star as an aggregate")
    void acceptsCountStarAggregate() {
        SemanticQueryRequest request = semanticSql("SELECT COUNT(*) FROM SaleOrder WHERE status = 'shipped'");

        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", request, SemanticRequestContext.empty());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> functions = (List<Map<String, Object>>) response.getExecution()
                .getAstValidation().get("functions");
        assertTrue(functions.stream().anyMatch(function -> "COUNT".equals(function.get("name"))));
    }

    @Test
    @DisplayName("SEMANTIC_SQL rejects fields denied by fieldAccess")
    void rejectsDeniedFieldAccess() {
        SemanticQueryRequest request = semanticSql("SELECT orderId, phone FROM SaleOrder");
        SemanticRequestContext context = SemanticRequestContext.of(
                null, null, Set.of("orderId", "amount", "customer.name", "orderDate", "shipDate", "status"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", request, context));

        assertTrue(ex.getMessage().contains("SEMANTIC_SQL_SENSITIVE_FIELD_DENIED"));
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
