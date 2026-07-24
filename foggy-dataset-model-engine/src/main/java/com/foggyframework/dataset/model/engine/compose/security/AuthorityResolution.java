package com.foggyframework.dataset.model.engine.compose.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Batch authority-resolution response.
 *
 * <p>{@code bindings} is keyed by QM model name. The resolver contract
 * (not enforced here — dataclass-level construction accepts any non-null
 * map so that test fakes can build negative cases) is that this key set
 * equals {@code request.modelNames()}. The caller detects missing keys
 * and raises {@link AuthorityResolutionException} with
 * {@link AuthorityErrorCodes#MODEL_BINDING_MISSING}.</p>
 *
 * @since 8.2.0.beta
 */
public final class AuthorityResolution {

    private final Map<String, ModelBinding> bindings;
    private final Map<String, String> extensions;

    private AuthorityResolution(Builder b) {
        if (b.bindings == null) {
            throw new IllegalArgumentException(
                    "AuthorityResolution.bindings must not be null; "
                            + "use empty map for an (illegal) empty response");
        }
        // Preserve iteration order from caller — helps logs/diagnostics.
        LinkedHashMap<String, ModelBinding> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ModelBinding> e : b.bindings.entrySet()) {
            String key = e.getKey();
            ModelBinding value = e.getValue();
            if (key == null || key.isEmpty()) {
                throw new IllegalArgumentException(
                        "AuthorityResolution.bindings keys must be non-empty strings");
            }
            Objects.requireNonNull(value,
                    "AuthorityResolution.bindings[" + key + "] must not be null");
            copy.put(key, value);
        }
        this.bindings = Collections.unmodifiableMap(copy);
        this.extensions = b.extensions == null
                ? null
                : Collections.unmodifiableMap(Map.copyOf(b.extensions));
    }

    public Map<String, ModelBinding> bindings() { return bindings; }
    public Map<String, String> extensions() { return extensions; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Map<String, ModelBinding> bindings;
        private Map<String, String> extensions;

        public Builder bindings(Map<String, ModelBinding> v) { this.bindings = v; return this; }
        public Builder extensions(Map<String, String> v) { this.extensions = v; return this; }

        public AuthorityResolution build() { return new AuthorityResolution(this); }
    }

    @Override
    public String toString() {
        return "AuthorityResolution{modelKeys=" + bindings.keySet() + '}';
    }
}
