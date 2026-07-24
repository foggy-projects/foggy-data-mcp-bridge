package com.foggyframework.dataset.model.api.backend;

/** Provider role for backends that explicitly advertise cache invalidation. */
public interface CacheInvalidationBackendProvider extends BackendProvider {

    CacheInvalidationPort cacheInvalidation();
}
