package com.foggyframework.dataset.mcp.experience;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExperienceRecipeArtifactResolution {
    private final byte[] content;
    private final String objectVersion;
    private final String objectEtag;
    private final Map<String, String> objectMetadata;

    private ExperienceRecipeArtifactResolution(
            byte[] content,
            String objectVersion,
            String objectEtag,
            Map<String, String> objectMetadata) {
        this.content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
        this.objectVersion = textOrNull(objectVersion);
        this.objectEtag = textOrNull(objectEtag);
        this.objectMetadata = normalizeMetadata(objectMetadata);
    }

    public static ExperienceRecipeArtifactResolution contentOnly(byte[] content) {
        return new ExperienceRecipeArtifactResolution(content, null, null, Map.of());
    }

    public static ExperienceRecipeArtifactResolution of(
            byte[] content,
            String objectVersion,
            String objectEtag,
            Map<String, String> objectMetadata) {
        return new ExperienceRecipeArtifactResolution(content, objectVersion, objectEtag, objectMetadata);
    }

    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    public String objectVersion() {
        return objectVersion;
    }

    public String objectEtag() {
        return objectEtag;
    }

    public Map<String, String> objectMetadata() {
        return Map.copyOf(objectMetadata);
    }

    public ExperienceRecipeEvidenceArtifact toTrustedObjectArtifact(ExperienceRecipeEvidenceArtifact artifact) {
        ExperienceRecipeEvidenceArtifact trusted = artifact == null
                ? new ExperienceRecipeEvidenceArtifact()
                : artifact.copy();
        trusted.setObjectVersion(objectVersion);
        trusted.setObjectEtag(objectEtag);
        trusted.setObjectMetadata(objectMetadata);
        return trusted;
    }

    private static Map<String, String> normalizeMetadata(Map<String, String> objectMetadata) {
        if (objectMetadata == null || objectMetadata.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        objectMetadata.forEach((key, value) -> {
            String normalizedKey = textOrNull(key);
            String normalizedValue = textOrNull(value);
            if (normalizedKey != null && normalizedValue != null) {
                normalized.put(normalizedKey, normalizedValue);
            }
        });
        return Map.copyOf(normalized);
    }

    private static String textOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
