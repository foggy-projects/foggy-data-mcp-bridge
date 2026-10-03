package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.CachedQueryContext;

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded, process-local preview cache for the Mongo-free development Runtime. */
public final class InMemoryCachedQueryStore implements CachedQueryStore {
    private final ConcurrentHashMap<String, CachedQueryContext> entries = new ConcurrentHashMap<>();
    private final int maxEntries;

    public InMemoryCachedQueryStore(int maxEntries) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    @Override
    public synchronized CachedQueryContext save(CachedQueryContext context) {
        Instant now = Instant.now();
        entries.values().removeIf(entry -> entry.getExpiresAt() != null && !entry.getExpiresAt().isAfter(now));
        if (!entries.containsKey(context.getQueryId()) && entries.size() >= maxEntries) {
            entries.values().stream()
                    .min(Comparator.comparing(CachedQueryContext::getCreatedAt))
                    .ifPresent(oldest -> entries.remove(oldest.getQueryId()));
        }
        entries.put(context.getQueryId(), context);
        return context;
    }

    @Override
    public Optional<CachedQueryContext> findUnexpired(String queryId, Instant now) {
        CachedQueryContext context = entries.get(queryId);
        if (context == null) return Optional.empty();
        if (context.getExpiresAt() != null && !context.getExpiresAt().isAfter(now)) {
            entries.remove(queryId, context);
            return Optional.empty();
        }
        return Optional.of(context);
    }
}
