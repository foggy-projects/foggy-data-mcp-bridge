package com.foggyframework.dataset.mcp.experience;

import java.util.Optional;

public interface ExperienceRecipeArtifactTrustStore {
    Optional<ExperienceRecipeArtifactTrustKey> findByKeyId(String keyId);
}
