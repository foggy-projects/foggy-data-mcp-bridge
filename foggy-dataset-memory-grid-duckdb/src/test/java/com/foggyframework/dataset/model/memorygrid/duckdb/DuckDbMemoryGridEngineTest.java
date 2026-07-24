package com.foggyframework.dataset.model.memorygrid.duckdb;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.memorygrid.GridSqlContractValidator;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridInputBinding;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridRequest;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridResultResolver;
import com.foggyframework.dataset.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuckDbMemoryGridEngineTest {

    @Test
    @DisplayName("DuckDB Grid SQL validates as descriptor-backed optional engine")
    void validatesGridSqlDescriptor() {
        SemanticQueryServiceV3Impl service = service(resolver());
        SemanticQueryResponse response = service.validateQuery("SaleOrder", request("""
                select team, actual_amount
                from actual
                where actual_amount > 100
                order by actual_amount desc
                """, false, 10), SemanticRequestContext.empty());

        Map<String, Object> validation = response.getExecution().getMemoryGridValidation();
        assertEquals("PLAN_READY", response.getExecution().getStatus());
        assertEquals(DuckDbMemoryGridEngine.ENGINE_ID, validation.get("memory_grid_engine"));
        assertEquals(DuckDbMemoryGridEngine.DIALECT_ID, validation.get("memory_grid_dialect"));
        assertEquals(true, validation.get("memory_grid_grid_sql_supported"));
        assertEquals(List.of("actual"), validation.get("grid_sql_aliases"));
    }

    @Test
    @DisplayName("DuckDB Grid SQL executes projection filter order and output limit")
    void executesSingleBindingQuery() {
        SemanticQueryServiceV3Impl service = service(resolver());
        SemanticQueryResponse response = service.queryModel("SaleOrder", request("""
                select team, actual_amount
                from actual
                where actual_amount > 100
                order by actual_amount desc
                """, true, 1), "execute", SemanticRequestContext.empty());

        assertEquals("EXECUTED", response.getExecution().getStatus());
        assertEquals(1, response.getItems().size());
        assertEquals("Team B", response.getItems().get(0).get("team"));
        assertEquals(180.0, (Double) response.getItems().get(0).get("actual_amount"), 0.0001);

        Map<String, Object> summary = response.getExecution().getMemoryGridExecutionSummary();
        assertEquals(1, summary.get("output_row_count"));
        assertEquals(1, summary.get("output_limit"));
        assertEquals(true, summary.get("output_truncated"));
    }

    @Test
    @DisplayName("DuckDB Grid SQL executes joins and numeric derived expressions")
    void executesJoinAndDerivedExpression() {
        SemanticQueryServiceV3Impl service = service(resolver());
        SemanticQueryResponse response = service.queryModel("SaleOrder", request("""
                select a.team, a.actual_amount / t.target_amount as achievement_rate
                from actual a join target t on a.team = t.team
                order by achievement_rate desc
                """, true, 10), "execute", SemanticRequestContext.empty());

        assertEquals(2, response.getItems().size());
        assertEquals("Team B", response.getItems().get(0).get("team"));
        assertEquals(1.2, (Double) response.getItems().get(0).get("achievement_rate"), 0.0001);
    }

    @Test
    @DisplayName("DuckDB Grid SQL executes CTEs over declared aliases")
    void executesCteOverAliases() {
        SemanticQueryServiceV3Impl service = service(resolver());
        SemanticQueryResponse response = service.queryModel("SaleOrder", request("""
                with gap as (
                  select a.team, a.actual_amount - t.target_amount as delta
                  from actual a join target t on a.team = t.team
                )
                select team, delta from gap where delta > 0 order by delta desc
                """, true, 10), "execute", SemanticRequestContext.empty());

        assertEquals(1, response.getItems().size());
        assertEquals("Team B", response.getItems().get(0).get("team"));
        assertEquals(30.0, (Double) response.getItems().get(0).get("delta"), 0.0001);
    }

    @Test
    @DisplayName("DuckDB Grid SQL still fails closed for external table functions")
    void rejectsExternalResourceBeforeDuckDbExecution() {
        SemanticQueryServiceV3Impl service = service(resolver());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.validateQuery(
                "SaleOrder",
                request("select * from read_csv('/tmp/orders.csv')", false, 10),
                SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains(GridSqlContractValidator.EXTERNAL_RESOURCE_DENIED));
    }

    @Test
    @DisplayName("DuckDB Grid SQL requires a configured result resolver")
    void rejectsMissingResultResolver() {
        DuckDbMemoryGridEngine engine = new DuckDbMemoryGridEngine(null);
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                engine.validate(memoryGridRequest(request("select * from actual", false, 10)), SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains(DuckDbMemoryGridEngine.RESULT_HANDLE_NOT_FOUND));
    }

    private SemanticQueryServiceV3Impl service(MemoryGridResultResolver resolver) {
        SemanticQueryServiceV3Impl service = new SemanticQueryServiceV3Impl();
        service.setMemoryGridEngine(new DuckDbMemoryGridEngine(resolver));
        return service;
    }

    private SemanticQueryRequest request(String sql, boolean execute, int outputLimit) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("MEMORY_GRID");
        request.setGridSql(sql);
        request.setMemoryGridBindings(List.of(
                binding("actual", "mgr_actual", 500),
                binding("target", "mgr_target", 500)));
        request.setHints(Map.of("memoryGridExecute", execute, "outputLimit", outputLimit));
        return request;
    }

    private MemoryGridInputBinding binding(String alias, String handle, int rowLimit) {
        return new MemoryGridInputBinding(alias, handle, "DSL_CTE", Map.of("row_limit", rowLimit));
    }

    private MemoryGridRequest memoryGridRequest(SemanticQueryRequest request) {
        return new MemoryGridRequest(
                request.getMemoryGridPlan(),
                request.getGridSql(),
                request.getMemoryGridBindings(),
                request.getHints(),
                request.getExecutablePlan());
    }

    private MemoryGridResultResolver resolver() {
        Map<String, MemoryGridResultResolver.ResolvedResult> results = Map.of(
                "mgr_actual", result("mgr_actual", schema("team", "actual_amount"), List.of(
                        row("team", "Team A", "actual_amount", 120.0),
                        row("team", "Team B", "actual_amount", 180.0))),
                "mgr_target", result("mgr_target", schema("team", "target_amount"), List.of(
                        row("team", "Team A", "target_amount", 150.0),
                        row("team", "Team B", "target_amount", 150.0))));
        return (resultHandle, context) -> results.get(resultHandle);
    }

    private MemoryGridResultResolver.ResolvedResult result(String handle,
                                                           Map<String, MemoryGridResultResolver.Column> schema,
                                                           List<Map<String, Object>> rows) {
        return new MemoryGridResultResolver.ResolvedResult(
                handle,
                "DSL_CTE",
                "default",
                List.of("team"),
                schema,
                rows,
                Map.of("query_hash", "hash-" + handle));
    }

    private Map<String, MemoryGridResultResolver.Column> schema(String dimension, String metric) {
        Map<String, MemoryGridResultResolver.Column> schema = new LinkedHashMap<>();
        schema.put(dimension, new MemoryGridResultResolver.Column(dimension, "string", true, false, true));
        schema.put(metric, new MemoryGridResultResolver.Column(metric, "number", false, true, true));
        return schema;
    }

    private Map<String, Object> row(Object... values) {
        assertFalse(values.length % 2 == 1);
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }
}
