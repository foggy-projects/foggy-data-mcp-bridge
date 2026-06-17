package com.foggyframework.dataset.db.model.cache.pivot;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Slf4j
public final class RedisPivotOuterCacheInvalidationMessageListener implements MessageListener {

    private final RedisPivotOuterCacheInvalidationConsumer consumer;

    public RedisPivotOuterCacheInvalidationMessageListener(RedisPivotOuterCacheInvalidationConsumer consumer) {
        this.consumer = Objects.requireNonNull(consumer, "consumer must not be null");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        byte[] body = message == null ? null : message.getBody();
        String payload = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        PivotOuterCacheInvalidationResult result = consumer.consume(payload);
        if (!result.success()) {
            log.warn("Redis Pivot outer-cache invalidation message was not fully consumed: {}", result.errors());
        }
    }
}
