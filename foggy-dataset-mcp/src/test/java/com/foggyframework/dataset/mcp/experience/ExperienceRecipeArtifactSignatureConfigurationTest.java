package com.foggyframework.dataset.mcp.experience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Experience recipe artifact signature configuration")
class ExperienceRecipeArtifactSignatureConfigurationTest {
    private static final String KEY_ID = "recipe-evidence-key";

    @Test
    @DisplayName("should bind configured Ed25519 trust key and wire default verifier")
    void shouldBindConfiguredTrustKeyAndWireDefaultVerifier() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        Instant signedAt = Instant.now().minusSeconds(60);
        Instant validFrom = signedAt.minusSeconds(60);
        Instant validTo = signedAt.plusSeconds(3600);

        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(
                        ExperienceRecipeRegistryProperties.class,
                        ExperienceRecipeArtifactSignatureConfiguration.class)
                .withPropertyValues(
                        "foggy.mcp.experience-recipe.registry.artifact-trust-keys[0].key-id=" + KEY_ID,
                        "foggy.mcp.experience-recipe.registry.artifact-trust-keys[0].public-key=" + publicKey,
                        "foggy.mcp.experience-recipe.registry.artifact-trust-keys[0].tenant-ids[0]=tenant-a",
                        "foggy.mcp.experience-recipe.registry.artifact-trust-keys[0].owner-ids[0]=finance_owner",
                        "foggy.mcp.experience-recipe.registry.artifact-trust-keys[0].artifact-types[0]=owner_signoff",
                        "foggy.mcp.experience-recipe.registry.artifact-trust-keys[0].signed-by-subjects[0]=registry_admin",
                        "foggy.mcp.experience-recipe.registry.artifact-trust-keys[0].valid-from=" + validFrom,
                        "foggy.mcp.experience-recipe.registry.artifact-trust-keys[0].valid-to=" + validTo);

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            ExperienceRecipeArtifactTrustStore trustStore = context.getBean(ExperienceRecipeArtifactTrustStore.class);
            assertTrue(trustStore.findByKeyId(KEY_ID).isPresent());

            ExperienceRecipeArtifactSignatureVerifier verifier =
                    context.getBean(ExperienceRecipeArtifactSignatureVerifier.class);
            assertNotNull(verifier);

            byte[] content = "owner signoff artifact".getBytes(StandardCharsets.UTF_8);
            ExperienceRecipeEvidenceArtifact artifact = ExperienceRecipeEvidenceArtifact.of(
                    "owner_signoff",
                    "foggy://experience-recipes/evidence/owner-signoff",
                    ExperienceRecipeArtifactHash.sha256(content),
                    "registry_admin");
            artifact.setSignedAt(signedAt.toString());

            ExperienceRecipeArtifactSignatureContext signatureContext =
                    new ExperienceRecipeArtifactSignatureContext(
                            "odoo",
                            "tenant-a",
                            "crm_source_funnel_and_stage_dropoff_dashboard@v1",
                            "crm_source_funnel_and_stage_dropoff_dashboard",
                            "v1",
                            "finance_owner");
            artifact.setArtifactSignature(signatureFor(keyPair.getPrivate(), signatureContext, artifact));

            ExperienceRecipeArtifactVerificationResult result =
                    verifier.verify(artifact, content, signatureContext);
            assertTrue(result.verified(), result.reason());
        });
    }

    private static String signatureFor(
            PrivateKey privateKey,
            ExperienceRecipeArtifactSignatureContext context,
            ExperienceRecipeEvidenceArtifact artifact) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(ExperienceRecipeArtifactSignaturePayload.canonicalBytes(context, artifact));
        return "sig:v1:ed25519:" + KEY_ID + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }
}
