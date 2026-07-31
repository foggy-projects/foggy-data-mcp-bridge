package com.foggyframework.runtime.api.dto;

import java.util.List;
import java.util.Map;

public record CapabilitiesResponse(
        String engine,
        String runtimeApiVersion,
        String schemaVersion,
        boolean enabled,
        String securityMode,
        Map<String, String> capabilities,
        List<String> warnings,
        AuthoringWorkspaceLimits authoringWorkspaceLimits
) {
    /** Compatibility constructor for existing callers. */
    public CapabilitiesResponse(
            String engine,
            String runtimeApiVersion,
            String schemaVersion,
            boolean enabled,
            String securityMode,
            Map<String, String> capabilities,
            List<String> warnings
    ) {
        this(engine, runtimeApiVersion, schemaVersion, enabled, securityMode,
                capabilities, warnings, null);
    }
}
