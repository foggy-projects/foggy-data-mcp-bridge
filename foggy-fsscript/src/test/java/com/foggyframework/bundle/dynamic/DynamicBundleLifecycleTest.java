package com.foggyframework.bundle.dynamic;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.closure.file.ResourceFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler;
import com.foggyframework.fsscript.loadder.RootFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
