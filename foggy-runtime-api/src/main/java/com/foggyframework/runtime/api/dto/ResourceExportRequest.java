package com.foggyframework.runtime.api.dto;

import java.util.List;

public record ResourceExportRequest(
        String namespace,
        String bundle,
        List<String> paths,
        Boolean includeContent
) {
}
