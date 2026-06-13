package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reusable contract for Pivot outer-cache invalidation fan-out implementations.
 */
public abstract class PivotOuterCacheInvalidationBroadcasterContractTest {

    protected abstract PivotOuterCacheInvalidationBroadcaster newBroadcaster(List<CacheNode> nodes);

    @Test
    @DisplayName("broadcaster evicts selected namespace and model on every node")
    void evictsSelectedNamespaceAndModelOnEveryNode() {
        List<CacheNode> nodes = List.of(new CacheNode("node-a"), new CacheNode("node-b"));
        nodes.forEach(this::seedCommonEntries);
        PivotOuterCacheInvalidationBroadcaster broadcaster = newBroadcaster(nodes);

        assertEquals(2, broadcaster.evict("ns-a", "ModelA"));

        for (CacheNode node : nodes) {
            assertFalse(node.hit("ns-a-model-a"), node.name() + " should evict ns-a ModelA");
            assertTrue(node.hit("ns-a-model-b"), node.name() + " should keep ns-a ModelB");
            assertTrue(node.hit("ns-b-model-a"), node.name() + " should keep ns-b ModelA");
            assertTrue(node.hit("default-model-a"), node.name() + " should keep default ModelA");
        }
    }

    @Test
    @DisplayName("broadcaster evicts all models in selected namespace on every node")
    void evictsAllModelsInSelectedNamespaceOnEveryNode() {
        List<CacheNode> nodes = List.of(new CacheNode("node-a"), new CacheNode("node-b"));
        nodes.forEach(this::seedCommonEntries);
        PivotOuterCacheInvalidationBroadcaster broadcaster = newBroadcaster(nodes);

        assertEquals(4, broadcaster.evict("ns-a", null));

        for (CacheNode node : nodes) {
            assertFalse(node.hit("ns-a-model-a"), node.name() + " should evict ns-a ModelA");
            assertFalse(node.hit("ns-a-model-b"), node.name() + " should evict ns-a ModelB");
            assertTrue(node.hit("ns-b-model-a"), node.name() + " should keep ns-b ModelA");
            assertTrue(node.hit("default-model-a"), node.name() + " should keep default ModelA");
        }
    }

    @Test
    @DisplayName("broadcaster treats blank model as all models in selected namespace")
    void blankModelMeansAllModelsInSelectedNamespace() {
        List<CacheNode> nodes = List.of(new CacheNode("node-a"), new CacheNode("node-b"));
        nodes.forEach(this::seedCommonEntries);
        PivotOuterCacheInvalidationBroadcaster broadcaster = newBroadcaster(nodes);

        assertEquals(4, broadcaster.evict("ns-a", " "));

        for (CacheNode node : nodes) {
            assertFalse(node.hit("ns-a-model-a"), node.name() + " should evict ns-a ModelA");
            assertFalse(node.hit("ns-a-model-b"), node.name() + " should evict ns-a ModelB");
            assertTrue(node.hit("ns-b-model-a"), node.name() + " should keep ns-b ModelA");
        }
    }

    @Test
    @DisplayName("broadcaster evicts selected model across all namespaces on every node")
    void evictsSelectedModelAcrossAllNamespacesOnEveryNode() {
        List<CacheNode> nodes = List.of(new CacheNode("node-a"), new CacheNode("node-b"));
        nodes.forEach(this::seedCommonEntries);
        PivotOuterCacheInvalidationBroadcaster broadcaster = newBroadcaster(nodes);

        assertEquals(6, broadcaster.evict(null, "ModelA"));

        for (CacheNode node : nodes) {
            assertFalse(node.hit("default-model-a"), node.name() + " should evict default ModelA");
            assertFalse(node.hit("ns-a-model-a"), node.name() + " should evict ns-a ModelA");
            assertFalse(node.hit("ns-b-model-a"), node.name() + " should evict ns-b ModelA");
            assertTrue(node.hit("ns-a-model-b"), node.name() + " should keep ns-a ModelB");
        }
    }

    @Test
    @DisplayName("broadcaster default namespace scope is distinct from named namespaces")
    void defaultNamespaceScopeIsDistinctFromNamedNamespaces() {
        List<CacheNode> nodes = List.of(new CacheNode("node-a"), new CacheNode("node-b"));
        nodes.forEach(this::seedCommonEntries);
        PivotOuterCacheInvalidationBroadcaster broadcaster = newBroadcaster(nodes);

        assertEquals(2, broadcaster.evict("", "ModelA"));

        for (CacheNode node : nodes) {
            assertFalse(node.hit("default-model-a"), node.name() + " should evict default ModelA");
            assertTrue(node.hit("ns-a-model-a"), node.name() + " should keep ns-a ModelA");
            assertTrue(node.hit("ns-b-model-a"), node.name() + " should keep ns-b ModelA");
        }
    }

    @Test
    @DisplayName("broadcaster all-scope eviction removes all nodes")
    void allScopeEvictionRemovesAllNodes() {
        List<CacheNode> nodes = List.of(new CacheNode("node-a"), new CacheNode("node-b"));
        nodes.forEach(this::seedCommonEntries);
        PivotOuterCacheInvalidationBroadcaster broadcaster = newBroadcaster(nodes);

        assertEquals(8, broadcaster.evict(null, null));
        assertEquals(0, broadcaster.evict(null, null), "all-scope eviction should be idempotent after removal");

        for (CacheNode node : nodes) {
            assertFalse(node.hit("default-model-a"));
            assertFalse(node.hit("ns-a-model-a"));
            assertFalse(node.hit("ns-a-model-b"));
            assertFalse(node.hit("ns-b-model-a"));
        }
    }

    protected void seedCommonEntries(CacheNode node) {
        node.store("default-model-a", "", "ModelA");
        node.store("ns-a-model-a", "ns-a", "ModelA");
        node.store("ns-a-model-b", "ns-a", "ModelB");
        node.store("ns-b-model-a", "ns-b", "ModelA");
    }

    protected static final class CacheNode {
        private final String name;
        private final PivotOuterCacheProvider provider;

        CacheNode(String name) {
            this.name = name;
            this.provider = new PivotOuterResponseCache(new PivotPipeline.OuterCacheOptions(true, 60_000L, 16));
        }

        String name() {
            return name;
        }

        PivotOuterCacheProvider provider() {
            return provider;
        }

        void store(String key, String namespace, String model) {
            provider.store(key, response(key), 100L, namespace, model);
        }

        boolean hit(String key) {
            return provider.lookup(key, 101L).hit();
        }

        private SemanticQueryResponse response(String value) {
            SemanticQueryResponse response = new SemanticQueryResponse();
            response.setItems(List.of(new LinkedHashMap<>(Map.of("value", value))));
            response.setTotal(1L);
            return response;
        }
    }
}
