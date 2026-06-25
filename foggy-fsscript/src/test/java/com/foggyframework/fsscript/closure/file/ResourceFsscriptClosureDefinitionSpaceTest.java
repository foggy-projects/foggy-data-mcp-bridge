package com.foggyframework.fsscript.closure.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static void writeJarResource(Path jarPath, String entryName, String content) throws IOException {
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            JarEntry entry = new JarEntry(entryName);
            jarOutputStream.putNextEntry(entry);
            jarOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
            jarOutputStream.closeEntry();
        }
    }
}
