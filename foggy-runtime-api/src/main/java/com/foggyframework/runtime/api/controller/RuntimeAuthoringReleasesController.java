package com.foggyframework.runtime.api.controller;

import com.foggyframework.runtime.api.RuntimeApiRoutes;
import com.foggyframework.runtime.api.dto.AuthoringReleaseExportRequest;
import com.foggyframework.runtime.api.dto.AuthoringReleaseImportRequest;
import com.foggyframework.runtime.api.dto.AuthoringReleasePackage;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo;
import com.foggyframework.runtime.api.dto.AuthoringWorkspacePromotionRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceRollbackRequest;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeAuthoringReleasePackageService;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceException;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspacePublicationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

@RestController
@RequestMapping(RuntimeApiRoutes.API_V1)
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeAuthoringReleasesController {

    private final RuntimeApiResponseFactory responses;
    private final RuntimeAuthoringReleasePackageService releases;
    private final RuntimeAuthoringWorkspacePublicationService publications;

    public RuntimeAuthoringReleasesController(
            RuntimeApiResponseFactory responses,
            RuntimeAuthoringReleasePackageService releases,
            RuntimeAuthoringWorkspacePublicationService publications
    ) {
        this.responses = responses;
        this.releases = releases;
        this.publications = publications;
    }

    @PostMapping(RuntimeApiRoutes.V1.AUTHORING_RELEASE_EXPORT)
    public RuntimeEnvelope<AuthoringReleasePackage> exportPackage(
            @PathVariable String workspaceId,
            @RequestBody(required = false) AuthoringReleaseExportRequest request
    ) {
        return invoke(() -> releases.exportPackage(workspaceId, request));
    }

    @PostMapping(RuntimeApiRoutes.V1.AUTHORING_RELEASE_IMPORT)
    public RuntimeEnvelope<AuthoringWorkspaceInfo> importPackage(
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestBody(required = false) AuthoringReleaseImportRequest request
    ) {
        return invoke(() -> releases.importPackage(namespace, request));
    }

    @PostMapping(RuntimeApiRoutes.V1.AUTHORING_PROMOTE)
    public RuntimeEnvelope<AuthoringWorkspaceInfo> promote(
            @PathVariable String workspaceId,
            @RequestBody(required = false) AuthoringWorkspacePromotionRequest request
    ) {
        return invoke(() -> publications.promote(workspaceId, request));
    }

    @PostMapping(RuntimeApiRoutes.V1.AUTHORING_ROLLBACK)
    public RuntimeEnvelope<AuthoringWorkspaceInfo> rollback(
            @PathVariable String workspaceId,
            @RequestBody(required = false) AuthoringWorkspaceRollbackRequest request
    ) {
        return invoke(() -> publications.rollback(workspaceId, request));
    }

    @PostMapping(RuntimeApiRoutes.V1.AUTHORING_ROLLBACK_RECOVER)
    public RuntimeEnvelope<AuthoringWorkspaceInfo> recoverRollback(
            @PathVariable String workspaceId,
            @RequestBody(required = false) AuthoringWorkspaceRollbackRequest request
    ) {
        return invoke(() -> publications.recoverRollback(workspaceId, request));
    }

    private <T> RuntimeEnvelope<T> invoke(Supplier<T> action) {
        try {
            return responses.ok(action.get());
        } catch (RuntimeAuthoringWorkspaceException failure) {
            return responses.fail(
                    failure.code(), failure.phase(), failure.getMessage(),
                    null, null, failure.path(),
                    failure.safeToAutoRepair()
                            ? "Refresh exact workspace metadata and retry."
                            : "Inspect release package, target capability, and Runtime promotion mode.",
                    failure.safeToAutoRepair());
        }
    }
}
