package com.foggyframework.dataset.mcp.experience;

import java.net.URI;
import java.util.List;
import java.util.Map;

class ExperienceRecipeArtifactUriPolicy {
    private static final List<String> PLACEHOLDERS = List.of(
            "{namespace}",
            "{tenant}",
            "{registryKey}",
            "{canonicalRecipeId}",
            "{version}",
            "{owner}",
            "{artifactType}");

    private final boolean enabled;
    private final List<String> allowedUriPrefixes;

    ExperienceRecipeArtifactUriPolicy(ExperienceRecipeRegistryProperties properties) {
        ExperienceRecipeRegistryProperties.ArtifactUriPolicyProperties policy =
                properties == null
                        ? new ExperienceRecipeRegistryProperties.ArtifactUriPolicyProperties()
                        : properties.getArtifactUriPolicy();
        this.enabled = policy.isEnabled();
        this.allowedUriPrefixes = policy.getAllowedUriPrefixes() == null
                ? List.of()
                : policy.getAllowedUriPrefixes().stream()
                        .filter(prefix -> prefix != null && !prefix.isBlank())
                        .map(String::trim)
                        .toList();
    }

    boolean enabled() {
        return enabled;
    }

    ExperienceRecipeArtifactVerificationResult validate(
            List<ExperienceRecipeEvidenceArtifact> artifacts,
            ExperienceRecipeArtifactSignatureContext context) {
        if (!enabled) {
            return ExperienceRecipeArtifactVerificationResult.passed();
        }
        if (allowedUriPrefixes.isEmpty()) {
            return ExperienceRecipeArtifactVerificationResult.failed(
                    "publish_validated blocked because artifact URI policy has no allowed prefixes");
        }
        for (ExperienceRecipeEvidenceArtifact artifact : artifacts) {
            if (artifact == null || !artifact.validForPublishGate()) {
                continue;
            }
            if (!matchesAnyPrefix(artifact, context)) {
                return ExperienceRecipeArtifactVerificationResult.failed(
                        "publish_validated blocked because evidence artifact URI is not bound to recipe context: "
                                + artifact.getArtifactUri());
            }
        }
        return ExperienceRecipeArtifactVerificationResult.passed();
    }

    private boolean matchesAnyPrefix(
            ExperienceRecipeEvidenceArtifact artifact,
            ExperienceRecipeArtifactSignatureContext context) {
        if (context == null || !safeUri(artifact.getArtifactUri())) {
            return false;
        }
        String artifactUri = artifact.getArtifactUri().trim();
        Map<String, String> values = Map.of(
                "{namespace}", safeValue(context.namespace()),
                "{tenant}", safeValue(context.tenantId()),
                "{registryKey}", safeValue(context.registryKey()),
                "{canonicalRecipeId}", safeValue(context.canonicalRecipeId()),
                "{version}", safeValue(context.recipeVersion()),
                "{owner}", safeValue(context.ownerId()),
                "{artifactType}", safeValue(artifact.getArtifactType()));
        if (values.values().stream().anyMatch(String::isBlank)) {
            return false;
        }
        for (String prefix : allowedUriPrefixes) {
            String expanded = expand(prefix, values);
            if (containsUnresolvedPlaceholder(expanded)) {
                return false;
            }
            if (artifactUri.startsWith(expanded)) {
                return true;
            }
        }
        return false;
    }

    private static boolean safeUri(String artifactUri) {
        if (artifactUri == null || artifactUri.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(artifactUri.trim());
            String rawPath = uri.getRawPath();
            return rawPath == null || rawPath.equals(uri.normalize().getRawPath());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String expand(String prefix, Map<String, String> values) {
        String expanded = prefix;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            expanded = expanded.replace(entry.getKey(), entry.getValue());
        }
        return expanded;
    }

    private static boolean containsUnresolvedPlaceholder(String value) {
        return PLACEHOLDERS.stream().anyMatch(value::contains);
    }

    private static String safeValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
            return "";
        }
        return trimmed;
    }
}
