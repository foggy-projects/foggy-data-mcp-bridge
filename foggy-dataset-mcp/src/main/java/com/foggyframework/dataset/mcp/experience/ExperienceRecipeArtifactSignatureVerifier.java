package com.foggyframework.dataset.mcp.experience;

public interface ExperienceRecipeArtifactSignatureVerifier {
    ExperienceRecipeArtifactVerificationResult verify(
            ExperienceRecipeEvidenceArtifact artifact,
            byte[] content,
            ExperienceRecipeArtifactSignatureContext context);
}
