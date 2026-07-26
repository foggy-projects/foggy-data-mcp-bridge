package com.foggyframework.fsscript.spring.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FsscriptHttpClientTest {

    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void newRequestShapePreservesOpaqueAuthorizationAndOmitsNullHeaders() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> absentHeader = new AtomicReference<>("not-called");
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            absentHeader.set(exchange.getRequestHeaders().getFirst("X-Optional"));
            query.set(exchange.getRequestURI().getRawQuery());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"allow\":true}");
        });

        Object result = client(1024, Duration.ofSeconds(2)).execute(Map.of(
                "url", url(server, "/resolve"),
                "headers", mapWithNullHeader("Custom opaque value"),
                "query", Map.of("scope", List.of("north", "east")),
                "body", Map.of("action", "EXECUTE"),
                "responseType", "map"
        ), HttpMethod.POST);

        assertEquals("Custom opaque value", authorization.get());
        assertNull(absentHeader.get());
        assertEquals("scope=north&scope=east", query.get());
        assertEquals("{\"action\":\"EXECUTE\"}", body.get());
        assertEquals(Boolean.TRUE, assertInstanceOf(Map.class, result).get("allow"));
    }

    @Test
    void legacyServiceApiPathParamsDataAndReturnClassRemainSupported() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "{\"value\":7}");
        });

        Object result = client(1024, Duration.ofSeconds(2)).execute(Map.of(
                "service", "127.0.0.1:" + server.getAddress().getPort(),
                "apiPath", "/legacy",
                "params", Map.of("page", 2),
                "returnClass", Map.class
        ), HttpMethod.GET);

        assertEquals("page=2", query.get());
        assertEquals(7, assertInstanceOf(Map.class, result).get("value"));
    }

    @Test
    void crossOriginRedirectStripsAuthorizationAndCookieButKeepsOrdinaryHeaders() throws Exception {
        AtomicReference<String> redirectedAuthorization = new AtomicReference<>("not-called");
        AtomicReference<String> redirectedCookie = new AtomicReference<>("not-called");
        AtomicReference<String> redirectedTrace = new AtomicReference<>();
        HttpServer target = server(exchange -> {
            redirectedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            redirectedCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            redirectedTrace.set(exchange.getRequestHeaders().getFirst("X-Trace"));
            respond(exchange, 200, "{\"ok\":true}");
        });
        HttpServer source = server(exchange -> {
            exchange.getResponseHeaders().add(
                    "Location", "http://localhost:" + target.getAddress().getPort() + "/target");
            respond(exchange, 307, "");
        });

        Object result = client(1024, Duration.ofSeconds(2)).execute(Map.of(
                "url", url(source, "/redirect"),
                "headers", Map.of(
                        "Authorization", "opaque-secret",
                        "Cookie", "session=secret",
                        "X-Trace", "trace-1"
                ),
                "responseType", "map"
        ), HttpMethod.GET);

        assertNull(redirectedAuthorization.get());
        assertNull(redirectedCookie.get());
        assertEquals("trace-1", redirectedTrace.get());
        assertEquals(Boolean.TRUE, assertInstanceOf(Map.class, result).get("ok"));
    }

    @Test
    void responseSizeAndRequestTimeoutAreBounded() throws Exception {
        HttpServer oversized = server(exchange ->
                respond(exchange, 200, "x".repeat(65)));
        RuntimeException sizeFailure = assertThrows(RuntimeException.class, () ->
                client(64, Duration.ofSeconds(2)).execute(Map.of(
                        "url", url(oversized, "/large"),
                        "responseType", "string"
                ), HttpMethod.GET));
        assertFalse(String.valueOf(sizeFailure.getMessage()).contains("x".repeat(32)));

        HttpServer slow = server(exchange -> {
            try {
                Thread.sleep(250);
                respond(exchange, 200, "{\"late\":true}");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        assertThrows(RuntimeException.class, () ->
                client(1024, Duration.ofMillis(40)).execute(Map.of(
                        "url", url(slow, "/slow"),
                        "responseType", "map"
                ), HttpMethod.GET));
    }

    @Test
    void rejectsHopByHopHeadersAndNonHttpSchemes() {
        assertThrows(RuntimeException.class, () ->
                client(1024, Duration.ofSeconds(2)).execute(Map.of(
                        "url", "https://permission.example.internal/v1/resolve",
                        "headers", Map.of("Host", "attacker.invalid")
                ), HttpMethod.GET));
        assertThrows(RuntimeException.class, () ->
                client(1024, Duration.ofSeconds(2)).execute(Map.of(
                        "url", "file:///tmp/secret"
                ), HttpMethod.GET));
    }

    private FsscriptHttpClient client(int maxResponseBytes, Duration requestTimeout) {
        return new FsscriptHttpClient(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(1))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                new ObjectMapper(),
                requestTimeout,
                maxResponseBytes,
                3
        );
    }

    private HttpServer server(ThrowingHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        servers.add(server);
        return server;
    }

    private String url(HttpServer server, String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private Map<String, Object> mapWithNullHeader(String authorization) {
        java.util.LinkedHashMap<String, Object> headers = new java.util.LinkedHashMap<>();
        headers.put("Authorization", authorization);
        headers.put("X-Optional", null);
        return headers;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
