package com.foggyframework.dataset.db.model.lifecycle.refresh;

import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogCandidate;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.db.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Admission behavior for known and unknown source-mutation scope. */
class CatalogAdmissionContractTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Test
    void coldKnownNamespaceMustBeAbsentWithoutPublishingAGeneration() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        store.capture(" tenant-a ");

        assertEquals(Set.of(TENANT_A), knownNamespaces(store));
        assertEquals("ABSENT", admissionState(store, TENANT_A));
        assertTrue(store.current(TENANT_A).isEmpty());
    }

    @Test
    void successfulPublicationMustMakeTheNamespaceActive() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot snapshot = seed(store, TENANT_A, "TenantAQueryModel");

        assertEquals("ACTIVE", admissionState(store, TENANT_A));
        assertSame(snapshot, readCurrent(store, TENANT_A).orElseThrow());
    }

    @Test
    void unknownScopeBlockMustRetainDiagnosticSnapshotButRejectNewReads() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot before = seed(store, TENANT_A, "TenantAQueryModel");

        invokeRequired(
                store,
                "markStaleAdmissionBlocked",
                new Class<?>[]{String.class, String.class},
                TENANT_A,
                "REFRESH_SCOPE_UNKNOWN: controlled file mutation"
        );

        assertEquals("STALE_ADMISSION_BLOCKED", admissionState(store, TENANT_A));
        assertSame(before, store.current(TENANT_A).orElseThrow(),
                "blocked admission must retain the old snapshot for diagnostics/retire");
        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> invokeReadCurrentAllowingProductionFailure(store, TENANT_A)
        );
        assertTrue(failure.getMessage().contains("REFRESH_SCOPE_UNKNOWN"),
                "blocked reads need the frozen stable failure code");
        assertSame(before, store.current(TENANT_A).orElseThrow(),
                "a rejected read must not clear or replace the diagnostic snapshot");
    }

    @Test
    void blockingOneNamespaceMustNotChangeAnotherNamespaceIdentityOrAdmission() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot tenantA = seed(store, TENANT_A, "TenantAQueryModel");
        CatalogSnapshot tenantB = seed(store, TENANT_B, "TenantBQueryModel");

        invokeRequired(
                store,
                "markStaleAdmissionBlocked",
                new Class<?>[]{String.class, String.class},
                TENANT_A,
                "REFRESH_SCOPE_UNKNOWN: controlled file mutation"
        );

        assertEquals("STALE_ADMISSION_BLOCKED", admissionState(store, TENANT_A));
        assertEquals("ACTIVE", admissionState(store, TENANT_B));
        assertSame(tenantA, store.current(TENANT_A).orElseThrow());
        assertSame(tenantB, readCurrent(store, TENANT_B).orElseThrow());
        assertSame(tenantB, store.current(TENANT_B).orElseThrow());
    }

    @SuppressWarnings("unchecked")
    private static Set<String> knownNamespaces(CatalogSnapshotStore store) {
        return (Set<String>) invokeRequired(
                store,
                "knownNamespaces",
                new Class<?>[0]
        );
    }

    private static String admissionState(CatalogSnapshotStore store, String namespace) {
        Object state = invokeRequired(
                store,
                "admissionState",
                new Class<?>[]{String.class},
                namespace
        );
        return ((Enum<?>) state).name();
    }

    @SuppressWarnings("unchecked")
    private static Optional<CatalogSnapshot> readCurrent(
            CatalogSnapshotStore store,
            String namespace
    ) {
        return (Optional<CatalogSnapshot>) invokeRequired(
                store,
                "readCurrent",
                new Class<?>[]{String.class},
                namespace
        );
    }

    private static void invokeReadCurrentAllowingProductionFailure(
            CatalogSnapshotStore store,
            String namespace
    ) {
        Method method;
        try {
            method = store.getClass().getMethod("readCurrent", String.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Missing Batch 5 store method: readCurrent", e);
        }
        try {
            method.invoke(store, namespace);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Batch 5 store method is not public: readCurrent", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError("Unexpected checked failure from readCurrent", cause);
        }
    }

    private static CatalogSnapshot seed(
            CatalogSnapshotStore store,
            String namespace,
            String modelName
    ) {
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(namespace)) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(Set.of(modelName));
            candidate.putQueryModel(
                    modelName,
                    queryModel(modelName, candidate.aliasFor(modelName)),
                    new ModelProvenance(
                            modelName,
                            ModelProvenance.ModelKind.QUERY,
                            candidate.sourceRevision(),
                            Set.of(),
                            Map.of(),
                            true,
                            List.of()
                    )
            );
            return scope.commit();
        }
    }

    private static QueryModel queryModel(String name, String alias) {
        QueryModel model = mock(QueryModel.class);
        when(model.getName()).thenReturn(name);
        when(model.getShortAlias()).thenReturn(alias);
        return model;
    }

    private static Object invokeRequired(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments
    ) {
        Method method;
        try {
            method = target.getClass().getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Missing Batch 5 store method: " + methodName, e);
        }
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Batch 5 store method is not public: " + methodName, e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Batch 5 store method failed: " + methodName, e.getCause());
        }
    }
}
