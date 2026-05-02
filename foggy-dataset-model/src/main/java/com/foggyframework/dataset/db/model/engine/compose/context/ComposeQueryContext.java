package com.foggyframework.dataset.db.model.engine.compose.context;

import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * ComposeQueryContext — the single server-side execution context threaded
 * through every {@code QueryPlan} in a Compose Query script invocation.
 *
 * <p><b>Script-invisible.</b> Neither this object, its {@link Principal},
 * its {@link AuthorityResolver}, nor its {@code traceId} may be exposed to
 * the JavaScript sandbox. Only {@code params} is pierced through as a
 * read-only map accessible via the script's {@code params.xxx} surface (see
 * the M9 sandbox scaffold docs, cases A-08 / A-10).</p>
 *
 * <p>Cross-repo invariant: mirrors Python {@code
 * foggy.dataset_model.engine.compose.context.ComposeQueryContext}.</p>
 *
 * @since 8.2.0.beta
 */
public final class ComposeQueryContext {

    private final Principal principal;
    private final String namespace;
    private final AuthorityResolver authorityResolver;
    private final String traceId;
    private final Map<String, Object> params;
    private final Map<String, String> extensions;

    private ComposeQueryContext(Builder b) {
        this.principal = Objects.requireNonNull(b.principal,
                "ComposeQueryContext.principal is required");
        this.namespace = b.namespace == null ? "" : b.namespace;
        this.authorityResolver = Objects.requireNonNull(b.authorityResolver,
                "ComposeQueryContext.authorityResolver is required; "
                        + "fail-closed means we never resolve with a null resolver");
        this.traceId = b.traceId;
        // Defensive snapshot + unmodifiable view. null input stays null
        // (distinct from empty map — signals "host did not inject any params").
        this.params = b.params == null
                ? null
                : Collections.unmodifiableMap(Map.copyOf(b.params));
        this.extensions = b.extensions == null
                ? null
                : Collections.unmodifiableMap(Map.copyOf(b.extensions));
    }

    public Principal principal() { return principal; }
    public String namespace() { return namespace; }
    public AuthorityResolver authorityResolver() { return authorityResolver; }
    public String traceId() { return traceId; }

    /** Read-only view of host-injected business params. May be null. */
    public Map<String, Object> params() { return params; }

    /** Read-only view of upstream-passthrough extensions. May be null. */
    public Map<String, String> extensions() { return extensions; }

    /**
     * Read a single param, returning {@code defaultValue} when the key is
     * absent or {@code params} itself is null. Used by the sandbox layer to
     * back the script's {@code params.xxx} surface without exposing the
     * underlying map.
     */
    public Object param(String key, Object defaultValue) {
        if (params == null) return defaultValue;
        return params.getOrDefault(key, defaultValue);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Principal principal;
        private String namespace;
        private AuthorityResolver authorityResolver;
        private String traceId;
        private Map<String, Object> params;
        private Map<String, String> extensions;

        public Builder principal(Principal v) { this.principal = v; return this; }
        public Builder namespace(String v) { this.namespace = v; return this; }
        public Builder authorityResolver(AuthorityResolver v) { this.authorityResolver = v; return this; }
        public Builder traceId(String v) { this.traceId = v; return this; }
        public Builder params(Map<String, Object> v) { this.params = v; return this; }
        public Builder extensions(Map<String, String> v) { this.extensions = v; return this; }

        public ComposeQueryContext build() { return new ComposeQueryContext(this); }
    }

    @Override
    public String toString() {
        return "ComposeQueryContext{principal=" + principal
                + ", namespace=" + namespace
                + ", traceId=" + traceId
                + ", paramKeys=" + (params == null ? "null" : params.keySet())
                + '}';
    }
}
