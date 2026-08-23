package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityRequest;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityResolver;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.port.SemanticModelCatalogReadPort;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService.NamespaceCatalogView;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.List;
import java.util.Objects;

import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.AUTHORITY_CATALOG_CONFLICT;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.AUTHORITY_CONTEXT_MISSING;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.AUTHORITY_NAMESPACE_MISMATCH;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.AUTHORITY_RESOLUTION_FAILED;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.MODEL_NAME_NOT_CANONICAL;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.MODEL_NOT_FOUND;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.MODEL_REVISION_MISMATCH;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.MODEL_REVISION_UNAVAILABLE;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.UNSUPPORTED_MODEL_KIND;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.UNTRACKED_CATALOG;

/** Resolves an opaque caller authority against one stable-revision-checked Foggy QM. */
public final class FoggyQueryAuthorityResolver
        implements QueryAuthorityResolver<FoggyAnalyticsAuthority> {

    private final SemanticModelCatalogReadPort catalogReadPort;
    private final FoggyStableModelRevisionReadPort modelRevisionReadPort;
    private final FoggySemanticRequestContextResolver semanticContextResolver;
    private final FoggyAnalyticsNamespaceMapper namespaceMapper;

    public FoggyQueryAuthorityResolver(
            SemanticModelCatalogReadPort catalogReadPort,
            FoggyStableModelRevisionReadPort modelRevisionReadPort,
            FoggySemanticRequestContextResolver semanticContextResolver) {
        this(
                catalogReadPort,
                modelRevisionReadPort,
                semanticContextResolver,
                FoggyAnalyticsNamespaceMapper.defaultConvention());
    }

    public FoggyQueryAuthorityResolver(
            SemanticModelCatalogReadPort catalogReadPort,
            FoggyStableModelRevisionReadPort modelRevisionReadPort,
            FoggySemanticRequestContextResolver semanticContextResolver,
            FoggyAnalyticsNamespaceMapper namespaceMapper) {
        this.catalogReadPort = Objects.requireNonNull(catalogReadPort, "catalogReadPort");
        this.modelRevisionReadPort = Objects.requireNonNull(
                modelRevisionReadPort,
                "modelRevisionReadPort");
        this.semanticContextResolver = Objects.requireNonNull(
                semanticContextResolver,
                "semanticContextResolver");
        this.namespaceMapper = Objects.requireNonNull(namespaceMapper, "namespaceMapper");
    }

    @Override
    public FoggyAnalyticsAuthority resolve(QueryAuthorityRequest request) {
        Objects.requireNonNull(request, "request");
        AnalyticsModelDependency dependency = request.modelDependency();
        if (!"qm".equals(dependency.modelKind())) {
            throw failure(
                    UNSUPPORTED_MODEL_KIND,
                    "Analytics query execution requires a QM model dependency");
        }

        String engineNamespace = engineNamespace(dependency);
        NamespaceCatalogView view = catalogReadPort.modelCatalogView(
                engineNamespace,
                List.of(dependency.modelName()));
        CatalogResolution<QueryModel> resolution = requireExactResolution(
                view,
                dependency,
                engineNamespace);
        requireCurrentStableRevision(dependency, resolution.catalogIdentity());

        SemanticRequestContext baseContext;
        try {
            baseContext = semanticContextResolver.resolve(request, resolution);
        } catch (FoggyAnalyticsAdapterException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new FoggyAnalyticsAdapterException(
                    AUTHORITY_RESOLUTION_FAILED,
                    "Host authority resolution failed",
                    failure);
        }
        if (baseContext == null) {
            throw failure(AUTHORITY_CONTEXT_MISSING, "Host authority context is unavailable");
        }
        String authorityNamespace = CatalogIdentity.canonicalNamespace(baseContext.getNamespace());
        if (!resolution.catalogIdentity().namespace().equals(authorityNamespace)) {
            throw failure(
                    AUTHORITY_NAMESPACE_MISMATCH,
                    "Host authority context belongs to another namespace");
        }

        SemanticRequestContext pinnedContext;
        try {
            pinnedContext = baseContext.withCatalogResolution(resolution);
        } catch (IllegalArgumentException | IllegalStateException conflict) {
            throw new FoggyAnalyticsAdapterException(
                    AUTHORITY_CATALOG_CONFLICT,
                    "Host authority context conflicts with the selected catalog",
                    conflict);
        }
        return new FoggyAnalyticsAuthority(
                dependency,
                engineNamespace,
                resolution,
                pinnedContext);
    }

    private CatalogResolution<QueryModel> requireExactResolution(
            NamespaceCatalogView view,
            AnalyticsModelDependency dependency,
            String engineNamespace) {
        if (view == null || view.identity() == null) {
            throw failure(UNTRACKED_CATALOG, "Foggy catalog does not expose a runtime identity");
        }
        if (!engineNamespace.equals(view.identity().namespace())) {
            throw failure(UNTRACKED_CATALOG, "Foggy catalog namespace identity is inconsistent");
        }
        CatalogResolution<QueryModel> resolution = view.resolutionsByModel()
                .get(dependency.modelName());
        if (resolution == null) {
            if (view.aliasesByModel().containsValue(dependency.modelName())) {
                throw failure(
                        MODEL_NAME_NOT_CANONICAL,
                        "Analytics model dependency must use the canonical QM name");
            }
            throw failure(MODEL_NOT_FOUND, "Pinned Analytics query model is unavailable");
        }
        if (!dependency.modelName().equals(resolution.canonicalName())
                || !view.identity().equals(resolution.catalogIdentity())) {
            throw failure(UNTRACKED_CATALOG, "Foggy catalog resolution is inconsistent");
        }
        return resolution;
    }

    private void requireCurrentStableRevision(
            AnalyticsModelDependency dependency,
            CatalogIdentity catalogIdentity) {
        AnalyticsModelRevision currentRevision = modelRevisionReadPort.findRevision(
                        new FoggyModelRevisionLookup(
                                catalogIdentity,
                                dependency.modelKind(),
                                dependency.modelName()))
                .orElseThrow(() -> failure(
                        MODEL_REVISION_UNAVAILABLE,
                        "Stable model revision is unavailable for the selected catalog"));
        if (!dependency.modelRevision().equals(currentRevision)) {
            throw failure(
                    MODEL_REVISION_MISMATCH,
                    "Pinned Analytics model revision is stale");
        }
    }

    private String engineNamespace(AnalyticsModelDependency dependency) {
        String mapped = Objects.requireNonNull(
                namespaceMapper.toEngineNamespace(dependency.namespace()),
                "mapped engine namespace");
        String canonical = CatalogIdentity.canonicalNamespace(mapped);
        if (!mapped.equals(canonical)) {
            throw new IllegalArgumentException("mapped engine namespace must be canonical");
        }
        return canonical;
    }

    private static FoggyAnalyticsAdapterException failure(
            FoggyAnalyticsAdapterException.Code code,
            String message) {
        return new FoggyAnalyticsAdapterException(code, message);
    }
}
