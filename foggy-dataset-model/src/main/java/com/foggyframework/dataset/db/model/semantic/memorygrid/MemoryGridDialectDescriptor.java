package com.foggyframework.dataset.db.model.semantic.memorygrid;

import java.util.List;

/**
 * Describes the Memory Grid implementation visible to routing and audit layers.
 */
public record MemoryGridDialectDescriptor(String engineId,
                                          String dialectId,
                                          boolean planSupported,
                                          boolean gridSqlSupported,
                                          List<String> supportedShapes,
                                          List<String> unsupportedShapes) {

    public MemoryGridDialectDescriptor {
        supportedShapes = supportedShapes == null ? List.of() : List.copyOf(supportedShapes);
        unsupportedShapes = unsupportedShapes == null ? List.of() : List.copyOf(unsupportedShapes);
    }
}
