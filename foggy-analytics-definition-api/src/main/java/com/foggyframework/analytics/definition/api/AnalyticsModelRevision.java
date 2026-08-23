package com.foggyframework.analytics.definition.api;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable content identity of a semantic model and its governed dependency closure.
 *
 * <p>This value is safe to persist in an Analytics Bundle. Runtime catalog identities
 * are deliberately excluded because they can change when an engine process restarts.</p>
 */
public record AnalyticsModelRevision(String value) {

    private static final Pattern CANONICAL_SHA256 =
            Pattern.compile("sha256:[0-9a-f]{64}");

    public AnalyticsModelRevision {
        value = Objects.requireNonNull(value, "modelRevision")
                .toLowerCase(Locale.ROOT);
        if (!CANONICAL_SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "modelRevision must use sha256:<64 lowercase hex characters>");
        }
    }

    public static AnalyticsModelRevision fromSha256Hex(String hex) {
        Objects.requireNonNull(hex, "hex");
        return new AnalyticsModelRevision("sha256:" + hex);
    }

    public String sha256Hex() {
        return value.substring("sha256:".length());
    }
}
