package com.foggyframework.runtime.api.dto;

import java.util.List;

public record DatasourceMutationResponse(
        DatasourceInfo datasource,
        List<String> warnings
) {
}
