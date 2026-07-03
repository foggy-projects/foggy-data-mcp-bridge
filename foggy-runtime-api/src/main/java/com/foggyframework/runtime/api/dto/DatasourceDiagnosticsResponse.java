package com.foggyframework.runtime.api.dto;

import java.util.List;
import java.util.Map;

public record DatasourceDiagnosticsResponse(
        boolean registryEnabled,
        String registryPath,
        boolean registryExists,
        Long registrySizeBytes,
        String registryLastModifiedAt,
        int managedDatasourceCount,
        Map<String, String> namespaceBindings,
        List<DatasourceInfo> datasources,
        List<String> warnings
) {
}
