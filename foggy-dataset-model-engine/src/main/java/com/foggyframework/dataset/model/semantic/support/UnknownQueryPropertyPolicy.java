package com.foggyframework.dataset.model.semantic.support;

/** Policy for unknown properties in public Query DSL payloads. */
public enum UnknownQueryPropertyPolicy {
    IGNORE,
    WARN,
    STRICT;

    public static UnknownQueryPropertyPolicy orDefault(UnknownQueryPropertyPolicy policy) {
        return policy == null ? WARN : policy;
    }
}
