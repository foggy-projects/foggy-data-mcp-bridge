package com.foggyframework.runtime.api.dto;

import java.util.List;

public record ResourceSaveResponse(
        String namespace,
        String bundle,
        String rootPath,
        Integer savedCount,
        List<ResourceFileInfo> savedResources,
        List<String> warnings
) {
}
