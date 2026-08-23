package com.foggyframework.analytics.runtime.api.service;

import com.foggyframework.analytics.definition.api.AnalyticsRenderModel;
import com.foggyframework.analytics.runtime.core.render.AnalyticsDashboardRenderRequest;
import com.foggyframework.analytics.runtime.core.render.AnalyticsRenderService;
import com.foggyframework.analytics.runtime.core.render.AnalyticsReportPreviewRequest;
import com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAuthority;

import java.util.Objects;

/** Thin HTTP composition adapter; Runtime Core remains the sole render orchestrator. */
public final class CoreAnalyticsRuntimeRenderOperations
        implements AnalyticsRuntimeRenderOperations {

    private final AnalyticsRenderService<FoggyAnalyticsAuthority> renderService;

    public CoreAnalyticsRuntimeRenderOperations(
            AnalyticsRenderService<FoggyAnalyticsAuthority> renderService) {
        this.renderService = Objects.requireNonNull(renderService, "renderService");
    }

    @Override
    public AnalyticsRenderModel previewReport(AnalyticsReportPreviewRequest request) {
        return renderService.preview(request);
    }

    @Override
    public AnalyticsRenderModel previewDashboard(AnalyticsDashboardRenderRequest request) {
        return renderService.render(request);
    }

    @Override
    public AnalyticsRenderModel renderDashboard(AnalyticsDashboardRenderRequest request) {
        return renderService.render(request);
    }
}
