package com.foggyframework.analytics.definition.api;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Internal content digest of a semantic model and its governed dependency closure.
 *
 * <p>This value may be persisted inside an Analytics Bundle for audit and stale
 * detection. It is not a caller-selectable model version and must not participate
 * in live model selection.</p>
 */
public record AnalyticsModelDigest(String value) {

    private static final Pattern CANONICAL_SHA256 =
            Pattern.compile("sha256:[0-9a-f]{64}");

    public AnalyticsModelDigest {
        value = Objects.requireNonNull(value, "modelDigest")
                .toLowerCase(Locale.ROOT);
        if (!CANONICAL_SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "modelDigest must use sha256:<64 lowercase hex characters>");
        }
    }

    public static AnalyticsModelDigest fromSha256Hex(String hex) {
        Objects.requireNonNull(hex, "hex");
        return new AnalyticsModelDigest("sha256:" + hex);
    }

    public String sha256Hex() {
        return value.substring("sha256:".length());
    }
}
