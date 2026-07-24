package com.foggyframework.dataset.db.model.cache.provider;

import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.api.backend.CacheInvalidationBackendProvider;
import com.foggyframework.dataset.model.tck.BackendProviderTck;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class QueryCacheBackendProviderTckTest
        extends BackendProviderTck<CacheInvalidationBackendProvider> {

    @Override
    protected CacheInvalidationBackendProvider createProvider() {
        return new QueryCacheBackendProvider(mock(QueryCacheProvider.class));
    }

    @Override
    protected BackendId expectedBackendId() {
        return QueryCacheBackendProvider.QUERY_CACHE;
    }

    @Override
    protected Set<BackendCapability> expectedCapabilities() {
        return Set.of(BackendCapability.CACHE_INVALIDATION);
    }

    @Override
    protected Class<CacheInvalidationBackendProvider> expectedProviderRole() {
        return CacheInvalidationBackendProvider.class;
    }

    @Override
    protected void verifyOperationalPort(CacheInvalidationBackendProvider provider) {
        assertSame(provider, provider.cacheInvalidation());
    }

    @Test
    void stableInvalidationPortDelegatesToLegacyProvider() {
        QueryCacheProvider delegate = mock(QueryCacheProvider.class);
        QueryCacheBackendProvider provider = new QueryCacheBackendProvider(delegate);

        assertSame(provider, provider.cacheInvalidation());
        provider.cacheInvalidation().evict("FactOrders");
        provider.cacheInvalidation().evictAll();

        verify(delegate).evict("FactOrders");
        verify(delegate).evictAll();
    }

    @Test
    void invalidModelIdentityFailsBeforeTouchingLegacyProvider() {
        QueryCacheProvider delegate = mock(QueryCacheProvider.class);
        QueryCacheBackendProvider provider = new QueryCacheBackendProvider(delegate);

        assertThrows(NullPointerException.class, () -> provider.evict(null));
        assertThrows(IllegalArgumentException.class, () -> provider.evict("  "));
        verifyNoInteractions(delegate);
    }
}
