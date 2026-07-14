package com.foggyframework.dataset.db.model.lifecycle.identity;

import java.util.Objects;

/**
 * Cache-safe logical datasource identity.  None of its components may contain
 * a JDBC URL, user name, credential or physical connection target.
 */
public record DatasourceBindingIdentity(
        String bindingKey,
        String backendId,
        DatasourceBindingGeneration generation
) implements Comparable<DatasourceBindingIdentity> {

    public DatasourceBindingIdentity {
        bindingKey = requireLogical(bindingKey, "binding key");
        backendId = requireLogical(backendId, "backend id");
        Objects.requireNonNull(generation, "generation");
    }

    private static String requireLogical(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public int compareTo(DatasourceBindingIdentity other) {
        int byKey = bindingKey.compareTo(other.bindingKey);
        if (byKey != 0) {
            return byKey;
        }
        int byBackend = backendId.compareTo(other.backendId);
        return byBackend != 0 ? byBackend : generation.value().compareTo(other.generation.value());
    }
}
