package com.foggyframework.runtime.api.dto;

public record TableListRequest(
        String dataSource,
        String namespace,
        String schema,
        String pattern,
        Boolean includeViews
) {
}
