package com.foggyframework.runtime.api.dto;

import java.util.List;

public record FsscriptResponse(
        boolean valid,
        String scriptKind,
        String mode,
        Object value,
        List<String> warnings
) {
}
