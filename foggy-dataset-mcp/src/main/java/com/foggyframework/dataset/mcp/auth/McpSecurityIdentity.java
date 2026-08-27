package com.foggyframework.dataset.mcp.auth;

import java.util.Map;
import java.util.Set;

/** Trusted identity produced only after access-token verification. */
public record McpSecurityIdentity(
        String subject,
        String tenantId,
        String departmentId,
        Set<String> roles,
        Set<String> scopes,
        Map<String, Object> attributes) {

    public McpSecurityIdentity {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}