package com.foggyframework.runtime.api.dto;

import java.util.List;

public record BundleListResponse(
        List<BundleInfo> bundles,
        List<String> warnings
) {
}
