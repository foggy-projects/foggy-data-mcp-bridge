package com.foggyframework.dataset.db.model.cache.pivot;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheProvider;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RedisPivotOuterCacheProviderTest {

    @Test
    @DisplayName("constructor and disabled provider do not touch Redis operations")
    void constructorAndDisabledProviderDoNotTouchStore() {
        ThrowingStore store = new ThrowingStore();
        RedisPivotOuterCacheProvider provider =
                new RedisPivotOuterCacheProvider(store, false, 60_000L, "foggy:test:pivot");

        assertFalse(provider.isEnabled());
        assertFalse(provider.lookup("disabled", 100L).hit());
        provider.store("disabled", response("disabled"), 100L, "ns-a", "ModelA");
        assertEquals(0, provider.evict("ns-a", "ModelA"));
        assertTrue(provider.estimatePayloadBytes(response("payload")) > 0);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        new RedisPivotOuterCacheProvider(redisTemplate, true, 60_000L, "foggy:test:pivot");
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Redis provider stores and returns isolated response copies")
    void hitReturnsIsolatedResponseCopies() {
        RedisPivotOuterCacheProvider provider = provider(60_000L);
        SemanticQueryResponse original = response("stable");

        provider.store("copy", original, 100L, "ns-a", "ModelA");
        original.getItems().get(0).put("value", "mutated-original");

        PivotOuterCacheProvider.LookupResult firstLookup = provider.lookup("copy", 101L);
        assertTrue(firstLookup.hit());
        assertEquals(1L, firstLookup.ageMs());
        assertEquals("stable", firstLookup.response().getItems().get(0).get("value"));
        firstLookup.response().getItems().get(0).put("value", "mutated-lookup");
        diagnostics(firstLookup.response()).get(0).put("event", "mutated-diagnostic");

        PivotOuterCacheProvider.LookupResult secondLookup = provider.lookup("copy", 102L);
        assertTrue(secondLookup.hit());
        assertNotSame(firstLookup.response(), secondLookup.response());
        assertEquals("stable", secondLookup.response().getItems().get(0).get("value"));
        assertEquals("pivot.cache.store", diagnostics(secondLookup.response()).get(0).get("event"));
    }

    @Test
    @DisplayName("Redis provider TTL expiry reports expired then removes payload")
    void ttlExpiryReportsExpiredThenMisses() {
        RedisPivotOuterCacheProvider provider = provider(10L);

        provider.store("ttl", response("ttl"), 100L, "ns-a", "ModelA");
        assertTrue(provider.lookup("ttl", 109L).hit());

        PivotOuterCacheProvider.LookupResult expired = provider.lookup("ttl", 110L);
        assertFalse(expired.hit());
        assertTrue(expired.expired());
        assertEquals(10L, expired.ageMs());

        PivotOuterCacheProvider.LookupResult afterRemoval = provider.lookup("ttl", 111L);
        assertFalse(afterRemoval.hit());
        assertFalse(afterRemoval.expired());
    }

    @Test
    @DisplayName("Redis provider eviction honors namespace and model indexes")
    void evictionHonorsNamespaceAndModelScope() {
        RedisPivotOuterCacheProvider provider = provider(60_000L);
        provider.store("default-model-a", response("default-model-a"), 100L, "", "ModelA");
        provider.store("ns-a-model-a", response("ns-a-model-a"), 101L, "ns-a", "ModelA");
        provider.store("ns-a-model-b", response("ns-a-model-b"), 102L, "ns-a", "ModelB");
        provider.store("ns-b-model-a", response("ns-b-model-a"), 103L, "ns-b", "ModelA");

        assertEquals(1, provider.evict("ns-a", "ModelA"));
        assertFalse(provider.lookup("ns-a-model-a", 104L).hit());
        assertTrue(provider.lookup("ns-a-model-b", 104L).hit());
        assertTrue(provider.lookup("ns-b-model-a", 104L).hit());

        assertEquals(2, provider.evict(null, "ModelA"));
        assertFalse(provider.lookup("default-model-a", 105L).hit());
        assertFalse(provider.lookup("ns-b-model-a", 105L).hit());
        assertTrue(provider.lookup("ns-a-model-b", 105L).hit());

        assertEquals(1, provider.evict("ns-a", null));
        assertFalse(provider.lookup("ns-a-model-b", 106L).hit());
    }

    @Test
    @DisplayName("Redis provider removes invalid Base64 payload and returns miss")
    void invalidBase64PayloadReturnsMiss() {
        InMemoryStore store = new InMemoryStore();
        RedisPivotOuterCacheProvider provider =
                new RedisPivotOuterCacheProvider(store, true, 60_000L, "foggy:test:pivot");
        store.values.put("foggy:test:pivot:response:bad", "not-base64");

        PivotOuterCacheProvider.LookupResult result = provider.lookup("bad", 100L);

        assertFalse(result.hit());
        assertFalse(store.values.containsKey("foggy:test:pivot:response:bad"));
    }

    @Test
    @DisplayName("Redis provider estimates payload bytes without store access")
    void estimatesPayloadBytesWithoutStoreAccess() {
        RedisPivotOuterCacheProvider provider =
                new RedisPivotOuterCacheProvider(new ThrowingStore(), true, 60_000L, "foggy:test:pivot");

        assertEquals(0, provider.estimatePayloadBytes(null));
        assertTrue(provider.estimatePayloadBytes(response("payload")) > 0);
    }

    private RedisPivotOuterCacheProvider provider(long ttlMillis) {
        return new RedisPivotOuterCacheProvider(new InMemoryStore(), true, ttlMillis, "foggy:test:pivot");
    }

    private SemanticQueryResponse response(String value) {
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> diagnostics(SemanticQueryResponse response) {
        return (List<Map<String, Object>>) response.getDebug().getExtra().get("pivotDiagnostics");
    }

    private static final class InMemoryStore implements PivotOuterCacheStringStore {
        private final Map<String, String> values = new LinkedHashMap<>();
        private final Map<String, Set<String>> indexes = new LinkedHashMap<>();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
        }

        @Override
        public boolean delete(String key) {
            return values.remove(key) != null;
        }

        @Override
        public Set<String> members(String key) {
            return new LinkedHashSet<>(indexes.getOrDefault(key, Set.of()));
        }

        @Override
        public void addMember(String key, String member, Duration ttl) {
            indexes.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(member);
        }

        @Override
        public void removeMember(String key, String member) {
            Set<String> members = indexes.get(key);
            if (members != null) {
                members.remove(member);
            }
        }
    }

    private static final class ThrowingStore implements PivotOuterCacheStringStore {
        @Override
        public String get(String key) {
            throw new AssertionError("store should not be read");
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            throw new AssertionError("store should not be written");
        }

        @Override
        public boolean delete(String key) {
            throw new AssertionError("store should not delete");
        }

        @Override
        public Set<String> members(String key) {
            throw new AssertionError("store should not read index members");
        }

        @Override
        public void addMember(String key, String member, Duration ttl) {
            throw new AssertionError("store should not add index members");
        }

        @Override
        public void removeMember(String key, String member) {
            throw new AssertionError("store should not remove index members");
        }
    }
}
