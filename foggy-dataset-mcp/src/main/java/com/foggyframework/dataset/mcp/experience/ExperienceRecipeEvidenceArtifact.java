package com.foggyframework.dataset.mcp.experience;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ExperienceRecipeEvidenceArtifact {
    private static final Pattern SHA_256_HASH = Pattern.compile("^sha256:[A-Fa-f0-9]{64}$");
    private static final Set<String> ALLOWED_URI_SCHEMES = Set.of("foggy", "https", "s3", "oss");

    private String artifactId;
    private String artifactType;
    private String artifactUri;
    private String artifactHash;
    private String artifactSignature;
    private String signedBy;
    private String signedAt;
    private String objectVersion;
    private String objectEtag;
    private Map<String, String> objectMetadata = new LinkedHashMap<>();

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
        artifact.setArtifactSignature(stringValue(map.get("artifactSignature")));
        artifact.setSignedBy(stringValue(map.get("signedBy")));
        artifact.setSignedAt(stringValue(map.get("signedAt")));
        artifact.setObjectVersion(stringValue(map.get("objectVersion")));
        artifact.setObjectEtag(stringValue(map.get("objectEtag")));
        artifact.setObjectMetadata(stringMapValue(map.get("objectMetadata")));
        return artifact;
    }

    public ExperienceRecipeEvidenceArtifact copy() {
        ExperienceRecipeEvidenceArtifact copy = new ExperienceRecipeEvidenceArtifact();
        copy.artifactId = artifactId;
        copy.artifactType = artifactType;
        copy.artifactUri = artifactUri;
        copy.artifactHash = artifactHash;
        copy.artifactSignature = artifactSignature;
        copy.signedBy = signedBy;
        copy.signedAt = signedAt;
        copy.objectVersion = objectVersion;
        copy.objectEtag = objectEtag;
        copy.objectMetadata = new LinkedHashMap<>(objectMetadata);
        return copy;
    }

    public boolean validForPublishGate() {
        return hasText(artifactType) && hasValidArtifactUri() && hasValidArtifactHash();
    }

    public boolean hasValidArtifactHash() {
        return hasText(artifactHash) && SHA_256_HASH.matcher(artifactHash.trim()).matches();
    }

    public boolean hasValidArtifactUri() {
        if (!hasText(artifactUri)) {
            return false;
        }
        try {
            URI uri = URI.create(artifactUri.trim());
            String scheme = uri.getScheme();
            return hasText(scheme)
                    && ALLOWED_URI_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))
                    && hasText(uri.getRawAuthority());
        } catch (IllegalArgumentException ex) {
            return false;
        }
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
        map.put("artifactSignature", artifactSignature);
        map.put("signedBy", signedBy);
        map.put("signedAt", signedAt);
        map.put("objectVersion", objectVersion);
        map.put("objectEtag", objectEtag);
        map.put("objectMetadata", new LinkedHashMap<>(objectMetadata));
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

    public String getArtifactSignature() {
        return artifactSignature;
    }

    public void setArtifactSignature(String artifactSignature) {
        this.artifactSignature = artifactSignature;
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

    public String getObjectVersion() {
        return objectVersion;
    }

    public void setObjectVersion(String objectVersion) {
        this.objectVersion = objectVersion;
    }

    public String getObjectEtag() {
        return objectEtag;
    }

    public void setObjectEtag(String objectEtag) {
        this.objectEtag = objectEtag;
    }

    public Map<String, String> getObjectMetadata() {
        return Collections.unmodifiableMap(objectMetadata);
    }

    public void setObjectMetadata(Map<String, String> objectMetadata) {
        if (objectMetadata == null) {
            this.objectMetadata = new LinkedHashMap<>();
            return;
        }
        this.objectMetadata = new LinkedHashMap<>();
        objectMetadata.forEach((key, value) -> {
            if (hasText(key) && hasText(value)) {
                this.objectMetadata.put(key.trim(), value.trim());
            }
        });
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static Map<String, String> stringMapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> {
            String stringKey = stringValue(key);
            String stringMapValue = stringValue(mapValue);
            if (stringKey != null && stringMapValue != null) {
                result.put(stringKey, stringMapValue);
            }
        });
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
