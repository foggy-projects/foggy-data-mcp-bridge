package com.foggyframework.dataset.model.semantic.memorygrid;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes the Memory Grid implementation visible to routing and audit layers.
 */
public record MemoryGridDialectDescriptor(String engineId,
                                          String dialectId,
                                          boolean planSupported,
                                          boolean gridSqlSupported,
                                          List<String> supportedShapes,
                                          List<String> unsupportedShapes,
                                          Map<String, Object> productionGuard) {

    public MemoryGridDialectDescriptor(String engineId,
                                       String dialectId,
                                       boolean planSupported,
                                       boolean gridSqlSupported,
                                       List<String> supportedShapes,
                                       List<String> unsupportedShapes) {
        this(engineId, dialectId, planSupported, gridSqlSupported, supportedShapes, unsupportedShapes, Map.of());
    }

    public MemoryGridDialectDescriptor {
        supportedShapes = supportedShapes == null ? List.of() : List.copyOf(supportedShapes);
        unsupportedShapes = unsupportedShapes == null ? List.of() : List.copyOf(unsupportedShapes);
        productionGuard = productionGuard == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(productionGuard));
    }
}
