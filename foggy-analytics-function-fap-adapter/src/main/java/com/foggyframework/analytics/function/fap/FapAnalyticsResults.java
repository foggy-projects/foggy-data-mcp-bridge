package com.foggyframework.analytics.function.fap;

import com.foggyframework.analytics.function.contract.AnalyticsArtifactDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleList;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionCapabilities;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyList;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelDescription;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FapAnalyticsResults {

    private FapAnalyticsResults() {
    }

    static Map<String, Object> result(
            String operation,
            AnalyticsFunctionEnvelope<?> envelope,
            Map<String, Object> data) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("operation", operation);
        value.put("functionContractVersion", envelope.functionContractVersion());
        value.put("analyticsRuntimeApiVersion", envelope.analyticsRuntimeApiVersion());
        value.put("schemaVersion", envelope.schemaVersion());
        value.put("data", data);
        value.put("context", context(envelope.context()));
        return FapAnalyticsValues.object("result", value);
    }

    static Map<String, Object> capabilities(AnalyticsFunctionCapabilities value) {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("maxRows", value.limits().maxRows());
        limits.put("configuredBundles", value.limits().configuredBundles());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("api", value.api());
        result.put("apiVersion", value.apiVersion());
        result.put("schemaVersion", value.schemaVersion());
        result.put("enabled", value.enabled());
        result.put("securityMode", value.securityMode());
        result.put("operations", value.operations());
        result.put("limits", limits);
        result.put("warnings", value.warnings());
        return FapAnalyticsValues.object("capabilities", result);
    }

    static Map<String, Object> bundleList(AnalyticsBundleList value) {
        List<Map<String, Object>> bundles = value.bundles().stream()
                .map(FapAnalyticsResults::bundleDescription)
                .toList();
        return FapAnalyticsValues.object("bundleList", Map.of("bundles", bundles));
    }

    static Map<String, Object> bundleDescription(AnalyticsBundleDescription value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bundleRef", value.bundleRef());
        result.put("bundleRevision", value.bundleRevision());
        result.put("definitionSchemaVersion", value.definitionSchemaVersion());
        result.put("namespaceRef", value.namespaceRef());
        result.put("sourceState", value.sourceState());
        result.put("dependencyState", value.dependencyState());
        result.put("writable", value.writable());
        result.put("valid", value.valid());
        result.put("errorCode", value.errorCode());
        return FapAnalyticsValues.object("bundleDescription", result);
    }

    static Map<String, Object> artifactDescription(AnalyticsArtifactDescription value) {
        return FapAnalyticsValues.object("artifactDescription", Map.of(
                "bundleRef", value.bundleRef(),
                "bundleRevision", value.bundleRevision(),
                "artifactKind", value.artifactKind(),
                "artifactRef", value.artifactRef()));
    }

    static Map<String, Object> render(AnalyticsRenderResult value) {
        Map<String, Object> artifact = Map.of(
                "kind", value.artifact().kind(),
                "ref", value.artifact().ref());
        List<Map<String, Object>> widgets = value.widgets().stream()
                .map(FapAnalyticsResults::widget)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifact", artifact);
        result.put("resolvedBundleRevision", value.resolvedBundleRevision());
        result.put("state", value.state());
        result.put("widgets", widgets);
        result.put("diagnostics", value.diagnostics());
        return FapAnalyticsValues.object("render", result);
    }

    static Map<String, Object> semanticModel(AnalyticsSemanticModelDescription value) {
        return FapAnalyticsValues.object("semanticModel", Map.of(
                "namespace", value.namespace(),
                "modelName", value.modelName(),
                "format", value.format(),
                "content", value.content()));
    }

    static Map<String, Object> modelDependencyList(AnalyticsModelDependencyList value) {
        List<Map<String, Object>> models = value.models().stream()
                .map(model -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("namespace", model.namespace());
                    item.put("modelKind", model.modelKind());
                    item.put("modelName", model.modelName());
                    return item;
                })
                .toList();
        return FapAnalyticsValues.object("modelDependencyList", Map.of(
                "namespace", value.namespace(),
                "modelKind", value.modelKind(),
                "models", models));
    }

    static Map<String, Object> semanticQuery(AnalyticsSemanticQueryResult value) {
        List<Map<String, Object>> columns = value.columns().stream()
                .map(column -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("name", column.name());
                    result.put("type", column.type());
                    result.put("title", column.title());
                    return result;
                })
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("namespace", value.namespace());
        result.put("modelName", value.modelName());
        result.put("columns", columns);
        result.put("rows", value.rows());
        result.put("total", value.total());
        result.put("hasMore", value.hasMore());
        result.put("truncated", value.truncated());
        result.put("warnings", value.warnings());
        return FapAnalyticsValues.object("semanticQuery", result);
    }

    private static Map<String, Object> context(AnalyticsFunctionContext value) {
        return Map.of("requestId", value.requestId(), "traceId", value.traceId());
    }

    private static Map<String, Object> widget(AnalyticsRenderResult.Widget value) {
        Map<String, Object> visual = Map.of(
                "kind", value.visual().kind(),
                "hints", value.visual().hints());
        List<Map<String, Object>> columns = value.columns().stream()
                .map(FapAnalyticsResults::column)
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>(value.rows());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("widgetRef", value.widgetRef());
        result.put("visual", visual);
        result.put("state", value.state());
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("truncated", value.truncated());
        result.put("diagnostics", value.diagnostics());
        return FapAnalyticsValues.object("widget", result);
    }

    private static Map<String, Object> column(AnalyticsRenderResult.Column value) {
        return Map.of(
                "name", value.name(),
                "type", value.type(),
                "nullable", value.nullable());
    }
}
