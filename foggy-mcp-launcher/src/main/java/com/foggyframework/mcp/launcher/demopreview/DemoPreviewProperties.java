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
    private String publicOpenUrl = "http://127.0.0.1:18172/demo-preview/open";
    private String cookieName = "foggy-demo-preview";
    private boolean secureCookie = true;
    private Duration launchTtl = Duration.ofHours(2);
    private Duration absoluteSessionTtl = Duration.ofHours(2);
    private Duration idleSessionTtl = Duration.ofHours(2);
    private String policyVersion = "demo-logistics-v1";
    /** SHA-256 hex fingerprint of the complete incoming Authorization header -> principal id. */
    private Map<String, String> credentialPrincipals = new LinkedHashMap<>();
    /** User id -> login data and synthetic station scope; passwords must be supplied outside source control. */
    private Map<String, DemoUser> users = new LinkedHashMap<>();

    @Data
    public static class DemoUser {
        private String displayName;
        private String password;
        private List<String> stationCodes = new ArrayList<>();
    }
}
