package com.foggyframework.analytics.runtime.api;

import com.foggyframework.analytics.function.contract.AnalyticsArtifactDescription;
import com.foggyframework.analytics.function.contract.AnalyticsArtifactFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleList;
import com.foggyframework.analytics.function.contract.AnalyticsComposeFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsComposeResult;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionCapabilities;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionErrorCodes;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionOperations;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyDescription;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyResolutionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelResult;
import com.foggyframework.analytics.function.contract.AnalyticsRenderFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryResult;
import com.foggyframework.analytics.runtime.api.config.FoggyAnalyticsRuntimeApiProperties;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsBundlesController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsCapabilitiesController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsComposeController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsModelDependenciesController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsRenderController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsRuntimeApiExceptionHandler;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsSemanticQueryController;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiResponseFactory;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeHttpResponseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AnalyticsRuntimeApiControllerContractTest {

    private static final String REVISION = "sha256:" + "a".repeat(64);

    private AnalyticsFunctionEndpoint endpoint;
    private AnalyticsRuntimeApiResponseFactory responses;
    private AnalyticsRuntimeHttpResponseMapper http;

    @BeforeEach
    void setUp() {
        FoggyAnalyticsRuntimeApiProperties properties =
                new FoggyAnalyticsRuntimeApiProperties();
        properties.setEnabled(true);
        endpoint = mock(AnalyticsFunctionEndpoint.class);
        responses = new AnalyticsRuntimeApiResponseFactory(properties);
        http = new AnalyticsRuntimeHttpResponseMapper();
    }

    @Test
    void exposesIndependentCapabilitiesAndFunctionContractVersion() throws Exception {
        when(endpoint.capabilities(any())).thenReturn(responses.ok(
                capabilities(),
                "request-capabilities",
                "trace-capabilities"));

        mvc().perform(get("/analytics/api/v1/capabilities")
                        .header("X-Request-Id", "request-capabilities")
                        .header("X-Trace-Id", "trace-capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.functionContractVersion")
                        .value("foggy-analytics-function/v1"))
                .andExpect(jsonPath("$.analyticsRuntimeApiVersion")
                        .value("foggy-analytics-runtime-api/v1"))
                .andExpect(jsonPath("$.data.operations['analytics.bundles.describe']")
                        .value("supported"))
                .andExpect(jsonPath("$.data.operations['analytics.bundles.pull']")
                        .value("unsupported"))
                .andExpect(jsonPath("$.data.operations['models.list']").doesNotExist())
                .andExpect(jsonPath("$.context.requestId")
                        .value("request-capabilities"))
                .andExpect(jsonPath("$.context.traceId")
                        .value("trace-capabilities"));

        ArgumentCaptor<AnalyticsFunctionRequestContext> context =
                ArgumentCaptor.forClass(AnalyticsFunctionRequestContext.class);
        verify(endpoint).capabilities(context.capture());
        assertEquals("request-capabilities", context.getValue().requestId());
    }

    @Test
    void cohostsWithFoggyRuntimeApiRouteWithoutMergingContracts() throws Exception {
        when(endpoint.capabilities(any())).thenReturn(responses.ok(
                capabilities(), null, null));
        MockMvc cohosted = standaloneSetup(
                capabilitiesController(),
                new FoggyRuntimeSiblingController()).build();

        cohosted.perform(get("/api/v1/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runtimeApiVersion")
                        .value("foggy-runtime-api/v1"))
                .andExpect(jsonPath("$.data.capabilities['runtime.capabilities']")
                        .value("supported"))
                .andExpect(jsonPath("$.functionContractVersion").doesNotExist());
        cohosted.perform(get("/analytics/api/v1/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.functionContractVersion")
                        .value("foggy-analytics-function/v1"))
                .andExpect(jsonPath("$.data.operations['analytics.capabilities']")
                        .value("supported"))
                .andExpect(jsonPath("$.data.capabilities").doesNotExist());
    }

    @Test
    void validatesAndDescribesLogicalBundleWithoutExposingFilesystemRoots()
            throws Exception {
        AnalyticsBundleDescription description = description();
        when(endpoint.validateBundle(any())).thenReturn(responses.ok(
                description, "request-validate", "trace-validate"));
        when(endpoint.describeBundle(any())).thenReturn(responses.ok(
                description, "request-describe", "trace-describe"));

        String validateResponse = mvc()
                .perform(post("/analytics/api/v1/bundles/sales/validate")
                        .contentType("application/json")
                        .content(bundleRequest("request-validate", "trace-validate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bundleRef").value("sales"))
                .andExpect(jsonPath("$.data.bundleRevision").value(REVISION))
                .andReturn().getResponse().getContentAsString();
        mvc().perform(post("/analytics/api/v1/bundles/sales/describe")
                        .contentType("application/json")
                        .content(bundleRequest("request-describe", "trace-describe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.namespaceRef").value("default"));

        assertFalse(validateResponse.contains("path"));
        assertFalse(validateResponse.contains("root"));
        ArgumentCaptor<AnalyticsBundleFunctionRequest> request =
                ArgumentCaptor.forClass(AnalyticsBundleFunctionRequest.class);
        verify(endpoint).validateBundle(request.capture());
        assertEquals("sales", request.getValue().bundleRef());
        assertEquals(REVISION, request.getValue().expectedBundleRevision());
    }

    @Test
    void listsOnlyContractBundleDescriptions() throws Exception {
        when(endpoint.listBundles(any())).thenReturn(responses.ok(
                new AnalyticsBundleList(List.of(description())),
                "request-list",
                "trace-list"));

        mvc().perform(get("/analytics/api/v1/bundles")
                        .header("X-Request-Id", "request-list")
                        .header("X-Trace-Id", "trace-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bundles[0].bundleRef").value("sales"))
                .andExpect(jsonPath("$.data.bundles[0].valid").value(true));
    }

    @Test
    void executesOnlyTheTypedSemanticQueryAgainstTheCurrentModel()
            throws Exception {
        when(endpoint.executeSemanticQuery(any())).thenReturn(responses.ok(
                new AnalyticsSemanticQueryResult(
                        "default",
                        "FactOrderQueryModel",
                        List.of(new AnalyticsSemanticQueryResult.Column(
                                "orderCount", "LONG", "订单数")),
                        List.of(Map.of("orderCount", 12)),
                        1L,
                        false,
                        false,
                        List.of()),
                "request-question",
                "trace-question"));

        mvc().perform(post(
                        "/analytics/api/v1/semantic-models/FactOrderQueryModel/query")
                        .contentType("application/json")
                        .content("""
                                {
                                  "namespace": "default",
                                  "query": {
                                    "columns": ["orderCount"],
                                    "filters": [],
                                    "groupBy": [],
                                    "orderBy": [],
                                    "start": 0,
                                    "limit": 100,
                                    "returnTotal": true,
                                    "distinct": false
                                  },
                                  "authority": {
                                    "provider": "tms",
                                    "reference": "subject:42"
                                  },
                                  "requestId": "request-question",
                                  "traceId": "trace-question"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].orderCount").value(12))
                .andExpect(jsonPath("$.data.modelRevision").doesNotExist())
                .andExpect(jsonPath("$.data.debug").doesNotExist());

        ArgumentCaptor<AnalyticsSemanticQueryFunctionRequest> captured =
                ArgumentCaptor.forClass(AnalyticsSemanticQueryFunctionRequest.class);
        verify(endpoint).executeSemanticQuery(captured.capture());
        assertEquals("FactOrderQueryModel", captured.getValue().modelName());
        assertEquals("subject:42", captured.getValue().authority().reference());
        assertEquals(List.of("orderCount"), captured.getValue().query().columns());
    }

    @Test
    void runsTheFullQueryModelDslAgainstTheCurrentModel() throws Exception {
        when(endpoint.runQueryModel(any())).thenReturn(responses.ok(
                new AnalyticsQueryModelResult(
                        "default",
                        "FactOrderQueryModel",
                        "execute",
                        Map.of("rows", List.of(Map.of("province", "山东省")))),
                "request-dsl",
                "trace-dsl"));

        mvc().perform(post(
                        "/analytics/api/v1/semantic-models/FactOrderQueryModel/query-model")
                        .contentType("application/json")
                        .content("""
                                {
                                  "namespace": "default",
                                  "mode": "execute",
                                  "payload": {
                                    "columns": ["province", "sum(orderAmount)"],
                                    "groupBy": ["province"],
                                    "orderBy": [{"field": "sum(orderAmount)", "direction": "desc"}],
                                    "limit": 10,
                                    "returnTotal": true
                                  },
                                  "authority": {
                                    "provider": "tms",
                                    "reference": "subject:42"
                                  },
                                  "requestId": "request-dsl",
                                  "traceId": "trace-dsl"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("execute"))
                .andExpect(jsonPath("$.data.response.rows[0].province")
                        .value("山东省"));

        ArgumentCaptor<AnalyticsQueryModelFunctionRequest> captured =
                ArgumentCaptor.forClass(AnalyticsQueryModelFunctionRequest.class);
        verify(endpoint).runQueryModel(captured.capture());
        assertEquals("FactOrderQueryModel", captured.getValue().modelName());
        assertEquals("execute", captured.getValue().mode());
        assertEquals(BigInteger.TEN, captured.getValue().payload().get("limit"));
        assertEquals("subject:42", captured.getValue().authority().reference());
    }

    @Test
    void runsRestrictedComposeWithoutAcceptingAuthorityInsideTheScriptPayload()
            throws Exception {
        when(endpoint.runCompose(any())).thenReturn(responses.ok(
                new AnalyticsComposeResult(
                        "default",
                        "execute",
                        true,
                        true,
                        List.of(Map.of("province", "山东省")),
                        "WITH regional AS (...) SELECT * FROM regional",
                        List.of("山东省"),
                        List.of()),
                "request-compose",
                "trace-compose"));

        mvc().perform(post("/analytics/api/v1/compose")
                        .contentType("application/json")
                        .content("""
                                {
                                  "namespace": "default",
                                  "mode": "execute",
                                  "script": "let regional = queryModel('FactOrderQueryModel', { columns: ['province'] }); return regional;",
                                  "params": {"province": "山东省"},
                                  "authority": {
                                    "provider": "tms",
                                    "reference": "subject:42"
                                  },
                                  "requestId": "request-compose",
                                  "traceId": "trace-compose"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.executed").value(true))
                .andExpect(jsonPath("$.data.value[0].province").value("山东省"));

        ArgumentCaptor<AnalyticsComposeFunctionRequest> captured =
                ArgumentCaptor.forClass(AnalyticsComposeFunctionRequest.class);
        verify(endpoint).runCompose(captured.capture());
        assertEquals("default", captured.getValue().namespace());
        assertEquals("execute", captured.getValue().mode());
        assertEquals("山东省", captured.getValue().params().get("province"));
        assertEquals("subject:42", captured.getValue().authority().reference());
    }

    @Test
    void describesAnExactParsedArtifactWithoutDataAuthority() throws Exception {
        when(endpoint.describeArtifact(any())).thenReturn(responses.ok(
                new AnalyticsArtifactDescription(
                        "sales", REVISION, "report", "sales-summary"),
                "request-artifact",
                "trace-artifact"));

        mvc().perform(post(
                        "/analytics/api/v1/bundles/sales/artifacts/report/"
                                + "sales-summary/describe")
                        .contentType("application/json")
                        .content(bundleRequest("request-artifact", "trace-artifact")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bundleRef").value("sales"))
                .andExpect(jsonPath("$.data.bundleRevision").value(REVISION))
                .andExpect(jsonPath("$.data.artifactKind").value("report"))
                .andExpect(jsonPath("$.data.artifactRef").value("sales-summary"));

        ArgumentCaptor<AnalyticsArtifactFunctionRequest> request =
                ArgumentCaptor.forClass(AnalyticsArtifactFunctionRequest.class);
        verify(endpoint).describeArtifact(request.capture());
        assertEquals("report", request.getValue().artifactKind());
        assertEquals(REVISION, request.getValue().expectedBundleRevision());
    }

    @Test
    void resolvesInternalDependencyDigestWithoutProductOrDataAuthority() throws Exception {
        when(endpoint.resolveModelDependency(any())).thenReturn(responses.ok(
                new AnalyticsModelDependencyDescription(
                        "tms-ai",
                        "qm",
                        "TenantOrgManagementQuery",
                        REVISION),
                "request-model",
                "trace-model"));

        String response = mvc().perform(post(
                        "/analytics/api/v1/model-dependencies/resolve")
                        .contentType("application/json")
                        .content("""
                                {
                                  "namespace": "tms-ai",
                                  "modelKind": "qm",
                                  "modelName": "TenantOrgManagementQuery",
                                  "requestId": "request-model",
                                  "traceId": "trace-model"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dependencyDigest").value(REVISION))
                .andExpect(jsonPath("$.data.namespace").value("tms-ai"))
                .andReturn().getResponse().getContentAsString();

        ArgumentCaptor<AnalyticsModelDependencyResolutionRequest> request =
                ArgumentCaptor.forClass(
                        AnalyticsModelDependencyResolutionRequest.class);
        verify(endpoint).resolveModelDependency(request.capture());
        assertEquals("qm", request.getValue().modelKind());
        assertEquals("request-model", request.getValue().context().requestId());
        assertFalse(response.contains("owner"));
        assertFalse(response.contains("authority"));
        assertFalse(response.contains("catalogIdentity"));
    }

    @Test
    void sanitizesInvalidModelDependencyRequestsBeforeEndpointInvocation()
            throws Exception {
        List<String> invalidBodies = List.of(
                """
                {
                  "namespace": "tms-ai",
                  "modelKind": "sql",
                  "modelName": "TenantOrgManagementQuery"
                }
                """,
                """
                {
                  "namespace": "",
                  "modelKind": "qm",
                  "modelName": "TenantOrgManagementQuery"
                }
                """,
                "null",
                "{");

        for (String body : invalidBodies) {
            mvc().perform(post("/analytics/api/v1/model-dependencies/resolve")
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code")
                            .value("ANALYTICS_INVALID_REQUEST"))
                    .andExpect(jsonPath("$.error.message")
                            .value("Analytics request is invalid."));
        }

        verify(endpoint, never()).resolveModelDependency(any());
    }

    @Test
    void mapsReportPreviewToExactRevisionAndOpaqueAuthorityBinding()
            throws Exception {
        when(endpoint.previewReport(any())).thenReturn(responses.ok(
                renderResult(), "request-preview", "trace-preview"));
        ArgumentCaptor<AnalyticsRenderFunctionRequest> request =
                ArgumentCaptor.forClass(AnalyticsRenderFunctionRequest.class);

        mvc().perform(post(
                        "/analytics/api/v1/bundles/sales/reports/sales-summary/preview")
                        .contentType("application/json")
                        .content(renderRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.artifact.kind").value("report"))
                .andExpect(jsonPath("$.data.artifact.ref").value("sales-summary"))
                .andExpect(jsonPath("$.data.resolvedBundleRevision").value(REVISION))
                .andExpect(jsonPath("$.data.widgets[0].visual.kind").value("table"))
                .andExpect(jsonPath("$.context.requestId").value("request-preview"));

        verify(endpoint).previewReport(request.capture());
        AnalyticsRenderFunctionRequest mapped = request.getValue();
        assertEquals("sales", mapped.bundleRef());
        assertEquals(REVISION, mapped.expectedBundleRevision());
        assertEquals("sales-summary", mapped.artifactRef());
        assertEquals("tms", mapped.authority().provider());
        assertEquals("subject:42", mapped.authority().reference());
        assertEquals("east", mapped.parameters().get("region"));
        assertEquals(BigInteger.valueOf(2), mapped.parameters().get("limit"));
        assertEquals(
                new BigDecimal("0.123456789012345678901234567890"),
                mapped.parameters().get("ratio"));
        assertNull(mapped.parameters().get("optional"));
    }

    @Test
    void rejectsProvidedHeaderUnsafeCorrelationInsteadOfSilentlyReplacingIt()
            throws Exception {
        mvc().perform(post("/analytics/api/v1/bundles/sales/validate")
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "bad id"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code")
                        .value("ANALYTICS_INVALID_REQUEST"))
                .andExpect(jsonPath("$.context.requestId").isNotEmpty());
    }

    @Test
    void allowsRuntimeToGenerateMissingCorrelationForTransportParity()
            throws Exception {
        when(endpoint.previewReport(any())).thenAnswer(invocation -> {
            AnalyticsRenderFunctionRequest request = invocation.getArgument(0);
            var normalized = responses.functionResponses().context(request.context());
            return responses.functionResponses().ok(renderResult(), normalized);
        });

        mvc().perform(post(
                        "/analytics/api/v1/bundles/sales/reports/sales-summary/preview")
                        .contentType("application/json")
                        .content(renderRequestWithoutContext()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.requestId").isNotEmpty())
                .andExpect(jsonPath("$.context.traceId").isNotEmpty());

        ArgumentCaptor<AnalyticsRenderFunctionRequest> request =
                ArgumentCaptor.forClass(AnalyticsRenderFunctionRequest.class);
        verify(endpoint).previewReport(request.capture());
        assertNull(request.getValue().context().requestId());
        assertNull(request.getValue().context().traceId());
    }

    @Test
    void preservesUnavailableCompositionAsCanonicalFunctionFailure()
            throws Exception {
        when(endpoint.previewReport(any())).thenReturn(responses.fail(
                "ANALYTICS_RENDER_UNAVAILABLE",
                "composition",
                "Analytics preview/render is unavailable in this host composition.",
                false,
                "request-preview",
                "trace-preview"));

        mvc().perform(post(
                        "/analytics/api/v1/bundles/sales/reports/sales-summary/preview")
                        .contentType("application/json")
                        .content(renderRequest()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("ANALYTICS_RENDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.phase").value("composition"));
    }

    @Test
    void returnsSanitizedBundleFailureWithoutTransportSpecificMutation()
            throws Exception {
        when(endpoint.validateBundle(any())).thenReturn(responses.fail(
                "ANALYTICS_BUNDLE_INVALID_BUNDLE",
                "bundle",
                "Analytics Bundle validation failed.",
                false,
                "request-validate",
                "trace-validate"));

        String response = mvc()
                .perform(post("/analytics/api/v1/bundles/sales/validate")
                        .contentType("application/json")
                        .content(bundleRequest("request-validate", "trace-validate")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code")
                        .value("ANALYTICS_BUNDLE_INVALID_BUNDLE"))
                .andExpect(jsonPath("$.error.message")
                        .value("Analytics Bundle validation failed."))
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains("/srv/private"));
        assertFalse(response.contains("customer-a"));
    }

    @Test
    void mapsCanonicalBundleNotRegisteredCodeToHttpNotFound() throws Exception {
        when(endpoint.describeBundle(any())).thenReturn(responses.fail(
                "ANALYTICS_BUNDLE_NOT_REGISTERED",
                "bundle",
                "Analytics Bundle is not registered.",
                false,
                "request-describe",
                "trace-describe"));

        mvc().perform(post("/analytics/api/v1/bundles/missing/describe")
                        .contentType("application/json")
                        .content(bundleRequest("request-describe", "trace-describe")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code")
                        .value("ANALYTICS_BUNDLE_NOT_REGISTERED"));
    }

    @Test
    void mapsEveryCoreFunctionErrorFamilyToAStableHttpStatus() {
        Map<String, HttpStatus> expected = Map.ofEntries(
                Map.entry(AnalyticsFunctionErrorCodes.INVALID_REQUEST,
                        HttpStatus.BAD_REQUEST),
                Map.entry(AnalyticsFunctionErrorCodes.BUNDLE_NOT_REGISTERED,
                        HttpStatus.NOT_FOUND),
                Map.entry(AnalyticsFunctionErrorCodes.REPORT_NOT_FOUND,
                        HttpStatus.NOT_FOUND),
                Map.entry(AnalyticsFunctionErrorCodes.DASHBOARD_NOT_FOUND,
                        HttpStatus.NOT_FOUND),
                Map.entry(AnalyticsFunctionErrorCodes.QUERY_NOT_FOUND,
                        HttpStatus.NOT_FOUND),
                Map.entry(AnalyticsFunctionErrorCodes.MODEL_DEPENDENCY_NOT_FOUND,
                        HttpStatus.NOT_FOUND),
                Map.entry(
                        AnalyticsFunctionErrorCodes.MODEL_DEPENDENCY_DIGEST_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE),
                Map.entry(AnalyticsFunctionErrorCodes.BUNDLE_REVISION_CONFLICT,
                        HttpStatus.CONFLICT),
                Map.entry(AnalyticsFunctionErrorCodes.BUNDLE_DEPENDENCY_STALE,
                        HttpStatus.CONFLICT),
                Map.entry(AnalyticsFunctionErrorCodes.BUNDLE_IMMUTABLE,
                        HttpStatus.FORBIDDEN),
                Map.entry(AnalyticsFunctionErrorCodes.BUNDLE_INVALID,
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Map.entry(AnalyticsFunctionErrorCodes.BUNDLE_IDENTITY_MISMATCH,
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Map.entry(AnalyticsFunctionErrorCodes.BUNDLE_DIGEST_MISMATCH,
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Map.entry(AnalyticsFunctionErrorCodes.BUNDLE_UNSAFE_PATH,
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Map.entry(
                        AnalyticsFunctionErrorCodes.BUNDLE_UNSUPPORTED_RESOURCE_PATH,
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Map.entry(AnalyticsFunctionErrorCodes.COMPOSE_INVALID,
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Map.entry(AnalyticsFunctionErrorCodes.COMPOSE_SANDBOX_VIOLATION,
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Map.entry(AnalyticsFunctionErrorCodes.BUNDLE_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE),
                Map.entry(AnalyticsFunctionErrorCodes.BUNDLE_RECOVERY_FAILED,
                        HttpStatus.SERVICE_UNAVAILABLE),
                Map.entry(AnalyticsFunctionErrorCodes.RENDER_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE),
                Map.entry(AnalyticsFunctionErrorCodes.INTERNAL_ERROR,
                        HttpStatus.INTERNAL_SERVER_ERROR));

        expected.forEach((code, status) -> assertEquals(
                status,
                http.map(responses.fail(
                        code,
                        "test",
                        "sanitized",
                        false,
                        "request-status",
                        "trace-status")).getStatusCode()));
    }

    private MockMvc mvc() {
        return standaloneSetup(
                        capabilitiesController(),
                        new AnalyticsBundlesController(endpoint, responses, http),
                        new AnalyticsModelDependenciesController(endpoint, responses, http),
                        new AnalyticsRenderController(endpoint, responses, http),
                        new AnalyticsSemanticQueryController(endpoint, responses, http),
                        new AnalyticsComposeController(endpoint, responses, http))
                .setControllerAdvice(new AnalyticsRuntimeApiExceptionHandler(responses))
                .build();
    }

    private AnalyticsCapabilitiesController capabilitiesController() {
        return new AnalyticsCapabilitiesController(endpoint, responses, http);
    }

    private static AnalyticsFunctionCapabilities capabilities() {
        return new AnalyticsFunctionCapabilities(
                "analytics",
                "foggy-analytics-runtime-api/v1",
                "analytics-runtime/v1",
                true,
                "host-managed",
                Map.of(
                        AnalyticsFunctionOperations.CAPABILITIES, "supported",
                        AnalyticsFunctionOperations.BUNDLES_DESCRIBE, "supported",
                        AnalyticsFunctionOperations.BUNDLES_PULL, "unsupported"),
                new AnalyticsFunctionCapabilities.Limits(1_000, 2),
                List.of());
    }

    private static AnalyticsBundleDescription description() {
        return new AnalyticsBundleDescription(
                "sales",
                REVISION,
                "1.0",
                "default",
                "CONFIGURED",
                "CURRENT",
                false,
                true,
                null);
    }

    private static AnalyticsRenderResult renderResult() {
        return new AnalyticsRenderResult(
                new AnalyticsRenderResult.Artifact("report", "sales-summary"),
                REVISION,
                "ready",
                List.of(new AnalyticsRenderResult.Widget(
                        "sales-summary",
                        new AnalyticsRenderResult.Visual("table", Map.of()),
                        "ready",
                        List.of(
                                new AnalyticsRenderResult.Column(
                                        "region", "string", true),
                                new AnalyticsRenderResult.Column(
                                        "amount", "decimal", true)),
                        List.of(Map.of("region", "east", "amount", 42)),
                        false,
                        List.of())),
                List.of());
    }

    private static String bundleRequest(String requestId, String traceId) {
        return """
                {
                  "expectedBundleRevision": "%s",
                  "requestId": "%s",
                  "traceId": "%s"
                }
                """.formatted(REVISION, requestId, traceId);
    }

    private static String renderRequest() {
        return """
                {
                  "expectedBundleRevision": "%s",
                  "parameters": {
                    "region": "east",
                    "limit": 2,
                    "ratio": 0.123456789012345678901234567890,
                    "optional": null
                  },
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

    private static String renderRequestWithoutContext() {
        return """
                {
                  "expectedBundleRevision": "%s",
                  "parameters": {
                    "region": "east",
                    "limit": 2,
                    "ratio": 0.123456789012345678901234567890,
                    "optional": null
                  },
                  "timezone": "Asia/Shanghai",
                  "locale": "zh-CN",
                  "authority": {
                    "provider": "tms",
                    "reference": "subject:42"
                  }
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
