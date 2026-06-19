package com.foggyframework.runtime.api.dto;

public record TableListRequest(
        String dataSource,
        String schema,
        String pattern,
        Boolean includeViews
) {
}
