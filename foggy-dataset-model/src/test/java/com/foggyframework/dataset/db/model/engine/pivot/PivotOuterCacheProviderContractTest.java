package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reusable contract for local or distributed Pivot outer-cache providers.
 */
public abstract class PivotOuterCacheProviderContractTest {

    protected abstract PivotOuterCacheProvider newEnabledProvider(long ttlMillis, int maximumSize);

    protected abstract PivotOuterCacheProvider newDisabledProvider();

    @Test
    @DisplayName("provider disabled state never stores or hits")
    void disabledProviderNeverStoresOrHits() {
        PivotOuterCacheProvider cache = newDisabledProvider();

        assertFalse(cache.isEnabled());
        cache.store("disabled", response("disabled"), 100L, "ns-a", "ModelA");

        assertFalse(cache.lookup("disabled", 101L).hit());
        assertFalse(cache.lookup("disabled", 101L).expired());
        assertEquals(0, cache.evict("ns-a", "ModelA"));
    }

    @Test
    @DisplayName("provider hit returns isolated response copies")
    void hitReturnsIsolatedResponseCopies() {
        PivotOuterCacheProvider cache = newEnabledProvider(60_000L, 8);
        SemanticQueryResponse original = response("stable");

        cache.store("copy", original, 100L, "ns-a", "ModelA");
        original.getItems().get(0).put("value", "mutated-original");

        PivotOuterCacheProvider.LookupResult firstLookup = cache.lookup("copy", 101L);
        assertTrue(firstLookup.hit());
        assertEquals(1L, firstLookup.ageMs());
        assertEquals("stable", firstLookup.response().getItems().get(0).get("value"));
        firstLookup.response().getItems().get(0).put("value", "mutated-lookup");
        mutateDiagnostic(firstLookup.response(), "mutated-diagnostic");

        PivotOuterCacheProvider.LookupResult secondLookup = cache.lookup("copy", 102L);
        assertTrue(secondLookup.hit());
        assertNotSame(firstLookup.response(), secondLookup.response());
        assertEquals("stable", secondLookup.response().getItems().get(0).get("value"));
        assertEquals("pivot.cache.store", diagnostics(secondLookup.response()).get(0).get("event"));
    }

    @Test
    @DisplayName("provider TTL expiry reports expired once then misses")
    void ttlExpiryReportsExpiredThenMisses() {
        PivotOuterCacheProvider cache = newEnabledProvider(10L, 8);

        cache.store("ttl", response("ttl"), 100L, "ns-a", "ModelA");
        assertTrue(cache.lookup("ttl", 109L).hit());

        PivotOuterCacheProvider.LookupResult expired = cache.lookup("ttl", 110L);
        assertFalse(expired.hit());
        assertTrue(expired.expired());
        assertEquals(10L, expired.ageMs());

        PivotOuterCacheProvider.LookupResult afterRemoval = cache.lookup("ttl", 111L);
        assertFalse(afterRemoval.hit());
        assertFalse(afterRemoval.expired());
    }

    @Test
    @DisplayName("provider eviction honors namespace and model scope")
    void evictionHonorsNamespaceAndModelScope() {
        PivotOuterCacheProvider cache = newEnabledProvider(60_000L, 8);
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

    @Test
    @DisplayName("provider all-scope eviction removes all entries")
    void allScopeEvictionRemovesAllEntries() {
        PivotOuterCacheProvider cache = newEnabledProvider(60_000L, 8);
        cache.store("a", response("a"), 100L, "ns-a", "ModelA");
        cache.store("b", response("b"), 101L, "ns-b", "ModelB");

        assertEquals(2, cache.evict(null, null));
        assertFalse(cache.lookup("a", 102L).hit());
        assertFalse(cache.lookup("b", 102L).hit());
    }

    @Test
    @DisplayName("provider estimates payload bytes from response content")
    void estimatesPayloadBytesFromResponseContent() {
        PivotOuterCacheProvider cache = newEnabledProvider(60_000L, 8);

        assertEquals(0, cache.estimatePayloadBytes(null));
        assertTrue(cache.estimatePayloadBytes(response("payload")) > 0);
    }

    protected SemanticQueryResponse response(String value) {
        SemanticQueryResponse response = new SemanticQueryResponse();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("metric", 1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("value", value);
        row.put("nested", nested);
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

    private void mutateDiagnostic(SemanticQueryResponse response, String event) {
        diagnostics(response).get(0).put("event", event);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> diagnostics(SemanticQueryResponse response) {
        return (List<Map<String, Object>>) response.getDebug().getExtra().get("pivotDiagnostics");
    }
}
