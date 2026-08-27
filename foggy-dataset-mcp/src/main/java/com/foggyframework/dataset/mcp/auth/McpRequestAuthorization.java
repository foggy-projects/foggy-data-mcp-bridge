package com.foggyframework.dataset.mcp.auth;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/** Resolves verified identity and credentials that may safely enter Foggy data-plane calls. */
public final class McpRequestAuthorization {

    static final String DATA_PLANE_AUTHORIZATION_ATTRIBUTE =
            McpRequestAuthorization.class.getName() + ".dataPlaneAuthorization";
    static final String AUTHENTICATED_MODE_ATTRIBUTE =
            McpRequestAuthorization.class.getName() + ".authenticatedMode";
    static final String SECURITY_IDENTITY_ATTRIBUTE =
            McpRequestAuthorization.class.getName() + ".securityIdentity";

    private static final Set<String> IDENTITY_HEADERS = Set.of(
            "x-user-id",
            "x-dept-id",
            "x-tenant-id",
            "x-roles",
            "x-permission-tags",
            "x-recipe-owner-roles",
            "x-registry-actor-role");

    private McpRequestAuthorization() {
    }

    public static String dataPlaneAuthorization(String legacyAuthorization) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return legacyAuthorization;
        }
        Object authenticatedMode = attributes.getAttribute(
                AUTHENTICATED_MODE_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        if (!Boolean.TRUE.equals(authenticatedMode)) {
            return legacyAuthorization;
        }
        Object value = attributes.getAttribute(
                DATA_PLANE_AUTHORIZATION_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        return value instanceof String authorization ? authorization : null;
    }

    public static McpSecurityIdentity currentIdentity() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object value = attributes.getAttribute(
                SECURITY_IDENTITY_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        return value instanceof McpSecurityIdentity identity ? identity : null;
    }

    /**
     * In OAuth mode, removes caller-provided identity headers and replaces them with verified claims.
     * Static development mode has no verified identity and retains its legacy header behavior.
     */
    public static Map<String, String> applyTrustedIdentityHeaders(Map<String, String> requestHeaders) {
        Map<String, String> result = new LinkedHashMap<>();
        if (requestHeaders != null) {
            requestHeaders.forEach((name, value) -> {
                if (name == null || !IDENTITY_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                    result.put(name, value);
                }
            });
        }
        McpSecurityIdentity identity = currentIdentity();
        if (identity == null) {
            return requestHeaders == null ? result : new LinkedHashMap<>(requestHeaders);
        }
        putIfText(result, "X-User-Id", identity.subject());
        putIfText(result, "X-Dept-Id", identity.departmentId());
        putIfText(result, "X-Tenant-Id", identity.tenantId());
        putIfText(result, "X-Roles", join(identity.roles()));
        putIfText(result, "X-Permission-Tags", join(identity.scopes()));
        return result;
    }

    private static String join(Set<String> values) {
        StringJoiner joiner = new StringJoiner(",");
        if (values != null) {
            values.stream().sorted().forEach(joiner::add);
        }
        return joiner.toString();
    }

    private static void putIfText(Map<String, String> headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.put(name, value);
        }
    }
}