package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;

/** Revision-guarded access to allowlisted files inside a registered Analytics Bundle. */
public interface AnalyticsArtifactStore {

    byte[] readArtifact(
            AnalyticsBundleRef bundleRef,
            AnalyticsBundleRevision expectedRevision,
            String relativePath);

    ResolvedAnalyticsBundle saveArtifact(
            AnalyticsBundleRef bundleRef,
            AnalyticsBundleRevision expectedRevision,
            String relativePath,
            byte[] content);
}
