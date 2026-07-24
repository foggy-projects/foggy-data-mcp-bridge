package com.foggyframework.dataset.model.api.backend;

/** Small stable port for atomically publishing a refreshed catalog snapshot. */
@FunctionalInterface
public interface AtomicRefreshPort {

    AtomicRefreshResult refresh(AtomicRefreshRequest request);
}
