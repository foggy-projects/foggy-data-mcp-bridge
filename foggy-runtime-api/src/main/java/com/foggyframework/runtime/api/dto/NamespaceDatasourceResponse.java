package com.foggyframework.runtime.api.dto;

import java.util.List;

public record NamespaceDatasourceResponse(
        String namespace,
        String dataSource,
        List<String> warnings
) {
}
