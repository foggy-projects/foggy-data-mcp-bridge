package com.foggyframework.runtime.api.controller;

import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeScriptService;
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
import com.foggyframework.runtime.api.service.RuntimeComposeException;
import com.foggyframework.runtime.api.service.RuntimeComposeInvocation;
import com.foggyframework.runtime.api.service.RuntimeComposeRunner;
import com.foggyframework.runtime.api.service.RuntimeComposeRunner.RuntimeComposeRunResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/fsscript")
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeFsscriptController {

    private static final String ENGINE = "java";

    private final FoggyRuntimeApiProperties runtimeApiProperties;
    private final RuntimeComposeRunner composeRunner;

    public RuntimeFsscriptController(
            FoggyRuntimeApiProperties runtimeApiProperties,
            RuntimeComposeRunner composeRunner
    ) {
        this.runtimeApiProperties = runtimeApiProperties;
        this.composeRunner = composeRunner;
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
        } catch (RuntimeComposeException e) {
            return fail(e);
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
        RuntimeComposeRunResult result = composeRunner.run(mode, phase,
                RuntimeComposeInvocation.fromFsscriptCteArgs(
                        fsscriptRequest, headerNamespace, authorization, headers, args, phase));
        return result.response();
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

    private RuntimeEnvelope<FsscriptResponse> fail(RuntimeComposeException e) {
        return RuntimeEnvelope.fail(
                ENGINE,
                runtimeApiProperties.getRuntimeApiVersion(),
                e.toRuntimeError(),
                e.diagnostics());
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
}
