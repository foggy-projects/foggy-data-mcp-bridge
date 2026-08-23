package com.foggyframework.analytics.runtime.api.controller;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsRenderFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import com.foggyframework.analytics.runtime.api.AnalyticsRuntimeApiRoutes;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsRenderRequest;
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

@RestController
@RequestMapping(AnalyticsRuntimeApiRoutes.API_V1 + AnalyticsRuntimeApiRoutes.V1.BUNDLES)
@ConditionalOnProperty(
        prefix = "foggy.analytics.runtime-api",
        name = "enabled",
        havingValue = "true")
public class AnalyticsRenderController {

    private final AnalyticsFunctionEndpoint endpoint;
    private final AnalyticsRuntimeApiResponseFactory responses;
    private final AnalyticsRuntimeHttpResponseMapper http;

    public AnalyticsRenderController(
            AnalyticsFunctionEndpoint endpoint,
            AnalyticsRuntimeApiResponseFactory responses,
            AnalyticsRuntimeHttpResponseMapper http) {
        this.endpoint = endpoint;
        this.responses = responses;
        this.http = http;
    }

    @PostMapping("/{bundleRef}/reports/{reportRef}/preview")
    public ResponseEntity<AnalyticsFunctionEnvelope<AnalyticsRenderResult>> previewReport(
            @PathVariable String bundleRef,
            @PathVariable String reportRef,
            @Valid @RequestBody AnalyticsRenderRequest request) {
        return http.map(endpoint.previewReport(
                functionRequest(bundleRef, reportRef, request)));
    }

    @PostMapping("/{bundleRef}/dashboards/{dashboardRef}/preview")
    public ResponseEntity<AnalyticsFunctionEnvelope<AnalyticsRenderResult>>
            previewDashboard(
            @PathVariable String bundleRef,
            @PathVariable String dashboardRef,
            @Valid @RequestBody AnalyticsRenderRequest request) {
        return http.map(endpoint.previewDashboard(
                functionRequest(bundleRef, dashboardRef, request)));
    }

    @PostMapping("/{bundleRef}/dashboards/{dashboardRef}/render")
    public ResponseEntity<AnalyticsFunctionEnvelope<AnalyticsRenderResult>>
            renderDashboard(
            @PathVariable String bundleRef,
            @PathVariable String dashboardRef,
            @Valid @RequestBody AnalyticsRenderRequest request) {
        return http.map(endpoint.renderDashboard(
                functionRequest(bundleRef, dashboardRef, request)));
    }

    private AnalyticsRenderFunctionRequest functionRequest(
            String bundleRef,
            String artifactRef,
            AnalyticsRenderRequest request) {
        return new AnalyticsRenderFunctionRequest(
                bundleRef,
                artifactRef,
                request.expectedBundleRevision(),
                request.parameters(),
                request.timezone(),
                request.locale(),
                new AnalyticsFunctionAuthority(
                        request.authority().provider(),
                        request.authority().reference()),
                responses.requestContext(request.requestId(), request.traceId()));
    }
}
