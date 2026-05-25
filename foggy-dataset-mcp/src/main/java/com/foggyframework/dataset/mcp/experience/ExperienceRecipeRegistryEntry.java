package com.foggyframework.dataset.mcp.experience;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExperienceRecipeRegistryEntry {
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
    private ExperienceRecipeStatus status = ExperienceRecipeStatus.NONE;
    private boolean activeForDiscovery;
    private String ownerRole;
    private Long recordVersion;
    private Instant createdAt;
    private Instant updatedAt;

    public ExperienceRecipeRegistryEntry copy() {
        ExperienceRecipeRegistryEntry copy = new ExperienceRecipeRegistryEntry();
        copy.registryKey = registryKey;
        copy.recipeId = recipeId;
        copy.recipeVersion = recipeVersion;
        copy.canonicalRecipeId = canonicalRecipeId;
        copy.title = title;
        copy.businessType = businessType;
        copy.route = route;
        copy.namespaceScope = namespaceScope;
        copy.tenantScope = tenantScope;
        copy.permissionTags = permissionTags;
        copy.status = status;
        copy.activeForDiscovery = activeForDiscovery;
        copy.ownerRole = ownerRole;
        copy.recordVersion = recordVersion;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        return copy;
    }

    public boolean discoverable() {
        return status == ExperienceRecipeStatus.VALIDATED && activeForDiscovery;
    }

    public Map<String, Object> toResponseMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("registryKey", registryKey);
        map.put("recipeId", recipeId);
        map.put("recipeVersion", recipeVersion);
        map.put("canonicalRecipeId", canonicalRecipeId);
        map.put("title", title);
        map.put("businessType", businessType);
        map.put("route", route);
        map.put("namespaceScope", toList(namespaceScope));
        map.put("tenantScope", toList(tenantScope));
        map.put("permissionTags", toList(permissionTags));
        map.put("status", status.wireValue());
        map.put("activeForDiscovery", activeForDiscovery);
        map.put("discoverable", discoverable());
        map.put("ownerRole", ownerRole);
        map.put("recordVersion", recordVersion);
        return map;
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

    public String getOwnerRole() {
        return ownerRole;
    }

    public void setOwnerRole(String ownerRole) {
        this.ownerRole = ownerRole;
    }

    public Long getRecordVersion() {
        return recordVersion;
    }

    public void setRecordVersion(Long recordVersion) {
        this.recordVersion = recordVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    private static List<String> toList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
