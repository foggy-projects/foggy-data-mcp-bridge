package com.foggyframework.dataset.mcp.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpProtectedResourceMetadataControllerTest {

    @Test
    void returnsNotFoundWhenOauthIsDisabled() {
        AuthProperties properties = new AuthProperties();

        var response = new McpProtectedResourceMetadataController(properties).metadata();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void publishesMetadataOnlyInOauthResourceServerMode() {
        AuthProperties properties = new AuthProperties();
        properties.setMode(AuthProperties.Mode.OAUTH_RESOURCE_SERVER);
        properties.setResourceUri("https://mcp.example.test/mcp");
        properties.setAuthorizationServers(List.of("https://auth.example.test"));
        properties.setScopesSupported(List.of("mcp:read"));

        var response = new McpProtectedResourceMetadataController(properties).metadata();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("resource", "https://mcp.example.test/mcp")
                .containsEntry("authorization_servers", List.of("https://auth.example.test"))
                .containsEntry("scopes_supported", List.of("mcp:read"));
    }
}
