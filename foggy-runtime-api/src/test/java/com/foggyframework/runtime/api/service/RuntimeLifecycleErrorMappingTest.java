package com.foggyframework.runtime.api.service;

import com.foggyframework.dataset.model.lifecycle.catalog.CatalogAdmissionBlockedException;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.controller.RuntimeModelsController;
import com.foggyframework.runtime.api.dto.DatasourceBindingGenerationSummary;
import com.foggyframework.runtime.api.dto.ModelRefreshResponse;
import com.foggyframework.runtime.api.dto.RuntimeCatalogState;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleErrorCode;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleFailureContext;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleFailureDiagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeLifecycleErrorMappingTest {

    @Test
    void controllerMapsTypedLifecycleFailureWithoutReconstructingItsContext() {
        RuntimeLifecycleFailureContext lifecycle = lifecycleContext();
        RuntimeDiagnostics diagnostics = new RuntimeDiagnostics(
                null, null, List.of("catalog remains blocked"), Map.of("attempt", "refresh"));
        RuntimeModelOperationException failure = new RuntimeModelOperationException(
                "MODEL_REFRESH_FAILED",
                "models.refresh",
                "Catalog refresh failed.",
                "OrderModel",
                "Fix the model and retry.",
                false,
                diagnostics,
                RuntimeLifecycleErrorCode.CATALOG_BUILD_FAILED,
                lifecycle
        );
        RuntimeModelOperations operations = mock(RuntimeModelOperations.class);
        when(operations.refreshModels(null, null)).thenThrow(failure);

        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        RuntimeModelsController controller = new RuntimeModelsController(
                new RuntimeApiResponseFactory(properties), operations);

        RuntimeEnvelope<ModelRefreshResponse> envelope = controller.refreshModels(null, null);

        assertAll(
                () -> assertFalse(envelope.success()),
                () -> assertNull(envelope.data()),
                () -> assertEquals("MODEL_REFRESH_FAILED", envelope.error().code()),
                () -> assertEquals("models.refresh", envelope.error().phase()),
                () -> assertEquals(RuntimeLifecycleErrorCode.CATALOG_BUILD_FAILED,
                        envelope.error().lifecycleCode()),
                () -> assertSame(lifecycle, envelope.error().lifecycle()),
                () -> assertSame(diagnostics, envelope.diagnostics())
        );
    }

    @Test
    void legacyExceptionConstructorLeavesAdditiveLifecycleContextAbsent() {
        RuntimeModelOperationException failure = new RuntimeModelOperationException(
                "MODEL_REFRESH_FAILED",
                "models.refresh",
                "failed",
                "OrderModel",
                "retry",
                false,
                RuntimeDiagnostics.empty()
        );

        assertAll(
                () -> assertNull(failure.lifecycleCode()),
                () -> assertNull(failure.lifecycle())
        );
    }

    @Test
    void admissionBlockKeepsDatasourceAndUnknownScopeCodesDistinct() {
        CatalogAdmissionBlockedException datasource =
                new CatalogAdmissionBlockedException(
                        "sales",
                        "DATASOURCE_BINDING_NOT_CURRENT: CHANGED");
        CatalogAdmissionBlockedException unknownScope =
                new CatalogAdmissionBlockedException(
                        "sales",
                        "REFRESH_SCOPE_UNKNOWN: committed source scope is not provable");

        assertAll(
                () -> assertEquals("DATASOURCE_BINDING_NOT_CURRENT",
                        datasource.code()),
                () -> assertEquals(
                        RuntimeLifecycleErrorCode.DATASOURCE_BINDING_NOT_CURRENT,
                        RuntimeModelOperations.lifecycleCode(datasource)),
                () -> assertEquals("REFRESH_SCOPE_UNKNOWN",
                        unknownScope.code()),
                () -> assertEquals(
                        RuntimeLifecycleErrorCode.REFRESH_SCOPE_UNKNOWN,
                        RuntimeModelOperations.lifecycleCode(unknownScope))
        );
    }

    private static RuntimeLifecycleFailureContext lifecycleContext() {
        DatasourceBindingGenerationSummary binding =
                new DatasourceBindingGenerationSummary(
                        "runtime:named:orders", "runtime-registry:orders", "opaque-generation");
        RuntimeLifecycleFailureDiagnostic diagnostic =
                new RuntimeLifecycleFailureDiagnostic(
                        "OrderModel", "build", "sanitized failure", "Fix the model and retry.");
        return new RuntimeLifecycleFailureContext(
                "sales",
                "before-generation",
                null,
                "source-revision",
                RuntimeCatalogState.STALE_ADMISSION_BLOCKED,
                List.of(binding),
                List.of("OrderModel"),
                List.of(diagnostic)
        );
    }
}
