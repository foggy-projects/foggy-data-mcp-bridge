package com.foggyframework.analytics.runtime.api.controller;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelDescription;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryResult;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelResult;
import com.foggyframework.analytics.runtime.api.AnalyticsRuntimeApiRoutes;
import com.foggyframework.analytics.runtime.api.AnalyticsRuntimeEndpoint;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsAuthorityRequest;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsSemanticModelHttpRequest;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsSemanticQueryHttpRequest;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsQueryModelHttpRequest;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiResponseFactory;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeHttpResponseMapper;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Independent Analytics Runtime API for direct governed data questions. */
@RestController
@RequestMapping(
        AnalyticsRuntimeApiRoutes.API_V1
                + AnalyticsRuntimeApiRoutes.V1.SEMANTIC_MODELS)
@ConditionalOnProperty(
        prefix = "foggy.analytics.runtime-api",
        name = "enabled",
        havingValue = "true")
public class AnalyticsSemanticQueryController {

    private final AnalyticsFunctionEndpoint endpoint;
    private final AnalyticsRuntimeApiResponseFactory responses;
    private final AnalyticsRuntimeHttpResponseMapper http;

    public AnalyticsSemanticQueryController(
            @AnalyticsRuntimeEndpoint AnalyticsFunctionEndpoint endpoint,
            AnalyticsRuntimeApiResponseFactory responses,
            AnalyticsRuntimeHttpResponseMapper http) {
        this.endpoint = endpoint;
        this.responses = responses;
        this.http = http;
    }

    @PostMapping("/{modelName}/describe")
    public ResponseEntity<AnalyticsFunctionEnvelope<AnalyticsSemanticModelDescription>>
            describe(
            @PathVariable String modelName,
            @Valid @RequestBody AnalyticsSemanticModelHttpRequest request) {
        return http.map(endpoint.describeSemanticModel(
                new AnalyticsSemanticModelFunctionRequest(
                        request.namespace(),
                        modelName,
                        authority(request.authority()),
                        responses.requestContext(request.requestId(), request.traceId()))));
    }

    @PostMapping("/{modelName}/query")
    public ResponseEntity<AnalyticsFunctionEnvelope<AnalyticsSemanticQueryResult>> execute(
            @PathVariable String modelName,
            @Valid @RequestBody AnalyticsSemanticQueryHttpRequest request) {
        return http.map(endpoint.executeSemanticQuery(
                new AnalyticsSemanticQueryFunctionRequest(
                        request.namespace(),
                        modelName,
                        request.query(),
                        authority(request.authority()),
                        responses.requestContext(request.requestId(), request.traceId()))));
    }

    @PostMapping("/{modelName}/query-model")
    public ResponseEntity<AnalyticsFunctionEnvelope<AnalyticsQueryModelResult>> runQueryModel(
            @PathVariable String modelName,
            @Valid @RequestBody AnalyticsQueryModelHttpRequest request) {
        return http.map(endpoint.runQueryModel(new AnalyticsQueryModelFunctionRequest(
                request.namespace(),
                modelName,
                request.mode(),
                request.payload(),
                authority(request.authority()),
                responses.requestContext(request.requestId(), request.traceId()))));
    }

    private static AnalyticsFunctionAuthority authority(AnalyticsAuthorityRequest request) {
        return new AnalyticsFunctionAuthority(request.provider(), request.reference());
    }
}
