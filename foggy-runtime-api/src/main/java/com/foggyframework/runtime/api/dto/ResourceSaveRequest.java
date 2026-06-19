package com.foggyframework.runtime.api.dto;

import java.util.List;

public record ResourceSaveRequest(
        String namespace,
        String bundle,
        List<ResourceSaveFile> files,
        Boolean validate,
        Boolean refresh
) {
}
