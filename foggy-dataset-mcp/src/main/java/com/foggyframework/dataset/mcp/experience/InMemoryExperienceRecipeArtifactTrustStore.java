package com.foggyframework.dataset.mcp.experience;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class InMemoryExperienceRecipeArtifactTrustStore implements ExperienceRecipeArtifactTrustStore {
    private final Map<String, ExperienceRecipeArtifactTrustKey> keys;

    public InMemoryExperienceRecipeArtifactTrustStore(Collection<ExperienceRecipeArtifactTrustKey> keys) {
        this.keys = keys == null ? Map.of() : keys.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ExperienceRecipeArtifactTrustKey::keyId,
                        Function.identity()));
    }

    @Override
    public Optional<ExperienceRecipeArtifactTrustKey> findByKeyId(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(keys.get(keyId.trim()));
    }
}
