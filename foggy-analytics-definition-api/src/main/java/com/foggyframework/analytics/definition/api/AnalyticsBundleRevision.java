package com.foggyframework.analytics.definition.api;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical immutable content identity of an Analytics Bundle. */
public record AnalyticsBundleRevision(String value) {

    private static final Pattern CANONICAL_SHA256 =
            Pattern.compile("sha256:[0-9a-f]{64}");

    public AnalyticsBundleRevision {
        value = Objects.requireNonNull(value, "bundleRevision")
                .toLowerCase(Locale.ROOT);
        if (!CANONICAL_SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "bundleRevision must use sha256:<64 lowercase hex characters>");
        }
    }

    public static AnalyticsBundleRevision fromSha256Hex(String hex) {
        Objects.requireNonNull(hex, "hex");
        return new AnalyticsBundleRevision("sha256:" + hex);
    }

    public String sha256Hex() {
        return value.substring("sha256:".length());
    }
}
