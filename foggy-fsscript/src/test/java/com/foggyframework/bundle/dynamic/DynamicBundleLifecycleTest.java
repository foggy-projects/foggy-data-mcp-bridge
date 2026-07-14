package com.foggyframework.bundle.dynamic;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.utils.file.WatchServiceFileTracer;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.closure.file.ResourceFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler;
import com.foggyframework.fsscript.loadder.FsscriptRemoveEvent;
import com.foggyframework.fsscript.loadder.RootFsscriptLoader;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
class DynamicBundleLifecycleTest {

    @Autowired
    private SystemBundlesContext systemBundlesContext;

    @Autowired
    private RootFsscriptLoader rootFsscriptLoader;

    @Autowired
    private FsscriptFileChangeHandler changeHandler;

    @Autowired
    private CommittedSourceRevisionRegistry sourceRevisionRegistry;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @TempDir
    Path tempDir;

    private String bundleName;

    @BeforeEach
    void setUp() {
        rootFsscriptLoader.clear();
        bundleName = "lifecycle-bundle-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        systemBundlesContext.removeBundle(bundleName);
        rootFsscriptLoader.clear();
    }

    @Test
    void removeExternalBundleShouldClearLoadedFsscriptCache() throws IOException {
        Path bundleRoot = tempDir.resolve("bundle-root");
        Files.createDirectories(bundleRoot);
        Files.writeString(bundleRoot.resolve("model.fsscript"), "export var value = 1;");

        assertTrue(systemBundlesContext.addExternalBundle(bundleName, "test", bundleRoot.toString(), false));
        BundleResource resource = systemBundlesContext.findResourceByName("model.fsscript", "test", true);
        String scriptPath = ResourceFsscriptClosureDefinitionSpace.getResourcePath(resource.getResource());

        Fsscript loaded = FileFsscriptLoader.getInstance().findLoadFsscript(resource);

        assertNotNull(loaded);
        assertNotNull(rootFsscriptLoader.findLoadFsscript(scriptPath));

        assertTrue(systemBundlesContext.removeBundle(bundleName));

        assertNull(rootFsscriptLoader.findLoadFsscript(scriptPath),
                "removing an external bundle should remove loaded scripts that belong to that bundle");
    }

    @Test
    void removeExternalBundleShouldUnwatchLoadedFsscriptFiles() throws IOException {
        Path bundleRoot = tempDir.resolve("watch-bundle-root");
        Files.createDirectories(bundleRoot);
        Files.writeString(bundleRoot.resolve("model.fsscript"), "export var value = 1;");

        assertTrue(systemBundlesContext.addExternalBundle(bundleName, "test", bundleRoot.toString(), true));
        BundleResource resource = systemBundlesContext.findResourceByName("model.fsscript", "test", true);
        File scriptFile = resource.getFile();

        Fsscript loaded = FileFsscriptLoader.getInstance().findLoadFsscript(resource);

        assertNotNull(loaded);
        assertTrue(isWatchedByChangeHandler(scriptFile),
                "loading a real file resource should register file watcher");

        assertTrue(systemBundlesContext.removeBundle(bundleName));

        assertFalse(isWatchedByChangeHandler(scriptFile),
                "removing an external bundle should unregister watchers for scripts under that bundle root");
    }

    @Test
    void watchEnabledExternalBundleMustCommitExactSourceEventForNewQmInNewSubdirectory()
            throws Exception {
        assertTrue(WatchServiceFileTracer.getInstance().isAvailable(),
                "the production directory-watcher contract requires WatchService");
        Path bundleRoot = tempDir.resolve("new-model-watch-root");
        Files.createDirectories(bundleRoot);
        String namespace = "test";
        CountDownLatch createdEvent = new CountDownLatch(1);
        AtomicReference<FsscriptRemoveEvent> observed = new AtomicReference<>();
        AtomicBoolean revisionCommittedAtPublication = new AtomicBoolean();

        assertTrue(systemBundlesContext.addExternalBundle(
                bundleName, namespace, bundleRoot.toString(), true));
        String revisionBeforeCreate = sourceRevisionRegistry.currentRevision(namespace);
        Path newQm = bundleRoot.resolve("query").resolve("NewRuntimeModel.qm");
        String expectedResource = newQm.toFile().getCanonicalPath();
        ApplicationListener<FsscriptRemoveEvent> listener = sourceEvent -> {
            if (sourceEvent.getAffectedResources().contains(expectedResource)) {
                observed.set(sourceEvent);
                revisionCommittedAtPublication.set(
                        sourceEvent.getCommittedSourceRevisions().get(namespace)
                                .equals(sourceRevisionRegistry.currentRevision(namespace)));
                createdEvent.countDown();
            }
        };
        applicationContext.addApplicationListener(listener);
        try {
            // Deliberately write immediately after creating the directory. The
            // production handler must register and scan the new subtree so this
            // real WatchService race cannot lose the source mutation.
            Files.createDirectories(newQm.getParent());
            Files.writeString(newQm, "export const queryModel = {};\n");

            assertTrue(createdEvent.await(8, TimeUnit.SECONDS),
                    "a newly created QM must enter the committed source lifecycle without restart");
            FsscriptRemoveEvent sourceEvent = observed.get();
            assertNotNull(sourceEvent);
            assertTrue(sourceEvent.isScopeKnown());
            assertEquals(java.util.Set.of(namespace), sourceEvent.getAffectedNamespaces());
            assertTrue(revisionCommittedAtPublication.get(),
                    "the synchronous event must observe its already committed revision");
            assertNotEquals(revisionBeforeCreate,
                    sourceEvent.getCommittedSourceRevisions().get(namespace));
            assertEquals(List.of(expectedResource), sourceEvent.getAffectedResources());
            assertTrue(isWatchedByChangeHandler(newQm.toFile()),
                    "the new source must be watched for later modify/delete events");
            assertTrue(isDirectoryWatched(bundleRoot));
            assertTrue(isDirectoryWatched(newQm.getParent()));

            assertTrue(systemBundlesContext.removeBundle(bundleName));

            assertFalse(isWatchedByChangeHandler(newQm.toFile()));
            assertFalse(isDirectoryWatched(bundleRoot));
            assertFalse(isDirectoryWatched(newQm.getParent()));
        } finally {
            applicationEventMulticaster().removeApplicationListener(listener);
        }
    }

    @Test
    void watchDisabledExternalBundleMustNotRegisterDirectoryAuthority()
            throws IOException {
        Path bundleRoot = tempDir.resolve("watch-disabled-root");
        Files.createDirectories(bundleRoot);

        assertTrue(systemBundlesContext.addExternalBundle(
                bundleName, "test", bundleRoot.toString(), false));

        assertFalse(isDirectoryWatched(bundleRoot));
    }

    @Test
    void sharedRootMustRetainWatcherAndPublishAllRegisteredNamespaces()
            throws Exception {
        assertTrue(WatchServiceFileTracer.getInstance().isAvailable(),
                "the production directory-watcher contract requires WatchService");
        Path bundleRoot = tempDir.resolve("shared-watch-root");
        Files.createDirectories(bundleRoot);
        String secondBundle = bundleName + "-second";
        Path firstQm = bundleRoot.resolve("SharedFirst.qm");
        Path secondQm = bundleRoot.resolve("SharedSecond.qm");
        String firstResource = firstQm.toFile().getCanonicalPath();
        String secondResource = secondQm.toFile().getCanonicalPath();
        CountDownLatch firstCreated = new CountDownLatch(1);
        CountDownLatch secondCreated = new CountDownLatch(1);
        AtomicReference<FsscriptRemoveEvent> firstObserved = new AtomicReference<>();
        AtomicReference<FsscriptRemoveEvent> secondObserved = new AtomicReference<>();
        ApplicationListener<FsscriptRemoveEvent> listener = sourceEvent -> {
            if (sourceEvent.getAffectedResources().contains(firstResource)) {
                firstObserved.set(sourceEvent);
                firstCreated.countDown();
            }
            if (sourceEvent.getAffectedResources().contains(secondResource)) {
                secondObserved.set(sourceEvent);
                secondCreated.countDown();
            }
        };

        assertTrue(systemBundlesContext.addExternalBundle(
                bundleName, "test", bundleRoot.toString(), true));
        assertTrue(systemBundlesContext.addExternalBundle(
                secondBundle, "other", bundleRoot.toString(), true));
        applicationContext.addApplicationListener(listener);
        try {
            Files.writeString(firstQm, "export const queryModel = {};\n");
            assertTrue(firstCreated.await(8, TimeUnit.SECONDS));
            assertEquals(java.util.Set.of("test", "other"),
                    firstObserved.get().getAffectedNamespaces());

            assertTrue(systemBundlesContext.removeBundle(bundleName));
            assertTrue(isDirectoryWatched(bundleRoot),
                    "removing one shared-root bundle must retain the other authority");

            Files.writeString(secondQm, "export const queryModel = {};\n");
            assertTrue(secondCreated.await(8, TimeUnit.SECONDS));
            assertEquals(java.util.Set.of("other"),
                    secondObserved.get().getAffectedNamespaces());

            assertTrue(systemBundlesContext.removeBundle(secondBundle));
            assertFalse(isDirectoryWatched(bundleRoot));
        } finally {
            applicationEventMulticaster().removeApplicationListener(listener);
            systemBundlesContext.removeBundle(secondBundle);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isWatchedByChangeHandler(File file) {
        Object watchServiceTracer = readField(changeHandler, "watchServiceTracer");
        Map<Path, ?> fileListeners = (Map<Path, ?>) readField(watchServiceTracer, "fileListeners");
        Path normalizedFile = file.toPath().toAbsolutePath().normalize();
        if (fileListeners.containsKey(normalizedFile)) {
            return true;
        }

        Object legacyTracer = readField(changeHandler, "legacyTracer");
        if (legacyTracer == null) {
            return false;
        }

        Object scanner = readStaticField(com.foggyframework.core.utils.file.FileTracer.class, "scaner");
        return containsLegacyFileListener(scanner, "files", legacyTracer, file)
                || containsLegacyFileListener(scanner, "tmpFiles", legacyTracer, file);
    }

    @SuppressWarnings("unchecked")
    private boolean isDirectoryWatched(Path directory) {
        Object watchServiceTracer = readField(changeHandler, "watchServiceTracer");
        Map<Path, ?> directoryListeners =
                (Map<Path, ?>) readField(watchServiceTracer, "directoryListeners");
        return directoryListeners.containsKey(directory.toAbsolutePath().normalize());
    }

    private ApplicationEventMulticaster applicationEventMulticaster() {
        return applicationContext.getBean(
                AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
                ApplicationEventMulticaster.class);
    }

    private boolean containsLegacyFileListener(Object scanner, String fieldName, Object expectedTracer, File expectedFile) {
        @SuppressWarnings("unchecked")
        List<Object> listeners = (List<Object>) readField(scanner, fieldName);
        for (Object listener : listeners.toArray()) {
            Object tracer = readField(listener, "tracer");
            File file = (File) readField(listener, "file");
            if (tracer == expectedTracer && expectedFile.equals(file)) {
                return true;
            }
        }
        return false;
    }

    private Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read field: " + fieldName, e);
        }
    }

    private Object readStaticField(Class<?> targetClass, String fieldName) {
        try {
            Field field = targetClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read static field: " + fieldName, e);
        }
    }
}
