package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import com.foggyframework.dataset.db.model.semantic.support.MemoryGridRegistryResultResolver;
import com.foggyframework.dataset.db.model.semantic.support.MemoryGridResultResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryGridAcceptanceSampleTest {

    private final SemanticQueryServiceV3Impl service = new SemanticQueryServiceV3Impl();

    @Test
    @DisplayName("third-011 Memory Grid sample accepts two governed result handles")
    void third011AcceptsGovernedResultHandles() {
        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", memoryGridPlan(third011Plan()), SemanticRequestContext.empty());

        assertEquals("MEMORY_GRID", response.getExecution().getRoute());
        assertEquals("PLAN_READY", response.getExecution().getStatus());
        assertNotNull(response.getExecution().getMemoryGridValidation());
        assertEquals(500, response.getExecution().getMemoryGridValidation().get("output_limit"));
        assertEquals("BRIDGE_DEFERRED", response.getExecution().getMemoryGridValidation()
                .get("memory_grid_bridge_status"));
    }

    @Test
    @DisplayName("third-009 Memory Grid sample accepts actual and target governed handles")
    void third009AcceptsActualAndTargetHandles() {
        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", memoryGridPlan(third009Plan()), SemanticRequestContext.empty());

        assertEquals("MEMORY_GRID", response.getExecution().getRoute());
        assertEquals("PLAN_READY", response.getExecution().getStatus());
        assertNotNull(response.getExecution().getMemoryGridValidation());
        assertEquals(200, response.getExecution().getMemoryGridValidation().get("output_limit"));
        assertEquals("BRIDGE_READY", response.getExecution().getMemoryGridValidation()
                .get("memory_grid_bridge_status"));
    }

    @Test
    @DisplayName("third-009 Memory Grid executes inner join with resolver when opt-in")
    void third009ExecutesWithResolverWhenOptIn() {
        ReflectionTestUtils.setField(service, "memoryGridResultResolver", third009Resolver());
        SemanticQueryRequest request = memoryGridPlan(third009Plan());
        request.setHints(Map.of("memoryGridExecute", true));

        SemanticQueryResponse response = service.queryModel(
                "SaleOrder", request, "execute", SemanticRequestContext.empty());

        assertEquals("EXECUTED", response.getExecution().getStatus());
        assertEquals(1, response.getItems().size());
        assertEquals("Team A", response.getItems().get(0).get("salesTeam.name"));
        assertEquals(1.2, (Double) response.getItems().get(0).get("targetAchievementRate"), 0.0001);
        assertEquals("BRIDGE_READY", response.getExecution().getMemoryGridExecutionSummary()
                .get("memory_grid_bridge_status"));
        assertNotNull(response.getExecution().getMemoryGridExecutionSummary().get("resolver_audit"));
        List<Map<String, Object>> audit = audit(response);
        assertEquals("hash_dsl_cte_result_actual_by_team_2026_05", audit.get(0).get("query_hash"));
        assertEquals("memory://result/dsl_cte_result_actual_by_team_2026_05", audit.get(0).get("storage_ref"));
    }

    @Test
    @DisplayName("Memory Grid opt-in execution fails closed without resolver")
    void executionRequiresResolver() {
        SemanticQueryRequest request = memoryGridPlan(third009Plan());
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_HANDLE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Memory Grid execution fails closed when resolver schema misses declared metric")
    void executionRequiresDeclaredMetricSchema() {
        ReflectionTestUtils.setField(service, "memoryGridResultResolver", resolverWithMissingActualMetricSchema());
        SemanticQueryRequest request = memoryGridPlan(third009Plan());
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_SCHEMA_MISMATCH"));
        assertTrue(ex.getMessage().contains("actualSalesAmount"));
    }

    @Test
    @DisplayName("Memory Grid execution fails closed when resolver rows exceed declared limit")
    void executionRequiresResolverRowsWithinDeclaredLimit() {
        ReflectionTestUtils.setField(service, "memoryGridResultResolver", third009Resolver());
        SemanticQueryRequest request = memoryGridPlan(third009Plan(1, 200, 200));
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_GOVERNANCE_MISMATCH"));
        assertTrue(ex.getMessage().contains("row count exceeds declared row_limit"));
    }

    @Test
    @DisplayName("Memory Grid execution fails closed when resolver source route mismatches plan")
    void executionRequiresResolverSourceRouteToMatchPlan() {
        ReflectionTestUtils.setField(service, "memoryGridResultResolver", resolverWithMismatchedActualSourceRoute());
        SemanticQueryRequest request = memoryGridPlan(third009Plan());
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_SOURCE_ROUTE_MISMATCH"));
        assertTrue(ex.getMessage().contains("mismatched source_route"));
    }

    @Test
    @DisplayName("Memory Grid production resolver fails closed when result handle expired")
    void executionRequiresUnexpiredResultHandle() {
        ReflectionTestUtils.setField(service, "memoryGridResultResolver",
                third009Resolver(Instant.now().minusSeconds(60), null));
        SemanticQueryRequest request = memoryGridPlan(third009Plan());
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_HANDLE_EXPIRED"));
    }

    @Test
    @DisplayName("Memory Grid production resolver fails closed when namespace mismatches request")
    void executionRequiresNamespaceToMatchContext() {
        ReflectionTestUtils.setField(service, "memoryGridResultResolver",
                third009Resolver(Instant.now().plusSeconds(3600), "tenant-a"));
        SemanticQueryRequest request = memoryGridPlan(third009Plan());
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", SemanticRequestContext.ofNamespace("tenant-b")));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_NAMESPACE_MISMATCH"));
    }

    @Test
    @DisplayName("Memory Grid production resolver fails closed when default namespace handle is read from named namespace")
    void executionRequiresDefaultHandleToStayInDefaultNamespace() {
        ReflectionTestUtils.setField(service, "memoryGridResultResolver", third009Resolver());
        SemanticQueryRequest request = memoryGridPlan(third009Plan());
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", SemanticRequestContext.ofNamespace("tenant-a")));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_NAMESPACE_MISMATCH"));
    }

    @Test
    @DisplayName("Memory Grid production resolver fails closed when derived operand is sensitive")
    void executionRejectsSensitiveDerivedOperand() {
        ReflectionTestUtils.setField(service, "memoryGridResultResolver", resolverWithSensitiveActualMetric());
        SemanticQueryRequest request = memoryGridPlan(third009Plan());
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_SCHEMA_MISMATCH")
                || ex.getMessage().contains("MEMORY_GRID_RESULT_GOVERNANCE_MISMATCH"));
        assertTrue(ex.getMessage().contains("actualSalesAmount"));
    }

    @Test
    @DisplayName("Memory Grid production resolver fails closed when metadata storage ref is missing")
    void executionRequiresStorageRefInMetadata() {
        ReflectionTestUtils.setField(service, "memoryGridResultResolver", resolverWithMissingStorageRef());
        SemanticQueryRequest request = memoryGridPlan(third009Plan());
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_GOVERNANCE_MISMATCH"));
        assertTrue(ex.getMessage().contains("storage_ref"));
    }

    private SemanticQueryRequest memoryGridPlan(Map<String, Object> plan) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("MEMORY_GRID");
        request.setMemoryGridPlan(plan);
        return request;
    }

    private Map<String, Object> third011Plan() {
        return Map.of(
                "inputs", List.of(
                        Map.of(
                                "name", "sales_by_customer",
                                "source_route", "DSL_CTE",
                                "result_handle", "dsl_cte_result_sales_by_customer_90d",
                                "model", "SaleOrder",
                                "grain", List.of("customer.name"),
                                "filters", List.of(Map.of("field", "orderDate", "op", "last_n_days", "value", 90)),
                                "metrics", List.of(Map.of("name", "salesAmount", "expr", "sum(amount)")),
                                "row_limit", 500,
                                "governed", true
                        ),
                        Map.of(
                                "name", "ar_by_customer",
                                "source_route", "DSL_CTE",
                                "result_handle", "dsl_cte_result_ar_by_customer_90d",
                                "model", "ArInvoice",
                                "grain", List.of("customer.name"),
                                "filters", List.of(Map.of("field", "invoiceDate", "op", "last_n_days", "value", 90)),
                                "metrics", List.of(Map.of("name", "unpaidAmount", "expr", "sum(unpaidAmount)")),
                                "row_limit", 500,
                                "governed", true
                        )
                ),
                "join", Map.of("keys", List.of("customer.name"), "type", "full_outer"),
                "derived", List.of(Map.of("name", "salesArGap", "expr", "salesAmount - unpaidAmount")),
                "output_limit", 500
        );
    }

    private Map<String, Object> third009Plan() {
        return third009Plan(200, 200, 200);
    }

    private Map<String, Object> third009Plan(int actualRowLimit, int targetRowLimit, int outputLimit) {
        return Map.of(
                "inputs", List.of(
                        Map.of(
                                "name", "actual_by_team",
                                "source_route", "DSL_CTE",
                                "result_handle", "dsl_cte_result_actual_by_team_2026_05",
                                "model", "SaleOrder",
                                "grain", List.of("salesTeam.name"),
                                "filters", List.of(Map.of("field", "orderDate", "op", "month", "value", "2026-05")),
                                "metrics", List.of(Map.of("name", "actualSalesAmount", "expr", "sum(amount)")),
                                "row_limit", actualRowLimit,
                                "governed", true
                        ),
                        Map.of(
                                "name", "target_by_team",
                                "source_route", "DSL",
                                "result_handle", "dsl_result_target_by_team_2026_05_approved",
                                "model", "SalesTarget",
                                "grain", List.of("salesTeam.name"),
                                "filters", List.of(
                                        Map.of("field", "targetMonth", "op", "=", "value", "2026-05"),
                                        Map.of("field", "targetVersion", "op", "=", "value", "approved")
                                ),
                                "metrics", List.of(Map.of("name", "targetSalesAmount", "expr", "sum(targetSalesAmount)")),
                                "row_limit", targetRowLimit,
                                "governed", true
                        )
                ),
                "join", Map.of("keys", List.of("salesTeam.name"), "type", "inner"),
                "derived", List.of(Map.of("name", "targetAchievementRate", "expr", "actualSalesAmount / targetSalesAmount")),
                "output_limit", outputLimit
        );
    }

    private MemoryGridResultResolver third009Resolver() {
        return third009Resolver(Instant.now().plusSeconds(3600), null);
    }

    private MemoryGridResultResolver third009Resolver(Instant expiresAt, String namespace) {
        return new MemoryGridRegistryResultResolver()
                .register(resolvedResult(
                        "dsl_cte_result_actual_by_team_2026_05",
                        "DSL_CTE",
                        namespace,
                        "SaleOrder",
                        "salesTeam.name",
                        "actualSalesAmount",
                        List.of(
                                row("salesTeam.name", "Team A", "actualSalesAmount", 120),
                                row("salesTeam.name", "Team B", "actualSalesAmount", 80)
                        ),
                        expiresAt))
                .register(resolvedResult(
                        "dsl_result_target_by_team_2026_05_approved",
                        "DSL",
                        namespace,
                        "SalesTarget",
                        "salesTeam.name",
                        "targetSalesAmount",
                        List.of(
                                row("salesTeam.name", "Team A", "targetSalesAmount", 100),
                                row("salesTeam.name", "Team C", "targetSalesAmount", 50)
                        ),
                        expiresAt));
    }

    private MemoryGridResultResolver resolverWithMissingActualMetricSchema() {
        return (resultHandle, context) -> {
            if ("dsl_cte_result_actual_by_team_2026_05".equals(resultHandle)) {
                Map<String, MemoryGridResultResolver.Column> schema = new LinkedHashMap<>();
                schema.put("salesTeam.name",
                        new MemoryGridResultResolver.Column("salesTeam.name", "string", true, false, true));
                return new MemoryGridResultResolver.ResolvedResult(
                        resultHandle,
                        "DSL_CTE",
                        null,
                        List.of("salesTeam.name"),
                        schema,
                        List.of(row("salesTeam.name", "Team A", "actualSalesAmount", 120)),
                        Map.of("model", "SaleOrder")
                );
            }
            return third009Resolver().resolve(resultHandle, context);
        };
    }

    private MemoryGridResultResolver resolverWithMismatchedActualSourceRoute() {
        return (resultHandle, context) -> {
            if ("dsl_cte_result_actual_by_team_2026_05".equals(resultHandle)) {
                return new MemoryGridResultResolver.ResolvedResult(
                        resultHandle,
                        "DSL",
                        null,
                        List.of("salesTeam.name"),
                        schema("salesTeam.name", "actualSalesAmount"),
                        List.of(row("salesTeam.name", "Team A", "actualSalesAmount", 120)),
                        Map.of("model", "SaleOrder")
                );
            }
            return third009Resolver().resolve(resultHandle, context);
        };
    }

    private MemoryGridResultResolver resolverWithMissingStorageRef() {
        return new MemoryGridRegistryResultResolver()
                .register(resolvedResult(
                        "dsl_cte_result_actual_by_team_2026_05",
                        "DSL_CTE",
                        null,
                        "SaleOrder",
                        "salesTeam.name",
                        "actualSalesAmount",
                        List.of(row("salesTeam.name", "Team A", "actualSalesAmount", 120)),
                        Instant.now().plusSeconds(3600),
                        null))
                .register(resolvedResult(
                        "dsl_result_target_by_team_2026_05_approved",
                        "DSL",
                        null,
                        "SalesTarget",
                        "salesTeam.name",
                        "targetSalesAmount",
                        List.of(row("salesTeam.name", "Team A", "targetSalesAmount", 100)),
                        Instant.now().plusSeconds(3600)));
    }

    private MemoryGridResultResolver resolverWithSensitiveActualMetric() {
        return (resultHandle, context) -> {
            if ("dsl_cte_result_actual_by_team_2026_05".equals(resultHandle)) {
                Map<String, MemoryGridResultResolver.Column> schema = schema("salesTeam.name", "actualSalesAmount");
                schema.put("actualSalesAmount",
                        new MemoryGridResultResolver.Column("actualSalesAmount", "number", false, true, true, true));
                return new MemoryGridResultResolver.ResolvedResult(
                        resultHandle,
                        "DSL_CTE",
                        null,
                        List.of("salesTeam.name"),
                        schema,
                        List.of(row("salesTeam.name", "Team A", "actualSalesAmount", 120)),
                        Map.of("model", "SaleOrder")
                );
            }
            return third009Resolver().resolve(resultHandle, context);
        };
    }

    private MemoryGridResultResolver.ResolvedResult resolvedResult(String handle,
                                                                   String sourceRoute,
                                                                   String namespace,
                                                                   String model,
                                                                   String joinKey,
                                                                   String metric,
                                                                   List<Map<String, Object>> rows,
                                                                   Instant expiresAt) {
        return resolvedResult(handle, sourceRoute, namespace, model, joinKey, metric, rows, expiresAt,
                "memory://result/" + handle);
    }

    private MemoryGridResultResolver.ResolvedResult resolvedResult(String handle,
                                                                   String sourceRoute,
                                                                   String namespace,
                                                                   String model,
                                                                   String joinKey,
                                                                   String metric,
                                                                   List<Map<String, Object>> rows,
                                                                   Instant expiresAt,
                                                                   String storageRef) {
        return new MemoryGridResultResolver.ResolvedResult(
                handle,
                sourceRoute,
                namespace,
                List.of(joinKey),
                schema(joinKey, metric),
                rows,
                Map.of("model", model),
                new MemoryGridResultResolver.ResultHandleMetadata(
                        handle,
                        namespace,
                        sourceRoute,
                        List.of(model),
                        "hash_" + handle,
                        Instant.now().minusSeconds(60),
                        expiresAt,
                        rows.size(),
                        200,
                        Map.of("model", model),
                        storageRef
                )
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> audit(SemanticQueryResponse response) {
        return (List<Map<String, Object>>) response.getExecution()
                .getMemoryGridExecutionSummary()
                .get("resolver_audit");
    }

    private Map<String, MemoryGridResultResolver.Column> schema(String joinKey, String metric) {
        Map<String, MemoryGridResultResolver.Column> schema = new LinkedHashMap<>();
        schema.put(joinKey, new MemoryGridResultResolver.Column(joinKey, "string", true, false, true));
        schema.put(metric, new MemoryGridResultResolver.Column(metric, "number", false, true, true));
        return schema;
    }

    private Map<String, Object> row(Object... entries) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            row.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return row;
    }
}
