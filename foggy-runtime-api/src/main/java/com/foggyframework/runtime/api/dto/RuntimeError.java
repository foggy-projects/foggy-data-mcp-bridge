package com.foggyframework.runtime.api.dto;

public record RuntimeError(
        String code,
        String phase,
        String message,
        String model,
        String field,
        String path,
        String suggestedNextAction,
        boolean safeToAutoRepair
) {
}
