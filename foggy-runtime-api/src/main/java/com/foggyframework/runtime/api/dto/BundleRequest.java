package com.foggyframework.runtime.api.dto;

public record BundleRequest(
        String name,
        String namespace,
        String path,
        Boolean watch,
        Boolean enabled,
        Boolean replace,
        Boolean validate,
        Boolean refresh
) {
}
