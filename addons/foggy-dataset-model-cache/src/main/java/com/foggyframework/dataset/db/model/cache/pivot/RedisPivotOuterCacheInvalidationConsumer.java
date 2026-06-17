package com.foggyframework.dataset.db.model.cache.pivot;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationEvent;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationReplayWindow;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationResult;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class RedisPivotOuterCacheInvalidationConsumer {

    private final ObjectProvider<SemanticQueryServiceV3> semanticQueryServiceProvider;
    private final RedisPivotOuterCacheInvalidationCodec codec;
    private final PivotOuterCacheInvalidationReplayWindow replayWindow;
    private final String localNodeId;
    private final LongSupplier clock;

    public RedisPivotOuterCacheInvalidationConsumer(
            ObjectProvider<SemanticQueryServiceV3> semanticQueryServiceProvider,
            RedisPivotOuterCacheInvalidationCodec codec,
            String localNodeId,
            long replayWindowMillis,
            int replayWindowMaximumEntries) {
        this(semanticQueryServiceProvider,
                codec,
                new PivotOuterCacheInvalidationReplayWindow(replayWindowMillis, replayWindowMaximumEntries),
                localNodeId,
                System::currentTimeMillis);
    }

    RedisPivotOuterCacheInvalidationConsumer(
            ObjectProvider<SemanticQueryServiceV3> semanticQueryServiceProvider,
            RedisPivotOuterCacheInvalidationCodec codec,
            PivotOuterCacheInvalidationReplayWindow replayWindow,
            String localNodeId,
            LongSupplier clock) {
        this.semanticQueryServiceProvider =
                Objects.requireNonNull(semanticQueryServiceProvider, "semanticQueryServiceProvider must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.replayWindow = Objects.requireNonNull(replayWindow, "replayWindow must not be null");
        this.localNodeId = normalizeOrDefault(localNodeId, "redis-pivot-node-" + UUID.randomUUID());
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public PivotOuterCacheInvalidationResult consume(String payload) {
        try {
            return consume(codec.decode(payload));
        } catch (IllegalArgumentException e) {
            return PivotOuterCacheInvalidationResult.unavailable(
                    "Redis Pivot outer-cache invalidation payload invalid: " + e.getMessage());
        }
    }

    public PivotOuterCacheInvalidationResult consume(PivotOuterCacheInvalidationEvent event) {
        PivotOuterCacheInvalidationEvent scoped =
                event == null ? PivotOuterCacheInvalidationEvent.all() : event;
        if (!replayWindow.shouldConsume(scoped, localNodeId, clock.getAsLong())) {
            return PivotOuterCacheInvalidationResult.aggregate(null);
        }
        SemanticQueryServiceV3 service = semanticQueryServiceProvider.getIfAvailable();
        if (service == null) {
            return PivotOuterCacheInvalidationResult.unavailable("SemanticQueryServiceV3 is unavailable");
        }
        try {
            return PivotOuterCacheInvalidationResult.local(
                    service.evictPivotOuterCache(scoped.namespace(), scoped.model()));
        } catch (RuntimeException e) {
            return PivotOuterCacheInvalidationResult.unavailable(
                    "Redis Pivot outer-cache invalidation consume failed: " + errorSummary(e));
        }
    }

    public String localNodeId() {
        return localNodeId;
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
