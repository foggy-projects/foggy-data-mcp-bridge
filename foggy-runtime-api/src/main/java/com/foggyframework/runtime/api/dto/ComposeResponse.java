package com.foggyframework.runtime.api.dto;

import java.util.List;

public record ComposeResponse(
        boolean valid,
        String scriptKind,
        String mode,
        Object value,
        String sql,
        List<Object> params,
        List<String> warnings
) {
}
