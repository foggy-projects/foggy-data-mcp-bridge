package com.foggyframework.dataset.db.model.cache.pivot;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationEvent;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationResult;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisPivotOuterCacheInvalidationBroadcasterTest {

    private final RedisPivotOuterCacheInvalidationCodec codec = new RedisPivotOuterCacheInvalidationCodec();

    @Test
    @DisplayName("constructor does not touch Redis")
    void constructorDoesNotTouchRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        new RedisPivotOuterCacheInvalidationBroadcaster(
                redisTemplate,
                provider(null),
                codec,
                "pivot:invalidation",
                "node-a");

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("broadcaster evicts local cache and publishes enriched event")
    void evictsLocalCacheAndPublishesEnrichedEvent() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SemanticQueryServiceV3 service = mock(SemanticQueryServiceV3.class);
        when(service.evictPivotOuterCache("ns-a", "ModelA")).thenReturn(2);
        when(redisTemplate.convertAndSend(eq("pivot:invalidation"), anyString())).thenReturn(1L);
        AtomicInteger eventCounter = new AtomicInteger();
        RedisPivotOuterCacheInvalidationBroadcaster broadcaster = new RedisPivotOuterCacheInvalidationBroadcaster(
                redisTemplate,
                provider(service),
                codec,
                "pivot:invalidation",
                "node-a",
                () -> "evt-" + eventCounter.incrementAndGet(),
                () -> 123L);

        PivotOuterCacheInvalidationResult result =
                broadcaster.evict(PivotOuterCacheInvalidationEvent.of("ns-a", "ModelA"));

        assertEquals(2, result.removed());
        assertEquals(2, result.attemptedNodes());
        assertEquals(2, result.succeededNodes());
        assertEquals(0, result.failedNodes());
        assertTrue(result.success());

        verify(service).evictPivotOuterCache("ns-a", "ModelA");
        String payload = capturePublishedPayload(redisTemplate);
        PivotOuterCacheInvalidationEvent published = codec.decode(payload);
        assertEquals("ns-a", published.namespace());
        assertEquals("ModelA", published.model());
        assertEquals("evt-1", published.eventId());
        assertEquals("node-a", published.sourceNodeId());
        assertEquals(123L, published.issuedAtMillis());
    }

    @Test
    @DisplayName("broadcaster preserves explicit event metadata")
    void preservesExplicitEventMetadata() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SemanticQueryServiceV3 service = mock(SemanticQueryServiceV3.class);
        when(redisTemplate.convertAndSend(eq("pivot:invalidation"), anyString())).thenReturn(1L);
        RedisPivotOuterCacheInvalidationBroadcaster broadcaster = new RedisPivotOuterCacheInvalidationBroadcaster(
                redisTemplate,
                provider(service),
                codec,
                "pivot:invalidation",
                "node-a",
                () -> "generated",
                () -> 200L);

        broadcaster.evict(PivotOuterCacheInvalidationEvent.of(null, "ModelA")
                .withMetadata("evt-explicit", "node-x", 100L));

        PivotOuterCacheInvalidationEvent published = codec.decode(capturePublishedPayload(redisTemplate));
        assertEquals("evt-explicit", published.eventId());
        assertEquals("node-x", published.sourceNodeId());
        assertEquals(100L, published.issuedAtMillis());
    }

    @Test
    @DisplayName("codec preserves default namespace scope")
    void codecPreservesDefaultNamespaceScope() {
        PivotOuterCacheInvalidationEvent event = PivotOuterCacheInvalidationEvent.of("", "ModelA")
                .withMetadata("evt-default", "node-a", 100L);

        PivotOuterCacheInvalidationEvent decoded = codec.decode(codec.encode(event));

        assertEquals("", decoded.namespace());
        assertEquals("ModelA", decoded.model());
        assertEquals("evt-default", decoded.eventId());
    }

    @Test
    @DisplayName("publish failure is reported without throwing")
    void publishFailureReturnsPartialResult() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SemanticQueryServiceV3 service = mock(SemanticQueryServiceV3.class);
        when(service.evictPivotOuterCache("ns-a", "ModelA")).thenReturn(1);
        when(redisTemplate.convertAndSend(eq("pivot:invalidation"), anyString()))
                .thenThrow(new IllegalStateException("redis down"));
        RedisPivotOuterCacheInvalidationBroadcaster broadcaster = new RedisPivotOuterCacheInvalidationBroadcaster(
                redisTemplate,
                provider(service),
                codec,
                "pivot:invalidation",
                "node-a",
                () -> "evt-1",
                () -> 100L);

        PivotOuterCacheInvalidationResult result =
                broadcaster.evict(PivotOuterCacheInvalidationEvent.of("ns-a", "ModelA"));

        assertEquals(1, result.removed());
        assertEquals(2, result.attemptedNodes());
        assertEquals(1, result.succeededNodes());
        assertEquals(1, result.failedNodes());
        assertFalse(result.success());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("publish failed"));
    }

    @Test
    @DisplayName("missing semantic service does not prevent event publish")
    void missingSemanticServiceStillPublishesEvent() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.convertAndSend(eq("pivot:invalidation"), anyString())).thenReturn(1L);
        RedisPivotOuterCacheInvalidationBroadcaster broadcaster = new RedisPivotOuterCacheInvalidationBroadcaster(
                redisTemplate,
                provider(null),
                codec,
                "pivot:invalidation",
                "node-a",
                () -> "evt-1",
                () -> 100L);

        PivotOuterCacheInvalidationResult result =
                broadcaster.evict(PivotOuterCacheInvalidationEvent.of("ns-a", "ModelA"));

        assertEquals(0, result.removed());
        assertEquals(2, result.attemptedNodes());
        assertEquals(1, result.succeededNodes());
        assertEquals(1, result.failedNodes());
        assertFalse(result.success());
        assertTrue(result.errors().get(0).contains("SemanticQueryServiceV3 is unavailable"));
        verify(redisTemplate).convertAndSend(eq("pivot:invalidation"), anyString());
    }

    private String capturePublishedPayload(StringRedisTemplate redisTemplate) {
        org.mockito.ArgumentCaptor<String> payloadCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("pivot:invalidation"), payloadCaptor.capture());
        return payloadCaptor.getValue();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<SemanticQueryServiceV3> provider(SemanticQueryServiceV3 service) {
        ObjectProvider<SemanticQueryServiceV3> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        return provider;
    }
}
