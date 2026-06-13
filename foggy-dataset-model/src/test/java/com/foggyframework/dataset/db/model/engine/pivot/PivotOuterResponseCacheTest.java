package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PivotOuterResponseCacheTest {

    @Test
    @DisplayName("E1b local cache evicts the oldest entry when maximumSize is exceeded")
    void testEvictsOldestEntry() {
        PivotOuterResponseCache cache = new PivotOuterResponseCache(
                new PivotPipeline.OuterCacheOptions(true, 60_000L, 1));

        cache.store("first", response("first"), 100L);
        cache.store("second", response("second"), 101L);

        assertFalse(cache.lookup("first", 102L).hit(), "oldest entry should be evicted");
        PivotOuterCacheProvider.LookupResult second = cache.lookup("second", 102L);
        assertTrue(second.hit(), "newest entry should remain available");
        assertEquals("second", second.response().getItems().get(0).get("value"));
    }

    @Test
    @DisplayName("E1b local cache returns deep response copies")
    void testReturnsDeepCopies() {
        PivotOuterResponseCache cache = new PivotOuterResponseCache(
                new PivotPipeline.OuterCacheOptions(true, 60_000L, 4));
        SemanticQueryResponse original = response("original");

        cache.store("copy", original, 100L);
        original.getItems().get(0).put("value", "mutated-original");

        SemanticQueryResponse firstLookup = cache.lookup("copy", 101L).response();
        firstLookup.getItems().get(0).put("value", "mutated-lookup");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> diagnostics = (List<Map<String, Object>>) firstLookup.getDebug()
                .getExtra()
                .get("pivotDiagnostics");
        diagnostics.get(0).put("event", "mutated-diagnostic");

        SemanticQueryResponse secondLookup = cache.lookup("copy", 102L).response();
        assertEquals("original", secondLookup.getItems().get(0).get("value"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> secondDiagnostics = (List<Map<String, Object>>) secondLookup.getDebug()
                .getExtra()
                .get("pivotDiagnostics");
        assertEquals("pivot.cache.store", secondDiagnostics.get(0).get("event"));
    }

    @Test
    @DisplayName("E1b local cache concurrent hits do not share mutable response instances")
    void testConcurrentHitsDoNotShareMutableResponses() throws ExecutionException, InterruptedException {
        PivotOuterResponseCache cache = new PivotOuterResponseCache(
                new PivotPipeline.OuterCacheOptions(true, 60_000L, 4));
        cache.store("shared", response("stable"), 100L);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    PivotOuterCacheProvider.LookupResult lookup = cache.lookup("shared", 101L + index);
                    assertTrue(lookup.hit());
                    lookup.response().getItems().get(0).put("value", "mutated-" + index);
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals("stable", cache.lookup("shared", 200L).response().getItems().get(0).get("value"));
    }

    @Test
    @DisplayName("E1b local cache evicts by namespace and model")
    void testEvictsByNamespaceAndModel() {
        PivotOuterResponseCache cache = new PivotOuterResponseCache(
                new PivotPipeline.OuterCacheOptions(true, 60_000L, 8));
        cache.store("default-model-a", response("default-model-a"), 100L, "", "ModelA");
        cache.store("ns-a-model-a", response("ns-a-model-a"), 101L, "ns-a", "ModelA");
        cache.store("ns-a-model-b", response("ns-a-model-b"), 102L, "ns-a", "ModelB");
        cache.store("ns-b-model-a", response("ns-b-model-a"), 103L, "ns-b", "ModelA");

        assertEquals(1, cache.evict("ns-a", "ModelA"));
        assertFalse(cache.lookup("ns-a-model-a", 104L).hit());
        assertTrue(cache.lookup("ns-a-model-b", 104L).hit());
        assertTrue(cache.lookup("ns-b-model-a", 104L).hit());

        assertEquals(2, cache.evict(null, "ModelA"));
        assertFalse(cache.lookup("default-model-a", 105L).hit());
        assertFalse(cache.lookup("ns-b-model-a", 105L).hit());
        assertTrue(cache.lookup("ns-a-model-b", 105L).hit());

        assertEquals(1, cache.evict("ns-a", null));
        assertFalse(cache.lookup("ns-a-model-b", 106L).hit());
    }

    private SemanticQueryResponse response(String value) {
        SemanticQueryResponse response = new SemanticQueryResponse();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("value", value);
        row.put("nested", new LinkedHashMap<>(Map.of("metric", 1)));
        response.setItems(List.of(row));
        response.setTotal(1L);

        SemanticQueryResponse.DebugInfo debug = new SemanticQueryResponse.DebugInfo();
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("event", "pivot.cache.store");
        diagnostic.put("keyHash", "copy");
        debug.setExtra(new LinkedHashMap<>(Map.of(
                "pivotDiagnostics", List.of(diagnostic),
                "pivotEngineContract", Map.of("signed", true)
        )));
        response.setDebug(debug);
        return response;
    }
}
