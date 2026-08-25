package com.foggyframework.analytics.function.contract;

/** LLM-oriented description of the model resolved for this invocation. */
public record AnalyticsSemanticModelDescription(
        String namespace,
        String modelName,
        String format,
        String content) {

    public AnalyticsSemanticModelDescription {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelName = AnalyticsFunctionValues.requireText("modelName", modelName);
        format = AnalyticsFunctionValues.requireText("format", format);
        content = AnalyticsFunctionValues.requireText("content", content);
        if (content.length() > 1_048_576) {
            throw new IllegalArgumentException("content exceeds the safe size limit");
        }
    }
}
