package com.foggyframework.analytics.definition.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable in-memory definition snapshot for one exact, validated bundle revision.
 *
 * <p>Binary assets stay in {@link AnalyticsArtifactStore} and are not materialized
 * while building a typed definition index.</p>
 */
public final class AnalyticsDefinitionSnapshot {

    private final ResolvedAnalyticsBundle resolvedBundle;
    private final Map<String, byte[]> definitions;

    public AnalyticsDefinitionSnapshot(
            ResolvedAnalyticsBundle resolvedBundle,
            Map<String, byte[]> definitions) {
        this.resolvedBundle = Objects.requireNonNull(resolvedBundle, "resolvedBundle");
        Objects.requireNonNull(definitions, "definitions");
        Map<String, byte[]> copy = new LinkedHashMap<>();
        definitions.forEach((path, content) -> copy.put(
                requirePath(path),
                Objects.requireNonNull(content, "definition content").clone()));
        this.definitions = Collections.unmodifiableMap(copy);
    }

    public ResolvedAnalyticsBundle resolvedBundle() {
        return resolvedBundle;
    }

    public Set<String> definitionPaths() {
        return definitions.keySet();
    }

    public byte[] readDefinition(String relativePath) {
        byte[] content = definitions.get(Objects.requireNonNull(relativePath, "relativePath"));
        if (content == null) {
            throw new IllegalArgumentException(
                    "Analytics definition is not present in the snapshot: " + relativePath);
        }
        return content.clone();
    }

    private static String requirePath(String path) {
        Objects.requireNonNull(path, "definition path");
        if (path.isBlank() || !path.equals(path.trim())) {
            throw new IllegalArgumentException("definition path must be non-blank and trimmed");
        }
        return path;
    }
}
