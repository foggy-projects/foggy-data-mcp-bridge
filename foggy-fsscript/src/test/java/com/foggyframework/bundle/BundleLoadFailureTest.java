package com.foggyframework.bundle;

import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.fsscript.closure.file.ResourceFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.loadder.FsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class BundleLoadFailureTest {

    @TempDir
    Path tempDir;

    @Test
    void bundleImplShouldReloadWhenCachedPathNoLongerHasLoadedScript() throws IOException {
        Path scriptFile = createScriptFile();
        Fsscript loadedScript = mock(Fsscript.class);
        ReloadingBundle bundle = new ReloadingBundle(new FileSystemResource(scriptFile));
        bundle.setName("classpath-bundle");
        bundle.getName2Path().put("model.fsscript", "stale-path");
        RecordingLoader loader = new RecordingLoader(null, loadedScript);

        Fsscript loaded = bundle.loadFsscript("model.fsscript", loader, true);

        assertSame(loadedScript, loaded);
        assertEquals(1, loader.stringLookupCount.get());
        assertEquals(1, loader.urlLookupCount.get());
        assertEquals(ResourceFsscriptClosureDefinitionSpace.getResourcePath(new FileSystemResource(scriptFile)),
                bundle.getName2Path().get("model.fsscript"));
    }

    @Test
    void externalFileBundleShouldReloadWhenCachedPathNoLongerHasLoadedScript() throws IOException {
        Path scriptFile = createScriptFile();
        Fsscript loadedScript = mock(Fsscript.class);
        ReloadingExternalFileBundle bundle = new ReloadingExternalFileBundle(new FileSystemResource(scriptFile));
        bundle.setName("external-bundle");
        bundle.getName2Path().put("model.fsscript", "stale-path");
        RecordingLoader loader = new RecordingLoader(null, loadedScript);

        Fsscript loaded = bundle.loadFsscript("model.fsscript", loader, true);

        assertSame(loadedScript, loaded);
        assertEquals(1, loader.stringLookupCount.get());
        assertEquals(1, loader.urlLookupCount.get());
        assertEquals(ResourceFsscriptClosureDefinitionSpace.getResourcePath(new FileSystemResource(scriptFile)),
                bundle.getName2Path().get("model.fsscript"));
    }

    @Test
    void bundleImplShouldNotSwallowSeriousLoaderErrorsFromCachedPath() {
        AssertionError loaderError = new AssertionError("serious loader failure");
        ReloadingBundle bundle = new ReloadingBundle(null);
        bundle.getName2Path().put("model.fsscript", "stale-path");
        RecordingLoader loader = new RecordingLoader(loaderError, null);

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> bundle.loadFsscript("model.fsscript", loader, true));

        assertSame(loaderError, thrown);
    }

    @Test
    void externalFileBundleShouldNotSwallowSeriousLoaderErrorsFromCachedPath() {
        AssertionError loaderError = new AssertionError("serious loader failure");
        ReloadingExternalFileBundle bundle = new ReloadingExternalFileBundle(null);
        bundle.getName2Path().put("model.fsscript", "stale-path");
        RecordingLoader loader = new RecordingLoader(loaderError, null);

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> bundle.loadFsscript("model.fsscript", loader, true));

        assertSame(loaderError, thrown);
    }

    private Path createScriptFile() throws IOException {
        Path scriptFile = tempDir.resolve("model.fsscript");
        Files.writeString(scriptFile, "export var value = 1;");
        return scriptFile;
    }

    private static class ReloadingBundle extends BundleImpl {
        private final FileSystemResource resource;

        ReloadingBundle(FileSystemResource resource) {
            super(null);
            this.resource = resource;
        }

        @Override
        public BundleResource findBundleResource(String name, boolean errorIfNotFound) {
            if (resource == null) {
                throw new IllegalStateException("resource lookup should not happen");
            }
            return new BundleResource(this, resource);
        }
    }

    private static class ReloadingExternalFileBundle extends ExternalFileBundle {
        private final FileSystemResource resource;

        ReloadingExternalFileBundle(FileSystemResource resource) {
            super(null);
            this.resource = resource;
        }

        @Override
        public BundleResource findBundleResource(String name, boolean errorIfNotFound) {
            if (resource == null) {
                throw new IllegalStateException("resource lookup should not happen");
            }
            return new BundleResource(this, resource);
        }
    }

    private static class RecordingLoader extends FsscriptLoader {
        private final AssertionError stringLookupError;
        private final Fsscript loadedScript;
        private final AtomicInteger stringLookupCount = new AtomicInteger();
        private final AtomicInteger urlLookupCount = new AtomicInteger();

        RecordingLoader(AssertionError stringLookupError, Fsscript loadedScript) {
            super(null);
            this.stringLookupError = stringLookupError;
            this.loadedScript = loadedScript;
        }

        @Override
        public Fsscript findLoadFsscript(String path) {
            stringLookupCount.incrementAndGet();
            if (stringLookupError != null) {
                throw stringLookupError;
            }
            return null;
        }

        @Override
        public ApplicationContext getAppCtx() {
            return null;
        }

        @Override
        public Fsscript findLoadFsscript(URL fscriptPath) {
            urlLookupCount.incrementAndGet();
            return loadedScript;
        }
    }
}
