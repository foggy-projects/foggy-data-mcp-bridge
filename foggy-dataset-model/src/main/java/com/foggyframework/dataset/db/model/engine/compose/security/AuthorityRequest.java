package com.foggyframework.dataset.db.model.engine.compose.security;

import com.foggyframework.dataset.db.model.engine.compose.context.Principal;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Batch authority-resolution request.
 *
 * <p><b>Always batched.</b> Even a single-model resolution sends
 * {@code models=[one]}. This prevents protocol drift when a future release
 * adds a per-call batch optimisation.</p>
 *
 * @since 8.2.0.beta
 */
public final class AuthorityRequest {

    private final Principal principal;
    private final String namespace;
    private final String traceId;
    private final List<ModelQuery> models;
    private final Map<String, String> extensions;

    private AuthorityRequest(Builder b) {
        this.principal = Objects.requireNonNull(b.principal,
                "AuthorityRequest.principal must be a Principal");
        this.namespace = b.namespace == null ? "" : b.namespace;
        this.traceId = b.traceId;  // optional
        if (b.models == null || b.models.isEmpty()) {
            throw new IllegalArgumentException(
                    "AuthorityRequest.models must be non-empty; "
                            + "single-model requests use a size-1 list");
        }
        for (int i = 0; i < b.models.size(); i++) {
            if (b.models.get(i) == null) {
                throw new IllegalArgumentException(
                        "AuthorityRequest.models[" + i + "] must not be null");
            }
        }
        this.models = Collections.unmodifiableList(List.copyOf(b.models));
        this.extensions = b.extensions == null
                ? null
                : Collections.unmodifiableMap(Map.copyOf(b.extensions));
    }

    public Principal principal() { return principal; }
    public String namespace() { return namespace; }
    public String traceId() { return traceId; }
    public List<ModelQuery> models() { return models; }
    public Map<String, String> extensions() { return extensions; }

    /** Ordered list of model names in this request. */
    public List<String> modelNames() {
        return models.stream().map(ModelQuery::model).collect(Collectors.toList());
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Principal principal;
        private String namespace;
        private String traceId;
        private List<ModelQuery> models;
        private Map<String, String> extensions;

        public Builder principal(Principal v) { this.principal = v; return this; }
        public Builder namespace(String v) { this.namespace = v; return this; }
        public Builder traceId(String v) { this.traceId = v; return this; }
        public Builder models(List<ModelQuery> v) { this.models = v; return this; }
        public Builder extensions(Map<String, String> v) { this.extensions = v; return this; }

        public AuthorityRequest build() { return new AuthorityRequest(this); }
    }

    @Override
    public String toString() {
        return "AuthorityRequest{namespace=" + namespace
                + ", principal=" + principal
                + ", traceId=" + traceId
                + ", models=" + modelNames() + '}';
    }
}
