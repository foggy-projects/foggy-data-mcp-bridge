package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.memorygrid.bridge.InMemoryResultHandleStore;
import com.foggyframework.dataset.db.model.memorygrid.bridge.InMemoryResultStorageAdapter;
import com.foggyframework.dataset.db.model.memorygrid.bridge.MemoryGridExecutor;
import com.foggyframework.dataset.db.model.memorygrid.bridge.MemoryGridGuardrailValidator;
import com.foggyframework.dataset.db.model.memorygrid.bridge.ResultHandleLifecycleManager;
import com.foggyframework.dataset.db.model.memorygrid.bridge.ResultHandleRecord;
import com.foggyframework.dataset.db.model.memorygrid.bridge.ResultHandleWriter;
import com.foggyframework.dataset.db.model.memorygrid.bridge.ResultStorageAdapter;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridResultResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryGridHandleLifecycleTest {

    @Test
    @DisplayName("Memory Grid lifecycle cleanup deletes expired and invalidated handles without exposing rows")
    void cleanupExpiredAndInvalidatedHandlesDeletesMetadataAndStorageRows() {
        InMemoryResultHandleStore store = new InMemoryResultHandleStore();
        InMemoryResultStorageAdapter storage = new InMemoryResultStorageAdapter();
        ResultHandleWriter writer = new ResultHandleWriter(store, storage);
        ResultHandleLifecycleManager lifecycle = new ResultHandleLifecycleManager(store, storage);
        SemanticRequestContext context = SemanticRequestContext.ofNamespace("tenant-a");
        Instant now = Instant.now();

        String activeHandle = writer.write(writeRequest("SaleOrder", "activeSalesAmount"), context);
        String expiredHandle = writer.write(writeRequest("SaleOrder", "expiredSalesAmount"), context);
        String invalidatedHandle = writer.write(writeRequest("SalesTarget", "targetSalesAmount"), context);

        ResultHandleRecord expiredRecord = store.find(expiredHandle).orElseThrow();
        MemoryGridResultResolver.ResultHandleMetadata expiredMetadata = expiredRecord.result()
                .metadata()
                .withExpiresAt(now.minusSeconds(60));
        store.save(new ResultHandleRecord(expiredRecord.result().withMetadata(expiredMetadata)));
        String expiredStorageRef = expiredMetadata.storageRef();

        store.invalidate(invalidatedHandle);
        String invalidatedStorageRef = store.find(invalidatedHandle).orElseThrow()
                .result()
                .metadata()
                .storageRef();
        String activeStorageRef = store.find(activeHandle).orElseThrow()
                .result()
                .metadata()
                .storageRef();

        Map<String, Object> before = lifecycle.inspect(now);
        assertEquals(3, before.get("total_handle_count"));
        assertEquals(1, before.get("active_handle_count"));
        assertEquals(1, before.get("expired_handle_count"));
        assertEquals(1, before.get("invalidated_handle_count"));
        assertEquals(false, before.get("rows_exposed"));
        assertFalse(before.containsKey("storage_ref"));

        ResultHandleLifecycleManager.CleanupReport report = lifecycle.cleanupExpired(now);
        Map<String, Object> evidence = report.toEvidence();
        assertEquals(3, evidence.get("scanned_handle_count"));
        assertEquals(2, evidence.get("deleted_handle_count"));
        assertEquals(2, evidence.get("deleted_storage_ref_count"));
        assertEquals(List.of(), evidence.get("failure_codes"));
        assertEquals(true, evidence.get("storage_refs_redacted"));
        assertEquals(false, evidence.get("rows_exposed"));
        assertFalse(evidence.containsKey("storage_ref"));
        assertFalse(evidence.containsKey("rows"));

        assertTrue(store.find(activeHandle).isPresent());
        assertTrue(store.find(expiredHandle).isEmpty());
        assertTrue(store.find(invalidatedHandle).isEmpty());
        assertEquals(1, storage.read(activeStorageRef).size());
        assertStorageUnavailable(storage, expiredStorageRef);
        assertStorageUnavailable(storage, invalidatedStorageRef);
    }

    @Test
    @DisplayName("Memory Grid lifecycle keeps metadata when storage deletion is not confirmed")
    void cleanupKeepsMetadataWhenStorageDeleteIsNotConfirmed() {
        InMemoryResultHandleStore store = new InMemoryResultHandleStore();
        NonDeletingStorageAdapter storage = new NonDeletingStorageAdapter();
        ResultHandleWriter writer = new ResultHandleWriter(store, storage);
        ResultHandleLifecycleManager lifecycle = new ResultHandleLifecycleManager(store, storage);
        Instant now = Instant.now();
        String handle = writer.write(writeRequest("SaleOrder", "expiredSalesAmount"), SemanticRequestContext.empty());
        ResultHandleRecord record = store.find(handle).orElseThrow();
        store.save(new ResultHandleRecord(record.result().withMetadata(
                record.result().metadata().withExpiresAt(now.minusSeconds(60)))));

        ResultHandleLifecycleManager.CleanupReport report = lifecycle.cleanupExpired(now);

        assertEquals(0, report.deletedHandleCount());
        assertEquals(0, report.deletedStorageRefCount());
        assertTrue(report.failureCodes().contains(MemoryGridExecutor.STORAGE_UNAVAILABLE));
        assertTrue(store.find(handle).isPresent());
    }

    @Test
    @DisplayName("Memory Grid production guard declares handle lifecycle support")
    void productionGuardDeclaresHandleLifecycleSupport() {
        @SuppressWarnings("unchecked")
        Map<String, Object> lifecycle = (Map<String, Object>) MemoryGridGuardrailValidator
                .productionGuardDescriptor()
                .get("handle_lifecycle");

        assertEquals(true, lifecycle.get("ttl_enforced"));
        assertEquals(true, lifecycle.get("invalidation_supported"));
        assertEquals(true, lifecycle.get("read_count_enforced"));
        assertEquals(true, lifecycle.get("cleanup_supported"));
        assertEquals(true, lifecycle.get("admin_inspect_supported"));
        assertEquals("external_safe_redacted", lifecycle.get("audit_exposure"));
    }

    private static void assertStorageUnavailable(InMemoryResultStorageAdapter storage, String storageRef) {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> storage.read(storageRef));
        assertTrue(ex.getMessage().contains(MemoryGridExecutor.STORAGE_UNAVAILABLE));
    }

    private static ResultHandleWriter.WriteRequest writeRequest(String model, String metric) {
        return new ResultHandleWriter.WriteRequest(
                "DSL_CTE",
                List.of(model),
                "hash_" + model + "_" + metric,
                List.of("salesTeam.name"),
                schema(metric),
                List.of(row("salesTeam.name", "Team A", metric, 120)),
                Map.of("model", model),
                200,
                10_000,
                Duration.ofHours(1),
                5
        );
    }

    private static Map<String, MemoryGridResultResolver.Column> schema(String metric) {
        Map<String, MemoryGridResultResolver.Column> schema = new LinkedHashMap<>();
        schema.put("salesTeam.name",
                new MemoryGridResultResolver.Column("salesTeam.name", "string", true, false, true));
        schema.put(metric, new MemoryGridResultResolver.Column(metric, "number", false, true, true));
        return schema;
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            row.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return row;
    }

    private static final class NonDeletingStorageAdapter implements ResultStorageAdapter {

        private final InMemoryResultStorageAdapter delegate = new InMemoryResultStorageAdapter();

        @Override
        public void write(String storageRef, List<Map<String, Object>> rows) {
            delegate.write(storageRef, rows);
        }

        @Override
        public List<Map<String, Object>> read(String storageRef) {
            return delegate.read(storageRef);
        }

        @Override
        public boolean delete(String storageRef) {
            return false;
        }
    }
}
