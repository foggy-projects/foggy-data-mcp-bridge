package com.foggyframework.analytics.runtime.api.controller;

import com.foggyframework.analytics.function.contract.AnalyticsComposeFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsComposeResult;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.runtime.api.AnalyticsRuntimeApiRoutes;
import com.foggyframework.analytics.runtime.api.AnalyticsRuntimeEndpoint;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsAuthorityRequest;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsComposeHttpRequest;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiResponseFactory;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeHttpResponseMapper;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Independent Analytics Runtime API for restricted SemanticDSL Compose/CTE. */
@RestController
@RequestMapping(AnalyticsRuntimeApiRoutes.API_V1 + AnalyticsRuntimeApiRoutes.V1.COMPOSE)
@ConditionalOnProperty(
        prefix = "foggy.analytics.runtime-api",
        name = "enabled",
        havingValue = "true")
public class AnalyticsComposeController {

    private final AnalyticsFunctionEndpoint endpoint;
    private final AnalyticsRuntimeApiResponseFactory responses;
    private final AnalyticsRuntimeHttpResponseMapper http;

    public AnalyticsComposeController(
            @AnalyticsRuntimeEndpoint AnalyticsFunctionEndpoint endpoint,
            AnalyticsRuntimeApiResponseFactory responses,
            AnalyticsRuntimeHttpResponseMapper http) {
        this.endpoint = endpoint;
        this.responses = responses;
        this.http = http;
    }

    @PostMapping
    public ResponseEntity<AnalyticsFunctionEnvelope<AnalyticsComposeResult>> run(
            @Valid @RequestBody AnalyticsComposeHttpRequest request) {
        return http.map(endpoint.runCompose(new AnalyticsComposeFunctionRequest(
                request.namespace(),
                request.mode(),
                request.script(),
                request.params(),
                authority(request.authority()),
                responses.requestContext(request.requestId(), request.traceId()))));
    }

    private static AnalyticsFunctionAuthority authority(AnalyticsAuthorityRequest request) {
        return new AnalyticsFunctionAuthority(request.provider(), request.reference());
    }
}
