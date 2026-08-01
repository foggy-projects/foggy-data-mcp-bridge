package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void restoreRejectsOverlapBeforeAddingRuntimeBundle() {
        Path storeRoot = tempDir.resolve("authoring-store");
        FoggyRuntimeApiProperties properties = properties(
                tempDir.resolve("runtime-bundles-overlap.json"));
        properties.getAuthoringWorkspaces().setPath(storeRoot.toString());
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        RuntimeAuthoringStorePathPolicy policy =
                new RuntimeAuthoringStorePathPolicy(properties, context);
        RuntimeBundleRegistryService registry =
                new RuntimeBundleRegistryService(
                        properties, context, new ObjectMapper(), policy);
        registry.save(registry.newRecord(
                "plugin-x", "business", storeRoot.resolve("models").toString(),
                false, true));

        assertThatThrownBy(registry::restoreOnReady)
                .isInstanceOf(
                        RuntimeAuthoringStorePathPolicy.PathConflictException.class);
        verify(context, never()).addExternalBundle(
                "plugin-x", "business",
                storeRoot.resolve("models").toString(), false);
    }

    @Test
    void configuredOverlapFailsReadyCheckWhenRuntimeRegistryIsDisabled() {
        Path storeRoot = tempDir.resolve("configured-authoring-store");
        FoggyRuntimeApiProperties properties = properties(
                tempDir.resolve("disabled-registry.json"));
        properties.getAuthoringWorkspaces().setPath(storeRoot.toString());
        properties.getBundleRegistry().setEnabled(false);
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.listExternalBundles()).thenReturn(List.of(
                new ExternalBundleDefinition(
                        "configured", "business",
                        storeRoot.resolve("models").toString(), false)));
        RuntimeAuthoringStorePathPolicy policy =
                new RuntimeAuthoringStorePathPolicy(properties, context);
        RuntimeBundleRegistryService registry =
                new RuntimeBundleRegistryService(
                        properties, context, new ObjectMapper(), policy);

        assertThatThrownBy(registry::restoreOnReady)
                .isInstanceOf(
                        RuntimeAuthoringStorePathPolicy.PathConflictException.class);
        verify(context, never()).addExternalBundle(
                "configured", "business",
                storeRoot.resolve("models").toString(), false);
    }

    @Test
    void startupRestoresImmutablePublishedBundleWithWatchDisabled() {
        Path publishedRoot = tempDir.resolve("published");
        Path artifact = publishedRoot.resolve("artifacts").resolve("attempt-1");
        FoggyRuntimeApiProperties properties = properties(
                tempDir.resolve("runtime-bundles-published.json"));
        properties.getAuthoringWorkspaces().setPath(
                tempDir.resolve("workspaces").toString());
        properties.getAuthoringWorkspaces().setPublishedBundlesPath(
                publishedRoot.toString());
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.listExternalBundles()).thenReturn(List.of());
        when(context.containBundle("published-sales")).thenReturn(false);
        RuntimeAuthoringStorePathPolicy policy =
                new RuntimeAuthoringStorePathPolicy(properties, context);
        RuntimeBundleRegistryService registry =
                new RuntimeBundleRegistryService(
                        properties, context, new ObjectMapper(), policy);
        registry.save(registry.newRecord(
                "published-sales", "sales", artifact.toString(), false, true)
                .withPublication(artifact.toString(),
                        "sha256:" + "a".repeat(64)));

        registry.restoreOnReady();

        verify(context).addExternalBundle(
                "published-sales", "sales", artifact.toString(), false);
        assertThat(registry.find("published-sales").orElseThrow()
                .immutablePublication()).isTrue();
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
