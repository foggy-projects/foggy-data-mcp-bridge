package com.foggyframework.runtime.api.dto;

import java.util.List;

public record DatasourceTestResponse(
        String name,
        boolean connected,
        String productName,
        String productVersion,
        String driverName,
        String url,
        List<String> warnings
) {
}
