package com.foggyframework.dataset.mcp.experience;

public class ExperienceRecipeRegistryMutationRequest {
    private ExperienceRecipeRegistryOperation operation;
    private String registryKey;
    private String recipeId;
    private String recipeVersion;
    private String canonicalRecipeId;
    private String title;
    private String businessType;
    private String route;
    private String namespaceScope;
    private String tenantScope;
    private String permissionTags;
    private String ownerRole;
    private String actorRole;
    private String idempotencyKey;
    private ExperienceRecipeStatus expectedFromStatus;
    private Boolean expectedFromActiveForDiscovery;
    private Long expectedRecordVersion;
    private ExperienceRecipeGovernanceEvidence governanceEvidence = new ExperienceRecipeGovernanceEvidence();
    private ExperienceRecipeFailureStage simulateFailureStage = ExperienceRecipeFailureStage.NONE;
    private String reason;

    public ExperienceRecipeRegistryOperation getOperation() {
        return operation;
    }

    public void setOperation(ExperienceRecipeRegistryOperation operation) {
        this.operation = operation;
    }

    public String getRegistryKey() {
        return registryKey;
    }

    public void setRegistryKey(String registryKey) {
        this.registryKey = registryKey;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(String recipeId) {
        this.recipeId = recipeId;
    }

    public String getRecipeVersion() {
        return recipeVersion;
    }

    public void setRecipeVersion(String recipeVersion) {
        this.recipeVersion = recipeVersion;
    }

    public String getCanonicalRecipeId() {
        return canonicalRecipeId;
    }

    public void setCanonicalRecipeId(String canonicalRecipeId) {
        this.canonicalRecipeId = canonicalRecipeId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getNamespaceScope() {
        return namespaceScope;
    }

    public void setNamespaceScope(String namespaceScope) {
        this.namespaceScope = namespaceScope;
    }

    public String getTenantScope() {
        return tenantScope;
    }

    public void setTenantScope(String tenantScope) {
        this.tenantScope = tenantScope;
    }

    public String getPermissionTags() {
        return permissionTags;
    }

    public void setPermissionTags(String permissionTags) {
        this.permissionTags = permissionTags;
    }

    public String getOwnerRole() {
        return ownerRole;
    }

    public void setOwnerRole(String ownerRole) {
        this.ownerRole = ownerRole;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public ExperienceRecipeStatus getExpectedFromStatus() {
        return expectedFromStatus;
    }

    public void setExpectedFromStatus(ExperienceRecipeStatus expectedFromStatus) {
        this.expectedFromStatus = expectedFromStatus;
    }

    public Boolean getExpectedFromActiveForDiscovery() {
        return expectedFromActiveForDiscovery;
    }

    public void setExpectedFromActiveForDiscovery(Boolean expectedFromActiveForDiscovery) {
        this.expectedFromActiveForDiscovery = expectedFromActiveForDiscovery;
    }

    public Long getExpectedRecordVersion() {
        return expectedRecordVersion;
    }

    public void setExpectedRecordVersion(Long expectedRecordVersion) {
        this.expectedRecordVersion = expectedRecordVersion;
    }

    public ExperienceRecipeGovernanceEvidence getGovernanceEvidence() {
        return governanceEvidence;
    }

    public void setGovernanceEvidence(ExperienceRecipeGovernanceEvidence governanceEvidence) {
        this.governanceEvidence = governanceEvidence == null
                ? new ExperienceRecipeGovernanceEvidence()
                : governanceEvidence;
    }

    public ExperienceRecipeFailureStage getSimulateFailureStage() {
        return simulateFailureStage;
    }

    public void setSimulateFailureStage(ExperienceRecipeFailureStage simulateFailureStage) {
        this.simulateFailureStage = simulateFailureStage == null
                ? ExperienceRecipeFailureStage.NONE
                : simulateFailureStage;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
