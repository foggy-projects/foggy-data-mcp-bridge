package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.candidate.CandidateContentRevision;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.service.RuntimePublishedBundleArtifactStore.PublicationAttempt;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService.RuntimeBundleRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeAuthoringPublicationStoreTest {

    @TempDir
    Path tempDirectory;

    @Test
    void initializesOwnedRootAndCommitsVerifiableImmutableArtifact() {
        RuntimePublishedBundleArtifactStore store = store(
                tempDirectory.resolve("published"));
        Map<String, byte[]> snapshot = Map.of(
                "model/Order.tm", bytes("tm"),
                "query/Order.qm", bytes("qm"),
                "shared/common.fsscript", bytes("script"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String attemptId = store.newAttemptId();

        Path artifact = store.prepareArtifact(
                attemptId, "workspace-opaque-identity", "sales",
                "managed-sales", revision, snapshot);
        PublicationAttempt attempt = attempt(
                attemptId, revision, artifact.getParent().resolve("old").toString());
        store.begin(attempt);

        assertThat(artifact.resolve("model/Order.tm")).hasBinaryContent(bytes("tm"));
        assertThat(artifact.resolve("query/Order.qm")).hasBinaryContent(bytes("qm"));
        assertThat(artifact.resolve("shared/common.fsscript"))
                .hasBinaryContent(bytes("script"));
        assertThat(artifact.resolve(".artifact.json")).isRegularFile();
        assertThat(artifact.getParent()).isDirectoryNotContaining(
                path -> path.getFileName().toString().endsWith(".owner.json"));
        assertThat(rootOf(artifact).resolve("attempts")).isDirectoryNotContaining(
                path -> path.getFileName().toString().endsWith(".owner.json"));
        assertThat(store.artifactPath(store.get(attemptId))).isEqualTo(artifact);
    }

    @Test
    void nonemptyUnownedRootFailsWithoutTouchingForeignData() throws Exception {
        Path root = Files.createDirectory(tempDirectory.resolve("foreign-root"));
        Path sentinel = Files.writeString(root.resolve("sentinel.txt"), "foreign");
        RuntimePublishedBundleArtifactStore store = store(root);
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("tm"));

        assertCode(() -> store.prepareArtifact(
                        store.newAttemptId(), "workspace-opaque-identity",
                        "sales", "managed-sales",
                        CandidateContentRevision.calculate(snapshot), snapshot),
                "WORKSPACE_ARTIFACT_STORE_FAILURE");

        assertThat(sentinel).hasContent("foreign");
        assertThat(root.resolve(".foggy-published-owner.json")).doesNotExist();
    }

    @Test
    void tamperingAndForeignEntriesFailClosedWithoutDeletion() throws Exception {
        Path root = tempDirectory.resolve("tampered-root");
        RuntimePublishedBundleArtifactStore store = store(root);
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("tm"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String attemptId = store.newAttemptId();
        Path artifact = store.prepareArtifact(
                attemptId, "workspace-opaque-identity", "sales",
                "managed-sales", revision, snapshot);
        store.begin(attempt(attemptId, revision,
                tempDirectory.resolve("old-source").toString()));
        Files.writeString(artifact.resolve("Order.tm"), "tampered");

        assertCode(() -> store.get(attemptId), "WORKSPACE_ARTIFACT_CORRUPT");
        assertThat(artifact.resolve("Order.tm")).hasContent("tampered");

        Path foreignRoot = tempDirectory.resolve("foreign-entry-root");
        RuntimePublishedBundleArtifactStore foreignStore = store(foreignRoot);
        String secondId = foreignStore.newAttemptId();
        Path second = foreignStore.prepareArtifact(
                secondId, "workspace-opaque-identity", "sales",
                "managed-sales", revision, snapshot);
        foreignStore.begin(attempt(secondId, revision,
                tempDirectory.resolve("old-source-2").toString()));
        Path sentinel = Files.writeString(foreignRoot.resolve("foreign.txt"),
                "preserve");

        assertCode(() -> foreignStore.get(secondId),
                "WORKSPACE_ARTIFACT_CORRUPT");
        assertThat(sentinel).hasContent("preserve");
        assertThat(second).isDirectory();
    }

    @Test
    void artifactSymlinkFailsClosedWithoutFollowingOrDeletingTarget()
            throws Exception {
        Path root = tempDirectory.resolve("symlink-root");
        RuntimePublishedBundleArtifactStore store = store(root);
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("tm"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String attemptId = store.newAttemptId();
        Path artifact = store.prepareArtifact(
                attemptId, "workspace-opaque-identity", "sales",
                "managed-sales", revision, snapshot);
        store.begin(attempt(attemptId, revision,
                tempDirectory.resolve("old-source").toString()));
        Path foreign = Files.writeString(
                tempDirectory.resolve("foreign-target.txt"), "preserve");
        Path link = Files.createSymbolicLink(
                artifact.resolve("linked.tm"), foreign);

        assertCode(() -> store.get(attemptId), "WORKSPACE_ARTIFACT_CORRUPT");
        assertThat(link).isSymbolicLink();
        assertThat(foreign).hasContent("preserve");
    }

    @Test
    void corruptManifestFailsClosedWithoutDeletingArtifact() throws Exception {
        Path root = tempDirectory.resolve("manifest-root");
        RuntimePublishedBundleArtifactStore store = store(root);
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("tm"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String attemptId = store.newAttemptId();
        Path artifact = store.prepareArtifact(
                attemptId, "workspace-opaque-identity", "sales",
                "managed-sales", revision, snapshot);
        store.begin(attempt(attemptId, revision,
                tempDirectory.resolve("old-source").toString()));
        Files.writeString(artifact.resolve(".artifact.json"), "{}");

        assertCode(() -> store.get(attemptId), "WORKSPACE_ARTIFACT_CORRUPT");
        assertThat(artifact.resolve("Order.tm")).hasContent("tm");
        assertThat(artifact.resolve(".artifact.json")).hasContent("{}");
    }

    @Test
    void unsupportedAttemptSchemaFailsClosedWithoutDeletingArtifact()
            throws Exception {
        Path root = tempDirectory.resolve("attempt-schema-root");
        RuntimePublishedBundleArtifactStore store = store(root);
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("tm"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String attemptId = store.newAttemptId();
        Path artifact = store.prepareArtifact(
                attemptId, "workspace-opaque-identity", "sales",
                "managed-sales", revision, snapshot);
        store.begin(attempt(attemptId, revision,
                tempDirectory.resolve("old-source").toString()));
        Path metadata = root.resolve("attempts").resolve(attemptId + ".json");
        String changed = Files.readString(metadata)
                .replace("\"version\" : 1", "\"version\" : 99");
        assertThat(changed).contains("\"version\" : 99");
        Files.writeString(metadata, changed);

        assertCode(() -> store.get(attemptId), "WORKSPACE_ARTIFACT_CORRUPT");
        assertThat(artifact.resolve("Order.tm")).hasContent("tm");
        assertThat(metadata).hasContent(changed);
    }

    @Test
    void invalidArtifactIsRejectedBeforeOwnedStagingBecomesVisible()
            throws Exception {
        Path root = tempDirectory.resolve("artifact-staging-root");
        RuntimePublishedBundleArtifactStore store = store(root);
        Map<String, byte[]> invalidSnapshot = Map.of(
                "a/Order.tm", bytes("partial-model"),
                "z/notes.txt", bytes("unsupported"));
        String invalidRevision = CandidateContentRevision.calculate(invalidSnapshot);

        assertCode(() -> store.prepareArtifact(
                        store.newAttemptId(), "workspace-opaque-identity",
                        "sales", "managed-sales", invalidRevision,
                        invalidSnapshot),
                "WORKSPACE_ARTIFACT_CORRUPT");

        assertThat(root.resolve(".foggy-published-owner.json")).isRegularFile();
        assertThat(root.resolve("artifacts")).isEmptyDirectory();
        assertThat(root.resolve("attempts")).isEmptyDirectory();
    }

    @Test
    void traversalArtifactIsRejectedBeforeOwnedStagingBecomesVisible() {
        Path root = tempDirectory.resolve("artifact-traversal-root");
        RuntimePublishedBundleArtifactStore store = store(root);
        Map<String, byte[]> traversal = Map.of("../Order.tm", bytes("escape"));

        assertCode(() -> store.prepareArtifact(
                        store.newAttemptId(), "workspace-opaque-identity",
                        "sales", "managed-sales",
                        CandidateContentRevision.calculate(traversal), traversal),
                "WORKSPACE_ARTIFACT_CORRUPT");

        assertThat(root.resolve("artifacts")).isEmptyDirectory();
        assertThat(tempDirectory.resolve("Order.tm")).doesNotExist();
    }

    @Test
    void restartRecoversOwnedPartialArtifactAndIsIdempotent() throws Exception {
        Path root = tempDirectory.resolve("owned-artifact-recovery-root");
        RuntimePublishedBundleArtifactStore store = store(root);
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("committed"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String committedId = store.newAttemptId();
        Path committed = store.prepareArtifact(
                committedId, "workspace-opaque-identity", "sales",
                "managed-sales", revision, snapshot);
        store.begin(attempt(committedId, revision,
                tempDirectory.resolve("old-source").toString()));

        String interruptedId = store.newAttemptId();
        String stagingName = ".staging-" + UUID.randomUUID();
        Path stagingOwner = root.resolve("artifacts")
                .resolve(stagingName + ".owner.json");
        Files.writeString(stagingOwner, artifactOwner(
                storeId(root), stagingName, interruptedId, revision));
        Path staging = Files.createDirectory(
                root.resolve("artifacts").resolve(stagingName));
        Path partial = Files.writeString(staging.resolve("Order.tm"), "partial");

        RuntimePublishedBundleArtifactStore restarted = store(root);
        assertThat(restarted.get(committedId).attemptId()).isEqualTo(committedId);
        assertThat(restarted.get(committedId).attemptId()).isEqualTo(committedId);

        assertThat(staging).doesNotExist();
        assertThat(stagingOwner).doesNotExist();
        assertThat(partial).doesNotExist();
        assertThat(committed.resolve("Order.tm")).hasContent("committed");
    }

    @Test
    void staleArtifactOwnerAfterCommitConvergesWithoutChangingFinalArtifact()
            throws Exception {
        Path root = tempDirectory.resolve("stale-artifact-owner-root");
        RuntimePublishedBundleArtifactStore store = store(root);
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("committed"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String attemptId = store.newAttemptId();
        Path artifact = store.prepareArtifact(
                attemptId, "workspace-opaque-identity", "sales",
                "managed-sales", revision, snapshot);
        store.begin(attempt(attemptId, revision,
                tempDirectory.resolve("old-source").toString()));
        String stagingName = ".staging-" + UUID.randomUUID();
        Path staleOwner = Files.writeString(root.resolve("artifacts")
                .resolve(stagingName + ".owner.json"), artifactOwner(
                storeId(root), stagingName, attemptId, revision));

        RuntimePublishedBundleArtifactStore restarted = store(root);
        assertThat(restarted.get(attemptId).attemptId()).isEqualTo(attemptId);

        assertThat(staleOwner).doesNotExist();
        assertThat(artifact.resolve("Order.tm")).hasContent("committed");
    }

    @Test
    void corruptOwnedArtifactMarkerAndSymlinkArePreservedAndBlockRecovery()
            throws Exception {
        Path root = tempDirectory.resolve("unsafe-artifact-recovery-root");
        RuntimePublishedBundleArtifactStore store = store(root);
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("committed"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String committedId = store.newAttemptId();
        store.prepareArtifact(committedId, "workspace-opaque-identity", "sales",
                "managed-sales", revision, snapshot);
        store.begin(attempt(committedId, revision,
                tempDirectory.resolve("old-source").toString()));
        String interruptedId = store.newAttemptId();
        String stagingName = ".staging-" + UUID.randomUUID();
        Path stagingOwner = Files.writeString(root.resolve("artifacts")
                .resolve(stagingName + ".owner.json"), artifactOwner(
                "foreign_store_identity_000000000000", stagingName,
                interruptedId, revision));
        Path staging = Files.createDirectory(root.resolve("artifacts")
                .resolve(stagingName));
        Path foreign = Files.writeString(
                tempDirectory.resolve("recovery-foreign.txt"), "preserve");
        Path link = Files.createSymbolicLink(staging.resolve("Order.tm"), foreign);

        RuntimePublishedBundleArtifactStore restarted = store(root);
        assertCode(() -> restarted.get(committedId),
                "WORKSPACE_ARTIFACT_CORRUPT");

        assertThat(stagingOwner).isRegularFile();
        assertThat(staging).isDirectory();
        assertThat(link).isSymbolicLink();
        assertThat(foreign).hasContent("preserve");
    }

    @Test
    void restartPreservesPublicationMetadataTemporaryAndFailsClosed()
            throws Exception {
        Path root = tempDirectory.resolve("attempt-temporary-root");
        RuntimePublishedBundleArtifactStore store = store(root);
        String attemptId = store.newAttemptId();
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("tm"));
        store.prepareArtifact(
                attemptId, "workspace-opaque-identity", "sales",
                "managed-sales", CandidateContentRevision.calculate(snapshot),
                snapshot);
        Path temporary = Files.writeString(
                root.resolve("attempts").resolve(
                        attemptId + ".json.tmp-" + UUID.randomUUID()),
                "partial-attempt-metadata");

        RuntimePublishedBundleArtifactStore restarted = store(root);
        assertCode(() -> restarted.get(attemptId),
                "WORKSPACE_ARTIFACT_CORRUPT");

        assertThat(temporary).hasContent("partial-attempt-metadata");
        assertThat(root.resolve("artifacts").resolve(attemptId)).isDirectory();
    }

    @Test
    void restartRecoversOwnedAttemptTemporaryAndStaleOwner() throws Exception {
        Path root = tempDirectory.resolve("owned-attempt-temporary-root");
        RuntimePublishedBundleArtifactStore store = store(root);
        String attemptId = store.newAttemptId();
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("tm"));
        String revision = CandidateContentRevision.calculate(snapshot);
        store.prepareArtifact(attemptId, "workspace-opaque-identity", "sales",
                "managed-sales", revision, snapshot);
        store.begin(attempt(attemptId, revision,
                tempDirectory.resolve("old-source").toString()));
        Path finalAttempt = root.resolve("attempts")
                .resolve(attemptId + ".json");
        String committed = Files.readString(finalAttempt);
        String temporaryName = attemptId + ".json.tmp-" + UUID.randomUUID();
        Path temporary = Files.writeString(finalAttempt.resolveSibling(
                temporaryName), committed);
        Path owner = Files.writeString(finalAttempt.resolveSibling(
                temporaryName + ".owner.json"), attemptOwner(
                storeId(root), temporaryName, attemptId));

        RuntimePublishedBundleArtifactStore restarted = store(root);
        assertThat(restarted.get(attemptId).attemptId()).isEqualTo(attemptId);

        assertThat(temporary).doesNotExist();
        assertThat(owner).doesNotExist();
        assertThat(finalAttempt).hasContent(committed);

        String markerOnlyName = attemptId + ".json.tmp-" + UUID.randomUUID();
        Path markerOnly = Files.writeString(finalAttempt.resolveSibling(
                markerOnlyName + ".owner.json"), attemptOwner(
                storeId(root), markerOnlyName, attemptId));
        assertThat(restarted.get(attemptId).attemptId()).isEqualTo(attemptId);
        assertThat(markerOnly).doesNotExist();
        assertThat(finalAttempt).hasContent(committed);
    }

    @Test
    void publishedRegistrySourceRequiresOwnedCompletedMatchingAttempt()
            throws Exception {
        Path root = tempDirectory.resolve("published-base-root");
        RuntimePublishedBundleArtifactStore store = store(root);
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("tm"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String attemptId = store.newAttemptId();
        Path artifact = store.prepareArtifact(
                attemptId, "workspace-opaque-identity", "sales",
                "managed-sales", revision, snapshot);
        PublicationAttempt publishing = attempt(
                attemptId, revision, tempDirectory.resolve("old-source").toString());
        store.begin(publishing);
        store.update(publishing.withStatus(
                "PUBLISHED", "source:published", null,
                "catalog:published", null, Instant.now().toString(), List.of()));
        String now = Instant.now().toString();
        RuntimeBundleRecord record = new RuntimeBundleRecord(
                "managed-sales", "sales", artifact.toString(), false, true,
                now, now, true, revision);

        assertThat(store.verifyPublishedSource(record)).isEqualTo(artifact);

        Path foreign = Files.createDirectories(tempDirectory.resolve("foreign-artifact"));
        RuntimeBundleRecord foreignRecord = new RuntimeBundleRecord(
                record.name(), record.namespace(), foreign.toString(), false, true,
                record.createdAt(), record.updatedAt(), true, revision);
        assertCode(() -> store.verifyPublishedSource(foreignRecord),
                "WORKSPACE_ARTIFACT_CORRUPT");
        assertThat(foreign).isDirectory();

        store.update(publishing.withStatus(
                "RECOVERED", "source:published", null,
                "catalog:published", "catalog:recovered",
                Instant.now().toString(), List.of()));
        assertCode(() -> store.verifyPublishedSource(record),
                "WORKSPACE_ARTIFACT_CORRUPT");
        assertThat(artifact.resolve("Order.tm")).hasContent("tm");
    }

    private RuntimePublishedBundleArtifactStore store(Path root) {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getAuthoringWorkspaces().setPublishedBundlesPath(
                root.toString());
        properties.getAuthoringWorkspaces().setPath(
                tempDirectory.resolve("workspaces").toString());
        return new RuntimePublishedBundleArtifactStore(
                properties, new ObjectMapper(), null, null);
    }

    private static PublicationAttempt attempt(
            String attemptId,
            String revision,
            String oldPath
    ) {
        String now = Instant.now().toString();
        return new PublicationAttempt(
                1, attemptId, "workspace-opaque-identity", "sales",
                "managed-sales", revision, revision, "source:base",
                oldPath, false, true, now, now, false, null,
                "PUBLISHING", null, null, null, null,
                now, null, List.of());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static Path rootOf(Path artifact) {
        return artifact.getParent().getParent();
    }

    private static String storeId(Path root) throws Exception {
        return new ObjectMapper().readTree(
                root.resolve(".foggy-published-owner.json").toFile())
                .path("storeId").asText();
    }

    private static String artifactOwner(
            String storeId,
            String stagingName,
            String attemptId,
            String revision
    ) {
        return """
                {"version":1,"storeId":"%s","stagingName":"%s",\
                "attemptId":"%s","workspaceId":"workspace-opaque-identity",\
                "namespace":"sales","bundle":"managed-sales",\
                "candidateRevision":"%s"}
                """.formatted(storeId, stagingName, attemptId, revision);
    }

    private static String attemptOwner(
            String storeId,
            String temporaryName,
            String attemptId
    ) {
        return """
                {"version":1,"storeId":"%s","temporaryName":"%s",\
                "finalName":"%s.json","attemptId":"%s"}
                """.formatted(storeId, temporaryName, attemptId, attemptId);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(RuntimeAuthoringWorkspaceException.class)
                .satisfies(error -> assertThat(
                        ((RuntimeAuthoringWorkspaceException) error).code())
                        .isEqualTo(code));
    }
}
