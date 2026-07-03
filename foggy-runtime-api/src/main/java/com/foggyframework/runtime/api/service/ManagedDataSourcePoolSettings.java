package com.foggyframework.runtime.api.service;

public record ManagedDataSourcePoolSettings(
        long idlePoolCloseMinutes,
        long cleanupIntervalMinutes,
        int maximumPoolSize,
        int minimumIdle,
        long connectionTimeoutMs,
        long idleTimeoutMs,
        long maxLifetimeMs,
        String driverClassName
) {
}
