package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleSourceState;

import java.nio.file.Path;
import java.util.Objects;

/** Trusted host-side registration of a bundle root; it is never accepted from Runtime API input. */
public record AnalyticsBundleRegistration(
        AnalyticsBundleRef bundleRef,
        Path root,
        AnalyticsBundleSourceState sourceState) {

    public AnalyticsBundleRegistration {
        bundleRef = Objects.requireNonNull(bundleRef, "bundleRef");
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        sourceState = Objects.requireNonNull(sourceState, "sourceState");
        if (root.getParent() == null) {
            throw new IllegalArgumentException("Analytics Bundle root cannot be a filesystem root");
        }
    }
}
