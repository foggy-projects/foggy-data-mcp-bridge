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
    private String functionTracePath =
            ".foggy-runtime/analytics-console/function-traces";
    private long maxDefinitionBytes = 1_048_576;
    private DevSubject devSubject = new DevSubject();
    private Fap fap = new Fap();
    private List<QuestionProfile> questionProfiles = new ArrayList<>();

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

    public String getFunctionTracePath() {
        return functionTracePath;
    }

    public void setFunctionTracePath(String functionTracePath) {
        this.functionTracePath = functionTracePath;
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

    public List<QuestionProfile> getQuestionProfiles() {
        return questionProfiles;
    }

    public void setQuestionProfiles(List<QuestionProfile> questionProfiles) {
        this.questionProfiles = questionProfiles == null
                ? new ArrayList<>()
                : new ArrayList<>(questionProfiles);
    }

    /** Server-owned Namespace allowlist entry shown in direct questions. */
    public static final class QuestionProfile {
        private String id;
        private String displayName;
        private String description;
        private String namespace;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
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
        private String questionSkillName = "analytics-question-answering";
        private String questionCapabilityName = "analytics.question-read";
        private String questionCallbackCapabilityId = "analytics.question-read";
        private int questionCallbackCapabilityRevision = 1;
        private String devAuthorization;
        private String devWorkspaceRef;
        private String devModelConfigRef;
        private String devModelVariantId;
        private String devTenantRef = "local-tenant";
        private String devProviderSubjectRef = "local-provider-subject";
        private boolean workspaceFilesEnabled;

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
        public String getQuestionSkillName() { return questionSkillName; }
        public void setQuestionSkillName(String questionSkillName) {
            this.questionSkillName = questionSkillName;
        }
        public String getQuestionCapabilityName() { return questionCapabilityName; }
        public void setQuestionCapabilityName(String questionCapabilityName) {
            this.questionCapabilityName = questionCapabilityName;
        }
        public String getQuestionCallbackCapabilityId() {
            return questionCallbackCapabilityId;
        }
        public void setQuestionCallbackCapabilityId(String value) {
            this.questionCallbackCapabilityId = value;
        }
        public int getQuestionCallbackCapabilityRevision() {
            return questionCallbackCapabilityRevision;
        }
        public void setQuestionCallbackCapabilityRevision(int value) {
            this.questionCallbackCapabilityRevision = value;
        }
        public String getDevAuthorization() { return devAuthorization; }
        public void setDevAuthorization(String value) { this.devAuthorization = value; }
        public String getDevWorkspaceRef() { return devWorkspaceRef; }
        public void setDevWorkspaceRef(String value) { this.devWorkspaceRef = value; }
        public String getDevModelConfigRef() { return devModelConfigRef; }
        public void setDevModelConfigRef(String value) { this.devModelConfigRef = value; }
        public String getDevModelVariantId() { return devModelVariantId; }
        public void setDevModelVariantId(String value) { this.devModelVariantId = value; }
        public String getDevTenantRef() { return devTenantRef; }
        public void setDevTenantRef(String value) { this.devTenantRef = value; }
        public String getDevProviderSubjectRef() { return devProviderSubjectRef; }
        public void setDevProviderSubjectRef(String value) {
            this.devProviderSubjectRef = value;
        }
        public boolean isWorkspaceFilesEnabled() { return workspaceFilesEnabled; }
        public void setWorkspaceFilesEnabled(boolean value) {
            this.workspaceFilesEnabled = value;
        }
    }
}
