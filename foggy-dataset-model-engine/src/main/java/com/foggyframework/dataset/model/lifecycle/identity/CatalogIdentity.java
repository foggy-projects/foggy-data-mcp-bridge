package com.foggyframework.dataset.model.lifecycle.identity;

import java.util.Objects;

/** Strong identity of the exact namespace catalog view used by a consumer. */
public record CatalogIdentity(
        String namespace,
        CatalogGeneration generation,
        SourceRevision sourceRevision
) {
    public CatalogIdentity {
        namespace = canonicalNamespace(namespace);
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(sourceRevision, "sourceRevision");
    }

    public static String canonicalNamespace(String namespace) {
        return namespace == null || namespace.isBlank() ? "" : namespace.trim();
    }
}
