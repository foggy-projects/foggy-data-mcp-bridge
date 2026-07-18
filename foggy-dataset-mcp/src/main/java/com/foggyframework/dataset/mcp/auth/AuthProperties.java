package com.foggyframework.dataset.mcp.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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

    /**
     * Authentication token for API access.
     * When configured, all endpoints require Bearer token authentication.
     * When empty, authentication is disabled (development mode).
     */
    private String token;

    /**
     * Check if authentication is enabled
     */
    public boolean isEnabled() {
        return token != null && !token.isBlank();
    }
}
