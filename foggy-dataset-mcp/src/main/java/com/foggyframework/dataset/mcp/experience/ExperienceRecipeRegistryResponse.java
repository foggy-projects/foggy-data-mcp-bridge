package com.foggyframework.dataset.mcp.experience;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExperienceRecipeRegistryResponse {
    private ExperienceRecipeApiResult apiResult;
    private String registryKey;
    private ExperienceRecipeStatus status = ExperienceRecipeStatus.NONE;
    private boolean activeForDiscovery;
    private boolean discoverable;
    private Long recordVersion;
    private List<ExperienceRecipeRegistryEntry> recipes = new ArrayList<>();
    private String governanceDecision = "none";
    private boolean conflictExposed;
    private List<String> candidateCanonicalGroups = new ArrayList<>();
    private Map<String, Integer> governanceFilteredCounts = new LinkedHashMap<>();
    private String failureStage = ExperienceRecipeFailureStage.NONE.wireValue();
    private String message;

    public static ExperienceRecipeRegistryResponse fromEntry(
            ExperienceRecipeApiResult apiResult,
            ExperienceRecipeRegistryEntry entry,
            ExperienceRecipeFailureStage failureStage,
            String message) {
        ExperienceRecipeRegistryResponse response = new ExperienceRecipeRegistryResponse();
        response.apiResult = apiResult;
        response.registryKey = entry == null ? null : entry.getRegistryKey();
        response.status = entry == null ? ExperienceRecipeStatus.NONE : entry.getStatus();
        response.activeForDiscovery = entry != null && entry.isActiveForDiscovery();
        response.discoverable = entry != null && entry.discoverable();
        response.recordVersion = entry == null ? null : entry.getRecordVersion();
        response.failureStage = (failureStage == null ? ExperienceRecipeFailureStage.NONE : failureStage).wireValue();
        response.message = message;
        if (entry != null && entry.discoverable()) {
            response.recipes = List.of(entry.copy());
        }
        return response;
    }

    public static ExperienceRecipeRegistryResponse readOk(List<ExperienceRecipeRegistryEntry> recipes) {
        return readOk(recipes, List.of(), Map.of());
    }

    public static ExperienceRecipeRegistryResponse readOk(
            List<ExperienceRecipeRegistryEntry> recipes,
            List<String> candidateCanonicalGroups,
            Map<String, Integer> governanceFilteredCounts) {
        ExperienceRecipeRegistryResponse response = new ExperienceRecipeRegistryResponse();
        response.apiResult = ExperienceRecipeApiResult.READ_OK;
        response.recipes = recipes == null ? List.of() : recipes.stream()
                .map(ExperienceRecipeRegistryEntry::copy)
                .toList();
        response.candidateCanonicalGroups = candidateCanonicalGroups == null
                ? List.of()
                : List.copyOf(candidateCanonicalGroups);
        response.conflictExposed = response.candidateCanonicalGroups.size() > 1;
        response.governanceDecision = response.conflictExposed
                ? "conflict_exposed"
                : response.recipes.isEmpty() ? "empty" : "selected";
        response.governanceFilteredCounts = governanceFilteredCounts == null
                ? Map.of()
                : new LinkedHashMap<>(governanceFilteredCounts);
        return response;
    }

    public Map<String, Object> toResponseMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("apiResult", apiResult == null ? null : apiResult.name());
        map.put("registryKey", registryKey);
        map.put("status", status == null ? ExperienceRecipeStatus.NONE.wireValue() : status.wireValue());
        map.put("activeForDiscovery", activeForDiscovery);
        map.put("discoverable", discoverable);
        map.put("recordVersion", recordVersion);
        map.put("failureStage", failureStage);
        map.put("message", message);
        map.put("governanceDecision", governanceDecision);
        map.put("conflictExposed", conflictExposed);
        map.put("candidateCanonicalGroups", candidateCanonicalGroups);
        map.put("governanceFilteredCounts", governanceFilteredCounts);
        map.put("returnedRegistryKeys", returnedRegistryKeys());
        map.put("recipes", recipes.stream()
                .map(ExperienceRecipeRegistryEntry::toResponseMap)
                .toList());
        return map;
    }

    public List<String> returnedRegistryKeys() {
        return recipes.stream()
                .map(ExperienceRecipeRegistryEntry::getRegistryKey)
                .toList();
    }

    public ExperienceRecipeApiResult getApiResult() {
        return apiResult;
    }

    public void setApiResult(ExperienceRecipeApiResult apiResult) {
        this.apiResult = apiResult;
    }

    public String getRegistryKey() {
        return registryKey;
    }

    public void setRegistryKey(String registryKey) {
        this.registryKey = registryKey;
    }

    public ExperienceRecipeStatus getStatus() {
        return status;
    }

    public void setStatus(ExperienceRecipeStatus status) {
        this.status = status == null ? ExperienceRecipeStatus.NONE : status;
    }

    public boolean isActiveForDiscovery() {
        return activeForDiscovery;
    }

    public void setActiveForDiscovery(boolean activeForDiscovery) {
        this.activeForDiscovery = activeForDiscovery;
    }

    public boolean isDiscoverable() {
        return discoverable;
    }

    public void setDiscoverable(boolean discoverable) {
        this.discoverable = discoverable;
    }

    public Long getRecordVersion() {
        return recordVersion;
    }

    public void setRecordVersion(Long recordVersion) {
        this.recordVersion = recordVersion;
    }

    public List<ExperienceRecipeRegistryEntry> getRecipes() {
        return recipes;
    }

    public void setRecipes(List<ExperienceRecipeRegistryEntry> recipes) {
        this.recipes = recipes == null ? List.of() : recipes;
    }

    public String getGovernanceDecision() {
        return governanceDecision;
    }

    public void setGovernanceDecision(String governanceDecision) {
        this.governanceDecision = governanceDecision;
    }

    public boolean isConflictExposed() {
        return conflictExposed;
    }

    public void setConflictExposed(boolean conflictExposed) {
        this.conflictExposed = conflictExposed;
    }

    public List<String> getCandidateCanonicalGroups() {
        return candidateCanonicalGroups;
    }

    public void setCandidateCanonicalGroups(List<String> candidateCanonicalGroups) {
        this.candidateCanonicalGroups = candidateCanonicalGroups == null ? List.of() : candidateCanonicalGroups;
    }

    public Map<String, Integer> getGovernanceFilteredCounts() {
        return governanceFilteredCounts;
    }

    public void setGovernanceFilteredCounts(Map<String, Integer> governanceFilteredCounts) {
        this.governanceFilteredCounts = governanceFilteredCounts == null ? Map.of() : governanceFilteredCounts;
    }

    public String getFailureStage() {
        return failureStage;
    }

    public void setFailureStage(String failureStage) {
        this.failureStage = failureStage;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
