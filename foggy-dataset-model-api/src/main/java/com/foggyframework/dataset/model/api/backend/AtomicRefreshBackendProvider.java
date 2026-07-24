package com.foggyframework.dataset.model.api.backend;

/** Provider role required when ATOMIC_REFRESH is advertised. */
public interface AtomicRefreshBackendProvider extends BackendProvider {

    AtomicRefreshPort atomicRefresh();
}
