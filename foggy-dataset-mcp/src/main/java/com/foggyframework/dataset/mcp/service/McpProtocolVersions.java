package com.foggyframework.dataset.mcp.service;

import java.util.List;

/** Explicit MCP compatibility contract for legacy and stateless clients. */
public final class McpProtocolVersions {

    public static final String MODERN_STATELESS = "2026-07-28";
    public static final String LATEST_LEGACY = "2025-11-25";
    public static final String LEGACY_COMPAT = "2024-11-05";
    public static final List<String> SUPPORTED = List.of(
            MODERN_STATELESS, LATEST_LEGACY, LEGACY_COMPAT);

    private McpProtocolVersions() {
    }

    static String negotiateLegacy(Object requestedVersion) {
        String requested = requestedVersion == null ? null : requestedVersion.toString();
        if (requested == null || requested.isBlank()) {
            return LEGACY_COMPAT;
        }
        if (LATEST_LEGACY.equals(requested) || LEGACY_COMPAT.equals(requested)) {
            return requested;
        }
        // Modern clients do not initialize; this avoids making a false support claim.
        return LATEST_LEGACY;
    }
}
