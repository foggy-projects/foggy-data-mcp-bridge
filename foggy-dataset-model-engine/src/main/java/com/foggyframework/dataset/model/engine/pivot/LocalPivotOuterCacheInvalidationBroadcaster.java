package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.dataset.model.semantic.port.PivotOuterCacheEvictionPort;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Process-local invalidation broadcaster.
 */
public class LocalPivotOuterCacheInvalidationBroadcaster implements PivotOuterCacheInvalidationBroadcaster {

    private final ObjectProvider<PivotOuterCacheEvictionPort> evictionPortProvider;

    public LocalPivotOuterCacheInvalidationBroadcaster(
            ObjectProvider<PivotOuterCacheEvictionPort> evictionPortProvider) {
        this.evictionPortProvider = evictionPortProvider;
    }

    @Override
    public int evict(String namespace, String model) {
        return evict(PivotOuterCacheInvalidationEvent.of(namespace, model)).removed();
    }

    @Override
    public PivotOuterCacheInvalidationResult evict(PivotOuterCacheInvalidationEvent event) {
        PivotOuterCacheInvalidationEvent scoped =
                event == null ? PivotOuterCacheInvalidationEvent.all() : event;
        PivotOuterCacheEvictionPort evictionPort = evictionPortProvider.getIfAvailable();
        if (evictionPort == null) {
            return PivotOuterCacheInvalidationResult.unavailable("PivotOuterCacheEvictionPort is unavailable");
        }
        return PivotOuterCacheInvalidationResult.local(
                evictionPort.evictPivotOuterCache(scoped.namespace(), scoped.model()));
    }
}
