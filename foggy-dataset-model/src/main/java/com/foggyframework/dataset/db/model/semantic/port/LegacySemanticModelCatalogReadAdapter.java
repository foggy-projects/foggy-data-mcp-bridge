package com.foggyframework.dataset.db.model.semantic.port;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticModelCatalogService.NamespaceCatalogView;
import com.foggyframework.dataset.db.model.spi.NamespaceContext;
import com.foggyframework.dataset.db.model.spi.NamespaceScope;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compatibility adapter for callers that have not adopted the lifecycle
 * catalog authority yet.
 *
 * <p>The loader remains contained inside the model module; addon code only
 * sees the catalog read port. This adapter is intentionally uncached.
 */
@Slf4j
public final class LegacySemanticModelCatalogReadAdapter
        implements SemanticModelCatalogReadPort {

    private final SystemBundlesContext systemBundlesContext;
    private final QueryModelLoader queryModelLoader;

    public LegacySemanticModelCatalogReadAdapter(
            SystemBundlesContext systemBundlesContext,
            QueryModelLoader queryModelLoader
    ) {
        this.systemBundlesContext = Objects.requireNonNull(
                systemBundlesContext, "systemBundlesContext");
        this.queryModelLoader = Objects.requireNonNull(
                queryModelLoader, "queryModelLoader");
    }

    /** Compatibility shape for consumers that historically only held a loader. */
    public LegacySemanticModelCatalogReadAdapter(QueryModelLoader queryModelLoader) {
        this.systemBundlesContext = null;
        this.queryModelLoader = Objects.requireNonNull(
                queryModelLoader, "queryModelLoader");
    }

    @Override
    public NamespaceCatalogView namespaceCatalogView(String namespace) {
        String canonicalNamespace = namespace == null ? "" : namespace.trim();
        LinkedHashMap<String, QueryModel> models = new LinkedHashMap<>();
        if (systemBundlesContext == null) {
            return new NamespaceCatalogView(
                    null, List.of(), Map.of(), Map.of(), Map.of());
        }
        try (NamespaceScope ignored = NamespaceContext.open(canonicalNamespace)) {
            try {
                systemBundlesContext.getBundleList().forEach(bundle -> {
                    try {
                        BundleResource[] resources = bundle.findBundleResources("**/*.qm");
                        if (resources == null) {
                            return;
                        }
                        for (BundleResource resource : resources) {
                            try {
                                QueryModel model = queryModelLoader.loadJdbcQueryModel(resource);
                                if (model != null && model.getName() != null
                                        && !model.getName().isBlank()) {
                                    models.putIfAbsent(model.getName(), model);
                                }
                            } catch (Exception e) {
                                log.debug("Failed to load legacy QM resource {}: {}",
                                        resource.getResource().getDescription(), e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to scan legacy bundle {} for QM files: {}",
                                bundle.getName(), e.getMessage());
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to scan legacy semantic model catalog: {}", e.getMessage());
            }
        }

        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        models.forEach((modelName, model) -> {
            String alias = model.getShortAlias();
            if (alias != null && !alias.isBlank()) {
                aliases.put(modelName, alias);
            }
        });
        return new NamespaceCatalogView(
                null, List.copyOf(models.keySet()), aliases, models, Map.of());
    }

    @Override
    public QueryModel resolveModel(
            NamespaceCatalogView view,
            String nameOrAlias,
            String namespace
    ) {
        String canonicalName = canonicalName(view, nameOrAlias);
        if (canonicalName != null) {
            QueryModel model = view.queryModels().get(canonicalName);
            if (model != null) {
                return model;
            }
        }
        return queryModelLoader.getJdbcQueryModel(nameOrAlias, namespace);
    }

    @Override
    public String resolveAlias(
            NamespaceCatalogView view,
            String nameOrAlias,
            String namespace
    ) {
        String canonicalName = canonicalName(view, nameOrAlias);
        if (canonicalName != null) {
            String alias = view.aliasesByModel().get(canonicalName);
            if (alias != null) {
                return alias;
            }
        }
        QueryModel model = resolveModel(view, nameOrAlias, namespace);
        return model == null ? null : model.getShortAlias();
    }

    private static String canonicalName(
            NamespaceCatalogView view,
            String nameOrAlias
    ) {
        if (view == null || nameOrAlias == null) {
            return null;
        }
        if (view.queryModels().containsKey(nameOrAlias)) {
            return nameOrAlias;
        }
        return view.aliasesByModel().entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue(), nameOrAlias))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
