package com.foggyframework.dataset.db.model.cache.config;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheDistributedProviderContract;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "foggy.dataset.pivot.outer-cache.redis")
public class PivotOuterCacheRedisProperties {

    /**
     * Explicit opt-in for the Redis Pivot outer-cache provider.
     */
    private boolean enabled = false;

    /**
     * Redis key prefix used by the distributed Pivot outer-cache contract.
     */
    private String keyPrefix = PivotOuterCacheDistributedProviderContract.DEFAULT_KEY_PREFIX;
}
