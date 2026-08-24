package com.foggyframework.analytics.console.model;

import java.time.Instant;
import java.util.Objects;

/** Product-owned folder metadata; never persisted in an Analytics Bundle. */
public record AnalyticsConsoleFolder(
        String folderId,
        String name,
        String parentFolderId,
        String ownerSubjectRef,
        Instant createdAt) {

    public AnalyticsConsoleFolder {
        folderId = required(folderId, "folderId");
        name = required(name, "name");
        ownerSubjectRef = required(ownerSubjectRef, "ownerSubjectRef");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}
