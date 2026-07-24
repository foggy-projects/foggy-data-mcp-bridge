package com.foggyframework.dataset.model.lifecycle.refresh;

import com.foggyframework.dataset.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable routing request for one synchronous catalog refresh attempt. */
public record CatalogRefreshRequest(
        String namespace,
        CatalogRefreshScope scope,
        Set<CatalogModelKey> targets,
        CatalogRefreshTrigger trigger
) {

    public CatalogRefreshRequest {
        namespace = CatalogIdentity.canonicalNamespace(namespace);
        Objects.requireNonNull(scope, "scope");
        targets = immutableTargets(targets);
        Objects.requireNonNull(trigger, "trigger");
        if (scope == CatalogRefreshScope.NAMESPACE && !targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "namespace refresh must not declare model targets");
        }
        if (scope == CatalogRefreshScope.MODELS && targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "model refresh requires at least one canonical target");
        }
    }

    public static CatalogRefreshRequest namespace(
            String namespace,
            CatalogRefreshTrigger trigger
    ) {
        return new CatalogRefreshRequest(
                namespace, CatalogRefreshScope.NAMESPACE, Set.of(), trigger);
    }

    public static CatalogRefreshRequest models(
            String namespace,
            Collection<CatalogModelKey> targets,
            CatalogRefreshTrigger trigger
    ) {
        return new CatalogRefreshRequest(
                namespace,
                CatalogRefreshScope.MODELS,
                immutableTargets(targets),
                trigger);
    }

    private static Set<CatalogModelKey> immutableTargets(
            Collection<CatalogModelKey> source
    ) {
        TreeSet<CatalogModelKey> sorted = new TreeSet<>();
        if (source != null) {
            source.forEach(target -> sorted.add(
                    Objects.requireNonNull(target, "target")));
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }
}
