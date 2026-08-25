package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsBundleDependencyState;
import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;
import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelDigest;
import com.foggyframework.analytics.definition.core.AnalyticsBundleDependencyStateResolver;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.semantic.port.SemanticModelCatalogReadPort;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService.NamespaceCatalogView;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Fail-closed dependency currentness check backed by exact Foggy catalog views. */
public final class FoggyAnalyticsBundleDependencyStateResolver
        implements AnalyticsBundleDependencyStateResolver {

    private final SemanticModelCatalogReadPort catalogReadPort;
    private final FoggyStableModelDigestReadPort modelDigestReadPort;
    private final FoggyAnalyticsNamespaceMapper namespaceMapper;

    public FoggyAnalyticsBundleDependencyStateResolver(
            SemanticModelCatalogReadPort catalogReadPort,
            FoggyStableModelDigestReadPort modelDigestReadPort) {
        this(
                catalogReadPort,
                modelDigestReadPort,
                FoggyAnalyticsNamespaceMapper.defaultConvention());
    }

    public FoggyAnalyticsBundleDependencyStateResolver(
            SemanticModelCatalogReadPort catalogReadPort,
            FoggyStableModelDigestReadPort modelDigestReadPort,
            FoggyAnalyticsNamespaceMapper namespaceMapper) {
        this.catalogReadPort = Objects.requireNonNull(catalogReadPort, "catalogReadPort");
        this.modelDigestReadPort = Objects.requireNonNull(
                modelDigestReadPort,
                "modelDigestReadPort");
        this.namespaceMapper = Objects.requireNonNull(namespaceMapper, "namespaceMapper");
    }

    @Override
    public AnalyticsBundleDependencyState resolve(AnalyticsBundleManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        if (manifest.modelDependencies().isEmpty()) {
            return AnalyticsBundleDependencyState.CURRENT;
        }
        try {
            Map<String, NamespaceCatalogView> views = new LinkedHashMap<>();
            for (AnalyticsModelDependency dependency : manifest.modelDependencies()) {
                String engineNamespace = engineNamespace(dependency);
                NamespaceCatalogView view = views.computeIfAbsent(
                        engineNamespace,
                        catalogReadPort::namespaceCatalogView);
                if (!isCurrent(dependency, engineNamespace, view)) {
                    return AnalyticsBundleDependencyState.STALE;
                }
            }
            return AnalyticsBundleDependencyState.CURRENT;
        } catch (RuntimeException unavailable) {
            return AnalyticsBundleDependencyState.STALE;
        }
    }

    private boolean isCurrent(
            AnalyticsModelDependency dependency,
            String engineNamespace,
            NamespaceCatalogView view) {
        if (view == null || view.identity() == null) {
            return false;
        }
        CatalogIdentity catalogIdentity = view.identity();
        if (!engineNamespace.equals(catalogIdentity.namespace())) {
            return false;
        }
        if ("qm".equals(dependency.modelKind())
                && !containsExactQueryModel(view, dependency.modelName())) {
            return false;
        }
        Optional<AnalyticsModelDigest> currentDigest = modelDigestReadPort.findDigest(
                new FoggyModelDigestLookup(
                        catalogIdentity,
                        dependency.modelKind(),
                        dependency.modelName()));
        return currentDigest.filter(dependency.modelDigest()::equals).isPresent();
    }

    private static boolean containsExactQueryModel(
            NamespaceCatalogView view,
            String modelName) {
        CatalogResolution<QueryModel> resolution = view.resolutionsByModel().get(modelName);
        return resolution != null
                && modelName.equals(resolution.canonicalName())
                && view.identity().equals(resolution.catalogIdentity());
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
}
