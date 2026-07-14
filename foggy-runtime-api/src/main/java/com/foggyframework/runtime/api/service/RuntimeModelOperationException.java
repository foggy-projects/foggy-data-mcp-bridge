package com.foggyframework.runtime.api.service;

import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleErrorCode;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleFailureContext;

public class RuntimeModelOperationException extends RuntimeException {

    private final String code;
    private final String phase;
    private final String model;
    private final String suggestedNextAction;
    private final boolean safeToAutoRepair;
    private final RuntimeDiagnostics diagnostics;
    private final RuntimeLifecycleErrorCode lifecycleCode;
    private final RuntimeLifecycleFailureContext lifecycle;

    public RuntimeModelOperationException(
            String code,
            String phase,
            String message,
            String model,
            String suggestedNextAction,
            boolean safeToAutoRepair,
            RuntimeDiagnostics diagnostics
    ) {
        this(code, phase, message, model, suggestedNextAction, safeToAutoRepair,
                diagnostics, null, null);
    }

    public RuntimeModelOperationException(
            String code,
            String phase,
            String message,
            String model,
            String suggestedNextAction,
            boolean safeToAutoRepair,
            RuntimeDiagnostics diagnostics,
            RuntimeLifecycleErrorCode lifecycleCode,
            RuntimeLifecycleFailureContext lifecycle
    ) {
        super(message);
        this.code = code;
        this.phase = phase;
        this.model = model;
        this.suggestedNextAction = suggestedNextAction;
        this.safeToAutoRepair = safeToAutoRepair;
        this.diagnostics = diagnostics != null ? diagnostics : RuntimeDiagnostics.empty();
        this.lifecycleCode = lifecycleCode;
        this.lifecycle = lifecycle;
    }

    public String code() {
        return code;
    }

    public String phase() {
        return phase;
    }

    public String model() {
        return model;
    }

    public String suggestedNextAction() {
        return suggestedNextAction;
    }

    public boolean safeToAutoRepair() {
        return safeToAutoRepair;
    }

    public RuntimeDiagnostics diagnostics() {
        return diagnostics;
    }

    public RuntimeLifecycleErrorCode lifecycleCode() {
        return lifecycleCode;
    }

    public RuntimeLifecycleFailureContext lifecycle() {
        return lifecycle;
    }
}
