package com.foggyframework.dataset.model.semantic.permission;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, validated action decision consumed by all permission layers.
 */
public final class PermissionDecision {

    private final boolean allow;
    private final Map<String, Object> attributes;
    private final List<PermissionPredicate> rowPredicates;
    private final String decisionId;
    private final String policyVersion;
    private final Instant expiresAt;
    private final String providerFingerprint;
    private final boolean publicDecision;

    public PermissionDecision(
            boolean allow,
            Map<String, Object> attributes,
            List<PermissionPredicate> rowPredicates,
            String decisionId,
            String policyVersion,
            Instant expiresAt,
            String providerFingerprint,
            boolean publicDecision
    ) {
        this.allow = allow;
        this.attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        this.rowPredicates = rowPredicates == null ? List.of() : List.copyOf(rowPredicates);
        this.decisionId = normalize(decisionId);
        this.policyVersion = normalize(policyVersion);
        this.expiresAt = expiresAt;
        this.providerFingerprint = normalize(providerFingerprint);
        this.publicDecision = publicDecision;
    }

    public static PermissionDecision publicAllow() {
        return new PermissionDecision(true, Map.of(), List.of(), "PUBLIC",
                "PUBLIC", null, "PUBLIC", true);
    }

    public boolean isAllow() {
        return allow;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public List<PermissionPredicate> getRowPredicates() {
        return rowPredicates;
    }

    public String getDecisionId() {
        return decisionId;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getProviderFingerprint() {
        return providerFingerprint;
    }

    public boolean isPublicDecision() {
        return publicDecision;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(Objects.requireNonNull(now, "now"));
    }

    public boolean hasUnprovableRowPredicate() {
        return rowPredicates.stream().anyMatch(predicate -> !predicate.isProvable());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
