package com.foggyframework.dataset.mcp.auth;

import java.util.Map;
import java.util.Set;

/** Host integration point for verifying OAuth access tokens presented to MCP. */
@FunctionalInterface
public interface McpAccessTokenVerifier {

    VerifiedIdentity verify(String accessToken);

    /**
     * Verified principal and an optional, separately-issued data-plane credential.
     * The inbound MCP access token must never be returned as dataPlaneAuthorization.
     */
    record VerifiedIdentity(
            String subject,
            Set<String> scopes,
            String dataPlaneAuthorization,
            String tenantId,
            String departmentId,
            Set<String> roles,
            Map<String, Object> attributes) {

        public VerifiedIdentity(String subject, Set<String> scopes, String dataPlaneAuthorization) {
            this(subject, scopes, dataPlaneAuthorization, null, null, Set.of(), Map.of());
        }

        public VerifiedIdentity {
            scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
            roles = roles == null ? Set.of() : Set.copyOf(roles);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }

        public McpSecurityIdentity securityIdentity() {
            return new McpSecurityIdentity(
                    subject, tenantId, departmentId, roles, scopes, attributes);
        }
    }
}