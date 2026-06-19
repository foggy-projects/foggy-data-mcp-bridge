package com.foggyframework.runtime.api.dto;

public record SqlColumnInfo(
        String name,
        String jdbcType,
        Integer jdbcTypeCode,
        Integer displaySize,
        Integer precision,
        Integer scale,
        Boolean nullable
) {
}
