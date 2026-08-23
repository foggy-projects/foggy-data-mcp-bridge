package com.foggyframework.analytics.runtime.core.query;

import java.util.Objects;

/** Opaque caller authority reference that must be validated by an adapter resolver. */
public record QueryAuthorityBinding(String provider, String reference) {

    public QueryAuthorityBinding {
        provider = requireValue("provider", provider);
        reference = requireValue("reference", reference);
    }

    private static String requireValue(String field, String value) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}
