package com.foggyframework.dataset.mcp.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties(prefix = "foggy.auth.jwt")
public class JwtMcpAuthProperties {

    private String issuerUri;
    private String jwkSetUri;
    private List<String> audiences = new ArrayList<>();
    private Set<String> allowedAlgorithms = new LinkedHashSet<>(Set.of("RS256"));
    private Duration clockSkew = Duration.ofSeconds(60);
    private boolean allowInsecureHttp;
    private String subjectClaim = "sub";
    private String tenantClaim = "tenant_id";
    private String departmentClaim = "dept_id";
    private String rolesClaim = "roles";
    private String scopesClaim = "scope";
    private boolean requireTenant;
    private Map<String, Set<String>> roleMappings = new LinkedHashMap<>();

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public List<String> getAudiences() {
        return audiences;
    }

    public void setAudiences(List<String> audiences) {
        this.audiences = audiences == null ? new ArrayList<>() : new ArrayList<>(audiences);
    }

    public Set<String> getAllowedAlgorithms() {
        return allowedAlgorithms;
    }

    public void setAllowedAlgorithms(Set<String> allowedAlgorithms) {
        this.allowedAlgorithms = allowedAlgorithms == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(allowedAlgorithms);
    }

    public Duration getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(Duration clockSkew) {
        this.clockSkew = clockSkew;
    }

    public boolean isAllowInsecureHttp() {
        return allowInsecureHttp;
    }

    public void setAllowInsecureHttp(boolean allowInsecureHttp) {
        this.allowInsecureHttp = allowInsecureHttp;
    }

    public String getSubjectClaim() {
        return subjectClaim;
    }

    public void setSubjectClaim(String subjectClaim) {
        this.subjectClaim = subjectClaim;
    }

    public String getTenantClaim() {
        return tenantClaim;
    }

    public void setTenantClaim(String tenantClaim) {
        this.tenantClaim = tenantClaim;
    }

    public String getDepartmentClaim() {
        return departmentClaim;
    }

    public void setDepartmentClaim(String departmentClaim) {
        this.departmentClaim = departmentClaim;
    }

    public String getRolesClaim() {
        return rolesClaim;
    }

    public void setRolesClaim(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    public String getScopesClaim() {
        return scopesClaim;
    }

    public void setScopesClaim(String scopesClaim) {
        this.scopesClaim = scopesClaim;
    }

    public boolean isRequireTenant() {
        return requireTenant;
    }

    public void setRequireTenant(boolean requireTenant) {
        this.requireTenant = requireTenant;
    }

    public Map<String, Set<String>> getRoleMappings() {
        return roleMappings;
    }

    public void setRoleMappings(Map<String, Set<String>> roleMappings) {
        this.roleMappings = roleMappings == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(roleMappings);
    }
}