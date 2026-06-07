package com.foggyframework.runtime.api.dto;

public record ModelValidateIssue(
        String file,
        String type,
        Integer line,
        Integer column,
        String severity,
        String code,
        String message,
        String suggestion,
        String category,
        String stackTrace
) {
}
