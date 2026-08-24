package com.foggyframework.analytics.runtime.core.function;

import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyDescription;

/** Host operation that resolves current model content into a stable manifest dependency. */
@FunctionalInterface
public interface AnalyticsModelDependencyOperations {

    AnalyticsModelDependencyDescription resolve(
            String namespace,
            String modelKind,
            String modelName);
}
