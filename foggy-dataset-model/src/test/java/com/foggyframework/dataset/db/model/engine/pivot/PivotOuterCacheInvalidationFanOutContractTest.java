package com.foggyframework.dataset.db.model.engine.pivot;

import java.util.List;

class PivotOuterCacheInvalidationFanOutContractTest
        extends PivotOuterCacheInvalidationBroadcasterContractTest {

    @Override
    protected PivotOuterCacheInvalidationBroadcaster newBroadcaster(List<CacheNode> nodes) {
        return (namespace, model) -> nodes.stream()
                .mapToInt(node -> node.provider().evict(namespace, model))
                .sum();
    }
}
