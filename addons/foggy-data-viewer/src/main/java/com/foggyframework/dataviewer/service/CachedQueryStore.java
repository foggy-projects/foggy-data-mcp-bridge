package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.CachedQueryContext;

import java.time.Instant;
import java.util.Optional;

/** Short-lived storage for DataViewer preview requests. */
public interface CachedQueryStore extends QueryContextStore {

    Optional<CachedQueryContext> findUnexpired(String queryId, Instant now);

    @Override
    default Optional<CachedQueryContext> findActive(String queryId, Instant now) {
        return findUnexpired(queryId, now);
    }
}
