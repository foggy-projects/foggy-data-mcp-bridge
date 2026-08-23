package com.foggyframework.analytics.function.http;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Immutable HTTP transport settings with redacted credential diagnostics. */
public final class AnalyticsHttpClientOptions {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final URI baseUrl;
    private final Duration requestTimeout;
    private final String authCode;
    private final String authorization;

    public AnalyticsHttpClientOptions(URI baseUrl) {
        this(baseUrl, DEFAULT_TIMEOUT, null, null);
    }

    public AnalyticsHttpClientOptions(
            URI baseUrl,
            Duration requestTimeout,
            String authCode,
            String authorization) {
        this.baseUrl = validateBaseUrl(baseUrl);
        this.requestTimeout = validateTimeout(requestTimeout);
        this.authCode = optionalSecret("authCode", authCode);
        this.authorization = optionalSecret("authorization", authorization);
        requireProtectedCredentialTransport(
                this.baseUrl,
                this.authCode,
                this.authorization);
    }

    public URI baseUrl() {
        return baseUrl;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public String authCode() {
        return authCode;
    }

    public String authorization() {
        return authorization;
    }

    @Override
    public String toString() {
        return "AnalyticsHttpClientOptions{" +
                "baseUrl=" + baseUrl +
                ", requestTimeout=" + requestTimeout +
                ", authCode=" + redacted(authCode) +
                ", authorization=" + redacted(authorization) +
                '}';
    }

    private static URI validateBaseUrl(URI value) {
        URI uri = Objects.requireNonNull(value, "baseUrl");
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("baseUrl must use HTTP or HTTPS");
        }
        if (!uri.isAbsolute() || uri.getHost() == null) {
            throw new IllegalArgumentException("baseUrl must be an absolute network URI");
        }
        if (uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "baseUrl must not contain user info, query or fragment");
        }
        return uri;
    }

    private static Duration validateTimeout(Duration value) {
        Duration timeout = Objects.requireNonNull(value, "requestTimeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        return timeout;
    }

    private static String optionalSecret(String field, String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x20 || character > 0x7e) {
                throw new IllegalArgumentException(
                        field + " must be an ASCII HTTP header value");
            }
        }
        return value;
    }

    private static void requireProtectedCredentialTransport(
            URI baseUrl,
            String authCode,
            String authorization) {
        if ((authCode != null || authorization != null)
                && "http".equalsIgnoreCase(baseUrl.getScheme())
                && !isLoopback(baseUrl.getHost())) {
            throw new IllegalArgumentException(
                    "credential-bearing baseUrl must use HTTPS or loopback HTTP");
        }
    }

    private static boolean isLoopback(String host) {
        String value = host.toLowerCase(java.util.Locale.ROOT);
        return "localhost".equals(value)
                || "::1".equals(value)
                || "[::1]".equals(value)
                || "127.0.0.1".equals(value);
    }

    private static String redacted(String value) {
        return value == null ? "<absent>" : "<redacted>";
    }
}
