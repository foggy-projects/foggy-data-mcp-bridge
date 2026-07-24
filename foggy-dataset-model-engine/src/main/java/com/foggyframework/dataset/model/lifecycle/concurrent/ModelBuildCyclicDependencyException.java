package com.foggyframework.dataset.model.lifecycle.concurrent;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Stable model-layer failure raised before a build graph waits on itself. */
public final class ModelBuildCyclicDependencyException extends IllegalStateException {

    public static final String CODE = "MODEL_BUILD_DEPENDENCY_CYCLE";

    private final List<ModelBuildKey> cyclePath;

    public ModelBuildCyclicDependencyException(List<ModelBuildKey> cyclePath) {
        super(buildMessage(cyclePath));
        if (cyclePath == null || cyclePath.size() < 2) {
            throw new IllegalArgumentException("cyclePath must contain at least two nodes");
        }
        this.cyclePath = List.copyOf(cyclePath);
    }

    public List<ModelBuildKey> cyclePath() {
        return cyclePath;
    }

    public String code() {
        return CODE;
    }

    private static String buildMessage(List<ModelBuildKey> cyclePath) {
        if (cyclePath == null || cyclePath.size() < 2) {
            return CODE + ": cyclic model build dependency";
        }
        return CODE + ": " + cyclePath.stream()
                .map(Objects::requireNonNull)
                .map(ModelBuildKey::diagnosticLabel)
                .collect(Collectors.joining(" -> "));
    }
}
