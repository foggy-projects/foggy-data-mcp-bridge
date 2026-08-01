package com.foggyframework.runtime.api.controller;

import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.ArtifactLifecycleInventory;
import com.foggyframework.runtime.api.dto.ArtifactLifecycleInventory.Summary;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeArtifactLifecycleInventoryService;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeArtifactLifecycleControllerTest {

    private final RuntimeArtifactLifecycleInventoryService service =
            mock(RuntimeArtifactLifecycleInventoryService.class);
    private final RuntimeArtifactLifecycleController controller =
            new RuntimeArtifactLifecycleController(
                    new RuntimeApiResponseFactory(new FoggyRuntimeApiProperties()),
                    service);

    @Test
    void returnsRuntimeEnvelopeForRedactedInventory() {
        ArtifactLifecycleInventory inventory = new ArtifactLifecycleInventory(
                "2026-08-01T00:00:00Z", "HEALTHY", List.of(),
                new Summary(0, 0, 0, 0, 0, 0), List.of(), List.of());
        when(service.inventory()).thenReturn(inventory);

        var response = controller.inventory();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(inventory);
    }

    @Test
    void mapsUnexpectedFailureWithoutLeakingFilesystemOrSecret() {
        RuntimeAuthoringWorkspaceException failure =
                new RuntimeAuthoringWorkspaceException(
                        "ARTIFACT_LIFECYCLE_INVENTORY_FAILED",
                        "runtime.artifacts.lifecycle.inventory",
                        "Artifact lifecycle inventory could not be collected.",
                        "/private/store", false);
        failure.addSuppressed(new IllegalStateException(
                "auth-code=must-not-leak /private/store"));
        when(service.inventory()).thenThrow(failure);

        var response = controller.inventory();

        assertThat(response.success()).isFalse();
        assertThat(response.error().code())
                .isEqualTo("ARTIFACT_LIFECYCLE_INVENTORY_FAILED");
        assertThat(response.error().phase())
                .isEqualTo("runtime.artifacts.lifecycle.inventory");
        assertThat(response.error().path()).isNull();
        assertThat(response.toString()).doesNotContain(
                "/private/store", "must-not-leak", "auth-code");
    }
}
