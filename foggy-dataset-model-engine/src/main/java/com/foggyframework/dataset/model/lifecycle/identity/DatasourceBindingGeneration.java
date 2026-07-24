package com.foggyframework.dataset.model.lifecycle.identity;

/** Opaque generation of one logical datasource/backend binding. */
public record DatasourceBindingGeneration(String value) {
    public DatasourceBindingGeneration {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("datasource binding generation must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
