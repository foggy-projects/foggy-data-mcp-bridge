package com.foggyframework.dataset.mcp.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpProtocolVersionsTest {

    @Test
    void preservesBothLegacyProtocolVersions() {
        assertThat(McpProtocolVersions.negotiateLegacy(null))
                .isEqualTo(McpProtocolVersions.LEGACY_COMPAT);
        assertThat(McpProtocolVersions.negotiateLegacy(McpProtocolVersions.LEGACY_COMPAT))
                .isEqualTo(McpProtocolVersions.LEGACY_COMPAT);
        assertThat(McpProtocolVersions.negotiateLegacy(McpProtocolVersions.LATEST_LEGACY))
                .isEqualTo(McpProtocolVersions.LATEST_LEGACY);
    }

    @Test
    void doesNotClaimModernProtocolThroughLegacyInitialize() {
        assertThat(McpProtocolVersions.negotiateLegacy(McpProtocolVersions.MODERN_STATELESS))
                .isEqualTo(McpProtocolVersions.LATEST_LEGACY);
        assertThat(McpProtocolVersions.SUPPORTED)
                .containsExactly(
                        McpProtocolVersions.MODERN_STATELESS,
                        McpProtocolVersions.LATEST_LEGACY,
                        McpProtocolVersions.LEGACY_COMPAT);
    }
}
