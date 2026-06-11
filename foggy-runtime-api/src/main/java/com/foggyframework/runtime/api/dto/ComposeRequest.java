package com.foggyframework.runtime.api.dto;

import java.util.Map;

public record ComposeRequest(
        String script,
        Map<String, Object> params,
        Map<String, Object> options,
        String namespace,
        String traceId
) {
}
