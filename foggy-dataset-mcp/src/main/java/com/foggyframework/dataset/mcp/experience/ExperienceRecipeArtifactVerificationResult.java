package com.foggyframework.dataset.mcp.experience;

public record ExperienceRecipeArtifactVerificationResult(boolean verified, String reason) {
    public static ExperienceRecipeArtifactVerificationResult passed() {
        return new ExperienceRecipeArtifactVerificationResult(true, null);
    }

    public static ExperienceRecipeArtifactVerificationResult failed(String reason) {
        return new ExperienceRecipeArtifactVerificationResult(false, reason);
    }
}
