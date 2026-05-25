package com.foggyframework.dataset.mcp.experience;

public record ExperienceRecipeArtifactSignatureContext(
        String namespace,
        String tenantId,
        String registryKey,
        String canonicalRecipeId,
        String recipeVersion,
        String ownerId) {
}
