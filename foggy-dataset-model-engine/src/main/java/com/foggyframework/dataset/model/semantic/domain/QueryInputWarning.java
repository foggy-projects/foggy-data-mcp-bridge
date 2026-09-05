package com.foggyframework.dataset.model.semantic.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Machine-readable diagnostic produced before a public Query DSL payload is mapped.
 *
 * <p>The compact top-level contract is intentionally stable. Optional, diagnostic-specific
 * attributes belong in {@code details}; raw input values must not be echoed here.</p>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QueryInputWarning(
        String code,
        String path,
        String message,
        String suggestedNextAction,
        boolean safeToAutoRepair,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        Map<String, Object> normalizedFragment,
        String docsRef,
        Map<String, Object> details
) {
    public QueryInputWarning {
        normalizedFragment = normalizedFragment == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(normalizedFragment));
        details = details == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    /** Source-compatible constructor for callers creating legacy diagnostics. */
    public QueryInputWarning(
            String code,
            String path,
            String message,
            String suggestedNextAction,
            boolean safeToAutoRepair,
            Map<String, Object> details) {
        this(code, path, message, suggestedNextAction, safeToAutoRepair,
                Map.of(), "query-dsl", details);
    }
}
