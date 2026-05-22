package com.foggyframework.dataset.mcp.experience;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Configuration
public class ExperienceRecipeArtifactSignatureConfiguration {

    @Bean
    @ConditionalOnMissingBean(ExperienceRecipeArtifactTrustStore.class)
    public ExperienceRecipeArtifactTrustStore experienceRecipeArtifactTrustStore(
            ExperienceRecipeRegistryProperties properties) {
        List<ExperienceRecipeArtifactTrustKey> trustKeys = properties.getArtifactTrustKeys().stream()
                .map(ExperienceRecipeArtifactSignatureConfiguration::trustKeyFrom)
                .toList();
        return new InMemoryExperienceRecipeArtifactTrustStore(trustKeys);
    }

    @Bean
    @ConditionalOnMissingBean(ExperienceRecipeArtifactSignatureVerifier.class)
    public ExperienceRecipeArtifactSignatureVerifier experienceRecipeArtifactSignatureVerifier(
            ExperienceRecipeArtifactTrustStore trustStore) {
        return new Ed25519ExperienceRecipeArtifactSignatureVerifier(trustStore);
    }

    private static ExperienceRecipeArtifactTrustKey trustKeyFrom(
            ExperienceRecipeRegistryProperties.ArtifactTrustKeyProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("artifact trust key cannot be null");
        }
        return new ExperienceRecipeArtifactTrustKey(
                properties.getKeyId(),
                properties.getAlgorithm(),
                decodePublicKey(properties.getPublicKey()),
                properties.getPurposes(),
                properties.getTenantIds(),
                properties.getOwnerIds(),
                properties.getArtifactTypes(),
                properties.getSignedBySubjects(),
                properties.getValidFrom(),
                properties.getValidTo(),
                statusFrom(properties.getStatus()),
                properties.getRevokedAt());
    }

    private static byte[] decodePublicKey(String publicKey) {
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalArgumentException("artifact trust key publicKey cannot be blank");
        }
        String normalized = publicKey.trim();
        if (normalized.contains("BEGIN PUBLIC KEY")) {
            normalized = normalized
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "");
        }
        normalized = normalized.replaceAll("\\s+", "");
        try {
            return Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException ex) {
            return Base64.getUrlDecoder().decode(normalized);
        }
    }

    private static ExperienceRecipeArtifactTrustKey.Status statusFrom(String status) {
        if (status == null || status.isBlank()) {
            return ExperienceRecipeArtifactTrustKey.Status.ENABLED;
        }
        return ExperienceRecipeArtifactTrustKey.Status.valueOf(
                status.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }
}
