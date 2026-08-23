package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.Objects;

/** Adapter-owned authority carrying both stable model identity and a request-local catalog pin. */
public final class FoggyAnalyticsAuthority {

    private final AnalyticsModelDependency modelDependency;
    private final String engineNamespace;
    private final CatalogResolution<QueryModel> catalogResolution;
    private final SemanticRequestContext semanticRequestContext;

    FoggyAnalyticsAuthority(
            AnalyticsModelDependency modelDependency,
            String engineNamespace,
            CatalogResolution<QueryModel> catalogResolution,
            SemanticRequestContext semanticRequestContext) {
        this.modelDependency = Objects.requireNonNull(modelDependency, "modelDependency");
        this.engineNamespace = requireCanonicalNamespace(engineNamespace);
        this.catalogResolution = Objects.requireNonNull(catalogResolution, "catalogResolution");
        this.semanticRequestContext = Objects.requireNonNull(
                semanticRequestContext,
                "semanticRequestContext");
        if (!"qm".equals(modelDependency.modelKind())) {
            throw new IllegalArgumentException("Foggy query authority requires a QM dependency");
        }
        if (!modelDependency.modelName().equals(catalogResolution.canonicalName())) {
            throw new IllegalArgumentException("model dependency does not match catalog resolution");
        }
        if (!this.engineNamespace.equals(catalogResolution.catalogIdentity().namespace())) {
            throw new IllegalArgumentException("model dependency namespace does not match catalog");
        }
        if (!sameResolution(
                catalogResolution,
                semanticRequestContext.getCatalogResolution())) {
            throw new IllegalArgumentException("semantic request context is not catalog-pinned");
        }
    }

    public AnalyticsModelDependency modelDependency() {
        return modelDependency;
    }

    public CatalogResolution<QueryModel> catalogResolution() {
        return catalogResolution;
    }

    public String engineNamespace() {
        return engineNamespace;
    }

    public CatalogIdentity catalogIdentity() {
        return catalogResolution.catalogIdentity();
    }

    public SemanticRequestContext semanticRequestContext() {
        return semanticRequestContext;
    }

    private static boolean sameResolution(
            CatalogResolution<QueryModel> left,
            CatalogResolution<QueryModel> right) {
        return right != null
                && left.model() == right.model()
                && left.canonicalName().equals(right.canonicalName())
                && left.catalogIdentity().equals(right.catalogIdentity())
                && left.dependencyBindings().equals(right.dependencyBindings())
                && left.bindingIdentityComplete() == right.bindingIdentityComplete();
    }

    private static String requireCanonicalNamespace(String namespace) {
        Objects.requireNonNull(namespace, "engineNamespace");
        String canonical = CatalogIdentity.canonicalNamespace(namespace);
        if (!namespace.equals(canonical)) {
            throw new IllegalArgumentException("engineNamespace must be canonical");
        }
        return canonical;
    }
}
