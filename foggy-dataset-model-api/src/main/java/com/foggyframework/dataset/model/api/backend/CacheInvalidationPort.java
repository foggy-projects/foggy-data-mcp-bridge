package com.foggyframework.dataset.model.api.backend;

/** Small stable port for invalidating backend-owned query state. */
public interface CacheInvalidationPort {

    /** Invalidates all cached query state associated with one model identity. */
    void evict(String modelName);

    /** Invalidates all cached query state owned by this backend. */
    void evictAll();
}
