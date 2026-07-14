package com.foggyframework.runtime.api.controller;

import com.foggyframework.runtime.api.RuntimeApiRoutes;
import com.foggyframework.runtime.api.dto.ModelDescribeRequest;
import com.foggyframework.runtime.api.dto.ModelDescribeResponse;
import com.foggyframework.runtime.api.dto.ModelRefreshRequest;
import com.foggyframework.runtime.api.dto.ModelRefreshResponse;
import com.foggyframework.runtime.api.dto.ModelValidateRequest;
import com.foggyframework.runtime.api.dto.ModelValidateResponse;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeModelOperationException;
import com.foggyframework.runtime.api.service.RuntimeModelOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(RuntimeApiRoutes.API_V1)
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeModelsController {

    private final RuntimeApiResponseFactory responses;
    private final RuntimeModelOperations modelOperations;

    public RuntimeModelsController(
            RuntimeApiResponseFactory responses,
            RuntimeModelOperations modelOperations
    ) {
        this.responses = responses;
        this.modelOperations = modelOperations;
    }

    @GetMapping(RuntimeApiRoutes.V1.MODELS)
    public RuntimeEnvelope<Map<String, Object>> listModels(
            @RequestParam Map<String, String> query,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        return responses.ok(modelOperations.listModels(query, namespace));
    }

    @PostMapping(RuntimeApiRoutes.V1.MODEL_DESCRIBE)
    public RuntimeEnvelope<ModelDescribeResponse> describeModel(
            @PathVariable String model,
            @RequestBody(required = false) ModelDescribeRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        try {
            return responses.ok(modelOperations.describeModel(model, request, namespace));
        } catch (RuntimeModelOperationException e) {
            return fail(e);
        }
    }

    @PostMapping(RuntimeApiRoutes.V1.MODELS_VALIDATE)
    public RuntimeEnvelope<ModelValidateResponse> validateModels(
            @RequestBody(required = false) ModelValidateRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        try {
            return responses.ok(modelOperations.validateModels(request, namespace));
        } catch (RuntimeModelOperationException e) {
            return fail(e);
        }
    }

    @PostMapping(RuntimeApiRoutes.V1.MODELS_REFRESH)
    public RuntimeEnvelope<ModelRefreshResponse> refreshModels(
            @RequestBody(required = false) ModelRefreshRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        try {
            return responses.ok(modelOperations.refreshModels(request, namespace));
        } catch (RuntimeModelOperationException e) {
            return fail(e);
        }
    }

    private <T> RuntimeEnvelope<T> fail(RuntimeModelOperationException e) {
        return responses.fail(
                e.code(),
                e.phase(),
                e.getMessage(),
                e.model(),
                null,
                null,
                e.suggestedNextAction(),
                e.safeToAutoRepair(),
                e.diagnostics(),
                e.lifecycleCode(),
                e.lifecycle()
        );
    }
}
