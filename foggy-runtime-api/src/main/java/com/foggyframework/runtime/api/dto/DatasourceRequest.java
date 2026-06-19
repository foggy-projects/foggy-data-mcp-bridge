package com.foggyframework.runtime.api.dto;

public record DatasourceRequest(
        String name,
        String type,
        String jdbcUrl,
        String username,
        String password,
        String passwordRef,
        Boolean replace,
        Boolean enabled
) {
}
