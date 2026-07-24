package com.foggyframework.dataset.model.api.backend;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Stable atomic refresh request.
 *
 * <p>An empty model-name set refreshes the namespace catalog. A non-empty set
 * refreshes those query-model identities and their governed dependencies.</p>
 */
public record AtomicRefreshRequest(String namespace, Set<String> modelNames) {

    public AtomicRefreshRequest {
        namespace = namespace == null || namespace.isBlank() ? "" : namespace.trim();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (modelNames != null) {
            modelNames.forEach(name -> {
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("modelNames must not contain blanks");
                }
                normalized.add(name.trim());
            });
        }
        modelNames = Set.copyOf(normalized);
    }

    public static AtomicRefreshRequest namespace(String namespace) {
        return new AtomicRefreshRequest(namespace, Set.of());
    }

    public static AtomicRefreshRequest models(String namespace, Set<String> modelNames) {
        return new AtomicRefreshRequest(namespace, modelNames);
    }
}
