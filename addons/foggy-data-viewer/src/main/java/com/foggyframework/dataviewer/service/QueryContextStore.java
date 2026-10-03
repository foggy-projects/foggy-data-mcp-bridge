package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.CachedQueryContext;

import java.time.Instant;
import java.util.Optional;

/** Storage boundary for short-lived DataViewer query definitions. */
public interface QueryContextStore {

    CachedQueryContext save(CachedQueryContext context);

    Optional<CachedQueryContext> findActive(String queryId, Instant now);

    default boolean extendExpiry(String queryId, String model, String namespace, Instant now, Instant expiresAt) {
        return findActive(queryId, now)
                .filter(context -> java.util.Objects.equals(model, context.getModel())
                        && java.util.Objects.equals(namespace, context.getNamespace()))
                .map(context -> {
                    context.setExpiresAt(expiresAt);
                    save(context);
                    return true;
                })
                .orElse(false);
    }

    default void updateEstimatedRowCount(String queryId, Long estimatedRowCount, Instant now) {
        findActive(queryId, now).ifPresent(context -> {
            context.setEstimatedRowCount(estimatedRowCount);
            save(context);
        });
    }
}
