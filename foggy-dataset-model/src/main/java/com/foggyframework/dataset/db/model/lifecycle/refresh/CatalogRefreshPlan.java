package com.foggyframework.dataset.db.model.lifecycle.refresh;

import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable source-discovery and target selection for one refresh attempt. */
public record CatalogRefreshPlan(
        CatalogRefreshScope scope,
        Set<CatalogModelKey> targets,
        Set<String> discoveredQueryModelNames
) {

    public CatalogRefreshPlan {
        Objects.requireNonNull(scope, "scope");
        targets = immutableModelKeys(targets);
        discoveredQueryModelNames = immutableNames(discoveredQueryModelNames);
        if (scope == CatalogRefreshScope.NAMESPACE && !targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "namespace refresh must not declare model targets");
        }
        if (scope == CatalogRefreshScope.MODELS && targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "model refresh requires at least one canonical target");
        }
    }

    public static CatalogRefreshPlan namespace(Collection<String> discovery) {
        return new CatalogRefreshPlan(
                CatalogRefreshScope.NAMESPACE, Set.of(), immutableNames(discovery));
    }

    public static CatalogRefreshPlan models(
            Collection<CatalogModelKey> targets,
            Collection<String> discovery
    ) {
        return new CatalogRefreshPlan(
                CatalogRefreshScope.MODELS,
                immutableModelKeys(targets),
                immutableNames(discovery));
    }

    private static Set<CatalogModelKey> immutableModelKeys(
            Collection<CatalogModelKey> source
    ) {
        TreeSet<CatalogModelKey> sorted = new TreeSet<>();
        if (source != null) {
            source.forEach(key -> sorted.add(Objects.requireNonNull(key, "target")));
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    private static Set<String> immutableNames(Collection<String> source) {
        TreeSet<String> sorted = new TreeSet<>();
        if (source != null) {
            source.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .forEach(sorted::add);
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }
}
