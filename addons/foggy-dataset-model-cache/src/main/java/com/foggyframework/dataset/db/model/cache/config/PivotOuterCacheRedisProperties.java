package com.foggyframework.dataset.db.model.cache.config;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheDistributedProviderContract;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "foggy.dataset.pivot.outer-cache.redis")
public class PivotOuterCacheRedisProperties {

    private static final String DEFAULT_INVALIDATION_CHANNEL_SUFFIX = ":invalidation";

    /**
     * Explicit opt-in for the Redis Pivot outer-cache provider.
     */
    private boolean enabled = false;

    /**
     * Redis key prefix used by the distributed Pivot outer-cache contract.
     */
    private String keyPrefix = PivotOuterCacheDistributedProviderContract.DEFAULT_KEY_PREFIX;

    /**
     * Explicit opt-in for Redis Pub/Sub backed Pivot outer-cache invalidation.
     */
    private boolean invalidationEnabled = false;

    /**
     * Redis Pub/Sub channel used to fan out Pivot outer-cache invalidation events.
     */
    private String invalidationChannel = "";

    /**
     * Process-local node id used to skip self-loop Pub/Sub events.
     */
    private String nodeId = "";

    /**
     * Local replay window for explicit invalidation event ids.
     */
    private long replayWindowMillis = 60_000L;

    /**
     * Maximum explicit event ids retained in the local replay window.
     */
    private int replayWindowMaximumEntries = 1024;

    /**
     * Whether to auto-wire a Redis Pub/Sub listener container for invalidation events.
     */
    private boolean invalidationListenerEnabled = true;

    /**
     * Whether the auto-wired Redis invalidation listener container starts with the Spring context.
     */
    private boolean invalidationListenerAutoStartup = true;

    /**
     * Recovery interval for the Redis invalidation listener container after subscription failures.
     */
    private long invalidationListenerRecoveryIntervalMillis = 5_000L;

    public String resolvedKeyPrefix() {
        return keyPrefix == null || keyPrefix.isBlank()
                ? PivotOuterCacheDistributedProviderContract.DEFAULT_KEY_PREFIX
                : keyPrefix.trim();
    }

    public String resolvedInvalidationChannel() {
        if (invalidationChannel == null || invalidationChannel.isBlank()) {
            return resolvedKeyPrefix() + DEFAULT_INVALIDATION_CHANNEL_SUFFIX;
        }
        return invalidationChannel.trim();
    }

    public String resolvedNodeId() {
        return nodeId == null ? "" : nodeId.trim();
    }

    public long resolvedReplayWindowMillis() {
        return replayWindowMillis <= 0L ? 60_000L : replayWindowMillis;
    }

    public int resolvedReplayWindowMaximumEntries() {
        return replayWindowMaximumEntries <= 0 ? 1024 : replayWindowMaximumEntries;
    }

    public long resolvedInvalidationListenerRecoveryIntervalMillis() {
        return invalidationListenerRecoveryIntervalMillis <= 0L
                ? 5_000L
                : invalidationListenerRecoveryIntervalMillis;
    }
}
