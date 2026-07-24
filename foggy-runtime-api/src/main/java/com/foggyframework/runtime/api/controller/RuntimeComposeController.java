package com.foggyframework.runtime.api.controller;

import com.foggyframework.dataset.model.semantic.port.ComposeOperation;
import com.foggyframework.runtime.api.RuntimeApiRoutes;
import com.foggyframework.runtime.api.dto.ComposeRequest;
import com.foggyframework.runtime.api.dto.ComposeResponse;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
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

import java.util.Map;

@RestController
@RequestMapping(RuntimeApiRoutes.API_V1)
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeComposeController {

    private final RuntimeApiResponseFactory responses;
    private final RuntimeComposeRunner composeRunner;

    public RuntimeComposeController(
            RuntimeApiResponseFactory responses,
            RuntimeComposeRunner composeRunner
    ) {
        this.responses = responses;
        this.composeRunner = composeRunner;
    }

    @PostMapping(RuntimeApiRoutes.V1.COMPOSE_VALIDATE)
    public RuntimeEnvelope<ComposeResponse> validate(
            @RequestBody(required = false) ComposeRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader Map<String, String> headers
    ) {
        return run(ComposeOperation.VALIDATE, "compose.validate",
                request, authorization, namespace, headers);
    }

    @PostMapping(RuntimeApiRoutes.V1.COMPOSE_PREVIEW)
    public RuntimeEnvelope<ComposeResponse> preview(
            @RequestBody(required = false) ComposeRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader Map<String, String> headers
    ) {
        return run(ComposeOperation.PREVIEW, "compose.preview",
                request, authorization, namespace, headers);
    }

    @PostMapping(RuntimeApiRoutes.V1.COMPOSE_EXECUTE)
    public RuntimeEnvelope<ComposeResponse> execute(
            @RequestBody(required = false) ComposeRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader Map<String, String> headers
    ) {
        return run(ComposeOperation.EXECUTE, "compose.execute",
                request, authorization, namespace, headers);
    }

    private RuntimeEnvelope<ComposeResponse> run(
            ComposeOperation operation,
            String phase,
            ComposeRequest request,
            String authorization,
            String namespace,
            Map<String, String> headers
    ) {
        try {
            RuntimeComposeRunResult result = composeRunner.run(operation, phase,
                    RuntimeComposeInvocation.fromComposeRequest(request, namespace, authorization, headers));
            return responses.ok(result.response(), result.diagnostics());
        } catch (RuntimeComposeException e) {
            return fail(e);
        }
    }

    private RuntimeEnvelope<ComposeResponse> fail(RuntimeComposeException e) {
        return responses.fail(e.toRuntimeError(), e.diagnostics());
    }

}
