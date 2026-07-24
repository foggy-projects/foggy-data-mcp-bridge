package com.foggyframework.dataset.model.engine.pivot;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate result for local or distributed Pivot outer-cache invalidation.
 */
public record PivotOuterCacheInvalidationResult(int removed,
                                                int attemptedNodes,
                                                int succeededNodes,
                                                int failedNodes,
                                                List<String> errors) {

    public PivotOuterCacheInvalidationResult {
        if (removed < 0 || attemptedNodes < 0 || succeededNodes < 0 || failedNodes < 0) {
            throw new IllegalArgumentException("result counters must be non-negative");
        }
        if (succeededNodes + failedNodes > attemptedNodes) {
            throw new IllegalArgumentException("succeededNodes + failedNodes must not exceed attemptedNodes");
        }
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static PivotOuterCacheInvalidationResult local(int removed) {
        return success(1, removed);
    }

    public static PivotOuterCacheInvalidationResult success(int attemptedNodes, int removed) {
        return new PivotOuterCacheInvalidationResult(removed, attemptedNodes, attemptedNodes, 0, List.of());
    }

    public static PivotOuterCacheInvalidationResult unavailable(String error) {
        String message = error == null || error.isBlank() ? "invalidation target is unavailable" : error;
        return new PivotOuterCacheInvalidationResult(0, 1, 0, 1, List.of(message));
    }

    public static PivotOuterCacheInvalidationResult aggregate(List<PivotOuterCacheInvalidationResult> results) {
        if (results == null || results.isEmpty()) {
            return new PivotOuterCacheInvalidationResult(0, 0, 0, 0, List.of());
        }
        int removed = 0;
        int attemptedNodes = 0;
        int succeededNodes = 0;
        int failedNodes = 0;
        List<String> errors = new ArrayList<>();
        for (PivotOuterCacheInvalidationResult result : results) {
            if (result == null) {
                continue;
            }
            removed += result.removed();
            attemptedNodes += result.attemptedNodes();
            succeededNodes += result.succeededNodes();
            failedNodes += result.failedNodes();
            errors.addAll(result.errors());
        }
        return new PivotOuterCacheInvalidationResult(removed, attemptedNodes, succeededNodes, failedNodes, errors);
    }

    public boolean success() {
        return failedNodes == 0;
    }
}
