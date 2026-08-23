package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;

/** Resolves a trusted registration to a validated, dependency-current bundle. */
@FunctionalInterface
public interface AnalyticsBundleResolver {

    ResolvedAnalyticsBundle resolve(AnalyticsBundleRef bundleRef);
}
