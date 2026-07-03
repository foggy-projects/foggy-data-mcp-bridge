package com.foggyframework.runtime.api.dto;

public record DatasourcePoolInfo(
        String registryPath,
        String lifecycleStatus,
        boolean poolExists,
        boolean poolClosed,
        Integer activeConnections,
        String lastBorrowedAt,
        String lastReturnedAt,
        String lastCloseReason,
        String lastCloseError,
        Long idlePoolCloseMinutes,
        Long cleanupIntervalMinutes,
        Integer maximumPoolSize,
        Integer minimumIdle,
        Long connectionTimeoutMs,
        Long idleTimeoutMs,
        Long maxLifetimeMs,
        String driverClassName
) {
}
