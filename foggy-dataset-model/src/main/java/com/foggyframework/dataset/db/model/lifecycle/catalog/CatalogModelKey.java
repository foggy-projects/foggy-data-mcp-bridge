package com.foggyframework.dataset.db.model.lifecycle.catalog;

import java.util.Objects;

/** Strong key for a model slot inside one catalog snapshot. */
public record CatalogModelKey(
        ModelProvenance.ModelKind kind,
        String canonicalName
) implements Comparable<CatalogModelKey> {

    public CatalogModelKey {
        Objects.requireNonNull(kind, "kind");
        if (canonicalName == null || canonicalName.isBlank()) {
            throw new IllegalArgumentException("canonicalName must not be blank");
        }
        canonicalName = canonicalName.trim();
    }

    public static CatalogModelKey table(String canonicalName) {
        return new CatalogModelKey(ModelProvenance.ModelKind.TABLE, canonicalName);
    }

    public static CatalogModelKey query(String canonicalName) {
        return new CatalogModelKey(ModelProvenance.ModelKind.QUERY, canonicalName);
    }

    public static CatalogModelKey syntheticQuery(String canonicalName) {
        return new CatalogModelKey(ModelProvenance.ModelKind.SYNTHETIC_QUERY, canonicalName);
    }

    @Override
    public int compareTo(CatalogModelKey other) {
        int byKind = kind.compareTo(other.kind);
        return byKind != 0 ? byKind : canonicalName.compareTo(other.canonicalName);
    }
}
