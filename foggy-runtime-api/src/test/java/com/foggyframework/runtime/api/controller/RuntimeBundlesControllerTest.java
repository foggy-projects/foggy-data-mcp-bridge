package com.foggyframework.runtime.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.BundleMutationResponse;
import com.foggyframework.runtime.api.dto.BundleRequest;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeBundleAdmissionService;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeBundlesControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void failedSourceAddMustRollbackDurableRegistryIntent() {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.containBundle("plugin-x")).thenReturn(false);
        when(context.addExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x", false))
                .thenReturn(false);
        RuntimeBundleRegistryService registry = registry(context);
        RuntimeBundlesController controller = controller(context, registry);

        RuntimeEnvelope<BundleMutationResponse> response =
                controller.addBundle(new BundleRequest(
                        "plugin-x",
                        "business",
                        "/bundles/plugin-x",
                        false,
                        true,
                        false,
                        false,
                        false
                ), null);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code()).isEqualTo("BUNDLE_ADD_FAILED");
        assertThat(registry.find("plugin-x")).isEmpty();
    }

    @Test
    void replaceMustUseAtomicBundleReplacementBoundary() {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.containBundle("plugin-x")).thenReturn(true);
        when(context.replaceExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x-v2", false))
                .thenReturn(true);
        RuntimeBundleRegistryService registry = registry(context);
        registry.save(registry.newRecord(
                "plugin-x", "business", "/bundles/plugin-x-v1",
                false, true));
        RuntimeBundlesController controller = controller(context, registry);

        RuntimeEnvelope<BundleMutationResponse> response =
                controller.updateBundle("plugin-x", new BundleRequest(
                        null,
                        "business",
                        "/bundles/plugin-x-v2",
                        false,
                        true,
                        true,
                        false,
                        false
                ), null);

        assertThat(response.success()).isTrue();
        assertThat(registry.find("plugin-x").orElseThrow().path())
                .isEqualTo("/bundles/plugin-x-v2");
        verify(context).replaceExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x-v2", false);
        verify(context, never()).removeBundle("plugin-x");
    }

    @Test
    void disablingManagedBundleMustRemoveOnlyItsRuntimeSource() {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.containBundle("plugin-x")).thenReturn(true);
        when(context.removeBundle("plugin-x")).thenReturn(true);
        RuntimeBundleRegistryService registry = registry(context);
        registry.save(registry.newRecord(
                "plugin-x", "business", "/bundles/plugin-x",
                false, true));
        RuntimeBundlesController controller = controller(context, registry);

        RuntimeEnvelope<BundleMutationResponse> response =
                controller.updateBundle("plugin-x", new BundleRequest(
                        null,
                        "business",
                        "/bundles/plugin-x",
                        false,
                        false,
                        true,
                        false,
                        false
                ), null);

        assertThat(response.success()).isTrue();
        assertThat(registry.find("plugin-x").orElseThrow().enabled())
                .isFalse();
        verify(context).removeBundle("plugin-x");
        verify(context, never()).replaceExternalBundle(
                "plugin-x", "business", "/bundles/plugin-x", false);
    }

    private RuntimeBundlesController controller(
            SystemBundlesContext context,
            RuntimeBundleRegistryService registry
    ) {
        FoggyRuntimeApiProperties properties = properties();
        return new RuntimeBundlesController(
                new RuntimeApiResponseFactory(properties),
                context,
                registry,
                mock(RuntimeBundleAdmissionService.class)
        );
    }

    private RuntimeBundleRegistryService registry(
            SystemBundlesContext context
    ) {
        return new RuntimeBundleRegistryService(
                properties(), context, new ObjectMapper());
    }

    private FoggyRuntimeApiProperties properties() {
        FoggyRuntimeApiProperties properties =
                new FoggyRuntimeApiProperties();
        properties.getBundleRegistry().setPath(
                tempDir.resolve("runtime-bundles.json").toString());
        return properties;
    }
}
