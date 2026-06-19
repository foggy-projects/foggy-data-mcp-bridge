package com.foggyframework.runtime.api.controller;

import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.CapabilitiesResponse;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeCapabilitiesController {

    private static final String ENGINE = "java";

    private final FoggyRuntimeApiProperties properties;
    private final ObjectProvider<DataSource> dataSourceProvider;

    public RuntimeCapabilitiesController(FoggyRuntimeApiProperties properties, ObjectProvider<DataSource> dataSourceProvider) {
        this.properties = properties;
        this.dataSourceProvider = dataSourceProvider;
    }

    @GetMapping("/capabilities")
    public RuntimeEnvelope<CapabilitiesResponse> capabilities() {
        Map<String, String> capabilities = new LinkedHashMap<>();
        capabilities.put("runtime.capabilities", "supported");
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
        capabilities.put("query.validate", "supported");
        capabilities.put("query.execute", "supported");
        capabilities.put("tables.inspect", dataSourceProvider.getIfAvailable() != null ? "supported" : "unsupported");
        capabilities.put("compose.validate", "supported");
        capabilities.put("compose.preview", "supported");
        capabilities.put("compose.execute", "supported");
        capabilities.put("fsscript.execute", "supported");
        capabilities.put("fsscript.cteBridge", "supported");

        CapabilitiesResponse response = new CapabilitiesResponse(
                ENGINE,
                properties.getRuntimeApiVersion(),
                properties.getSchemaVersion(),
                properties.isEnabled(),
                properties.getSecurityMode(),
                capabilities,
                List.of("Runtime API is intended for development and testing only.")
        );

        return RuntimeEnvelope.ok(ENGINE, properties.getRuntimeApiVersion(), response);
    }
}
