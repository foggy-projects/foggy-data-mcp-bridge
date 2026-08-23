package com.foggyframework.analytics.runtime.api.dto;

import java.util.List;

public record AnalyticsBundleListResponse(List<AnalyticsBundleSummary> bundles) {

    public AnalyticsBundleListResponse {
        bundles = List.copyOf(bundles);
    }
}
