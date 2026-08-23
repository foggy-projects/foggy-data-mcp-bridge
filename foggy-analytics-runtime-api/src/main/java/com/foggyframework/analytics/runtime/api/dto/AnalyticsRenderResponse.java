package com.foggyframework.analytics.runtime.api.dto;

import com.foggyframework.analytics.definition.api.AnalyticsColumnSchema;
import com.foggyframework.analytics.definition.api.AnalyticsRenderModel;
import com.foggyframework.analytics.definition.api.AnalyticsVisualIntent;
import com.foggyframework.analytics.definition.api.AnalyticsWidgetData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;

/** Stable wire projection that does not expose engine or adapter implementation types. */
public record AnalyticsRenderResponse(
        Artifact artifact,
        String resolvedBundleRevision,
        String state,
        List<Widget> widgets,
        List<String> diagnostics) {

    public AnalyticsRenderResponse {
        widgets = List.copyOf(widgets);
        diagnostics = List.copyOf(diagnostics);
    }

    public static AnalyticsRenderResponse from(AnalyticsRenderModel model) {
        return new AnalyticsRenderResponse(
                new Artifact(
                        model.artifactRef().kind().name().toLowerCase(Locale.ROOT),
                        model.artifactRef().value()),
                model.resolvedBundleRevision().value(),
                model.state().name().toLowerCase(Locale.ROOT),
                model.widgets().stream().map(Widget::from).toList(),
                model.diagnostics());
    }

    public record Artifact(String kind, String ref) {
    }

    public record Visual(String kind, Map<String, String> hints) {

        static Visual from(AnalyticsVisualIntent intent) {
            return new Visual(
                    intent.kind().name().toLowerCase(Locale.ROOT),
                    Map.copyOf(intent.hints()));
        }
    }

    public record Column(String name, String type, boolean nullable) {

        static Column from(AnalyticsColumnSchema column) {
            return new Column(column.name(), column.type(), column.nullable());
        }
    }

    public record Widget(
            String widgetRef,
            Visual visual,
            String state,
            List<Column> columns,
            List<Map<String, Object>> rows,
            boolean truncated,
            List<String> diagnostics) {

        public Widget {
            columns = List.copyOf(columns);
            rows = rows.stream().map(Widget::copyRow).toList();
            diagnostics = List.copyOf(diagnostics);
        }

        static Widget from(AnalyticsWidgetData widget) {
            return new Widget(
                    widget.widgetRef(),
                    Visual.from(widget.visualIntent()),
                    widget.state().name().toLowerCase(Locale.ROOT),
                    widget.columns().stream().map(Column::from).toList(),
                    widget.rows(),
                    widget.truncated(),
                    widget.diagnostics());
        }

        private static Map<String, Object> copyRow(Map<String, Object> source) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }
    }
}
