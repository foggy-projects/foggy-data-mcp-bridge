package com.foggyframework.analytics.runtime.core.function;

import com.foggyframework.analytics.definition.api.AnalyticsColumnSchema;
import com.foggyframework.analytics.definition.api.AnalyticsRenderModel;
import com.foggyframework.analytics.definition.api.AnalyticsVisualIntent;
import com.foggyframework.analytics.definition.api.AnalyticsWidgetData;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;

import java.util.Locale;
import java.util.Map;

/** Stable projection from Definition API render types to the Function contract. */
public final class AnalyticsRenderResultMapper {

    private AnalyticsRenderResultMapper() {
    }

    public static AnalyticsRenderResult from(AnalyticsRenderModel model) {
        return new AnalyticsRenderResult(
                new AnalyticsRenderResult.Artifact(
                        model.artifactRef().kind().name().toLowerCase(Locale.ROOT),
                        model.artifactRef().value()),
                model.resolvedBundleRevision().value(),
                model.state().name().toLowerCase(Locale.ROOT),
                model.widgets().stream().map(AnalyticsRenderResultMapper::widget).toList(),
                model.diagnostics());
    }

    private static AnalyticsRenderResult.Widget widget(AnalyticsWidgetData widget) {
        return new AnalyticsRenderResult.Widget(
                widget.widgetRef(),
                visual(widget.visualIntent()),
                widget.state().name().toLowerCase(Locale.ROOT),
                widget.columns().stream().map(AnalyticsRenderResultMapper::column).toList(),
                widget.rows(),
                widget.truncated(),
                widget.diagnostics());
    }

    private static AnalyticsRenderResult.Visual visual(AnalyticsVisualIntent intent) {
        return new AnalyticsRenderResult.Visual(
                intent.kind().name().toLowerCase(Locale.ROOT),
                Map.copyOf(intent.hints()));
    }

    private static AnalyticsRenderResult.Column column(AnalyticsColumnSchema column) {
        return new AnalyticsRenderResult.Column(
                column.name(),
                column.type(),
                column.nullable());
    }
}
