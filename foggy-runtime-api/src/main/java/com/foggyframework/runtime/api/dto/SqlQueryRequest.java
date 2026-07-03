package com.foggyframework.runtime.api.dto;

public record SqlQueryRequest(
        String dataSource,
        String namespace,
        String sql,
        Integer maxRows,
        Integer timeoutSeconds
) {
}
