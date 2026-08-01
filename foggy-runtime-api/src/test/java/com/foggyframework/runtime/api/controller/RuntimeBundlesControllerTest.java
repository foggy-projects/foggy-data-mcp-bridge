package com.foggyframework.runtime.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.BundleMutationResponse;
import com.foggyframework.runtime.api.dto.BundleRequest;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeAuthoringPublicationLock;
import com.foggyframework.runtime.api.service.RuntimeAuthoringStorePathPolicy;
import com.foggyframework.runtime.api.service.RuntimeBundleModelConflictDetector;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeBundlesControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void failedSourceAddMustLeaveRegistryEmpty() {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.containBundle("plugin-x")).thenReturn(false);
        when(context.addExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x", false))
                .thenReturn(false);
        RuntimeBundleRegistryService registry = registry(
                context, tempDir.resolve("runtime-bundles.json"));
        RuntimeBundlesController controller = controller(context, registry);

        RuntimeEnvelope<BundleMutationResponse> response =
                controller.addBundle(request(
                        "plugin-x", "/bundles/plugin-x", true, false), null);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code()).isEqualTo("BUNDLE_ADD_FAILED");
        assertThat(registry.find("plugin-x")).isEmpty();
    }

    @Test
    void replaceMustUseAtomicBundleReplacementBoundary() {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.containBundle("plugin-x")).thenReturn(true);
        when(context.getBundleDefinitionByName("plugin-x"))
                .thenReturn(externalDefinition(
                        "plugin-x", "/bundles/plugin-x-v1"));
        when(context.replaceExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x-v2", false))
                .thenReturn(true);
        RuntimeBundleRegistryService registry = registry(
                context, tempDir.resolve("runtime-bundles.json"));
        registry.save(registry.newRecord(
                "plugin-x", "business", "/bundles/plugin-x-v1",
                false, true));
        RuntimeBundlesController controller = controller(context, registry);

        RuntimeEnvelope<BundleMutationResponse> response =
                controller.updateBundle("plugin-x", request(
                        null, "/bundles/plugin-x-v2", true, true), null);

        assertThat(response.success()).isTrue();
        assertThat(registry.find("plugin-x").orElseThrow().path())
                .isEqualTo("/bundles/plugin-x-v2");
        verify(context).replaceExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x-v2", false);
        verify(context, never()).removeBundle("plugin-x");
    }

    @Test
    void sourceAddMustBeRolledBackWhenRegistryPersistenceFails()
            throws Exception {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.containBundle("plugin-x"))
                .thenReturn(false, false, true);
        when(context.addExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x", false))
                .thenReturn(true);
        when(context.removeBundle("plugin-x")).thenReturn(true);
        Path parentFile = Files.writeString(
                tempDir.resolve("not-a-directory"), "x");
        RuntimeBundleRegistryService registry = registry(
                context, parentFile.resolve("runtime-bundles.json"));
        RuntimeBundlesController controller = controller(context, registry);

        RuntimeEnvelope<BundleMutationResponse> response =
                controller.addBundle(request(
                        "plugin-x", "/bundles/plugin-x", true, false), null);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code())
                .isEqualTo("BUNDLE_REGISTRY_PERSIST_FAILED");
        assertThat(registry.find("plugin-x")).isEmpty();
        verify(context).removeBundle("plugin-x");
    }

    @Test
    void sourceReplaceMustRestoreOldVersionWhenRegistryPersistenceFails()
            throws Exception {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        ExternalBundleDefinition oldDefinition = externalDefinition(
                "plugin-x", "/bundles/plugin-x-v1");
        when(context.containBundle("plugin-x")).thenReturn(true);
        when(context.getBundleDefinitionByName("plugin-x"))
                .thenReturn(oldDefinition);
        when(context.replaceExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x-v2", false))
                .thenReturn(true);
        when(context.replaceExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x-v1", false))
                .thenReturn(true);

        FoggyRuntimeApiProperties properties = properties(
                tempDir.resolve("runtime-bundles.json"));
        RuntimeBundleRegistryService registry = registry(context, properties);
        registry.save(registry.newRecord(
                "plugin-x", "business", "/bundles/plugin-x-v1",
                false, true));
        Path parentFile = Files.writeString(
                tempDir.resolve("not-a-directory"), "x");
        properties.getBundleRegistry().setPath(
                parentFile.resolve("runtime-bundles.json").toString());
        RuntimeBundlesController controller = controller(context, registry);

        RuntimeEnvelope<BundleMutationResponse> response =
                controller.updateBundle("plugin-x", request(
                        null, "/bundles/plugin-x-v2", true, true), null);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code())
                .isEqualTo("BUNDLE_REGISTRY_PERSIST_FAILED");
        assertThat(registry.find("plugin-x").orElseThrow().path())
                .isEqualTo("/bundles/plugin-x-v1");
        verify(context).replaceExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x-v1", false);
    }

    @Test
    void sourceRemoveMustRestoreOldVersionWhenRegistryPersistenceFails()
            throws Exception {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.containBundle("plugin-x")).thenReturn(true, false);
        when(context.getBundleDefinitionByName("plugin-x"))
                .thenReturn(externalDefinition(
                        "plugin-x", "/bundles/plugin-x-v1"));
        when(context.removeBundle("plugin-x")).thenReturn(true);
        when(context.addExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x-v1", false))
                .thenReturn(true);

        FoggyRuntimeApiProperties properties = properties(
                tempDir.resolve("runtime-bundles.json"));
        RuntimeBundleRegistryService registry = registry(context, properties);
        registry.save(registry.newRecord(
                "plugin-x", "business", "/bundles/plugin-x-v1",
                false, true));
        Path parentFile = Files.writeString(
                tempDir.resolve("not-a-directory"), "x");
        properties.getBundleRegistry().setPath(
                parentFile.resolve("runtime-bundles.json").toString());
        RuntimeBundlesController controller = controller(context, registry);

        RuntimeEnvelope<BundleMutationResponse> response =
                controller.removeBundle("plugin-x");

        assertThat(response.success()).isFalse();
        assertThat(response.error().code())
                .isEqualTo("BUNDLE_REGISTRY_PERSIST_FAILED");
        assertThat(registry.find("plugin-x")).isPresent();
        verify(context).addExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x-v1", false);
    }

    @Test
    void sourceRollbackFailureMustReturnStableError() throws Exception {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.containBundle("plugin-x"))
                .thenReturn(false, false, true);
        when(context.addExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x", false))
                .thenReturn(true);
        when(context.removeBundle("plugin-x")).thenReturn(false);
        Path parentFile = Files.writeString(
                tempDir.resolve("not-a-directory"), "x");
        RuntimeBundleRegistryService registry = registry(
                context, parentFile.resolve("runtime-bundles.json"));
        RuntimeBundlesController controller = controller(context, registry);

        RuntimeEnvelope<BundleMutationResponse> response =
                controller.addBundle(request(
                        "plugin-x", "/bundles/plugin-x", true, false), null);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code())
                .isEqualTo("BUNDLE_ROLLBACK_FAILED");
        assertThat(registry.find("plugin-x")).isEmpty();
    }

    @Test
    void overlappingBundlePathsFailBeforeRuntimeOrRegistryMutation() {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        Path storeRoot = tempDir.resolve("authoring-store");
        FoggyRuntimeApiProperties properties = properties(
                tempDir.resolve("runtime-bundles-overlap.json"));
        properties.getAuthoringWorkspaces().setPath(storeRoot.toString());
        RuntimeBundleRegistryService registry = registry(context, properties);
        RuntimeBundlesController controller = controller(
                context, registry, properties);
        List<Path> overlapping = List.of(
                storeRoot,
                storeRoot.resolve("bundle-source"),
                tempDir);

        for (int index = 0; index < overlapping.size(); index++) {
            String name = "overlap-" + index;
            RuntimeEnvelope<BundleMutationResponse> response =
                    controller.addBundle(request(
                            name, overlapping.get(index).toString(),
                            true, false), null);
            assertThat(response.success()).isFalse();
            assertThat(response.error().code()).isEqualTo("BUNDLE_PATH_CONFLICT");
            assertThat(registry.find(name)).isEmpty();
        }
        verify(context, never()).addExternalBundle(
                anyString(), anyString(), anyString(), anyBoolean());
        verify(context, never()).replaceExternalBundle(
                anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void overlappingUpdateCannotEnableOrRewriteExistingRegistryRecord() {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        Path storeRoot = tempDir.resolve("authoring-update-store");
        FoggyRuntimeApiProperties properties = properties(
                tempDir.resolve("runtime-bundles-update-overlap.json"));
        properties.getAuthoringWorkspaces().setPath(storeRoot.toString());
        RuntimeBundleRegistryService registry = registry(context, properties);
        var existing = registry.save(registry.newRecord(
                "overlap", "business", storeRoot.resolve("models").toString(),
                false, false));
        RuntimeBundlesController controller = controller(
                context, registry, properties);

        RuntimeEnvelope<BundleMutationResponse> response =
                controller.updateBundle("overlap", request(
                        null, storeRoot.resolve("models").toString(),
                        true, true), null);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code()).isEqualTo("BUNDLE_PATH_CONFLICT");
        assertThat(registry.find("overlap")).contains(existing);
        verify(context, never()).addExternalBundle(
                anyString(), anyString(), anyString(), anyBoolean());
        verify(context, never()).replaceExternalBundle(
                anyString(), anyString(), anyString(), anyBoolean());
        verify(context, never()).removeBundle(anyString());
    }

    @Test
    void replacingAndRemovingPublishedBundleNeverDeletesArtifact()
            throws Exception {
        Path artifact = Files.createDirectories(
                tempDir.resolve("published-artifact"));
        Path resource = Files.writeString(
                artifact.resolve("Order.tm"), "published");
        Path replacement = Files.createDirectories(
                tempDir.resolve("replacement"));
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.containBundle("plugin-x")).thenReturn(true);
        when(context.getBundleDefinitionByName("plugin-x"))
                .thenReturn(externalDefinition("plugin-x", artifact.toString()),
                        externalDefinition("plugin-x", replacement.toString()));
        when(context.replaceExternalBundle(
                "plugin-x", "business", replacement.toString(), false))
                .thenReturn(true);
        when(context.removeBundle("plugin-x")).thenReturn(true);
        FoggyRuntimeApiProperties properties = properties(
                tempDir.resolve("runtime-bundles-retention.json"));
        RuntimeBundleRegistryService registry = registry(context, properties);
        registry.save(registry.newRecord(
                "plugin-x", "business", artifact.toString(), false, true)
                .withPublication(artifact.toString(),
                        "sha256:" + "a".repeat(64)));
        RuntimeBundlesController controller = controller(
                context, registry, properties);

        var replaced = controller.updateBundle("plugin-x", request(
                null, replacement.toString(), true, true), null);
        var removed = controller.removeBundle("plugin-x");

        assertThat(replaced.success()).isTrue();
        assertThat(removed.success()).isTrue();
        assertThat(resource).hasContent("published");
        assertThat(artifact).isDirectory();
    }

    @Test
    void bundleReplacementUsesSharedPublicationLock() throws Exception {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.containBundle("plugin-x")).thenReturn(true);
        when(context.getBundleDefinitionByName("plugin-x"))
                .thenReturn(externalDefinition(
                        "plugin-x", "/bundles/plugin-x-v1"));
        when(context.replaceExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x-v2", false))
                .thenReturn(true);
        FoggyRuntimeApiProperties properties = properties(
                tempDir.resolve("runtime-bundles-locked.json"));
        RuntimeBundleRegistryService registry = registry(context, properties);
        registry.save(registry.newRecord(
                "plugin-x", "business", "/bundles/plugin-x-v1",
                false, true));
        RuntimeBundleModelConflictDetector detector =
                mock(RuntimeBundleModelConflictDetector.class);
        when(detector.findConflicts(
                anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(List.of());
        RuntimeAuthoringPublicationLock publicationLock =
                new RuntimeAuthoringPublicationLock();
        RuntimeBundlesController controller = new RuntimeBundlesController(
                new RuntimeApiResponseFactory(properties), context, registry,
                detector, new RuntimeAuthoringStorePathPolicy(properties, context),
                publicationLock);
        CountDownLatch started = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try (RuntimeAuthoringPublicationLock.Guard ignored =
                     publicationLock.acquire()) {
            var future = executor.submit(() -> {
                started.countDown();
                return controller.updateBundle("plugin-x", request(
                        null, "/bundles/plugin-x-v2", true, true), null);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(future.isDone()).isFalse();
        } finally {
            executor.shutdown();
        }
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(registry.find("plugin-x").orElseThrow().path())
                .isEqualTo("/bundles/plugin-x-v2");
    }

    private RuntimeBundlesController controller(
            SystemBundlesContext context,
            RuntimeBundleRegistryService registry
    ) {
        return controller(context, registry,
                properties(tempDir.resolve("unused.json")));
    }

    private RuntimeBundlesController controller(
            SystemBundlesContext context,
            RuntimeBundleRegistryService registry,
            FoggyRuntimeApiProperties properties
    ) {
        RuntimeBundleModelConflictDetector detector =
                mock(RuntimeBundleModelConflictDetector.class);
        when(detector.findConflicts(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(List.of());
        return new RuntimeBundlesController(
                new RuntimeApiResponseFactory(properties),
                context,
                registry,
                detector,
                new RuntimeAuthoringStorePathPolicy(properties, context)
        );
    }

    private RuntimeBundleRegistryService registry(
            SystemBundlesContext context,
            Path path
    ) {
        return registry(context, properties(path));
    }

    private RuntimeBundleRegistryService registry(
            SystemBundlesContext context,
            FoggyRuntimeApiProperties properties
    ) {
        return new RuntimeBundleRegistryService(
                properties, context, new ObjectMapper());
    }

    private static BundleRequest request(
            String name,
            String path,
            boolean enabled,
            boolean replace
    ) {
        return new BundleRequest(
                name,
                "business",
                path,
                false,
                enabled,
                replace,
                false,
                false
        );
    }

    private static ExternalBundleDefinition externalDefinition(
            String name,
            String path
    ) {
        return new ExternalBundleDefinition(
                name, "business", path, false);
    }

    private static FoggyRuntimeApiProperties properties(Path path) {
        FoggyRuntimeApiProperties properties =
                new FoggyRuntimeApiProperties();
        properties.getBundleRegistry().setPath(path.toString());
        return properties;
    }
}
