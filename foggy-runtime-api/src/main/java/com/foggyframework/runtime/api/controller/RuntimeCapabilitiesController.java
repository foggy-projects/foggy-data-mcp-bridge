package com.foggyframework.runtime.api.controller;

import com.foggyframework.runtime.api.RuntimeApiRoutes;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.CapabilitiesResponse;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceLimits;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RuntimeApiRoutes.API_V1)
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeCapabilitiesController {

    private final FoggyRuntimeApiProperties properties;
    private final RuntimeApiResponseFactory responses;
    private final RuntimeDatasourceRegistryService datasourceRegistryService;

    public RuntimeCapabilitiesController(
            FoggyRuntimeApiProperties properties,
            RuntimeApiResponseFactory responses,
            RuntimeDatasourceRegistryService datasourceRegistryService
    ) {
        this.properties = properties;
        this.responses = responses;
        this.datasourceRegistryService = datasourceRegistryService;
    }

    @GetMapping(RuntimeApiRoutes.V1.CAPABILITIES)
    public RuntimeEnvelope<CapabilitiesResponse> capabilities() {
        Map<String, String> capabilities = new LinkedHashMap<>();
        capabilities.put("runtime.capabilities", "supported");
        capabilities.put("runtime.accessCheck", "supported");
        capabilities.put("models.list", "supported");
        capabilities.put("models.describe", "supported");
        capabilities.put("models.validate", "supported");
        capabilities.put("models.refresh", "supported");
        capabilities.put("bundles.list", "supported");
        capabilities.put("bundles.add", "supported");
        capabilities.put("bundles.update", "supported");
        capabilities.put("bundles.remove", "supported");
        capabilities.put("resources.export", "supported");
        capabilities.put("resources.save", "supported");
        capabilities.put("datasources.list", "supported");
        capabilities.put("datasources.add", "supported");
        capabilities.put("datasources.update", "supported");
        capabilities.put("datasources.remove", "supported");
        capabilities.put("datasources.test", "supported");
        capabilities.put("datasources.bind", "supported");
        capabilities.put("datasources.diagnostics", "supported");
        capabilities.put("query.validate", "supported");
        capabilities.put("query.execute", "supported");
        capabilities.put("sql.query", "supported");
        capabilities.put("tables.list", "supported");
        capabilities.put("tables.inspect", "supported");
        capabilities.put("compose.validate", "supported");
        capabilities.put("compose.preview", "supported");
        capabilities.put("compose.execute", "supported");
        capabilities.put("fsscript.execute", "supported");
        capabilities.put("fsscript.cteBridge", "supported");
        capabilities.put("authoring.workspaces", "supported");
        capabilities.put("authoring.resources", "supported");
        capabilities.put("authoring.diff", "supported");
        capabilities.put("authoring.validate", "supported");
        capabilities.put("authoring.query", "supported");

        CapabilitiesResponse response = new CapabilitiesResponse(
                responses.engine(),
                responses.runtimeApiVersion(),
                properties.getSchemaVersion(),
                properties.isEnabled(),
                properties.getEffectiveSecurityMode(),
                capabilities,
                warnings(),
                authoringLimits()
        );

        return responses.ok(response);
    }

    private List<String> warnings() {
        if ("auth-code".equals(properties.getEffectiveSecurityMode()) && !properties.isAuthCodeConfigured()) {
            return List.of("Runtime API auth-code mode is enabled, but no auth code is configured.");
        }
        if ("auth-code".equals(properties.getEffectiveSecurityMode())) {
            if (properties.isManagementAllAuthScope()) {
                return List.of("Runtime API management-all scope requires an auth code for every /api/v1 operation.");
            }
            return List.of("Runtime API management operations require an auth code.");
        }
        return List.of("Runtime API is intended for development and testing only.");
    }

    private AuthoringWorkspaceLimits authoringLimits() {
        FoggyRuntimeApiProperties.AuthoringWorkspaces configured =
                properties.getAuthoringWorkspaces() == null
                        ? new FoggyRuntimeApiProperties.AuthoringWorkspaces()
                        : properties.getAuthoringWorkspaces();
        return new AuthoringWorkspaceLimits(
                configured.getMaxActiveWorkspaces(),
                configured.getMaxResourcesPerRevision(),
                configured.getMaxResourceBytes(),
                configured.getMaxRevisionBytes(),
                configured.getMaxBatchOperations(),
                configured.getMaxPathBytes());
    }
}
