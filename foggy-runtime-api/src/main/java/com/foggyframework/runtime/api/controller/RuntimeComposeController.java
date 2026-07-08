package com.foggyframework.runtime.api.controller;

import com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeCompileException;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeScriptService;
import com.foggyframework.dataset.db.model.engine.compose.sandbox.ComposeSandboxViolationException;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaException;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.ComposeRequest;
import com.foggyframework.runtime.api.dto.ComposeResponse;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.dto.RuntimeError;
import com.foggyframework.runtime.api.service.RuntimeComposeContextFactory;
import com.foggyframework.runtime.api.service.RuntimeComposeContextFactory.RuntimeComposeContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/compose")
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeComposeController {

    private static final String ENGINE = "java";

    private final FoggyRuntimeApiProperties runtimeApiProperties;
    private final SemanticQueryServiceV3 semanticQueryServiceV3;
    private final RuntimeComposeContextFactory contextFactory;

    public RuntimeComposeController(
            FoggyRuntimeApiProperties runtimeApiProperties,
            SemanticQueryServiceV3 semanticQueryServiceV3,
            RuntimeComposeContextFactory contextFactory
    ) {
        this.runtimeApiProperties = runtimeApiProperties;
        this.semanticQueryServiceV3 = semanticQueryServiceV3;
        this.contextFactory = contextFactory;
    }

    @PostMapping("/validate")
    public RuntimeEnvelope<ComposeResponse> validate(
            @RequestBody(required = false) ComposeRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader Map<String, String> headers
    ) {
        return run(ComposeScriptService.Mode.VALIDATE, "compose.validate",
                request, authorization, namespace, headers);
    }

    @PostMapping("/preview")
    public RuntimeEnvelope<ComposeResponse> preview(
            @RequestBody(required = false) ComposeRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader Map<String, String> headers
    ) {
        return run(ComposeScriptService.Mode.PREVIEW, "compose.preview",
                request, authorization, namespace, headers);
    }

    @PostMapping("/execute")
    public RuntimeEnvelope<ComposeResponse> execute(
            @RequestBody(required = false) ComposeRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader Map<String, String> headers
    ) {
        return run(ComposeScriptService.Mode.EXECUTE, "compose.execute",
                request, authorization, namespace, headers);
    }

    private RuntimeEnvelope<ComposeResponse> run(
            ComposeScriptService.Mode mode,
            String phase,
            ComposeRequest request,
            String authorization,
            String namespace,
            Map<String, String> headers
    ) {
        if (request == null || request.script() == null || request.script().isBlank()) {
            return fail("COMPOSE_SCRIPT_INVALID", phase,
                    "parameter 'script' is required and must be non-blank",
                    null, "Provide an inline compose script.", true);
        }

        RuntimeComposeContext context = null;
        try {
            context = contextFactory.create(
                    request.namespace(),
                    request.traceId(),
                    request.params(),
                    request.options(),
                    namespace,
                    authorization,
                    headers);
            ComposeScriptService.ComposeScriptResult result = ComposeScriptService.run(
                    context.toScriptRequest(mode, request.script(), semanticQueryServiceV3));
            return RuntimeEnvelope.ok(
                    ENGINE,
                    runtimeApiProperties.getRuntimeApiVersion(),
                    toResponse(result, context),
                    context.diagnostics());
        } catch (ComposeSandboxViolationException e) {
            return fail("COMPOSE_SANDBOX_VIOLATION", phase, e.getMessage(),
                    null, "Remove forbidden script host access and retry.", false,
                    diagnostics(context));
        } catch (ComposeSchemaException e) {
            return fail(mapScriptErrorCode(phase), phase, e.getMessage(),
                    e.offendingField(), "Inspect compose fields/schema and retry.", true,
                    diagnostics(context));
        } catch (ComposeCompileException e) {
            return fail(mapScriptErrorCode(phase), phase, e.getMessage(),
                    null, "Fix compose script or model metadata and retry.", true,
                    diagnostics(context));
        } catch (RuntimeException e) {
            return fail(mapRuntimeErrorCode(phase), phase, e.getMessage(),
                    null, "Inspect diagnostics and runtime logs, then retry.", false,
                    diagnostics(context));
        }
    }

    private ComposeResponse toResponse(
            ComposeScriptService.ComposeScriptResult result,
            RuntimeComposeContext context) {
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

    private RuntimeEnvelope<ComposeResponse> fail(
            String code,
            String phase,
            String message,
            String field,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        return fail(code, phase, message, field, suggestedNextAction, safeToAutoRepair,
                RuntimeDiagnostics.empty());
    }

    private RuntimeEnvelope<ComposeResponse> fail(
            String code,
            String phase,
            String message,
            String field,
            String suggestedNextAction,
            boolean safeToAutoRepair,
            RuntimeDiagnostics diagnostics
    ) {
        RuntimeError error = new RuntimeError(
                code,
                phase,
                message,
                null,
                field,
                null,
                suggestedNextAction,
                safeToAutoRepair
        );
        return RuntimeEnvelope.fail(ENGINE, runtimeApiProperties.getRuntimeApiVersion(), error, diagnostics);
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

}
