package com.foggyframework.runtime.api.service;

import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogCandidate;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.validation.DetachedModelValidationFactory;
import com.foggyframework.runtime.api.dto.ModelDescribeRequest;
import com.foggyframework.runtime.api.dto.ModelDescribeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeModelOperationsDescribeTest {

    private static final String NAMESPACE = "business";
    private static final String MODEL = "SalesModel";

    @Test
    void describeMustExposePinnedBundleSourceAndPreserveMetadataData() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        publish(store, new ModelProvenance.ModelSource(
                "sales-bundle",
                NAMESPACE,
                "/query/SalesModel.qm"));
        SemanticServiceV3 semanticService = semanticService();
        RuntimeModelOperations operations = operations(store, semanticService);

        ModelDescribeResponse response = operations.describeModel(
                MODEL,
                new ModelDescribeRequest(
                        "json", NAMESPACE, List.of(), List.of(), false),
                null);

        assertThat(response.data()).containsEntry("existing", "value");
        assertThat(response.data().get("modelSource"))
                .isEqualTo(Map.of(
                        "known", true,
                        "bundleName", "sales-bundle",
                        "namespace", NAMESPACE,
                        "resourceIdentity", "/query/SalesModel.qm"));
    }

    @Test
    void describeMustMarkUnknownSourceWithoutInventingOwnership() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        publish(store, null);
        RuntimeModelOperations operations =
                operations(store, semanticService());

        ModelDescribeResponse response = operations.describeModel(
                MODEL, null, NAMESPACE);

        assertThat(response.data().get("modelSource"))
                .isEqualTo(Map.of("known", false));
    }

    private static SemanticServiceV3 semanticService() {
        SemanticServiceV3 semanticService = mock(SemanticServiceV3.class);
        SemanticMetadataResponse metadata = new SemanticMetadataResponse();
        metadata.setFormat("json");
        metadata.setContent("{}");
        metadata.setData(Map.of(
                "existing", "value",
                "models", Map.of(MODEL, Map.of())));
        when(semanticService.getMetadata(any(), anyString(), any()))
                .thenReturn(metadata);
        return semanticService;
    }

    @SuppressWarnings("unchecked")
    private static RuntimeModelOperations operations(
            CatalogSnapshotStore store,
            SemanticServiceV3 semanticService
    ) {
        ObjectProvider<DatasetProperties> datasetProperties =
                mock(ObjectProvider.class);
        ObjectProvider<CatalogSnapshotStore> snapshotStoreProvider =
                mock(ObjectProvider.class);
        ObjectProvider<CatalogRefreshCoordinator> refreshCoordinatorProvider =
                mock(ObjectProvider.class);
        when(datasetProperties.getIfAvailable()).thenReturn(null);
        when(snapshotStoreProvider.getIfAvailable()).thenReturn(store);
        when(refreshCoordinatorProvider.getIfAvailable()).thenReturn(null);
        return new RuntimeModelOperations(
                mock(SemanticModelCatalogService.class),
                semanticService,
                mock(DetachedModelValidationFactory.class),
                datasetProperties,
                snapshotStoreProvider,
                refreshCoordinatorProvider);
    }

    private static void publish(
            CatalogSnapshotStore store,
            ModelProvenance.ModelSource source
    ) {
        try (CatalogSnapshotStore.CandidateScope scope =
                     store.openCandidate(NAMESPACE)) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(Set.of(MODEL));
            QueryModel model = mock(QueryModel.class);
            when(model.getName()).thenReturn(MODEL);
            when(model.getShortAlias()).thenReturn(candidate.aliasFor(MODEL));
            candidate.putQueryModel(
                    MODEL,
                    model,
                    new ModelProvenance(
                            MODEL,
                            ModelProvenance.ModelKind.QUERY,
                            candidate.sourceRevision(),
                            Set.of(),
                            Map.of(),
                            true,
                            List.of(),
                            source));
            scope.commit();
        }
    }
}
