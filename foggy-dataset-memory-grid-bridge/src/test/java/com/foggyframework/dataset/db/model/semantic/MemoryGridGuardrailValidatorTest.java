package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import com.foggyframework.dataset.db.model.memorygrid.bridge.BridgeMemoryGridEngine;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridRequest;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void setUp() {
        service.setMemoryGridEngine(new BridgeMemoryGridEngine());
    }

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
        @SuppressWarnings("unchecked")
        Map<String, Object> guard = (Map<String, Object>) response.getExecution()
                .getMemoryGridValidation()
                .get("memory_grid_guard");
        assertEquals("bounded-result-handle-v1", guard.get("guard_profile"));
        assertEquals("result_handle_store", guard.get("handle_backend"));
        assertEquals(false, guard.get("grid_sql_supported"));
        @SuppressWarnings("unchecked")
        Map<String, Object> limits = (Map<String, Object>) guard.get("limits");
        assertEquals(500, limits.get("max_input_row_limit"));
        assertEquals(3, limits.get("max_input_count"));
        assertEquals(1000, limits.get("max_output_limit"));
        assertEquals(50_000, limits.get("max_cell_count"));
        @SuppressWarnings("unchecked")
        Map<String, Object> alignmentContract = (Map<String, Object>) response.getExecution()
                .getMemoryGridValidation()
                .get("alignment_contract");
        assertEquals("bounded_target_achievement_merge@v1", alignmentContract.get("template"));
        assertEquals(true, alignmentContract.get("version_or_scenario_declared"));
        assertEquals(Map.of("actual", "actual_by_customer", "target", "target_by_customer"),
                alignmentContract.get("input_roles"));
        @SuppressWarnings("unchecked")
        Map<String, Object> crossModelContract = (Map<String, Object>) guard.get("cross_model_alignment_contract");
        assertEquals(true, crossModelContract.get("required_for_distinct_input_models"));
    }

    @Test
    @DisplayName("MEMORY_GRID rejects cross-model plans without alignment contract")
    void rejectsMissingCrossModelAlignmentContract() {
        Map<String, Object> plan = validPlan(500, 500, "customer.name");
        plan.remove("alignment_contract");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", memoryGridPlan(plan), SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_ALIGNMENT_CONTRACT_MISSING"));
    }

    @Test
    @DisplayName("MEMORY_GRID rejects cross-model alignment without target version or forecast scenario")
    void rejectsCrossModelAlignmentWithoutVersionOrScenario() {
        Map<String, Object> plan = validPlan(500, 500, "customer.name");
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) plan.get("alignment_contract");
        contract.remove("version");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", memoryGridPlan(plan), SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_ALIGNMENT_CONTRACT_MISSING"));
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
    @DisplayName("Bridge engine rejects grid_sql until a Grid SQL contract is implemented")
    void rejectsGridSqlUntilContractIsImplemented() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                new BridgeMemoryGridEngine().validate(
                        new MemoryGridRequest(Map.of(), "select * from actual", List.of(), Map.of(), null),
                        SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_GRID_SQL_NOT_SUPPORTED"));
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
                                "name", "actual_by_customer",
                                "role", "actual",
                                "source_route", "DSL_CTE",
                                "model", "SaleOrder",
                                "grain", List.of(key),
                                "metrics", List.of(Map.of("name", "actualSalesAmount", "expr", "sum(amount)")),
                                "row_limit", rowLimit,
                                "result_handle", "dsl_cte_result_actual_by_customer",
                                "governed", true
                        )),
                        new java.util.LinkedHashMap<>(Map.of(
                                "name", "target_by_customer",
                                "role", "target",
                                "source_route", "DSL_CTE",
                                "model", "SalesTarget",
                                "grain", List.of(key),
                                "filters", List.of(Map.of("field", "targetVersion", "op", "=", "value", "approved")),
                                "metrics", List.of(Map.of("name", "targetSalesAmount", "expr", "sum(targetSalesAmount)")),
                                "row_limit", rowLimit,
                                "result_handle", "dsl_cte_result_target_by_customer",
                                "governed", true
                        ))
                ),
                "join", Map.of("keys", List.of(key), "type", "full_outer"),
                "derived", List.of(Map.of("name", "targetGap", "expr", "actualSalesAmount - targetSalesAmount")),
                "alignment_contract", new java.util.LinkedHashMap<>(Map.of(
                        "template", "bounded_target_achievement_merge@v1",
                        "input_roles", Map.of("actual", "actual_by_customer", "target", "target_by_customer"),
                        "match_keys", List.of(key),
                        "grain", List.of(key),
                        "version", "approved",
                        "formula", "actualSalesAmount - targetSalesAmount"
                )),
                "output_limit", outputLimit
        ));
    }
}
