package com.foggyframework.dataset.mcp.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validates static development credentials or verified OAuth bearer access tokens. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor, InitializingBean {

    private final AuthProperties authProperties;
    private final ObjectProvider<McpAccessTokenVerifier> accessTokenVerifier;

    private static final Set<String> WHITELIST_PATHS = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/api/v1/health",
            "/healthz",
            "/readyz",
            "/.well-known/oauth-protected-resource",
            "/.well-known/oauth-protected-resource/mcp"
    );

    @Override
    public void afterPropertiesSet() {
        if (authProperties.effectiveMode() == AuthProperties.Mode.STATIC_DEV_ONLY) {
            if (!StringUtils.hasText(authProperties.getToken())) {
                throw new IllegalStateException(
                        "foggy.auth.token is required in static-dev-only mode");
            }
            return;
        }
        if (authProperties.effectiveMode() != AuthProperties.Mode.OAUTH_RESOURCE_SERVER) {
            return;
        }
        List<String> authorizationServers = authProperties.getAuthorizationServers();
        if (!StringUtils.hasText(authProperties.getResourceUri())
                || authorizationServers == null
                || authorizationServers.stream().noneMatch(StringUtils::hasText)) {
            throw new IllegalStateException(
                    "foggy.auth.resource-uri and authorization-servers are required in OAuth resource-server mode");
        }
        if (accessTokenVerifier.getIfAvailable() == null) {
            throw new IllegalStateException(
                    "OAuth resource-server mode requires an McpAccessTokenVerifier bean");
        }
        protectedResourceMetadataUri();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!authProperties.isEnabled()) {
            return true;
        }

        String path = request.getRequestURI();
        if (isWhitelisted(path)) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader)) {
            log.warn("Missing Authorization header for path: {}", path);
            sendUnauthorized(response, "Missing Authorization header");
            return false;
        }
        if (!authHeader.startsWith("Bearer ")) {
            log.warn("Invalid Authorization header format for path: {}", path);
            sendUnauthorized(response, "Invalid Authorization header format. Expected: Bearer <token>");
            return false;
        }

        String token = authHeader.substring(7);
        if (!StringUtils.hasText(token)) {
            sendUnauthorized(response, "Invalid token");
            return false;
        }

        if (authProperties.effectiveMode() == AuthProperties.Mode.STATIC_DEV_ONLY) {
            if (!MessageDigest.isEqual(
                    authProperties.getToken().getBytes(StandardCharsets.UTF_8),
                    token.getBytes(StandardCharsets.UTF_8))) {
                log.warn("Invalid token for path: {}", path);
                sendUnauthorized(response, "Invalid token");
                return false;
            }
            request.setAttribute(McpRequestAuthorization.AUTHENTICATED_MODE_ATTRIBUTE, true);
            return true;
        }

        McpAccessTokenVerifier.VerifiedIdentity verified;
        try {
            verified = accessTokenVerifier.getObject().verify(token);
        } catch (RuntimeException verificationFailure) {
            log.warn("Access token verification failed for path: {}", path);
            log.debug("Access token verification detail for path {}: {}",
                    path, verificationFailure.getMessage());
            sendUnauthorized(response, "Invalid token");
            return false;
        }
        if (verified == null || !StringUtils.hasText(verified.subject())) {
            sendUnauthorized(response, "Invalid token");
            return false;
        }
        String dataPlaneAuthorization = verified.dataPlaneAuthorization();
        if (authHeader.equals(dataPlaneAuthorization) || token.equals(dataPlaneAuthorization)) {
            log.error("Verifier attempted to reuse the inbound access token as a data-plane credential");
            sendUnauthorized(response, "Invalid token mapping");
            return false;
        }
        if (!hasRequiredScopes(verified.scopes())) {
            sendForbidden(response, "Insufficient scope");
            return false;
        }
        if (!hasRequiredMcpRole(request, verified.roles())) {
            sendForbidden(response, "Insufficient role");
            return false;
        }

        if (StringUtils.hasText(dataPlaneAuthorization)) {
            request.setAttribute(
                    McpRequestAuthorization.DATA_PLANE_AUTHORIZATION_ATTRIBUTE,
                    dataPlaneAuthorization);
        }
        request.setAttribute(McpRequestAuthorization.AUTHENTICATED_MODE_ATTRIBUTE, true);
        request.setAttribute(
                McpRequestAuthorization.SECURITY_IDENTITY_ATTRIBUTE,
                verified.securityIdentity());
        return true;
    }

    private boolean hasRequiredScopes(Set<String> actualScopes) {
        List<String> configured = authProperties.getRequiredScopes();
        if (configured == null) {
            return true;
        }
        Set<String> required = configured.stream()
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        return required.isEmpty()
                || (actualScopes != null && actualScopes.containsAll(required));
    }

    private boolean hasRequiredMcpRole(HttpServletRequest request, Set<String> actualRoles) {
        if (!authProperties.isEnforceMcpRolePaths()) {
            return true;
        }
        String requiredRole = requiredMcpRole(request);
        if (requiredRole == null) {
            return true;
        }
        Set<String> roles = actualRoles == null ? Set.of() : actualRoles;
        return roles.stream()
                .filter(StringUtils::hasText)
                .map(role -> role.toUpperCase(Locale.ROOT))
                .anyMatch(role -> "ADMIN".equals(role) || requiredRole.equals(role));
    }

    private String requiredMcpRole(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path.startsWith("/mcp/admin")) {
            return "ADMIN";
        }
        if (path.startsWith("/mcp/analyst")) {
            return "ANALYST";
        }
        if (path.startsWith("/mcp/business")) {
            return "BUSINESS";
        }
        return null;
    }

    private boolean isWhitelisted(String path) {
        if (WHITELIST_PATHS.contains(path)) {
            return true;
        }
        return path.startsWith("/actuator/");
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        writeAuthError(response, message);
    }

    private void sendForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        writeAuthError(response, message);
    }

    private void writeAuthError(HttpServletResponse response, String message) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        if (authProperties.effectiveMode() == AuthProperties.Mode.OAUTH_RESOURCE_SERVER) {
            String metadata = protectedResourceMetadataUri();
            response.setHeader("WWW-Authenticate",
                    "Bearer resource_metadata=\"" + metadata + "\"");
        }
        response.getWriter().write(String.format(
                "{\"code\":%d,\"msg\":\"%s\",\"data\":null}",
                response.getStatus(), message));
    }

    private String protectedResourceMetadataUri() {
        URI resource;
        try {
            resource = URI.create(authProperties.getResourceUri());
        } catch (IllegalArgumentException invalidUri) {
            throw new IllegalStateException(
                    "foggy.auth.resource-uri must be an absolute URI", invalidUri);
        }
        if (!resource.isAbsolute() || resource.getRawAuthority() == null
                || resource.getRawQuery() != null || resource.getRawFragment() != null) {
            throw new IllegalStateException(
                    "foggy.auth.resource-uri must be an absolute URI without query or fragment");
        }
        String path = resource.getRawPath();
        if (path == null || "/".equals(path)) {
            path = "";
        } else if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return resource.getScheme() + "://" + resource.getRawAuthority()
                + "/.well-known/oauth-protected-resource" + path;
    }
}