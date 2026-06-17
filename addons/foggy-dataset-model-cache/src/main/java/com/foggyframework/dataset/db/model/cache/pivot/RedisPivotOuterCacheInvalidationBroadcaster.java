package com.foggyframework.dataset.db.model.cache.pivot;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationEvent;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationResult;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class RedisPivotOuterCacheInvalidationBroadcaster implements PivotOuterCacheInvalidationBroadcaster {

    private static final String DEFAULT_CHANNEL = "foggy:pivot:outer:cache:invalidation";

    private final StringRedisTemplate redisTemplate;
    private final ObjectProvider<SemanticQueryServiceV3> semanticQueryServiceProvider;
    private final RedisPivotOuterCacheInvalidationCodec codec;
    private final String channel;
    private final String localNodeId;
    private final Supplier<String> eventIdSupplier;
    private final LongSupplier clock;

    public RedisPivotOuterCacheInvalidationBroadcaster(
            StringRedisTemplate redisTemplate,
            ObjectProvider<SemanticQueryServiceV3> semanticQueryServiceProvider,
            String channel,
            String localNodeId) {
        this(redisTemplate,
                semanticQueryServiceProvider,
                new RedisPivotOuterCacheInvalidationCodec(),
                channel,
                localNodeId);
    }

    public RedisPivotOuterCacheInvalidationBroadcaster(
            StringRedisTemplate redisTemplate,
            ObjectProvider<SemanticQueryServiceV3> semanticQueryServiceProvider,
            RedisPivotOuterCacheInvalidationCodec codec,
            String channel,
            String localNodeId) {
        this(redisTemplate,
                semanticQueryServiceProvider,
                codec,
                channel,
                localNodeId,
                () -> UUID.randomUUID().toString(),
                System::currentTimeMillis);
    }

    RedisPivotOuterCacheInvalidationBroadcaster(
            StringRedisTemplate redisTemplate,
            ObjectProvider<SemanticQueryServiceV3> semanticQueryServiceProvider,
            RedisPivotOuterCacheInvalidationCodec codec,
            String channel,
            String localNodeId,
            Supplier<String> eventIdSupplier,
            LongSupplier clock) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.semanticQueryServiceProvider =
                Objects.requireNonNull(semanticQueryServiceProvider, "semanticQueryServiceProvider must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.channel = normalizeOrDefault(channel, DEFAULT_CHANNEL);
        this.localNodeId = normalizeOrDefault(localNodeId, "redis-pivot-node-" + UUID.randomUUID());
        this.eventIdSupplier = Objects.requireNonNull(eventIdSupplier, "eventIdSupplier must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public int evict(String namespace, String model) {
        return evict(PivotOuterCacheInvalidationEvent.of(namespace, model)).removed();
    }

    @Override
    public PivotOuterCacheInvalidationResult evict(PivotOuterCacheInvalidationEvent event) {
        PivotOuterCacheInvalidationEvent enriched = enrich(event);
        return PivotOuterCacheInvalidationResult.aggregate(List.of(
                evictLocal(enriched),
                publish(enriched)
        ));
    }

    public String channel() {
        return channel;
    }

    public String localNodeId() {
        return localNodeId;
    }

    private PivotOuterCacheInvalidationEvent enrich(PivotOuterCacheInvalidationEvent event) {
        PivotOuterCacheInvalidationEvent scoped =
                event == null ? PivotOuterCacheInvalidationEvent.all() : event;
        String eventId = scoped.eventId() == null ? eventIdSupplier.get() : scoped.eventId();
        String sourceNodeId = scoped.sourceNodeId() == null ? localNodeId : scoped.sourceNodeId();
        long issuedAtMillis = scoped.issuedAtMillis() > 0L ? scoped.issuedAtMillis() : clock.getAsLong();
        return scoped.withMetadata(eventId, sourceNodeId, issuedAtMillis);
    }

    private PivotOuterCacheInvalidationResult evictLocal(PivotOuterCacheInvalidationEvent event) {
        SemanticQueryServiceV3 service = semanticQueryServiceProvider.getIfAvailable();
        if (service == null) {
            return PivotOuterCacheInvalidationResult.unavailable("SemanticQueryServiceV3 is unavailable");
        }
        try {
            return PivotOuterCacheInvalidationResult.local(
                    service.evictPivotOuterCache(event.namespace(), event.model()));
        } catch (RuntimeException e) {
            return PivotOuterCacheInvalidationResult.unavailable(
                    "Local Pivot outer-cache invalidation failed: " + errorSummary(e));
        }
    }

    private PivotOuterCacheInvalidationResult publish(PivotOuterCacheInvalidationEvent event) {
        try {
            redisTemplate.convertAndSend(channel, codec.encode(event));
            return PivotOuterCacheInvalidationResult.success(1, 0);
        } catch (RuntimeException e) {
            return PivotOuterCacheInvalidationResult.unavailable(
                    "Redis Pivot outer-cache invalidation publish failed: " + errorSummary(e));
        }
    }

    private String errorSummary(RuntimeException e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static String normalizeOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
