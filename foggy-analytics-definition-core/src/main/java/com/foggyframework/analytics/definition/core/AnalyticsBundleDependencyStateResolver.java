package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsBundleDependencyState;
import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;

/** Product/engine adapter port used to derive whether pinned model dependencies are still current. */
@FunctionalInterface
public interface AnalyticsBundleDependencyStateResolver {

    AnalyticsBundleDependencyState resolve(AnalyticsBundleManifest manifest);

    /** Safe default until a model-catalog adapter is installed. */
    static AnalyticsBundleDependencyStateResolver failClosed() {
        return manifest -> manifest.modelDependencies().isEmpty()
                ? AnalyticsBundleDependencyState.CURRENT
                : AnalyticsBundleDependencyState.STALE;
    }
}
