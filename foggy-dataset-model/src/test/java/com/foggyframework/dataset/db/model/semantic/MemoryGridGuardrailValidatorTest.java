package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryGridGuardrailValidatorTest {

    private final SemanticQueryServiceV3Impl service = new SemanticQueryServiceV3Impl();

    @Test
    @DisplayName("MEMORY_GRID accepts bounded governed inputs")
    void acceptsBoundedGovernedInputs() {
        SemanticQueryRequest request = memoryGridPlan(validPlan(500, 500, "customer.name"));

        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", request, SemanticRequestContext.empty());

        assertEquals("MEMORY_GRID", response.getExecution().getRoute());
        assertEquals("PLAN_READY", response.getExecution().getStatus());
        assertEquals(request.getMemoryGridPlan(), response.getExecution().getMemoryGridPlan());
        assertNotNull(response.getExecution().getMemoryGridValidation());
        assertEquals(500, response.getExecution().getMemoryGridValidation().get("output_limit"));
    }

    @Test
    @DisplayName("MEMORY_GRID rejects missing input row limit")
    void rejectsUnboundedInput() {
        Map<String, Object> plan = validPlan(500, 500, "customer.name");
        @SuppressWarnings("unchecked")
        Map<String, Object> firstInput = (Map<String, Object>) ((List<?>) plan.get("inputs")).get(0);
        firstInput.remove("row_limit");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", memoryGridPlan(plan), SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_UNBOUNDED_INPUT"));
    }

    @Test
    @DisplayName("MEMORY_GRID rejects ungoverned sources")
    void rejectsUngovernedSource() {
        Map<String, Object> plan = validPlan(500, 500, "customer.name");
        @SuppressWarnings("unchecked")
        Map<String, Object> firstInput = (Map<String, Object>) ((List<?>) plan.get("inputs")).get(0);
        firstInput.put("source_route", "PHYSICAL_SQL");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", memoryGridPlan(plan), SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_UNGOVERNED_SOURCE"));
    }

    @Test
    @DisplayName("MEMORY_GRID rejects inputs without governed result handle")
    void rejectsMissingResultHandle() {
        Map<String, Object> plan = validPlan(500, 500, "customer.name");
        @SuppressWarnings("unchecked")
        Map<String, Object> firstInput = (Map<String, Object>) ((List<?>) plan.get("inputs")).get(0);
        firstInput.remove("result_handle");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", memoryGridPlan(plan), SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_UNGOVERNED_SOURCE"));
    }

    @Test
    @DisplayName("MEMORY_GRID rejects missing join key")
    void rejectsMissingJoinKey() {
        Map<String, Object> plan = validPlan(500, 500, "customer.name");
        plan.put("join", Map.of("type", "full_outer", "keys", List.of()));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", memoryGridPlan(plan), SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_GRAIN_MISMATCH"));
    }

    @Test
    @DisplayName("MEMORY_GRID rejects join keys outside input grain")
    void rejectsJoinKeysOutsideInputGrain() {
        Map<String, Object> plan = validPlan(500, 500, "customer.name");
        plan.put("join", Map.of("keys", List.of("salesTeam.name"), "type", "full_outer"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", memoryGridPlan(plan), SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_GRAIN_MISMATCH"));
    }

    @Test
    @DisplayName("MEMORY_GRID rejects row and output limits beyond phase 1")
    void rejectsLimitExceeded() {
        Map<String, Object> plan = validPlan(501, 500, "customer.name");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", memoryGridPlan(plan), SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("MEMORY_GRID rejects denied join or grain fields")
    void rejectsDeniedFieldAccess() {
        Map<String, Object> plan = validPlan(200, 200, "phone");
        SemanticRequestContext context = SemanticRequestContext.of(null, null, Set.of("customer.name"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", memoryGridPlan(plan), context));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_UNGOVERNED_SOURCE"));
    }

    @Test
    @DisplayName("MEMORY_GRID does not enter SQL generation in P0")
    void generateSqlRejectsExecution() {
        SemanticQueryRequest request = memoryGridPlan(validPlan(200, 200, "customer.name"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.generateSql("SaleOrder", request, SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_EXECUTION_NOT_IMPLEMENTED"));
    }

    private SemanticQueryRequest memoryGridPlan(Map<String, Object> plan) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("MEMORY_GRID");
        request.setMemoryGridPlan(plan);
        return request;
    }

    private Map<String, Object> validPlan(int rowLimit, int outputLimit, String key) {
        return new java.util.LinkedHashMap<>(Map.of(
                "inputs", List.of(
                        new java.util.LinkedHashMap<>(Map.of(
                                "name", "sales_by_customer",
                                "source_route", "DSL_CTE",
                                "model", "SaleOrder",
                                "grain", List.of(key),
                                "metrics", List.of(Map.of("name", "salesAmount", "expr", "sum(amount)")),
                                "row_limit", rowLimit,
                                "result_handle", "dsl_cte_result_sales_by_customer",
                                "governed", true
                        )),
                        new java.util.LinkedHashMap<>(Map.of(
                                "name", "ar_by_customer",
                                "source_route", "DSL_CTE",
                                "model", "ArInvoice",
                                "grain", List.of(key),
                                "metrics", List.of(Map.of("name", "unpaidAmount", "expr", "sum(unpaidAmount)")),
                                "row_limit", rowLimit,
                                "result_handle", "dsl_cte_result_ar_by_customer",
                                "governed", true
                        ))
                ),
                "join", Map.of("keys", List.of(key), "type", "full_outer"),
                "derived", List.of(Map.of("name", "salesArGap", "expr", "salesAmount - unpaidAmount")),
                "output_limit", outputLimit
        ));
    }
}
