package com.foggyframework.analytics.function.contract;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Renderer-neutral wire projection returned by Report and Dashboard functions. */
public record AnalyticsRenderResult(
        Artifact artifact,
        String resolvedBundleRevision,
        String state,
        List<Widget> widgets,
        List<String> diagnostics) {

    public AnalyticsRenderResult {
        artifact = Objects.requireNonNull(artifact, "artifact");
        resolvedBundleRevision = AnalyticsFunctionValues.requireRevision(
                "resolvedBundleRevision", resolvedBundleRevision);
        state = AnalyticsFunctionValues.requireText("state", state);
        widgets = List.copyOf(Objects.requireNonNull(widgets, "widgets"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public record Artifact(String kind, String ref) {

        public Artifact {
            kind = AnalyticsFunctionValues.requireText("artifact.kind", kind);
            ref = AnalyticsFunctionValues.requireLogicalRef("artifact.ref", ref);
        }
    }

    public record Visual(String kind, Map<String, String> hints) {

        public Visual {
            kind = AnalyticsFunctionValues.requireText("visual.kind", kind);
            hints = Map.copyOf(Objects.requireNonNull(hints, "hints"));
        }
    }

    public record Column(String name, String type, boolean nullable) {

        public Column {
            name = AnalyticsFunctionValues.requireText("column.name", name);
            type = AnalyticsFunctionValues.requireText("column.type", type);
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
            widgetRef = AnalyticsFunctionValues.requireLogicalRef(
                    "widgetRef", widgetRef);
            visual = Objects.requireNonNull(visual, "visual");
            state = AnalyticsFunctionValues.requireText("widget.state", state);
            columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
            rows = Objects.requireNonNull(rows, "rows").stream()
                    .map(Widget::copyRow)
                    .toList();
            diagnostics = List.copyOf(Objects.requireNonNull(
                    diagnostics, "diagnostics"));
        }

        private static Map<String, Object> copyRow(Map<String, Object> source) {
            Objects.requireNonNull(source, "row");
            return AnalyticsFunctionJsonValues.normalizeObject("row", source);
        }
    }
}
