package com.foggyframework.fsscript.loadder;

import com.foggyframework.core.utils.file.WatchAuthorityLossReason;
import com.foggyframework.core.utils.file.WatchServiceFileTracer;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
class FsscriptFileChangeHandlerAuthorityTest {

    @Autowired
    private RootFsscriptLoader rootFsscriptLoader;

    @Autowired
    private CommittedSourceRevisionRegistry sourceRevisionRegistry;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @TempDir
    Path tempDir;

    @Test
    void sourceCreatedAfterInitialSnapshotBeforeRegistrationMustCommitExactlyOnce()
            throws Exception {
        assertTrue(WatchServiceFileTracer.getInstance().isAvailable());
        Path bundleRoot = Files.createDirectory(tempDir.resolve("scan-race-root"));
        Path runtimeDirectory = bundleRoot.resolve("runtime");
        Path model = runtimeDirectory.resolve("RaceModel.qm");
        Path stagedModel = tempDir.resolve("RaceModel.staged");
        Path barrier = runtimeDirectory.resolve("watch-barrier.txt");
        String namespace = "scan-race";
        FreezingScanner scanner = new FreezingScanner(runtimeDirectory);
        CountDownLatch barrierObserved = new CountDownLatch(1);
        BarrierHandler handler = new BarrierHandler(
                rootFsscriptLoader,
                sourceRevisionRegistry,
                scanner,
                barrier,
                barrierObserved);
        AtomicInteger matchingEvents = new AtomicInteger();
        AtomicReference<FsscriptRemoveEvent> observed = new AtomicReference<>();
        CountDownLatch committed = new CountDownLatch(1);
        String expectedResource = model.toFile().getCanonicalPath();
        ApplicationListener<FsscriptRemoveEvent> listener = event -> {
            if (event.getAffectedResources().contains(expectedResource)) {
                observed.set(event);
                matchingEvents.incrementAndGet();
                committed.countDown();
            }
        };
        applicationContext.addApplicationListener(listener);
        try {
            assertTrue(handler.watchExternalBundle(
                    bundleRoot.toString(), namespace));
            String revisionBefore = sourceRevisionRegistry.currentRevision(namespace);
            Files.writeString(stagedModel, "export const queryModel = {};\n");

            Files.createDirectory(runtimeDirectory);
            assertTrue(scanner.snapshotTaken.await(5, TimeUnit.SECONDS),
                    "new-subtree handler must reach the deterministic scan/register boundary");
            Files.move(stagedModel, model);
            scanner.allowRegistration.countDown();

            assertTrue(committed.await(5, TimeUnit.SECONDS),
                    "post-registration reconciliation must commit the gap source");
            Files.writeString(barrier, "drain queued create events\n");
            assertTrue(barrierObserved.await(5, TimeUnit.SECONDS),
                    "same-key barrier must drain the queued scan/event race");

            FsscriptRemoveEvent event = observed.get();
            assertNotNull(event);
            assertTrue(event.isScopeKnown());
            assertEquals(Set.of(namespace), event.getAffectedNamespaces());
            assertEquals(1, matchingEvents.get(),
                    "reconciliation and queued create event must converge exactly once");
            assertNotEquals(revisionBefore,
                    sourceRevisionRegistry.currentRevision(namespace));
            assertTrue(isFileWatched(model));
            assertTrue(isDirectoryWatched(runtimeDirectory));
        } finally {
            scanner.allowRegistration.countDown();
            handler.unwatchExternalBundle(bundleRoot.toString(), namespace);
            applicationEventMulticaster().removeApplicationListener(listener);
        }
    }

    @Test
    void directoryDiscoveredByReconciliationMustForceAnotherStableScan()
            throws Exception {
        Path bundleRoot = Files.createDirectory(tempDir.resolve("fixed-point-root"));
        Path runtimeDirectory = bundleRoot.resolve("runtime");
        Path firstChild = runtimeDirectory.resolve("first");
        Path secondChild = firstChild.resolve("second");
        Path model = secondChild.resolve("SecondRoundRace.qm");
        Path barrier = secondChild.resolve("fixed-point-barrier.txt");
        GrowingTreeScanner scanner = new GrowingTreeScanner(
                runtimeDirectory, firstChild, secondChild, model);
        CountDownLatch barrierObserved = new CountDownLatch(1);
        BarrierHandler handler = new BarrierHandler(
                rootFsscriptLoader,
                sourceRevisionRegistry,
                scanner,
                barrier,
                barrierObserved);
        AtomicBoolean committedAfterStableScan = new AtomicBoolean();
        AtomicInteger matchingEvents = new AtomicInteger();
        CountDownLatch committed = new CountDownLatch(1);
        String expectedResource = model.toFile().getCanonicalPath();
        ApplicationListener<FsscriptRemoveEvent> listener = event -> {
            if (event.getAffectedResources().contains(expectedResource)) {
                committedAfterStableScan.set(scanner.targetScans.get() >= 3);
                matchingEvents.incrementAndGet();
                committed.countDown();
            }
        };
        applicationContext.addApplicationListener(listener);
        try {
            assertTrue(handler.watchExternalBundle(
                    bundleRoot.toString(), "fixed-point"));

            Files.createDirectory(runtimeDirectory);
            assertTrue(committed.await(5, TimeUnit.SECONDS));
            Files.writeString(barrier, "drain fixed-point events\n");
            assertTrue(barrierObserved.await(5, TimeUnit.SECONDS));

            assertTrue(committedAfterStableScan.get(),
                    "a directory first seen by reconciliation requires a later stable scan");
            assertEquals(3, scanner.targetScans.get());
            assertEquals(1, matchingEvents.get());
            assertTrue(isDirectoryWatched(firstChild));
            assertTrue(isDirectoryWatched(secondChild));
            assertTrue(isFileWatched(model));
        } finally {
            handler.unwatchExternalBundle(
                    bundleRoot.toString(), "fixed-point");
            applicationEventMulticaster().removeApplicationListener(listener);
        }
    }

    @Test
    void repeatedAuthorityLossMustCommitUnknownOnceAndPreserveSharedRoot()
            throws Exception {
        Path bundleRoot = Files.createDirectory(tempDir.resolve("authority-loss-root"));
        Path child = Files.createDirectory(bundleRoot.resolve("child"));
        Path source = child.resolve("Existing.qm");
        Files.writeString(source, "export const queryModel = {};\n");
        FsscriptFileChangeHandler handler = new FsscriptFileChangeHandler(
                rootFsscriptLoader, sourceRevisionRegistry);
        AtomicInteger unknownEvents = new AtomicInteger();
        AtomicReference<FsscriptRemoveEvent> observed = new AtomicReference<>();
        ApplicationListener<FsscriptRemoveEvent> listener = event -> {
            if (!event.isScopeKnown()
                    && event.getAffectedResources().contains(child.toFile().getAbsolutePath())) {
                observed.set(event);
                unknownEvents.incrementAndGet();
            }
        };
        applicationContext.addApplicationListener(listener);
        try {
            assertTrue(handler.watchExternalBundle(bundleRoot.toString(), "alpha"));
            assertTrue(handler.watchExternalBundle(bundleRoot.toString(), "beta"));
            String alphaBefore = sourceRevisionRegistry.currentRevision("alpha");
            String betaBefore = sourceRevisionRegistry.currentRevision("beta");

            handler.onWatchAuthorityLost(
                    child.toFile(), WatchAuthorityLossReason.EVENT_OVERFLOW);
            String alphaAfterFirst = sourceRevisionRegistry.currentRevision("alpha");
            String betaAfterFirst = sourceRevisionRegistry.currentRevision("beta");
            handler.onWatchAuthorityLost(
                    child.toFile(), WatchAuthorityLossReason.WATCH_KEY_INVALID);

            assertEquals(1, unknownEvents.get());
            assertNotNull(observed.get());
            assertFalse(observed.get().isScopeKnown());
            assertTrue(observed.get().getAffectedNamespaces().isEmpty());
            assertNotEquals(alphaBefore, alphaAfterFirst);
            assertNotEquals(betaBefore, betaAfterFirst);
            assertEquals(alphaAfterFirst,
                    sourceRevisionRegistry.currentRevision("alpha"));
            assertEquals(betaAfterFirst,
                    sourceRevisionRegistry.currentRevision("beta"));
            assertTrue(isDirectoryWatched(bundleRoot),
                    "losing one child authority must not remove the valid shared root watcher");
            assertFalse(isDirectoryWatched(child));
            assertFalse(isFileWatched(source));

            assertEquals(0, handler.unwatchExternalBundle(
                    bundleRoot.toString(), "alpha"));
            assertTrue(isDirectoryWatched(bundleRoot));
        } finally {
            handler.unwatchExternalBundle(bundleRoot.toString(), "alpha");
            handler.unwatchExternalBundle(bundleRoot.toString(), "beta");
            applicationEventMulticaster().removeApplicationListener(listener);
        }
    }

    @Test
    void sourceFileWatchRegistrationFailureMustCommitUnknownOnce()
            throws Exception {
        Path bundleRoot = Files.createDirectory(tempDir.resolve("file-watch-failure-root"));
        Path runtimeDirectory = bundleRoot.resolve("runtime" );
        Path disappearingSource = runtimeDirectory.resolve("Disappearing.qm");
        VanishingSourceScanner scanner = new VanishingSourceScanner(
                runtimeDirectory, disappearingSource);
        FsscriptFileChangeHandler handler = new FsscriptFileChangeHandler(
                rootFsscriptLoader, sourceRevisionRegistry, scanner);
        AtomicInteger unknownEvents = new AtomicInteger();
        AtomicReference<FsscriptRemoveEvent> observed = new AtomicReference<>();
        CountDownLatch committed = new CountDownLatch(1);
        ApplicationListener<FsscriptRemoveEvent> listener = event -> {
            if (!event.isScopeKnown()) {
                observed.set(event);
                unknownEvents.incrementAndGet();
                committed.countDown();
            }
        };
        applicationContext.addApplicationListener(listener);
        try {
            assertTrue(handler.watchExternalBundle(bundleRoot.toString(), "watch-failure"));
            String revisionBefore = sourceRevisionRegistry.currentRevision("watch-failure");

            Files.createDirectory(runtimeDirectory);
            assertTrue(committed.await(5, TimeUnit.SECONDS),
                    "an unwatchable scanned source must fail closed");
            handler.onWatchAuthorityLost(
                    runtimeDirectory.toFile(),
                    WatchAuthorityLossReason.WATCH_KEY_INVALID);

            assertEquals(1, unknownEvents.get());
            assertNotNull(observed.get());
            assertFalse(observed.get().isScopeKnown());
            assertNotEquals(revisionBefore,
                    sourceRevisionRegistry.currentRevision("watch-failure"));
            assertFalse(isFileWatched(disappearingSource));
        } finally {
            handler.unwatchExternalBundle(bundleRoot.toString(), "watch-failure");
            applicationEventMulticaster().removeApplicationListener(listener);
        }
    }

    private final class BarrierHandler extends FsscriptFileChangeHandler {
        private final Path barrier;
        private final CountDownLatch barrierObserved;

        private BarrierHandler(
                RootFsscriptLoader loader,
                CommittedSourceRevisionRegistry registry,
                DirectoryTreeScanner scanner,
                Path barrier,
                CountDownLatch barrierObserved
        ) {
            super(loader, registry, scanner);
            this.barrier = normalize(barrier);
            this.barrierObserved = barrierObserved;
        }

        @Override
        public void onFileCreated(File file) {
            super.onFileCreated(file);
            if (normalize(file.toPath()).equals(barrier)) {
                barrierObserved.countDown();
            }
        }
    }

    private static final class FreezingScanner
            implements FsscriptFileChangeHandler.DirectoryTreeScanner {
        private final Path target;
        private final AtomicBoolean frozen = new AtomicBoolean();
        private final CountDownLatch snapshotTaken = new CountDownLatch(1);
        private final CountDownLatch allowRegistration = new CountDownLatch(1);

        private FreezingScanner(Path target) {
            this.target = normalize(target);
        }

        @Override
        public List<Path> scan(Path root) throws IOException {
            List<Path> snapshot = scanTree(root);
            if (normalize(root).equals(target) && frozen.compareAndSet(false, true)) {
                snapshotTaken.countDown();
                try {
                    if (!allowRegistration.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting at scan/register boundary");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted at scan/register boundary", e);
                }
            }
            return snapshot;
        }
    }

    private static final class VanishingSourceScanner
            implements FsscriptFileChangeHandler.DirectoryTreeScanner {
        private final Path target;
        private final Path source;
        private final AtomicBoolean vanished = new AtomicBoolean();

        private VanishingSourceScanner(Path target, Path source) {
            this.target = normalize(target);
            this.source = normalize(source);
        }

        @Override
        public List<Path> scan(Path root) throws IOException {
            if (normalize(root).equals(target) && vanished.compareAndSet(false, true)) {
                Files.writeString(source, "export const queryModel = {};\n");
                List<Path> snapshot = scanTree(root);
                Files.delete(source);
                return snapshot;
            }
            return scanTree(root);
        }
    }

    private static final class GrowingTreeScanner
            implements FsscriptFileChangeHandler.DirectoryTreeScanner {
        private final Path target;
        private final Path firstChild;
        private final Path secondChild;
        private final Path source;
        private final AtomicInteger targetScans = new AtomicInteger();

        private GrowingTreeScanner(
                Path target,
                Path firstChild,
                Path secondChild,
                Path source
        ) {
            this.target = normalize(target);
            this.firstChild = normalize(firstChild);
            this.secondChild = normalize(secondChild);
            this.source = normalize(source);
        }

        @Override
        public List<Path> scan(Path root) throws IOException {
            if (!normalize(root).equals(target)) {
                return scanTree(root);
            }
            int invocation = targetScans.incrementAndGet();
            if (invocation == 1) {
                Files.createDirectories(firstChild);
                return scanTree(root);
            }
            if (invocation == 2) {
                Files.createDirectories(secondChild);
                List<Path> snapshot = scanTree(root);
                Path staged = source.resolveSibling(source.getFileName() + ".staged");
                Files.writeString(staged, "export const queryModel = {};\n");
                Files.move(staged, source);
                return snapshot;
            }
            return scanTree(root);
        }
    }

    private static List<Path> scanTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.toList();
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isDirectoryWatched(Path directory) {
        Map<Path, ?> listeners = (Map<Path, ?>) readField(
                WatchServiceFileTracer.getInstance(), "directoryListeners");
        return listeners.containsKey(normalize(directory));
    }

    @SuppressWarnings("unchecked")
    private boolean isFileWatched(Path file) {
        Map<Path, ?> listeners = (Map<Path, ?>) readField(
                WatchServiceFileTracer.getInstance(), "fileListeners");
        return listeners.containsKey(normalize(file));
    }

    private ApplicationEventMulticaster applicationEventMulticaster() {
        return applicationContext.getBean(
                AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
                ApplicationEventMulticaster.class);
    }

    private Object readField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read field " + name, e);
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
