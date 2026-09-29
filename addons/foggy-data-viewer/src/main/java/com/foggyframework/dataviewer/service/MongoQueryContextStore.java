package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.repository.CachedQueryRepository;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
public class MongoQueryContextStore implements QueryContextStore {

    private final CachedQueryRepository repository;

    @Override
    public CachedQueryContext save(CachedQueryContext context) {
        return repository.save(context);
    }

    @Override
    public Optional<CachedQueryContext> findActive(String queryId, Instant now) {
        return repository.findByQueryIdAndExpiresAtAfter(queryId, now);
    }

    @Override
    public boolean extendExpiry(String queryId, String model, String namespace, Instant now, Instant expiresAt) {
        return findActive(queryId, now)
                .filter(context -> Objects.equals(model, context.getModel())
                        && Objects.equals(namespace, context.getNamespace()))
                .map(context -> {
                    context.setExpiresAt(expiresAt);
                    repository.save(context);
                    return true;
                }).orElse(false);
    }

    @Override
    public void updateEstimatedRowCount(String queryId, Long estimatedRowCount, Instant now) {
        findActive(queryId, now).ifPresent(context -> {
            context.setEstimatedRowCount(estimatedRowCount);
            repository.save(context);
        });
    }
}
