package com.foggyframework.runtime.api.dto;

public record TableInfo(
        String schema,
        String name,
        String type,
        String remarks
) {
}
