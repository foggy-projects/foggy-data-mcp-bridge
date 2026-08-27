package com.foggyframework.dataset.mcp.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Authentication configuration properties
 *
 * <p>When foggy.auth.token is configured, all API endpoints (except health checks)
 * require Bearer token authentication.
 *
 * <h3>Usage:</h3>
 * <pre>
 * # Environment variable
 * FOGGY_AUTH_TOKEN=${FOGGY_AUTH_TOKEN}
 *
 * # Or in application.yml
 * foggy:
 *   auth:
 *     token: ${FOGGY_AUTH_TOKEN}
 * </pre>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "foggy.auth")
public class AuthProperties {

    public enum Mode {
        AUTO,
        DISABLED,
        STATIC_DEV_ONLY,
        OAUTH_RESOURCE_SERVER
    }

    /** AUTO preserves the legacy token opt-in behavior. */
    private Mode mode = Mode.AUTO;

    /**
     * Authentication token for API access.
     * When configured, all endpoints require Bearer token authentication.
     * When empty, authentication is disabled (development mode).
     */
    private String token;


    /** Canonical MCP protected-resource URI advertised to OAuth clients. */
    private String resourceUri;

    /** Authorization servers allowed to mint access tokens for this resource. */
    private List<String> authorizationServers = new ArrayList<>();

    /** Scopes advertised by protected-resource metadata. */
    private List<String> scopesSupported = new ArrayList<>();

    /** Scopes every authenticated OAuth request must contain. */
    private List<String> requiredScopes = new ArrayList<>();

    /** Require verified JWT roles to match /mcp/{role} endpoints. */
    private boolean enforceMcpRolePaths = true;
    /**
     * Check if authentication is enabled
     */
    public boolean isEnabled() {
        return effectiveMode() != Mode.DISABLED;
    }

    public Mode effectiveMode() {
        if (mode != Mode.AUTO) {
            return mode;
        }
        return token != null && !token.isBlank()
                ? Mode.STATIC_DEV_ONLY
                : Mode.DISABLED;
    }
}
