package com.foggyframework.runtime.api.dto;

public record RuntimeEnvelope<T>(
        boolean success,
        String engine,
        String runtimeApiVersion,
        T data,
        RuntimeDiagnostics diagnostics,
        RuntimeError error
) {
    public static <T> RuntimeEnvelope<T> ok(String engine, String runtimeApiVersion, T data) {
        return new RuntimeEnvelope<>(true, engine, runtimeApiVersion, data, RuntimeDiagnostics.empty(), null);
    }

    public static <T> RuntimeEnvelope<T> ok(
            String engine,
            String runtimeApiVersion,
            T data,
            RuntimeDiagnostics diagnostics
    ) {
        return new RuntimeEnvelope<>(
                true,
                engine,
                runtimeApiVersion,
                data,
                diagnostics != null ? diagnostics : RuntimeDiagnostics.empty(),
                null
        );
    }

    public static <T> RuntimeEnvelope<T> fail(
            String engine,
            String runtimeApiVersion,
            RuntimeError error,
            RuntimeDiagnostics diagnostics
    ) {
        return new RuntimeEnvelope<>(
                false,
                engine,
                runtimeApiVersion,
                null,
                diagnostics != null ? diagnostics : RuntimeDiagnostics.empty(),
                error
        );
    }

    public static <T> RuntimeEnvelope<T> fail(
            String engine,
            String runtimeApiVersion,
            String code,
            String phase,
            String message,
            String model,
            String field,
            String path,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        return fail(engine, runtimeApiVersion, code, phase, message, model, field, path,
                suggestedNextAction, safeToAutoRepair, RuntimeDiagnostics.empty());
    }

    public static <T> RuntimeEnvelope<T> fail(
            String engine,
            String runtimeApiVersion,
            String code,
            String phase,
            String message,
            String model,
            String field,
            String path,
            String suggestedNextAction,
            boolean safeToAutoRepair,
            RuntimeDiagnostics diagnostics
    ) {
        RuntimeError error = new RuntimeError(
                code,
                phase,
                message,
                model,
                field,
                path,
                suggestedNextAction,
                safeToAutoRepair
        );
        return fail(engine, runtimeApiVersion, error, diagnostics);
    }
}
