package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.candidate.CandidateContentRevision;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.ArtifactLifecycleInventory;
import com.foggyframework.runtime.api.dto.ArtifactLifecycleInventory.InventoryObject;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService.RuntimeBundleRecord;
import com.foggyframework.runtime.api.service.RuntimePublishedBundleArtifactStore.PublicationAttempt;
import com.foggyframework.runtime.api.service.RuntimePublishedBundleArtifactStore.RollbackAttempt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RuntimeArtifactLifecycleInventoryServiceTest {

    private static final String SOURCE_IDENTITY =
            "sha256:" + "1".repeat(64);

    @TempDir
    Path tempDirectory;

    @Test
    void missingRootsAreReportedWithoutInitializationOrMutation() {
        Fixture fixture = fixture("missing");

        ArtifactLifecycleInventory inventory = fixture.inventory.inventory();

        assertThat(inventory.health()).isEqualTo("NOT_INITIALIZED");
        assertThat(inventory.roots())
                .extracting(ArtifactLifecycleInventory.RootHealth::health)
                .containsOnly("NOT_INITIALIZED");
        assertThat(inventory.summary().totalObjects()).isZero();
        assertThat(fixture.workspaceRoot).doesNotExist();
        assertThat(fixture.publishedRoot).doesNotExist();
        assertThat(fixture.registryPath).doesNotExist();
    }

    @Test
    void healthyInventoryRetainsCrossStoreAttemptWorkspaceAndLiveReferences()
            throws Exception {
        Fixture fixture = fixture("healthy");
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("healthy-model"));
        var workspace = fixture.workspaceStore.create(
                "sales", "managed-sales", "source:base", SOURCE_IDENTITY,
                snapshot);
        String revision = CandidateContentRevision.calculate(snapshot);
        String attemptId = fixture.artifactStore.newAttemptId();
        Path artifact = fixture.artifactStore.prepareArtifact(
                attemptId, workspace.workspaceId(), "sales", "managed-sales",
                revision, snapshot);
        PublicationAttempt publishing = attempt(
                attemptId, workspace.workspaceId(), revision,
                tempDirectory.resolve("previous-source").toString());
        fixture.artifactStore.begin(publishing);
        fixture.artifactStore.update(publishing.withStatus(
                "PUBLISHED", "source:published", "catalog:before",
                "catalog:published", null, Instant.now().toString(), List.of()));
        String now = Instant.now().toString();
        fixture.bundleRegistry.save(new RuntimeBundleRecord(
                "managed-sales", "sales", artifact.toString(), false, true,
                now, now, true, revision));
        Map<String, String> before = fingerprint(
                fixture.workspaceRoot, fixture.publishedRoot,
                fixture.registryPath);

        ArtifactLifecycleInventory inventory = fixture.inventory.inventory();

        assertThat(inventory.health()).isEqualTo("HEALTHY");
        assertThat(inventory.blockedReasons()).isEmpty();
        assertThat(object(inventory, "artifact:" + attemptId).referenceClass())
                .isEqualTo(RuntimeArtifactLifecycleInventoryService.MUST_RETAIN);
        assertThat(object(inventory, "artifact:" + attemptId).references())
                .contains("attempt:" + attemptId + ":candidate",
                        "bundle:sales:managed-sales:current",
                        "workspace:" + workspace.workspaceId() + ":base",
                        "workspace:" + workspace.workspaceId() + ":candidate");
        assertThat(object(inventory, "attempt:" + attemptId).referenceClass())
                .isEqualTo(RuntimeArtifactLifecycleInventoryService.MUST_RETAIN);
        assertThat(object(inventory,
                "workspace:" + workspace.workspaceId()
                        + ":revision:" + revision).referenceClass())
                .isEqualTo(RuntimeArtifactLifecycleInventoryService.MUST_RETAIN);
        assertThat(inventory.toString()).doesNotContain(
                fixture.workspaceRoot.toString(), fixture.publishedRoot.toString(),
                "healthy-model", "storeId", "auth-code");
        assertThat(fingerprint(fixture.workspaceRoot, fixture.publishedRoot,
                fixture.registryPath)).isEqualTo(before);
        assertThat(inventory.objects()).isSortedAccordingTo(
                Comparator.comparing(InventoryObject::store)
                        .thenComparing(InventoryObject::type)
                        .thenComparing(InventoryObject::identity));
        assertThat(inventory.objects()).allSatisfy(value ->
                assertThat(value.references()).isSorted().doesNotHaveDuplicates());
        assertThat(inventory.summary().totalObjects())
                .isEqualTo(inventory.objects().size());
        assertThat(inventory.summary().totalBytes())
                .isEqualTo(inventory.roots().stream()
                        .mapToLong(ArtifactLifecycleInventory.RootHealth::bytes)
                        .sum());
    }

    @Test
    void activeLeaseKeepsSupersededWorkspaceRevisionRetainedDuringScan()
            throws Exception {
        Fixture fixture = fixture("lease");
        var workspace = fixture.workspaceStore.create(
                "sales", "managed-sales", "source:base", SOURCE_IDENTITY,
                Map.of("Order.tm", bytes("base")));
        var firstCandidate = fixture.workspaceStore.replace(
                workspace.workspaceId(), workspace.candidateRevision(),
                Map.of("Order.tm", bytes("candidate-v1")));
        String oldRevision = firstCandidate.candidateRevision();

        try (var lease = fixture.workspaceStore.acquire(
                workspace.workspaceId(), oldRevision, "test")) {
            fixture.workspaceStore.replace(
                    workspace.workspaceId(), oldRevision,
                    Map.of("Order.tm", bytes("v2")));
            Map<String, String> before = fingerprint(fixture.workspaceRoot);

            ArtifactLifecycleInventory inventory = fixture.inventory.inventory();

            InventoryObject old = object(inventory,
                    "workspace:" + workspace.workspaceId()
                            + ":revision:" + oldRevision);
            assertThat(old.referenceClass())
                    .isEqualTo(RuntimeArtifactLifecycleInventoryService.MUST_RETAIN);
            assertThat(old.references()).contains(
                    "workspace:" + workspace.workspaceId() + ":active-lease");
            assertThat(fingerprint(fixture.workspaceRoot)).isEqualTo(before);
        }
    }

    @Test
    void rollbackAttemptRetainsBothCandidateAndPreviousImmutableArtifacts()
            throws Exception {
        Fixture fixture = fixture("rollback-references");
        Map<String, byte[]> previousSnapshot = Map.of(
                "Order.tm", bytes("previous-model"));
        Map<String, byte[]> candidateSnapshot = Map.of(
                "Order.tm", bytes("candidate-model"));
        String previousRevision = CandidateContentRevision.calculate(
                previousSnapshot);
        String candidateRevision = CandidateContentRevision.calculate(
                candidateSnapshot);
        String previousId = fixture.artifactStore.newAttemptId();
        String candidateId = fixture.artifactStore.newAttemptId();
        Path previous = fixture.artifactStore.prepareArtifact(
                previousId, "workspace-opaque", "sales", "managed-sales",
                previousRevision, previousSnapshot);
        fixture.artifactStore.prepareArtifact(
                candidateId, "workspace-opaque", "sales", "managed-sales",
                candidateRevision, candidateSnapshot);
        String now = Instant.now().toString();
        PublicationAttempt attempt = new PublicationAttempt(
                1, candidateId, "workspace-opaque", "sales", "managed-sales",
                candidateRevision, previousRevision, "source:base",
                previous.toString(), false, true, now, now, true,
                previousRevision, "PUBLISHED", "source:candidate",
                "catalog:before", "catalog:candidate", null, now, now,
                List.of(), new RollbackAttempt(
                "ROLLING_BACK", now, null, null, null, null, null, List.of()));
        fixture.artifactStore.begin(attempt);
        Map<String, String> before = fingerprint(fixture.publishedRoot);

        ArtifactLifecycleInventory inventory = fixture.inventory.inventory();

        assertThat(object(inventory, "artifact:" + candidateId).referenceClass())
                .isEqualTo(RuntimeArtifactLifecycleInventoryService.MUST_RETAIN);
        assertThat(object(inventory, "artifact:" + candidateId).references())
                .contains("attempt:" + candidateId + ":candidate");
        assertThat(object(inventory, "artifact:" + previousId).referenceClass())
                .isEqualTo(RuntimeArtifactLifecycleInventoryService.MUST_RETAIN);
        assertThat(object(inventory, "artifact:" + previousId).references())
                .contains("attempt:" + candidateId + ":previous");
        assertThat(object(inventory, "attempt:" + candidateId).status())
                .isEqualTo("PUBLISHED/ROLLING_BACK");
        assertThat(fingerprint(fixture.publishedRoot)).isEqualTo(before);
    }

    @Test
    void invalidLivePathRetainsMatchingArtifactByRedactedRevisionFallback()
            throws Exception {
        Fixture fixture = fixture("live-revision-fallback");
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("live-model"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String attemptId = fixture.artifactStore.newAttemptId();
        fixture.artifactStore.prepareArtifact(
                attemptId, "workspace-opaque", "sales", "managed-sales",
                revision, snapshot);
        String now = Instant.now().toString();
        fixture.bundleRegistry.save(new RuntimeBundleRecord(
                "managed-sales", "sales",
                tempDirectory.resolve("foreign-live-source").toString(),
                false, true, now, now, true, revision));
        Map<String, String> before = fingerprint(
                fixture.publishedRoot, fixture.registryPath);

        ArtifactLifecycleInventory inventory = fixture.inventory.inventory();

        InventoryObject artifact = object(inventory, "artifact:" + attemptId);
        assertThat(artifact.referenceClass()).isEqualTo(
                RuntimeArtifactLifecycleInventoryService.MUST_RETAIN);
        assertThat(artifact.references()).contains(
                "bundle:sales:managed-sales:current-by-revision");
        assertThat(inventory.blockedReasons()).contains(
                "LIVE_PUBLISHED_SOURCE_IDENTITY_INVALID");
        assertThat(fingerprint(fixture.publishedRoot, fixture.registryPath))
                .isEqualTo(before);
    }

    @Test
    void completeUnreferencedWorkspaceRevisionIsOnlyARecoveryCandidate()
            throws Exception {
        Fixture fixture = fixture("obsolete-revision");
        var workspace = fixture.workspaceStore.create(
                "sales", "managed-sales", "source:base", SOURCE_IDENTITY,
                Map.of("Order.tm", bytes("current")));
        Map<String, byte[]> obsolete = Map.of("Order.tm", bytes("obsolete"));
        String revision = CandidateContentRevision.calculate(obsolete);
        Path revisionRoot = fixture.workspaceRoot.resolve(workspace.workspaceId())
                .resolve("revisions").resolve(revision.substring("sha256:".length()));
        Files.createDirectories(revisionRoot);
        Files.write(revisionRoot.resolve("Order.tm"), obsolete.get("Order.tm"));
        Map<String, String> before = fingerprint(fixture.workspaceRoot);

        ArtifactLifecycleInventory inventory = fixture.inventory.inventory();

        InventoryObject candidate = object(inventory,
                "workspace:" + workspace.workspaceId() + ":revision:" + revision);
        assertThat(candidate.referenceClass()).isEqualTo(
                RuntimeArtifactLifecycleInventoryService.CANDIDATE);
        assertThat(candidate.references()).isEmpty();
        assertThat(fingerprint(fixture.workspaceRoot)).isEqualTo(before);
    }

    @Test
    void singleInitializedRootIsReportedAsPartialWithoutCreatingTheOtherRoot()
            throws Exception {
        Fixture fixture = fixture("partial");
        fixture.workspaceStore.create(
                "sales", "managed-sales", "source:base", SOURCE_IDENTITY,
                Map.of("Order.tm", bytes("current")));
        Map<String, String> before = fingerprint(fixture.workspaceRoot);

        ArtifactLifecycleInventory inventory = fixture.inventory.inventory();

        assertThat(inventory.health()).isEqualTo("PARTIAL");
        assertThat(inventory.roots())
                .extracting(ArtifactLifecycleInventory.RootHealth::store,
                        ArtifactLifecycleInventory.RootHealth::health)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("WORKSPACE", "HEALTHY"),
                        org.assertj.core.groups.Tuple.tuple(
                                "PUBLISHED", "NOT_INITIALIZED"));
        assertThat(fixture.publishedRoot).doesNotExist();
        assertThat(fingerprint(fixture.workspaceRoot)).isEqualTo(before);
    }

    @Test
    void interruptedPublishedWritesAreBlockedAndPreservedByteForByte()
            throws Exception {
        Fixture fixture = fixture("interrupted");
        Map<String, byte[]> committed = Map.of("Order.tm", bytes("committed"));
        fixture.artifactStore.prepareArtifact(
                fixture.artifactStore.newAttemptId(), "workspace-opaque",
                "sales", "managed-sales",
                CandidateContentRevision.calculate(committed), committed);
        Path legacyStaging = Files.createDirectory(
                fixture.publishedRoot.resolve("artifacts")
                        .resolve(".staging-" + UUID.randomUUID()));
        Files.createDirectories(legacyStaging.resolve("a"));
        Files.writeString(legacyStaging.resolve("a/Order.tm"), "partial-model");
        String attemptId = fixture.artifactStore.newAttemptId();
        Path temporary = Files.writeString(
                fixture.publishedRoot.resolve("attempts").resolve(
                        attemptId + ".json.tmp-" + UUID.randomUUID()),
                "partial-attempt-metadata");
        Map<String, String> before = fingerprint(fixture.publishedRoot);

        ArtifactLifecycleInventory inventory = fixture.inventory.inventory();

        assertThat(inventory.health()).isEqualTo("BLOCKED");
        assertThat(inventory.blockedReasons()).contains(
                "PUBLISHED_ARTIFACT_STAGING_UNOWNED",
                "PUBLICATION_METADATA_TEMPORARY");
        assertThat(inventory.objects())
                .filteredOn(value -> "PUBLISHED_ARTIFACT_STAGING".equals(value.type()))
                .singleElement()
                .satisfies(value -> assertThat(value.referenceClass())
                        .isEqualTo(RuntimeArtifactLifecycleInventoryService.UNKNOWN_PRESERVE));
        assertThat(temporary).hasContent("partial-attempt-metadata");
        assertThat(fingerprint(fixture.publishedRoot)).isEqualTo(before);
    }

    @Test
    void ownedInterruptedWritesAreReportedAsRecoveryPendingWithoutMutation()
            throws Exception {
        Fixture fixture = fixture("owned-interrupted");
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("committed"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String committedId = fixture.artifactStore.newAttemptId();
        fixture.artifactStore.prepareArtifact(
                committedId, "workspace-opaque", "sales", "managed-sales",
                revision, snapshot);
        fixture.artifactStore.begin(attempt(
                committedId, "workspace-opaque", revision,
                tempDirectory.resolve("previous-source").toString()));
        String storeId = publishedStoreId(fixture.publishedRoot);

        String interruptedId = fixture.artifactStore.newAttemptId();
        String stagingName = ".staging-" + UUID.randomUUID();
        Path owner = Files.writeString(fixture.publishedRoot.resolve("artifacts")
                .resolve(stagingName + ".owner.json"), artifactOwner(
                storeId, stagingName, interruptedId, revision));
        Path staging = Files.createDirectory(fixture.publishedRoot
                .resolve("artifacts").resolve(stagingName));
        Files.writeString(staging.resolve("Order.tm"), "partial");

        Path finalAttempt = fixture.publishedRoot.resolve("attempts")
                .resolve(committedId + ".json");
        String temporaryName = committedId + ".json.tmp-" + UUID.randomUUID();
        Path temporary = Files.writeString(finalAttempt.resolveSibling(
                temporaryName), Files.readString(finalAttempt));
        Path temporaryOwner = Files.writeString(finalAttempt.resolveSibling(
                temporaryName + ".owner.json"), attemptOwner(
                storeId, temporaryName, committedId));
        Map<String, String> before = fingerprint(fixture.publishedRoot);

        ArtifactLifecycleInventory inventory = fixture.inventory.inventory();

        assertThat(inventory.health()).isEqualTo("BLOCKED");
        assertThat(inventory.blockedReasons()).contains(
                "PUBLISHED_ARTIFACT_RECOVERY_PENDING",
                "PUBLICATION_METADATA_RECOVERY_PENDING");
        assertThat(inventory.objects())
                .filteredOn(value -> value.type().endsWith("RECOVERY_PENDING"))
                .hasSize(2)
                .allSatisfy(value -> assertThat(value.referenceClass())
                        .isEqualTo(RuntimeArtifactLifecycleInventoryService.UNKNOWN_PRESERVE));
        assertThat(fingerprint(fixture.publishedRoot)).isEqualTo(before);
        assertThat(owner).isRegularFile();
        assertThat(staging).isDirectory();
        assertThat(temporary).isRegularFile();
        assertThat(temporaryOwner).isRegularFile();
    }

    @Test
    void corruptArtifactSymlinkAndForeignEntryAreNotFollowedOrDeleted()
            throws Exception {
        Fixture fixture = fixture("corrupt");
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("original"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String attemptId = fixture.artifactStore.newAttemptId();
        Path artifact = fixture.artifactStore.prepareArtifact(
                attemptId, "workspace-opaque", "sales", "managed-sales",
                revision, snapshot);
        Path foreignTarget = Files.writeString(
                tempDirectory.resolve("foreign-target.txt"), "preserve-target");
        Path symlink = TestFileSystemSupport.createSymbolicLinkOrSkip(
                artifact.resolve("linked.tm"), foreignTarget);
        Files.writeString(artifact.resolve("Order.tm"), "tampered");
        Path sentinel = Files.writeString(
                fixture.publishedRoot.resolve("foreign.txt"), "preserve-root");
        Map<String, String> before = fingerprint(
                fixture.publishedRoot, foreignTarget);

        ArtifactLifecycleInventory inventory = fixture.inventory.inventory();

        assertThat(inventory.health()).isEqualTo("BLOCKED");
        assertThat(inventory.blockedReasons()).contains(
                "PUBLISHED_ARTIFACT_CORRUPT", "PUBLISHED_UNKNOWN_ENTRY");
        assertThat(object(inventory, "artifact:" + attemptId).referenceClass())
                .isEqualTo(RuntimeArtifactLifecycleInventoryService.UNKNOWN_PRESERVE);
        assertThat(symlink).isSymbolicLink();
        assertThat(foreignTarget).hasContent("preserve-target");
        assertThat(sentinel).hasContent("preserve-root");
        assertThat(fingerprint(fixture.publishedRoot, foreignTarget))
                .isEqualTo(before);
    }

    @Test
    void corruptAttemptPreventsOtherwiseValidArtifactFromBecomingCandidate()
            throws Exception {
        Fixture fixture = fixture("corrupt-attempt");
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("valid-model"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String attemptId = fixture.artifactStore.newAttemptId();
        fixture.artifactStore.prepareArtifact(
                attemptId, "workspace-opaque", "sales", "managed-sales",
                revision, snapshot);
        Files.writeString(fixture.publishedRoot.resolve("attempts")
                .resolve(attemptId + ".json"), "{not-json");
        Map<String, String> before = fingerprint(fixture.publishedRoot);

        ArtifactLifecycleInventory inventory = fixture.inventory.inventory();

        InventoryObject artifact = object(inventory, "artifact:" + attemptId);
        assertThat(artifact.referenceClass()).isEqualTo(
                RuntimeArtifactLifecycleInventoryService.UNKNOWN_PRESERVE);
        assertThat(artifact.blockedReason()).isEqualTo(
                "ARTIFACT_REFERENCE_GRAPH_INCOMPLETE");
        assertThat(inventory.blockedReasons()).contains(
                "PUBLICATION_ATTEMPT_CORRUPT",
                "ARTIFACT_REFERENCE_GRAPH_INCOMPLETE");
        assertThat(fingerprint(fixture.publishedRoot)).isEqualTo(before);
    }

    @Test
    void corruptWorkspaceRegistryConservativelyPreservesPublishedArtifact()
            throws Exception {
        Fixture fixture = fixture("corrupt-workspace-registry");
        fixture.workspaceStore.create(
                "sales", "managed-sales", "source:base", SOURCE_IDENTITY,
                Map.of("Order.tm", bytes("workspace-model")));
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("artifact-model"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String attemptId = fixture.artifactStore.newAttemptId();
        fixture.artifactStore.prepareArtifact(
                attemptId, "workspace-opaque", "sales", "managed-sales",
                revision, snapshot);
        Files.writeString(fixture.workspaceRoot.resolve("workspaces.json"),
                "{not-json");
        Map<String, String> before = fingerprint(
                fixture.workspaceRoot, fixture.publishedRoot);

        ArtifactLifecycleInventory inventory = fixture.inventory.inventory();

        assertThat(object(inventory, "artifact:" + attemptId).referenceClass())
                .isEqualTo(RuntimeArtifactLifecycleInventoryService.UNKNOWN_PRESERVE);
        assertThat(inventory.blockedReasons()).contains(
                "WORKSPACE_REGISTRY_CORRUPT",
                "ARTIFACT_REFERENCE_GRAPH_INCOMPLETE");
        assertThat(fingerprint(fixture.workspaceRoot, fixture.publishedRoot))
                .isEqualTo(before);
    }

    @Test
    void unreadableLiveRegistryConservativelyPreservesPublishedArtifact()
            throws Exception {
        Fixture fixture = fixture("unreadable-live-registry");
        Map<String, byte[]> snapshot = Map.of("Order.tm", bytes("artifact-model"));
        String revision = CandidateContentRevision.calculate(snapshot);
        String attemptId = fixture.artifactStore.newAttemptId();
        fixture.artifactStore.prepareArtifact(
                attemptId, "workspace-opaque", "sales", "managed-sales",
                revision, snapshot);
        Files.writeString(fixture.registryPath, "{not-json");
        Map<String, String> before = fingerprint(
                fixture.publishedRoot, fixture.registryPath);

        ArtifactLifecycleInventory inventory = fixture.inventory.inventory();

        assertThat(object(inventory, "artifact:" + attemptId).referenceClass())
                .isEqualTo(RuntimeArtifactLifecycleInventoryService.UNKNOWN_PRESERVE);
        assertThat(inventory.blockedReasons()).contains(
                "LIVE_REGISTRY_UNREADABLE",
                "ARTIFACT_REFERENCE_GRAPH_INCOMPLETE");
        assertThat(fingerprint(fixture.publishedRoot, fixture.registryPath))
                .isEqualTo(before);
    }

    private Fixture fixture(String name) {
        Path workspaceRoot = tempDirectory.resolve(name + "-workspaces");
        Path publishedRoot = tempDirectory.resolve(name + "-published");
        Path registryPath = tempDirectory.resolve(name + "-bundles.json");
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getAuthoringWorkspaces().setPath(workspaceRoot.toString());
        properties.getAuthoringWorkspaces().setPublishedBundlesPath(
                publishedRoot.toString());
        properties.getBundleRegistry().setPath(registryPath.toString());
        ObjectMapper mapper = new ObjectMapper();
        RuntimeBundleRegistryService registry = new RuntimeBundleRegistryService(
                properties, mock(SystemBundlesContext.class), mapper);
        RuntimeAuthoringWorkspaceStore workspaceStore =
                new RuntimeAuthoringWorkspaceStore(properties, mapper);
        RuntimeAuthoringPublicationLock lock =
                new RuntimeAuthoringPublicationLock();
        RuntimePublishedBundleArtifactStore artifactStore =
                new RuntimePublishedBundleArtifactStore(
                        properties, mapper, null, null);
        RuntimeArtifactLifecycleInventoryService inventory =
                new RuntimeArtifactLifecycleInventoryService(
                        properties, mapper, lock, workspaceStore, registry);
        return new Fixture(workspaceRoot, publishedRoot, registryPath,
                workspaceStore, artifactStore, registry, inventory);
    }

    private static PublicationAttempt attempt(
            String attemptId,
            String workspaceId,
            String revision,
            String previousPath
    ) {
        String now = Instant.now().toString();
        return new PublicationAttempt(
                1, attemptId, workspaceId, "sales", "managed-sales",
                revision, revision, "source:base", previousPath,
                false, true, now, now, false, null,
                "PUBLISHING", null, null, null, null,
                now, null, List.of());
    }

    private static InventoryObject object(
            ArtifactLifecycleInventory inventory,
            String identity
    ) {
        return inventory.objects().stream()
                .filter(value -> identity.equals(value.identity()))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, String> fingerprint(Path... roots)
            throws Exception {
        Map<String, String> result = new TreeMap<>();
        for (int index = 0; index < roots.length; index++) {
            Path root = roots[index];
            String prefix = "root-" + index;
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                result.put(prefix, "missing");
                continue;
            }
            if (Files.isSymbolicLink(root)) {
                result.put(prefix, "link:" + Files.readSymbolicLink(root));
                continue;
            }
            if (Files.isRegularFile(root, LinkOption.NOFOLLOW_LINKS)) {
                result.put(prefix, fileIdentity(root));
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.toList()) {
                    String relative = root.equals(path) ? ""
                            : root.relativize(path).toString().replace('\\', '/');
                    String key = prefix + "/" + relative;
                    if (Files.isSymbolicLink(path)) {
                        result.put(key, "link:" + Files.readSymbolicLink(path));
                    } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        result.put(key, "directory");
                    } else {
                        result.put(key, fileIdentity(path));
                    }
                }
            }
        }
        return result;
    }

    private static String fileIdentity(Path path) throws Exception {
        return Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis()
                + ":" + sha256(Files.readAllBytes(path));
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String publishedStoreId(Path root) throws Exception {
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
                "attemptId":"%s","workspaceId":"workspace-opaque",\
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

    private record Fixture(
            Path workspaceRoot,
            Path publishedRoot,
            Path registryPath,
            RuntimeAuthoringWorkspaceStore workspaceStore,
            RuntimePublishedBundleArtifactStore artifactStore,
            RuntimeBundleRegistryService bundleRegistry,
            RuntimeArtifactLifecycleInventoryService inventory
    ) {
    }
}
