package com.foggyframework.analytics.function.contract;

import java.util.Set;
import java.util.regex.Pattern;

/** Internal model dependency fingerprint used when authoring an Analytics manifest. */
public record AnalyticsModelDependencyDescription(
        String namespace,
        String modelKind,
        String modelName,
        String dependencyDigest) {

    private static final Set<String> MODEL_KINDS = Set.of("tm", "qm");
    private static final Pattern SHA256 = Pattern.compile("sha256:[0-9a-f]{64}");

    public AnalyticsModelDependencyDescription {
        namespace = AnalyticsFunctionValues.requireText("namespace", namespace);
        modelKind = AnalyticsFunctionValues.requireText("modelKind", modelKind);
        if (!MODEL_KINDS.contains(modelKind)) {
            throw new IllegalArgumentException("modelKind must be tm or qm");
        }
        modelName = AnalyticsFunctionValues.requireText("modelName", modelName);
        dependencyDigest = AnalyticsFunctionValues.requireText(
                "dependencyDigest", dependencyDigest);
        if (!SHA256.matcher(dependencyDigest).matches()) {
            throw new IllegalArgumentException(
                    "dependencyDigest must use sha256:<64 lowercase hex characters>");
        }
    }
}
