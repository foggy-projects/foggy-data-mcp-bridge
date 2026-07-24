package com.foggyframework.dataset.model.semantic.port;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Engine-neutral SQL generation result consumed by the Compose compiler.
 *
 * <p>The DTO deliberately excludes the JDBC query-engine instance carried by
 * the legacy result. Compose only needs SQL text, ordered parameters,
 * prerequisite CTE stages, and diagnostics.</p>
 *
 * @since 9.4.0
 */
public record ComposeSqlGeneration(
        String sql,
        List<Object> params,
        List<CteStage> cteStages,
        Map<String, Object> diagnostics) {

    public ComposeSqlGeneration {
        params = params == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(params));
        cteStages = cteStages == null ? List.of() : List.copyOf(cteStages);
        diagnostics = diagnostics == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(diagnostics));
    }

    public boolean hasCteStages() {
        return !cteStages.isEmpty();
    }

    public record CteStage(String alias, String sql, List<Object> params) {
        public CteStage {
            params = params == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(params));
        }
    }
}
