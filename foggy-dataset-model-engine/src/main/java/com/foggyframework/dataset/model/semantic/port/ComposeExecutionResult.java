package com.foggyframework.dataset.model.semantic.port;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** DTO-only result returned by the model-side Compose execution boundary. */
public record ComposeExecutionResult(
        ComposeOperation operation,
        boolean valid,
        boolean executed,
        Object value,
        String sql,
        List<Object> params,
        List<String> warnings
) {
    public ComposeExecutionResult {
        params = params == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(params));
        warnings = warnings == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(warnings));
    }
}
