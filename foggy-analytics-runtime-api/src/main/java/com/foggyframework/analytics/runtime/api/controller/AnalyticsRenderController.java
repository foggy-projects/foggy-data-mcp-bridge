package com.foggyframework.analytics.runtime.api.controller;

import com.foggyframework.analytics.definition.api.AnalyticsArtifactKind;
import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.api.AnalyticsRenderModel;
import com.foggyframework.analytics.runtime.api.AnalyticsRuntimeApiRoutes;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsRenderRequest;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsRenderResponse;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsRuntimeEnvelope;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiException;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiResponseFactory;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeRenderOperations;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;
import com.foggyframework.analytics.runtime.core.render.AnalyticsDashboardRenderRequest;
import com.foggyframework.analytics.runtime.core.render.AnalyticsRenderRequestContext;
import com.foggyframework.analytics.runtime.core.render.AnalyticsReportPreviewRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;

@RestController
@RequestMapping(AnalyticsRuntimeApiRoutes.API_V1 + AnalyticsRuntimeApiRoutes.V1.BUNDLES)
@ConditionalOnProperty(
        prefix = "foggy.analytics.runtime-api",
        name = "enabled",
        havingValue = "true")
public class AnalyticsRenderController {

    private final ObjectProvider<AnalyticsRuntimeRenderOperations> operationsProvider;
    private final AnalyticsRuntimeApiResponseFactory responses;

    public AnalyticsRenderController(
            ObjectProvider<AnalyticsRuntimeRenderOperations> operationsProvider,
            AnalyticsRuntimeApiResponseFactory responses) {
        this.operationsProvider = operationsProvider;
        this.responses = responses;
    }

    @PostMapping("/{bundleRef}/reports/{reportRef}/preview")
    public AnalyticsRuntimeEnvelope<AnalyticsRenderResponse> previewReport(
            @PathVariable String bundleRef,
            @PathVariable String reportRef,
            @Valid @RequestBody AnalyticsRenderRequest request) {
        AnalyticsRenderModel result = operations().previewReport(
                new AnalyticsReportPreviewRequest(
                        new AnalyticsBundleRef(bundleRef),
                        revision(request),
                        new AnalyticsArtifactRef(AnalyticsArtifactKind.REPORT, reportRef),
                        context(request)));
        return responses.ok(
                AnalyticsRenderResponse.from(result),
                request.requestId(),
                request.traceId());
    }

    @PostMapping("/{bundleRef}/dashboards/{dashboardRef}/preview")
    public AnalyticsRuntimeEnvelope<AnalyticsRenderResponse> previewDashboard(
            @PathVariable String bundleRef,
            @PathVariable String dashboardRef,
            @Valid @RequestBody AnalyticsRenderRequest request) {
        AnalyticsRenderModel result = operations().previewDashboard(
                dashboardRequest(bundleRef, dashboardRef, request));
        return responses.ok(
                AnalyticsRenderResponse.from(result),
                request.requestId(),
                request.traceId());
    }

    @PostMapping("/{bundleRef}/dashboards/{dashboardRef}/render")
    public AnalyticsRuntimeEnvelope<AnalyticsRenderResponse> renderDashboard(
            @PathVariable String bundleRef,
            @PathVariable String dashboardRef,
            @Valid @RequestBody AnalyticsRenderRequest request) {
        AnalyticsRenderModel result = operations().renderDashboard(
                dashboardRequest(bundleRef, dashboardRef, request));
        return responses.ok(
                AnalyticsRenderResponse.from(result),
                request.requestId(),
                request.traceId());
    }

    private AnalyticsDashboardRenderRequest dashboardRequest(
            String bundleRef,
            String dashboardRef,
            AnalyticsRenderRequest request) {
        return new AnalyticsDashboardRenderRequest(
                new AnalyticsBundleRef(bundleRef),
                revision(request),
                new AnalyticsArtifactRef(AnalyticsArtifactKind.DASHBOARD, dashboardRef),
                context(request));
    }

    private static AnalyticsBundleRevision revision(AnalyticsRenderRequest request) {
        return new AnalyticsBundleRevision(request.expectedBundleRevision());
    }

    private static AnalyticsRenderRequestContext context(AnalyticsRenderRequest request) {
        try {
            Locale locale = Locale.forLanguageTag(request.locale());
            if (locale.getLanguage().isBlank()) {
                throw new IllegalArgumentException("locale must be a valid language tag");
            }
            return new AnalyticsRenderRequestContext(
                    request.parameters(),
                    ZoneId.of(request.timezone()),
                    locale,
                    new QueryAuthorityBinding(
                            request.authority().provider(),
                            request.authority().reference()),
                    request.requestId(),
                    request.traceId());
        } catch (DateTimeException invalidTimezone) {
            throw new IllegalArgumentException("timezone must be a valid ZoneId");
        }
    }

    private AnalyticsRuntimeRenderOperations operations() {
        AnalyticsRuntimeRenderOperations operations = operationsProvider.getIfAvailable();
        if (operations == null) {
            throw new AnalyticsRuntimeApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ANALYTICS_RENDER_UNAVAILABLE",
                    "composition",
                    "Analytics preview/render is unavailable in this host composition.",
                    false);
        }
        return operations;
    }
}
