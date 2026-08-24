package com.foggyframework.analytics.console.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Narrow FAP Service Provider client; mutations are sent exactly once. */
public final class HttpAnalyticsConsoleAgentGateway
        implements AnalyticsConsoleAgentGateway {

    private static final String CONTRACT_VERSION = "fap.service-provider.v1alpha1";
    private static final String ROOT = "/api/v1/service-provider";
    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;

    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper json;
    private final HttpClient http;

    public HttpAnalyticsConsoleAgentGateway(
            URI baseUri,
            Duration timeout,
            ObjectMapper json) {
        this(baseUri, timeout, json,
                HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    HttpAnalyticsConsoleAgentGateway(
            URI baseUri,
            Duration timeout,
            ObjectMapper json,
            HttpClient http) {
        this.baseUri = baseUri;
        this.timeout = timeout;
        this.json = json.copy().findAndRegisterModules();
        this.http = http;
    }

    @Override
    public Accepted start(
            AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
            StartCommand command) {
        ObjectNode body = request("CREATE_ASK", command.requestId());
        body.put("operation", "START");
        body.put("externalConversationRef", command.externalConversationRef());
        body.put("prompt", command.prompt());
        body.put("initialSystemInstruction", command.initialSystemInstruction());
        body.put("workspaceRef", command.workspaceRef());
        body.put("modelConfigRef", command.modelConfigRef());
        body.put("modelVariantId", command.modelVariantId());
        body.putArray("skills").addObject()
                .put("mode", "NAME").put("name", command.skillName());
        body.putArray("capabilities").addObject()
                .put("mode", "NAME").put("name", command.capabilityName());
        JsonNode response = exchange(
                "POST", ROOT + "/asks", binding.authorization(), body, 202);
        requireType(response, "ASK_ACCEPTED");
        return new Accepted(
                required(response, "askInvocationRef"),
                required(response, "runtimeExecutionId"),
                required(response, "runtimeTaskId"));
    }

    @Override
    public Accepted continueConversation(
            AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
            ContinueCommand command) {
        ObjectNode body = request("CREATE_ASK", command.requestId());
        body.put("operation", "CONTINUE");
        body.put("externalConversationRef", command.externalConversationRef());
        body.put("runtimeExecutionId", command.runtimeExecutionId());
        body.put("prompt", command.prompt());
        body.put("modelVariantId", command.modelVariantId());
        body.putArray("skills").addObject()
                .put("mode", "NAME").put("name", command.skillName());
        body.putArray("capabilities").addObject()
                .put("mode", "NAME").put("name", command.capabilityName());
        JsonNode response = exchange(
                "POST", ROOT + "/asks", binding.authorization(), body, 202);
        requireType(response, "ASK_ACCEPTED");
        String executionId = required(response, "runtimeExecutionId");
        if (!command.runtimeExecutionId().equals(executionId)) {
            throw protocol("FAP CONTINUE changed the frozen Runtime execution", null);
        }
        return new Accepted(
                required(response, "askInvocationRef"),
                executionId,
                required(response, "runtimeTaskId"));
    }

    @Override
    public List<Turn> turns(
            AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
            String requestId,
            String externalConversationRef) {
        String path = ROOT + "/conversations/" + segment(externalConversationRef)
                + "/turns?requestId=" + segment(requestId) + "&limit=50";
        JsonNode response = exchange("GET", path, binding.authorization(), null, 200);
        requireType(response, "ASK_CONVERSATION_TURN_PAGE");
        JsonNode values = response.path("turns");
        if (!values.isArray() || values.size() > 50) {
            throw protocol("FAP conversation turns are invalid", null);
        }
        List<Turn> turns = new ArrayList<>();
        for (JsonNode value : values) {
            JsonNode user = value.path("userMessage");
            String userMessage = "AVAILABLE".equals(user.path("contentState").asText())
                    ? user.path("text").asText(null)
                    : null;
            JsonNode assistant = value.path("assistantMessage");
            String message = "AVAILABLE".equals(
                    assistant.path("contentState").asText())
                    ? assistant.path("text").asText(null)
                    : null;
            turns.add(new Turn(
                    required(value, "askInvocationRef"),
                    required(value, "operation"),
                    required(value, "displayState"),
                    value.path("definitiveTerminal").asBoolean(false),
                    userMessage,
                    message,
                    optional(value, "failureCode")));
        }
        return List.copyOf(turns);
    }

    private JsonNode exchange(
            String method,
            String path,
            String authorization,
            JsonNode body,
            int expectedStatus) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(timeout)
                    .header("Authorization", authorization)
                    .header("Accept", "application/json");
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(
                                json.writeValueAsBytes(body)));
            }
            HttpResponse<InputStream> response = http.send(
                    request.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] responseBytes;
            try (InputStream stream = response.body()) {
                responseBytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
            }
            if (responseBytes.length > MAX_RESPONSE_BYTES) {
                throw protocol("FAP response exceeds the safe size limit", null);
            }
            JsonNode value = responseBytes.length == 0
                    ? json.createObjectNode()
                    : json.readTree(responseBytes);
            if (response.statusCode() != expectedStatus) {
                throw remote(response.statusCode(), value);
            }
            if (!value.isObject()) {
                throw protocol("FAP response is not a JSON object", null);
            }
            return value;
        } catch (AnalyticsConsoleCatalogException known) {
            throw known;
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw protocol("FAP Service Provider is unavailable", failure);
        }
    }

    private ObjectNode request(String type, String requestId) {
        ObjectNode body = json.createObjectNode();
        body.put("type", type);
        body.putObject("meta")
                .put("contractVersion", CONTRACT_VERSION)
                .put("requestId", requestId);
        return body;
    }

    private static void requireType(JsonNode response, String type) {
        if (!type.equals(response.path("type").asText())) {
            throw protocol("FAP response type is invalid", null);
        }
    }

    private static String required(JsonNode node, String field) {
        String value = optional(node, field);
        if (value == null) {
            throw protocol("FAP response field is missing", null);
        }
        return value;
    }

    private static String optional(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            return null;
        }
        return value.textValue();
    }

    private static String segment(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static AnalyticsConsoleCatalogException remote(int status, JsonNode body) {
        String code = body.path("error").path("code").asText("FAP_REMOTE_ERROR");
        return new AnalyticsConsoleCatalogException(
                "ANALYTICS_CONSOLE_" + safeCode(code),
                status == 401 || status == 403
                        ? "FAP authorization was rejected"
                        : "FAP Service Provider rejected the request");
    }

    private static String safeCode(String code) {
        String value = code.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase();
        return value.isBlank() ? "FAP_REMOTE_ERROR" : value;
    }

    private static AnalyticsConsoleCatalogException protocol(
            String message, Throwable cause) {
        return cause == null
                ? new AnalyticsConsoleCatalogException(
                        "ANALYTICS_CONSOLE_FAP_UNAVAILABLE", message)
                : new AnalyticsConsoleCatalogException(
                        "ANALYTICS_CONSOLE_FAP_UNAVAILABLE", message, cause);
    }
}
