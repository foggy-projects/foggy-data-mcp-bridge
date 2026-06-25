package com.foggyframework.runtime.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "foggy.runtime-api")
public class FoggyRuntimeApiProperties {

    private boolean enabled = false;
    private String runtimeApiVersion = "foggy-runtime-api/v1";
    private String schemaVersion = "2026-06-06";
    private String securityMode = "none-dev-test-only";
    private String authCode;
    private BundleRegistry bundleRegistry = new BundleRegistry();
    private DatasourceRegistry datasourceRegistry = new DatasourceRegistry();

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

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public boolean isAuthCodeConfigured() {
        return StringUtils.hasText(authCode);
    }

    public boolean isAuthCodeRequired() {
        return "auth-code".equalsIgnoreCase(securityMode) || isAuthCodeConfigured();
    }

    public String getEffectiveSecurityMode() {
        return isAuthCodeRequired() ? "auth-code" : securityMode;
    }

    public BundleRegistry getBundleRegistry() {
        return bundleRegistry;
    }

    public void setBundleRegistry(BundleRegistry bundleRegistry) {
        this.bundleRegistry = bundleRegistry;
    }

    public DatasourceRegistry getDatasourceRegistry() {
        return datasourceRegistry;
    }

    public void setDatasourceRegistry(DatasourceRegistry datasourceRegistry) {
        this.datasourceRegistry = datasourceRegistry;
    }

    public static class BundleRegistry {
        private boolean enabled = true;
        private String path = ".foggy-runtime/runtime-bundles.json";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class DatasourceRegistry {
        private boolean enabled = true;
        private String path = ".foggy-runtime/runtime-datasources.json";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
