package com.foggyframework.dataset.mcp.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** RFC 9728 protected-resource metadata used by MCP OAuth clients. */
@RestController
@RequiredArgsConstructor
public class McpProtectedResourceMetadataController {

    private final AuthProperties properties;

    @GetMapping({
            "/.well-known/oauth-protected-resource",
            "/.well-known/oauth-protected-resource/mcp"
    })
    public ResponseEntity<Map<String, Object>> metadata() {
        if (properties.effectiveMode() != AuthProperties.Mode.OAUTH_RESOURCE_SERVER) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("resource", properties.getResourceUri());
        metadata.put(
                "authorization_servers",
                properties.getAuthorizationServers());
        metadata.put("scopes_supported", properties.getScopesSupported());
        metadata.put("bearer_methods_supported", java.util.List.of("header"));
        return ResponseEntity.ok(metadata);
    }
}
