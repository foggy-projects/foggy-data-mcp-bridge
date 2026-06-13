package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PivotOuterCacheSafeProviderTest {

    @Test
    @DisplayName("safe provider degrades external cache failures to miss and no-op by default")
    void testSafeProviderDegradesExternalCacheFailuresByDefault() {
        PivotOuterCacheProvider provider = PivotOuterCacheSafeProvider.wrap(new FailingProvider(), false);
        SemanticQueryResponse response = response();

        assertEquals(PivotOuterCacheSafeProvider.UNAVAILABLE_NAME, provider.name());
        assertFalse(provider.isEnabled());
        assertEquals(0L, provider.ttlMillis());
        assertFalse(provider.lookup("key-a", 100L).hit());
        assertDoesNotThrow(() -> provider.store("key-a", response, 100L, "ns-a", "SalesQM"));
        assertEquals(0, provider.evict("ns-a", "SalesQM"));
        assertEquals(0, provider.estimatePayloadBytes(response));
    }

    @Test
    @DisplayName("safe provider can fail fast when provider availability is required")
    void testSafeProviderCanFailFastWhenConfigured() {
        PivotOuterCacheProvider provider = PivotOuterCacheSafeProvider.wrap(new FailingProvider(), true);
        SemanticQueryResponse response = response();

        IllegalStateException enabledError = assertThrows(IllegalStateException.class, provider::isEnabled);
        assertTrue(enabledError.getMessage().contains("isEnabled"));
        assertThrows(IllegalStateException.class, () -> provider.lookup("key-a", 100L));
        assertThrows(IllegalStateException.class,
                () -> provider.store("key-a", response, 100L, "ns-a", "SalesQM"));
        assertThrows(IllegalStateException.class, () -> provider.evict("ns-a", "SalesQM"));
        assertThrows(IllegalStateException.class, () -> provider.estimatePayloadBytes(response));
    }

    @Test
    @DisplayName("safe provider delegates to healthy external provider")
    void testSafeProviderDelegatesToHealthyProvider() {
        SemanticQueryResponse response = response();
        HealthyProvider delegate = new HealthyProvider(response);
        PivotOuterCacheProvider provider = PivotOuterCacheSafeProvider.wrap(delegate, false);

        assertEquals("healthy-cache", provider.name());
        assertTrue(provider.isEnabled());
        assertEquals(123L, provider.ttlMillis());
        PivotOuterCacheProvider.LookupResult lookup = provider.lookup("key-a", 100L);
        assertTrue(lookup.hit());
        assertSame(response, lookup.response());
        provider.store("key-a", response, 100L, "ns-a", "SalesQM");
        assertTrue(delegate.stored);
        assertEquals(7, provider.evict("ns-a", "SalesQM"));
        assertEquals(42, provider.estimatePayloadBytes(response));
    }

    @Test
    @DisplayName("semantic service wraps optional provider so unavailable cache does not block eviction")
    void testSemanticServiceWrapsUnavailableProviderByDefault() {
        SemanticQueryServiceV3Impl service = semanticService(false);

        assertDoesNotThrow(() -> assertEquals(0, service.evictPivotOuterCache("ns-a", "SalesQM")));
    }

    @Test
    @DisplayName("semantic service preserves explicit fail-fast provider availability mode")
    void testSemanticServicePreservesFailFastProviderMode() {
        SemanticQueryServiceV3Impl service = semanticService(true);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.evictPivotOuterCache("ns-a", "SalesQM"));
        assertTrue(error.getMessage().contains("evict"));
    }

    private SemanticQueryServiceV3Impl semanticService(boolean failOnProviderUnavailable) {
        DatasetProperties properties = new DatasetProperties();
        properties.getPivot().getOuterCache().setFailOnProviderUnavailable(failOnProviderUnavailable);
        SemanticQueryServiceV3Impl service = new SemanticQueryServiceV3Impl();
        ReflectionTestUtils.setField(service, "datasetProperties", properties);
        service.setPivotOuterCacheProvider(new FailingProvider());
        return service;
    }

    private SemanticQueryResponse response() {
        SemanticQueryResponse response = new SemanticQueryResponse();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("amount", 10);
        response.setItems(List.of(item));
        return response;
    }

    private static final class FailingProvider implements PivotOuterCacheProvider {
        @Override
        public String name() {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public boolean isEnabled() {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public long ttlMillis() {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public LookupResult lookup(String keyHash, long nowMillis) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public void store(String keyHash,
                          SemanticQueryResponse response,
                          long nowMillis,
                          String namespace,
                          String model) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public int evict(String namespace, String model) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public int estimatePayloadBytes(SemanticQueryResponse response) {
            throw new IllegalStateException("redis unavailable");
        }
    }

    private static final class HealthyProvider implements PivotOuterCacheProvider {
        private final SemanticQueryResponse response;
        private boolean stored;

        private HealthyProvider(SemanticQueryResponse response) {
            this.response = response;
        }

        @Override
        public String name() {
            return "healthy-cache";
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public long ttlMillis() {
            return 123L;
        }

        @Override
        public LookupResult lookup(String keyHash, long nowMillis) {
            return LookupResult.hit(response, 9L);
        }

        @Override
        public void store(String keyHash,
                          SemanticQueryResponse response,
                          long nowMillis,
                          String namespace,
                          String model) {
            stored = true;
        }

        @Override
        public int evict(String namespace, String model) {
            return 7;
        }

        @Override
        public int estimatePayloadBytes(SemanticQueryResponse response) {
            return 42;
        }
    }
}
