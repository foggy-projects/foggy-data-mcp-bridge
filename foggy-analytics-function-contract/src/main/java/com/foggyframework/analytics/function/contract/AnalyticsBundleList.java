package com.foggyframework.analytics.function.contract;

import java.util.List;
import java.util.Objects;

public record AnalyticsBundleList(List<AnalyticsBundleDescription> bundles) {

    public AnalyticsBundleList {
        bundles = List.copyOf(Objects.requireNonNull(bundles, "bundles"));
    }
}
