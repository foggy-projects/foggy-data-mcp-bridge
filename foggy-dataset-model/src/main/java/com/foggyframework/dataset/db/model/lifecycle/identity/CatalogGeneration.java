package com.foggyframework.dataset.db.model.lifecycle.identity;

/** Opaque, non-reusable identity of one committed namespace catalog view. */
public record CatalogGeneration(String value) {
    public CatalogGeneration {
        value = requireOpaque(value, "catalog generation");
    }

    private static String requireOpaque(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
