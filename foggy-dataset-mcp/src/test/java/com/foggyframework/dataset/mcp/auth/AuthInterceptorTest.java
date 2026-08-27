package com.foggyframework.dataset.mcp.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void staticModeFailsClosedWithoutToken() {
        AuthProperties properties = new AuthProperties();
        properties.setMode(AuthProperties.Mode.STATIC_DEV_ONLY);

        assertThatThrownBy(() -> interceptor(properties, null).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("foggy.auth.token");
    }

    @Test
    void oauthModeFailsClosedWithoutVerifier() {
        AuthProperties properties = oauthProperties();

        assertThatThrownBy(() -> interceptor(properties, null).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("McpAccessTokenVerifier");
    }

    @Test
    void oauthIdentityUsesMappedDataPlaneCredential() throws Exception {
        AuthProperties properties = oauthProperties();
        McpAccessTokenVerifier verifier = mock(McpAccessTokenVerifier.class);
        when(verifier.verify("inbound-token")).thenReturn(identity(
                "subject-1", Set.of("mcp:read"), "Bearer downstream-token",
                "tenant-1", Set.of("ANALYST")));
        AuthInterceptor interceptor = interceptor(properties, verifier);
        interceptor.afterPropertiesSet();
        MockHttpServletRequest request = request("/mcp/analyst", "Bearer inbound-token");

        assertThat(interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(request.getAttribute(
                McpRequestAuthorization.DATA_PLANE_AUTHORIZATION_ATTRIBUTE))
                .isEqualTo("Bearer downstream-token");
        assertThat(request.getAttribute(
                McpRequestAuthorization.AUTHENTICATED_MODE_ATTRIBUTE))
                .isEqualTo(true);
        assertThat(request.getAttribute(
                McpRequestAuthorization.SECURITY_IDENTITY_ATTRIBUTE))
                .isInstanceOf(McpSecurityIdentity.class);
    }

    @Test
    void oauthRejectsInsufficientRole() throws Exception {
        AuthProperties properties = oauthProperties();
        AuthInterceptor interceptor = interceptor(properties, token -> identity(
                "subject-1", Set.of("mcp:read"), null,
                "tenant-1", Set.of("BUSINESS")));
        interceptor.afterPropertiesSet();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(
                request("/mcp/admin", "Bearer token"), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Insufficient role");
    }

    @Test
    void oauthRejectsInsufficientScope() throws Exception {
        AuthProperties properties = oauthProperties();
        properties.setRequiredScopes(List.of("mcp:read", "data:query"));
        AuthInterceptor interceptor = interceptor(properties, token -> identity(
                "subject-1", Set.of("mcp:read"), null,
                "tenant-1", Set.of("ANALYST")));
        interceptor.afterPropertiesSet();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(
                request("/mcp/analyst", "Bearer token"), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Insufficient scope");
    }

    @Test
    void verifiedClaimsReplaceCallerIdentityHeaders() throws Exception {
        AuthProperties properties = oauthProperties();
        AuthInterceptor interceptor = interceptor(properties, token -> identity(
                "verified-user", Set.of("mcp:read"), null,
                "verified-tenant", Set.of("ANALYST")));
        interceptor.afterPropertiesSet();
        MockHttpServletRequest request = request("/mcp/analyst", "Bearer token");
        assertThat(interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object())).isTrue();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Map<String, String> callerHeaders = new LinkedHashMap<>();
        callerHeaders.put("X-User-Id", "spoofed-user");
        callerHeaders.put("X-Tenant-Id", "spoofed-tenant");
        callerHeaders.put("X-Roles", "ADMIN");
        callerHeaders.put("X-Recipe-Owner-Roles", "OWNER");
        callerHeaders.put("X-Trace-Id", "trace-1");

        Map<String, String> trusted = McpRequestAuthorization.applyTrustedIdentityHeaders(callerHeaders);

        assertThat(trusted)
                .containsEntry("X-User-Id", "verified-user")
                .containsEntry("X-Tenant-Id", "verified-tenant")
                .containsEntry("X-Roles", "ANALYST")
                .containsEntry("X-Permission-Tags", "mcp:read")
                .containsEntry("X-Trace-Id", "trace-1")
                .doesNotContainKey("X-Recipe-Owner-Roles");
    }

    @Test
    void oauthRejectsInboundTokenReuseForDataPlane() throws Exception {
        AuthProperties properties = oauthProperties();
        AuthInterceptor interceptor = interceptor(properties, token -> identity(
                "subject-1", Set.of(), "Bearer token",
                "tenant-1", Set.of("ANALYST")));
        interceptor.afterPropertiesSet();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(
                request("/mcp/analyst", "Bearer token"), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void blankConfiguredScopeDoesNotBecomeARequirement() throws Exception {
        AuthProperties properties = oauthProperties();
        properties.setRequiredScopes(List.of(""));
        AuthInterceptor interceptor = interceptor(properties, token -> identity(
                "subject-1", Set.of(), null,
                "tenant-1", Set.of("ANALYST")));
        interceptor.afterPropertiesSet();

        assertThat(interceptor.preHandle(
                request("/mcp/analyst", "Bearer token"),
                new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void oauthChallengeUsesProtectedResourceMetadataPath() throws Exception {
        AuthProperties properties = oauthProperties();
        AuthInterceptor interceptor = interceptor(properties, token -> identity(
                "subject-1", Set.of(), null, "tenant-1", Set.of("ANALYST")));
        interceptor.afterPropertiesSet();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/mcp/analyst");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo(
                "Bearer resource_metadata=\"https://mcp.example.test/"
                        + ".well-known/oauth-protected-resource/mcp\"");
    }

    private static MockHttpServletRequest request(String path, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.addHeader("Authorization", authorization);
        return request;
    }

    private static McpAccessTokenVerifier.VerifiedIdentity identity(
            String subject,
            Set<String> scopes,
            String dataPlaneAuthorization,
            String tenantId,
            Set<String> roles) {
        return new McpAccessTokenVerifier.VerifiedIdentity(
                subject, scopes, dataPlaneAuthorization, tenantId, null, roles, Map.of());
    }

    private static AuthProperties oauthProperties() {
        AuthProperties properties = new AuthProperties();
        properties.setMode(AuthProperties.Mode.OAUTH_RESOURCE_SERVER);
        properties.setResourceUri("https://mcp.example.test/mcp");
        properties.setAuthorizationServers(List.of("https://auth.example.test"));
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static AuthInterceptor interceptor(
            AuthProperties properties, McpAccessTokenVerifier verifier) {
        ObjectProvider<McpAccessTokenVerifier> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(verifier);
        if (verifier != null) {
            when(provider.getObject()).thenReturn(verifier);
        }
        return new AuthInterceptor(properties, provider);
    }
}