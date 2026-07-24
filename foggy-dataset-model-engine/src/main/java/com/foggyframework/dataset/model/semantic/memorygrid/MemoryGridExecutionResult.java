package com.foggyframework.dataset.model.semantic.memorygrid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public record MemoryGridExecutionResult(List<Map<String, Object>> rows,
                                        Map<String, Object> validation,
                                        Map<String, Object> summary) {

    public MemoryGridExecutionResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
        validation = validation == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(validation));
        summary = summary == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(summary));
    }
}
