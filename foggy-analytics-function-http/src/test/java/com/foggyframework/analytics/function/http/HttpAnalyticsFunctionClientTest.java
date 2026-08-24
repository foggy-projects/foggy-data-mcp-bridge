package com.foggyframework.analytics.function.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.function.contract.AnalyticsArtifactDescription;
import com.foggyframework.analytics.function.contract.AnalyticsArtifactFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleList;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionCapabilities;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContract;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionError;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionOperations;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyDescription;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyResolutionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelDescription;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQuery;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryResult;
import com.foggyframework.analytics.function.sdk.AnalyticsFunctionClient;
import com.foggyframework.analytics.function.sdk.AnalyticsFunctionClients;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpAnalyticsFunctionClientTest {

    private static final String REVISION = "sha256:" + "a".repeat(64);
    private static final AnalyticsFunctionRequestContext CONTEXT =
            new AnalyticsFunctionRequestContext("request-1", "trace-1");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<CapturedRequest> requests = new ArrayList<>();

    private HttpServer server;
    private AnalyticsHttpClientOptions options;
    private HttpAnalyticsFunctionClient client;
    private AnalyticsFunctionEnvelope<?> forcedOutcome;
    private int forcedStatus;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/analytics/api/v1", this::handle);
        server.start();
        options = new AnalyticsHttpClientOptions(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/analytics"),
                Duration.ofSeconds(2),
                "management-secret",
                "Bearer data-secret");
        client = new HttpAnalyticsFunctionClient(options);
        forcedStatus = 200;
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsEveryV1RouteAndScopesAuthorizationToRenderDataPlane() {
        assertTrue(client.capabilities(CONTEXT).success());
        assertTrue(client.listBundles(CONTEXT).success());
        assertTrue(client.validateBundle(bundleRequest()).success());
        assertTrue(client.describeBundle(bundleRequest()).success());
        assertTrue(client.describeArtifact(artifactRequest()).success());
        assertTrue(client.resolveModelDependency(modelDependencyRequest()).success());
        assertTrue(client.describeSemanticModel(semanticModelRequest()).success());
        assertTrue(client.executeSemanticQuery(semanticQueryRequest()).success());
        assertTrue(client.previewReport(renderRequest()).success());
        assertTrue(client.previewDashboard(renderRequest()).success());
        assertTrue(client.renderDashboard(renderRequest()).success());

        assertEquals(List.of(
                        "/analytics/api/v1/capabilities",
                        "/analytics/api/v1/bundles",
                        "/analytics/api/v1/bundles/sales/validate",
                        "/analytics/api/v1/bundles/sales/describe",
                        "/analytics/api/v1/bundles/sales/artifacts/report/"
                                + "sales-summary/describe",
                        "/analytics/api/v1/model-dependencies/resolve",
                        "/analytics/api/v1/semantic-models/FactOrderQueryModel/describe",
                        "/analytics/api/v1/semantic-models/FactOrderQueryModel/query",
                        "/analytics/api/v1/bundles/sales/reports/sales-summary/preview",
                        "/analytics/api/v1/bundles/sales/dashboards/sales-summary/preview",
                        "/analytics/api/v1/bundles/sales/dashboards/sales-summary/render"),
                requests.stream().map(CapturedRequest::path).toList());
        assertEquals(List.of(
                        "GET", "GET", "POST", "POST", "POST", "POST", "POST", "POST",
                        "POST", "POST", "POST"),
                requests.stream().map(CapturedRequest::method).toList());
        requests.forEach(request -> assertEquals(
                "management-secret", request.authCode()));
        requests.subList(0, 6).forEach(request ->
                assertNull(request.authorization()));
        requests.subList(6, 11).forEach(request -> assertEquals(
                "Bearer data-secret", request.authorization()));
        assertEquals(REVISION,
                requests.get(2).body().get("expectedBundleRevision"));
        assertFalse(requests.get(2).body().containsKey("bundleRef"));
        @SuppressWarnings("unchecked")
        Map<String, Object> authority = (Map<String, Object>)
                requests.get(6).body().get("authority");
        assertEquals("tms", authority.get("provider"));
        assertEquals("subject:42", authority.get("reference"));
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>)
                requests.get(8).body().get("parameters");
        assertEquals(2, parameters.get("limit"));
        assertTrue(parameters.containsKey("optional"));
        assertNull(parameters.get("optional"));
        assertEquals("tms-ai", requests.get(5).body().get("namespace"));
        assertEquals("TenantOrgManagementQuery",
                requests.get(5).body().get("modelName"));
        assertEquals(REVISION,
                requests.get(7).body().get("expectedModelRevision"));
        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>)
                requests.get(7).body().get("query");
        assertFalse(query.containsKey("rawSql"));
    }

    @Test
    void embeddedAndHttpClientsReturnTheSameCanonicalOutcome() {
        AnalyticsFunctionEnvelope<AnalyticsRenderResult> expected =
                AnalyticsFunctionEnvelope.ok(
                        AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                        AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION,
                        renderResult(),
                        new AnalyticsFunctionContext("request-1", "trace-1"));
        forcedOutcome = expected;
        AnalyticsFunctionClient embedded = AnalyticsFunctionClients.embedded(
                new EndpointStub() {
                    @Override
                    public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
                            AnalyticsRenderFunctionRequest request) {
                        return expected;
                    }
                });

        assertEquals(
                embedded.previewReport(renderRequest()),
                client.previewReport(renderRequest()));
    }

    @Test
    void preservesServerErrorsWithoutLeakingTransportDetails() {
        AnalyticsFunctionEnvelope<AnalyticsBundleDescription> expected =
                AnalyticsFunctionEnvelope.fail(
                        AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                        AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION,
                        new AnalyticsFunctionError(
                                "ANALYTICS_BUNDLE_REVISION_CONFLICT",
                                "bundle",
                                "Analytics Bundle revision does not match.",
                                false),
                        new AnalyticsFunctionContext("request-1", "trace-1"));
        forcedOutcome = expected;
        forcedStatus = 409;

        AnalyticsFunctionEnvelope<AnalyticsBundleDescription> actual =
                client.validateBundle(bundleRequest());

        assertEquals(expected, actual);
        assertFalse(actual.error().message().contains(options.baseUrl().toString()));
    }

    @Test
    void failsClosedForProtocolMismatchAndUnavailableTransport() {
        forcedOutcome = AnalyticsFunctionEnvelope.ok(
                AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION,
                description(),
                new AnalyticsFunctionContext("request-1", "trace-1"));
        forcedStatus = 500;

        var protocol = client.validateBundle(bundleRequest());
        assertFalse(protocol.success());
        assertEquals("ANALYTICS_CLIENT_PROTOCOL_ERROR", protocol.error().code());
        assertFalse(protocol.error().retryable());

        forcedStatus = 200;
        forcedOutcome = new AnalyticsFunctionEnvelope<>(
                true,
                "other-engine",
                AnalyticsFunctionContract.VERSION,
                AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION,
                description(),
                new AnalyticsFunctionContext("request-1", "trace-1"),
                null);
        assertEquals(
                "ANALYTICS_CLIENT_PROTOCOL_ERROR",
                client.validateBundle(bundleRequest()).error().code());

        server.stop(0);
        server = null;
        var unavailable = client.validateBundle(bundleRequest());
        assertFalse(unavailable.success());
        assertEquals("ANALYTICS_CLIENT_TRANSPORT_ERROR",
                unavailable.error().code());
        assertTrue(unavailable.error().retryable());
        assertFalse(options.toString().contains("management-secret"));
        assertFalse(options.toString().contains("data-secret"));
    }

    @Test
    void rejectsInjectedHttpClientsThatCouldForwardCredentialsOnRedirect() {
        HttpClient redirecting = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> new HttpAnalyticsFunctionClient(
                        options,
                        redirecting,
                        objectMapper));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyticsHttpClientOptions(
                        URI.create("http://analytics.internal/service"),
                        Duration.ofSeconds(2),
                        "management-secret",
                        null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyticsHttpClientOptions(
                        URI.create("https://analytics.internal/service"),
                        Duration.ofSeconds(2),
                        "bad\nsecret",
                        null));
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] requestBytes = exchange.getRequestBody().readAllBytes();
        Map<String, Object> body = requestBytes.length == 0
                ? Map.of()
                : objectMapper.readValue(
                        requestBytes,
                        new TypeReference<Map<String, Object>>() {
                        });
        CapturedRequest captured = new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath(),
                exchange.getRequestHeaders().getFirst(
                        AnalyticsFunctionHttpHeaders.RUNTIME_CODE),
                exchange.getRequestHeaders().getFirst(
                        AnalyticsFunctionHttpHeaders.AUTHORIZATION),
                body);
        requests.add(captured);
        AnalyticsFunctionEnvelope<?> outcome = forcedOutcome == null
                ? routeOutcome(captured)
                : forcedOutcome;
        byte[] response = objectMapper.writeValueAsBytes(outcome);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(forcedStatus, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static AnalyticsFunctionEnvelope<?> routeOutcome(
            CapturedRequest request) {
        Object data;
        if (request.path().endsWith("/capabilities")) {
            data = capabilities();
        } else if ("GET".equals(request.method())
                && request.path().endsWith("/bundles")) {
            data = new AnalyticsBundleList(List.of(description()));
        } else if (request.path().contains("/artifacts/")) {
            data = artifactDescription();
        } else if (request.path().endsWith("/model-dependencies/resolve")) {
            data = modelDependencyDescription();
        } else if (request.path().contains("/semantic-models/")
                && request.path().endsWith("/describe")) {
            data = semanticModelDescription();
        } else if (request.path().contains("/semantic-models/")
                && request.path().endsWith("/query")) {
            data = semanticQueryResult();
        } else if (request.path().endsWith("/validate")
                || request.path().endsWith("/describe")) {
            data = description();
        } else {
            data = renderResult();
        }
        return AnalyticsFunctionEnvelope.ok(
                AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION,
                data,
                new AnalyticsFunctionContext("request-1", "trace-1"));
    }

    private static AnalyticsBundleFunctionRequest bundleRequest() {
        return new AnalyticsBundleFunctionRequest("sales", REVISION, CONTEXT);
    }

    private static AnalyticsArtifactFunctionRequest artifactRequest() {
        return new AnalyticsArtifactFunctionRequest(
                "sales", "report", "sales-summary", REVISION, CONTEXT);
    }

    private static AnalyticsModelDependencyResolutionRequest modelDependencyRequest() {
        return new AnalyticsModelDependencyResolutionRequest(
                "tms-ai", "qm", "TenantOrgManagementQuery", CONTEXT);
    }

    private static AnalyticsSemanticModelFunctionRequest semanticModelRequest() {
        return new AnalyticsSemanticModelFunctionRequest(
                "default",
                "FactOrderQueryModel",
                REVISION,
                new AnalyticsFunctionAuthority("tms", "subject:42"),
                CONTEXT);
    }

    private static AnalyticsSemanticQueryFunctionRequest semanticQueryRequest() {
        return new AnalyticsSemanticQueryFunctionRequest(
                "default",
                "FactOrderQueryModel",
                REVISION,
                new AnalyticsSemanticQuery(
                        List.of("orderCount"),
                        List.of(),
                        List.of(),
                        List.of(),
                        0,
                        100,
                        true,
                        false),
                new AnalyticsFunctionAuthority("tms", "subject:42"),
                CONTEXT);
    }

    private static AnalyticsRenderFunctionRequest renderRequest() {
        Map<String, Object> parameters = new java.util.LinkedHashMap<>();
        parameters.put("region", "east");
        parameters.put("limit", 2);
        parameters.put("optional", null);
        return new AnalyticsRenderFunctionRequest(
                "sales",
                "sales-summary",
                REVISION,
                parameters,
                "Asia/Shanghai",
                "zh-CN",
                new AnalyticsFunctionAuthority("tms", "subject:42"),
                CONTEXT);
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

    private static AnalyticsArtifactDescription artifactDescription() {
        return new AnalyticsArtifactDescription(
                "sales", REVISION, "report", "sales-summary");
    }

    private static AnalyticsModelDependencyDescription modelDependencyDescription() {
        return new AnalyticsModelDependencyDescription(
                "tms-ai", "qm", "TenantOrgManagementQuery", REVISION);
    }

    private static AnalyticsSemanticModelDescription semanticModelDescription() {
        return new AnalyticsSemanticModelDescription(
                "default", "FactOrderQueryModel", REVISION, "markdown", "# Orders");
    }

    private static AnalyticsSemanticQueryResult semanticQueryResult() {
        return new AnalyticsSemanticQueryResult(
                "default",
                "FactOrderQueryModel",
                REVISION,
                List.of(new AnalyticsSemanticQueryResult.Column(
                        "orderCount", "LONG", "订单数")),
                List.of(Map.of("orderCount", 12)),
                1L,
                false,
                false,
                List.of());
    }

    private static AnalyticsFunctionCapabilities capabilities() {
        return new AnalyticsFunctionCapabilities(
                "analytics",
                AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION,
                true,
                "host-managed",
                Map.of(AnalyticsFunctionOperations.CAPABILITIES, "supported"),
                new AnalyticsFunctionCapabilities.Limits(1_000, 1),
                List.of());
    }

    private static AnalyticsRenderResult renderResult() {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("region", "east");
        row.put("amount", new BigDecimal("12.50"));
        row.put("count", BigInteger.valueOf(7));
        row.put("optional", null);
        row.put("details", List.of(Map.of("active", true)));
        return new AnalyticsRenderResult(
                new AnalyticsRenderResult.Artifact("report", "sales-summary"),
                REVISION,
                "ready",
                List.of(new AnalyticsRenderResult.Widget(
                        "sales-summary",
                        new AnalyticsRenderResult.Visual("table", Map.of()),
                        "ready",
                        List.of(new AnalyticsRenderResult.Column(
                                "region", "string", true)),
                        List.of(row),
                        false,
                        List.of())),
                List.of());
    }

    private record CapturedRequest(
            String method,
            String path,
            String authCode,
            String authorization,
            Map<String, Object> body) {
    }

    private abstract static class EndpointStub implements AnalyticsFunctionEndpoint {

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsFunctionCapabilities> capabilities(
                AnalyticsFunctionRequestContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsBundleList> listBundles(
                AnalyticsFunctionRequestContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsBundleDescription> validateBundle(
                AnalyticsBundleFunctionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsBundleDescription> describeBundle(
                AnalyticsBundleFunctionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
                AnalyticsRenderFunctionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewDashboard(
                AnalyticsRenderFunctionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsRenderResult> renderDashboard(
                AnalyticsRenderFunctionRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
