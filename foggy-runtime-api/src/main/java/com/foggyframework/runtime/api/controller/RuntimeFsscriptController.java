package com.foggyframework.runtime.api.controller;

import com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeCompileException;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeScriptService;
import com.foggyframework.dataset.db.model.engine.compose.sandbox.ComposeSandboxViolationException;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaException;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;
import com.foggyframework.fsscript.utils.ExpUtils;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.ComposeResponse;
import com.foggyframework.runtime.api.dto.FsscriptRequest;
import com.foggyframework.runtime.api.dto.FsscriptResponse;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/fsscript")
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeFsscriptController {

    private static final String ENGINE = "java";

    private final FoggyRuntimeApiProperties runtimeApiProperties;
    private final SemanticQueryServiceV3 semanticQueryServiceV3;
    private final RuntimeComposeContextFactory contextFactory;

    public RuntimeFsscriptController(
            FoggyRuntimeApiProperties runtimeApiProperties,
            SemanticQueryServiceV3 semanticQueryServiceV3,
            RuntimeComposeContextFactory contextFactory
    ) {
        this.runtimeApiProperties = runtimeApiProperties;
        this.semanticQueryServiceV3 = semanticQueryServiceV3;
        this.contextFactory = contextFactory;
    }

    @PostMapping("/execute")
    public RuntimeEnvelope<FsscriptResponse> execute(
            @RequestBody(required = false) FsscriptRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader Map<String, String> headers
    ) {
        if (request == null || request.script() == null || request.script().isBlank()) {
            return fail("FSSCRIPT_EXECUTE_FAILED", "fsscript.execute",
                    "parameter 'script' is required and must be non-blank",
                    null, "Provide an inline fsscript.", true);
        }

        try {
            SimpleFsscriptClosureDefinitionSpace space = new SimpleFsscriptClosureDefinitionSpace();
            FsscriptClosureDefinition definition = space.newFsscriptClosureDefinition();
            Exp exp = ExpUtils.compileEl(definition, request.script(), null);
            ExpEvaluator evaluator = DefaultExpEvaluator.newInstance(null, definition.newFoggyClosure());
            evaluator.setVar("params", request.params() != null ? request.params() : Map.of());
            if (request.params() != null) {
                evaluator.setMap2Var(request.params());
            }
            evaluator.setVar("foggy", foggyHost(request, namespace, authorization, headers));

            Object value = exp != null ? exp.evalResult(evaluator) : null;
            FsscriptResponse response = new FsscriptResponse(true, "fsscript", "execute", value, List.of());
            return RuntimeEnvelope.ok(ENGINE, runtimeApiProperties.getRuntimeApiVersion(), response);
        } catch (CteBridgeDeniedException e) {
            return fail("FSSCRIPT_CTE_BRIDGE_DENIED", "fsscript.execute", e.getMessage(),
                    null, "Enable capabilities.cteBridge for this dev/test request and retry.", false);
        } catch (ComposeBridgeException e) {
            return fail(e.code(), e.phase(), e.getMessage(), e.field(), e.suggestedNextAction(), e.safeToAutoRepair());
        } catch (RuntimeException e) {
            return fail("FSSCRIPT_EXECUTE_FAILED", "fsscript.execute", e.getMessage(),
                    null, "Inspect the fsscript source and runtime diagnostics, then retry.", false);
        }
    }

    private FoggyHost foggyHost(
            FsscriptRequest request,
            String headerNamespace,
            String authorization,
            Map<String, String> headers
    ) {
        PropertyFunction cte = cteBridgeEnabled(request)
                ? new CteFunctions(request, headerNamespace, authorization, headers)
                : new DeniedCteFunctions();
        return new FoggyHost(cte);
    }

    private boolean cteBridgeEnabled(FsscriptRequest request) {
        return booleanFlag(request.capabilities(), "cteBridge")
                || booleanFlag(request.options(), "cteBridge");
    }

    private ComposeResponse invokeCte(
            FsscriptRequest fsscriptRequest,
            String headerNamespace,
            String authorization,
            Map<String, String> headers,
            ComposeScriptService.Mode mode,
            String phase,
            Object[] args
    ) {
        ComposeRequestParts compose = composeRequest(args, phase);
        if (compose.script() == null || compose.script().isBlank()) {
            throw new ComposeBridgeException("COMPOSE_SCRIPT_INVALID", phase,
                    "parameter 'script' is required and must be non-blank",
                    null, "Provide an inline compose script.", true);
        }

        try {
            RuntimeComposeContext context = contextFactory.create(
                    fsscriptRequest.namespace(),
                    fsscriptRequest.traceId(),
                    compose.params(),
                    fsscriptRequest.options(),
                    headerNamespace,
                    authorization,
                    headers);
            ComposeScriptService.ComposeScriptResult result = ComposeScriptService.run(
                    context.toScriptRequest(mode, compose.script(), semanticQueryServiceV3));
            return toResponse(result, context);
        } catch (ComposeSandboxViolationException e) {
            throw new ComposeBridgeException("COMPOSE_SANDBOX_VIOLATION", phase, e.getMessage(),
                    null, "Remove forbidden script host access and retry.", false);
        } catch (ComposeSchemaException e) {
            throw new ComposeBridgeException(mapScriptErrorCode(phase), phase, e.getMessage(),
                    e.offendingField(), "Inspect compose fields/schema and retry.", true);
        } catch (ComposeCompileException e) {
            throw new ComposeBridgeException(mapScriptErrorCode(phase), phase, e.getMessage(),
                    null, "Fix compose script or model metadata and retry.", true);
        } catch (RuntimeException e) {
            throw new ComposeBridgeException(mapRuntimeErrorCode(phase), phase, e.getMessage(),
                    null, "Inspect diagnostics and runtime logs, then retry.", false);
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

    private ComposeRequestParts composeRequest(Object[] args, String phase) {
        if (args == null || args.length == 0 || args[0] == null) {
            return new ComposeRequestParts(null, Map.of());
        }
        Object arg = args[0];
        if (arg instanceof String script) {
            return new ComposeRequestParts(script, Map.of());
        }
        if (arg instanceof Map<?, ?> map) {
            return new ComposeRequestParts(
                    stringValue(map.get("script")),
                    objectMap(map.get("params"))
            );
        }
        throw new ComposeBridgeException("COMPOSE_SCRIPT_INVALID", phase,
                "cte request must be a string script or object containing script",
                null, "Pass foggy.cte.*({ script: '...' }).", true);
    }

    private RuntimeEnvelope<FsscriptResponse> fail(
            String code,
            String phase,
            String message,
            String field,
            String suggestedNextAction,
            boolean safeToAutoRepair
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
        return RuntimeEnvelope.fail(ENGINE, runtimeApiProperties.getRuntimeApiVersion(), error, RuntimeDiagnostics.empty());
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

    private static boolean booleanFlag(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return false;
        }
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> {
                if (key != null) {
                    result.put(key.toString(), mapValue);
                }
            });
            return result;
        }
        return Map.of();
    }

    private record ComposeRequestParts(String script, Map<String, Object> params) {
    }

    private final class CteFunctions implements PropertyFunction {
        private final FsscriptRequest request;
        private final String headerNamespace;
        private final String authorization;
        private final Map<String, String> headers;

        private CteFunctions(
                FsscriptRequest request,
                String headerNamespace,
                String authorization,
                Map<String, String> headers
        ) {
            this.request = request;
            this.headerNamespace = headerNamespace;
            this.authorization = authorization;
            this.headers = headers;
        }

        @Override
        public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
            if ("validate".equals(methodName)) {
                return invokeCte(request, headerNamespace, authorization, headers,
                        ComposeScriptService.Mode.VALIDATE, "compose.validate", args);
            }
            if ("preview".equals(methodName)) {
                return invokeCte(request, headerNamespace, authorization, headers,
                        ComposeScriptService.Mode.PREVIEW, "compose.preview", args);
            }
            if ("execute".equals(methodName)) {
                return invokeCte(request, headerNamespace, authorization, headers,
                        ComposeScriptService.Mode.EXECUTE, "compose.execute", args);
            }
            throw new CteBridgeDeniedException("Unsupported foggy.cte function: " + methodName);
        }
    }

    private static final class DeniedCteFunctions implements PropertyFunction {
        @Override
        public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
            throw new CteBridgeDeniedException("foggy.cte." + methodName + " is disabled for this request.");
        }
    }

    private static final class FoggyHost implements PropertyHolder {
        private final PropertyFunction cte;

        private FoggyHost(PropertyFunction cte) {
            this.cte = cte;
        }

        @Override
        public Object getProperty(String name) {
            if ("cte".equals(name)) {
                return cte;
            }
            return PropertyHolder.NO_MATCH;
        }
    }

    private static final class CteBridgeDeniedException extends RuntimeException {
        private CteBridgeDeniedException(String message) {
            super(message);
        }
    }

    private static final class ComposeBridgeException extends RuntimeException {
        private final String code;
        private final String phase;
        private final String field;
        private final String suggestedNextAction;
        private final boolean safeToAutoRepair;

        private ComposeBridgeException(
                String code,
                String phase,
                String message,
                String field,
                String suggestedNextAction,
                boolean safeToAutoRepair
        ) {
            super(message);
            this.code = code;
            this.phase = phase;
            this.field = field;
            this.suggestedNextAction = suggestedNextAction;
            this.safeToAutoRepair = safeToAutoRepair;
        }

        private String code() {
            return code;
        }

        private String phase() {
            return phase;
        }

        private String field() {
            return field;
        }

        private String suggestedNextAction() {
            return suggestedNextAction;
        }

        private boolean safeToAutoRepair() {
            return safeToAutoRepair;
        }
    }
}
