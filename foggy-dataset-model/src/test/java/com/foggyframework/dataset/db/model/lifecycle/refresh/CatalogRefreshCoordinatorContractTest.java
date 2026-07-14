package com.foggyframework.dataset.db.model.lifecycle.refresh;

import com.foggyframework.dataset.db.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.db.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshotStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test-first surface contract for the Batch 5 refresh authority.
 *
 * <p>The frozen 9.3.3 contract owns these types in dataset-model. Keeping this
 * probe reflection based lets the suite compile before the production surface
 * exists while still producing an assertion-red report rather than a test
 * discovery or linkage error.</p>
 */
class CatalogRefreshCoordinatorContractTest {

    private static final String REFRESH =
            "com.foggyframework.dataset.db.model.lifecycle.refresh.";

    @Test
    void frozenRefreshTypesMustExistInTheModelAuthorityPackage() {
        Class<?> coordinator = requireType(REFRESH + "CatalogRefreshCoordinator");
        Class<?> request = requireType(REFRESH + "CatalogRefreshRequest");
        Class<?> result = requireType(REFRESH + "CatalogRefreshResult");

        assertAll(
                () -> assertTrue(Modifier.isFinal(coordinator.getModifiers()),
                        "CatalogRefreshCoordinator must be final"),
                () -> assertTrue(request.isRecord(),
                        "CatalogRefreshRequest must be an immutable record"),
                () -> assertTrue(result.isRecord(),
                        "CatalogRefreshResult must be an immutable record")
        );
    }

    @Test
    void refreshScopeAndTriggerMustUseTheFrozenClosedValueSets() {
        assertEnumValues(
                REFRESH + "CatalogRefreshScope",
                "NAMESPACE", "MODELS"
        );
        assertEnumValues(
                REFRESH + "CatalogRefreshTrigger",
                "RUNTIME_API", "BUNDLE", "FILE", "DATASOURCE", "EXPLICIT_RECOVERY"
        );
    }

    @Test
    void refreshRequestAndResultMustCarryTheCompletePinnedInputAndOutcome() {
        assertRecordComponents(
                REFRESH + "CatalogRefreshRequest",
                component("namespace", "java.lang.String"),
                component("scope", REFRESH + "CatalogRefreshScope"),
                component("targets", "java.util.Set<com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey>"),
                component("trigger", REFRESH + "CatalogRefreshTrigger")
        );
        assertRecordComponents(
                REFRESH + "CatalogRefreshResult",
                component("namespace", "java.lang.String"),
                component("scope", REFRESH + "CatalogRefreshScope"),
                component("beforeIdentity", "com.foggyframework.dataset.db.model.lifecycle.identity.CatalogIdentity"),
                component("afterIdentity", "com.foggyframework.dataset.db.model.lifecycle.identity.CatalogIdentity"),
                component("sourceRevision", "com.foggyframework.dataset.db.model.lifecycle.identity.SourceRevision"),
                component("refreshedModels", "java.util.Set<com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey>"),
                component("preservedModels", "java.util.Set<com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey>"),
                component("affectedBindings", "java.util.List<com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity>"),
                component("durationMs", "long"),
                component("catalogState", "com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogAdmissionState"),
                component("diagnostics", "java.util.List<com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshDiagnostic>")
        );
    }

    @Test
    void coordinatorMustExposeOneSynchronousRefreshBoundary() {
        Class<?> coordinator = requireType(REFRESH + "CatalogRefreshCoordinator");
        Class<?> request = requireType(REFRESH + "CatalogRefreshRequest");
        Class<?> result = requireType(REFRESH + "CatalogRefreshResult");

        Constructor<?> constructor;
        Method refresh;
        try {
            constructor = coordinator.getDeclaredConstructor(
                    CatalogSnapshotStore.class,
                    TableModelLoaderManagerImpl.class,
                    QueryModelLoaderImpl.class
            );
            refresh = coordinator.getDeclaredMethod("refresh", request);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Missing frozen CatalogRefreshCoordinator boundary", e);
        }

        assertAll(
                () -> assertTrue(Modifier.isPublic(constructor.getModifiers()),
                        "refresh coordinator constructor must be public"),
                () -> assertTrue(Modifier.isPublic(refresh.getModifiers()),
                        "refresh entry must be public"),
                () -> assertEquals(result, refresh.getReturnType(),
                        "refresh must return CatalogRefreshResult")
        );
    }

    private static void assertEnumValues(String typeName, String... expected) {
        Class<?> type = requireType(typeName);
        assertTrue(type.isEnum(), () -> "Frozen type must be an enum: " + typeName);
        List<String> actual = Arrays.stream(type.getEnumConstants())
                .map(value -> ((Enum<?>) value).name())
                .toList();
        assertEquals(List.of(expected), actual,
                () -> "Frozen enum values changed for " + typeName);
    }

    private static void assertRecordComponents(String typeName, Component... expected) {
        Class<?> type = requireType(typeName);
        assertTrue(type.isRecord(), () -> "Frozen type must be a record: " + typeName);
        RecordComponent[] components = type.getRecordComponents();
        assertNotNull(components, () -> "Record components unavailable for " + typeName);
        List<Component> actual = Arrays.stream(components)
                .map(component -> component(
                        component.getName(), component.getGenericType().getTypeName()))
                .toList();
        assertEquals(List.of(expected), actual,
                () -> "Frozen record component order/type changed for " + typeName);
    }

    private static Class<?> requireType(String typeName) {
        try {
            return Class.forName(typeName);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing frozen Batch 5 refresh type: " + typeName, e);
        } catch (LinkageError e) {
            throw new AssertionError("Batch 5 refresh type could not be linked: " + typeName, e);
        }
    }

    private static Component component(String name, String type) {
        return new Component(name, type);
    }

    private record Component(String name, String type) {
    }
}
