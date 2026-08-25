package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelDigest;
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
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.MODEL_DIGEST_MISMATCH;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.MODEL_DIGEST_UNAVAILABLE;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.UNSUPPORTED_MODEL_KIND;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.UNTRACKED_CATALOG;

/** Resolves an opaque caller authority against one request-local Foggy catalog resolution. */
public final class FoggyQueryAuthorityResolver
        implements QueryAuthorityResolver<FoggyAnalyticsAuthority> {

    private final SemanticModelCatalogReadPort catalogReadPort;
    private final FoggyStableModelDigestReadPort modelDigestReadPort;
    private final FoggySemanticRequestContextResolver semanticContextResolver;
    private final FoggyAnalyticsNamespaceMapper namespaceMapper;

    /** Creates a resolver for live Functions, which never consult Bundle digests. */
    public FoggyQueryAuthorityResolver(
            SemanticModelCatalogReadPort catalogReadPort,
            FoggySemanticRequestContextResolver semanticContextResolver) {
        this(
                catalogReadPort,
                null,
                semanticContextResolver,
                FoggyAnalyticsNamespaceMapper.defaultConvention());
    }

    /** Creates a resolver that also supports persisted Bundle query dependencies. */
    public FoggyQueryAuthorityResolver(
            SemanticModelCatalogReadPort catalogReadPort,
            FoggyStableModelDigestReadPort modelDigestReadPort,
            FoggySemanticRequestContextResolver semanticContextResolver) {
        this(
                catalogReadPort,
                modelDigestReadPort,
                semanticContextResolver,
                FoggyAnalyticsNamespaceMapper.defaultConvention());
    }

    public FoggyQueryAuthorityResolver(
            SemanticModelCatalogReadPort catalogReadPort,
            FoggyStableModelDigestReadPort modelDigestReadPort,
            FoggySemanticRequestContextResolver semanticContextResolver,
            FoggyAnalyticsNamespaceMapper namespaceMapper) {
        this.catalogReadPort = Objects.requireNonNull(catalogReadPort, "catalogReadPort");
        this.modelDigestReadPort = modelDigestReadPort;
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
                dependency.modelName(),
                engineNamespace);
        requireCurrentStableDigest(dependency, resolution.catalogIdentity());
        SemanticRequestContext pinnedContext = resolveContext(
                new FoggySemanticAuthorityRequest(
                        dependency.namespace(),
                        dependency.modelName(),
                        request.binding(),
                        request.requestId(),
                        request.traceId()),
                resolution);
        return new FoggyAnalyticsAuthority(
                dependency,
                engineNamespace,
                resolution,
                pinnedContext);
    }

    /** Resolves the current valid model once and carries that exact resolution through the call. */
    public FoggyAnalyticsAuthority resolveCurrent(
            FoggyCurrentQueryAuthorityRequest request) {
        Objects.requireNonNull(request, "request");
        String engineNamespace = engineNamespace(request.namespace());
        NamespaceCatalogView view = catalogReadPort.modelCatalogView(
                engineNamespace,
                List.of(request.modelName()));
        CatalogResolution<QueryModel> resolution = requireExactResolution(
                view,
                request.modelName(),
                engineNamespace);
        SemanticRequestContext pinnedContext = resolveContext(
                new FoggySemanticAuthorityRequest(
                        request.namespace(),
                        request.modelName(),
                        request.binding(),
                        request.requestId(),
                        request.traceId()),
                resolution);
        return FoggyAnalyticsAuthority.current(
                request.namespace(),
                request.modelName(),
                engineNamespace,
                resolution,
                pinnedContext);
    }

    private SemanticRequestContext resolveContext(
            FoggySemanticAuthorityRequest request,
            CatalogResolution<QueryModel> resolution) {
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
        try {
            return baseContext.withCatalogResolution(resolution);
        } catch (IllegalArgumentException | IllegalStateException conflict) {
            throw new FoggyAnalyticsAdapterException(
                    AUTHORITY_CATALOG_CONFLICT,
                    "Host authority context conflicts with the selected catalog",
                    conflict);
        }
    }

    private CatalogResolution<QueryModel> requireExactResolution(
            NamespaceCatalogView view,
            String modelName,
            String engineNamespace) {
        if (view == null || view.identity() == null) {
            throw failure(UNTRACKED_CATALOG, "Foggy catalog does not expose a runtime identity");
        }
        if (!engineNamespace.equals(view.identity().namespace())) {
            throw failure(UNTRACKED_CATALOG, "Foggy catalog namespace identity is inconsistent");
        }
        CatalogResolution<QueryModel> resolution = view.resolutionsByModel()
                .get(modelName);
        if (resolution == null) {
            if (view.aliasesByModel().containsValue(modelName)) {
                throw failure(
                        MODEL_NAME_NOT_CANONICAL,
                        "Analytics query model must use the canonical QM name");
            }
            throw failure(MODEL_NOT_FOUND, "Requested Analytics query model is unavailable");
        }
        if (!modelName.equals(resolution.canonicalName())
                || !view.identity().equals(resolution.catalogIdentity())) {
            throw failure(UNTRACKED_CATALOG, "Foggy catalog resolution is inconsistent");
        }
        return resolution;
    }

    private void requireCurrentStableDigest(
            AnalyticsModelDependency dependency,
            CatalogIdentity catalogIdentity) {
        if (modelDigestReadPort == null) {
            throw failure(
                    MODEL_DIGEST_UNAVAILABLE,
                    "Internal model digest is unavailable for Bundle execution");
        }
        AnalyticsModelDigest currentDigest = modelDigestReadPort.findDigest(
                        new FoggyModelDigestLookup(
                                catalogIdentity,
                                dependency.modelKind(),
                                dependency.modelName()))
                .orElseThrow(() -> failure(
                        MODEL_DIGEST_UNAVAILABLE,
                        "Internal model digest is unavailable for the selected catalog"));
        if (!dependency.modelDigest().equals(currentDigest)) {
            throw failure(
                    MODEL_DIGEST_MISMATCH,
                    "Persisted Analytics model digest is stale");
        }
    }

    private String engineNamespace(AnalyticsModelDependency dependency) {
        return engineNamespace(dependency.namespace());
    }

    private String engineNamespace(
            com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef namespace) {
        String mapped = Objects.requireNonNull(
                namespaceMapper.toEngineNamespace(namespace),
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
