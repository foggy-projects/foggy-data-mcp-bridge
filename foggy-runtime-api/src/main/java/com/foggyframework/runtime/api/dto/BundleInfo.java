package com.foggyframework.runtime.api.dto;

public record BundleInfo(
        String name,
        String namespace,
        String path,
        Boolean watch,
        Boolean enabled,
        String source,
        Boolean managedByRuntimeApi,
        Boolean canUpdate,
        Boolean canRemove,
        String status,
        String message
) {
}
