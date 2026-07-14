package com.foggyframework.runtime.api.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exact regression for the frozen 9.3.3 additive Runtime lifecycle DTO shape.
 */
class RuntimeLifecycleDtoContractTest {

    private static final String DTO = "com.foggyframework.runtime.api.dto.";

    @Test
    void frozenLifecycleTypesAndLegacyConstructorsMustCoexist() {
        assertAll(
                "frozen Runtime lifecycle DTO contract",
                () -> assertEnumValues(
                        DTO + "RuntimeCatalogState",
                        "ACTIVE",
                        "ACTIVE_OLD_PRESERVED",
                        "STALE_ADMISSION_BLOCKED",
                        "ABSENT"
                ),
                () -> assertRecordComponents(
                        DTO + "DatasourceBindingGenerationSummary",
                        component("bindingKey", "java.lang.String"),
                        component("backendId", "java.lang.String"),
                        component("generation", "java.lang.String")
                ),
                () -> assertEnumValues(
                        DTO + "RuntimeLifecycleErrorCode",
                        "CATALOG_BUILD_FAILED",
                        "CATALOG_VALIDATION_FAILED",
                        "CATALOG_CANDIDATE_STALE",
                        "DATASOURCE_BINDING_NOT_CURRENT",
                        "SINGLE_FLIGHT_CYCLIC_DEPENDENCY",
                        "NAMESPACE_SCOPE_MISUSE",
                        "REFRESH_SCOPE_UNKNOWN",
                        "SOURCE_REVISION_STALE",
                        "DATASOURCE_BINDING_REVOKED"
                ),
                () -> assertRecordComponents(
                        DTO + "RuntimeLifecycleFailureDiagnostic",
                        component("target", "java.lang.String"),
                        component("phase", "java.lang.String"),
                        component("message", "java.lang.String"),
                        component("suggestedNextAction", "java.lang.String")
                ),
                () -> assertRecordComponents(
                        DTO + "RuntimeLifecycleFailureContext",
                        component("namespace", "java.lang.String"),
                        component("beforeCatalogGeneration", "java.lang.String"),
                        component("afterCatalogGeneration", "java.lang.String"),
                        component("sourceRevision", "java.lang.String"),
                        component("catalogState", DTO + "RuntimeCatalogState"),
                        component("affectedBindingGenerations",
                                listOf(DTO + "DatasourceBindingGenerationSummary")),
                        component("failedTargets", listOf("java.lang.String")),
                        component("diagnostics", listOf(DTO + "RuntimeLifecycleFailureDiagnostic"))
                ),
                () -> assertRecordComponents(
                        DTO + "ModelRefreshResponse",
                        component("namespace", "java.lang.String"),
                        component("scope", "java.lang.String"),
                        component("clearedCaches", listOf("java.lang.String")),
                        component("refreshedModels", listOf("java.lang.String")),
                        component("loadedCount", "int"),
                        component("failedCount", "int"),
                        component("failures", listOf(DTO + "ModelRefreshFailure")),
                        component("warnings", listOf("java.lang.String")),
                        component("beforeCatalogGeneration", "java.lang.String"),
                        component("afterCatalogGeneration", "java.lang.String"),
                        component("sourceRevision", "java.lang.String"),
                        component("affectedBindingGenerations",
                                listOf(DTO + "DatasourceBindingGenerationSummary")),
                        component("refreshedCount", "int"),
                        component("preservedCount", "int"),
                        component("durationMs", "java.lang.Long"),
                        component("catalogState", DTO + "RuntimeCatalogState")
                ),
                () -> assertRecordComponents(
                        DTO + "ModelValidateResponse",
                        component("valid", "boolean"),
                        component("namespace", "java.lang.String"),
                        component("path", "java.lang.String"),
                        component("totalFiles", "int"),
                        component("validFiles", "int"),
                        component("invalidFiles", "int"),
                        component("cascadingErrors", "int"),
                        component("durationMs", "java.lang.Long"),
                        component("errors", listOf(DTO + "ModelValidateIssue")),
                        component("warnings", listOf(DTO + "ModelValidateIssue")),
                        component("beforeCatalogGeneration", "java.lang.String"),
                        component("afterCatalogGeneration", "java.lang.String"),
                        component("sourceRevision", "java.lang.String"),
                        component("affectedBindingGenerations",
                                listOf(DTO + "DatasourceBindingGenerationSummary")),
                        component("catalogState", DTO + "RuntimeCatalogState")
                ),
                () -> assertRecordComponents(
                        DTO + "RuntimeError",
                        component("code", "java.lang.String"),
                        component("phase", "java.lang.String"),
                        component("message", "java.lang.String"),
                        component("model", "java.lang.String"),
                        component("field", "java.lang.String"),
                        component("path", "java.lang.String"),
                        component("suggestedNextAction", "java.lang.String"),
                        component("safeToAutoRepair", "boolean"),
                        component("lifecycleCode", DTO + "RuntimeLifecycleErrorCode"),
                        component("lifecycle", DTO + "RuntimeLifecycleFailureContext")
                ),
                () -> assertPublicConstructor(
                        DTO + "ModelRefreshResponse",
                        "java.lang.String", "java.lang.String", "java.util.List", "java.util.List",
                        "int", "int", "java.util.List", "java.util.List"
                ),
                () -> assertPublicConstructor(
                        DTO + "ModelValidateResponse",
                        "boolean", "java.lang.String", "java.lang.String", "int", "int", "int", "int",
                        "java.lang.Long", "java.util.List", "java.util.List"
                ),
                () -> assertPublicConstructor(
                        DTO + "RuntimeError",
                        "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String",
                        "java.lang.String", "java.lang.String", "java.lang.String", "boolean"
                )
        );
    }

    @Test
    void legacyConstructorsDelegateToAdditiveLifecycleDefaults() {
        ModelRefreshResponse refresh = new ModelRefreshResponse(
                "sales", "models", List.of("legacy"), List.of("OrderModel"),
                1, 0, List.of(), List.of());
        ModelValidateResponse validation = new ModelValidateResponse(
                true, "sales", ".", 2, 2, 0, 0, 3L, List.of(), List.of());
        RuntimeError error = new RuntimeError(
                "MODEL_REFRESH_FAILED", "models.refresh", "failed", "OrderModel",
                null, null, "retry", false);

        assertAll(
                () -> assertNull(refresh.beforeCatalogGeneration()),
                () -> assertNull(refresh.afterCatalogGeneration()),
                () -> assertNull(refresh.sourceRevision()),
                () -> assertEquals(List.of(), refresh.affectedBindingGenerations()),
                () -> assertEquals(refresh.loadedCount(), refresh.refreshedCount()),
                () -> assertEquals(0, refresh.preservedCount()),
                () -> assertNull(refresh.durationMs()),
                () -> assertEquals(RuntimeCatalogState.ABSENT, refresh.catalogState()),
                () -> assertNull(validation.beforeCatalogGeneration()),
                () -> assertNull(validation.afterCatalogGeneration()),
                () -> assertNull(validation.sourceRevision()),
                () -> assertEquals(List.of(), validation.affectedBindingGenerations()),
                () -> assertEquals(RuntimeCatalogState.ABSENT, validation.catalogState()),
                () -> assertNull(error.lifecycleCode()),
                () -> assertNull(error.lifecycle())
        );
    }

    private static void assertEnumValues(String typeName, String... expectedValues) {
        Class<?> type = requireType(typeName);
        assertTrue(type.isEnum(), () -> "Frozen type must be an enum: " + typeName);
        List<String> actualValues = Arrays.stream(type.getEnumConstants())
                .map(value -> ((Enum<?>) value).name())
                .toList();
        assertEquals(List.of(expectedValues), actualValues,
                () -> "Frozen enum values changed for " + typeName);
    }

    private static void assertRecordComponents(String typeName, Component... expectedComponents) {
        Class<?> type = requireType(typeName);
        assertTrue(type.isRecord(), () -> "Frozen type must be a record: " + typeName);
        RecordComponent[] components = type.getRecordComponents();
        assertNotNull(components, () -> "Record components unavailable for " + typeName);
        List<Component> actualComponents = Arrays.stream(components)
                .map(component -> component(component.getName(), component.getGenericType().getTypeName()))
                .toList();
        assertEquals(List.of(expectedComponents), actualComponents,
                () -> "Frozen record component order/type changed for " + typeName);
    }

    private static void assertPublicConstructor(String typeName, String... parameterTypeNames) {
        Class<?> type = requireType(typeName);
        Class<?>[] parameterTypes = Arrays.stream(parameterTypeNames)
                .map(RuntimeLifecycleDtoContractTest::requireType)
                .toArray(Class<?>[]::new);
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            assertTrue(Modifier.isPublic(constructor.getModifiers()),
                    () -> "Legacy constructor is no longer public: " + constructor);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(
                    "Missing frozen legacy constructor " + typeName + List.of(parameterTypeNames),
                    e
            );
        }
    }

    private static Class<?> requireType(String typeName) {
        return switch (typeName) {
            case "boolean" -> boolean.class;
            case "int" -> int.class;
            default -> loadReferenceType(typeName);
        };
    }

    private static Class<?> loadReferenceType(String typeName) {
        try {
            return Class.forName(typeName);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing frozen Runtime lifecycle type: " + typeName, e);
        } catch (LinkageError e) {
            throw new AssertionError("Frozen Runtime lifecycle type could not be linked: " + typeName, e);
        }
    }

    private static Component component(String name, String type) {
        return new Component(name, type);
    }

    private static String listOf(String elementType) {
        return "java.util.List<" + elementType + ">";
    }

    private record Component(String name, String type) {
    }
}
