package com.foggyframework.runtime.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.BundleMutationResponse;
import com.foggyframework.runtime.api.dto.BundleRequest;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeBundleModelConflictDetector;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

    private RuntimeBundlesController controller(
            SystemBundlesContext context,
            RuntimeBundleRegistryService registry
    ) {
        RuntimeBundleModelConflictDetector detector =
                mock(RuntimeBundleModelConflictDetector.class);
        when(detector.findConflicts(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(List.of());
        FoggyRuntimeApiProperties properties = properties(
                tempDir.resolve("unused.json"));
        return new RuntimeBundlesController(
                new RuntimeApiResponseFactory(properties),
                context,
                registry,
                detector
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
