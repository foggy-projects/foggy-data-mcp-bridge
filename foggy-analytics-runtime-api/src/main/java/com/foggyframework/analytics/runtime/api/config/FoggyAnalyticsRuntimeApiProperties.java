package com.foggyframework.analytics.runtime.api.config;

import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleSourceState;
import com.foggyframework.analytics.definition.core.AnalyticsBundleRegistration;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Host-owned Analytics Runtime API and trusted Bundle registration settings. */
@ConfigurationProperties(prefix = "foggy.analytics.runtime-api")
public class FoggyAnalyticsRuntimeApiProperties {

    private boolean enabled;
    private String runtimeApiVersion = "foggy-analytics-runtime-api/v1";
    private String schemaVersion = "analytics-runtime/v1";
    private String securityMode = "host-managed";
    private int maxRows = 1_000;
    private List<Bundle> bundles = new ArrayList<>();

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
        this.runtimeApiVersion = requireValue("runtimeApiVersion", runtimeApiVersion);
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = requireValue("schemaVersion", schemaVersion);
    }

    public String getSecurityMode() {
        return securityMode;
    }

    public void setSecurityMode(String securityMode) {
        this.securityMode = requireValue("securityMode", securityMode);
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        if (maxRows <= 0) {
            throw new IllegalArgumentException("maxRows must be positive");
        }
        this.maxRows = maxRows;
    }

    public List<Bundle> getBundles() {
        return bundles;
    }

    public void setBundles(List<Bundle> bundles) {
        this.bundles = new ArrayList<>(Objects.requireNonNull(bundles, "bundles"));
    }

    public List<AnalyticsBundleRegistration> registrations() {
        List<AnalyticsBundleRegistration> registrations = new ArrayList<>();
        Set<AnalyticsBundleRef> identities = new HashSet<>();
        for (Bundle configured : bundles) {
            Objects.requireNonNull(configured, "bundle registration");
            AnalyticsBundleRef bundleRef = new AnalyticsBundleRef(
                    requireValue("bundle.ref", configured.getRef()));
            if (!identities.add(bundleRef)) {
                throw new IllegalArgumentException(
                        "Analytics Bundle registration is duplicated: " + bundleRef.value());
            }
            registrations.add(new AnalyticsBundleRegistration(
                    bundleRef,
                    Path.of(requireValue("bundle.path", configured.getPath())),
                    Objects.requireNonNull(configured.getSourceState(), "bundle.sourceState")));
        }
        return List.copyOf(registrations);
    }

    private static String requireValue(String field, String value) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }

    public static class Bundle {

        private String ref;
        private String path;
        private AnalyticsBundleSourceState sourceState = AnalyticsBundleSourceState.CONFIGURED;

        public String getRef() {
            return ref;
        }

        public void setRef(String ref) {
            this.ref = ref;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public AnalyticsBundleSourceState getSourceState() {
            return sourceState;
        }

        public void setSourceState(AnalyticsBundleSourceState sourceState) {
            this.sourceState = sourceState;
        }
    }
}
