package com.foggyframework.runtime.api.dto;

import java.util.List;

public record ModelValidateResponse(
        boolean valid,
        String namespace,
        String path,
        int totalFiles,
        int validFiles,
        int invalidFiles,
        int cascadingErrors,
        Long durationMs,
        List<ModelValidateIssue> errors,
        List<ModelValidateIssue> warnings
) {
}
