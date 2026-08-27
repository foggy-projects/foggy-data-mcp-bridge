package com.foggyframework.dataset.mcp.auth.jwt;

import com.foggyframework.dataset.mcp.auth.McpAccessTokenVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtMcpAccessTokenVerifierTest {

    @Test
    void mapsNestedClaimsAndExternalRoles() {
        JwtMcpAuthProperties properties = properties();
        properties.setRolesClaim("realm_access.roles");
        properties.setRoleMappings(Map.of(
                "ADMIN", Set.of("foggy-admin"),
                "ANALYST", Set.of("data-analyst")));
        Jwt jwt = jwt(Map.of(
                "sub", "user-1",
                "tenant_id", "tenant-1",
                "dept_id", "dept-1",
                "scope", "mcp:read data:query",
                "realm_access", Map.of("roles", List.of("data-analyst", "untrusted"))));
        JwtMcpAccessTokenVerifier verifier = new JwtMcpAccessTokenVerifier(token -> jwt, properties);

        McpAccessTokenVerifier.VerifiedIdentity identity = verifier.verify("token");

        assertThat(identity.subject()).isEqualTo("user-1");
        assertThat(identity.tenantId()).isEqualTo("tenant-1");
        assertThat(identity.departmentId()).isEqualTo("dept-1");
        assertThat(identity.scopes()).containsExactlyInAnyOrder("mcp:read", "data:query");
        assertThat(identity.roles()).containsExactly("ANALYST");
        assertThat(identity.dataPlaneAuthorization()).isNull();
    }

    @Test
    void rejectsMissingRequiredTenant() {
        JwtMcpAuthProperties properties = properties();
        properties.setRequireTenant(true);
        Jwt jwt = jwt(Map.of("sub", "user-1", "scope", "mcp:read"));

        assertThatThrownBy(() -> new JwtMcpAccessTokenVerifier(token -> jwt, properties).verify("token"))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    void rejectsAudienceEvenWithHostDecoder() {
        JwtMcpAuthProperties properties = properties();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-1")
                .audience(List.of("different-resource"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThatThrownBy(() -> new JwtMcpAccessTokenVerifier(token -> jwt, properties).verify("token"))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void autoConfigurationBacksOffForHostVerifier() {
        McpAccessTokenVerifier hostVerifier = token ->
                new McpAccessTokenVerifier.VerifiedIdentity("host", Set.of(), null);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JwtMcpAuthAutoConfiguration.class))
                .withPropertyValues("foggy.auth.mode=oauth-resource-server")
                .withBean(McpAccessTokenVerifier.class, () -> hostVerifier)
                .run(context -> {
                    assertThat(context).hasSingleBean(McpAccessTokenVerifier.class);
                    assertThat(context).doesNotHaveBean(JwtMcpAccessTokenVerifier.class);
                    assertThat(context).doesNotHaveBean(JwtDecoder.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"oauth-resource-server", "OAUTH_RESOURCE_SERVER"})
    void autoConfigurationUsesHostDecoder(String mode) {
        Jwt jwt = jwt(Map.of(
                "sub", "user-1",
                "tenant_id", "tenant-1",
                "roles", List.of("ANALYST"),
                "scope", "mcp:read"));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JwtMcpAuthAutoConfiguration.class))
                .withPropertyValues(
                        "foggy.auth.mode=" + mode,
                        "foggy.auth.jwt.audiences=foggy-data")
                .withBean(JwtDecoder.class, () -> token -> jwt)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtMcpAccessTokenVerifier.class);
                    assertThat(context).hasSingleBean(com.foggyframework.dataset.model.spi.SecurityIdentityResolver.class);
                });
    }

    private static JwtMcpAuthProperties properties() {
        JwtMcpAuthProperties properties = new JwtMcpAuthProperties();
        properties.setAudiences(List.of("foggy-data"));
        return properties;
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .audience(List.of("foggy-data"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }
}