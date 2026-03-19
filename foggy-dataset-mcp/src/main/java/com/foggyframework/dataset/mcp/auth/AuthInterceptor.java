package com.foggyframework.dataset.mcp.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Authentication interceptor for API endpoints.
 *
 * <p>Validates Bearer token from Authorization header.
 * Only active when foggy.auth.token is configured.
 *
 * <h3>Whitelist paths:</h3>
 * <ul>
 *   <li>/actuator/health - Health check endpoint</li>
 *   <li>/actuator/info - Service info endpoint</li>
 *   <li>/api/v1/health - API health check</li>
 * </ul>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthProperties authProperties;

    /**
     * Paths that don't require authentication
     */
    private static final Set<String> WHITELIST_PATHS = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/api/v1/health",
            "/healthz",
            "/readyz"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Skip authentication if not configured (auth disabled)
        if (!authProperties.isEnabled()) {
            return true;
        }

        String path = request.getRequestURI();

        // Whitelist paths don't require authentication
        if (isWhitelisted(path)) {
            return true;
        }

        // Get Authorization header
        String authHeader = request.getHeader("Authorization");

        if (!StringUtils.hasText(authHeader)) {
            log.warn("Missing Authorization header for path: {}", path);
            sendUnauthorized(response, "Missing Authorization header");
            return false;
        }

        // Validate Bearer token format
        if (!authHeader.startsWith("Bearer ")) {
            log.warn("Invalid Authorization header format for path: {}", path);
            sendUnauthorized(response, "Invalid Authorization header format. Expected: Bearer <token>");
            return false;
        }

        String token = authHeader.substring(7); // Remove "Bearer " prefix

        // Validate token
        if (!authProperties.getToken().equals(token)) {
            log.warn("Invalid token for path: {}", path);
            sendUnauthorized(response, "Invalid token");
            return false;
        }

        return true;
    }

    /**
     * Check if path is whitelisted
     */
    private boolean isWhitelisted(String path) {
        // Exact match
        if (WHITELIST_PATHS.contains(path)) {
            return true;
        }
        // Prefix match for actuator endpoints
        return path.startsWith("/actuator/");
    }

    /**
     * Send unauthorized response
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format(
                "{\"code\":401,\"msg\":\"%s\",\"data\":null}",
                message
        ));
    }
}