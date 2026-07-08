package com.foggyframework.runtime.api.service;

import com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeCompileException;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeScriptService;
import com.foggyframework.dataset.db.model.engine.compose.sandbox.ComposeSandboxViolationException;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaException;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.runtime.api.dto.ComposeResponse;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.service.RuntimeComposeContextFactory.RuntimeComposeContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeComposeRunner {

    private final SemanticQueryServiceV3 semanticQueryServiceV3;
    private final RuntimeComposeContextFactory contextFactory;

    public RuntimeComposeRunner(
            SemanticQueryServiceV3 semanticQueryServiceV3,
            RuntimeComposeContextFactory contextFactory
    ) {
        this.semanticQueryServiceV3 = semanticQueryServiceV3;
        this.contextFactory = contextFactory;
    }

    public RuntimeComposeRunResult run(
            ComposeScriptService.Mode mode,
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
            ComposeScriptService.ComposeScriptResult result = ComposeScriptService.run(
                    context.toScriptRequest(mode, invocation.script(), semanticQueryServiceV3));
            return new RuntimeComposeRunResult(toResponse(result, context), context.diagnostics());
        } catch (ComposeSandboxViolationException e) {
            throw failure("COMPOSE_SANDBOX_VIOLATION", phase, e.getMessage(),
                    null, "Remove forbidden script host access and retry.", false,
                    diagnostics(context), e);
        } catch (ComposeSchemaException e) {
            throw failure(mapScriptErrorCode(phase), phase, e.getMessage(),
                    e.offendingField(), "Inspect compose fields/schema and retry.", true,
                    diagnostics(context), e);
        } catch (ComposeCompileException e) {
            throw failure(mapScriptErrorCode(phase), phase, e.getMessage(),
                    null, "Fix compose script or model metadata and retry.", true,
                    diagnostics(context), e);
        } catch (RuntimeComposeException e) {
            throw e;
        } catch (RuntimeException e) {
            throw failure(mapRuntimeErrorCode(phase), phase, e.getMessage(),
                    null, "Inspect diagnostics and runtime logs, then retry.", false,
                    diagnostics(context), e);
        }
    }

    private ComposeResponse toResponse(
            ComposeScriptService.ComposeScriptResult result,
            RuntimeComposeContext context
    ) {
        return new ComposeResponse(
                result.valid(),
                "compose",
                result.mode().name().toLowerCase(),
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
