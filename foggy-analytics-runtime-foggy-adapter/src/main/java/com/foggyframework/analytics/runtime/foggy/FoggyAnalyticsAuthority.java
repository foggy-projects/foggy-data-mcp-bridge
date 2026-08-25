package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.Objects;
import java.util.Optional;

/** Adapter-owned authority carrying a request-local catalog pin and optional Bundle dependency. */
public final class FoggyAnalyticsAuthority {

    private final AnalyticsModelDependency modelDependency;
    private final AnalyticsNamespaceRef namespace;
    private final String modelName;
    private final String engineNamespace;
    private final CatalogResolution<QueryModel> catalogResolution;
    private final SemanticRequestContext semanticRequestContext;

    FoggyAnalyticsAuthority(
            AnalyticsModelDependency modelDependency,
            String engineNamespace,
            CatalogResolution<QueryModel> catalogResolution,
            SemanticRequestContext semanticRequestContext) {
        this(
                Objects.requireNonNull(modelDependency, "modelDependency"),
                modelDependency.namespace(),
                modelDependency.modelName(),
                engineNamespace,
                catalogResolution,
                semanticRequestContext);
    }

    private FoggyAnalyticsAuthority(
            AnalyticsModelDependency modelDependency,
            AnalyticsNamespaceRef namespace,
            String modelName,
            String engineNamespace,
            CatalogResolution<QueryModel> catalogResolution,
            SemanticRequestContext semanticRequestContext) {
        this.modelDependency = modelDependency;
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.modelName = Objects.requireNonNull(modelName, "modelName");
        this.engineNamespace = requireCanonicalNamespace(engineNamespace);
        this.catalogResolution = Objects.requireNonNull(catalogResolution, "catalogResolution");
        this.semanticRequestContext = Objects.requireNonNull(
                semanticRequestContext,
                "semanticRequestContext");
        if (modelDependency != null && !"qm".equals(modelDependency.modelKind())) {
            throw new IllegalArgumentException("Foggy query authority requires a QM dependency");
        }
        if (!modelName.equals(catalogResolution.canonicalName())) {
            throw new IllegalArgumentException("model selection does not match catalog resolution");
        }
        if (!this.engineNamespace.equals(catalogResolution.catalogIdentity().namespace())) {
            throw new IllegalArgumentException("engine namespace does not match catalog");
        }
        if (!sameResolution(
                catalogResolution,
                semanticRequestContext.getCatalogResolution())) {
            throw new IllegalArgumentException("semantic request context is not catalog-pinned");
        }
    }

    static FoggyAnalyticsAuthority current(
            AnalyticsNamespaceRef namespace,
            String modelName,
            String engineNamespace,
            CatalogResolution<QueryModel> catalogResolution,
            SemanticRequestContext semanticRequestContext) {
        return new FoggyAnalyticsAuthority(
                null,
                namespace,
                modelName,
                engineNamespace,
                catalogResolution,
                semanticRequestContext);
    }

    public AnalyticsModelDependency modelDependency() {
        return pinnedModelDependency().orElseThrow(() -> new IllegalStateException(
                "Current-model authority has no pinned Bundle dependency"));
    }

    public Optional<AnalyticsModelDependency> pinnedModelDependency() {
        return Optional.ofNullable(modelDependency);
    }

    public String modelName() {
        return modelName;
    }

    public AnalyticsNamespaceRef namespace() {
        return namespace;
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
