package com.foggyframework.runtime.api.dto;

import java.util.List;

public record ModelRefreshResponse(
        String namespace,
        String scope,
        List<String> clearedCaches,
        List<String> refreshedModels,
        int loadedCount,
        int failedCount,
        List<ModelRefreshFailure> failures,
        List<String> warnings
) {
}
