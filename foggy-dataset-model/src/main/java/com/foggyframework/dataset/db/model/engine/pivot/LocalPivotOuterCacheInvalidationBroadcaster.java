package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Process-local invalidation broadcaster.
 */
public class LocalPivotOuterCacheInvalidationBroadcaster implements PivotOuterCacheInvalidationBroadcaster {

    private final ObjectProvider<SemanticQueryServiceV3> semanticQueryServiceProvider;

    public LocalPivotOuterCacheInvalidationBroadcaster(
            ObjectProvider<SemanticQueryServiceV3> semanticQueryServiceProvider) {
        this.semanticQueryServiceProvider = semanticQueryServiceProvider;
    }

    @Override
    public int evict(String namespace, String model) {
        return evict(PivotOuterCacheInvalidationEvent.of(namespace, model)).removed();
    }

    @Override
    public PivotOuterCacheInvalidationResult evict(PivotOuterCacheInvalidationEvent event) {
        PivotOuterCacheInvalidationEvent scoped =
                event == null ? PivotOuterCacheInvalidationEvent.all() : event;
        SemanticQueryServiceV3 service = semanticQueryServiceProvider.getIfAvailable();
        if (service == null) {
            return PivotOuterCacheInvalidationResult.unavailable("SemanticQueryServiceV3 is unavailable");
        }
        return PivotOuterCacheInvalidationResult.local(
                service.evictPivotOuterCache(scoped.namespace(), scoped.model()));
    }
}
