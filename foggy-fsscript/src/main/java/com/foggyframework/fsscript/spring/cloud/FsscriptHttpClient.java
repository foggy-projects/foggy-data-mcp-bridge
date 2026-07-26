package com.foggyframework.fsscript.spring.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.core.ex.RX;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Platform-maintained HTTP client for trusted TM/QM author scripts.
 *
 * <p>The client deliberately owns redirect handling so credentials are never
 * forwarded across origins. It does not log request headers, bodies, response
 * bodies, URLs, or query parameters.</p>
 */
public class FsscriptHttpClient {

    private static final Set<String> FORBIDDEN_HEADERS = Set.of(
            "connection", "content-length", "host", "keep-alive",
            "proxy-authenticate", "proxy-authorization", "te", "trailer",
            "transfer-encoding", "upgrade"
    );
    private static final Set<String> CROSS_ORIGIN_SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "proxy-authorization"
    );
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final int maxResponseBytes;
    private final int maxRedirects;

    public FsscriptHttpClient(
            ObjectMapper objectMapper,
            Duration connectTimeout,
            Duration requestTimeout,
            int maxResponseBytes,
            int maxRedirects
    ) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(requirePositive(connectTimeout, "connectTimeout"))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                objectMapper,
                requestTimeout,
                maxResponseBytes,
                maxRedirects
        );
    }

    FsscriptHttpClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Duration requestTimeout,
            int maxResponseBytes,
            int maxRedirects
    ) {
        this.httpClient = java.util.Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper");
        this.requestTimeout = requirePositive(requestTimeout, "requestTimeout");
        if (maxResponseBytes < 1) {
            throw new IllegalArgumentException("maxResponseBytes must be positive");
        }
        if (maxRedirects < 0) {
            throw new IllegalArgumentException("maxRedirects must not be negative");
        }
        this.maxResponseBytes = maxResponseBytes;
        this.maxRedirects = maxRedirects;
    }

    public Object execute(Map<String, Object> config, HttpMethod method) {
        if (config == null) {
            throw RX.throwB("HTTP 请求配置不能为空");
        }
        if (method != HttpMethod.GET && method != HttpMethod.POST) {
            throw RX.throwB("当前不支持 httpMethod：" + method);
        }
        URI uri = buildUri(config);
        Map<String, List<String>> headers = parseHeaders(config.get("headers"));
        Object body = config.containsKey("body") ? config.get("body") : config.get("data");
        Class<?> responseClass = resolveResponseClass(config);
        try {
            byte[] response = exchange(uri, method, headers, body, 0);
            return decodeResponse(response, responseClass);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw RX.throwB("HTTP 请求被中断", ex);
        } catch (IOException ex) {
            throw RX.throwB("HTTP 请求失败", ex);
        }
    }

    private byte[] exchange(
            URI uri,
            HttpMethod method,
            Map<String, List<String>> headers,
            Object body,
            int redirects
    ) throws IOException, InterruptedException {
        HttpRequest request = buildRequest(uri, method, headers, body);
        HttpResponse<InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (REDIRECT_STATUSES.contains(status)) {
            closeQuietly(response.body());
            if (redirects >= maxRedirects) {
                throw RX.throwB("HTTP 重定向次数超过限制");
            }
            String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> RX.throwB("HTTP 重定向缺少 Location"));
            URI target = uri.resolve(location);
            validateUri(target);
            Map<String, List<String>> redirectedHeaders = headers;
            if (!sameOrigin(uri, target)) {
                redirectedHeaders = withoutCrossOriginSensitiveHeaders(headers);
            }
            HttpMethod redirectedMethod = status == 303 ? HttpMethod.GET : method;
            Object redirectedBody = redirectedMethod == HttpMethod.GET ? null : body;
            return exchange(target, redirectedMethod, redirectedHeaders, redirectedBody, redirects + 1);
        }
        try (InputStream input = response.body()) {
            byte[] bytes = readLimited(input);
            if (status < 200 || status >= 300) {
                throw RX.throwB("HTTP 上游返回状态码：" + status);
            }
            return bytes;
        }
    }

    private HttpRequest buildRequest(
            URI uri,
            HttpMethod method,
            Map<String, List<String>> headers,
            Object body
    ) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(requestTimeout);
        boolean hasContentType = headers.keySet().stream()
                .anyMatch(name -> "content-type".equalsIgnoreCase(name));
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String value : entry.getValue()) {
                builder.header(entry.getKey(), value);
            }
        }
        if (method == HttpMethod.POST) {
            byte[] payload = encodeBody(body);
            if (payload.length > 0 && !hasContentType) {
                builder.header("Content-Type", "application/json");
            }
            builder.POST(HttpRequest.BodyPublishers.ofByteArray(payload));
        } else {
            builder.GET();
        }
        return builder.build();
    }

    private URI buildUri(Map<String, Object> config) {
        String value = asNonBlankString(config.get("url"));
        if (value == null) {
            String service = asNonBlankString(config.get("service"));
            String apiPath = asNonBlankString(config.get("apiPath"));
            if (service == null) {
                throw RX.throwB("HTTP 请求缺少 url 或 service");
            }
            value = "http://" + service + (apiPath == null ? "" : apiPath);
        }
        URI base;
        try {
            base = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw RX.throwB("HTTP 请求 URL 无效", ex);
        }
        validateUri(base);
        Object queryValue = config.containsKey("query") ? config.get("query") : config.get("params");
        if (!(queryValue instanceof Map<?, ?> query) || query.isEmpty()) {
            return base;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(base);
        for (Map.Entry<?, ?> entry : query.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            Object item = entry.getValue();
            if (item instanceof Collection<?> values) {
                for (Object valueItem : values) {
                    if (valueItem != null) {
                        builder.queryParam(key, valueItem);
                    }
                }
            } else {
                builder.queryParam(key, item);
            }
        }
        return builder.build().encode().toUri();
    }

    private void validateUri(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || uri.getHost() == null) {
            throw RX.throwB("HTTP 请求只支持有效的 http/https URL");
        }
        if (uri.getUserInfo() != null) {
            throw RX.throwB("HTTP 请求 URL 不允许包含 user-info");
        }
    }

    private Map<String, List<String>> parseHeaders(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> source)) {
            throw RX.throwB("HTTP headers 必须是对象");
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String name = String.valueOf(entry.getKey()).trim();
            String canonical = name.toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(name) || FORBIDDEN_HEADERS.contains(canonical)) {
                throw RX.throwB("HTTP Header 不允许由模型设置：" + name);
            }
            Object rawValue = entry.getValue();
            if (rawValue instanceof Collection<?> values) {
                List<String> converted = values.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(String::valueOf)
                        .toList();
                if (!converted.isEmpty()) {
                    headers.put(name, converted);
                }
            } else {
                headers.put(name, List.of(String.valueOf(rawValue)));
            }
        }
        return Map.copyOf(headers);
    }

    private Map<String, List<String>> withoutCrossOriginSensitiveHeaders(
            Map<String, List<String>> source
    ) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        source.forEach((name, values) -> {
            if (!CROSS_ORIGIN_SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                result.put(name, values);
            }
        });
        return Map.copyOf(result);
    }

    private boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private byte[] encodeBody(Object body) throws IOException {
        if (body == null) {
            return new byte[0];
        }
        if (body instanceof byte[] bytes) {
            return bytes;
        }
        if (body instanceof String text) {
            return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return objectMapper.writeValueAsBytes(body);
    }

    private byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                Math.min(maxResponseBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxResponseBytes) {
                throw RX.throwB("HTTP 响应超过大小限制");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private Object decodeResponse(byte[] response, Class<?> responseClass) throws IOException {
        if (response.length == 0) {
            return null;
        }
        if (responseClass == byte[].class) {
            return response;
        }
        if (responseClass == String.class) {
            return new String(response, java.nio.charset.StandardCharsets.UTF_8);
        }
        return objectMapper.readValue(response, responseClass);
    }

    private Class<?> resolveResponseClass(Map<String, Object> config) {
        Object responseType = config.get("responseType");
        if (responseType != null) {
            String type = String.valueOf(responseType).trim().toLowerCase(Locale.ROOT);
            return switch (type) {
                case "map" -> Map.class;
                case "list" -> List.class;
                case "string", "text" -> String.class;
                case "bytes", "byte[]" -> byte[].class;
                case "object", "json" -> Object.class;
                default -> resolveClass(responseType);
            };
        }
        return resolveClass(config.get("returnClass"));
    }

    private Class<?> resolveClass(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return Object.class;
        }
        if (value instanceof Class<?> cls) {
            return cls;
        }
        if (value instanceof String className) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ex) {
                throw RX.throwB("不支持的响应类型", ex);
            }
        }
        throw RX.throwB("不支持的响应类型");
    }

    private String asNonBlankString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private void closeQuietly(InputStream input) {
        if (input == null) {
            return;
        }
        try {
            input.close();
        } catch (IOException ignored) {
            // Redirect cleanup only; no sensitive material is logged.
        }
    }
}
