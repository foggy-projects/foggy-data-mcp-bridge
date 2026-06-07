package com.foggyframework.runtime.api.dto;

public record TableForeignKeyInfo(
        String name,
        String column,
        String referencedSchema,
        String referencedTable,
        String referencedColumn
) {
}
