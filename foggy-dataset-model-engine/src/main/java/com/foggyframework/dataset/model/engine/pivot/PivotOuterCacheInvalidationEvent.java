package com.foggyframework.dataset.model.engine.pivot;

import java.util.Optional;

/**
 * Transport-safe event payload for Pivot outer-cache invalidation.
 *
 * <p>The namespace/model scope intentionally mirrors
 * {@link PivotOuterCacheProvider#evict(String, String)}: {@code namespace == null}
 * targets all namespaces, blank namespace targets the default namespace, and
 * {@code model == null} targets all models in the selected namespace scope.</p>
 */
public record PivotOuterCacheInvalidationEvent(String namespace,
                                               String model,
                                               String eventId,
                                               String sourceNodeId,
                                               long issuedAtMillis) {

    public PivotOuterCacheInvalidationEvent {
        namespace = normalizeNamespace(namespace);
        model = normalizeModel(model);
        eventId = normalizeBlankToNull(eventId);
        sourceNodeId = normalizeBlankToNull(sourceNodeId);
        if (issuedAtMillis < 0L) {
            throw new IllegalArgumentException("issuedAtMillis must be non-negative");
        }
    }

    public static PivotOuterCacheInvalidationEvent of(String namespace, String model) {
        return new PivotOuterCacheInvalidationEvent(namespace, model, null, null, 0L);
    }

    public static PivotOuterCacheInvalidationEvent all() {
        return of(null, null);
    }

    public PivotOuterCacheInvalidationEvent withMetadata(String eventId,
                                                         String sourceNodeId,
                                                         long issuedAtMillis) {
        return new PivotOuterCacheInvalidationEvent(namespace, model, eventId, sourceNodeId, issuedAtMillis);
    }

    /**
     * Stable replay-deduplication key for event-bus backed broadcasters.
     *
     * <p>Only explicit event ids are deduplicated. Scope-only invalidation is
     * intentionally not treated as a replay key because repeated manual cleanup
     * can be a legitimate operational action.</p>
     */
    public Optional<String> replayDeduplicationKey() {
        if (eventId == null) {
            return Optional.empty();
        }
        return Optional.of("pivot-outer-cache-invalidation:" + eventId);
    }

    public boolean sameReplayEvent(PivotOuterCacheInvalidationEvent other) {
        if (other == null) {
            return false;
        }
        Optional<String> thisKey = replayDeduplicationKey();
        return thisKey.isPresent() && thisKey.equals(other.replayDeduplicationKey());
    }

    public boolean allNamespaces() {
        return namespace == null;
    }

    public boolean defaultNamespace() {
        return namespace != null && namespace.isEmpty();
    }

    public boolean allModels() {
        return model == null;
    }

    public String scope() {
        if (allNamespaces() && allModels()) {
            return "all-namespaces/all-models";
        }
        if (allNamespaces()) {
            return "all-namespaces/model";
        }
        if (allModels()) {
            return "namespace/all-models";
        }
        return "namespace/model";
    }

    private static String normalizeNamespace(String value) {
        if (value == null) {
            return null;
        }
        return value.isBlank() ? "" : value;
    }

    private static String normalizeModel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
