package com.foggyframework.analytics.definition.api;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsDefinitionStructureTest {

    @Test
    void reportReferencesQueryWithoutDuplicatingQueryBody() {
        AnalyticsQuerySpec query = new AnalyticsQuerySpec(
                new AnalyticsQueryRef("sales-by-region"),
                new AnalyticsNamespaceRef("default"),
                "SalesOrder",
                List.of("region", "amount"),
                List.of("region"));
        AnalyticsReportDefinition report = new AnalyticsReportDefinition(
                new AnalyticsArtifactRef(AnalyticsArtifactKind.REPORT, "sales-report"),
                query.queryRef(),
                new AnalyticsVisualIntent(AnalyticsVisualKind.TABLE, Map.of()));

        assertEquals(query.queryRef(), report.queryRef());
        assertEquals(List.of("region", "amount"), query.columns());
        assertThrows(UnsupportedOperationException.class,
                () -> query.columns().add("customer"));
    }

    @Test
    void dashboardWidgetHasOneReferenceTruthSource() {
        AnalyticsVisualIntent chart = new AnalyticsVisualIntent(
                AnalyticsVisualKind.CHART,
                Map.of("shape", "bar"));
        AnalyticsDashboardWidget widget = new AnalyticsDashboardWidget(
                "sales-widget",
                new AnalyticsArtifactRef(AnalyticsArtifactKind.REPORT, "sales-report"),
                null,
                chart);
        AnalyticsDashboardDefinition dashboard = new AnalyticsDashboardDefinition(
                new AnalyticsArtifactRef(AnalyticsArtifactKind.DASHBOARD, "sales-dashboard"),
                List.of(widget));

        assertEquals(1, dashboard.widgets().size());
        assertThrows(IllegalArgumentException.class, () -> new AnalyticsDashboardWidget(
                "../sales-widget", widget.reportRef(), null, chart));
        assertThrows(IllegalArgumentException.class, () -> new AnalyticsDashboardWidget(
                "invalid", widget.reportRef(), new AnalyticsQueryRef("query"), chart));
    }

    @Test
    void renderModelKeepsRendererNeutralProjection() {
        Map<String, Object> nullableRow = new LinkedHashMap<>();
        nullableRow.put("amount", null);
        AnalyticsVisualIntent visualIntent = new AnalyticsVisualIntent(
                AnalyticsVisualKind.TABLE,
                Map.of());
        AnalyticsWidgetData widgetData = new AnalyticsWidgetData(
                "sales-widget",
                visualIntent,
                AnalyticsRenderState.READY,
                List.of(new AnalyticsColumnSchema("amount", "decimal", false)),
                List.of(nullableRow),
                false,
                List.of());
        AnalyticsRenderModel model = new AnalyticsRenderModel(
                new AnalyticsArtifactRef(AnalyticsArtifactKind.DASHBOARD, "sales-dashboard"),
                AnalyticsBundleRevision.fromSha256Hex("a".repeat(64)),
                AnalyticsRenderState.READY,
                List.of(widgetData),
                List.of());

        assertEquals(AnalyticsRenderState.READY, model.state());
        assertEquals(visualIntent, model.widgets().get(0).visualIntent());
        assertNull(model.widgets().get(0).rows().get(0).get("amount"));
        assertTrue(model.diagnostics().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> model.widgets().add(widgetData));
        assertThrows(IllegalArgumentException.class, () -> new AnalyticsWidgetData(
                "sales/widget",
                visualIntent,
                AnalyticsRenderState.READY,
                List.of(),
                List.of(),
                false,
                List.of()));
    }
}
