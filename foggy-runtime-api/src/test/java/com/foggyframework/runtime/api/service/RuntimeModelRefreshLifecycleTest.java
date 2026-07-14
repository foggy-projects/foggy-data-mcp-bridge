package com.foggyframework.runtime.api.service;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogAdmissionState;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshDiagnostic;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshException;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshRequest;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshResult;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshScope;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshTrigger;
import com.foggyframework.dataset.db.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.runtime.api.dto.DatasourceBindingGenerationSummary;
import com.foggyframework.runtime.api.dto.ModelRefreshRequest;
import com.foggyframework.runtime.api.dto.ModelRefreshResponse;
import com.foggyframework.runtime.api.dto.ModelValidateRequest;
import com.foggyframework.runtime.api.dto.RuntimeCatalogState;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleErrorCode;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleFailureContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeModelRefreshLifecycleTest {

    private static final String NAMESPACE = "runtime-batch5";

    @Test
    void refreshDelegatesOneTypedRequestToCoordinatorAndMapsTheAtomicResult() {
        Fixture fixture = fixture(new CatalogSnapshotStore());
        CatalogIdentity before = identity("catalog-before", "source-before");
        CatalogIdentity after = identity("catalog-after", "source-after");
        CatalogRefreshResult result = new CatalogRefreshResult(
                NAMESPACE,
                CatalogRefreshScope.MODELS,
                before,
                after,
                after.sourceRevision(),
                Set.of(CatalogModelKey.query("InvoiceModel"),
                        CatalogModelKey.query("OrderModel")),
                Set.of(CatalogModelKey.query("CustomerModel")),
                List.of(
                        binding("runtime:named:z", "runtime-registry:z", "binding-z"),
                        binding("runtime:named:a", "runtime-registry:b", "binding-ab"),
                        binding("runtime:named:a", "runtime-registry:a", "binding-aa")
                ),
                37L,
                CatalogAdmissionState.ACTIVE,
                List.of()
        );
        when(fixture.refreshCoordinator().refresh(any(CatalogRefreshRequest.class)))
                .thenReturn(result);

        ModelRefreshResponse response = fixture.operations().refreshModels(
                new ModelRefreshRequest(
                        NAMESPACE,
                        List.of("OrderModel", "InvoiceModel", "OrderModel", " ")),
                null
        );

        ArgumentCaptor<CatalogRefreshRequest> requestCaptor =
                ArgumentCaptor.forClass(CatalogRefreshRequest.class);
        verify(fixture.refreshCoordinator()).refresh(requestCaptor.capture());
        CatalogRefreshRequest delegated = requestCaptor.getValue();

        assertAll(
                "the Runtime adapter delegates ownership and maps the committed result",
                () -> assertThat(delegated.namespace()).isEqualTo(NAMESPACE),
                () -> assertThat(delegated.scope()).isEqualTo(CatalogRefreshScope.MODELS),
                () -> assertThat(delegated.targets()).containsExactly(
                        CatalogModelKey.query("InvoiceModel"),
                        CatalogModelKey.query("OrderModel")),
                () -> assertThat(delegated.trigger()).isEqualTo(CatalogRefreshTrigger.RUNTIME_API),
                () -> assertThat(response.namespace()).isEqualTo(NAMESPACE),
                () -> assertThat(response.scope()).isEqualTo("models"),
                () -> assertThat(response.clearedCaches()).isNotNull().isEmpty(),
                () -> assertThat(response.refreshedModels())
                        .containsExactly("InvoiceModel", "OrderModel"),
                () -> assertThat(response.loadedCount()).isEqualTo(2),
                () -> assertThat(response.refreshedCount())
                        .isEqualTo(response.loadedCount()),
                () -> assertThat(response.failedCount()).isZero(),
                () -> assertThat(response.failures()).isEmpty(),
                () -> assertThat(response.beforeCatalogGeneration())
                        .isEqualTo("catalog-before"),
                () -> assertThat(response.afterCatalogGeneration())
                        .isEqualTo("catalog-after"),
                () -> assertThat(response.sourceRevision()).isEqualTo("source-after"),
                () -> assertThat(response.preservedCount()).isEqualTo(1),
                () -> assertThat(response.durationMs()).isEqualTo(37L),
                () -> assertThat(response.catalogState()).isEqualTo(RuntimeCatalogState.ACTIVE),
                () -> assertThat(bindingOrder(response.affectedBindingGenerations()))
                        .containsExactly(
                                "runtime:named:a/runtime-registry:a/binding-aa",
                                "runtime:named:a/runtime-registry:b/binding-ab",
                                "runtime:named:z/runtime-registry:z/binding-z")
        );
        assertNoLegacyClearOrWarmup(fixture);
    }

    @Test
    void coordinatorFailureKeepsAfterNullAndMapsStableTypedLifecycleContext() {
        CatalogSnapshotStore snapshotStore = mock(CatalogSnapshotStore.class);
        when(snapshotStore.admissionState(NAMESPACE))
                .thenReturn(CatalogAdmissionState.ACTIVE_OLD_PRESERVED);
        when(snapshotStore.current(NAMESPACE)).thenReturn(Optional.empty());
        Fixture fixture = fixture(snapshotStore);
        CatalogIdentity before = identity("catalog-before", "source-before");
        CatalogRefreshRequest coreRequest = CatalogRefreshRequest.models(
                NAMESPACE,
                List.of(CatalogModelKey.query("OrderModel")),
                CatalogRefreshTrigger.RUNTIME_API
        );
        CatalogRefreshException coreFailure = new CatalogRefreshException(
                "SOURCE_REVISION_STALE",
                coreRequest,
                before,
                CatalogAdmissionState.ACTIVE_OLD_PRESERVED,
                List.of(new CatalogRefreshDiagnostic(
                        "SOURCE_REVISION_STALE",
                        "OrderModel",
                        "The committed logical source changed during candidate build."
                )),
                new IllegalStateException("candidate source changed")
        );
        when(fixture.refreshCoordinator().refresh(any(CatalogRefreshRequest.class)))
                .thenThrow(coreFailure);

        assertThatThrownBy(() -> fixture.operations().refreshModels(
                new ModelRefreshRequest(NAMESPACE, List.of("OrderModel")),
                null
        ))
                .isInstanceOf(RuntimeModelOperationException.class)
                .satisfies(error -> {
                    RuntimeModelOperationException failure =
                            (RuntimeModelOperationException) error;
                    RuntimeLifecycleFailureContext lifecycle = failure.lifecycle();
                    assertAll(
                            () -> assertThat(failure.code())
                                    .isEqualTo("MODEL_REFRESH_FAILED"),
                            () -> assertThat(failure.phase()).isEqualTo("models.refresh"),
                            () -> assertThat(failure.lifecycleCode())
                                    .isEqualTo(RuntimeLifecycleErrorCode.SOURCE_REVISION_STALE),
                            () -> assertThat(lifecycle).isNotNull(),
                            () -> assertThat(lifecycle.namespace()).isEqualTo(NAMESPACE),
                            () -> assertThat(lifecycle.beforeCatalogGeneration())
                                    .isEqualTo("catalog-before"),
                            () -> assertThat(lifecycle.afterCatalogGeneration()).isNull(),
                            () -> assertThat(lifecycle.sourceRevision())
                                    .isEqualTo("source-before"),
                            () -> assertThat(lifecycle.catalogState())
                                    .isEqualTo(RuntimeCatalogState.ACTIVE_OLD_PRESERVED),
                            () -> assertThat(lifecycle.affectedBindingGenerations()).isEmpty(),
                            () -> assertThat(lifecycle.failedTargets())
                                    .containsExactly("OrderModel"),
                            () -> assertThat(lifecycle.diagnostics()).hasSize(1),
                            () -> assertThat(lifecycle.diagnostics().get(0).target())
                                    .isEqualTo("OrderModel")
                    );
                });

        verify(fixture.refreshCoordinator()).refresh(any(CatalogRefreshRequest.class));
        assertNoLegacyClearOrWarmup(fixture);
    }

    @Test
    void refreshFailureReportsTheLatestAdmissionBlockInsteadOfTheCapturedActiveState() {
        CatalogSnapshotStore snapshotStore = new CatalogSnapshotStore();
        Fixture fixture = fixture(snapshotStore);
        CatalogIdentity before = identity("catalog-before", "source-before");
        CatalogRefreshRequest coreRequest = CatalogRefreshRequest.namespace(
                NAMESPACE, CatalogRefreshTrigger.RUNTIME_API);
        CatalogRefreshException coreFailure = new CatalogRefreshException(
                "DATASOURCE_BINDING_NOT_CURRENT",
                coreRequest,
                before,
                CatalogAdmissionState.ACTIVE_OLD_PRESERVED,
                List.of(),
                new IllegalStateException("controlled binding rotation")
        );
        when(fixture.refreshCoordinator().refresh(any(CatalogRefreshRequest.class)))
                .thenAnswer(ignored -> {
                    snapshotStore.markStaleAdmissionBlocked(
                            NAMESPACE,
                            "DATASOURCE_BINDING_NOT_CURRENT: controlled binding rotation");
                    throw coreFailure;
                });

        assertThatThrownBy(() -> fixture.operations().refreshModels(
                new ModelRefreshRequest(NAMESPACE, List.of()),
                null
        ))
                .isInstanceOf(RuntimeModelOperationException.class)
                .satisfies(error -> {
                    RuntimeModelOperationException failure =
                            (RuntimeModelOperationException) error;
                    assertAll(
                            () -> assertThat(failure.lifecycleCode()).isEqualTo(
                                    RuntimeLifecycleErrorCode.DATASOURCE_BINDING_NOT_CURRENT),
                            () -> assertThat(failure.lifecycle().catalogState()).isEqualTo(
                                    RuntimeCatalogState.STALE_ADMISSION_BLOCKED),
                            () -> assertThat(failure.lifecycle()
                                    .afterCatalogGeneration()).isNull()
                    );
                });

        assertNoLegacyClearOrWarmup(fixture);
    }

    @Test
    void validateInfrastructureExceptionAfterAdmissionHasTypedLifecycleFailure(
            @TempDir Path modelDirectory
    ) {
        CatalogSnapshotStore snapshotStore = mock(CatalogSnapshotStore.class);
        when(snapshotStore.admissionState(NAMESPACE)).thenThrow(
                new IllegalStateException(
                        "failed at /srv/private-731/Broken.tm using password=secret-731"));
        Fixture fixture = fixture(snapshotStore);

        assertThatThrownBy(() -> fixture.operations().validateModels(
                new ModelValidateRequest(
                        modelDirectory.toString(), NAMESPACE, false, false, false),
                null
        ))
                .isInstanceOf(RuntimeModelOperationException.class)
                .satisfies(error -> {
                    RuntimeModelOperationException failure =
                            (RuntimeModelOperationException) error;
                    RuntimeLifecycleFailureContext lifecycle = failure.lifecycle();
                    assertAll(
                            () -> assertThat(failure.code())
                                    .isEqualTo("MODEL_VALIDATE_FAILED"),
                            () -> assertThat(failure.phase()).isEqualTo("models.validate"),
                            () -> assertThat(failure.lifecycleCode())
                                    .isEqualTo(RuntimeLifecycleErrorCode.CATALOG_VALIDATION_FAILED),
                            () -> assertThat(lifecycle).isNotNull(),
                            () -> assertThat(lifecycle.namespace()).isEqualTo(NAMESPACE),
                            () -> assertThat(lifecycle.afterCatalogGeneration()).isNull(),
                            () -> assertThat(lifecycle.catalogState()).isNotNull(),
                            () -> assertThat(lifecycle.failedTargets()).isEmpty(),
                            () -> assertThat(lifecycle.diagnostics()).isNotNull()
                    );
                });

        verify(fixture.refreshCoordinator(), never())
                .refresh(any(CatalogRefreshRequest.class));
        assertNoLegacyClearOrWarmup(fixture);
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(CatalogSnapshotStore snapshotStore) {
        SemanticModelCatalogService catalogService =
                mock(SemanticModelCatalogService.class);
        SemanticServiceV3 semanticService = mock(SemanticServiceV3.class);
        SystemBundlesContext systemBundlesContext = mock(SystemBundlesContext.class);
        QueryModelLoader queryModelLoader = mock(QueryModelLoader.class);
        TableModelLoaderManager tableModelLoaderManager =
                mock(TableModelLoaderManager.class);
        CatalogRefreshCoordinator refreshCoordinator =
                mock(CatalogRefreshCoordinator.class);
        ObjectProvider<DatasetProperties> datasetProperties = mock(ObjectProvider.class);
        ObjectProvider<CatalogSnapshotStore> snapshotStoreProvider =
                mock(ObjectProvider.class);
        ObjectProvider<CatalogRefreshCoordinator> refreshCoordinatorProvider =
                mock(ObjectProvider.class);

        when(datasetProperties.getIfAvailable()).thenReturn(null);
        when(snapshotStoreProvider.getIfAvailable()).thenReturn(snapshotStore);
        when(refreshCoordinatorProvider.getIfAvailable()).thenReturn(refreshCoordinator);

        RuntimeModelOperations operations = new RuntimeModelOperations(
                catalogService,
                semanticService,
                systemBundlesContext,
                queryModelLoader,
                tableModelLoaderManager,
                datasetProperties,
                snapshotStoreProvider,
                refreshCoordinatorProvider
        );
        return new Fixture(
                operations,
                catalogService,
                queryModelLoader,
                tableModelLoaderManager,
                refreshCoordinator
        );
    }

    private static CatalogIdentity identity(String generation, String sourceRevision) {
        return new CatalogIdentity(
                NAMESPACE,
                new CatalogGeneration(generation),
                new SourceRevision(sourceRevision)
        );
    }

    private static DatasourceBindingIdentity binding(
            String bindingKey,
            String backendId,
            String generation
    ) {
        return new DatasourceBindingIdentity(
                bindingKey,
                backendId,
                new DatasourceBindingGeneration(generation)
        );
    }

    private static List<String> bindingOrder(
            List<DatasourceBindingGenerationSummary> bindings
    ) {
        return bindings.stream()
                .map(binding -> String.join("/",
                        binding.bindingKey(), binding.backendId(), binding.generation()))
                .toList();
    }

    private static void assertNoLegacyClearOrWarmup(Fixture fixture) {
        assertAll(
                () -> verify(fixture.tableModelLoaderManager(), never())
                        .clearByNamespace(any()),
                () -> verify(fixture.queryModelLoader(), never())
                        .clearByNamespace(any()),
                () -> verify(fixture.queryModelLoader(), never())
                        .getJdbcQueryModel(any(), any()),
                () -> verify(fixture.catalogService(), never()).clearCachedModelNames()
        );
    }

    private record Fixture(
            RuntimeModelOperations operations,
            SemanticModelCatalogService catalogService,
            QueryModelLoader queryModelLoader,
            TableModelLoaderManager tableModelLoaderManager,
            CatalogRefreshCoordinator refreshCoordinator
    ) {
    }
}
