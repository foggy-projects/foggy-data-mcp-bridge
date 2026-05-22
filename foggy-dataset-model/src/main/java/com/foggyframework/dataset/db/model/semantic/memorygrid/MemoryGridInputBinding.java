package com.foggyframework.dataset.db.model.semantic.memorygrid;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public record MemoryGridInputBinding(String alias,
                                     @JsonProperty("result_handle")
                                     String resultHandle,
                                     @JsonProperty("source_route")
                                     String sourceRoute,
                                     Map<String, Object> metadata) {

    public MemoryGridInputBinding {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
