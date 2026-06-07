package com.foggyframework.runtime.api.dto;

public record TableColumnInfo(
        String name,
        String jdbcType,
        Integer jdbcTypeCode,
        Integer size,
        Integer decimalDigits,
        Boolean nullable,
        String defaultValue,
        Integer ordinalPosition
) {
}
