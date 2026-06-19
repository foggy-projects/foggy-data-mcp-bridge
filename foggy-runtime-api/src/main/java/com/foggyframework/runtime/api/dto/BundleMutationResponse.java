package com.foggyframework.runtime.api.dto;

import java.util.List;

public record BundleMutationResponse(
        BundleInfo bundle,
        List<String> warnings
) {
}
