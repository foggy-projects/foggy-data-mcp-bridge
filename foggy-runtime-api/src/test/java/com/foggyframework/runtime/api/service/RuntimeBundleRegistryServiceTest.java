package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RuntimeBundleRegistryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void failedSaveMustRestoreInMemoryRegistry() throws Exception {
        Path parentFile = Files.writeString(
                tempDir.resolve("not-a-directory"), "x");
        FoggyRuntimeApiProperties properties = properties(
                parentFile.resolve("runtime-bundles.json"));
        RuntimeBundleRegistryService registry = registry(properties);

        assertThatThrownBy(() -> registry.save(registry.newRecord(
                "plugin-x", "business", "/bundles/plugin-x",
                false, true)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(registry.find("plugin-x")).isEmpty();
    }

    @Test
    void failedRemoveMustRestoreInMemoryRegistry() throws Exception {
        FoggyRuntimeApiProperties properties = properties(
                tempDir.resolve("runtime-bundles.json"));
        RuntimeBundleRegistryService registry = registry(properties);
        registry.save(registry.newRecord(
                "plugin-x", "business", "/bundles/plugin-x",
                false, true));
        Path parentFile = Files.writeString(
                tempDir.resolve("not-a-directory"), "x");
        properties.getBundleRegistry().setPath(
                parentFile.resolve("runtime-bundles.json").toString());

        assertThatThrownBy(() -> registry.remove("plugin-x"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(registry.find("plugin-x")).isPresent();
    }

    private static RuntimeBundleRegistryService registry(
            FoggyRuntimeApiProperties properties
    ) {
        return new RuntimeBundleRegistryService(
                properties,
                mock(SystemBundlesContext.class),
                new ObjectMapper()
        );
    }

    private static FoggyRuntimeApiProperties properties(Path path) {
        FoggyRuntimeApiProperties properties =
                new FoggyRuntimeApiProperties();
        properties.getBundleRegistry().setPath(path.toString());
        return properties;
    }
}
