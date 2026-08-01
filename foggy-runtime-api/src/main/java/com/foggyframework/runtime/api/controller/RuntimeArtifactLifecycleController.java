package com.foggyframework.runtime.api.controller;

import com.foggyframework.runtime.api.RuntimeApiRoutes;
import com.foggyframework.runtime.api.dto.ArtifactLifecycleInventory;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeArtifactLifecycleInventoryService;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RuntimeApiRoutes.API_V1)
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeArtifactLifecycleController {

    private final RuntimeApiResponseFactory responses;
    private final RuntimeArtifactLifecycleInventoryService inventoryService;

    public RuntimeArtifactLifecycleController(
            RuntimeApiResponseFactory responses,
            RuntimeArtifactLifecycleInventoryService inventoryService
    ) {
        this.responses = responses;
        this.inventoryService = inventoryService;
    }

    @GetMapping(RuntimeApiRoutes.V1.AUTHORING_ARTIFACT_LIFECYCLE)
    public RuntimeEnvelope<ArtifactLifecycleInventory> inventory() {
        try {
            return responses.ok(inventoryService.inventory());
        } catch (RuntimeAuthoringWorkspaceException failure) {
            return responses.fail(
                    failure.code(), failure.phase(), failure.getMessage(),
                    null, null, null,
                    "Inspect the redacted lifecycle blocked reasons and Runtime configuration.",
                    false);
        } catch (RuntimeException failure) {
            return responses.fail(
                    "ARTIFACT_LIFECYCLE_INVENTORY_FAILED",
                    "runtime.artifacts.lifecycle.inventory",
                    "Artifact lifecycle inventory could not be collected.",
                    null, null, null,
                    "Inspect Runtime filesystem health and retry.", false);
        }
    }
}
