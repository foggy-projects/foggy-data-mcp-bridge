package com.foggyframework.runtime.api.dto;

public record NamespaceDatasourceRequest(
        String namespace,
        String dataSource,
        String revokeMode
) {
    public NamespaceDatasourceRequest(String namespace, String dataSource) {
        this(namespace, dataSource, null);
    }
}
