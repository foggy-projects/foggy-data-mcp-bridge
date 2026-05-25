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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticSqlAcceptanceSampleTest {

    private SemanticQueryServiceV3Impl service;

    @BeforeEach
    void setUp() {
        service = new SemanticQueryServiceV3Impl();
        QueryModelLoader loader = mock(QueryModelLoader.class);
        Map<String, QueryModel> models = Map.of(
                "SaleOrder", queryModel(
                        "SaleOrder",
                        "orderId", "amount", "customer.name", "orderDate", "shipDate", "status"
                ),
                "ArInvoice", queryModel(
                        "ArInvoice",
                        "invoiceId", "invoiceDate", "dueDate", "unpaidAmount", "customer.name", "paidAt"
                ),
                "Movie", queryModel(
                        "Movie",
                        "movieId", "title", "cast.actorName"
                ),
                "Employee", queryModel(
                        "Employee",
                        "employeeName", "salary", "manager.employeeName", "manager.salary"
                ),
                "Singer", queryModel(
                        "Singer",
                        "singerId", "singerName", "albums"
                )
        );
        for (Map.Entry<String, QueryModel> entry : models.entrySet()) {
            when(loader.getJdbcQueryModel(entry.getKey(), null)).thenReturn(entry.getValue());
        }
        ReflectionTestUtils.setField(service, "queryModelLoader", loader);
    }

    @Test
    @DisplayName("SEMANTIC_SQL acceptance samples pass current AST whitelist")
    void acceptsSemanticSqlAcceptanceSamples() {
        assertSemanticSqlReady(
                "biz-007",
                "SaleOrder",
                "SELECT orderId, amount, customer.name, orderDate, shipDate FROM SaleOrder "
                        + "WHERE amount IS NULL OR customer.name IS NULL OR orderDate > shipDate"
        );
        assertSemanticSqlReady(
                "third-002",
                "SaleOrder",
                "SELECT orderId, orderDate, shipDate FROM SaleOrder "
                        + "WHERE status = 'shipped' "
                        + "AND orderDate >= START_OF_QUARTER(CURRENT_DATE) "
                        + "AND orderDate < START_OF_NEXT_QUARTER(CURRENT_DATE) "
                        + "AND DATE_DIFF('day', orderDate, shipDate) > 10"
        );
        assertSemanticSqlReady(
                "third-006",
                "ArInvoice",
                "SELECT invoiceId, invoiceDate, dueDate, unpaidAmount FROM ArInvoice "
                        + "WHERE dueDate < invoiceDate OR unpaidAmount < 0"
        );
        assertSemanticSqlReady(
                "third-008",
                "ArInvoice",
                "SELECT customer.name, SUM(unpaidAmount) AS overdueUnpaidAmount FROM ArInvoice "
                        + "WHERE paidAt IS NULL AND unpaidAmount > 0 "
                        + "AND DATE_DIFF('day', dueDate, DATE '2026-05-14') > 45 "
                        + "GROUP BY customer.name"
        );
        assertSemanticSqlReady(
                "third-029",
                "Movie",
                "SELECT movieId, title FROM Movie "
                        + "WHERE cast.actorName IN ('演员 A','演员 B') "
                        + "GROUP BY movieId, title "
                        + "HAVING COUNT(DISTINCT cast.actorName) = 2"
        );
        assertSemanticSqlReady(
                "third-032",
                "Employee",
                "SELECT employeeName, salary, manager.employeeName AS managerName, "
                        + "manager.salary AS managerSalary FROM Employee "
                        + "WHERE salary > manager.salary"
        );
        assertSemanticSqlReady(
                "third-037",
                "Singer",
                "SELECT singerId, singerName FROM Singer WHERE NOT EXISTS albums"
        );
    }

    private void assertSemanticSqlReady(String sampleId, String model, String sql) {
        SemanticQueryRequest request = semanticSql(sql);

        SemanticQueryResponse response = service.validateQuery(model, request, SemanticRequestContext.empty());

        assertEquals("SEMANTIC_SQL", response.getExecution().getRoute(), sampleId);
        assertEquals("PLAN_READY", response.getExecution().getStatus(), sampleId);
        assertEquals(sql, response.getExecution().getSemanticSql(), sampleId);
        assertNotNull(response.getExecution().getAstValidation(), sampleId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> from = (List<Map<String, Object>>) response.getExecution()
                .getAstValidation().get("from");
        assertTrue(from.stream().anyMatch(item -> model.equals(item.get("name"))), sampleId);
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
