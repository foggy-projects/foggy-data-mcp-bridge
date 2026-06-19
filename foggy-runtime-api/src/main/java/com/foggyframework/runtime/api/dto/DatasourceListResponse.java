package com.foggyframework.runtime.api.dto;

import java.util.List;

public record DatasourceListResponse(
        List<DatasourceInfo> datasources,
        List<String> warnings
) {
}
