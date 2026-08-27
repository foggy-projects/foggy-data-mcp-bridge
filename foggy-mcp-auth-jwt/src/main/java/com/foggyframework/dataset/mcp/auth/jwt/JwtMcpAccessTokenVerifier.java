package com.foggyframework.dataset.mcp.auth.jwt;

import com.foggyframework.dataset.mcp.auth.McpAccessTokenVerifier;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class JwtMcpAccessTokenVerifier implements McpAccessTokenVerifier {

    private final JwtDecoder jwtDecoder;
    private final JwtMcpAuthProperties properties;

    public JwtMcpAccessTokenVerifier(JwtDecoder jwtDecoder, JwtMcpAuthProperties properties) {
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
    }

    @Override
    public VerifiedIdentity verify(String accessToken) {
        Jwt jwt = jwtDecoder.decode(accessToken);
        if (properties.getAudiences() == null
                || jwt.getAudience().stream().noneMatch(properties.getAudiences()::contains)) {
            throw new BadJwtException("JWT audience is not allowed");
        }
        String subject = stringClaim(jwt, properties.getSubjectClaim());
        if (!StringUtils.hasText(subject)) {
            throw new BadJwtException("JWT subject claim is required");
        }
        String tenantId = stringClaim(jwt, properties.getTenantClaim());
        if (properties.isRequireTenant() && !StringUtils.hasText(tenantId)) {
            throw new BadJwtException("JWT tenant claim is required");
        }
        String departmentId = stringClaim(jwt, properties.getDepartmentClaim());
        Set<String> scopes = stringSet(claim(jwt, properties.getScopesClaim()));
        Set<String> roles = mapRoles(stringSet(claim(jwt, properties.getRolesClaim())));

        Map<String, Object> attributes = new LinkedHashMap<>();
        if (jwt.getIssuer() != null) {
            attributes.put("issuer", jwt.getIssuer().toString());
        }
        if (StringUtils.hasText(jwt.getId())) {
            attributes.put("jwtId", jwt.getId());
        }
        return new VerifiedIdentity(
                subject,
                scopes,
                null,
                tenantId,
                departmentId,
                roles,
                attributes);
    }

    private Set<String> mapRoles(Set<String> externalRoles) {
        Map<String, Set<String>> mappings = properties.getRoleMappings();
        Set<String> mapped = new LinkedHashSet<>();
        if (mappings == null || mappings.isEmpty()) {
            externalRoles.stream()
                    .filter(StringUtils::hasText)
                    .map(role -> role.toUpperCase(Locale.ROOT))
                    .forEach(mapped::add);
            return mapped;
        }
        mappings.forEach((internalRole, aliases) -> {
            if (!StringUtils.hasText(internalRole)) {
                return;
            }
            boolean matched = externalRoles.stream().anyMatch(external ->
                    internalRole.equalsIgnoreCase(external)
                            || (aliases != null && aliases.stream()
                            .anyMatch(alias -> alias != null && alias.equalsIgnoreCase(external))));
            if (matched) {
                mapped.add(internalRole.toUpperCase(Locale.ROOT));
            }
        });
        return mapped;
    }

    private static String stringClaim(Jwt jwt, String claimName) {
        Object value = claim(jwt, claimName);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Object claim(Jwt jwt, String claimPath) {
        if (!StringUtils.hasText(claimPath)) {
            return null;
        }
        Object current = jwt.getClaims();
        for (String segment : claimPath.split("\\.")) {
            if (!(current instanceof Map<?, ?> values)) {
                return null;
            }
            current = values.get(segment);
        }
        return current;
    }

    private static Set<String> stringSet(Object value) {
        Set<String> result = new LinkedHashSet<>();
        addValues(result, value);
        return result;
    }

    private static void addValues(Set<String> target, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> addValues(target, item));
            return;
        }
        if (value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                addValues(target, Array.get(value, index));
            }
            return;
        }
        for (String item : value.toString().split("[\\s,]+")) {
            if (!item.isBlank()) {
                target.add(item.trim());
            }
        }
    }
}