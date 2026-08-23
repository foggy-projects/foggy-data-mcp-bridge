package com.foggyframework.analytics.runtime.api.controller;

import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.runtime.api.AnalyticsRuntimeApiRoutes;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsBundleListResponse;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsBundleSummary;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsBundleValidationRequest;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsRuntimeEnvelope;
import com.foggyframework.analytics.runtime.api.service.AnalyticsBundleOperations;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiResponseFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AnalyticsRuntimeApiRoutes.API_V1 + AnalyticsRuntimeApiRoutes.V1.BUNDLES)
@ConditionalOnProperty(
        prefix = "foggy.analytics.runtime-api",
        name = "enabled",
        havingValue = "true")
public class AnalyticsBundlesController {

    private final AnalyticsBundleOperations operations;
    private final AnalyticsRuntimeApiResponseFactory responses;

    public AnalyticsBundlesController(
            AnalyticsBundleOperations operations,
            AnalyticsRuntimeApiResponseFactory responses) {
        this.operations = operations;
        this.responses = responses;
    }

    @GetMapping
    public AnalyticsRuntimeEnvelope<AnalyticsBundleListResponse> list(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return responses.ok(operations.list(), requestId, traceId);
    }

    @PostMapping("/{bundleRef}/validate")
    public AnalyticsRuntimeEnvelope<AnalyticsBundleSummary> validate(
            @PathVariable String bundleRef,
            @RequestBody(required = false) AnalyticsBundleValidationRequest request) {
        String expectedRevision = request == null ? null : request.expectedBundleRevision();
        AnalyticsBundleSummary result = operations.validate(
                new AnalyticsBundleRef(bundleRef),
                expectedRevision);
        return responses.ok(
                result,
                request == null ? null : request.requestId(),
                request == null ? null : request.traceId());
    }
}
