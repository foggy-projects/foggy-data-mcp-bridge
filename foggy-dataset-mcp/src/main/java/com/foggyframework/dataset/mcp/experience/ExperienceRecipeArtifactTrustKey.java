package com.foggyframework.dataset.mcp.experience;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record ExperienceRecipeArtifactTrustKey(
        String keyId,
        String algorithm,
        byte[] publicKey,
        Set<String> purposes,
        Set<String> tenantIds,
        Set<String> ownerIds,
        Set<String> artifactTypes,
        Set<String> signedBySubjects,
        Instant validFrom,
        Instant validTo,
        Status status,
        Instant revokedAt) {

    public ExperienceRecipeArtifactTrustKey {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId cannot be blank");
        }
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("algorithm cannot be blank");
        }
        if (publicKey == null || publicKey.length == 0) {
            throw new IllegalArgumentException("publicKey cannot be empty");
        }
        publicKey = publicKey.clone();
        purposes = normalizeSet(purposes);
        tenantIds = normalizeSet(tenantIds);
        ownerIds = normalizeSet(ownerIds);
        artifactTypes = normalizeSet(artifactTypes);
        signedBySubjects = normalizeSet(signedBySubjects);
        status = status == null ? Status.ENABLED : status;
    }

    public byte[] publicKey() {
        return publicKey.clone();
    }

    public enum Status {
        ENABLED,
        DISABLED,
        REVOKED
    }

    private static Set<String> normalizeSet(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        return raw.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
