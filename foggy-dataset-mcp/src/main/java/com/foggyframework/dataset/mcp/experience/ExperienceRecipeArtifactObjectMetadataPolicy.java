package com.foggyframework.dataset.mcp.experience;

import java.util.List;
import java.util.Map;

class ExperienceRecipeArtifactObjectMetadataPolicy {
    private final boolean enabled;
    private final boolean requireResolvedObjectMetadata;

    ExperienceRecipeArtifactObjectMetadataPolicy(ExperienceRecipeRegistryProperties properties) {
        ExperienceRecipeRegistryProperties.ArtifactObjectMetadataPolicyProperties policy =
                properties == null
                        ? new ExperienceRecipeRegistryProperties.ArtifactObjectMetadataPolicyProperties()
                        : properties.getArtifactObjectMetadataPolicy();
        this.enabled = policy.isEnabled();
        this.requireResolvedObjectMetadata = enabled && policy.isRequireResolvedObjectMetadata();
    }

    boolean enabled() {
        return enabled;
    }

    boolean requireResolvedObjectMetadata() {
        return requireResolvedObjectMetadata;
    }

    ExperienceRecipeArtifactVerificationResult validate(
            List<ExperienceRecipeEvidenceArtifact> artifacts,
            ExperienceRecipeArtifactSignatureContext context) {
        if (!enabled) {
            return ExperienceRecipeArtifactVerificationResult.passed();
        }
        if (context == null) {
            return ExperienceRecipeArtifactVerificationResult.failed(
                    "publish_validated blocked because artifact object metadata context is missing");
        }
        for (ExperienceRecipeEvidenceArtifact artifact : artifacts) {
            if (artifact == null || !artifact.validForPublishGate()) {
                continue;
            }
            ExperienceRecipeArtifactVerificationResult result = validateArtifact(artifact, context);
            if (!result.verified()) {
                return result;
            }
        }
        return ExperienceRecipeArtifactVerificationResult.passed();
    }

    private ExperienceRecipeArtifactVerificationResult validateArtifact(
            ExperienceRecipeEvidenceArtifact artifact,
            ExperienceRecipeArtifactSignatureContext context) {
        if (isBlank(artifact.getObjectVersion()) && isBlank(artifact.getObjectEtag())) {
            return failed(artifact, "object identity is missing");
        }
        Map<String, String> metadata = artifact.getObjectMetadata();
        if (metadata.isEmpty()) {
            return failed(artifact, "object metadata is missing");
        }
        if (!matches(metadata, "namespace", context.namespace())
                || !matches(metadata, "tenant", context.tenantId())
                || !matches(metadata, "owner", context.ownerId())
                || !matches(metadata, "registryKey", context.registryKey())
                || !matches(metadata, "canonicalRecipeId", context.canonicalRecipeId())
                || !matches(metadata, "version", context.recipeVersion())
                || !matches(metadata, "artifactType", artifact.getArtifactType())
                || !matches(metadata, "artifactHash", artifact.getArtifactHash())) {
            return failed(artifact, "object metadata is not bound to recipe context");
        }
        return ExperienceRecipeArtifactVerificationResult.passed();
    }

    private static boolean matches(Map<String, String> metadata, String key, String expected) {
        String actual = metadata.get(key);
        return !isBlank(actual) && !isBlank(expected) && actual.trim().equals(expected.trim());
    }

    private static ExperienceRecipeArtifactVerificationResult failed(
            ExperienceRecipeEvidenceArtifact artifact,
            String reason) {
        return ExperienceRecipeArtifactVerificationResult.failed(
                "publish_validated blocked because evidence artifact "
                        + reason
                        + ": "
                        + artifact.getArtifactUri());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
