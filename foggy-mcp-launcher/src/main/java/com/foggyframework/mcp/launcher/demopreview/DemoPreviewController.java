package com.foggyframework.mcp.launcher.demopreview;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.InetAddress;

/** Existing model-permission callback: all browser data still goes through engine authorization. */
@RestController
@ConditionalOnProperty(prefix="foggy.demo-preview",name="enabled",havingValue="true")
public class DemoPreviewController {
    private final DemoPreviewSessionService identities;
    private final DemoPreviewProperties properties;
    public DemoPreviewController(DemoPreviewSessionService identities,DemoPreviewProperties properties) { this.identities=identities; this.properties=properties; }
    @PostMapping("/demo-preview/internal/model-permissions")
    public ResponseEntity<DemoPreviewSessionService.PermissionGrant> resolveModelPermissions(
            @RequestBody PermissionRequest permission,@RequestHeader(value="Authorization",required=false) String authorization,
            HttpServletRequest request) {
        boolean loopback;
        try { loopback=InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress(); }
        catch (Exception e) { loopback=false; }
        if (!loopback) return ResponseEntity.status(403).header("Cache-Control","no-store")
                .body(DemoPreviewSessionService.PermissionGrant.denied(properties.getPolicyVersion()));
        return ResponseEntity.ok().header("Cache-Control","no-store").body(identities.resolveAuthorization(
                authorization,permission.namespace(),permission.model(),permission.action()));
    }
    public record PermissionRequest(String namespace,String model,String action) { }
}
