package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(RuntimeAuthoringWorkspaceException.class)
                .satisfies(error -> assertThat(
                        ((RuntimeAuthoringWorkspaceException) error).code())
                        .isEqualTo(code));
    }
}
