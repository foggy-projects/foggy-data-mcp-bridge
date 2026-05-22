package com.foggyframework.dataset.db.model.semantic.memorygrid;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record MemoryGridValidation(Map<String, Object> evidence) {

    public MemoryGridValidation {
        evidence = evidence == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
    }
}
