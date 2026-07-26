package com.foggyframework.dataset.model.semantic.permission;

import java.time.Instant;
import java.util.Objects;

/**
 * Engine-generated, non-reversible identity of one final effective permission
 * snapshot. Raw authorization values are deliberately excluded.
 */
public record AuthorizationSignature(
        String value,
        boolean publicIdentity,
        Instant expiresAt
) {
    public AuthorizationSignature {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("authorization signature must not be blank");
        }
        value = value.trim();
        if (publicIdentity && expiresAt != null) {
            throw new IllegalArgumentException("PUBLIC authorization signature must not expire");
        }
    }

    public boolean isUsableAt(Instant now) {
        return expiresAt == null || expiresAt.isAfter(Objects.requireNonNull(now, "now"));
    }
}
