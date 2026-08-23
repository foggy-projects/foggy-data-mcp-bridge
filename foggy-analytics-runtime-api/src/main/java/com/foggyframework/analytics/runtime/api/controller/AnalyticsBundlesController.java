package com.foggyframework.analytics.runtime.api.controller;

import com.foggyframework.analytics.function.contract.AnalyticsArtifactDescription;
import com.foggyframework.analytics.function.contract.AnalyticsArtifactFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleList;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.runtime.api.AnalyticsRuntimeApiRoutes;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsArtifactDescriptionRequest;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsBundleValidationRequest;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiResponseFactory;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeHttpResponseMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
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

    private final AnalyticsFunctionEndpoint endpoint;
    private final AnalyticsRuntimeApiResponseFactory responses;
    private final AnalyticsRuntimeHttpResponseMapper http;

    public AnalyticsBundlesController(
            AnalyticsFunctionEndpoint endpoint,
            AnalyticsRuntimeApiResponseFactory responses,
            AnalyticsRuntimeHttpResponseMapper http) {
        this.endpoint = endpoint;
        this.responses = responses;
        this.http = http;
    }

    @GetMapping
    public ResponseEntity<AnalyticsFunctionEnvelope<AnalyticsBundleList>> list(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return http.map(endpoint.listBundles(
                responses.requestContext(requestId, traceId)));
    }

    @PostMapping("/{bundleRef}/validate")
    public ResponseEntity<AnalyticsFunctionEnvelope<AnalyticsBundleDescription>> validate(
            @PathVariable String bundleRef,
            @RequestBody(required = false) AnalyticsBundleValidationRequest request) {
        return http.map(endpoint.validateBundle(bundleRequest(bundleRef, request)));
    }

    @PostMapping("/{bundleRef}/describe")
    public ResponseEntity<AnalyticsFunctionEnvelope<AnalyticsBundleDescription>> describe(
            @PathVariable String bundleRef,
            @RequestBody(required = false) AnalyticsBundleValidationRequest request) {
        return http.map(endpoint.describeBundle(bundleRequest(bundleRef, request)));
    }

    @PostMapping("/{bundleRef}/artifacts/{artifactKind}/{artifactRef}/describe")
    public ResponseEntity<AnalyticsFunctionEnvelope<AnalyticsArtifactDescription>>
            describeArtifact(
                    @PathVariable String bundleRef,
                    @PathVariable String artifactKind,
                    @PathVariable String artifactRef,
                    @RequestBody AnalyticsArtifactDescriptionRequest request) {
        return http.map(endpoint.describeArtifact(new AnalyticsArtifactFunctionRequest(
                bundleRef,
                artifactKind,
                artifactRef,
                request == null ? null : request.expectedBundleRevision(),
                responses.requestContext(
                        request == null ? null : request.requestId(),
                        request == null ? null : request.traceId()))));
    }

    private AnalyticsBundleFunctionRequest bundleRequest(
            String bundleRef,
            AnalyticsBundleValidationRequest request) {
        return new AnalyticsBundleFunctionRequest(
                bundleRef,
                request == null ? null : request.expectedBundleRevision(),
                responses.requestContext(
                        request == null ? null : request.requestId(),
                        request == null ? null : request.traceId()));
    }
}
