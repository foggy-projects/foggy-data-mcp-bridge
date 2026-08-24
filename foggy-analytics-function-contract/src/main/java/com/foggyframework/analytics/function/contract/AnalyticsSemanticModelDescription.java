package com.foggyframework.analytics.function.contract;

/** LLM-oriented semantic model description pinned to one stable model revision. */
public record AnalyticsSemanticModelDescription(
        String namespace,
        String modelName,
        String modelRevision,
        String format,
        String content) {

    public AnalyticsSemanticModelDescription {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelName = AnalyticsFunctionValues.requireText("modelName", modelName);
        modelRevision = AnalyticsFunctionValues.requireRevision(
                "modelRevision", modelRevision);
        format = AnalyticsFunctionValues.requireText("format", format);
        content = AnalyticsFunctionValues.requireText("content", content);
        if (content.length() > 1_048_576) {
            throw new IllegalArgumentException("content exceeds the safe size limit");
        }
    }
}
