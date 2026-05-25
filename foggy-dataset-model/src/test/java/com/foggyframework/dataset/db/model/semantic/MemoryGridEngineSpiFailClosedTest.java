package com.foggyframework.dataset.db.model.semantic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.memorygrid.GridSqlContractValidator;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridDialectDescriptor;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridEngine;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridExecutionResult;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridInputBinding;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridRequest;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridValidation;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryGridEngineSpiFailClosedTest {

    private final SemanticQueryServiceV3Impl service = new SemanticQueryServiceV3Impl();

    @Test
    @DisplayName("MEMORY_GRID route requires an explicitly configured engine")
    void memoryGridRouteRequiresConfiguredEngine() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("MEMORY_GRID");
        request.setMemoryGridPlan(Map.of(
                "inputs", List.of(Map.of(
                        "handle", "mgr_actual_sales",
                        "governed", true,
                        "row_limit", 100
                )),
                "output", Map.of("limit", 100)
        ));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", request, SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_ENGINE_NOT_CONFIGURED"));
    }

    @Test
    @DisplayName("grid_sql route requires an explicitly configured engine")
    void gridSqlRouteRequiresConfiguredEngine() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", gridSqlRequest("select * from sales"), SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_ENGINE_NOT_CONFIGURED"));
    }

    @Test
    @DisplayName("grid_sql is rejected before engine validation when descriptor does not support it")
    void gridSqlUnsupportedDescriptorDoesNotEnterEngineValidation() {
        CountingMemoryGridEngine engine = new CountingMemoryGridEngine(false);
        service.setMemoryGridEngine(engine);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", gridSqlRequest("select * from sales"), SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_GRID_SQL_NOT_SUPPORTED"));
        assertEquals(0, engine.validateCalls);
        assertEquals(0, engine.executeCalls);
    }

    @Test
    @DisplayName("grid_sql shared validator runs before engine validation")
    void gridSqlContractValidationRunsBeforeEngineValidation() {
        CountingMemoryGridEngine engine = new CountingMemoryGridEngine(true);
        service.setMemoryGridEngine(engine);

        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder",
                gridSqlRequest("select sales.customer_id, sales.amount from sales order by sales.amount desc"),
                SemanticRequestContext.empty());

        Map<String, Object> evidence = response.getExecution().getMemoryGridValidation();
        assertEquals(true, evidence.get("grid_sql_supported"));
        assertEquals("foggy-grid-sql-v1", evidence.get("grid_sql_dialect"));
        assertEquals("memory-grid-test", evidence.get("memory_grid_engine"));
        assertEquals(true, evidence.get("engine_validate_called"));
        assertEquals(1, engine.validateCalls);
        assertEquals(0, engine.executeCalls);
    }

    @Test
    @DisplayName("invalid grid_sql fails before engine validation")
    void invalidGridSqlFailsBeforeEngineValidation() {
        CountingMemoryGridEngine engine = new CountingMemoryGridEngine(true);
        service.setMemoryGridEngine(engine);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", gridSqlRequest("select * from physical.sales"),
                        SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains(GridSqlContractValidator.RESOURCE_DENIED));
        assertEquals(0, engine.validateCalls);
        assertEquals(0, engine.executeCalls);
    }

    @Test
    @DisplayName("valid grid_sql execution path preflights then delegates to engine")
    void validGridSqlExecutionPreflightsThenDelegatesToEngine() {
        CountingMemoryGridEngine engine = new CountingMemoryGridEngine(true);
        service.setMemoryGridEngine(engine);
        SemanticQueryRequest request = gridSqlRequest("select * from sales");
        request.setHints(Map.of("memoryGridExecute", true, "outputLimit", 50));

        SemanticQueryResponse response = service.queryModel("SaleOrder", request, null, SemanticRequestContext.empty());

        assertEquals(1, response.getItems().size());
        assertEquals(1, engine.validateCalls);
        assertEquals(1, engine.executeCalls);
        assertEquals(true, response.getExecution().getMemoryGridValidation().get("grid_sql_supported"));
        assertEquals(true, response.getExecution().getMemoryGridValidation().get("engine_execute_called"));
    }

    @Test
    @DisplayName("grid_sql request JSON maps external binding field names")
    void gridSqlRequestJsonMapsExternalBindingFieldNames() throws Exception {
        SemanticQueryRequest request = new ObjectMapper().readValue("""
                {
                  "route": "MEMORY_GRID",
                  "grid_sql": "select * from sales",
                  "bindings": [
                    {
                      "alias": "sales",
                      "result_handle": "mgr_sales",
                      "source_route": "DSL",
                      "metadata": {"row_limit": 100}
                    }
                  ]
                }
                """, SemanticQueryRequest.class);

        assertEquals("select * from sales", request.getGridSql());
        assertEquals("mgr_sales", request.getMemoryGridBindings().get(0).resultHandle());
        assertEquals("DSL", request.getMemoryGridBindings().get(0).sourceRoute());
    }

    private SemanticQueryRequest gridSqlRequest(String sql) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("MEMORY_GRID");
        request.setGridSql(sql);
        request.setMemoryGridBindings(List.of(new MemoryGridInputBinding(
                "sales",
                "mgr_sales",
                "DSL_CTE",
                Map.of("row_limit", 100, "model", "SaleOrder"))));
        return request;
    }

    private static class CountingMemoryGridEngine implements MemoryGridEngine {

        private final MemoryGridDialectDescriptor dialect;
        private int validateCalls;
        private int executeCalls;

        CountingMemoryGridEngine(boolean gridSqlSupported) {
            this.dialect = new MemoryGridDialectDescriptor(
                    "memory-grid-test",
                    "foggy-grid-sql-v1",
                    true,
                    gridSqlSupported,
                    List.of("test grid_sql"),
                    List.of());
        }

        @Override
        public MemoryGridDialectDescriptor dialect() {
            return dialect;
        }

        @Override
        public MemoryGridValidation validate(MemoryGridRequest request, SemanticRequestContext context) {
            validateCalls++;
            return new MemoryGridValidation(Map.of("engine_validate_called", true));
        }

        @Override
        public MemoryGridExecutionResult execute(MemoryGridRequest request, SemanticRequestContext context) {
            executeCalls++;
            return new MemoryGridExecutionResult(
                    List.of(Map.of("customer_id", "C001", "amount", 100)),
                    Map.of("engine_execute_called", true),
                    Map.of("row_count", 1));
        }
    }
}
