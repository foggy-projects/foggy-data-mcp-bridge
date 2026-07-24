package com.foggyframework.dataset.model.engine.pivot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PivotOuterResponseCacheTest extends PivotOuterCacheProviderContractTest {

    @Override
    protected PivotOuterCacheProvider newEnabledProvider(long ttlMillis, int maximumSize) {
        return new PivotOuterResponseCache(new PivotPipeline.OuterCacheOptions(true, ttlMillis, maximumSize));
    }

    @Override
    protected PivotOuterCacheProvider newDisabledProvider() {
        return new PivotOuterResponseCache(new PivotPipeline.OuterCacheOptions(false, 60_000L, 4));
    }

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
}
