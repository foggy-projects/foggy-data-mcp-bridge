package com.foggyframework.runtime.api.service;

import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.dto.RuntimeError;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleErrorCode;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleFailureContext;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleSanitizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeApiResponseFactory {

    private static final String ENGINE = "java";

    private final FoggyRuntimeApiProperties properties;

    public RuntimeApiResponseFactory(FoggyRuntimeApiProperties properties) {
        this.properties = properties;
    }

    public String engine() {
        return ENGINE;
    }

    public String runtimeApiVersion() {
        return properties.getRuntimeApiVersion();
    }

    public <T> RuntimeEnvelope<T> ok(T data) {
        return RuntimeEnvelope.ok(engine(), runtimeApiVersion(), data);
    }

    public <T> RuntimeEnvelope<T> ok(T data, RuntimeDiagnostics diagnostics) {
        return RuntimeEnvelope.ok(engine(), runtimeApiVersion(), data, diagnostics);
    }

    public <T> RuntimeEnvelope<T> fail(RuntimeError error, RuntimeDiagnostics diagnostics) {
        return RuntimeEnvelope.fail(engine(), runtimeApiVersion(), error, diagnostics);
    }

    public <T> RuntimeEnvelope<T> fail(
            String code,
            String phase,
            String message,
            String model,
            String field,
            String path,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        return RuntimeEnvelope.fail(engine(), runtimeApiVersion(), code, phase, message, model, field, path,
                suggestedNextAction, safeToAutoRepair);
    }

    public <T> RuntimeEnvelope<T> fail(
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
        return RuntimeEnvelope.fail(engine(), runtimeApiVersion(), code, phase, message, model, field, path,
                suggestedNextAction, safeToAutoRepair, diagnostics);
    }

    public <T> RuntimeEnvelope<T> fail(
            String code,
            String phase,
            String message,
            String model,
            String field,
            String path,
            String suggestedNextAction,
            boolean safeToAutoRepair,
            RuntimeDiagnostics diagnostics,
            RuntimeLifecycleErrorCode lifecycleCode,
            RuntimeLifecycleFailureContext lifecycle
    ) {
        RuntimeDiagnostics sanitizedDiagnostics =
                RuntimeLifecycleSanitizer.sanitizeDiagnostics(diagnostics);
        return fail(new RuntimeError(
                code,
                phase,
                RuntimeLifecycleSanitizer.sanitizeMessage(message),
                RuntimeLifecycleSanitizer.sanitizeTarget(model),
                RuntimeLifecycleSanitizer.sanitizeTarget(field),
                RuntimeLifecycleSanitizer.sanitizeTarget(path),
                suggestedNextAction == null
                        ? null
                        : RuntimeLifecycleSanitizer.sanitizeMessage(
                        suggestedNextAction),
                safeToAutoRepair,
                lifecycleCode,
                lifecycle
        ), sanitizedDiagnostics);
    }
}
