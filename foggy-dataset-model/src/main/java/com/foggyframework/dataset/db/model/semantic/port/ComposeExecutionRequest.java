package com.foggyframework.dataset.db.model.semantic.port;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** DTO-only request accepted by the model-side Compose execution boundary. */
public record ComposeExecutionRequest(
        ComposeOperation operation,
        String script,
        String namespace,
        String traceId,
        Map<String, Object> params,
        ComposeCaller caller,
        String dialect
) {
    public ComposeExecutionRequest {
        operation = operation == null ? ComposeOperation.EXECUTE : operation;
        params = params == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(params));
        if (caller == null) {
            throw new IllegalArgumentException("ComposeExecutionRequest.caller is required");
        }
    }
}
