package com.foggyframework.dataset.model.semantic.port;

import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService.NamespaceCatalogView;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Internal read boundary for namespace-scoped semantic model discovery.
 *
 * <p>Addons consume this port instead of invoking {@code QueryModelLoader}
 * directly. The returned view is immutable and, when available, pinned to one
 * lifecycle catalog identity.
 */
public interface SemanticModelCatalogReadPort {

    NamespaceCatalogView namespaceCatalogView(String namespace);

    /**
     * Returns a view containing only the requested models.
     *
     * <p>Lifecycle-aware implementations can override this method to
     * materialize the requested subset without refreshing the whole
     * namespace. The default keeps compatibility with legacy/custom ports by
     * filtering their namespace view.</p>
     */
    default NamespaceCatalogView modelCatalogView(
            String namespace,
            Collection<String> modelNames
    ) {
        NamespaceCatalogView namespaceView = namespaceCatalogView(namespace);
        if (modelNames == null) {
            return namespaceView;
        }

        LinkedHashSet<String> requested = new LinkedHashSet<>();
        modelNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .forEach(requested::add);

        LinkedHashSet<String> canonicalNames = new LinkedHashSet<>();
        for (String requestedName : requested) {
            String canonical = canonicalName(namespaceView, requestedName);
            if (canonical != null) {
                canonicalNames.add(canonical);
            }
        }

        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        LinkedHashMap<String, QueryModel> models = new LinkedHashMap<>();
        LinkedHashMap<String, CatalogResolution<QueryModel>> resolutions =
                new LinkedHashMap<>();
        for (String canonicalName : canonicalNames) {
            aliases.put(canonicalName,
                    namespaceView.aliasesByModel().get(canonicalName));
            models.put(canonicalName,
                    namespaceView.queryModels().get(canonicalName));
            if (namespaceView.identity() != null) {
                resolutions.put(canonicalName,
                        namespaceView.resolutionsByModel().get(canonicalName));
            }
        }
        return new NamespaceCatalogView(
                namespaceView.identity(),
                List.copyOf(canonicalNames),
                aliases,
                models,
                resolutions);
    }

    default List<String> getAllModelNames(String namespace) {
        return namespaceCatalogView(namespace).modelNames();
    }

    default QueryModel resolveModel(
            NamespaceCatalogView view,
            String nameOrAlias,
            String namespace) {
        return resolveModelFromView(view, nameOrAlias);
    }

    default String resolveAlias(
            NamespaceCatalogView view,
            String nameOrAlias,
            String namespace) {
        return resolveAliasFromView(view, nameOrAlias);
    }

    static QueryModel resolveModelFromView(
            NamespaceCatalogView view,
            String nameOrAlias
    ) {
        String canonicalName = canonicalName(view, nameOrAlias);
        if (canonicalName == null) {
            return null;
        }
        if (view.identity() != null) {
            CatalogResolution<QueryModel> resolution = view.resolutionsByModel()
                    .get(canonicalName);
            return resolution == null ? null : resolution.model();
        }
        return view.queryModels().get(canonicalName);
    }

    static String resolveAliasFromView(
            NamespaceCatalogView view,
            String nameOrAlias
    ) {
        String canonicalName = canonicalName(view, nameOrAlias);
        return canonicalName == null
                ? null
                : view.aliasesByModel().get(canonicalName);
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
