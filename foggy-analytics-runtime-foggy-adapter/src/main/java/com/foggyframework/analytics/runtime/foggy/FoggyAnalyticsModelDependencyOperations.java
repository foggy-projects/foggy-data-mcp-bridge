package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelDigest;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyDescription;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyList;
import com.foggyframework.analytics.function.contract.AnalyticsModelSummary;
import com.foggyframework.analytics.runtime.core.function.AnalyticsModelDependencyOperations;
import com.foggyframework.analytics.runtime.core.function.AnalyticsModelDependencyResolutionException;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.semantic.port.SemanticModelCatalogReadPort;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService.NamespaceCatalogView;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.List;
import java.util.Objects;
import java.util.Comparator;
import java.util.Optional;

/** Resolves an internal dependency digest or lists current models from the Foggy catalog. */
public final class FoggyAnalyticsModelDependencyOperations
        implements AnalyticsModelDependencyOperations {

    private final SemanticModelCatalogReadPort catalogReadPort;
    private final FoggyStableModelDigestReadPort digestReadPort;
    private final FoggyAnalyticsNamespaceMapper namespaceMapper;

    public FoggyAnalyticsModelDependencyOperations(
            SemanticModelCatalogReadPort catalogReadPort,
            FoggyStableModelDigestReadPort digestReadPort) {
        this(
                catalogReadPort,
                digestReadPort,
                FoggyAnalyticsNamespaceMapper.defaultConvention());
    }

    public FoggyAnalyticsModelDependencyOperations(
            SemanticModelCatalogReadPort catalogReadPort,
            FoggyStableModelDigestReadPort digestReadPort,
            FoggyAnalyticsNamespaceMapper namespaceMapper) {
        this.catalogReadPort = Objects.requireNonNull(catalogReadPort, "catalogReadPort");
        this.digestReadPort = Objects.requireNonNull(digestReadPort, "digestReadPort");
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
        AnalyticsModelDigest digest = digestReadPort.findDigest(
                        new FoggyModelDigestLookup(
                                catalogIdentity, modelKind, modelName))
                .orElseThrow(FoggyAnalyticsModelDependencyOperations::digestUnavailable);
        return new AnalyticsModelDependencyDescription(
                namespace,
                modelKind,
                modelName,
                digest.value());
    }

    @Override
    public AnalyticsModelDependencyList list(String namespace, String modelKind) {
        if (!"qm".equals(modelKind)) {
            throw new IllegalArgumentException("Only QM dependency listing is supported");
        }
        AnalyticsNamespaceRef namespaceRef = new AnalyticsNamespaceRef(namespace);
        String engineNamespace = canonicalEngineNamespace(namespaceRef);
        List<AnalyticsModelSummary> models = catalogReadPort
                .discoverAvailableQueryModelNames(engineNamespace)
                .stream()
                .map(modelName -> describeAvailableQueryModel(
                        namespace, engineNamespace, modelName))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(AnalyticsModelSummary::modelName))
                .toList();
        return new AnalyticsModelDependencyList(namespace, modelKind, models);
    }

    private Optional<AnalyticsModelSummary> describeAvailableQueryModel(
            String namespace,
            String engineNamespace,
            String modelName) {
        try {
            NamespaceCatalogView view = catalogReadPort.modelCatalogView(
                    engineNamespace, List.of(modelName));
            CatalogIdentity identity = requireCatalog(view, engineNamespace);
            requireExactQueryModel(view, modelName, identity);
            return Optional.of(new AnalyticsModelSummary(namespace, "qm", modelName));
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    private CatalogIdentity requireCatalog(
            NamespaceCatalogView view,
            String engineNamespace) {
        if (view == null
                || view.identity() == null
                || !engineNamespace.equals(view.identity().namespace())) {
            throw digestUnavailable();
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

    private static AnalyticsModelDependencyResolutionException digestUnavailable() {
        return new AnalyticsModelDependencyResolutionException(
                AnalyticsModelDependencyResolutionException.Code.DIGEST_UNAVAILABLE,
                "Stable model dependency digest is unavailable");
    }
}
