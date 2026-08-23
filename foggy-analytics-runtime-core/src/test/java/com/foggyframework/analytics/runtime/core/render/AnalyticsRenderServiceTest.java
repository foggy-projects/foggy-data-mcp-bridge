package com.foggyframework.analytics.runtime.core.render;

import com.foggyframework.analytics.definition.api.AnalyticsArtifactKind;
import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleDependencyState;
import com.foggyframework.analytics.definition.api.AnalyticsBundleLifecycle;
import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.api.AnalyticsBundleSourceState;
import com.foggyframework.analytics.definition.api.AnalyticsColumnSchema;
import com.foggyframework.analytics.definition.api.AnalyticsDashboardDefinition;
import com.foggyframework.analytics.definition.api.AnalyticsDashboardWidget;
import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.definition.api.AnalyticsQueryRef;
import com.foggyframework.analytics.definition.api.AnalyticsQuerySpec;
import com.foggyframework.analytics.definition.api.AnalyticsRenderModel;
import com.foggyframework.analytics.definition.api.AnalyticsRenderState;
import com.foggyframework.analytics.definition.api.AnalyticsReportDefinition;
import com.foggyframework.analytics.definition.api.AnalyticsSchemaVersion;
import com.foggyframework.analytics.definition.api.AnalyticsVisualIntent;
import com.foggyframework.analytics.definition.api.AnalyticsVisualKind;
import com.foggyframework.analytics.definition.core.AnalyticsBundleIndex;
import com.foggyframework.analytics.definition.core.ResolvedAnalyticsBundle;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;
import com.foggyframework.analytics.runtime.core.query.QueryExecutionContext;
import com.foggyframework.analytics.runtime.core.query.QueryExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsRenderServiceTest {

    private static final AnalyticsBundleRef SALES = new AnalyticsBundleRef("sales");
    private static final AnalyticsBundleRevision REVISION =
            AnalyticsBundleRevision.fromSha256Hex("a".repeat(64));
    private static final AnalyticsArtifactRef REPORT = new AnalyticsArtifactRef(
            AnalyticsArtifactKind.REPORT,
            "sales-report");
    private static final AnalyticsArtifactRef DASHBOARD = new AnalyticsArtifactRef(
            AnalyticsArtifactKind.DASHBOARD,
            "sales-dashboard");
    private static final AnalyticsQueryRef SALES_QUERY = new AnalyticsQueryRef("sales-query");

    @Test
    void reportPreviewPinsRevisionAndDefensivelyBoundsRows() {
        AnalyticsBundleIndex index = index(
                Map.of(SALES_QUERY, query(SALES_QUERY)),
                Map.of(REPORT, report(REPORT, SALES_QUERY)),
                Map.of());
        AtomicReference<QueryExecutionContext<String>> execution = new AtomicReference<>();
        AnalyticsRenderService<String> service = new AnalyticsRenderService<>(
                (bundleRef, expectedRevision) -> {
                    assertEquals(SALES, bundleRef);
                    assertEquals(REVISION, expectedRevision);
                    return index;
                },
                request -> request.binding().reference(),
                context -> {
                    execution.set(context);
                    return result(List.of(row(1), row(2), row(3)));
                },
                2);

        AnalyticsRenderModel preview = service.preview(new AnalyticsReportPreviewRequest(
                SALES,
                REVISION,
                REPORT,
                requestContext()));

        assertEquals(AnalyticsRenderState.READY, preview.state());
        assertEquals(2, preview.widgets().get(0).rows().size());
        assertTrue(preview.widgets().get(0).truncated());
        assertEquals(2, execution.get().rowLimit());
        assertEquals(
                index.manifest().modelDependencies().get(0).modelRevision(),
                execution.get().modelDependency().modelRevision());
    }

    @Test
    void dashboardExecutesSharedQueryOnlyOnce() {
        AnalyticsDashboardDefinition dashboard = new AnalyticsDashboardDefinition(
                DASHBOARD,
                List.of(
                        new AnalyticsDashboardWidget(
                                "report-widget",
                                REPORT,
                                null,
                                visual(AnalyticsVisualKind.TABLE)),
                        new AnalyticsDashboardWidget(
                                "query-widget",
                                null,
                                SALES_QUERY,
                                visual(AnalyticsVisualKind.CHART))));
        AnalyticsBundleIndex index = index(
                Map.of(SALES_QUERY, query(SALES_QUERY)),
                Map.of(REPORT, report(REPORT, SALES_QUERY)),
                Map.of(DASHBOARD, dashboard));
        AtomicInteger executions = new AtomicInteger();
        AnalyticsRenderService<String> service = service(index, context -> {
            executions.incrementAndGet();
            return result(List.of(row(1)));
        });

        AnalyticsRenderModel rendered = service.render(new AnalyticsDashboardRenderRequest(
                SALES,
                REVISION,
                DASHBOARD,
                requestContext()));

        assertEquals(AnalyticsRenderState.READY, rendered.state());
        assertEquals(2, rendered.widgets().size());
        assertEquals(1, executions.get());
    }

    @Test
    void dashboardKeepsSuccessfulWidgetsWhenOneQueryFails() {
        AnalyticsQueryRef failingQuery = new AnalyticsQueryRef("failing-query");
        AnalyticsDashboardDefinition dashboard = new AnalyticsDashboardDefinition(
                DASHBOARD,
                List.of(
                        new AnalyticsDashboardWidget(
                                "ready-widget",
                                null,
                                SALES_QUERY,
                                visual(AnalyticsVisualKind.TABLE)),
                        new AnalyticsDashboardWidget(
                                "failed-widget",
                                null,
                                failingQuery,
                                visual(AnalyticsVisualKind.CHART))));
        AnalyticsBundleIndex index = index(
                Map.of(
                        SALES_QUERY, query(SALES_QUERY),
                        failingQuery, query(failingQuery)),
                Map.of(),
                Map.of(DASHBOARD, dashboard));
        AtomicInteger executions = new AtomicInteger();
        AnalyticsRenderService<String> service = service(index, context -> {
            executions.incrementAndGet();
            if (context.querySpec().queryRef().equals(failingQuery)) {
                throw new IllegalStateException("adapter details must not escape");
            }
            return result(List.of(row(1)));
        });

        AnalyticsRenderModel rendered = service.render(new AnalyticsDashboardRenderRequest(
                SALES,
                REVISION,
                DASHBOARD,
                requestContext()));

        assertEquals(AnalyticsRenderState.PARTIAL, rendered.state());
        assertEquals(AnalyticsRenderState.READY, rendered.widgets().get(0).state());
        assertEquals(AnalyticsRenderState.ERROR, rendered.widgets().get(1).state());
        assertEquals(List.of("failed-widget:QUERY_EXECUTION_FAILED"), rendered.diagnostics());
        assertEquals(2, executions.get());
    }

    private static AnalyticsRenderService<String> service(
            AnalyticsBundleIndex index,
            com.foggyframework.analytics.runtime.core.query.QueryExecutor<String> executor) {
        return new AnalyticsRenderService<>(
                (bundleRef, expectedRevision) -> index,
                request -> request.binding().reference(),
                executor,
                100);
    }

    private static AnalyticsBundleIndex index(
            Map<AnalyticsQueryRef, AnalyticsQuerySpec> queries,
            Map<AnalyticsArtifactRef, AnalyticsReportDefinition> reports,
            Map<AnalyticsArtifactRef, AnalyticsDashboardDefinition> dashboards) {
        AnalyticsBundleManifest manifest = new AnalyticsBundleManifest(
                AnalyticsBundleManifest.ANALYTICS_KIND,
                AnalyticsSchemaVersion.V1,
                SALES,
                REVISION,
                new AnalyticsNamespaceRef("default"),
                List.of(new AnalyticsModelDependency(
                        new AnalyticsNamespaceRef("default"),
                        "qm",
                        "SalesOrder",
                        AnalyticsModelRevision.fromSha256Hex("b".repeat(64)))));
        return new AnalyticsBundleIndex(
                new ResolvedAnalyticsBundle(
                        manifest,
                        new AnalyticsBundleLifecycle(
                                AnalyticsBundleSourceState.CONFIGURED,
                                AnalyticsBundleDependencyState.CURRENT)),
                queries,
                reports,
                dashboards);
    }

    private static AnalyticsQuerySpec query(AnalyticsQueryRef queryRef) {
        return new AnalyticsQuerySpec(
                queryRef,
                new AnalyticsNamespaceRef("default"),
                "SalesOrder",
                List.of("amount"),
                List.of());
    }

    private static AnalyticsReportDefinition report(
            AnalyticsArtifactRef reportRef,
            AnalyticsQueryRef queryRef) {
        return new AnalyticsReportDefinition(
                reportRef,
                queryRef,
                visual(AnalyticsVisualKind.TABLE));
    }

    private static AnalyticsVisualIntent visual(AnalyticsVisualKind kind) {
        return new AnalyticsVisualIntent(kind, Map.of());
    }

    private static AnalyticsRenderRequestContext requestContext() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("optional-region", null);
        return new AnalyticsRenderRequestContext(
                parameters,
                ZoneId.of("Asia/Shanghai"),
                Locale.SIMPLIFIED_CHINESE,
                new QueryAuthorityBinding("host", "authority-42"),
                "request-1",
                "trace-1");
    }

    private static QueryExecutionResult result(List<Map<String, Object>> rows) {
        return new QueryExecutionResult(
                List.of(new AnalyticsColumnSchema("amount", "decimal", false)),
                rows,
                false,
                List.of());
    }

    private static Map<String, Object> row(int amount) {
        return Map.of("amount", amount);
    }
}
