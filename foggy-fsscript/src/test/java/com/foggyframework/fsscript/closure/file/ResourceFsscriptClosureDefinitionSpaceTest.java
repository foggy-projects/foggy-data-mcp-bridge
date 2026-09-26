package com.foggyframework.fsscript.closure.file;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceFsscriptClosureDefinitionSpaceTest {

    @TempDir
    Path tempDir;

    @Test
    void classpathResourceInsideJarShouldNotRequireFile() throws Exception {
        Path jarPath = tempDir.resolve("fsscript-test-bundle.jar");
        writeJarResource(jarPath, "scripts/in-jar.fsscript", "export var value = 1;");

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jarPath.toUri().toURL()}, null)) {
            Resource resource = new ClassPathResource("scripts/in-jar.fsscript", classLoader);

            assertTrue(resource.exists(), "test jar resource should be resolvable from classpath");
            String identity = assertDoesNotThrow(
                    () -> ResourceFsscriptClosureDefinitionSpace.getResourcePath(resource),
                    "classpath resources loaded from a jar should have a stable identity without Resource#getFile()");
            assertTrue(identity.contains("scripts/in-jar.fsscript"));
        }
    }

    @Test
    void urlResourceIdentityShouldIncludeSchemeAndHost() throws Exception {
        Resource first = new UrlResource("http://host-a.example.com/same/model.fsscript");
        Resource second = new UrlResource("http://host-b.example.com/same/model.fsscript");

        String firstIdentity = ResourceFsscriptClosureDefinitionSpace.getResourcePath(first);
        String secondIdentity = ResourceFsscriptClosureDefinitionSpace.getResourcePath(second);

        assertNotEquals(firstIdentity, secondIdentity,
                "resource identity must not collapse different URL origins with the same path");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void classpathStarBundleShouldAllowSiblingImportFromExplodedClasses() throws Exception {
        Path classesRoot = tempDir.resolve("classes");
        Path templatesRoot = classesRoot.resolve("foggy/templates");
        Files.createDirectories(templatesRoot);
        Files.writeString(templatesRoot.resolve("main.fsscript"), "export var value = 1;");
        Files.writeString(templatesRoot.resolve("viewer.fsscript"), "export var view = 2;");

        try (URLClassLoader classLoader = classLoaderFor(classesRoot)) {
            Resource mainScript = new ClassPathResource("foggy/templates/main.fsscript", classLoader);
            assertTrue(mainScript.exists());
            ResourceFsscriptClosureDefinitionSpace space = definitionSpace(mainScript);
            ExpEvaluator evaluator = evaluator();

            Resource sibling = assertDoesNotThrow(
                    () -> space.getResource(evaluator, "./viewer.fsscript"),
                    "a sibling import inside a classpath*: bundle must be accepted");
            assertTrue(sibling.exists());
        }
    }

    @Test
    void classpathStarBundleShouldRejectRelativeImportEscapingRoot() throws Exception {
        Path classesRoot = tempDir.resolve("escape-classes");
        Path templatesRoot = classesRoot.resolve("foggy/templates/sub");
        Files.createDirectories(templatesRoot);
        Files.writeString(classesRoot.resolve("foggy/outside.fsscript"), "export var leaked = 99;");
        Files.writeString(templatesRoot.resolve("main.fsscript"), "export var value = 1;");

        try (URLClassLoader classLoader = classLoaderFor(classesRoot)) {
            Resource mainScript = new ClassPathResource("foggy/templates/sub/main.fsscript", classLoader);
            assertTrue(mainScript.exists());
            ResourceFsscriptClosureDefinitionSpace space = definitionSpace(mainScript);
            ExpEvaluator evaluator = evaluator();

            assertThrows(RuntimeException.class,
                    () -> space.getResource(evaluator, "../../outside.fsscript"),
                    "a relative import outside a classpath*: bundle must be rejected");
        }
    }

    @Test
    void classpathStarBundleInsideJarShouldAllowSiblingImport() throws Exception {
        Path jarPath = tempDir.resolve("sibling-bundle.jar");
        writeJarResources(jarPath, Map.of(
                "foggy/templates/main.fsscript", "export var value = 1;",
                "foggy/templates/viewer.fsscript", "export var view = 2;"));

        try (URLClassLoader classLoader = classLoaderFor(jarPath)) {
            Resource mainScript = new ClassPathResource("foggy/templates/main.fsscript", classLoader);
            assertTrue(mainScript.exists());
            assertFalse(mainScript.isFile());
            ResourceFsscriptClosureDefinitionSpace space = definitionSpace(mainScript);

            Resource sibling = assertDoesNotThrow(
                    () -> space.getResource(evaluator(), "./viewer.fsscript"),
                    "a sibling import inside a JAR-backed classpath*: bundle must be accepted");
            assertTrue(sibling.exists());
        }
    }

    @Test
    void classpathStarBundleInsideJarShouldRejectRelativeImportEscapingRoot() throws Exception {
        Path jarPath = tempDir.resolve("escape-bundle.jar");
        writeJarResources(jarPath, Map.of(
                "foggy/templates/sub/main.fsscript", "export var value = 1;",
                "foggy/outside.fsscript", "export var leaked = 99;"));

        try (URLClassLoader classLoader = classLoaderFor(jarPath)) {
            Resource mainScript = new ClassPathResource("foggy/templates/sub/main.fsscript", classLoader);
            assertTrue(mainScript.exists());
            assertFalse(mainScript.isFile());
            assertTrue(new ClassPathResource("foggy/outside.fsscript", classLoader).exists());
            ResourceFsscriptClosureDefinitionSpace space = definitionSpace(mainScript);

            assertThrows(RuntimeException.class,
                    () -> space.getResource(evaluator(), "../../outside.fsscript"),
                    "a relative import outside a JAR-backed classpath*: bundle must be rejected");
        }
    }

    @Test
    void classpathStarBundleShouldNotImportFromAnotherClasspathEntry() throws Exception {
        Path firstClasses = tempDir.resolve("first-classes");
        Path secondClasses = tempDir.resolve("second-classes");
        Files.createDirectories(firstClasses.resolve("foggy/templates"));
        Files.createDirectories(secondClasses.resolve("foggy/templates"));
        Files.writeString(firstClasses.resolve("foggy/templates/main.fsscript"), "export var value = 1;");
        Files.writeString(secondClasses.resolve("foggy/templates/viewer.fsscript"), "export var view = 2;");

        URL[] classpath = {firstClasses.toUri().toURL(), secondClasses.toUri().toURL()};
        try (URLClassLoader classLoader = new URLClassLoader(classpath, null)) {
            Resource mainScript = new ClassPathResource("foggy/templates/main.fsscript", classLoader);
            assertTrue(mainScript.exists());
            assertTrue(mainScript.createRelative("./viewer.fsscript").exists());
            ResourceFsscriptClosureDefinitionSpace space = definitionSpace(mainScript);

            assertThrows(RuntimeException.class,
                    () -> space.getResource(evaluator(), "./viewer.fsscript"),
                    "a classpath*: import must stay in the source resource's classpath entry");
        }
    }

    @Test
    void explicitJarBundleRootShouldConfineRelativeImports() throws Exception {
        Path jarPath = tempDir.resolve("explicit-root-bundle.jar");
        writeJarResources(jarPath, Map.of(
                "foggy/templates/main.fsscript", "export var value = 1;",
                "foggy/templates/viewer.fsscript", "export var view = 2;",
                "foggy/outside.fsscript", "export var leaked = 99;"));

        try (URLClassLoader classLoader = classLoaderFor(jarPath)) {
            Resource mainScript = new ClassPathResource("foggy/templates/main.fsscript", classLoader);
            String rootLocation = "jar:" + jarPath.toUri() + "!/foggy/templates";
            ResourceFsscriptClosureDefinitionSpace space = definitionSpace(mainScript, rootLocation);
            ExpEvaluator evaluator = evaluator();

            assertTrue(space.getResource(evaluator, "./viewer.fsscript").exists());
            assertThrows(RuntimeException.class,
                    () -> space.getResource(evaluator, "../outside.fsscript"));
        }
    }

    @Test
    void httpBundleRootShouldConfineRelativeResources() throws Exception {
        Resource mainScript = new UrlResource("https://host.example.com/models/main.fsscript");
        ResourceFsscriptClosureDefinitionSpace space = definitionSpace(
                mainScript, "https://host.example.com/models");
        ExpEvaluator evaluator = evaluator();

        Resource sibling = space.getResource(evaluator, "./viewer.fsscript");
        assertEquals("https://host.example.com/models/viewer.fsscript", sibling.getURI().toString());
        assertThrows(RuntimeException.class,
                () -> space.getResource(evaluator, "../outside.fsscript"));
    }

    private ResourceFsscriptClosureDefinitionSpace definitionSpace(Resource mainScript) {
        return definitionSpace(mainScript, "classpath*:foggy/templates");
    }

    private ResourceFsscriptClosureDefinitionSpace definitionSpace(Resource mainScript, String rootLocation) {
        ExternalFileBundle bundle = new ExternalFileBundle(mock(SystemBundlesContext.class));
        bundle.setRootPath(rootLocation);
        return new ResourceFsscriptClosureDefinitionSpace(new BundleResource(bundle, mainScript));
    }

    private ExpEvaluator evaluator() {
        ExpEvaluator evaluator = mock(ExpEvaluator.class);
        when(evaluator.getApplicationContext()).thenReturn(mock(ApplicationContext.class));
        return evaluator;
    }

    private URLClassLoader classLoaderFor(Path classesRoot) throws IOException {
        URL classesUrl = classesRoot.toUri().toURL();
        return new URLClassLoader(new URL[]{classesUrl}, null);
    }

    private static void writeJarResource(Path jarPath, String entryName, String content) throws IOException {
        writeJarResources(jarPath, Map.of(entryName, content));
    }

    private static void writeJarResources(Path jarPath, Map<String, String> entries) throws IOException {
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            for (Map.Entry<String, String> resource : entries.entrySet()) {
                JarEntry entry = new JarEntry(resource.getKey());
                jarOutputStream.putNextEntry(entry);
                jarOutputStream.write(resource.getValue().getBytes(StandardCharsets.UTF_8));
                jarOutputStream.closeEntry();
            }
        }
    }
}
