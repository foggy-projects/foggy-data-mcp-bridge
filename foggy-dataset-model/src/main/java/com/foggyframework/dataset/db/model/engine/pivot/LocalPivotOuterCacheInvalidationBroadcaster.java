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
        SemanticQueryServiceV3 service = semanticQueryServiceProvider.getIfAvailable();
        return service == null ? 0 : service.evictPivotOuterCache(namespace, model);
    }
}
