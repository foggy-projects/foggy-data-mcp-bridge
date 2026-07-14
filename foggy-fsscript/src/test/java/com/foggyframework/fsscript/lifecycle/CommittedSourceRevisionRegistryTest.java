package com.foggyframework.fsscript.lifecycle;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommittedSourceRevisionRegistryTest {

    @Test
    void knownMutationAdvancesOnlyTheAffectedNamespace() {
        CommittedSourceRevisionRegistry registry =
                new CommittedSourceRevisionRegistry("test-epoch");
        String beforeA = registry.currentRevision("tenant-a");
        String beforeB = registry.currentRevision("tenant-b");

        CommittedSourceRevisionRegistry.MutationCommit<String> commit =
                registry.commitKnown(Set.of("tenant-a"), () -> "committed");

        assertEquals("committed", commit.value());
        assertEquals(Set.of("tenant-a"), commit.affectedNamespaces());
        assertNotEquals(beforeA, registry.currentRevision("tenant-a"));
        assertEquals(beforeB, registry.currentRevision("tenant-b"));
    }

    @Test
    void unknownMutationAdvancesEveryNamespaceView() {
        CommittedSourceRevisionRegistry registry =
                new CommittedSourceRevisionRegistry("test-epoch");
        String beforeA = registry.currentRevision("tenant-a");
        String beforeB = registry.currentRevision("tenant-b");

        CommittedSourceRevisionRegistry.MutationCommit<String> commit =
                registry.commitUnknown(() -> "committed");

        assertTrue(!commit.scopeKnown());
        assertNotEquals(beforeA, registry.currentRevision("tenant-a"));
        assertNotEquals(beforeB, registry.currentRevision("tenant-b"));
    }

    @Test
    void staleCapturedRevisionCannotPublish() {
        CommittedSourceRevisionRegistry registry =
                new CommittedSourceRevisionRegistry("test-epoch");
        String captured = registry.currentRevision("tenant-a");
        registry.commitKnown(Set.of("tenant-a"), () -> null);

        assertThrows(
                CommittedSourceRevisionRegistry
                        .CommittedSourceRevisionChangedException.class,
                () -> registry.publishIfCurrent(
                        "tenant-a", captured, () -> "must-not-publish"));
    }

    @Test
    void sourceMutationCannotInterleaveWithFinalPublicationGuard() {
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> {
                    CommittedSourceRevisionRegistry registry =
                            new CommittedSourceRevisionRegistry("test-epoch");
                    String captured = registry.currentRevision("tenant-a");
                    CountDownLatch publicationEntered = new CountDownLatch(1);
                    CountDownLatch allowPublication = new CountDownLatch(1);
                    CountDownLatch mutationAttempted = new CountDownLatch(1);
                    CountDownLatch mutationEntered = new CountDownLatch(1);
                    ExecutorService executor = Executors.newFixedThreadPool(2);
                    try {
                        Future<String> publication = executor.submit(() ->
                                registry.publishIfCurrent("tenant-a", captured, () -> {
                                    publicationEntered.countDown();
                                    await(allowPublication);
                                    return "published";
                                }));
                        await(publicationEntered);
                        Future<?> mutation = executor.submit(() -> {
                            mutationAttempted.countDown();
                            return registry.commitKnown(Set.of("tenant-a"), () -> {
                                    mutationEntered.countDown();
                                    return null;
                                });
                        });

                        await(mutationAttempted);
                        assertEquals(1L, mutationEntered.getCount(),
                                "mutation must wait behind the final source read guard");
                        allowPublication.countDown();
                        assertEquals("published", publication.get());
                        mutation.get();
                        assertEquals(0L, mutationEntered.getCount());
                    } finally {
                        allowPublication.countDown();
                        executor.shutdownNow();
                    }
                });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for deterministic test latch");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for test latch", interrupted);
        }
    }
}
