package com.foggyframework.analytics.runtime.api.controller;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyDescription;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyResolutionRequest;
import com.foggyframework.analytics.runtime.api.AnalyticsRuntimeApiRoutes;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsModelDependencyResolutionHttpRequest;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiResponseFactory;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeHttpResponseMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Product-neutral design-time discovery of stable model dependency identities. */
@RestController
@RequestMapping(
        AnalyticsRuntimeApiRoutes.API_V1
                + AnalyticsRuntimeApiRoutes.V1.MODEL_DEPENDENCIES)
@ConditionalOnProperty(
        prefix = "foggy.analytics.runtime-api",
        name = "enabled",
        havingValue = "true")
public class AnalyticsModelDependenciesController {

    private final AnalyticsFunctionEndpoint endpoint;
    private final AnalyticsRuntimeApiResponseFactory responses;
    private final AnalyticsRuntimeHttpResponseMapper http;

    public AnalyticsModelDependenciesController(
            AnalyticsFunctionEndpoint endpoint,
            AnalyticsRuntimeApiResponseFactory responses,
            AnalyticsRuntimeHttpResponseMapper http) {
        this.endpoint = endpoint;
        this.responses = responses;
        this.http = http;
    }

    @PostMapping("/resolve")
    public ResponseEntity<AnalyticsFunctionEnvelope<AnalyticsModelDependencyDescription>>
            resolve(@RequestBody AnalyticsModelDependencyResolutionHttpRequest request) {
        return http.map(endpoint.resolveModelDependency(
                new AnalyticsModelDependencyResolutionRequest(
                        request.namespace(),
                        request.modelKind(),
                        request.modelName(),
                        responses.requestContext(request.requestId(), request.traceId()))));
    }
}
