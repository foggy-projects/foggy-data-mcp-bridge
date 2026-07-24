package com.foggyframework.dataset.model.engine.pivot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PivotDiagnosticCollectorTest {

    @Test
    @DisplayName("provider unavailable diagnostics keep the response contract stable")
    void testCacheProviderUnavailableDiagnosticsContract() {
        PivotDiagnosticCollector collector = new PivotDiagnosticCollector("SalesPivot");
        PivotOuterCacheSafeProvider.UnavailableEvent unavailable =
                new PivotOuterCacheSafeProvider.UnavailableEvent(
                        "lookup", "redis-pivot-cache", "RedisConnectionException", "connection refused");

        collector.cacheProviderUnavailable("key-123", PivotOuterCacheTelemetry.CACHE_STAGE,
                unavailable, "flat");
        collector.cacheMiss("key-123", PivotOuterCacheTelemetry.CACHE_STAGE,
                PivotOuterCacheTelemetry.CACHE_PROVIDER_UNAVAILABLE_REASON, "flat");

        List<Map<String, Object>> diagnostics = collector.snapshot();

        Map<String, Object> providerUnavailable = diagnostics.get(0);
        assertEquals("pivot.cache.provider_unavailable", providerUnavailable.get("event"));
        assertEquals("degraded", providerUnavailable.get("decision"));
        assertEquals("SalesPivot", providerUnavailable.get("model"));
        assertEquals("key-123", providerUnavailable.get("keyHash"));
        assertEquals(PivotOuterCacheTelemetry.CACHE_STAGE, providerUnavailable.get("eligibilityStage"));
        assertEquals("lookup", providerUnavailable.get("operation"));
        assertEquals("redis-pivot-cache", providerUnavailable.get("providerName"));
        assertEquals("RedisConnectionException", providerUnavailable.get("reasonClass"));
        assertEquals("connection refused", providerUnavailable.get("reason"));
        assertEquals("flat", providerUnavailable.get("shapeClass"));

        Map<String, Object> miss = diagnostics.get(1);
        assertEquals("pivot.cache.miss", miss.get("event"));
        assertEquals(PivotOuterCacheTelemetry.CACHE_PROVIDER_UNAVAILABLE_REASON, miss.get("reason"));

        providerUnavailable.put("reason", "mutated");
        assertEquals("connection refused", collector.snapshot().get(0).get("reason"));
    }

    @Test
    @DisplayName("null provider unavailable event does not add diagnostics")
    void testNullCacheProviderUnavailableEventIsIgnored() {
        PivotDiagnosticCollector collector = new PivotDiagnosticCollector("SalesPivot");

        collector.cacheProviderUnavailable("key-123", PivotOuterCacheTelemetry.CACHE_STAGE,
                null, "flat");

        assertFalse(collector.snapshot().iterator().hasNext());
    }
}
