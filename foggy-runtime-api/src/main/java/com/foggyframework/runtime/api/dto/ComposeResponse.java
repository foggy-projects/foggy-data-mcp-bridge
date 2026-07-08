package com.foggyframework.runtime.api.dto;

import java.util.List;
import java.util.Map;

public record ComposeResponse(
        boolean valid,
        String scriptKind,
        String mode,
        Object value,
        String sql,
        List<Object> params,
        List<String> warnings,
        Map<String, Object> diagnostics
) {
}
