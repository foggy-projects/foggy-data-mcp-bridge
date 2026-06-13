package com.foggyframework.dataset.db.model.engine.pivot;

/**
 * Broadcast boundary for Pivot outer-cache invalidation.
 *
 * <p>The default implementation clears the current JVM. Multi-node
 * deployments can replace this bean with a registry/event-bus backed
 * implementation and still call the same engine hook.</p>
 */
public interface PivotOuterCacheInvalidationBroadcaster {

    int evict(String namespace, String model);
}
