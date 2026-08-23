package com.foggyframework.analytics.runtime.api.controller;

import com.foggyframework.analytics.runtime.api.AnalyticsRuntimeApiRoutes;
import com.foggyframework.analytics.runtime.api.config.FoggyAnalyticsRuntimeApiProperties;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsCapabilitiesResponse;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsRuntimeEnvelope;
import com.foggyframework.analytics.runtime.api.service.AnalyticsBundleOperations;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiResponseFactory;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeRenderOperations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(AnalyticsRuntimeApiRoutes.API_V1)
@ConditionalOnProperty(
        prefix = "foggy.analytics.runtime-api",
        name = "enabled",
        havingValue = "true")
public class AnalyticsCapabilitiesController {

    private final FoggyAnalyticsRuntimeApiProperties properties;
    private final AnalyticsBundleOperations bundleOperations;
    private final ObjectProvider<AnalyticsRuntimeRenderOperations> renderOperations;
    private final AnalyticsRuntimeApiResponseFactory responses;

    public AnalyticsCapabilitiesController(
            FoggyAnalyticsRuntimeApiProperties properties,
            AnalyticsBundleOperations bundleOperations,
            ObjectProvider<AnalyticsRuntimeRenderOperations> renderOperations,
            AnalyticsRuntimeApiResponseFactory responses) {
        this.properties = properties;
        this.bundleOperations = bundleOperations;
        this.renderOperations = renderOperations;
        this.responses = responses;
    }

    @GetMapping(AnalyticsRuntimeApiRoutes.V1.CAPABILITIES)
    public AnalyticsRuntimeEnvelope<AnalyticsCapabilitiesResponse> capabilities(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        boolean renderAvailable = renderOperations.getIfAvailable() != null;
        Map<String, String> operations = new LinkedHashMap<>();
        operations.put("analytics.capabilities", "supported");
        operations.put("analytics.bundles.list", "supported");
        operations.put("analytics.bundles.validate", "supported");
        operations.put("analytics.bundles.pull", "unsupported");
        operations.put("analytics.bundles.save", "unsupported");
        operations.put("analytics.reports.preview", status(renderAvailable));
        operations.put("analytics.dashboards.preview", status(renderAvailable));
        operations.put("analytics.dashboards.render", status(renderAvailable));

        AnalyticsCapabilitiesResponse result = new AnalyticsCapabilitiesResponse(
                "analytics",
                properties.getRuntimeApiVersion(),
                properties.getSchemaVersion(),
                properties.isEnabled(),
                properties.getSecurityMode(),
                operations,
                new AnalyticsCapabilitiesResponse.Limits(
                        properties.getMaxRows(),
                        bundleOperations.configuredBundleCount()),
                warnings(renderAvailable));
        return responses.ok(result, requestId, traceId);
    }

    private List<String> warnings(boolean renderAvailable) {
        List<String> warnings = new ArrayList<>();
        if (!renderAvailable) {
            warnings.add(
                    "Preview/render requires a host FoggySemanticRequestContextResolver.");
        }
        if (bundleOperations.configuredBundleCount() == 0) {
            warnings.add("No trusted Analytics Bundle registrations are configured.");
        }
        return List.copyOf(warnings);
    }

    private static String status(boolean available) {
        return available ? "supported" : "unavailable";
    }
}
