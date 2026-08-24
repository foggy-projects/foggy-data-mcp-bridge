package com.foggyframework.analytics.console.config;

import com.foggyframework.analytics.console.security.AnalyticsConsoleRole;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Product configuration; credentials are never projected through Console APIs. */
@ConfigurationProperties(prefix = "foggy.analytics-console")
public class AnalyticsConsoleProperties {

    private boolean enabled;
    private String securityMode = "host-managed";
    private String catalogPath = ".foggy-runtime/analytics-console/catalog.json";
    private long maxDefinitionBytes = 1_048_576;
    private DevSubject devSubject = new DevSubject();
    private Fap fap = new Fap();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecurityMode() {
        return securityMode;
    }

    public void setSecurityMode(String securityMode) {
        this.securityMode = securityMode;
    }

    public String getCatalogPath() {
        return catalogPath;
    }

    public void setCatalogPath(String catalogPath) {
        this.catalogPath = catalogPath;
    }

    public long getMaxDefinitionBytes() {
        return maxDefinitionBytes;
    }

    public void setMaxDefinitionBytes(long maxDefinitionBytes) {
        this.maxDefinitionBytes = maxDefinitionBytes;
    }

    public DevSubject getDevSubject() {
        return devSubject;
    }

    public void setDevSubject(DevSubject devSubject) {
        this.devSubject = devSubject;
    }

    public Fap getFap() {
        return fap;
    }

    public void setFap(Fap fap) {
        this.fap = fap;
    }

    public static final class DevSubject {
        private String subjectRef = "local-admin";
        private String displayName = "Local Analytics Admin";
        private List<AnalyticsConsoleRole> roles = new ArrayList<>();
        private String authorityProvider = "console";
        private String authorityReference = "local-dev-only";

        public String getSubjectRef() { return subjectRef; }
        public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public List<AnalyticsConsoleRole> getRoles() { return roles; }
        public void setRoles(List<AnalyticsConsoleRole> roles) {
            this.roles = new ArrayList<>(roles);
        }
        public String getAuthorityProvider() { return authorityProvider; }
        public void setAuthorityProvider(String authorityProvider) {
            this.authorityProvider = authorityProvider;
        }
        public String getAuthorityReference() { return authorityReference; }
        public void setAuthorityReference(String authorityReference) {
            this.authorityReference = authorityReference;
        }
    }

    public static final class Fap {
        private boolean enabled;
        private String baseUrl;
        private String providerRef;
        private String skillName = "analytics-design-guidance";
        private String capabilityName = "analytics.design-read";
        private String callbackCapabilityId = "analytics.design-read";
        private int callbackCapabilityRevision = 1;
        private String callbackAuthorization;
        private int timeoutSeconds = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getProviderRef() { return providerRef; }
        public void setProviderRef(String providerRef) { this.providerRef = providerRef; }
        public String getSkillName() { return skillName; }
        public void setSkillName(String skillName) { this.skillName = skillName; }
        public String getCapabilityName() { return capabilityName; }
        public void setCapabilityName(String capabilityName) {
            this.capabilityName = capabilityName;
        }
        public String getCallbackCapabilityId() { return callbackCapabilityId; }
        public void setCallbackCapabilityId(String callbackCapabilityId) {
            this.callbackCapabilityId = callbackCapabilityId;
        }
        public int getCallbackCapabilityRevision() { return callbackCapabilityRevision; }
        public void setCallbackCapabilityRevision(int callbackCapabilityRevision) {
            this.callbackCapabilityRevision = callbackCapabilityRevision;
        }
        public String getCallbackAuthorization() { return callbackAuthorization; }
        public void setCallbackAuthorization(String callbackAuthorization) {
            this.callbackAuthorization = callbackAuthorization;
        }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}
