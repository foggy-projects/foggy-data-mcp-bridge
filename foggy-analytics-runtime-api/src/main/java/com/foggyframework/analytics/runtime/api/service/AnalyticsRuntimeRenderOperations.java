package com.foggyframework.analytics.runtime.api.service;

import com.foggyframework.analytics.definition.api.AnalyticsRenderModel;
import com.foggyframework.analytics.runtime.core.render.AnalyticsDashboardRenderRequest;
import com.foggyframework.analytics.runtime.core.render.AnalyticsReportPreviewRequest;

/** Optional render lane; present only when the host supplies authority resolution. */
public interface AnalyticsRuntimeRenderOperations {

    AnalyticsRenderModel previewReport(AnalyticsReportPreviewRequest request);

    AnalyticsRenderModel previewDashboard(AnalyticsDashboardRenderRequest request);

    AnalyticsRenderModel renderDashboard(AnalyticsDashboardRenderRequest request);
}
