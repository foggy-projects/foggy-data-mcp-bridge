package com.foggyframework.runtime.api.dto;

public record NamespaceDatasourceRequest(
        String namespace,
        String dataSource
) {
}
