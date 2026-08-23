package com.foggyframework.analytics.runtime.core.function;

import com.foggyframework.analytics.definition.api.AnalyticsRenderModel;
import com.foggyframework.analytics.runtime.core.render.AnalyticsDashboardRenderRequest;
import com.foggyframework.analytics.runtime.core.render.AnalyticsReportPreviewRequest;

/** Optional render lane supplied by the concrete engine authority adapter. */
public interface AnalyticsFunctionRenderOperations {

    AnalyticsRenderModel previewReport(AnalyticsReportPreviewRequest request);

    AnalyticsRenderModel previewDashboard(AnalyticsDashboardRenderRequest request);

    AnalyticsRenderModel renderDashboard(AnalyticsDashboardRenderRequest request);
}
