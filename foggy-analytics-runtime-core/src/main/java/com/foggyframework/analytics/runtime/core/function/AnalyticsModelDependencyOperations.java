package com.foggyframework.analytics.runtime.core.function;

import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyDescription;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyList;

/** Host operation that resolves current model content into a stable manifest dependency. */
@FunctionalInterface
public interface AnalyticsModelDependencyOperations {

    AnalyticsModelDependencyDescription resolve(
            String namespace,
            String modelKind,
            String modelName);

    default AnalyticsModelDependencyList list(String namespace, String modelKind) {
        throw new AnalyticsModelDependencyResolutionException(
                AnalyticsModelDependencyResolutionException.Code.REVISION_UNAVAILABLE,
                "Model dependency listing is unavailable");
    }
}
