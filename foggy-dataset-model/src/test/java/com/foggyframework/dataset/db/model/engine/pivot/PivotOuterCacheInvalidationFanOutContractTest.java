package com.foggyframework.dataset.db.model.engine.pivot;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Test
    @DisplayName("fan-out broadcaster deduplicates repeated explicit event ids")
    void fanOutDeduplicatesRepeatedExplicitEventIds() {
        List<CacheNode> nodes = List.of(new CacheNode("node-a"), new CacheNode("node-b"));
        nodes.forEach(this::seedCommonEntries);
        PivotOuterCacheInvalidationBroadcaster broadcaster = deduplicatingFanOutBroadcaster("node-a", nodes);
        PivotOuterCacheInvalidationEvent event =
                PivotOuterCacheInvalidationEvent.of("ns-a", "ModelA")
                        .withMetadata("evt-1", "node-x", 100L);

        PivotOuterCacheInvalidationResult first = broadcaster.evict(event);
        PivotOuterCacheInvalidationResult replay = broadcaster.evict(event.withMetadata("evt-1", "node-y", 101L));

        assertEquals(2, first.removed());
        assertEquals(2, first.attemptedNodes());
        assertEquals(0, replay.removed());
        assertEquals(0, replay.attemptedNodes());
        assertEquals(0, replay.failedNodes());
    }

    @Test
    @DisplayName("fan-out broadcaster skips local self-loop consumption")
    void fanOutSkipsLocalSelfLoopConsumption() {
        List<CacheNode> nodes = List.of(new CacheNode("node-a"), new CacheNode("node-b"));
        nodes.forEach(this::seedCommonEntries);
        PivotOuterCacheInvalidationBroadcaster broadcaster = deduplicatingFanOutBroadcaster("node-a", nodes);
        PivotOuterCacheInvalidationEvent event =
                PivotOuterCacheInvalidationEvent.of("ns-a", "ModelA")
                        .withMetadata("evt-2", "node-a", 100L);

        assertEquals(1, nodes.get(0).provider().evict("ns-a", "ModelA"));
        PivotOuterCacheInvalidationResult result = broadcaster.evict(event);

        assertEquals(1, result.removed());
        assertEquals(1, result.attemptedNodes());
        assertEquals(1, result.succeededNodes());
        assertEquals(0, result.failedNodes());
        assertFalse(nodes.get(0).hit("ns-a-model-a"), "source node should already have applied local eviction");
        assertFalse(nodes.get(1).hit("ns-a-model-a"), "remote node should consume fan-out event");
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

    private PivotOuterCacheInvalidationBroadcaster deduplicatingFanOutBroadcaster(String localNodeId,
                                                                                  List<CacheNode> nodes) {
        return new PivotOuterCacheInvalidationBroadcaster() {
            private final Map<String, PivotOuterCacheInvalidationReplayWindow> replayWindows = nodes.stream()
                    .collect(Collectors.toMap(
                            CacheNode::name,
                            node -> new PivotOuterCacheInvalidationReplayWindow(60_000L, 1024)));

            @Override
            public int evict(String namespace, String model) {
                return evict(PivotOuterCacheInvalidationEvent.of(namespace, model)).removed();
            }

            @Override
            public PivotOuterCacheInvalidationResult evict(PivotOuterCacheInvalidationEvent event) {
                PivotOuterCacheInvalidationEvent scoped =
                        event == null ? PivotOuterCacheInvalidationEvent.all() : event;
                List<PivotOuterCacheInvalidationResult> results = nodes.stream()
                        .filter(node -> replayWindows.get(node.name())
                                .shouldConsume(scoped, node.name(), scoped.issuedAtMillis()))
                        .map(node -> PivotOuterCacheInvalidationResult.local(
                                node.provider().evict(scoped.namespace(), scoped.model())))
                        .toList();
                return PivotOuterCacheInvalidationResult.aggregate(results);
            }
        };
    }
}
