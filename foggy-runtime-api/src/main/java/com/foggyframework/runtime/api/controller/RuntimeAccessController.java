package com.foggyframework.runtime.api.controller;

import com.foggyframework.runtime.api.RuntimeApiRoutes;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.AccessCheckResponse;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RuntimeApiRoutes.API_V1)
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeAccessController {

    private final FoggyRuntimeApiProperties properties;
    private final RuntimeApiResponseFactory responses;

    public RuntimeAccessController(
            FoggyRuntimeApiProperties properties,
            RuntimeApiResponseFactory responses
    ) {
        this.properties = properties;
        this.responses = responses;
    }

    @GetMapping(RuntimeApiRoutes.V1.ACCESS_CHECK)
    public ResponseEntity<RuntimeEnvelope<AccessCheckResponse>> check() {
        AccessCheckResponse response = new AccessCheckResponse(
                true,
                properties.getAuthScope().propertyValue(),
                responses.runtimeApiVersion()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(responses.ok(response));
    }
}
