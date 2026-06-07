package com.foggyframework.runtime.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "foggy.runtime-api")
public class FoggyRuntimeApiProperties {

    private boolean enabled = false;
    private String runtimeApiVersion = "foggy-runtime-api/v1";
    private String schemaVersion = "2026-06-06";
    private String securityMode = "none-dev-test-only";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRuntimeApiVersion() {
        return runtimeApiVersion;
    }

    public void setRuntimeApiVersion(String runtimeApiVersion) {
        this.runtimeApiVersion = runtimeApiVersion;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getSecurityMode() {
        return securityMode;
    }

    public void setSecurityMode(String securityMode) {
        this.securityMode = securityMode;
    }
}
