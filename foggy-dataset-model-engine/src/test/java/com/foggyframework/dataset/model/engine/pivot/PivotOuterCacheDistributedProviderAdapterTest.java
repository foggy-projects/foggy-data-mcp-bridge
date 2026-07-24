package com.foggyframework.dataset.model.engine.pivot;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PivotOuterCacheDistributedProviderAdapterTest extends PivotOuterCacheProviderContractTest {

    @Override
    protected PivotOuterCacheProvider newEnabledProvider(long ttlMillis, int maximumSize) {
        return new InMemoryDistributedProvider(true, ttlMillis);
    }

    @Override
    protected PivotOuterCacheProvider newDisabledProvider() {
        return new InMemoryDistributedProvider(false, 60_000L);
    }

    @Test
    @DisplayName("distributed adapter stores payload with contract response and index keys")
    void distributedAdapterStoresPayloadWithContractKeys() {
        InMemoryDistributedProvider provider = new InMemoryDistributedProvider(true, 60_000L);

        provider.store("key-1", response("stored"), 100L, "ns-a", "ModelA");

        assertTrue(provider.store.payloads.containsKey("foggy:pivot:outer:response:key-1"));
        assertTrue(provider.store.indexes.get("foggy:pivot:outer:idx:namespace:all")
                .contains("foggy:pivot:outer:response:key-1"));
        assertTrue(provider.store.indexes.get("foggy:pivot:outer:idx:namespace:ns-a")
                .contains("foggy:pivot:outer:response:key-1"));
        assertTrue(provider.store.indexes.get("foggy:pivot:outer:idx:namespace:all:model:ModelA")
                .contains("foggy:pivot:outer:response:key-1"));
        assertTrue(provider.store.indexes.get("foggy:pivot:outer:idx:namespace:ns-a:model:ModelA")
                .contains("foggy:pivot:outer:response:key-1"));

        PivotOuterCacheDistributedPayload payload =
                new PivotOuterCacheJsonPayloadCodec().decode(
                        provider.store.payloads.get("foggy:pivot:outer:response:key-1"));
        assertEquals("v1", payload.payloadVersion());
        assertEquals("application/json", payload.payloadContentType());
        assertEquals(100L, payload.storedAtMillis());
        assertEquals(60_100L, payload.expiresAtMillis());
        assertEquals("ns-a", payload.namespace());
        assertEquals("ModelA", payload.model());
    }

    @Test
    @DisplayName("distributed adapter removes corrupt payload and returns miss")
    void distributedAdapterRemovesCorruptPayloadAndReturnsMiss() {
        InMemoryDistributedProvider provider = new InMemoryDistributedProvider(true, 60_000L);
        provider.store.payloads.put("foggy:pivot:outer:response:bad", new byte[] {1, 2, 3});

        PivotOuterCacheProvider.LookupResult result = provider.lookup("bad", 100L);

        assertFalse(result.hit());
        assertFalse(provider.store.payloads.containsKey("foggy:pivot:outer:response:bad"));
    }

    private static final class InMemoryDistributedProvider extends PivotOuterCacheDistributedProviderAdapter {
        private final Store store = new Store();

        private InMemoryDistributedProvider(boolean enabled, long ttlMillis) {
            super("in_memory_distributed",
                    enabled,
                    PivotOuterCacheDistributedProviderContract.defaultJson(ttlMillis),
                    new PivotOuterCacheJsonPayloadCodec());
        }

        @Override
        protected byte[] readPayload(String responseKey) {
            byte[] bytes = store.payloads.get(responseKey);
            return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        protected void writePayload(String responseKey, byte[] payloadBytes, long expiresAtMillis) {
            store.payloads.put(responseKey, Arrays.copyOf(payloadBytes, payloadBytes.length));
        }

        @Override
        protected boolean deletePayload(String responseKey) {
            return store.payloads.remove(responseKey) != null;
        }

        @Override
        protected Set<String> readIndexMembers(String indexKey) {
            return new LinkedHashSet<>(store.indexes.getOrDefault(indexKey, Set.of()));
        }

        @Override
        protected void addIndexMember(String indexKey, String responseKey, long expiresAtMillis) {
            store.indexes.computeIfAbsent(indexKey, ignored -> new LinkedHashSet<>()).add(responseKey);
        }

        @Override
        protected void removeIndexMember(String indexKey, String responseKey) {
            Set<String> members = store.indexes.get(indexKey);
            if (members != null) {
                members.remove(responseKey);
            }
        }
    }

    private static final class Store {
        private final Map<String, byte[]> payloads = new LinkedHashMap<>();
        private final Map<String, Set<String>> indexes = new LinkedHashMap<>();
    }
}
