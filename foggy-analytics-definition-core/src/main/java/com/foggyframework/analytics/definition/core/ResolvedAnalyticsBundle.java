package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsBundleLifecycle;
import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;

import java.util.Objects;

/** Validated bundle identity returned without exposing its host filesystem path. */
public record ResolvedAnalyticsBundle(
        AnalyticsBundleManifest manifest,
        AnalyticsBundleLifecycle lifecycle) {

    public ResolvedAnalyticsBundle {
        manifest = Objects.requireNonNull(manifest, "manifest");
        lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    public AnalyticsBundleRevision bundleRevision() {
        return manifest.bundleRevision();
    }
}
