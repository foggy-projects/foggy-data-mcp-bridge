package com.foggyframework.dataset.mcp.experience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Experience recipe artifact URI policy")
class ExperienceRecipeArtifactUriPolicyTest {

    @Test
    @DisplayName("should match S3 artifact URI scoped by recipe context")
    void shouldMatchS3ArtifactUriScopedByRecipeContext() {
        ExperienceRecipeArtifactUriPolicy policy = policyWithPrefixes(List.of(
                "s3://foggy-artifacts/tenants/{tenant}/owners/{owner}/recipes/{registryKey}/evidence/{artifactType}/"));

        ExperienceRecipeArtifactVerificationResult result = policy.validate(
                List.of(artifact(
                        "owner_signoff",
                        "s3://foggy-artifacts/tenants/tenant-a/owners/finance_owner/recipes/"
                                + "crm_source_funnel_and_stage_dropoff_dashboard@v1/evidence/owner_signoff/signoff.json")),
                context());

        assertTrue(result.verified(), result.reason());
    }

    @Test
    @DisplayName("should match OSS artifact URI scoped by recipe context")
    void shouldMatchOssArtifactUriScopedByRecipeContext() {
        ExperienceRecipeArtifactUriPolicy policy = policyWithPrefixes(List.of(
                "oss://foggy-artifacts/tenants/{tenant}/owners/{owner}/recipes/{registryKey}/evidence/{artifactType}/"));

        ExperienceRecipeArtifactVerificationResult result = policy.validate(
                List.of(artifact(
                        "runtime_sample",
                        "oss://foggy-artifacts/tenants/tenant-a/owners/finance_owner/recipes/"
                                + "crm_source_funnel_and_stage_dropoff_dashboard@v1/evidence/runtime_sample/sample.json")),
                context());

        assertTrue(result.verified(), result.reason());
    }

    @Test
    @DisplayName("should reject object URI path traversal before resolver")
    void shouldRejectObjectUriPathTraversalBeforeResolver() {
        ExperienceRecipeArtifactUriPolicy policy = policyWithPrefixes(List.of(
                "s3://foggy-artifacts/tenants/{tenant}/owners/{owner}/recipes/{registryKey}/evidence/{artifactType}/"));

        ExperienceRecipeArtifactVerificationResult result = policy.validate(
                List.of(artifact(
                        "owner_signoff",
                        "s3://foggy-artifacts/tenants/tenant-a/owners/finance_owner/recipes/"
                                + "crm_source_funnel_and_stage_dropoff_dashboard@v1/evidence/owner_signoff/../evil.json")),
                context());

        assertFalse(result.verified());
        assertTrue(result.reason().contains("artifact URI is not bound to recipe context"));
    }

    private static ExperienceRecipeArtifactUriPolicy policyWithPrefixes(List<String> allowedPrefixes) {
        ExperienceRecipeRegistryProperties properties = new ExperienceRecipeRegistryProperties();
        properties.getArtifactUriPolicy().setEnabled(true);
        properties.getArtifactUriPolicy().setAllowedUriPrefixes(allowedPrefixes);
        return new ExperienceRecipeArtifactUriPolicy(properties);
    }

    private static ExperienceRecipeArtifactSignatureContext context() {
        return new ExperienceRecipeArtifactSignatureContext(
                "odoo",
                "tenant-a",
                "crm_source_funnel_and_stage_dropoff_dashboard@v1",
                "crm_source_funnel_and_stage_dropoff_dashboard",
                "v1",
                "finance_owner");
    }

    private static ExperienceRecipeEvidenceArtifact artifact(String artifactType, String artifactUri) {
        return ExperienceRecipeEvidenceArtifact.of(
                artifactType,
                artifactUri,
                "sha256:" + "a".repeat(64),
                "registry_admin");
    }
}
