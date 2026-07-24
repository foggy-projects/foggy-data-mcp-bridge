package com.foggyframework.dataset.model.semantic;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import com.foggyframework.dataset.model.memorygrid.bridge.BridgeMemoryGridEngine;
import com.foggyframework.dataset.model.memorygrid.bridge.InMemoryResultHandleStore;
import com.foggyframework.dataset.model.memorygrid.bridge.InMemoryResultStorageAdapter;
import com.foggyframework.dataset.model.memorygrid.bridge.MemoryGridExecutor;
import com.foggyframework.dataset.model.memorygrid.bridge.MemoryGridRegistryResultResolver;
import com.foggyframework.dataset.model.memorygrid.bridge.MemoryGridStoreBackedResultResolver;
import com.foggyframework.dataset.model.memorygrid.bridge.ResultHandleRecord;
import com.foggyframework.dataset.model.memorygrid.bridge.ResultHandleWriter;
import com.foggyframework.dataset.model.memorygrid.bridge.ResultStorageAdapter;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridResultResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryGridAcceptanceSampleTest {

    private final SemanticQueryServiceV3Impl service = new SemanticQueryServiceV3Impl();
    private BridgeMemoryGridEngine bridgeEngine;

    @BeforeEach
    void setUp() {
        bridgeEngine = new BridgeMemoryGridEngine();
        service.setMemoryGridEngine(bridgeEngine);
    }

    private void setResultResolver(MemoryGridResultResolver resolver) {
        bridgeEngine.setResultResolver(resolver);
    }

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
        @SuppressWarnings("unchecked")
        Map<String, Object> alignmentContract = (Map<String, Object>) response.getExecution()
                .getMemoryGridValidation()
                .get("alignment_contract");
        assertEquals("bounded_target_achievement_merge@v1", alignmentContract.get("template"));
        assertEquals(true, alignmentContract.get("version_or_scenario_declared"));
        assertEquals(Map.of("actual", "actual_by_team", "target", "target_by_team"),
                alignmentContract.get("input_roles"));
    }

    @Test
    @DisplayName("third-009 Memory Grid executes inner join with resolver when opt-in")
    void third009ExecutesWithResolverWhenOptIn() {
        setResultResolver(third009Resolver());
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
        @SuppressWarnings("unchecked")
        Map<String, Object> executionAlignmentContract = (Map<String, Object>) response.getExecution()
                .getMemoryGridExecutionSummary()
                .get("alignment_contract");
        assertEquals("bounded_target_achievement_merge@v1", executionAlignmentContract.get("template"));
        @SuppressWarnings("unchecked")
        Map<String, Object> guard = (Map<String, Object>) response.getExecution()
                .getMemoryGridValidation()
                .get("memory_grid_guard");
        assertEquals("bounded-result-handle-v1", guard.get("guard_profile"));
        assertEquals("strict_owner_field_schema_replay", guard.get("handle_replay_mode"));
        assertEquals(false, guard.get("request_rows_allowed"));
        assertNotNull(response.getExecution().getMemoryGridExecutionSummary().get("resolver_audit"));
        List<Map<String, Object>> audit = audit(response);
        assertEquals("hash_dsl_cte_result_actual_by_team_2026_05", audit.get(0).get("query_hash"));
        assertEquals(true, audit.get(0).get("storage_ref_redacted"));
        assertTrue(!audit.get(0).containsKey("storage_ref"));
    }

    @Test
    @DisplayName("third-009 Memory Grid executes with store-backed production handles")
    void third009ExecutesWithStoreBackedProductionHandles() {
        InMemoryResultHandleStore store = new InMemoryResultHandleStore();
        InMemoryResultStorageAdapter storage = new InMemoryResultStorageAdapter();
        ResultHandleWriter writer = new ResultHandleWriter(store, storage);
        SemanticRequestContext context = SemanticRequestContext.ofNamespace("tenant-a");

        String actualHandle = writer.write(writeRequest(
                "DSL_CTE",
                "SaleOrder",
                "salesTeam.name",
                "actualSalesAmount",
                List.of(
                        row("salesTeam.name", "Team A", "actualSalesAmount", 120),
                        row("salesTeam.name", "Team B", "actualSalesAmount", 80)
                )), context);
        String targetHandle = writer.write(writeRequest(
                "DSL",
                "SalesTarget",
                "salesTeam.name",
                "targetSalesAmount",
                List.of(
                        row("salesTeam.name", "Team A", "targetSalesAmount", 100),
                        row("salesTeam.name", "Team C", "targetSalesAmount", 50)
                )), context);

        assertTrue(actualHandle.startsWith("mgr_"));
        assertTrue(targetHandle.startsWith("mgr_"));

        setResultResolver(new MemoryGridStoreBackedResultResolver(store, storage));
        SemanticQueryRequest request = memoryGridPlan(third009Plan(actualHandle, targetHandle));
        request.setHints(Map.of("memoryGridExecute", true));

        SemanticQueryResponse response = service.queryModel("SaleOrder", request, "execute", context);

        assertEquals("EXECUTED", response.getExecution().getStatus());
        assertEquals(1, response.getItems().size());
        assertEquals("Team A", response.getItems().get(0).get("salesTeam.name"));
        assertEquals(1.2, (Double) response.getItems().get(0).get("targetAchievementRate"), 0.0001);
        @SuppressWarnings("unchecked")
        Map<String, Object> guard = (Map<String, Object>) response.getExecution()
                .getMemoryGridValidation()
                .get("memory_grid_guard");
        assertEquals("result_handle_store", guard.get("handle_backend"));
        @SuppressWarnings("unchecked")
        List<String> failClosedCodes = (List<String>) guard.get("fail_closed_codes");
        assertTrue(failClosedCodes.contains(MemoryGridExecutor.RESULT_HANDLE_EXPIRED));
        assertTrue(failClosedCodes.contains(MemoryGridExecutor.AUTH_REPLAY_MISMATCH));
        List<Map<String, Object>> audit = audit(response);
        assertEquals(actualHandle, audit.get(0).get("result_handle"));
        assertEquals(true, audit.get(0).get("storage_ref_redacted"));
        assertTrue(!audit.get(0).containsKey("storage_ref"));
        assertEquals(1, audit.get(0).get("read_count"));
        assertEquals(4, audit.get(0).get("cell_count"));
    }

    @Test
    @DisplayName("Memory Grid store-backed resolver rejects owner context changes")
    void storeBackedResolverRejectsOwnerContextChange() {
        InMemoryResultHandleStore store = new InMemoryResultHandleStore();
        InMemoryResultStorageAdapter storage = new InMemoryResultStorageAdapter();
        ResultHandleWriter writer = new ResultHandleWriter(store, storage);
        SemanticRequestContext writeContext = SemanticRequestContext.of("tenant-a", "Bearer writer");
        String actualHandle = writer.write(writeRequest(
                "DSL_CTE",
                "SaleOrder",
                "salesTeam.name",
                "actualSalesAmount",
                List.of(row("salesTeam.name", "Team A", "actualSalesAmount", 120))
        ), writeContext);
        String targetHandle = writer.write(writeRequest(
                "DSL",
                "SalesTarget",
                "salesTeam.name",
                "targetSalesAmount",
                List.of(row("salesTeam.name", "Team A", "targetSalesAmount", 100))
        ), writeContext);

        setResultResolver(new MemoryGridStoreBackedResultResolver(store, storage));
        SemanticQueryRequest request = memoryGridPlan(third009Plan(actualHandle, targetHandle));
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute",
                        SemanticRequestContext.of("tenant-a", "Bearer reader")));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_AUTH_REPLAY_MISMATCH"));
    }

    @Test
    @DisplayName("Memory Grid store-backed resolver rejects narrowed field access")
    void storeBackedResolverRejectsNarrowedFieldAccess() {
        InMemoryResultHandleStore store = new InMemoryResultHandleStore();
        InMemoryResultStorageAdapter storage = new InMemoryResultStorageAdapter();
        ResultHandleWriter writer = new ResultHandleWriter(store, storage);
        SemanticRequestContext writeContext = SemanticRequestContext.of("tenant-a", null,
                Set.of("salesTeam.name", "actualSalesAmount", "targetSalesAmount"));
        String actualHandle = writer.write(writeRequest(
                "DSL_CTE",
                "SaleOrder",
                "salesTeam.name",
                "actualSalesAmount",
                List.of(row("salesTeam.name", "Team A", "actualSalesAmount", 120))
        ), writeContext);
        String targetHandle = writer.write(writeRequest(
                "DSL",
                "SalesTarget",
                "salesTeam.name",
                "targetSalesAmount",
                List.of(row("salesTeam.name", "Team A", "targetSalesAmount", 100))
        ), writeContext);

        setResultResolver(new MemoryGridStoreBackedResultResolver(store, storage));
        SemanticQueryRequest request = memoryGridPlan(third009Plan(actualHandle, targetHandle));
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute",
                        SemanticRequestContext.of("tenant-a", null, Set.of("salesTeam.name", "targetSalesAmount"))));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_AUTH_REPLAY_MISMATCH"));
    }

    @Test
    @DisplayName("Memory Grid store-backed resolver rejects schema snapshot drift")
    void storeBackedResolverRejectsSchemaSnapshotDrift() {
        InMemoryResultHandleStore store = new InMemoryResultHandleStore();
        InMemoryResultStorageAdapter storage = new InMemoryResultStorageAdapter();
        ResultHandleWriter writer = new ResultHandleWriter(store, storage);
        SemanticRequestContext context = SemanticRequestContext.ofNamespace("tenant-a");
        String actualHandle = writer.write(writeRequest(
                "DSL_CTE",
                "SaleOrder",
                "salesTeam.name",
                "actualSalesAmount",
                List.of(row("salesTeam.name", "Team A", "actualSalesAmount", 120))
        ), context);
        String targetHandle = writer.write(writeRequest(
                "DSL",
                "SalesTarget",
                "salesTeam.name",
                "targetSalesAmount",
                List.of(row("salesTeam.name", "Team A", "targetSalesAmount", 100))
        ), context);
        ResultHandleRecord record = store.find(actualHandle).orElseThrow();
        MemoryGridResultResolver.ResultHandleMetadata metadata = record.result().metadata();
        MemoryGridResultResolver.PolicySnapshot snapshot = metadata.policySnapshot();
        MemoryGridResultResolver.PolicySnapshot driftedSnapshot = new MemoryGridResultResolver.PolicySnapshot(
                snapshot.ownerContextHash(),
                snapshot.fieldAccessHash(),
                "sha256:drifted",
                snapshot.policyVersion(),
                snapshot.schemaVersion()
        );
        store.save(new ResultHandleRecord(record.result().withMetadata(metadataWithSnapshot(metadata, driftedSnapshot))));

        setResultResolver(new MemoryGridStoreBackedResultResolver(store, storage));
        SemanticQueryRequest request = memoryGridPlan(third009Plan(actualHandle, targetHandle));
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", context));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_SCHEMA_DRIFT"));
    }

    @Test
    @DisplayName("Memory Grid store-backed resolver fails closed when handle is invalidated")
    void storeBackedResolverRejectsInvalidatedHandle() {
        InMemoryResultHandleStore store = new InMemoryResultHandleStore();
        InMemoryResultStorageAdapter storage = new InMemoryResultStorageAdapter();
        ResultHandleWriter writer = new ResultHandleWriter(store, storage);
        String actualHandle = writer.write(writeRequest(
                "DSL_CTE",
                "SaleOrder",
                "salesTeam.name",
                "actualSalesAmount",
                List.of(row("salesTeam.name", "Team A", "actualSalesAmount", 120))
        ), SemanticRequestContext.empty());
        String targetHandle = writer.write(writeRequest(
                "DSL",
                "SalesTarget",
                "salesTeam.name",
                "targetSalesAmount",
                List.of(row("salesTeam.name", "Team A", "targetSalesAmount", 100))
        ), SemanticRequestContext.empty());
        store.invalidate(actualHandle);

        setResultResolver(new MemoryGridStoreBackedResultResolver(store, storage));
        SemanticQueryRequest request = memoryGridPlan(third009Plan(actualHandle, targetHandle));
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_HANDLE_EXPIRED"));
    }

    @Test
    @DisplayName("Memory Grid store-backed resolver fails closed when storage rows are unavailable")
    void storeBackedResolverRejectsUnavailableStorage() {
        InMemoryResultHandleStore store = new InMemoryResultHandleStore();
        InMemoryResultStorageAdapter storage = new InMemoryResultStorageAdapter();
        ResultHandleWriter writer = new ResultHandleWriter(store, storage);
        String actualHandle = writer.write(writeRequest(
                "DSL_CTE",
                "SaleOrder",
                "salesTeam.name",
                "actualSalesAmount",
                List.of(row("salesTeam.name", "Team A", "actualSalesAmount", 120))
        ), SemanticRequestContext.empty());
        String targetHandle = writer.write(writeRequest(
                "DSL",
                "SalesTarget",
                "salesTeam.name",
                "targetSalesAmount",
                List.of(row("salesTeam.name", "Team A", "targetSalesAmount", 100))
        ), SemanticRequestContext.empty());
        ResultStorageAdapter unavailableStorage = new ResultStorageAdapter() {
            @Override
            public void write(String storageRef, List<Map<String, Object>> rows) {
            }

            @Override
            public List<Map<String, Object>> read(String storageRef) {
                throw new RuntimeException(MemoryGridExecutor.STORAGE_UNAVAILABLE + ": " + storageRef);
            }
        };

        setResultResolver(new MemoryGridStoreBackedResultResolver(store, unavailableStorage));
        SemanticQueryRequest request = memoryGridPlan(third009Plan(actualHandle, targetHandle));
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_STORAGE_UNAVAILABLE"));
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
        setResultResolver(resolverWithMissingActualMetricSchema());
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
        setResultResolver(third009Resolver());
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
        setResultResolver(resolverWithMismatchedActualSourceRoute());
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
        setResultResolver(third009Resolver(Instant.now().minusSeconds(60), null));
        SemanticQueryRequest request = memoryGridPlan(third009Plan());
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_HANDLE_EXPIRED"));
    }

    @Test
    @DisplayName("Memory Grid production resolver fails closed when namespace mismatches request")
    void executionRequiresNamespaceToMatchContext() {
        setResultResolver(third009Resolver(Instant.now().plusSeconds(3600), "tenant-a"));
        SemanticQueryRequest request = memoryGridPlan(third009Plan());
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", SemanticRequestContext.ofNamespace("tenant-b")));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_NAMESPACE_MISMATCH"));
    }

    @Test
    @DisplayName("Memory Grid production resolver fails closed when default namespace handle is read from named namespace")
    void executionRequiresDefaultHandleToStayInDefaultNamespace() {
        setResultResolver(third009Resolver());
        SemanticQueryRequest request = memoryGridPlan(third009Plan());
        request.setHints(Map.of("memoryGridExecute", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.queryModel("SaleOrder", request, "execute", SemanticRequestContext.ofNamespace("tenant-a")));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_RESULT_NAMESPACE_MISMATCH"));
    }

    @Test
    @DisplayName("Memory Grid production resolver fails closed when derived operand is sensitive")
    void executionRejectsSensitiveDerivedOperand() {
        setResultResolver(resolverWithSensitiveActualMetric());
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
        setResultResolver(resolverWithMissingStorageRef());
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
                                "role", "left",
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
                                "role", "right",
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
                "alignment_contract", Map.of(
                        "template", "bounded_cross_model_metric_merge@v1",
                        "input_roles", Map.of("left", "sales_by_customer", "right", "ar_by_customer"),
                        "match_keys", List.of("customer.name"),
                        "grain", List.of("customer.name"),
                        "formula", "salesAmount - unpaidAmount"
                ),
                "output_limit", 500
        );
    }

    private Map<String, Object> third009Plan() {
        return third009Plan(200, 200, 200);
    }

    private Map<String, Object> third009Plan(String actualHandle, String targetHandle) {
        return third009Plan(200, 200, 200, actualHandle, targetHandle);
    }

    private Map<String, Object> third009Plan(int actualRowLimit, int targetRowLimit, int outputLimit) {
        return third009Plan(actualRowLimit, targetRowLimit, outputLimit,
                "dsl_cte_result_actual_by_team_2026_05",
                "dsl_result_target_by_team_2026_05_approved");
    }

    private Map<String, Object> third009Plan(int actualRowLimit,
                                             int targetRowLimit,
                                             int outputLimit,
                                             String actualHandle,
                                             String targetHandle) {
        return Map.of(
                "inputs", List.of(
                        Map.of(
                                "name", "actual_by_team",
                                "role", "actual",
                                "source_route", "DSL_CTE",
                                "result_handle", actualHandle,
                                "model", "SaleOrder",
                                "grain", List.of("salesTeam.name"),
                                "filters", List.of(Map.of("field", "orderDate", "op", "month", "value", "2026-05")),
                                "metrics", List.of(Map.of("name", "actualSalesAmount", "expr", "sum(amount)")),
                                "row_limit", actualRowLimit,
                                "governed", true
                        ),
                        Map.of(
                                "name", "target_by_team",
                                "role", "target",
                                "source_route", "DSL",
                                "result_handle", targetHandle,
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
                "alignment_contract", Map.of(
                        "template", "bounded_target_achievement_merge@v1",
                        "input_roles", Map.of("actual", "actual_by_team", "target", "target_by_team"),
                        "match_keys", List.of("salesTeam.name"),
                        "grain", List.of("salesTeam.name"),
                        "version", "approved",
                        "formula", "actualSalesAmount / targetSalesAmount"
                ),
                "output_limit", outputLimit
        );
    }

    private ResultHandleWriter.WriteRequest writeRequest(String sourceRoute,
                                                         String model,
                                                         String joinKey,
                                                         String metric,
                                                         List<Map<String, Object>> rows) {
        return new ResultHandleWriter.WriteRequest(
                sourceRoute,
                List.of(model),
                "hash_" + sourceRoute + "_" + model + "_" + metric,
                List.of(joinKey),
                schema(joinKey, metric),
                rows,
                Map.of("model", model),
                200,
                10_000,
                Duration.ofHours(1),
                5
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

    private MemoryGridResultResolver.ResultHandleMetadata metadataWithSnapshot(
            MemoryGridResultResolver.ResultHandleMetadata metadata,
            MemoryGridResultResolver.PolicySnapshot snapshot) {
        return new MemoryGridResultResolver.ResultHandleMetadata(
                metadata.handleId(),
                metadata.namespace(),
                metadata.ownerContextHash(),
                metadata.sourceRoute(),
                metadata.sourceModelRefs(),
                metadata.queryHash(),
                metadata.createdAt(),
                metadata.expiresAt(),
                metadata.invalidatedAt(),
                metadata.rowCount(),
                metadata.rowLimit(),
                metadata.cellCount(),
                metadata.byteSize(),
                metadata.lineage(),
                metadata.storageRef(),
                metadata.readCount(),
                metadata.maxReadCount(),
                snapshot
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
