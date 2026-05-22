package com.foggyframework.dataset.db.model.semantic.memorygrid;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public record MemoryGridInputBinding(String alias,
                                     String resultHandle,
                                     String sourceRoute,
                                     Map<String, Object> metadata) {

    public MemoryGridInputBinding {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
