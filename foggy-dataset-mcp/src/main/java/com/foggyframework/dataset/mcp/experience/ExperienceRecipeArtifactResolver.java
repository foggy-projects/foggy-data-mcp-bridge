package com.foggyframework.dataset.mcp.experience;

import java.util.Optional;

public interface ExperienceRecipeArtifactResolver {
    Optional<byte[]> resolve(ExperienceRecipeEvidenceArtifact artifact);

    default Optional<ExperienceRecipeArtifactResolution> resolveArtifact(
            ExperienceRecipeEvidenceArtifact artifact) {
        return resolve(artifact).map(ExperienceRecipeArtifactResolution::contentOnly);
    }
}
