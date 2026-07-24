package com.foggyframework.dataset.model.backend;

import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.api.backend.AtomicRefreshBackendProvider;
import com.foggyframework.dataset.model.api.backend.AtomicRefreshPort;
import com.foggyframework.dataset.model.api.backend.AtomicRefreshRequest;
import com.foggyframework.dataset.model.api.backend.AtomicRefreshResult;
import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendDescriptor;
import com.foggyframework.dataset.model.api.backend.ModelLoadBackendProvider;
import com.foggyframework.dataset.model.api.backend.ModelLoadPort;
import com.foggyframework.dataset.model.api.backend.ModelLoadRequest;
import com.foggyframework.dataset.model.api.backend.ModelLoadResult;
import com.foggyframework.dataset.model.api.backend.QueryBackendProvider;
import com.foggyframework.dataset.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.model.jdbc.JdbcQueryBackendProvider;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshRequest;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshResult;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshTrigger;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JDBC engine provider exposing only the stable v2 roles backed by the
 * namespace-aware loader and atomic catalog publication path.
 */
public final class JdbcEngineBackendProvider implements QueryBackendProvider,
        ModelLoadBackendProvider, AtomicRefreshBackendProvider {

    private static final BackendDescriptor DESCRIPTOR = new BackendDescriptor(
            JdbcQueryBackendProvider.JDBC,
            Set.of(
                    BackendCapability.QUERY,
                    BackendCapability.MODEL_LOAD,
                    BackendCapability.ATOMIC_REFRESH));

    private final QueryFacade queryFacade;
    private final QueryModelLoaderImpl queryModelLoader;
    private final CatalogRefreshCoordinator refreshCoordinator;
    private final ModelLoadPort modelLoadPort = this::load;
    private final AtomicRefreshPort atomicRefreshPort = this::refresh;

    public JdbcEngineBackendProvider(
            QueryFacade queryFacade,
            QueryModelLoaderImpl queryModelLoader,
            CatalogRefreshCoordinator refreshCoordinator
    ) {
        this.queryFacade = Objects.requireNonNull(queryFacade, "queryFacade");
        this.queryModelLoader = Objects.requireNonNull(queryModelLoader, "queryModelLoader");
        this.refreshCoordinator = Objects.requireNonNull(refreshCoordinator, "refreshCoordinator");
    }

    @Override
    public BackendDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public QueryFacade queryFacade() {
        return queryFacade;
    }

    @Override
    public ModelLoadPort modelLoader() {
        return modelLoadPort;
    }

    @Override
    public AtomicRefreshPort atomicRefresh() {
        return atomicRefreshPort;
    }

    private ModelLoadResult load(ModelLoadRequest request) {
        Objects.requireNonNull(request, "request");
        CatalogResolution<QueryModel> resolution = queryModelLoader.resolveJdbcQueryModel(
                request.modelName(), request.namespace());
        return new ModelLoadResult(
                resolution.canonicalName(),
                resolution.catalogIdentity().namespace(),
                resolution.catalogIdentity().generation().value(),
                resolution.catalogIdentity().sourceRevision().value(),
                resolution.bindingIdentityComplete());
    }

    private AtomicRefreshResult refresh(AtomicRefreshRequest request) {
        Objects.requireNonNull(request, "request");
        CatalogRefreshRequest engineRequest = request.modelNames().isEmpty()
                ? CatalogRefreshRequest.namespace(
                        request.namespace(), CatalogRefreshTrigger.RUNTIME_API)
                : CatalogRefreshRequest.models(
                        request.namespace(),
                        request.modelNames().stream()
                                .map(CatalogModelKey::query)
                                .collect(Collectors.toUnmodifiableSet()),
                        CatalogRefreshTrigger.RUNTIME_API);
        CatalogRefreshResult result = refreshCoordinator.refresh(engineRequest);
        return new AtomicRefreshResult(
                result.namespace(),
                result.beforeIdentity() == null
                        ? null
                        : result.beforeIdentity().generation().value(),
                result.afterIdentity().generation().value(),
                result.sourceRevision().value(),
                modelNames(result.refreshedModels()),
                modelNames(result.preservedModels()),
                result.durationMs());
    }

    private static Set<String> modelNames(Set<CatalogModelKey> keys) {
        return keys.stream()
                .map(key -> key.kind().name().toLowerCase() + ":" + key.canonicalName())
                .collect(Collectors.toUnmodifiableSet());
    }
}
