package com.foggyframework.runtime.api.service;

import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.dto.RuntimeError;

public final class RuntimeComposeException extends RuntimeException {

    private final String code;
    private final String phase;
    private final String field;
    private final String suggestedNextAction;
    private final boolean safeToAutoRepair;
    private final RuntimeDiagnostics diagnostics;

    public RuntimeComposeException(
            String code,
            String phase,
            String message,
            String field,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        this(code, phase, message, field, suggestedNextAction, safeToAutoRepair,
                RuntimeDiagnostics.empty(), null);
    }

    public RuntimeComposeException(
            String code,
            String phase,
            String message,
            String field,
            String suggestedNextAction,
            boolean safeToAutoRepair,
            RuntimeDiagnostics diagnostics,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.phase = phase;
        this.field = field;
        this.suggestedNextAction = suggestedNextAction;
        this.safeToAutoRepair = safeToAutoRepair;
        this.diagnostics = diagnostics != null ? diagnostics : RuntimeDiagnostics.empty();
    }

    public RuntimeError toRuntimeError() {
        return new RuntimeError(
                code,
                phase,
                getMessage(),
                null,
                field,
                null,
                suggestedNextAction,
                safeToAutoRepair
        );
    }

    public RuntimeDiagnostics diagnostics() {
        return diagnostics;
    }
}
