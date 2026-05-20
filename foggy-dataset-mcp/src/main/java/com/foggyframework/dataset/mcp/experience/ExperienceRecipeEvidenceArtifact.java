package com.foggyframework.dataset.mcp.experience;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class ExperienceRecipeEvidenceArtifact {
    private String artifactId;
    private String artifactType;
    private String artifactUri;
    private String artifactHash;
    private String signedBy;
    private String signedAt;

    public static ExperienceRecipeEvidenceArtifact of(
            String artifactType,
            String artifactUri,
            String artifactHash,
            String signedBy) {
        ExperienceRecipeEvidenceArtifact artifact = new ExperienceRecipeEvidenceArtifact();
        artifact.setArtifactId(artifactType + ":artifact");
        artifact.setArtifactType(artifactType);
        artifact.setArtifactUri(artifactUri);
        artifact.setArtifactHash(artifactHash);
        artifact.setSignedBy(signedBy);
        return artifact;
    }

    public static ExperienceRecipeEvidenceArtifact fromMap(Map<?, ?> map) {
        ExperienceRecipeEvidenceArtifact artifact = new ExperienceRecipeEvidenceArtifact();
        artifact.setArtifactId(stringValue(map.get("artifactId")));
        artifact.setArtifactType(stringValue(map.get("artifactType")));
        artifact.setArtifactUri(stringValue(map.get("artifactUri")));
        artifact.setArtifactHash(stringValue(map.get("artifactHash")));
        artifact.setSignedBy(stringValue(map.get("signedBy")));
        artifact.setSignedAt(stringValue(map.get("signedAt")));
        return artifact;
    }

    public ExperienceRecipeEvidenceArtifact copy() {
        ExperienceRecipeEvidenceArtifact copy = new ExperienceRecipeEvidenceArtifact();
        copy.artifactId = artifactId;
        copy.artifactType = artifactType;
        copy.artifactUri = artifactUri;
        copy.artifactHash = artifactHash;
        copy.signedBy = signedBy;
        copy.signedAt = signedAt;
        return copy;
    }

    public boolean validForPublishGate() {
        return hasText(artifactType) && hasText(artifactUri) && hasText(artifactHash);
    }

    public String normalizedType() {
        return artifactType == null ? "" : artifactType.trim().toLowerCase(Locale.ROOT);
    }

    public Map<String, Object> toResponseMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("artifactId", artifactId);
        map.put("artifactType", artifactType);
        map.put("artifactUri", artifactUri);
        map.put("artifactHash", artifactHash);
        map.put("signedBy", signedBy);
        map.put("signedAt", signedAt);
        return map;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public String getArtifactUri() {
        return artifactUri;
    }

    public void setArtifactUri(String artifactUri) {
        this.artifactUri = artifactUri;
    }

    public String getArtifactHash() {
        return artifactHash;
    }

    public void setArtifactHash(String artifactHash) {
        this.artifactHash = artifactHash;
    }

    public String getSignedBy() {
        return signedBy;
    }

    public void setSignedBy(String signedBy) {
        this.signedBy = signedBy;
    }

    public String getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(String signedAt) {
        this.signedAt = signedAt;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
