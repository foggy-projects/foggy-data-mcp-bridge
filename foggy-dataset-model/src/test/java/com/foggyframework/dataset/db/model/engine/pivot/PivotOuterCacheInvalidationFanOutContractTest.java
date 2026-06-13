package com.foggyframework.dataset.db.model.engine.pivot;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PivotOuterCacheInvalidationFanOutContractTest
        extends PivotOuterCacheInvalidationBroadcasterContractTest {

    @Override
    protected PivotOuterCacheInvalidationBroadcaster newBroadcaster(List<CacheNode> nodes) {
        return fanOutBroadcaster(nodes);
    }

    @Test
    @DisplayName("fan-out broadcaster continues after node failure and reports partial result")
    void fanOutContinuesAfterNodeFailureAndReportsPartialResult() {
        List<CacheNode> nodes = List.of(new CacheNode("node-a"), new CacheNode("node-b"));
        nodes.forEach(this::seedCommonEntries);
        PivotOuterCacheInvalidationBroadcaster broadcaster = new PivotOuterCacheInvalidationBroadcaster() {
            @Override
            public int evict(String namespace, String model) {
                return evict(PivotOuterCacheInvalidationEvent.of(namespace, model)).removed();
            }

            @Override
            public PivotOuterCacheInvalidationResult evict(PivotOuterCacheInvalidationEvent event) {
                PivotOuterCacheInvalidationEvent scoped =
                        event == null ? PivotOuterCacheInvalidationEvent.all() : event;
                List<PivotOuterCacheInvalidationResult> results = List.of(
                        PivotOuterCacheInvalidationResult.local(
                                nodes.get(0).provider().evict(scoped.namespace(), scoped.model())),
                        new PivotOuterCacheInvalidationResult(
                                0, 1, 0, 1, List.of("node-b publish failed")));
                return PivotOuterCacheInvalidationResult.aggregate(results);
            }
        };

        PivotOuterCacheInvalidationResult result =
                broadcaster.evict(PivotOuterCacheInvalidationEvent.of("ns-a", "ModelA"));

        assertEquals(1, result.removed());
        assertEquals(2, result.attemptedNodes());
        assertEquals(1, result.succeededNodes());
        assertEquals(1, result.failedNodes());
        assertFalse(result.success());
        assertEquals(List.of("node-b publish failed"), result.errors());
    }

    private PivotOuterCacheInvalidationBroadcaster fanOutBroadcaster(List<CacheNode> nodes) {
        return new PivotOuterCacheInvalidationBroadcaster() {
            @Override
            public int evict(String namespace, String model) {
                return evict(PivotOuterCacheInvalidationEvent.of(namespace, model)).removed();
            }

            @Override
            public PivotOuterCacheInvalidationResult evict(PivotOuterCacheInvalidationEvent event) {
                PivotOuterCacheInvalidationEvent scoped =
                        event == null ? PivotOuterCacheInvalidationEvent.all() : event;
                List<PivotOuterCacheInvalidationResult> results = nodes.stream()
                        .map(node -> PivotOuterCacheInvalidationResult.local(
                                node.provider().evict(scoped.namespace(), scoped.model())))
                        .toList();
                return PivotOuterCacheInvalidationResult.aggregate(results);
            }
        };
    }
}
