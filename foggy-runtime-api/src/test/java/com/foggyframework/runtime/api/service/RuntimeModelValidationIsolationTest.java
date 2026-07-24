package com.foggyframework.runtime.api.service;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.spi.TableModelLoaderManager;
import com.foggyframework.fsscript.loadder.AbstractFileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.runtime.api.dto.ModelValidateRequest;
import com.foggyframework.runtime.api.dto.ModelValidateResponse;
import com.foggyframework.runtime.api.dto.RuntimeCatalogState;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleErrorCode;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleFailureContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.GenericApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RuntimeModelValidationIsolationTest {

    private static final String NAMESPACE = "runtime-validation-isolation";

    private GenericApplicationContext applicationContext;

    @AfterEach
    void closeApplicationContext() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    @Test
    void validCandidateUsesFreshScriptCacheAndLeavesLiveAuthoritiesUnchanged(
            @TempDir Path modelDirectory
    ) throws IOException {
        Files.writeString(modelDirectory.resolve("CandidateModel.tm"), """
                export const model = {
                    name: 'CandidateModel',
                    tableName: 'candidate_table'
                };
                """);
        Fixture fixture = fixture();
        CatalogSnapshot before = fixture.liveSnapshot();
        AbstractFileFsscriptLoader liveScriptLoader = FileFsscriptLoader.getInstance();

        ModelValidateResponse response = fixture.operations().validateModels(
                new ModelValidateRequest(
                        modelDirectory.toString(), NAMESPACE, true, true, false),
                null
        );

        assertAll(
                () -> assertThat(response.valid()).isTrue(),
                () -> assertThat(response.totalFiles()).isEqualTo(1),
                () -> assertThat(response.beforeCatalogGeneration())
                        .isEqualTo(before.identity().generation().value()),
                () -> assertThat(response.afterCatalogGeneration())
                        .isEqualTo(before.identity().generation().value()),
                () -> assertThat(response.sourceRevision())
                        .isEqualTo(before.identity().sourceRevision().value()),
                () -> assertThat(response.catalogState())
                        .isEqualTo(RuntimeCatalogState.ACTIVE),
                () -> assertThat(response.affectedBindingGenerations()).isEmpty(),
                () -> assertThat(fixture.catalogSnapshotStore()
                        .current(NAMESPACE).orElseThrow()).isSameAs(before),
                () -> assertThat(FileFsscriptLoader.getInstance())
                        .isSameAs(liveScriptLoader)
        );
        assertLiveAuthoritiesWereNotMutated(fixture);
    }

    @Test
    void invalidCandidateReportsTheUnchangedLiveIdentityWithoutPublishingAnything(
            @TempDir Path modelDirectory
    ) throws IOException {
        Files.writeString(modelDirectory.resolve("BrokenModel.tm"), """
                export const model = {
                    name: ;
                };
                """);
        Fixture fixture = fixture();
        CatalogSnapshot before = fixture.liveSnapshot();

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
                    assertThat(failure.code()).isEqualTo("MODEL_VALIDATE_FAILED");
                    assertAll(
                            () -> assertThat(failure.lifecycleCode())
                                    .isEqualTo(RuntimeLifecycleErrorCode.CATALOG_VALIDATION_FAILED),
                            () -> assertThat(lifecycle).isNotNull(),
                            () -> assertThat(lifecycle.namespace()).isEqualTo(NAMESPACE),
                            () -> assertThat(lifecycle.beforeCatalogGeneration())
                                    .isEqualTo(before.identity().generation().value()),
                            () -> assertThat(lifecycle.afterCatalogGeneration()).isNull(),
                            () -> assertThat(lifecycle.sourceRevision())
                                    .isEqualTo(before.identity().sourceRevision().value()),
                            () -> assertThat(lifecycle.catalogState())
                                    .isEqualTo(RuntimeCatalogState.ACTIVE),
                            () -> assertThat(lifecycle.failedTargets())
                                    .containsExactly("BrokenModel"),
                            () -> assertThat(lifecycle.diagnostics()).hasSize(1),
                            () -> assertThat(lifecycle.diagnostics().get(0).target())
                                    .isEqualTo("BrokenModel"),
                            () -> assertThat(failure.diagnostics().attributes())
                                    .containsEntry("invalidFiles", 1)
                    );
                });

        assertThat(fixture.catalogSnapshotStore().current(NAMESPACE).orElseThrow())
                .isSameAs(before);
        assertLiveAuthoritiesWereNotMutated(fixture);
    }

    private void assertLiveAuthoritiesWereNotMutated(Fixture fixture) {
        assertAll(
                () -> verify(fixture.systemBundlesContext(), never())
                        .addExternalBundle(any(), any(), any(), anyBoolean()),
                () -> verify(fixture.systemBundlesContext(), never()).removeBundle(any()),
                () -> verifyNoInteractions(fixture.queryModelLoader()),
                () -> verifyNoInteractions(fixture.tableModelLoaderManager())
        );
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        applicationContext = new GenericApplicationContext();
        applicationContext.refresh();

        SemanticModelCatalogService catalogService =
                mock(SemanticModelCatalogService.class);
        SemanticServiceV3 semanticService = mock(SemanticServiceV3.class);
        SystemBundlesContext systemBundlesContext = mock(SystemBundlesContext.class);
        QueryModelLoader queryModelLoader = mock(QueryModelLoader.class);
        TableModelLoaderManager tableModelLoaderManager =
                mock(TableModelLoaderManager.class);
        ObjectProvider<DatasetProperties> datasetProperties = mock(ObjectProvider.class);
        ObjectProvider<CatalogSnapshotStore> catalogSnapshotStoreProvider =
                mock(ObjectProvider.class);
        CatalogSnapshotStore catalogSnapshotStore = new CatalogSnapshotStore();
        try (CatalogSnapshotStore.CandidateScope scope =
                     catalogSnapshotStore.openCandidate(NAMESPACE)) {
            scope.candidate().discoverQueryModels(List.of("LiveOrderModel"));
            scope.commit();
        }

        when(systemBundlesContext.getApplicationContext())
                .thenReturn(applicationContext);
        when(datasetProperties.getIfAvailable()).thenReturn(null);
        when(catalogSnapshotStoreProvider.getIfAvailable())
                .thenReturn(catalogSnapshotStore);

        RuntimeModelOperations operations = new RuntimeModelOperations(
                catalogService,
                semanticService,
                systemBundlesContext,
                queryModelLoader,
                tableModelLoaderManager,
                datasetProperties,
                catalogSnapshotStoreProvider
        );
        return new Fixture(
                operations,
                systemBundlesContext,
                queryModelLoader,
                tableModelLoaderManager,
                catalogSnapshotStore
        );
    }

    private record Fixture(
            RuntimeModelOperations operations,
            SystemBundlesContext systemBundlesContext,
            QueryModelLoader queryModelLoader,
            TableModelLoaderManager tableModelLoaderManager,
            CatalogSnapshotStore catalogSnapshotStore
    ) {
        private CatalogSnapshot liveSnapshot() {
            return catalogSnapshotStore.current(NAMESPACE).orElseThrow();
        }
    }
}
