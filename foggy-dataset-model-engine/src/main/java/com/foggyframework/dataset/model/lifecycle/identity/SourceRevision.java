package com.foggyframework.dataset.model.lifecycle.identity;

/** Opaque identity of the committed source view used to build a catalog. */
public record SourceRevision(String value) {
    public SourceRevision {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("source revision must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
