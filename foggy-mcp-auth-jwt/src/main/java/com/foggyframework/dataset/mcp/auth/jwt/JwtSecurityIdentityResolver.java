package com.foggyframework.dataset.mcp.auth.jwt;

import com.foggyframework.dataset.mcp.auth.McpAccessTokenVerifier;
import com.foggyframework.dataset.model.spi.SecurityIdentityResolver;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.StringJoiner;

/** Bridges the verified JWT identity into existing Foggy model identity consumers. */
public final class JwtSecurityIdentityResolver implements SecurityIdentityResolver {

    private final JwtMcpAccessTokenVerifier verifier;

    public JwtSecurityIdentityResolver(JwtMcpAccessTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public ResolvedIdentity resolve(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Expected Bearer authorization");
        }
        McpAccessTokenVerifier.VerifiedIdentity identity = verifier.verify(authorization.substring(7));
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("scopes", join(identity.scopes()));
        attributes.put("roles", join(identity.roles()));
        identity.attributes().forEach((key, value) -> {
            if (key != null && value != null) {
                attributes.put(key, value.toString());
            }
        });
        return new ResolvedIdentity(
                identity.subject(),
                identity.departmentId(),
                identity.tenantId(),
                attributes);
    }

    private static String join(java.util.Set<String> values) {
        StringJoiner joiner = new StringJoiner(",");
        values.stream().sorted().forEach(joiner::add);
        return joiner.toString();
    }
}