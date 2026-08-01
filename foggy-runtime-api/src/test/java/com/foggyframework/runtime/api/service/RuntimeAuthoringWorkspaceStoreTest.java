package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.candidate.CandidateContentRevision;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeAuthoringWorkspaceStoreTest {

    private static final String SOURCE_IDENTITY =
            "sha256:" + "1".repeat(64);

    @TempDir
    Path tempDirectory;

    @Test
    void nonemptyUnownedRootFailsWithoutMutatingSentinel() throws Exception {
        Path root = Files.createDirectory(tempDirectory.resolve("unowned"));
        Path nested = Files.createDirectory(root.resolve("business-data"));
        Path sentinel = Files.writeString(nested.resolve("sentinel.txt"),
                "must survive byte-for-byte");

        assertCode(() -> store(root).list(null, null, true),
                "WORKSPACE_STORE_FAILURE");

        assertThat(sentinel).hasContent("must survive byte-for-byte");
        try (var entries = Files.walk(root)) {
            assertThat(entries.map(root::relativize)
                    .map(Path::toString)
                    .toList())
                    .containsExactlyInAnyOrder("", "business-data",
                            "business-data/sentinel.txt");
        }
    }

    @Test
    void initializesVersionTwoRootAndWorkspaceOwnership() throws Exception {
        Path root = tempDirectory.resolve("owned-v2");
        RuntimeAuthoringWorkspaceStore store = store(root);

        var created = create(store);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode registry = mapper.readTree(root.resolve("workspaces.json").toFile());
        String storeId = registry.path("storeId").asText();
        JsonNode marker = mapper.readTree(root.resolve(created.workspaceId())
                .resolve(".workspace-owner.json").toFile());
        assertThat(registry.path("version").asInt()).isEqualTo(2);
        assertThat(storeId).matches("[A-Za-z0-9_-]{22,64}");
        assertThat(marker.path("version").asInt()).isEqualTo(1);
        assertThat(marker.path("storeId").asText()).isEqualTo(storeId);
        assertThat(marker.path("workspaceId").asText())
                .isEqualTo(created.workspaceId());
    }

    @Test
    void restartDeletesOnlyOwnedOrphansAndPreservesForeignEntries()
            throws Exception {
        Path ownedRoot = tempDirectory.resolve("owned-cleanup");
        RuntimeAuthoringWorkspaceStore first = store(ownedRoot);
        create(first);
        String storeId = new ObjectMapper().readTree(
                ownedRoot.resolve("workspaces.json").toFile())
                .path("storeId").asText();
        String orphanId = "A".repeat(24);
        Path orphan = Files.createDirectory(ownedRoot.resolve(orphanId));
        writeMarker(orphan, storeId, orphanId);
        Files.createDirectory(orphan.resolve("revisions"));

        store(ownedRoot).list(null, null, true);

        assertThat(orphan).doesNotExist();

        Path foreignRoot = tempDirectory.resolve("foreign-cleanup");
        RuntimeAuthoringWorkspaceStore foreignFirst = store(foreignRoot);
        create(foreignFirst);
        String foreignStoreId = new ObjectMapper().readTree(
                        foreignRoot.resolve("workspaces.json").toFile())
                .path("storeId").asText();
        String ownedSiblingId = "D".repeat(24);
        Path ownedSibling = Files.createDirectory(
                foreignRoot.resolve(ownedSiblingId));
        writeMarker(ownedSibling, foreignStoreId, ownedSiblingId);
        Files.createDirectory(ownedSibling.resolve("revisions"));
        String foreignId = "B".repeat(24);
        Path foreign = Files.createDirectory(foreignRoot.resolve(foreignId));
        writeMarker(foreign, "C".repeat(24), foreignId);
        Path sentinel = Files.writeString(foreign.resolve("sentinel.txt"),
                "foreign");

        assertCode(() -> store(foreignRoot).list(null, null, true),
                "WORKSPACE_STORE_CORRUPT");
        assertThat(sentinel).hasContent("foreign");
        assertThat(ownedSibling).isDirectory();
    }

    @Test
    void restartCleansOnlyOwnershipBearingCrashStagingAndOldRevision()
            throws Exception {
        Path root = tempDirectory.resolve("owned-staging");
        RuntimeAuthoringWorkspaceStore first = store(root);
        var created = create(first);
        Path workspace = root.resolve(created.workspaceId());
        String uuid = "00000000-0000-0000-0000-000000000000";
        Path registryStaging = root.resolve("workspaces.json.tmp-" + uuid);
        Files.copy(root.resolve("workspaces.json"), registryStaging);
        Path markerStaging = workspace.resolve(
                ".workspace-owner.json.tmp-" + uuid);
        Files.copy(workspace.resolve(".workspace-owner.json"), markerStaging);
        Path snapshotStaging = Files.createDirectory(
                workspace.resolve(".staging-" + uuid));
        Files.writeString(snapshotStaging.resolve("Order.tm"), "partial");
        Map<String, byte[]> oldSnapshot = Map.of(
                "Old.tm", bytes("old revision"));
        String oldRevision = CandidateContentRevision.calculate(oldSnapshot);
        Path oldRevisionPath = Files.createDirectory(workspace
                .resolve("revisions")
                .resolve(oldRevision.substring("sha256:".length())));
        Files.write(oldRevisionPath.resolve("Old.tm"), oldSnapshot.get("Old.tm"));

        RuntimeAuthoringWorkspaceStore restarted = store(root);
        restarted.list(null, null, true);

        assertThat(registryStaging).doesNotExist();
        assertThat(markerStaging).doesNotExist();
        assertThat(snapshotStaging).doesNotExist();
        assertThat(oldRevisionPath).doesNotExist();
        assertThat(restarted.get(created.workspaceId())).isEqualTo(created);
    }

    @Test
    void unknownOwnedRootEntryAndSymlinkStagingFailClosed()
            throws Exception {
        Path unknownRoot = tempDirectory.resolve("unknown-owned-entry");
        create(store(unknownRoot));
        Path unknown = Files.writeString(unknownRoot.resolve("operator.txt"),
                "keep");

        assertCode(() -> store(unknownRoot).list(null, null, true),
                "WORKSPACE_STORE_CORRUPT");
        assertThat(unknown).hasContent("keep");

        Path foreignTempRoot = tempDirectory.resolve("foreign-registry-staging");
        create(store(foreignTempRoot));
        Path foreignRegistryStaging = foreignTempRoot.resolve(
                "workspaces.json.tmp-00000000-0000-0000-0000-000000000000");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                foreignRegistryStaging.toFile(),
                Map.of("version", 2, "storeId", "Z".repeat(24),
                        "workspaces", List.of()));
        assertCode(() -> store(foreignTempRoot).list(null, null, true),
                "WORKSPACE_STORE_CORRUPT");
        assertThat(foreignRegistryStaging).isRegularFile();

        Path symlinkRoot = tempDirectory.resolve("symlink-staging");
        var created = create(store(symlinkRoot));
        Path staging = Files.createDirectory(symlinkRoot
                .resolve(created.workspaceId())
                .resolve(".staging-00000000-0000-0000-0000-000000000000"));
        Path outside = Files.writeString(
                tempDirectory.resolve("staging-outside.txt"), "outside");
        Path link = Files.createSymbolicLink(staging.resolve("link"), outside);

        assertCode(() -> store(symlinkRoot).list(null, null, true),
                "WORKSPACE_STORE_CORRUPT");
        assertThat(link).isSymbolicLink();
        assertThat(outside).hasContent("outside");
    }

    @Test
    void migratesLegacyStoreWithoutLosingStateOrTombstones()
            throws Exception {
        Path root = tempDirectory.resolve("legacy");
        LegacyFixture fixture = writeLegacyStore(root);

        RuntimeAuthoringWorkspaceStore migrated = store(root);
        List<RuntimeAuthoringWorkspaceStore.StoredWorkspace> records =
                migrated.list(null, null, true);

        assertThat(records).containsExactly(
                fixture.active(), fixture.tombstone());
        JsonNode registry = new ObjectMapper().readTree(
                root.resolve("workspaces.json").toFile());
        assertThat(registry.path("version").asInt()).isEqualTo(2);
        assertThat(registry.path("storeId").asText())
                .matches("[A-Za-z0-9_-]{22,64}");
        assertThat(root.resolve(fixture.active().workspaceId())
                .resolve(".workspace-owner.json")).isRegularFile();
        assertThat(root.resolve(".migration-v2.json")).doesNotExist();
        assertThat(migrated.snapshot(
                fixture.active().workspaceId(),
                fixture.active().candidateRevision()).get("Order.tm"))
                .isEqualTo(bytes("legacy-head"));
        var migratedDiff = migrated.diff(
                fixture.active().workspaceId(),
                fixture.active().candidateRevision(), true);
        assertThat(migratedDiff.changes()).singleElement()
                .satisfies(change -> {
                    assertThat(change.baseContent()).isEqualTo("legacy-base");
                    assertThat(change.candidateContent()).isEqualTo("legacy-head");
                });
    }

    @Test
    void resumesInterruptedLegacyMigrationIdempotently() throws Exception {
        Path root = tempDirectory.resolve("legacy-retry");
        LegacyFixture fixture = writeLegacyStore(root);
        Path registry = root.resolve("workspaces.json");
        String storeId = "D".repeat(24);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                root.resolve(".migration-v2.json").toFile(),
                Map.of("version", 1, "storeId", storeId,
                        "registrySha256", digest(Files.readAllBytes(registry))));
        writeMarker(root.resolve(fixture.active().workspaceId()),
                storeId, fixture.active().workspaceId());

        RuntimeAuthoringWorkspaceStore migrated = store(root);

        assertThat(migrated.get(fixture.active().workspaceId()))
                .isEqualTo(fixture.active());
        JsonNode migratedRegistry = new ObjectMapper().readTree(registry.toFile());
        assertThat(migratedRegistry.path("version").asInt()).isEqualTo(2);
        assertThat(migratedRegistry.path("storeId").asText()).isEqualTo(storeId);
        assertThat(root.resolve(".migration-v2.json")).doesNotExist();
    }

    @Test
    void invalidLegacyLayoutIsPreservedWithoutStartingMigration()
            throws Exception {
        Path root = tempDirectory.resolve("legacy-invalid");
        writeLegacyStore(root);
        Path sentinel = Files.writeString(root.resolve("foreign.txt"), "keep");

        assertCode(() -> store(root).list(null, null, true),
                "WORKSPACE_STORE_CORRUPT");

        assertThat(sentinel).hasContent("keep");
        assertThat(new ObjectMapper().readTree(
                root.resolve("workspaces.json").toFile())
                .path("version").asInt()).isEqualTo(1);
        assertThat(root.resolve(".migration-v2.json")).doesNotExist();
    }

    @Test
    void persistsExactBaseHeadMetadataAndDiffAcrossRestart() {
        Path root = tempDirectory.resolve("store");
        RuntimeAuthoringWorkspaceStore first = store(root);
        var created = first.create(
                "sales", "managed-sales", "source:boot:g0:n0",
                SOURCE_IDENTITY,
                Map.of(
                        "model/Order.tm", bytes("base tm"),
                        "query/Order.qm", bytes("base qm"),
                        "shared/common.fsscript", bytes("base script")));
        Map<String, byte[]> changed = new java.util.TreeMap<>(
                first.snapshot(created.workspaceId(), created.candidateRevision()));
        changed.put("query/Order.qm", bytes("candidate qm"));
        changed.remove("model/Order.tm");
        changed.put("model/NewOrder.tm", bytes("new tm"));
        var updated = first.replace(
                created.workspaceId(), created.candidateRevision(), changed);

        RuntimeAuthoringWorkspaceStore restarted = store(root);
        var recovered = restarted.get(created.workspaceId());

        assertThat(recovered).isEqualTo(updated);
        assertThat(restarted.snapshot(
                recovered.workspaceId(), recovered.candidateRevision()))
                .containsOnlyKeys("model/NewOrder.tm", "query/Order.qm",
                        "shared/common.fsscript");
        var diff = restarted.diff(
                recovered.workspaceId(), recovered.candidateRevision(), true);
        assertThat(diff.changes()).extracting(
                        change -> change.path() + ":" + change.changeType())
                .containsExactly(
                        "model/NewOrder.tm:ADDED",
                        "model/Order.tm:DELETED",
                        "query/Order.qm:MODIFIED");
        assertThat(diff.changes().get(2).candidateContent())
                .isEqualTo("candidate qm");
    }

    @Test
    void rejectsUnsafePathsStrictUtf8CaseCollisionAndQuota() {
        FoggyRuntimeApiProperties properties = properties(
                tempDirectory.resolve("limits"));
        properties.getAuthoringWorkspaces().setMaxResourceBytes(4);
        properties.getAuthoringWorkspaces().setMaxResourcesPerRevision(2);
        RuntimeAuthoringWorkspaceStore store = new RuntimeAuthoringWorkspaceStore(
                properties, new ObjectMapper());

        assertCode(() -> store.canonicalResourcePath(
                        "../escape.tm", "test"),
                "WORKSPACE_RESOURCE_PATH_INVALID");
        assertCode(() -> store.canonicalResourcePath(
                        "/absolute.tm", "test"),
                "WORKSPACE_RESOURCE_PATH_INVALID");
        assertCode(() -> store.canonicalResourcePath(
                        "model\\Order.tm", "test"),
                "WORKSPACE_RESOURCE_PATH_INVALID");
        assertCode(() -> store.canonicalResourcePath(
                        "notes.txt", "test"),
                "WORKSPACE_RESOURCE_TYPE_UNSUPPORTED");
        assertCode(() -> store.create(
                        "sales", "managed-sales", "source:1",
                        SOURCE_IDENTITY,
                        Map.of("Order.tm", bytes("12345"))),
                "WORKSPACE_LIMIT_EXCEEDED");
        assertCode(() -> store.create(
                        "sales", "managed-sales", "source:1",
                        SOURCE_IDENTITY,
                        Map.of("Order.tm", bytes("a"),
                                "order.tm", bytes("b"))),
                "WORKSPACE_RESOURCE_PATH_INVALID");
        assertCode(() -> store.create(
                        "sales", "managed-sales", "source:1",
                        SOURCE_IDENTITY,
                        Map.of("Order.tm", new byte[]{(byte) 0xc3, 0x28})),
                "WORKSPACE_INVALID_REQUEST");
        assertThat(store.list(null, null, true)).isEmpty();
    }

    @Test
    void persistenceFailureLeavesHeadStateAndVisibleFilesUnchanged()
            throws Exception {
        Path root = tempDirectory.resolve("persist-failure");
        RuntimeAuthoringWorkspaceStore store = store(root);
        var created = create(store);
        Path registry = root.resolve("workspaces.json");
        Files.delete(registry);
        Files.createDirectory(registry);

        assertCode(() -> store.replace(
                        created.workspaceId(), created.candidateRevision(),
                        Map.of("Order.tm", bytes("changed"))),
                "WORKSPACE_STORE_FAILURE");

        assertThat(store.get(created.workspaceId()).candidateRevision())
                .isEqualTo(created.candidateRevision());
        assertThat(store.snapshot(
                created.workspaceId(), created.candidateRevision())
                .get("Order.tm")).isEqualTo(bytes("base"));
    }

    @Test
    void commitGuardFailureLeavesHeadStateEvidenceAndFilesUnchanged()
            throws Exception {
        Path root = tempDirectory.resolve("commit-guard-failure");
        RuntimeAuthoringWorkspaceStore store = store(root);
        var created = create(store);
        var evidence = new com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo
                .ValidationEvidence(
                true,
                created.candidateRevision(),
                created.baseBundleRevision(),
                created.baseSourceRevision(),
                Instant.now().toString(),
                1, 1, 0, 0, List.of());
        var validated = store.recordValidation(
                created.workspaceId(), created.candidateRevision(), evidence);
        Path workspacePath = root.resolve(created.workspaceId());

        assertCode(() -> store.replace(
                        validated.workspaceId(), validated.candidateRevision(),
                        Map.of("Order.tm", bytes("changed")),
                        () -> {
                            throw RuntimeAuthoringWorkspaceStore.failure(
                                    "WORKSPACE_OVERLAY_FORBIDDEN",
                                    "workspaces.resources.save",
                                    "Candidate resource belongs to another Bundle.",
                                    "Order.tm", false);
                        }),
                "WORKSPACE_OVERLAY_FORBIDDEN");

        assertThat(store.get(created.workspaceId())).isEqualTo(validated);
        assertThat(store.snapshot(
                created.workspaceId(), created.candidateRevision())
                .get("Order.tm")).isEqualTo(bytes("base"));
        try (var revisions = Files.list(workspacePath.resolve("revisions"));
             var workspaceChildren = Files.list(workspacePath)) {
            assertThat(revisions.toList()).hasSize(1);
            assertThat(workspaceChildren
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(".staging-"))
                    .toList()).isEmpty();
        }
    }

    @Test
    void sameRevisionConcurrentWritersHaveExactlyOneWinner()
            throws Exception {
        RuntimeAuthoringWorkspaceStore store = store(
                tempDirectory.resolve("concurrent"));
        var created = create(store);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (String content : List.of("first", "second")) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        store.replace(created.workspaceId(),
                                created.candidateRevision(),
                                Map.of("Order.tm", bytes(content)));
                        return "committed";
                    } catch (RuntimeAuthoringWorkspaceException failure) {
                        return failure.code();
                    }
                }));
            }
            ready.await();
            start.countDown();
            assertThat(List.of(futures.get(0).get(), futures.get(1).get()))
                    .containsExactlyInAnyOrder(
                            "committed", "WORKSPACE_REVISION_CONFLICT");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void revisionLeasePinsOldHeadUntilReaderCloses() {
        RuntimeAuthoringWorkspaceStore store = store(
                tempDirectory.resolve("leases"));
        var created = create(store);
        var firstHead = store.replace(
                created.workspaceId(), created.candidateRevision(),
                Map.of("Order.tm", bytes("first")));
        RuntimeAuthoringWorkspaceStore.RevisionLease lease = store.acquire(
                firstHead.workspaceId(), firstHead.candidateRevision(), "test");
        Path leasedPath = lease.path();

        store.replace(firstHead.workspaceId(), firstHead.candidateRevision(),
                Map.of("Order.tm", bytes("second")));

        assertThat(leasedPath).isDirectory();
        lease.close();
        assertThat(leasedPath).doesNotExist();
    }

    @Test
    void discardIsTerminalAndRetainsOnlyTombstoneMetadata() {
        RuntimeAuthoringWorkspaceStore store = store(
                tempDirectory.resolve("discard"));
        var created = create(store);
        var discarded = store.discard(
                created.workspaceId(), created.candidateRevision());

        assertThat(discarded.state())
                .isEqualTo(AuthoringWorkspaceState.DISCARDED);
        assertThat(store.get(created.workspaceId()).state())
                .isEqualTo(AuthoringWorkspaceState.DISCARDED);
        assertCode(() -> store.snapshot(
                        created.workspaceId(), created.candidateRevision()),
                "WORKSPACE_STATE_INVALID");
        assertCode(() -> store.replace(
                        created.workspaceId(), created.candidateRevision(),
                        Map.of("Order.tm", bytes("new"))),
                "WORKSPACE_STATE_INVALID");
    }

    @Test
    void detectsSymlinkHashMismatchMissingFileAndUnknownSchemaAsCorruption()
            throws Exception {
        Path root = tempDirectory.resolve("corruption");
        RuntimeAuthoringWorkspaceStore store = store(root);
        var created = create(store);
        Path outside = Files.writeString(
                tempDirectory.resolve("outside.tm"), "outside");
        try (var lease = store.acquire(
                created.workspaceId(), created.candidateRevision(), "test")) {
            Files.createSymbolicLink(
                    lease.path().resolve("linked.tm"), outside);
        }
        assertCode(() -> store.snapshot(
                        created.workspaceId(), created.candidateRevision()),
                "WORKSPACE_STORE_CORRUPT");

        RuntimeAuthoringWorkspaceStore hashStore = store(
                tempDirectory.resolve("hash-corruption"));
        var hashWorkspace = create(hashStore);
        try (var lease = hashStore.acquire(
                hashWorkspace.workspaceId(),
                hashWorkspace.candidateRevision(), "test")) {
            Files.writeString(lease.path().resolve("Order.tm"), "tampered");
        }
        assertCode(() -> hashStore.snapshot(
                        hashWorkspace.workspaceId(),
                        hashWorkspace.candidateRevision()),
                "WORKSPACE_STORE_CORRUPT");

        RuntimeAuthoringWorkspaceStore missingStore = store(
                tempDirectory.resolve("missing-file-corruption"));
        var missingWorkspace = create(missingStore);
        try (var lease = missingStore.acquire(
                missingWorkspace.workspaceId(),
                missingWorkspace.candidateRevision(), "test")) {
            Files.delete(lease.path().resolve("Order.tm"));
        }
        assertCode(() -> missingStore.snapshot(
                        missingWorkspace.workspaceId(),
                        missingWorkspace.candidateRevision()),
                "WORKSPACE_STORE_CORRUPT");

        Path unknownRoot = tempDirectory.resolve("unknown-schema");
        Files.createDirectories(unknownRoot);
        Files.writeString(unknownRoot.resolve("workspaces.json"),
                "{\"version\":99,\"workspaces\":[]}");
        assertCode(() -> store(unknownRoot).list(null, null, true),
                "WORKSPACE_STORE_CORRUPT");
    }

    private RuntimeAuthoringWorkspaceStore store(Path root) {
        return new RuntimeAuthoringWorkspaceStore(
                properties(root), new ObjectMapper());
    }

    private static FoggyRuntimeApiProperties properties(Path root) {
        FoggyRuntimeApiProperties properties =
                new FoggyRuntimeApiProperties();
        properties.getAuthoringWorkspaces().setPath(root.toString());
        return properties;
    }

    private static RuntimeAuthoringWorkspaceStore.StoredWorkspace create(
            RuntimeAuthoringWorkspaceStore store
    ) {
        return store.create(
                "sales", "managed-sales", "source:boot:g0:n0",
                SOURCE_IDENTITY, Map.of("Order.tm", bytes("base")));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void writeMarker(
            Path workspace,
            String storeId,
            String workspaceId
    ) throws Exception {
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                workspace.resolve(".workspace-owner.json").toFile(),
                Map.of("version", 1, "storeId", storeId,
                        "workspaceId", workspaceId));
    }

    private static LegacyFixture writeLegacyStore(Path root) throws Exception {
        Files.createDirectories(root);
        Map<String, byte[]> baseSnapshot = Map.of(
                "Order.tm", bytes("legacy-base"));
        Map<String, byte[]> candidateSnapshot = Map.of(
                "Order.tm", bytes("legacy-head"));
        String baseRevision = CandidateContentRevision.calculate(baseSnapshot);
        String candidateRevision = CandidateContentRevision.calculate(
                candidateSnapshot);
        String now = Instant.parse("2026-08-01T00:00:00Z").toString();
        AuthoringWorkspaceInfo.ValidationEvidence evidence =
                new AuthoringWorkspaceInfo.ValidationEvidence(
                        true, candidateRevision, baseRevision,
                        "source:boot:g0:n0", now,
                        1, 1, 0, 0, List.of());
        RuntimeAuthoringWorkspaceStore.StoredWorkspace active =
                new RuntimeAuthoringWorkspaceStore.StoredWorkspace(
                        "L".repeat(24), "sales", "managed-sales",
                        "runtime-managed", SOURCE_IDENTITY, baseRevision,
                        "source:boot:g0:n0", candidateRevision,
                        AuthoringWorkspaceState.VALIDATED, now, now,
                        evidence, null);
        RuntimeAuthoringWorkspaceStore.StoredWorkspace tombstone =
                new RuntimeAuthoringWorkspaceStore.StoredWorkspace(
                        "T".repeat(24), "sales", "managed-sales",
                        "runtime-managed", SOURCE_IDENTITY, baseRevision,
                        "source:boot:g0:n0", candidateRevision,
                        AuthoringWorkspaceState.DISCARDED, now, now,
                        evidence, null);
        Path baseRevisionRoot = Files.createDirectories(root
                .resolve(active.workspaceId()).resolve("revisions")
                .resolve(baseRevision.substring("sha256:".length())));
        Files.write(baseRevisionRoot.resolve("Order.tm"),
                baseSnapshot.get("Order.tm"));
        Path candidateRevisionRoot = Files.createDirectory(root
                .resolve(active.workspaceId()).resolve("revisions")
                .resolve(candidateRevision.substring("sha256:".length())));
        Files.write(candidateRevisionRoot.resolve("Order.tm"),
                candidateSnapshot.get("Order.tm"));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                root.resolve("workspaces.json").toFile(),
                Map.of("version", 1,
                        "workspaces", List.of(active, tombstone)));
        return new LegacyFixture(active, tombstone);
    }

    private static String digest(byte[] value) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value));
    }

    private record LegacyFixture(
            RuntimeAuthoringWorkspaceStore.StoredWorkspace active,
            RuntimeAuthoringWorkspaceStore.StoredWorkspace tombstone
    ) {
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(RuntimeAuthoringWorkspaceException.class)
                .satisfies(error -> assertThat(
                        ((RuntimeAuthoringWorkspaceException) error).code())
                        .isEqualTo(code));
    }
}
