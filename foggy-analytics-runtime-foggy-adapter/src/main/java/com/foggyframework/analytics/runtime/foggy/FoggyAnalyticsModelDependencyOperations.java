package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyDescription;
import com.foggyframework.analytics.runtime.core.function.AnalyticsModelDependencyOperations;
import com.foggyframework.analytics.runtime.core.function.AnalyticsModelDependencyResolutionException;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.semantic.port.SemanticModelCatalogReadPort;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService.NamespaceCatalogView;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.List;
import java.util.Objects;

/** Resolves a stable Analytics model dependency from the current Foggy catalog view. */
public final class FoggyAnalyticsModelDependencyOperations
        implements AnalyticsModelDependencyOperations {

    private final SemanticModelCatalogReadPort catalogReadPort;
    private final FoggyStableModelRevisionReadPort revisionReadPort;
    private final FoggyAnalyticsNamespaceMapper namespaceMapper;

    public FoggyAnalyticsModelDependencyOperations(
            SemanticModelCatalogReadPort catalogReadPort,
            FoggyStableModelRevisionReadPort revisionReadPort) {
        this(
                catalogReadPort,
                revisionReadPort,
                FoggyAnalyticsNamespaceMapper.defaultConvention());
    }

    public FoggyAnalyticsModelDependencyOperations(
            SemanticModelCatalogReadPort catalogReadPort,
            FoggyStableModelRevisionReadPort revisionReadPort,
            FoggyAnalyticsNamespaceMapper namespaceMapper) {
        this.catalogReadPort = Objects.requireNonNull(catalogReadPort, "catalogReadPort");
        this.revisionReadPort = Objects.requireNonNull(revisionReadPort, "revisionReadPort");
        this.namespaceMapper = Objects.requireNonNull(namespaceMapper, "namespaceMapper");
    }

    @Override
    public AnalyticsModelDependencyDescription resolve(
            String namespace,
            String modelKind,
            String modelName) {
        AnalyticsNamespaceRef namespaceRef = new AnalyticsNamespaceRef(namespace);
        String engineNamespace = canonicalEngineNamespace(namespaceRef);
        NamespaceCatalogView view = "qm".equals(modelKind)
                ? catalogReadPort.modelCatalogView(engineNamespace, List.of(modelName))
                : catalogReadPort.namespaceCatalogView(engineNamespace);
        CatalogIdentity catalogIdentity = requireCatalog(view, engineNamespace);
        if ("qm".equals(modelKind)) {
            requireExactQueryModel(view, modelName, catalogIdentity);
        }
        AnalyticsModelRevision revision = revisionReadPort.findRevision(
                        new FoggyModelRevisionLookup(
                                catalogIdentity, modelKind, modelName))
                .orElseThrow(FoggyAnalyticsModelDependencyOperations::revisionUnavailable);
        return new AnalyticsModelDependencyDescription(
                namespace,
                modelKind,
                modelName,
                revision.value());
    }

    private CatalogIdentity requireCatalog(
            NamespaceCatalogView view,
            String engineNamespace) {
        if (view == null
                || view.identity() == null
                || !engineNamespace.equals(view.identity().namespace())) {
            throw revisionUnavailable();
        }
        return view.identity();
    }

    private void requireExactQueryModel(
            NamespaceCatalogView view,
            String modelName,
            CatalogIdentity catalogIdentity) {
        CatalogResolution<QueryModel> resolution = view.resolutionsByModel().get(modelName);
        if (resolution == null
                || !modelName.equals(resolution.canonicalName())
                || !catalogIdentity.equals(resolution.catalogIdentity())) {
            throw new AnalyticsModelDependencyResolutionException(
                    AnalyticsModelDependencyResolutionException.Code.MODEL_NOT_FOUND,
                    "Selected model does not exist in the current catalog");
        }
    }

    private String canonicalEngineNamespace(AnalyticsNamespaceRef namespaceRef) {
        String mapped = Objects.requireNonNull(
                namespaceMapper.toEngineNamespace(namespaceRef),
                "mapped engine namespace");
        String canonical = CatalogIdentity.canonicalNamespace(mapped);
        if (!mapped.equals(canonical)) {
            throw new IllegalArgumentException("mapped engine namespace must be canonical");
        }
        return canonical;
    }

    private static AnalyticsModelDependencyResolutionException revisionUnavailable() {
        return new AnalyticsModelDependencyResolutionException(
                AnalyticsModelDependencyResolutionException.Code.REVISION_UNAVAILABLE,
                "Stable model revision is unavailable");
    }
}
