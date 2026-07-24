package com.foggyframework.dataset.db.model.cache.provider;

import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendDescriptor;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.api.backend.CacheInvalidationBackendProvider;
import com.foggyframework.dataset.model.api.backend.CacheInvalidationPort;

import java.util.Objects;
import java.util.Set;

/** Compatibility adapter from the legacy query-cache SPI to the v2 invalidation port. */
public final class QueryCacheBackendProvider
        implements CacheInvalidationBackendProvider, CacheInvalidationPort {

    public static final BackendId QUERY_CACHE = BackendId.of("query-cache");

    private static final BackendDescriptor DESCRIPTOR = new BackendDescriptor(
            QUERY_CACHE, Set.of(BackendCapability.CACHE_INVALIDATION));

    private final QueryCacheProvider delegate;

    public QueryCacheBackendProvider(QueryCacheProvider delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public BackendDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CacheInvalidationPort cacheInvalidation() {
        return this;
    }

    @Override
    public void evict(String modelName) {
        Objects.requireNonNull(modelName, "modelName must not be null");
        if (modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        delegate.evict(modelName);
    }

    @Override
    public void evictAll() {
        delegate.evictAll();
    }
}
