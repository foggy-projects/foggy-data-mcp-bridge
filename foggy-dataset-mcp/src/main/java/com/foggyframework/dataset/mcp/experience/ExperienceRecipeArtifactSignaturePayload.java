package com.foggyframework.dataset.mcp.experience;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class ExperienceRecipeArtifactSignaturePayload {
    static final String PURPOSE = "experience_recipe_evidence";
    static final String VERSION = "v1";

    private ExperienceRecipeArtifactSignaturePayload() {
    }

    static byte[] canonicalBytes(
            ExperienceRecipeArtifactSignatureContext context,
            ExperienceRecipeEvidenceArtifact artifact) {
        return canonicalText(context, artifact).getBytes(StandardCharsets.UTF_8);
    }

    static String canonicalText(
            ExperienceRecipeArtifactSignatureContext context,
            ExperienceRecipeEvidenceArtifact artifact) {
        if (context == null) {
            throw new IllegalArgumentException("signature context cannot be null");
        }
        if (artifact == null) {
            throw new IllegalArgumentException("evidence artifact cannot be null");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("purpose", PURPOSE);
        fields.put("payloadVersion", VERSION);
        fields.put("namespace", required("namespace", context.namespace()));
        fields.put("tenantId", required("tenantId", context.tenantId()));
        fields.put("registryKey", required("registryKey", context.registryKey()));
        fields.put("canonicalRecipeId", required("canonicalRecipeId", context.canonicalRecipeId()));
        fields.put("recipeVersion", required("recipeVersion", context.recipeVersion()));
        fields.put("ownerId", required("ownerId", context.ownerId()));
        fields.put("artifactType", required("artifactType", artifact.getArtifactType()));
        fields.put("artifactUri", required("artifactUri", artifact.getArtifactUri()));
        fields.put("artifactHash", required("artifactHash", artifact.getArtifactHash()));
        fields.put("signedBy", required("signedBy", artifact.getSignedBy()));
        fields.put("signedAt", required("signedAt", artifact.getSignedAt()));

        StringBuilder builder = new StringBuilder();
        fields.forEach((key, value) -> builder.append(key).append('=').append(value).append('\n'));
        return builder.toString();
    }

    private static String required(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("signature payload field cannot be blank: " + fieldName);
        }
        String trimmed = value.trim();
        if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("signature payload field cannot contain newline: " + fieldName);
        }
        return trimmed;
    }
}
