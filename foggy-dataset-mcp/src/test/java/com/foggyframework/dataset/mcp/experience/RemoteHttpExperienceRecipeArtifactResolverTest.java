package com.foggyframework.dataset.mcp.experience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Remote HTTP experience recipe artifact resolver")
class RemoteHttpExperienceRecipeArtifactResolverTest {

    @Test
    @DisplayName("should resolve allowed HTTPS artifact")
    void shouldResolveAllowedHttpsArtifact() {
        byte[] content = "owner signoff artifact".getBytes(StandardCharsets.UTF_8);
        RemoteHttpExperienceRecipeArtifactResolver resolver =
                new RemoteHttpExperienceRecipeArtifactResolver(
                        properties(Set.of("artifacts.example.com"), 1024),
                        (uri, timeout) -> new RemoteHttpExperienceRecipeArtifactResolver
                                .RemoteHttpArtifactResponse(200, content));

        Optional<byte[]> resolved = resolver.resolve(artifact(
                "https://artifacts.example.com/evidence/owner-signoff"));

        assertTrue(resolved.isPresent());
        assertArrayEquals(content, resolved.get());
    }

    @Test
    @DisplayName("should not call transport for blocked HTTPS authorities")
    void shouldRejectBlockedHttpsAuthoritiesBeforeTransport() {
        AtomicInteger calls = new AtomicInteger();
        RemoteHttpExperienceRecipeArtifactResolver resolver =
                new RemoteHttpExperienceRecipeArtifactResolver(
                        properties(Set.of("artifacts.example.com"), 1024),
                        (uri, timeout) -> {
                            calls.incrementAndGet();
                            return new RemoteHttpExperienceRecipeArtifactResolver
                                    .RemoteHttpArtifactResponse(200, new byte[0]);
                        });

        assertTrue(resolver.resolve(artifact("https://evil.example.com/evidence/owner-signoff")).isEmpty());
        assertTrue(resolver.resolve(artifact("https://user@artifacts.example.com/evidence/owner-signoff")).isEmpty());
        assertTrue(resolver.resolve(artifact("https://artifacts.example.com/evidence/owner-signoff#frag")).isEmpty());
        assertTrue(resolver.resolve(artifact("http://artifacts.example.com/evidence/owner-signoff")).isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("should reject non-OK and oversized responses")
    void shouldRejectNonOkAndOversizedResponses() {
        RemoteHttpExperienceRecipeArtifactResolver nonOkResolver =
                new RemoteHttpExperienceRecipeArtifactResolver(
                        properties(Set.of("artifacts.example.com"), 1024),
                        (uri, timeout) -> new RemoteHttpExperienceRecipeArtifactResolver
                                .RemoteHttpArtifactResponse(404, "missing".getBytes(StandardCharsets.UTF_8)));
        RemoteHttpExperienceRecipeArtifactResolver oversizedResolver =
                new RemoteHttpExperienceRecipeArtifactResolver(
                        properties(Set.of("artifacts.example.com"), 4),
                        (uri, timeout) -> new RemoteHttpExperienceRecipeArtifactResolver
                                .RemoteHttpArtifactResponse(200, "too-large".getBytes(StandardCharsets.UTF_8)));

        assertTrue(nonOkResolver.resolve(artifact("https://artifacts.example.com/evidence/owner-signoff")).isEmpty());
        assertTrue(oversizedResolver.resolve(artifact("https://artifacts.example.com/evidence/owner-signoff")).isEmpty());
    }

    @Test
    @DisplayName("should resolve trusted object metadata from internal artifact service headers")
    void shouldResolveTrustedObjectMetadataFromInternalArtifactServiceHeaders() {
        byte[] content = "owner signoff artifact".getBytes(StandardCharsets.UTF_8);
        String artifactHash = ExperienceRecipeArtifactHash.sha256(content);
        ExperienceRecipeEvidenceArtifact artifact = artifact(
                "https://artifacts.example.com/evidence/owner-signoff",
                artifactHash);
        RemoteHttpExperienceRecipeArtifactResolver resolver =
                new RemoteHttpExperienceRecipeArtifactResolver(
                        properties(Set.of("artifacts.example.com"), 1024),
                        (uri, timeout) -> new RemoteHttpExperienceRecipeArtifactResolver
                                .RemoteHttpArtifactResponse(200, content, trustedHeaders(artifact)));

        Optional<ExperienceRecipeArtifactResolution> resolved = resolver.resolveArtifact(artifact);

        assertTrue(resolved.isPresent());
        assertArrayEquals(content, resolved.get().content());
        assertEquals("v-owner_signoff", resolved.get().objectVersion());
        assertEquals("etag-owner_signoff", resolved.get().objectEtag());
        assertEquals("tenant-a", resolved.get().objectMetadata().get("tenant"));
        assertEquals("sales_team_target_achievement_memory_grid", resolved.get()
                .objectMetadata()
                .get("canonicalRecipeId"));
        assertEquals(artifactHash, resolved.get().objectMetadata().get("artifactHash"));
    }

    @Test
    @DisplayName("should reject trusted artifact service hash mismatch")
    void shouldRejectTrustedArtifactServiceHashMismatch() {
        byte[] content = "owner signoff artifact".getBytes(StandardCharsets.UTF_8);
        RemoteHttpExperienceRecipeArtifactResolver resolver =
                new RemoteHttpExperienceRecipeArtifactResolver(
                        properties(Set.of("artifacts.example.com"), 1024),
                        (uri, timeout) -> new RemoteHttpExperienceRecipeArtifactResolver
                                .RemoteHttpArtifactResponse(
                                        200,
                                        content,
                                        Map.of("X-Foggy-Artifact-Hash", List.of("sha256:" + "f".repeat(64)))));

        assertTrue(resolver.resolveArtifact(artifact(
                "https://artifacts.example.com/evidence/owner-signoff",
                ExperienceRecipeArtifactHash.sha256(content))).isEmpty());
    }

    @Test
    @DisplayName("should reject trusted artifact service size mismatch")
    void shouldRejectTrustedArtifactServiceSizeMismatch() {
        byte[] content = "owner signoff artifact".getBytes(StandardCharsets.UTF_8);
        RemoteHttpExperienceRecipeArtifactResolver resolver =
                new RemoteHttpExperienceRecipeArtifactResolver(
                        properties(Set.of("artifacts.example.com"), 1024),
                        (uri, timeout) -> new RemoteHttpExperienceRecipeArtifactResolver
                                .RemoteHttpArtifactResponse(
                                        200,
                                        content,
                                        Map.of("X-Foggy-Artifact-Size", List.of("1"))));

        assertTrue(resolver.resolveArtifact(artifact(
                "https://artifacts.example.com/evidence/owner-signoff",
                ExperienceRecipeArtifactHash.sha256(content))).isEmpty());
    }

    private static ExperienceRecipeRegistryProperties properties(Set<String> allowedHosts, int maxBytes) {
        ExperienceRecipeRegistryProperties properties = new ExperienceRecipeRegistryProperties();
        properties.getRemoteHttp().setAllowedHosts(allowedHosts);
        properties.getRemoteHttp().setMaxBytes(maxBytes);
        return properties;
    }

    private static ExperienceRecipeEvidenceArtifact artifact(String artifactUri) {
        return artifact(artifactUri, "sha256:" + "a".repeat(64));
    }

    private static ExperienceRecipeEvidenceArtifact artifact(String artifactUri, String artifactHash) {
        return ExperienceRecipeEvidenceArtifact.of(
                "owner_signoff",
                artifactUri,
                artifactHash,
                "registry_admin");
    }

    private static Map<String, List<String>> trustedHeaders(ExperienceRecipeEvidenceArtifact artifact) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("X-Foggy-Artifact-Object-Version", List.of("v-owner_signoff"));
        headers.put("X-Foggy-Artifact-Object-Etag", List.of("etag-owner_signoff"));
        headers.put("X-Foggy-Artifact-Hash", List.of(artifact.getArtifactHash()));
        headers.put("X-Foggy-Artifact-Size", List.of(String.valueOf("owner signoff artifact".getBytes(
                StandardCharsets.UTF_8).length)));
        headers.put("X-Foggy-Artifact-Metadata-Namespace", List.of("odoo"));
        headers.put("X-Foggy-Artifact-Metadata-Tenant", List.of("tenant-a"));
        headers.put("X-Foggy-Artifact-Metadata-Owner", List.of("finance_owner"));
        headers.put("X-Foggy-Artifact-Metadata-Registry-Key", List.of(
                "sales_team_target_achievement_memory_grid_finance_owner@v1"));
        headers.put("X-Foggy-Artifact-Metadata-Canonical-Recipe-Id", List.of(
                "sales_team_target_achievement_memory_grid"));
        headers.put("X-Foggy-Artifact-Metadata-Version", List.of("v1"));
        headers.put("X-Foggy-Artifact-Metadata-Artifact-Type", List.of(artifact.getArtifactType()));
        headers.put("X-Foggy-Artifact-Metadata-Artifact-Hash", List.of(artifact.getArtifactHash()));
        return headers;
    }
}
