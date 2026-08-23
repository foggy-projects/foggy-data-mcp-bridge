package com.foggyframework.analytics.runtime.core.function;

import com.foggyframework.analytics.definition.api.AnalyticsRenderModel;
import com.foggyframework.analytics.runtime.core.render.AnalyticsDashboardRenderRequest;
import com.foggyframework.analytics.runtime.core.render.AnalyticsRenderService;
import com.foggyframework.analytics.runtime.core.render.AnalyticsReportPreviewRequest;

import java.util.Objects;

/** Thin facade that keeps {@link AnalyticsRenderService} as the sole orchestrator. */
public final class CoreAnalyticsFunctionRenderOperations<A>
        implements AnalyticsFunctionRenderOperations {

    private final AnalyticsRenderService<A> renderService;

    public CoreAnalyticsFunctionRenderOperations(AnalyticsRenderService<A> renderService) {
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
