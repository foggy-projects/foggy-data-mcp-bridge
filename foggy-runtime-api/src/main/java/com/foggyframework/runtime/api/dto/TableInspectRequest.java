package com.foggyframework.runtime.api.dto;

public record TableInspectRequest(
        String dataSource,
        String schema,
        String table,
        Boolean includeIndexes,
        Boolean includeForeignKeys
) {
}
