package com.foggyframework.runtime.api.dto;

import java.util.Map;

public record FsscriptRequest(
        String script,
        Map<String, Object> params,
        Map<String, Object> options,
        Map<String, Object> capabilities,
        String namespace,
        String traceId
) {
}
