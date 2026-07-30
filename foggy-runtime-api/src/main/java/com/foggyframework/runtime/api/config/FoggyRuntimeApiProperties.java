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
    private RuntimeApiAuthScope authScope = RuntimeApiAuthScope.MUTATIONS;
    private BundleRegistry bundleRegistry = new BundleRegistry();
    private DatasourceRegistry datasourceRegistry = new DatasourceRegistry();
    private DatasourcePool datasourcePool = new DatasourcePool();

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

    public RuntimeApiAuthScope getAuthScope() {
        return authScope;
    }

    public void setAuthScope(RuntimeApiAuthScope authScope) {
        this.authScope = authScope;
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

    public boolean isManagementAllAuthScope() {
        return authScope == RuntimeApiAuthScope.MANAGEMENT_ALL;
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

    public DatasourcePool getDatasourcePool() {
        return datasourcePool;
    }

    public void setDatasourcePool(DatasourcePool datasourcePool) {
        this.datasourcePool = datasourcePool;
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

    public static class DatasourcePool {
        private boolean cleanupEnabled = true;
        private long idlePoolCloseMinutes = 15;
        private long cleanupIntervalMinutes = 1;
        private int maximumPoolSize = 4;
        private int minimumIdle = 0;
        private long connectionTimeoutMs = 10000;
        private long idleTimeoutMs = 120000;
        private long maxLifetimeMs = 1800000;
        /**
         * Maximum time an old datasource binding may drain existing leases.
         * Values outside 1000..300000 fail Runtime API startup.
         */
        private long leaseDrainTimeoutMs = 60000;

        public boolean isCleanupEnabled() {
            return cleanupEnabled;
        }

        public void setCleanupEnabled(boolean cleanupEnabled) {
            this.cleanupEnabled = cleanupEnabled;
        }

        public long getIdlePoolCloseMinutes() {
            return idlePoolCloseMinutes;
        }

        public void setIdlePoolCloseMinutes(long idlePoolCloseMinutes) {
            this.idlePoolCloseMinutes = idlePoolCloseMinutes;
        }

        public long getCleanupIntervalMinutes() {
            return cleanupIntervalMinutes;
        }

        public void setCleanupIntervalMinutes(long cleanupIntervalMinutes) {
            this.cleanupIntervalMinutes = cleanupIntervalMinutes;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public long getIdleTimeoutMs() {
            return idleTimeoutMs;
        }

        public void setIdleTimeoutMs(long idleTimeoutMs) {
            this.idleTimeoutMs = idleTimeoutMs;
        }

        public long getMaxLifetimeMs() {
            return maxLifetimeMs;
        }

        public void setMaxLifetimeMs(long maxLifetimeMs) {
            this.maxLifetimeMs = maxLifetimeMs;
        }

        public long getLeaseDrainTimeoutMs() {
            return leaseDrainTimeoutMs;
        }

        public void setLeaseDrainTimeoutMs(long leaseDrainTimeoutMs) {
            this.leaseDrainTimeoutMs = leaseDrainTimeoutMs;
        }
    }
}
