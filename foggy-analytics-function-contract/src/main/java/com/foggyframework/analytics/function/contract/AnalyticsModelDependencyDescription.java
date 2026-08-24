package com.foggyframework.analytics.function.contract;

import java.util.Set;
import java.util.regex.Pattern;

/** Stable model dependency fields that may be persisted in an Analytics manifest. */
public record AnalyticsModelDependencyDescription(
        String namespace,
        String modelKind,
        String modelName,
        String modelRevision) {

    private static final Set<String> MODEL_KINDS = Set.of("tm", "qm");
    private static final Pattern SHA256 = Pattern.compile("sha256:[0-9a-f]{64}");

    public AnalyticsModelDependencyDescription {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelKind = AnalyticsFunctionValues.requireText("modelKind", modelKind);
        if (!MODEL_KINDS.contains(modelKind)) {
            throw new IllegalArgumentException("modelKind must be tm or qm");
        }
        modelName = AnalyticsFunctionValues.requireText("modelName", modelName);
        modelRevision = AnalyticsFunctionValues.requireText(
                "modelRevision", modelRevision);
        if (!SHA256.matcher(modelRevision).matches()) {
            throw new IllegalArgumentException(
                    "modelRevision must use sha256:<64 lowercase hex characters>");
        }
    }
}
