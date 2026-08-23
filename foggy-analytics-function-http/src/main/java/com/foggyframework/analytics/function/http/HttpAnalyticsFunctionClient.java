package com.foggyframework.analytics.function.http;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.function.contract.AnalyticsBundleDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleList;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionCapabilities;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContract;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionError;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionErrorCodes;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsRenderFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import com.foggyframework.analytics.function.sdk.AnalyticsFunctionClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** JDK HTTP client for the exact Analytics Function v1 contract. */
public final class HttpAnalyticsFunctionClient implements AnalyticsFunctionClient {

    private static final String JSON = "application/json";

    private final AnalyticsHttpClientOptions options;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiRoot;

    public HttpAnalyticsFunctionClient(AnalyticsHttpClientOptions options) {
        this(
                options,
                HttpClient.newBuilder()
                        .connectTimeout(options.requestTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                new ObjectMapper());
    }

    public HttpAnalyticsFunctionClient(
            AnalyticsHttpClientOptions options,
            HttpClient httpClient,
            ObjectMapper objectMapper) {
        this.options = Objects.requireNonNull(options, "options");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException(
                    "httpClient must disable redirects to protect credentials");
        }
        this.objectMapper = canonicalObjectMapper(
                Objects.requireNonNull(objectMapper, "objectMapper"));
        this.apiRoot = apiRoot(options.baseUrl());
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsFunctionCapabilities> capabilities(
            AnalyticsFunctionRequestContext context) {
        return invoke(
                "GET",
                "/capabilities",
                null,
                requireContext(context),
                false,
                AnalyticsFunctionCapabilities.class);
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsBundleList> listBundles(
            AnalyticsFunctionRequestContext context) {
        return invoke(
                "GET",
                "/bundles",
                null,
                requireContext(context),
                false,
                AnalyticsBundleList.class);
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsBundleDescription> validateBundle(
            AnalyticsBundleFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(
                "POST",
                "/bundles/" + pathSegment(request.bundleRef()) + "/validate",
                bundleBody(request),
                request.context(),
                false,
                AnalyticsBundleDescription.class);
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsBundleDescription> describeBundle(
            AnalyticsBundleFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(
                "POST",
                "/bundles/" + pathSegment(request.bundleRef()) + "/describe",
                bundleBody(request),
                request.context(),
                false,
                AnalyticsBundleDescription.class);
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
            AnalyticsRenderFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(
                "POST",
                renderPath(request, "reports", "preview"),
                renderBody(request),
                request.context(),
                true,
                AnalyticsRenderResult.class);
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewDashboard(
            AnalyticsRenderFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(
                "POST",
                renderPath(request, "dashboards", "preview"),
                renderBody(request),
                request.context(),
                true,
                AnalyticsRenderResult.class);
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsRenderResult> renderDashboard(
            AnalyticsRenderFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(
                "POST",
                renderPath(request, "dashboards", "render"),
                renderBody(request),
                request.context(),
                true,
                AnalyticsRenderResult.class);
    }

    private <T> AnalyticsFunctionEnvelope<T> invoke(
            String method,
            String path,
            Object body,
            AnalyticsFunctionRequestContext context,
            boolean dataPlane,
            Class<T> dataType) {
        try {
            HttpRequest request = request(method, path, body, context, dataPlane);
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            AnalyticsFunctionEnvelope<T> envelope = readEnvelope(
                    response.body(), dataType);
            boolean httpSuccess = response.statusCode() >= 200
                    && response.statusCode() < 300;
            if (httpSuccess != envelope.success()) {
                return protocolFailure(context);
            }
            if (!AnalyticsFunctionContract.VERSION.equals(
                    envelope.functionContractVersion())) {
                return protocolFailure(context);
            }
            if (!AnalyticsFunctionContract.ENGINE.equals(envelope.engine())) {
                return protocolFailure(context);
            }
            return envelope;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return transportFailure(context);
        } catch (IOException transportFailure) {
            return transportFailure(context);
        } catch (RuntimeException protocolFailure) {
            return protocolFailure(context);
        }
    }

    private HttpRequest request(
            String method,
            String path,
            Object body,
            AnalyticsFunctionRequestContext context,
            boolean dataPlane) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiRoot + path))
                .timeout(options.requestTimeout())
                .header("Accept", JSON);
        if (context.requestId() != null) {
            builder.header("X-Request-Id", context.requestId());
        }
        if (context.traceId() != null) {
            builder.header("X-Trace-Id", context.traceId());
        }
        if (options.authCode() != null) {
            builder.header(
                    AnalyticsFunctionHttpHeaders.RUNTIME_CODE,
                    options.authCode());
        }
        if (dataPlane && options.authorization() != null) {
            builder.header(
                    AnalyticsFunctionHttpHeaders.AUTHORIZATION,
                    options.authorization());
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", JSON)
                    .method(method, HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body),
                            StandardCharsets.UTF_8));
        }
        return builder.build();
    }

    private <T> AnalyticsFunctionEnvelope<T> readEnvelope(
            String body,
            Class<T> dataType) throws IOException {
        JavaType envelopeType = objectMapper.getTypeFactory()
                .constructParametricType(AnalyticsFunctionEnvelope.class, dataType);
        return objectMapper.readValue(body, envelopeType);
    }

    private <T> AnalyticsFunctionEnvelope<T> transportFailure(
            AnalyticsFunctionRequestContext requested) {
        return failure(
                requested,
                AnalyticsFunctionErrorCodes.CLIENT_TRANSPORT_ERROR,
                "transport",
                "Analytics Runtime transport is unavailable.",
                true);
    }

    private <T> AnalyticsFunctionEnvelope<T> protocolFailure(
            AnalyticsFunctionRequestContext requested) {
        return failure(
                requested,
                AnalyticsFunctionErrorCodes.CLIENT_PROTOCOL_ERROR,
                "transport",
                "Analytics Runtime returned an incompatible response.",
                false);
    }

    private static <T> AnalyticsFunctionEnvelope<T> failure(
            AnalyticsFunctionRequestContext requested,
            String code,
            String phase,
            String message,
            boolean retryable) {
        return AnalyticsFunctionEnvelope.fail(
                AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION,
                new AnalyticsFunctionError(code, phase, message, retryable),
                AnalyticsFunctionContext.normalize(requested));
    }

    private static Map<String, Object> bundleBody(
            AnalyticsBundleFunctionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (request.expectedBundleRevision() != null) {
            body.put("expectedBundleRevision", request.expectedBundleRevision());
        }
        putContext(body, request.context());
        return body;
    }

    private static Map<String, Object> renderBody(
            AnalyticsRenderFunctionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("expectedBundleRevision", request.expectedBundleRevision());
        body.put("parameters", request.parameters());
        body.put("timezone", request.timezone());
        body.put("locale", request.locale());
        body.put("authority", Map.of(
                "provider", request.authority().provider(),
                "reference", request.authority().reference()));
        putContext(body, request.context());
        return body;
    }

    private static void putContext(
            Map<String, Object> body,
            AnalyticsFunctionRequestContext context) {
        if (context.requestId() != null) {
            body.put("requestId", context.requestId());
        }
        if (context.traceId() != null) {
            body.put("traceId", context.traceId());
        }
    }

    private static String renderPath(
            AnalyticsRenderFunctionRequest request,
            String collection,
            String action) {
        return "/bundles/" + pathSegment(request.bundleRef())
                + '/' + collection + '/' + pathSegment(request.artifactRef())
                + '/' + action;
    }

    private static AnalyticsFunctionRequestContext requireContext(
            AnalyticsFunctionRequestContext context) {
        return Objects.requireNonNull(context, "context");
    }

    private static String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String apiRoot(URI baseUrl) {
        String root = baseUrl.toString();
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        return root.endsWith("/api/v1") ? root : root + "/api/v1";
    }

    private static ObjectMapper canonicalObjectMapper(ObjectMapper source) {
        return source.copy()
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }
}
