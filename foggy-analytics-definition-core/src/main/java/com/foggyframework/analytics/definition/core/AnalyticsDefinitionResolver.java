package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;

/** Resolves typed definitions from one explicitly pinned Analytics Bundle revision. */
@FunctionalInterface
public interface AnalyticsDefinitionResolver {

    AnalyticsBundleIndex resolve(
            AnalyticsBundleRef bundleRef,
            AnalyticsBundleRevision expectedRevision);
}
