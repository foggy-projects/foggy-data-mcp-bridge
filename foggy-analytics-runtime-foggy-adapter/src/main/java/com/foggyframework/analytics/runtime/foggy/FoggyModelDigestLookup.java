package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;

import java.util.Objects;
import java.util.Set;

/** Exact runtime catalog/model key used to calculate an internal content digest. */
public record FoggyModelDigestLookup(
        CatalogIdentity catalogIdentity,
        String modelKind,
        String canonicalModelName) {

    private static final Set<String> SUPPORTED_MODEL_KINDS = Set.of("tm", "qm");

    public FoggyModelDigestLookup {
        catalogIdentity = Objects.requireNonNull(catalogIdentity, "catalogIdentity");
        modelKind = requireValue("modelKind", modelKind);
        if (!SUPPORTED_MODEL_KINDS.contains(modelKind)) {
            throw new IllegalArgumentException("modelKind must be 'tm' or 'qm'");
        }
        canonicalModelName = requireValue("canonicalModelName", canonicalModelName);
    }

    private static String requireValue(String field, String value) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}
