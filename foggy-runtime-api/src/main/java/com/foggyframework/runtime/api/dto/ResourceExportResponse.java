package com.foggyframework.runtime.api.dto;

import java.util.List;

public record ResourceExportResponse(
        String namespace,
        String bundle,
        String rootPath,
        List<ResourceFileInfo> resources,
        List<String> warnings
) {
}
