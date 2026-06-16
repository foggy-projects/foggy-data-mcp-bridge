package com.foggyframework.dataset.db.model.cache.pivot;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheDistributedProviderAdapter;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheDistributedProviderContract;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheJsonPayloadCodec;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

public final class RedisPivotOuterCacheProvider extends PivotOuterCacheDistributedProviderAdapter {

    public static final String CACHE_NAME = "pivot_outer_response_redis";

    private final PivotOuterCacheStringStore store;

    public RedisPivotOuterCacheProvider(StringRedisTemplate redisTemplate, boolean enabled, long ttlMillis) {
        this(redisTemplate, enabled, ttlMillis, PivotOuterCacheDistributedProviderContract.DEFAULT_KEY_PREFIX);
    }

    public RedisPivotOuterCacheProvider(StringRedisTemplate redisTemplate,
                                        boolean enabled,
                                        long ttlMillis,
                                        String keyPrefix) {
        this(new StringRedisTemplatePivotOuterCacheStore(redisTemplate), enabled, ttlMillis, keyPrefix);
    }

    RedisPivotOuterCacheProvider(PivotOuterCacheStringStore store,
                                 boolean enabled,
                                 long ttlMillis,
                                 String keyPrefix) {
        super(CACHE_NAME,
                enabled,
                contract(keyPrefix, ttlMillis),
                new PivotOuterCacheJsonPayloadCodec());
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    @Override
    protected byte[] readPayload(String responseKey) {
        String encoded = store.get(responseKey);
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            store.delete(responseKey);
            return null;
        }
    }

    @Override
    protected void writePayload(String responseKey, byte[] payloadBytes, long expiresAtMillis) {
        store.set(responseKey, Base64.getEncoder().encodeToString(payloadBytes), ttlUntil(expiresAtMillis));
    }

    @Override
    protected boolean deletePayload(String responseKey) {
        return store.delete(responseKey);
    }

    @Override
    protected Set<String> readIndexMembers(String indexKey) {
        return store.members(indexKey);
    }

    @Override
    protected void addIndexMember(String indexKey, String responseKey, long expiresAtMillis) {
        store.addMember(indexKey, responseKey, ttlUntil(expiresAtMillis));
    }

    @Override
    protected void removeIndexMember(String indexKey, String responseKey) {
        store.removeMember(indexKey, responseKey);
    }

    private Duration ttlUntil(long expiresAtMillis) {
        return Duration.ofMillis(Math.max(1L, expiresAtMillis - System.currentTimeMillis()));
    }

    private static PivotOuterCacheDistributedProviderContract contract(String keyPrefix, long ttlMillis) {
        String effectiveKeyPrefix = keyPrefix == null || keyPrefix.isBlank()
                ? PivotOuterCacheDistributedProviderContract.DEFAULT_KEY_PREFIX
                : keyPrefix.trim();
        return PivotOuterCacheDistributedProviderContract.json(effectiveKeyPrefix, ttlMillis);
    }
}
