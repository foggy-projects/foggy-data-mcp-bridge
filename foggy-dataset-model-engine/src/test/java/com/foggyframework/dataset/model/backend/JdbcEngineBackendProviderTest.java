package com.foggyframework.dataset.model.backend;

import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.api.backend.AtomicRefreshRequest;
import com.foggyframework.dataset.model.api.backend.AtomicRefreshResult;
import com.foggyframework.dataset.model.api.backend.ModelLoadRequest;
import com.foggyframework.dataset.model.api.backend.ModelLoadResult;
import com.foggyframework.dataset.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogAdmissionState;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshRequest;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshResult;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshScope;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshTrigger;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcEngineBackendProviderTest {

    @Test
    void modelLoadProjectsThePinnedCatalogIdentity() {
        QueryFacade queryFacade = request -> null;
        QueryModelLoaderImpl loader = mock(QueryModelLoaderImpl.class);
        CatalogRefreshCoordinator coordinator = mock(CatalogRefreshCoordinator.class);
        QueryModel model = mock(QueryModel.class);
        SourceRevision revision = new SourceRevision("source:7");
        CatalogIdentity identity = new CatalogIdentity(
                "tenant-a", new CatalogGeneration("catalog:7"), revision);
        CatalogResolution<QueryModel> resolution =
                new CatalogResolution<>("orders", model, identity, Map.of(), false);
        when(loader.resolveJdbcQueryModel("orders", "tenant-a")).thenReturn(resolution);

        JdbcEngineBackendProvider provider =
                new JdbcEngineBackendProvider(queryFacade, loader, coordinator);
        ModelLoadResult result = provider.modelLoader().load(
                new ModelLoadRequest("orders", "tenant-a"));

        assertSame(queryFacade, provider.queryFacade());
        assertEquals("orders", result.modelName());
        assertEquals("tenant-a", result.namespace());
        assertEquals("catalog:7", result.catalogGeneration());
        assertEquals("source:7", result.sourceRevision());
        assertFalse(result.datasourceIdentityComplete());
    }

    @Test
    void atomicRefreshUsesTheGovernedRuntimeApiRoute() {
        QueryFacade queryFacade = request -> null;
        QueryModelLoaderImpl loader = mock(QueryModelLoaderImpl.class);
        CatalogRefreshCoordinator coordinator = mock(CatalogRefreshCoordinator.class);
        SourceRevision revision = new SourceRevision("source:8");
        CatalogIdentity before = new CatalogIdentity(
                "tenant-a", new CatalogGeneration("catalog:7"), new SourceRevision("source:7"));
        CatalogIdentity after = new CatalogIdentity(
                "tenant-a", new CatalogGeneration("catalog:8"), revision);
        when(coordinator.refresh(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new CatalogRefreshResult(
                        "tenant-a",
                        CatalogRefreshScope.MODELS,
                        before,
                        after,
                        revision,
                        Set.of(CatalogModelKey.query("orders")),
                        Set.of(CatalogModelKey.table("customers")),
                        List.of(),
                        12L,
                        CatalogAdmissionState.ACTIVE,
                        List.of()));

        JdbcEngineBackendProvider provider =
                new JdbcEngineBackendProvider(queryFacade, loader, coordinator);
        AtomicRefreshResult result = provider.atomicRefresh().refresh(
                AtomicRefreshRequest.models("tenant-a", Set.of("orders")));

        ArgumentCaptor<CatalogRefreshRequest> request =
                ArgumentCaptor.forClass(CatalogRefreshRequest.class);
        verify(coordinator).refresh(request.capture());
        assertEquals(CatalogRefreshScope.MODELS, request.getValue().scope());
        assertEquals(CatalogRefreshTrigger.RUNTIME_API, request.getValue().trigger());
        assertEquals(Set.of(CatalogModelKey.query("orders")), request.getValue().targets());
        assertEquals("catalog:7", result.beforeGeneration());
        assertEquals("catalog:8", result.afterGeneration());
        assertEquals(Set.of("query:orders"), result.refreshedModels());
        assertEquals(Set.of("table:customers"), result.preservedModels());
        assertEquals(12L, result.durationMs());
    }
}
