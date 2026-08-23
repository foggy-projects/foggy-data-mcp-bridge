package com.foggyframework.analytics.definition.api;

import java.util.Objects;

/**
 * Runtime storage state plus derived model dependency state.
 *
 * <p>Stale is deliberately represented separately from publication state so
 * a product publish operation cannot be confused with dependency resolution.</p>
 */
public record AnalyticsBundleLifecycle(
        AnalyticsBundleSourceState sourceState,
        AnalyticsBundleDependencyState dependencyState) {

    public AnalyticsBundleLifecycle {
        sourceState = Objects.requireNonNull(sourceState, "sourceState");
        dependencyState = Objects.requireNonNull(dependencyState, "dependencyState");
    }

    public boolean isWritable() {
        return sourceState == AnalyticsBundleSourceState.RUNTIME_OWNED;
    }

    public boolean isImmutable() {
        return sourceState == AnalyticsBundleSourceState.PUBLISHED;
    }

    public boolean isStale() {
        return dependencyState == AnalyticsBundleDependencyState.STALE;
    }
}
