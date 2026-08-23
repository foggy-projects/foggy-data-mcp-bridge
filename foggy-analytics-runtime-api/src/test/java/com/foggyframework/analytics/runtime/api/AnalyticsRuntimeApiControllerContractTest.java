package com.foggyframework.analytics.runtime.api;

import com.foggyframework.analytics.definition.api.AnalyticsArtifactKind;
import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.api.AnalyticsColumnSchema;
import com.foggyframework.analytics.definition.api.AnalyticsRenderModel;
import com.foggyframework.analytics.definition.api.AnalyticsRenderState;
import com.foggyframework.analytics.definition.api.AnalyticsVisualIntent;
import com.foggyframework.analytics.definition.api.AnalyticsVisualKind;
import com.foggyframework.analytics.definition.api.AnalyticsWidgetData;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException;
import com.foggyframework.analytics.runtime.api.config.FoggyAnalyticsRuntimeApiProperties;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsBundlesController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsCapabilitiesController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsRenderController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsRuntimeApiExceptionHandler;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsBundleSummary;
import com.foggyframework.analytics.runtime.api.service.AnalyticsBundleOperations;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiResponseFactory;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeRenderOperations;
import com.foggyframework.analytics.runtime.core.render.AnalyticsReportPreviewRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AnalyticsRuntimeApiControllerContractTest {

    private static final String REVISION = "sha256:" + "a".repeat(64);

    private FoggyAnalyticsRuntimeApiProperties properties;
    private AnalyticsBundleOperations bundleOperations;
    private AnalyticsRuntimeRenderOperations renderOperations;
    private AnalyticsRuntimeApiResponseFactory responses;

    @BeforeEach
    void setUp() {
        properties = new FoggyAnalyticsRuntimeApiProperties();
        properties.setEnabled(true);
        bundleOperations = mock(AnalyticsBundleOperations.class);
        renderOperations = mock(AnalyticsRuntimeRenderOperations.class);
        responses = new AnalyticsRuntimeApiResponseFactory(properties);
    }

    @Test
    void exposesIndependentCapabilitiesAndHonestUnsupportedOperations() throws Exception {
        when(bundleOperations.configuredBundleCount()).thenReturn(2);

        MockMvc mvc = mvc(provider(renderOperations));

        mvc.perform(get("/analytics/api/v1/capabilities")
                        .header("X-Request-Id", "request-capabilities")
                        .header("X-Trace-Id", "trace-capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.analyticsRuntimeApiVersion")
                        .value("foggy-analytics-runtime-api/v1"))
                .andExpect(jsonPath("$.data.api").value("analytics"))
                .andExpect(jsonPath("$.data.operations['analytics.bundles.pull']")
                        .value("unsupported"))
                .andExpect(jsonPath("$.data.operations['analytics.bundles.save']")
                        .value("unsupported"))
                .andExpect(jsonPath("$.data.operations['analytics.reports.preview']")
                        .value("supported"))
                .andExpect(jsonPath("$.data.operations['models.list']").doesNotExist())
                .andExpect(jsonPath("$.context.requestId").value("request-capabilities"))
                .andExpect(jsonPath("$.context.traceId").value("trace-capabilities"));
    }

    @Test
    void cohostsWithFoggyRuntimeApiRouteWithoutMergingContracts() throws Exception {
        when(bundleOperations.configuredBundleCount()).thenReturn(0);
        ObjectProvider<AnalyticsRuntimeRenderOperations> provider =
                provider(renderOperations);
        MockMvc cohosted = standaloneSetup(
                        new AnalyticsCapabilitiesController(
                                properties,
                                bundleOperations,
                                provider,
                                responses),
                        new FoggyRuntimeSiblingController())
                .build();

        cohosted.perform(get("/api/v1/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runtimeApiVersion")
                        .value("foggy-runtime-api/v1"))
                .andExpect(jsonPath("$.data.capabilities['runtime.capabilities']")
                        .value("supported"))
                .andExpect(jsonPath("$.data.operations").doesNotExist());
        cohosted.perform(get("/analytics/api/v1/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analyticsRuntimeApiVersion")
                        .value("foggy-analytics-runtime-api/v1"))
                .andExpect(jsonPath("$.data.operations['analytics.capabilities']")
                        .value("supported"))
                .andExpect(jsonPath("$.data.capabilities").doesNotExist());
    }

    @Test
    void validatesTrustedBundleWithoutExposingFilesystemRoots() throws Exception {
        AnalyticsBundleSummary summary = new AnalyticsBundleSummary(
                "sales",
                REVISION,
                "1.0",
                "default",
                "CONFIGURED",
                "CURRENT",
                false,
                true,
                null);
        when(bundleOperations.validate(any(), any())).thenReturn(summary);

        String response = mvc(provider(renderOperations))
                .perform(post("/analytics/api/v1/bundles/sales/validate")
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedBundleRevision": "%s",
                                  "requestId": "request-validate",
                                  "traceId": "trace-validate"
                                }
                                """.formatted(REVISION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bundleRef").value("sales"))
                .andExpect(jsonPath("$.data.bundleRevision").value(REVISION))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(response.contains("path"));
        assertFalse(response.contains("root"));
        verify(bundleOperations).validate(
                new com.foggyframework.analytics.definition.api.AnalyticsBundleRef("sales"),
                REVISION);
    }

    @Test
    void mapsReportPreviewToExactRevisionAndOpaqueAuthorityBinding() throws Exception {
        when(renderOperations.previewReport(any())).thenReturn(renderModel());
        ArgumentCaptor<AnalyticsReportPreviewRequest> requestCaptor =
                ArgumentCaptor.forClass(AnalyticsReportPreviewRequest.class);

        mvc(provider(renderOperations))
                .perform(post("/analytics/api/v1/bundles/sales/reports/sales-summary/preview")
                        .contentType("application/json")
                        .content(renderRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.artifact.kind").value("report"))
                .andExpect(jsonPath("$.data.artifact.ref").value("sales-summary"))
                .andExpect(jsonPath("$.data.resolvedBundleRevision").value(REVISION))
                .andExpect(jsonPath("$.data.widgets[0].visual.kind").value("table"))
                .andExpect(jsonPath("$.context.requestId").value("request-preview"))
                .andExpect(jsonPath("$.context.traceId").value("trace-preview"));

        verify(renderOperations).previewReport(requestCaptor.capture());
        AnalyticsReportPreviewRequest mapped = requestCaptor.getValue();
        assertEquals("sales", mapped.bundleRef().value());
        assertEquals(REVISION, mapped.expectedBundleRevision().value());
        assertEquals("sales-summary", mapped.reportRef().value());
        assertEquals("tms", mapped.context().authorityBinding().provider());
        assertEquals("subject:42", mapped.context().authorityBinding().reference());
        assertEquals("east", mapped.context().parameters().get("region"));
    }

    @Test
    void failsClosedWhenHostAuthorityCompositionIsMissing() throws Exception {
        mvc(provider(null))
                .perform(post("/analytics/api/v1/bundles/sales/reports/sales-summary/preview")
                        .contentType("application/json")
                        .content(renderRequest()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("ANALYTICS_RENDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.phase").value("composition"));
    }

    @Test
    void sanitizesBundleFailuresAtTheApiBoundary() throws Exception {
        when(bundleOperations.validate(any(), any())).thenThrow(
                new AnalyticsBundleStoreException(
                        AnalyticsBundleStoreException.Code.INVALID_BUNDLE,
                        "invalid file at /srv/private/customer-a/manifest.json"));

        String response = mvc(provider(renderOperations))
                .perform(post("/analytics/api/v1/bundles/sales/validate")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code")
                        .value("ANALYTICS_BUNDLE_INVALID_BUNDLE"))
                .andExpect(jsonPath("$.error.message")
                        .value("Analytics Bundle validation failed."))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(response.contains("/srv/private"));
        assertFalse(response.contains("customer-a"));
    }

    private MockMvc mvc(ObjectProvider<AnalyticsRuntimeRenderOperations> provider) {
        AnalyticsCapabilitiesController capabilities = new AnalyticsCapabilitiesController(
                properties,
                bundleOperations,
                provider,
                responses);
        AnalyticsBundlesController bundles = new AnalyticsBundlesController(
                bundleOperations,
                responses);
        AnalyticsRenderController render = new AnalyticsRenderController(provider, responses);
        return standaloneSetup(capabilities, bundles, render)
                .setControllerAdvice(new AnalyticsRuntimeApiExceptionHandler(responses))
                .build();
    }

    private static ObjectProvider<AnalyticsRuntimeRenderOperations> provider(
            AnalyticsRuntimeRenderOperations operations) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (operations != null) {
            factory.addBean("analyticsRuntimeRenderOperations", operations);
        }
        return factory.getBeanProvider(AnalyticsRuntimeRenderOperations.class);
    }

    private static AnalyticsRenderModel renderModel() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("region", "east");
        row.put("amount", null);
        AnalyticsWidgetData widget = new AnalyticsWidgetData(
                "sales-summary",
                new AnalyticsVisualIntent(AnalyticsVisualKind.TABLE, Map.of()),
                AnalyticsRenderState.READY,
                List.of(
                        new AnalyticsColumnSchema("region", "string", true),
                        new AnalyticsColumnSchema("amount", "decimal", true)),
                List.of(row),
                false,
                List.of());
        return new AnalyticsRenderModel(
                new AnalyticsArtifactRef(AnalyticsArtifactKind.REPORT, "sales-summary"),
                new AnalyticsBundleRevision(REVISION),
                AnalyticsRenderState.READY,
                List.of(widget),
                List.of());
    }

    private static String renderRequest() {
        return """
                {
                  "expectedBundleRevision": "%s",
                  "parameters": {"region": "east"},
                  "timezone": "Asia/Shanghai",
                  "locale": "zh-CN",
                  "authority": {
                    "provider": "tms",
                    "reference": "subject:42"
                  },
                  "requestId": "request-preview",
                  "traceId": "trace-preview"
                }
                """.formatted(REVISION);
    }

    @RestController
    static class FoggyRuntimeSiblingController {

        @GetMapping("/api/v1/capabilities")
        Map<String, Object> capabilities() {
            return Map.of(
                    "success", true,
                    "engine", "java",
                    "runtimeApiVersion", "foggy-runtime-api/v1",
                    "data", Map.of(
                            "capabilities",
                            Map.of("runtime.capabilities", "supported")));
        }
    }
}
