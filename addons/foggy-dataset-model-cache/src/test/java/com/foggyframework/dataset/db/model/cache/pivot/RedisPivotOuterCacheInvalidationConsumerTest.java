package com.foggyframework.dataset.db.model.cache.pivot;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationEvent;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationReplayWindow;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationResult;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisPivotOuterCacheInvalidationConsumerTest {

    private final RedisPivotOuterCacheInvalidationCodec codec = new RedisPivotOuterCacheInvalidationCodec();

    @Test
    @DisplayName("consumer decodes payload and evicts local cache")
    void decodesPayloadAndEvictsLocalCache() {
        SemanticQueryServiceV3 service = mock(SemanticQueryServiceV3.class);
        when(service.evictPivotOuterCache("ns-a", "ModelA")).thenReturn(3);
        RedisPivotOuterCacheInvalidationConsumer consumer = consumer(service, "node-b", 100L);
        String payload = codec.encode(PivotOuterCacheInvalidationEvent.of("ns-a", "ModelA")
                .withMetadata("evt-1", "node-a", 100L));

        PivotOuterCacheInvalidationResult result = consumer.consume(payload);

        assertEquals(3, result.removed());
        assertEquals(1, result.attemptedNodes());
        assertEquals(1, result.succeededNodes());
        assertEquals(0, result.failedNodes());
        assertTrue(result.success());
        verify(service).evictPivotOuterCache("ns-a", "ModelA");
    }

    @Test
    @DisplayName("consumer deduplicates repeated explicit event id")
    void deduplicatesRepeatedExplicitEventId() {
        SemanticQueryServiceV3 service = mock(SemanticQueryServiceV3.class);
        when(service.evictPivotOuterCache("ns-a", "ModelA")).thenReturn(1);
        RedisPivotOuterCacheInvalidationConsumer consumer = consumer(service, "node-b", 100L);
        PivotOuterCacheInvalidationEvent event = PivotOuterCacheInvalidationEvent.of("ns-a", "ModelA")
                .withMetadata("evt-1", "node-a", 100L);

        PivotOuterCacheInvalidationResult first = consumer.consume(event);
        PivotOuterCacheInvalidationResult replay = consumer.consume(event.withMetadata("evt-1", "node-c", 101L));

        assertEquals(1, first.removed());
        assertEquals(1, first.attemptedNodes());
        assertEquals(0, replay.removed());
        assertEquals(0, replay.attemptedNodes());
        assertEquals(0, replay.failedNodes());
        verify(service).evictPivotOuterCache("ns-a", "ModelA");
    }

    @Test
    @DisplayName("consumer skips local self-loop event")
    void skipsSelfLoopEvent() {
        SemanticQueryServiceV3 service = mock(SemanticQueryServiceV3.class);
        RedisPivotOuterCacheInvalidationConsumer consumer = consumer(service, "node-b", 100L);
        PivotOuterCacheInvalidationEvent event = PivotOuterCacheInvalidationEvent.of("ns-a", "ModelA")
                .withMetadata("evt-1", "node-b", 100L);

        PivotOuterCacheInvalidationResult result = consumer.consume(event);

        assertEquals(0, result.removed());
        assertEquals(0, result.attemptedNodes());
        assertEquals(0, result.failedNodes());
        verify(service, never()).evictPivotOuterCache("ns-a", "ModelA");
    }

    @Test
    @DisplayName("invalid payload returns diagnostic result without touching service")
    void invalidPayloadReturnsDiagnosticResult() {
        SemanticQueryServiceV3 service = mock(SemanticQueryServiceV3.class);
        RedisPivotOuterCacheInvalidationConsumer consumer = consumer(service, "node-b", 100L);

        PivotOuterCacheInvalidationResult result = consumer.consume("not-json");

        assertEquals(0, result.removed());
        assertEquals(1, result.attemptedNodes());
        assertEquals(0, result.succeededNodes());
        assertEquals(1, result.failedNodes());
        assertFalse(result.success());
        assertTrue(result.errors().get(0).contains("payload invalid"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("missing semantic service returns unavailable result")
    void missingSemanticServiceReturnsUnavailableResult() {
        RedisPivotOuterCacheInvalidationConsumer consumer = consumer(null, "node-b", 100L);

        PivotOuterCacheInvalidationResult result = consumer.consume(PivotOuterCacheInvalidationEvent.of("ns-a", null)
                .withMetadata("evt-1", "node-a", 100L));

        assertEquals(0, result.removed());
        assertEquals(1, result.attemptedNodes());
        assertEquals(1, result.failedNodes());
        assertTrue(result.errors().get(0).contains("SemanticQueryServiceV3 is unavailable"));
    }

    private RedisPivotOuterCacheInvalidationConsumer consumer(
            SemanticQueryServiceV3 service,
            String localNodeId,
            long nowMillis) {
        AtomicLong clock = new AtomicLong(nowMillis);
        return new RedisPivotOuterCacheInvalidationConsumer(
                provider(service),
                codec,
                new PivotOuterCacheInvalidationReplayWindow(60_000L, 1024),
                localNodeId,
                clock::get);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<SemanticQueryServiceV3> provider(SemanticQueryServiceV3 service) {
        ObjectProvider<SemanticQueryServiceV3> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        return provider;
    }
}
