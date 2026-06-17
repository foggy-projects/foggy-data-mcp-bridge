package com.foggyframework.dataset.db.model.cache.pivot;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationEvent;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisPivotOuterCacheInvalidationMessageListenerTest {

    private final RedisPivotOuterCacheInvalidationCodec codec = new RedisPivotOuterCacheInvalidationCodec();

    @Test
    @DisplayName("message listener forwards Redis payloads to the invalidation consumer")
    void forwardsRedisPayloadToConsumer() {
        SemanticQueryServiceV3 service = mock(SemanticQueryServiceV3.class);
        RedisPivotOuterCacheInvalidationMessageListener listener = listener(service, "node-b");
        String payload = codec.encode(PivotOuterCacheInvalidationEvent.of("ns-a", "ModelA")
                .withMetadata("evt-1", "node-a", 100L));

        listener.onMessage(message(payload), null);

        verify(service).evictPivotOuterCache("ns-a", "ModelA");
    }

    @Test
    @DisplayName("message listener keeps invalid payloads local and non-throwing")
    void invalidPayloadDoesNotThrow() {
        SemanticQueryServiceV3 service = mock(SemanticQueryServiceV3.class);
        RedisPivotOuterCacheInvalidationMessageListener listener = listener(service, "node-b");

        assertDoesNotThrow(() -> listener.onMessage(message("not-json"), null));

        verifyNoInteractions(service);
    }

    private RedisPivotOuterCacheInvalidationMessageListener listener(
            SemanticQueryServiceV3 service,
            String localNodeId) {
        return new RedisPivotOuterCacheInvalidationMessageListener(
                new RedisPivotOuterCacheInvalidationConsumer(
                        provider(service),
                        codec,
                        localNodeId,
                        60_000L,
                        1024));
    }

    private Message message(String payload) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));
        return message;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<SemanticQueryServiceV3> provider(SemanticQueryServiceV3 service) {
        ObjectProvider<SemanticQueryServiceV3> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        return provider;
    }
}
