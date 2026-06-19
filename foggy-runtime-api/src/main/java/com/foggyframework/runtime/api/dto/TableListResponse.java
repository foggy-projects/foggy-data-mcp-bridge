package com.foggyframework.runtime.api.dto;

import java.util.List;

public record TableListResponse(
        String dataSource,
        String schema,
        List<TableInfo> tables,
        List<String> warnings
) {
}
