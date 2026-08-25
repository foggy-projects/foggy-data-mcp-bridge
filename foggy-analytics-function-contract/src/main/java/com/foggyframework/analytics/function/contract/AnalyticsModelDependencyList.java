package com.foggyframework.analytics.function.contract;

import java.util.List;
import java.util.Objects;

/** Current selectable models available inside one namespace. */
public record AnalyticsModelDependencyList(
        String namespace,
        String modelKind,
        List<AnalyticsModelSummary> models) {

    public AnalyticsModelDependencyList {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelKind = AnalyticsFunctionValues.requireText("modelKind", modelKind);
        models = List.copyOf(Objects.requireNonNull(models, "models"));
        for (AnalyticsModelSummary value : models) {
            if (!namespace.equals(value.namespace())
                    || !modelKind.equals(value.modelKind())) {
                throw new IllegalArgumentException(
                        "models must belong to the requested namespace and model kind");
            }
        }
    }
}
