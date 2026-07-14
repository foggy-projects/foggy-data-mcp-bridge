package com.foggyframework.runtime.api.dto;

public record RuntimeError(
        String code,
        String phase,
        String message,
        String model,
        String field,
        String path,
        String suggestedNextAction,
        boolean safeToAutoRepair,
        RuntimeLifecycleErrorCode lifecycleCode,
        RuntimeLifecycleFailureContext lifecycle
) {

    /** Compatibility constructor retaining the original Runtime API Java surface. */
    public RuntimeError(
            String code,
            String phase,
            String message,
            String model,
            String field,
            String path,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        this(code, phase, message, model, field, path, suggestedNextAction,
                safeToAutoRepair, null, null);
    }
}
