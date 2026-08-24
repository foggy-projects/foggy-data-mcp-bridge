package com.foggyframework.analytics.console.security;

import java.util.Objects;
import java.util.Set;

/** Authenticated Console subject plus one opaque Java query-authority binding. */
public record AnalyticsConsoleSubject(
        String subjectRef,
        String displayName,
        Set<AnalyticsConsoleRole> roles,
        String authorityProvider,
        String authorityReference) {

    public AnalyticsConsoleSubject {
        subjectRef = required(subjectRef, "subjectRef");
        displayName = required(displayName, "displayName");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("roles must not be empty");
        }
        authorityProvider = required(authorityProvider, "authorityProvider");
        authorityReference = required(authorityReference, "authorityReference");
    }

    public boolean hasRole(AnalyticsConsoleRole role) {
        return roles.contains(role);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}
