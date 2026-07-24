package com.foggyframework.dataset.model.api.backend;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable, case-sensitive provider identity used for discovery and routing. */
public record BackendId(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9._-]*");

    public BackendId {
        Objects.requireNonNull(value, "backendId must not be null");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "backendId must match " + FORMAT.pattern() + ": " + value);
        }
    }

    public static BackendId of(String value) { return new BackendId(value); }

    @Override
    public String toString() { return value; }
}
