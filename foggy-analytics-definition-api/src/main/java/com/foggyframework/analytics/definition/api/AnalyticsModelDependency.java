package com.foggyframework.analytics.definition.api;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable persisted identity of a TM/QM model dependency used by an Analytics Bundle.
 *
 * <p>The revision identifies stable model content. Adapter-owned runtime catalog
 * identities must never be persisted here.</p>
 */
public record AnalyticsModelDependency(
        AnalyticsNamespaceRef namespace,
        String modelKind,
        String modelName,
        AnalyticsModelRevision modelRevision) {

    private static final Set<String> SUPPORTED_MODEL_KINDS = Set.of("tm", "qm");

    public AnalyticsModelDependency {
        namespace = Objects.requireNonNull(namespace, "namespace");
        modelKind = requireValue("modelKind", modelKind);
        if (!SUPPORTED_MODEL_KINDS.contains(modelKind)) {
            throw new IllegalArgumentException("modelKind must be 'tm' or 'qm'");
        }
        modelName = requireValue("modelName", modelName);
        modelRevision = Objects.requireNonNull(modelRevision, "modelRevision");
    }

    private static String requireValue(String field, String value) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}
