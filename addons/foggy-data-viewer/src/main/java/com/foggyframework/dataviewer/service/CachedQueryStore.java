package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.CachedQueryContext;

import java.time.Instant;
import java.util.Optional;

/** Short-lived storage for DataViewer preview requests. */
public interface CachedQueryStore {
    CachedQueryContext save(CachedQueryContext context);

    Optional<CachedQueryContext> findUnexpired(String queryId, Instant now);
}
