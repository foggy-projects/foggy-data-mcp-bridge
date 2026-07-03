package com.foggyframework.runtime.api.dto;

public record DatasourceInfo(
        String name,
        String type,
        String jdbcUrl,
        String username,
        String passwordRef,
        boolean enabled,
        String source,
        boolean managedByRuntimeApi,
        boolean canUpdate,
        boolean canRemove,
        boolean canTest,
        String status,
        String message,
        DatasourcePoolInfo pool
) {
    public DatasourceInfo(
            String name,
            String type,
            String jdbcUrl,
            String username,
            String passwordRef,
            boolean enabled,
            String source,
            boolean managedByRuntimeApi,
            boolean canUpdate,
            boolean canRemove,
            boolean canTest,
            String status,
            String message
    ) {
        this(name, type, jdbcUrl, username, passwordRef, enabled, source, managedByRuntimeApi,
                canUpdate, canRemove, canTest, status, message, null);
    }
}
