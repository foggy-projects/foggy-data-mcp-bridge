package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runtime guard for optional external Pivot outer-cache providers.
 *
 * <p>Distributed cache storage is an acceleration path, not a query
 * correctness dependency. By default this wrapper turns provider runtime
 * failures, such as an unavailable Redis service, into cache misses or no-op
 * writes so the query engine remains usable.</p>
 */
public final class PivotOuterCacheSafeProvider implements PivotOuterCacheProvider {

    static final String UNAVAILABLE_NAME = "pivot_outer_cache_provider_unavailable";

    private final PivotOuterCacheProvider delegate;
    private final boolean failOnProviderUnavailable;
    private final ThreadLocal<UnavailableEvent> lastUnavailable = new ThreadLocal<>();

    private PivotOuterCacheSafeProvider(PivotOuterCacheProvider delegate, boolean failOnProviderUnavailable) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.failOnProviderUnavailable = failOnProviderUnavailable;
    }

    public static PivotOuterCacheProvider wrap(PivotOuterCacheProvider delegate, boolean failOnProviderUnavailable) {
        if (delegate instanceof PivotOuterCacheSafeProvider safeProvider
                && safeProvider.failOnProviderUnavailable == failOnProviderUnavailable) {
            return delegate;
        }
        return new PivotOuterCacheSafeProvider(delegate, failOnProviderUnavailable);
    }

    @Override
    public String name() {
        return safe("name", delegate::name, UNAVAILABLE_NAME);
    }

    @Override
    public boolean isEnabled() {
        return safe("isEnabled", delegate::isEnabled, false);
    }

    @Override
    public long ttlMillis() {
        return safe("ttlMillis", delegate::ttlMillis, 0L);
    }

    @Override
    public LookupResult lookup(String keyHash, long nowMillis) {
        return safe("lookup", () -> delegate.lookup(keyHash, nowMillis), LookupResult.miss());
    }

    @Override
    public void store(String keyHash,
                      SemanticQueryResponse response,
                      long nowMillis,
                      String namespace,
                      String model) {
        safeRun("store", () -> delegate.store(keyHash, response, nowMillis, namespace, model));
    }

    @Override
    public int evict(String namespace, String model) {
        return safe("evict", () -> delegate.evict(namespace, model), 0);
    }

    @Override
    public int estimatePayloadBytes(SemanticQueryResponse response) {
        return safe("estimatePayloadBytes", () -> delegate.estimatePayloadBytes(response), 0);
    }

    public Optional<UnavailableEvent> consumeLastUnavailable() {
        UnavailableEvent event = lastUnavailable.get();
        lastUnavailable.remove();
        return Optional.ofNullable(event);
    }

    private <T> T safe(String operation, Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (RuntimeException ex) {
            if (failOnProviderUnavailable) {
                throw unavailable(operation, ex);
            }
            lastUnavailable.set(unavailableEvent(operation, ex));
            return fallback;
        }
    }

    private void safeRun(String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            if (failOnProviderUnavailable) {
                throw unavailable(operation, ex);
            }
            lastUnavailable.set(unavailableEvent(operation, ex));
        }
    }

    private RuntimeException unavailable(String operation, RuntimeException cause) {
        return new IllegalStateException(
                "Pivot outer-cache provider unavailable during " + operation + " (" + providerLabel() + ")",
                cause);
    }

    private String providerLabel() {
        try {
            String name = delegate.name();
            return name == null || name.isBlank() ? delegate.getClass().getName() : name;
        } catch (RuntimeException ex) {
            return delegate.getClass().getName();
        }
    }

    private UnavailableEvent unavailableEvent(String operation, RuntimeException cause) {
        return new UnavailableEvent(operation, providerLabel(), cause.getClass().getSimpleName(), safeReason(cause));
    }

    private String safeReason(RuntimeException cause) {
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        String normalized = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() > 240) {
            return normalized.substring(0, 240) + "...";
        }
        return normalized;
    }

    public record UnavailableEvent(String operation, String providerName, String reasonClass, String reason) {
    }
}
