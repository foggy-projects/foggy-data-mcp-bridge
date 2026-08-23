package com.foggyframework.analytics.runtime.api.controller;

import com.foggyframework.analytics.runtime.api.AnalyticsRuntimeApiRoutes;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionCapabilities;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiResponseFactory;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeHttpResponseMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AnalyticsRuntimeApiRoutes.API_V1)
@ConditionalOnProperty(
        prefix = "foggy.analytics.runtime-api",
        name = "enabled",
        havingValue = "true")
public class AnalyticsCapabilitiesController {

    private final AnalyticsFunctionEndpoint endpoint;
    private final AnalyticsRuntimeApiResponseFactory responses;
    private final AnalyticsRuntimeHttpResponseMapper http;

    public AnalyticsCapabilitiesController(
            AnalyticsFunctionEndpoint endpoint,
            AnalyticsRuntimeApiResponseFactory responses,
            AnalyticsRuntimeHttpResponseMapper http) {
        this.endpoint = endpoint;
        this.responses = responses;
        this.http = http;
    }

    @GetMapping(AnalyticsRuntimeApiRoutes.V1.CAPABILITIES)
    public ResponseEntity<AnalyticsFunctionEnvelope<AnalyticsFunctionCapabilities>>
            capabilities(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return http.map(endpoint.capabilities(
                responses.requestContext(requestId, traceId)));
    }
}
