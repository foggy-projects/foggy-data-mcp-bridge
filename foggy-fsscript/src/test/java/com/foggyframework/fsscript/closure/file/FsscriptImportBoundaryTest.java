package com.foggyframework.fsscript.closure.file;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.loadder.RootFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
class FsscriptImportBoundaryTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RootFsscriptLoader rootFsscriptLoader;

    @Autowired
    private SystemBundlesContext systemBundlesContext;

    @TempDir
    Path tempDir;

    private Path bundleRoot;
    private ExternalFileBundle bundle;

    @BeforeEach
    void setUp() throws IOException {
        rootFsscriptLoader.clear();
        bundleRoot = tempDir.resolve("bundle-root");
        Files.createDirectories(bundleRoot.resolve("sub"));
        Files.writeString(bundleRoot.resolve("utils.fsscript"), "export var value = 7;");

        ExternalBundleDefinition definition = new ExternalBundleDefinition(
                "import-boundary-bundle", bundleRoot.toString(), false);
        bundle = new ExternalFileBundle(systemBundlesContext);
        bundle.setName("import-boundary-bundle");
        bundle.setBasePath(bundleRoot.toString());
        bundle.setRootPath(bundleRoot.toString());
        bundle.setBundleDefinition(definition);
    }

    @Test
    void relativeImportWithinBundleRootShouldStillWork() throws IOException {
        Path main = bundleRoot.resolve("sub/main.fsscript");
        Files.writeString(main, """
                import {value} from '../utils.fsscript';
                export var importedValue = value;
                """);

        Fsscript fsscript = loadFromBundle(main);
        ExpEvaluator evaluator = fsscript.eval(applicationContext);

        assertEquals(7, ((Number) evaluator.getExportObject("importedValue")).intValue());
    }

    @Test
    void relativeImportShouldNotEscapeBundleRoot() throws IOException {
        Path outside = tempDir.resolve("outside.fsscript");
        Files.writeString(outside, "export var leaked = 99;");

        Path main = bundleRoot.resolve("sub/main.fsscript");
        Files.writeString(main, """
                import {leaked} from '../../outside.fsscript';
                export var importedValue = leaked;
                """);

        assertThrows(RuntimeException.class, () -> {
            Fsscript fsscript = loadFromBundle(main);
            fsscript.eval(applicationContext);
        }, "relative imports must stay within the owning bundle root");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void classpathStarBundleShouldAllowSiblingImportFromExplodedClasses() throws Exception {
        Path classesRoot = tempDir.resolve("classes");
        Path templatesRoot = classesRoot.resolve("foggy/templates");
        Files.createDirectories(templatesRoot);
        Files.writeString(templatesRoot.resolve("FactOrderSettlementModel.fsscript"), """
                import {value} from './viewer.fsscript';
                export var importedValue = value;
                """);
        Files.writeString(templatesRoot.resolve("viewer.fsscript"), "export var value = 7;");

        try (URLClassLoader classLoader = classLoaderFor(classesRoot)) {
            ExternalFileBundle classpathBundle = createClasspathBundle("classpath*:foggy/templates");
            Resource mainScript = new ClassPathResource(
                    "foggy/templates/FactOrderSettlementModel.fsscript", classLoader);

            Fsscript fsscript = loadFromBundle(classpathBundle, mainScript);
            ExpEvaluator evaluator = fsscript.eval(applicationContext);

            assertEquals(7, ((Number) evaluator.getExportObject("importedValue")).intValue());
        }
    }

    @Test
    void classpathStarBundleShouldRejectRelativeImportEscapingRoot() throws Exception {
        Path classesRoot = tempDir.resolve("classpath-escape-classes");
        Path templatesRoot = classesRoot.resolve("foggy/templates/sub");
        Files.createDirectories(templatesRoot);
        Files.writeString(classesRoot.resolve("foggy/outside.fsscript"), "export var leaked = 99;");
        Files.writeString(templatesRoot.resolve("main.fsscript"), """
                import {leaked} from '../../outside.fsscript';
                export var importedValue = leaked;
                """);

        try (URLClassLoader classLoader = classLoaderFor(classesRoot)) {
            ExternalFileBundle classpathBundle = createClasspathBundle("classpath*:foggy/templates");
            Resource mainScript = new ClassPathResource("foggy/templates/sub/main.fsscript", classLoader);

            assertThrows(RuntimeException.class, () -> {
                Fsscript fsscript = loadFromBundle(classpathBundle, mainScript);
                fsscript.eval(applicationContext);
            }, "relative imports must stay within a classpath-backed bundle root");
        }
    }

    private Fsscript loadFromBundle(Path scriptPath) {
        return loadFromBundle(bundle, new FileSystemResource(scriptPath));
    }

    private Fsscript loadFromBundle(ExternalFileBundle owner, Resource scriptResource) {
        BundleResource bundleResource = new BundleResource(owner, scriptResource);
        return FileFsscriptLoader.getInstance().findLoadFsscript(bundleResource);
    }

    private ExternalFileBundle createClasspathBundle(String rootPath) {
        ExternalBundleDefinition definition = new ExternalBundleDefinition(
                "classpath-import-boundary-bundle", rootPath, false);
        ExternalFileBundle classpathBundle = new ExternalFileBundle(systemBundlesContext);
        classpathBundle.setName("classpath-import-boundary-bundle");
        classpathBundle.setBasePath(rootPath);
        classpathBundle.setRootPath(rootPath);
        classpathBundle.setBundleDefinition(definition);
        return classpathBundle;
    }

    private URLClassLoader classLoaderFor(Path classesRoot) throws IOException {
        URL classesUrl = classesRoot.toUri().toURL();
        return new URLClassLoader(new URL[]{classesUrl}, ClassLoader.getPlatformClassLoader());
    }
}
