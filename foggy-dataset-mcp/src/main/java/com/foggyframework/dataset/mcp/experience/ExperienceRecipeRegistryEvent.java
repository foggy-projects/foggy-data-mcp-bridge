package com.foggyframework.dataset.mcp.experience;

import java.time.Instant;
import java.util.UUID;

public class ExperienceRecipeRegistryEvent {
    private String eventId = UUID.randomUUID().toString();
    private String registryKey;
    private String idempotencyKey;
    private ExperienceRecipeRegistryOperation operation;
    private String actorRole;
    private ExperienceRecipeApiResult apiResult;
    private ExperienceRecipeFailureStage failureStage = ExperienceRecipeFailureStage.NONE;
    private ExperienceRecipeStatus fromStatus = ExperienceRecipeStatus.NONE;
    private ExperienceRecipeStatus toStatus = ExperienceRecipeStatus.NONE;
    private boolean fromActiveForDiscovery;
    private boolean toActiveForDiscovery;
    private ExperienceRecipeStatus responseStatus = ExperienceRecipeStatus.NONE;
    private boolean responseActiveForDiscovery;
    private boolean responseDiscoverable;
    private String reason;
    private Instant createdAt = Instant.now();

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getRegistryKey() {
        return registryKey;
    }

    public void setRegistryKey(String registryKey) {
        this.registryKey = registryKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public ExperienceRecipeRegistryOperation getOperation() {
        return operation;
    }

    public void setOperation(ExperienceRecipeRegistryOperation operation) {
        this.operation = operation;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public ExperienceRecipeApiResult getApiResult() {
        return apiResult;
    }

    public void setApiResult(ExperienceRecipeApiResult apiResult) {
        this.apiResult = apiResult;
    }

    public ExperienceRecipeFailureStage getFailureStage() {
        return failureStage;
    }

    public void setFailureStage(ExperienceRecipeFailureStage failureStage) {
        this.failureStage = failureStage == null ? ExperienceRecipeFailureStage.NONE : failureStage;
    }

    public ExperienceRecipeStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(ExperienceRecipeStatus fromStatus) {
        this.fromStatus = fromStatus == null ? ExperienceRecipeStatus.NONE : fromStatus;
    }

    public ExperienceRecipeStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(ExperienceRecipeStatus toStatus) {
        this.toStatus = toStatus == null ? ExperienceRecipeStatus.NONE : toStatus;
    }

    public boolean isFromActiveForDiscovery() {
        return fromActiveForDiscovery;
    }

    public void setFromActiveForDiscovery(boolean fromActiveForDiscovery) {
        this.fromActiveForDiscovery = fromActiveForDiscovery;
    }

    public boolean isToActiveForDiscovery() {
        return toActiveForDiscovery;
    }

    public void setToActiveForDiscovery(boolean toActiveForDiscovery) {
        this.toActiveForDiscovery = toActiveForDiscovery;
    }

    public ExperienceRecipeStatus getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(ExperienceRecipeStatus responseStatus) {
        this.responseStatus = responseStatus == null ? ExperienceRecipeStatus.NONE : responseStatus;
    }

    public boolean isResponseActiveForDiscovery() {
        return responseActiveForDiscovery;
    }

    public void setResponseActiveForDiscovery(boolean responseActiveForDiscovery) {
        this.responseActiveForDiscovery = responseActiveForDiscovery;
    }

    public boolean isResponseDiscoverable() {
        return responseDiscoverable;
    }

    public void setResponseDiscoverable(boolean responseDiscoverable) {
        this.responseDiscoverable = responseDiscoverable;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
