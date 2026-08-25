package com.foggyframework.analytics.console.security;

import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;
import com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException;
import com.foggyframework.analytics.runtime.foggy.FoggySemanticAuthorityRequest;
import com.foggyframework.analytics.runtime.foggy.FoggySemanticRequestContextResolver;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.Objects;

/** Local-only authority bridge; host-managed deployments must provide their own resolver. */
public final class StaticDevFoggySemanticRequestContextResolver
        implements FoggySemanticRequestContextResolver {

    private final QueryAuthorityBinding expectedBinding;

    public StaticDevFoggySemanticRequestContextResolver(
            AnalyticsConsoleProperties properties) {
        AnalyticsConsoleProperties.DevSubject subject = Objects.requireNonNull(
                properties,
                "properties").getDevSubject();
        this.expectedBinding = new QueryAuthorityBinding(
                subject.getAuthorityProvider(),
                subject.getAuthorityReference());
    }

    @Override
    public SemanticRequestContext resolve(
            FoggySemanticAuthorityRequest request,
            CatalogResolution<QueryModel> catalogResolution) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(catalogResolution, "catalogResolution");
        if (!expectedBinding.equals(request.binding())) {
            throw new FoggyAnalyticsAdapterException(
                    FoggyAnalyticsAdapterException.Code.AUTHORITY_MISMATCH,
                    "Analytics authority binding does not match the local development subject");
        }
        return SemanticRequestContext.ofNamespace(
                catalogResolution.catalogIdentity().namespace());
    }
}
