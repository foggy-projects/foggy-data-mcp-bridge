package com.foggyframework.dataset.db.model.engine.pivot;

/**
 * Broadcast boundary for Pivot outer-cache invalidation.
 *
 * <p>The default implementation clears the current JVM. Multi-node
 * deployments can replace this bean with a registry/event-bus backed
 * implementation and still call the same engine hook.</p>
 *
 * <p>Scope rules mirror {@link PivotOuterCacheProvider#evict(String, String)}:
 * {@code namespace == null} targets all namespaces, blank namespace targets
 * the default namespace, and blank model targets all models in the selected
 * namespace scope. Implementations should return the aggregate number of
 * removed entries across the reached nodes/providers.</p>
 */
public interface PivotOuterCacheInvalidationBroadcaster {

    int evict(String namespace, String model);
}
