package com.foggyframework.dataset.mcp.experience;

import java.util.LinkedHashSet;
import java.util.Set;

public class ExperienceRecipeSearchRequest {
    private String registryKey;
    private String businessType;
    private String route;
    private String namespace;
    private String tenantId;
    private Set<String> permissionTags = new LinkedHashSet<>();
    private Set<String> ownerRoles = new LinkedHashSet<>();
    private Integer limit;

    public String getRegistryKey() {
        return registryKey;
    }

    public void setRegistryKey(String registryKey) {
        this.registryKey = registryKey;
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

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Set<String> getPermissionTags() {
        return permissionTags;
    }

    public void setPermissionTags(Set<String> permissionTags) {
        this.permissionTags = permissionTags == null ? new LinkedHashSet<>() : new LinkedHashSet<>(permissionTags);
    }

    public Set<String> getOwnerRoles() {
        return ownerRoles;
    }

    public void setOwnerRoles(Set<String> ownerRoles) {
        this.ownerRoles = ownerRoles == null ? new LinkedHashSet<>() : new LinkedHashSet<>(ownerRoles);
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
