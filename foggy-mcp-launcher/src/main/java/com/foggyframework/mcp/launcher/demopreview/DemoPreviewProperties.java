package com.foggyframework.mcp.launcher.demopreview;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opt-in configuration for the local synthetic-data preview demo. */
@Data
@ConfigurationProperties(prefix = "foggy.demo-preview")
public class DemoPreviewProperties {

    private boolean enabled;
    private String namespace = "harness_demo_logistics";
    private String model = "DemoWaybillQueryModel";
    private String internalBaseUrl = "http://127.0.0.1:18172";
    private String publicOpenUrl = "http://127.0.0.1:18172/data-viewer/open";
    private String policyVersion = "demo-logistics-v1";
    /** SHA-256 hex fingerprint of the complete incoming Authorization header -> principal id. */
    private Map<String, String> credentialPrincipals = new LinkedHashMap<>();
    /** Service scope id -> synthetic station scope. No personal users or passwords. */
    private Map<String, DemoUser> users = new LinkedHashMap<>();

    @Data
    public static class DemoUser {
        private String displayName;
        private List<String> stationCodes = new ArrayList<>();
    }
}
