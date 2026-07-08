package com.foggyframework.runtime.api.controller;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.utils.ExpUtils;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.FsscriptRequest;
import com.foggyframework.runtime.api.dto.FsscriptResponse;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeComposeException;
import com.foggyframework.runtime.api.service.RuntimeFsscriptCteBridge;
import com.foggyframework.runtime.api.service.RuntimeFsscriptCteBridge.CteBridgeDeniedException;
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
    private final RuntimeFsscriptCteBridge cteBridge;

    public RuntimeFsscriptController(
            FoggyRuntimeApiProperties runtimeApiProperties,
            RuntimeFsscriptCteBridge cteBridge
    ) {
        this.runtimeApiProperties = runtimeApiProperties;
        this.cteBridge = cteBridge;
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
            evaluator.setVar("foggy", cteBridge.foggyHost(request, namespace, authorization, headers));

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

    private RuntimeEnvelope<FsscriptResponse> fail(
            String code,
            String phase,
            String message,
            String field,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        return RuntimeEnvelope.fail(
                ENGINE,
                runtimeApiProperties.getRuntimeApiVersion(),
                code,
                phase,
                message,
                null,
                field,
                null,
                suggestedNextAction,
                safeToAutoRepair
        );
    }

    private RuntimeEnvelope<FsscriptResponse> fail(RuntimeComposeException e) {
        return RuntimeEnvelope.fail(
                ENGINE,
                runtimeApiProperties.getRuntimeApiVersion(),
                e.toRuntimeError(),
                e.diagnostics());
    }

}
