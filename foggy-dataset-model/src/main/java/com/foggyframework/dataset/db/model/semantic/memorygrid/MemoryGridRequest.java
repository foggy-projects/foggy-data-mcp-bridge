package com.foggyframework.dataset.db.model.semantic.memorygrid;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MemoryGridRequest(Map<String, Object> plan,
                                String gridSql,
                                List<MemoryGridInputBinding> bindings,
                                Map<String, Object> hints,
                                Object executablePlan) {

    public MemoryGridRequest {
        plan = plan == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(plan));
        gridSql = gridSql == null || gridSql.isBlank() ? null : gridSql;
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        hints = hints == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(hints));
    }

    public static MemoryGridRequest fromPlan(Map<String, Object> plan,
                                             Map<String, Object> hints,
                                             Object executablePlan) {
        return new MemoryGridRequest(plan, null, List.of(), hints, executablePlan);
    }
}
