package com.foggyframework.runtime.api.dto;

import java.util.List;
import java.util.Map;

public record RuntimeDiagnostics(
        String sql,
        Object plan,
        List<String> warnings,
        Map<String, Object> attributes
) {
    public static RuntimeDiagnostics empty() {
        return new RuntimeDiagnostics(null, null, List.of(), Map.of());
    }
}
