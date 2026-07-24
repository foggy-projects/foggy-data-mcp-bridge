package com.foggyframework.runtime.api.service;

import com.foggyframework.dataset.db.model.semantic.port.ComposeExecutionException;
import com.foggyframework.dataset.db.model.semantic.port.ComposeExecutionPort;
import com.foggyframework.dataset.db.model.semantic.port.ComposeExecutionResult;
import com.foggyframework.dataset.db.model.semantic.port.ComposeOperation;
import com.foggyframework.runtime.api.dto.ComposeResponse;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.service.RuntimeComposeContextFactory.RuntimeComposeContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeComposeRunner {

    private final ComposeExecutionPort composeExecutionPort;
    private final RuntimeComposeContextFactory contextFactory;

    public RuntimeComposeRunner(
            ComposeExecutionPort composeExecutionPort,
            RuntimeComposeContextFactory contextFactory
    ) {
        this.composeExecutionPort = composeExecutionPort;
        this.contextFactory = contextFactory;
    }

    public RuntimeComposeRunResult run(
            ComposeOperation operation,
            String phase,
            RuntimeComposeInvocation invocation
    ) {
        if (invocation == null || invocation.script() == null || invocation.script().isBlank()) {
            throw new RuntimeComposeException("COMPOSE_SCRIPT_INVALID", phase,
                    "parameter 'script' is required and must be non-blank",
                    null, "Provide an inline compose script.", true);
        }

        RuntimeComposeContext context = null;
        try {
            context = contextFactory.create(
                    invocation.namespace(),
                    invocation.traceId(),
                    invocation.params(),
                    invocation.options(),
                    invocation.headerNamespace(),
                    invocation.authorization(),
                    invocation.headers());
            ComposeExecutionResult result = composeExecutionPort.execute(
                    context.toExecutionRequest(operation, invocation.script()));
            return new RuntimeComposeRunResult(toResponse(result, context), context.diagnostics());
        } catch (ComposeExecutionException e) {
            if (e.kind() == ComposeExecutionException.Kind.SANDBOX) {
                throw failure("COMPOSE_SANDBOX_VIOLATION", phase, e.getMessage(),
                        null, "Remove forbidden script host access and retry.", false,
                        diagnostics(context), e);
            }
            if (e.kind() == ComposeExecutionException.Kind.AUTHORITY) {
                throw failure(mapRuntimeErrorCode(phase), phase, e.getMessage(),
                        null, "Inspect permission diagnostics and retry.", false,
                        diagnostics(context), e);
            }
            String action = e.kind() == ComposeExecutionException.Kind.SCHEMA
                    ? "Inspect compose fields/schema and retry."
                    : "Fix compose script or model metadata and retry.";
            throw failure(mapScriptErrorCode(phase), phase, e.getMessage(),
                    e.field(), action, true, diagnostics(context), e);
        } catch (RuntimeComposeException e) {
            throw e;
        } catch (RuntimeException e) {
            throw failure(mapRuntimeErrorCode(phase), phase, e.getMessage(),
                    null, "Inspect diagnostics and runtime logs, then retry.", false,
                    diagnostics(context), e);
        }
    }

    private ComposeResponse toResponse(
            ComposeExecutionResult result,
            RuntimeComposeContext context
    ) {
        return new ComposeResponse(
                result.valid(),
                "compose",
                result.operation().name().toLowerCase(),
                result.value(),
                result.sql(),
                result.params() != null ? result.params() : List.of(),
                result.warnings() != null ? result.warnings() : List.of(),
                context.diagnosticsAttributes()
        );
    }

    private static RuntimeComposeException failure(
            String code,
            String phase,
            String message,
            String field,
            String suggestedNextAction,
            boolean safeToAutoRepair,
            RuntimeDiagnostics diagnostics,
            Throwable cause
    ) {
        return new RuntimeComposeException(
                code,
                phase,
                message,
                field,
                suggestedNextAction,
                safeToAutoRepair,
                diagnostics,
                cause);
    }

    private static RuntimeDiagnostics diagnostics(RuntimeComposeContext context) {
        return context != null ? context.diagnostics() : RuntimeDiagnostics.empty();
    }

    private static String mapScriptErrorCode(String phase) {
        if ("compose.validate".equals(phase)) {
            return "COMPOSE_SCRIPT_INVALID";
        }
        if ("compose.preview".equals(phase)) {
            return "COMPOSE_COMPILE_FAILED";
        }
        return "COMPOSE_EXECUTE_FAILED";
    }

    private static String mapRuntimeErrorCode(String phase) {
        if ("compose.execute".equals(phase)) {
            return "COMPOSE_EXECUTE_FAILED";
        }
        if ("compose.preview".equals(phase)) {
            return "COMPOSE_COMPILE_FAILED";
        }
        return "COMPOSE_SCRIPT_INVALID";
    }

    public record RuntimeComposeRunResult(
            ComposeResponse response,
            RuntimeDiagnostics diagnostics) {
    }
}
