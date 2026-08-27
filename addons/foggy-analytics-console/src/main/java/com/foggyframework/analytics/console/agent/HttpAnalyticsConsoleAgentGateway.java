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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        addWorkspaceFilesPolicy(body, binding);
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
        addWorkspaceFilesPolicy(body, binding);
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

    /**
     * Workspace Files is an explicit caller opt-in. FAP remains the authority that validates
     * the policy against the selected Worker capability and permission scope.
     */
    private static void addWorkspaceFilesPolicy(
            ObjectNode body,
            AnalyticsConsoleFapBindingResolver.OutboundBinding binding) {
        if (!binding.workspaceFilesEnabled()) return;
        body.putObject("workspaceFiles")
                .put("protocol", "WORKSPACE_FILES_V1")
                .put("isolationMode", "WORKSPACE_GUARDED");
    }

    @Override
    public List<Turn> turns(
            AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
            String requestId,
            String externalConversationRef) {
        return readTurns(binding, requestId, externalConversationRef, 50);
    }

    @Override
    public String firstUserMessage(
            AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
            String requestId,
            String externalConversationRef) {
        return readTurns(binding, requestId, externalConversationRef, 1).stream()
                .filter(turn -> "START".equals(turn.operation()))
                .map(Turn::userMessage)
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse(null);
    }

    private List<Turn> readTurns(
            AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
            String requestId,
            String externalConversationRef,
            int limit) {
        String path = ROOT + "/conversations/" + segment(externalConversationRef)
                + "/turns?requestId=" + segment(requestId) + "&limit=" + limit;
        JsonNode response = exchange("GET", path, binding.authorization(), null, 200);
        requireType(response, "ASK_CONVERSATION_TURN_PAGE");
        JsonNode values = response.path("turns");
        if (!values.isArray() || values.size() > limit) {
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
            Instant askCreatedAt = timestamp(value, "createdAt");
            Instant taskUpdatedAt = timestamp(value, "updatedAt");
            if (taskUpdatedAt.isBefore(askCreatedAt)) {
                throw protocol("FAP conversation turn timestamps are inconsistent", null);
            }
            ExecutionTiming timing = executionTiming(value);
            turns.add(new Turn(
                    required(value, "askInvocationRef"),
                    required(value, "operation"),
                    required(value, "displayState"),
                    value.path("definitiveTerminal").asBoolean(false),
                    userMessage,
                    message,
                    optional(value, "failureCode"),
                    timing == null ? null : timing.startedAt(),
                    timing == null ? null : timing.completedAt(),
                    timing == null ? null : timing.durationMs()));
        }
        return List.copyOf(turns);
    }

    private static ExecutionTiming executionTiming(JsonNode turn) {
        JsonNode value = turn.get("executionTiming");
        if (value == null || value.isNull()) return null;
        if (!value.isObject()) {
            throw protocol("FAP executionTiming is invalid", null);
        }
        Instant startedAt = timestamp(value, "startedAt");
        Instant completedAt = timestamp(value, "completedAt");
        long durationMs = nonNegativeLong(value, "durationMs");
        if (completedAt.isBefore(startedAt)
                || Duration.between(startedAt, completedAt).toMillis() != durationMs) {
            throw protocol("FAP executionTiming is inconsistent", null);
        }
        return new ExecutionTiming(startedAt, completedAt, durationMs);
    }

    @Override
    public TurnDetail turnDetail(
            AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
            String askRequestId,
            String expectedAskInvocationRef,
            String expectedExternalConversationRef) {
        JsonNode response = exchange(
                "GET",
                ROOT + "/asks/requests/" + segment(askRequestId) + "/trace",
                binding.authorization(),
                null,
                200);
        requireType(response, "ASK_TRACE");
        if (!expectedAskInvocationRef.equals(required(response, "askInvocationRef"))
                || !expectedExternalConversationRef.equals(
                        required(response, "externalConversationRef"))) {
            throw protocol("FAP Ask trace changed the frozen conversation binding", null);
        }
        JsonNode history = response.path("eventHistory");
        JsonNode events = response.path("events");
        if (!history.isObject() || !events.isArray() || events.size() > 256) {
            throw protocol("FAP Ask trace is invalid", null);
        }

        List<AgentActivity> activities = new ArrayList<>();
        Map<String, MutableToolCall> tools = new LinkedHashMap<>();
        for (JsonNode event : events) {
            long sequence = positiveLong(event, "eventSeq");
            String eventType = required(event, "eventType");
            Instant occurredAt = timestamp(event, "occurredAt");
            AgentActivity activity = activity(sequence, eventType, occurredAt, event);
            if (activity != null) activities.add(activity);
            if ("langbiz.function.started".equals(eventType)
                    || "langbiz.function.completed".equals(eventType)
                    || "langbiz.function.rejected".equals(eventType)) {
                collectToolCall(tools, sequence, eventType, occurredAt, event);
            }
        }
        String historyState = required(history, "state");
        if (!List.of("PENDING", "COMPLETE", "PARTIAL", "UNAVAILABLE")
                .contains(historyState)
                || !history.path("eventsTruncated").isBoolean()) {
            throw protocol("FAP Ask trace history is invalid", null);
        }
        return new TurnDetail(
                expectedAskInvocationRef,
                historyState,
                history.path("eventsTruncated").booleanValue(),
                activities,
                tools.values().stream().map(MutableToolCall::freeze).toList());
    }

    private static AgentActivity activity(
            long sequence,
            String eventType,
            Instant occurredAt,
            JsonNode event) {
        return switch (eventType) {
            case "worker.operation.input.accepted" -> new AgentActivity(
                    sequence, "已接收本轮问题", "SUCCEEDED", occurredAt, null);
            case "provider.operation.started" -> new AgentActivity(
                    sequence, "Agent 开始分析", "RUNNING", occurredAt, null);
            case "provider.operation.terminal" -> new AgentActivity(
                    sequence, "Agent 完成本轮分析", "SUCCEEDED", occurredAt, null);
            case "codex.turn.failed" -> new AgentActivity(
                    sequence, "Agent 分析失败", "FAILED", occurredAt, errorCode(event));
            case "codex.stream.diagnostic" -> new AgentActivity(
                    sequence, "Agent 返回诊断信息", "FAILED", occurredAt, errorCode(event));
            default -> null;
        };
    }

    private static void collectToolCall(
            Map<String, MutableToolCall> tools,
            long sequence,
            String eventType,
            Instant occurredAt,
            JsonNode event) {
        if ("langbiz.function.schema-delivered".equals(eventType)) return;
        String invocationId = required(event, "functionInvocationId");
        String functionRef = required(event, "functionRef");
        MutableToolCall tool = tools.computeIfAbsent(
                invocationId,
                ignored -> new MutableToolCall(sequence, invocationId, functionRef));
        if (!tool.functionRef.equals(functionRef)) {
            throw protocol("FAP trace changed a Function invocation binding", null);
        }
        switch (eventType) {
            case "langbiz.function.started" -> tool.startedAt = occurredAt;
            case "langbiz.function.completed" -> {
                tool.completedAt = occurredAt;
                tool.state = "SUCCEEDED";
            }
            case "langbiz.function.rejected" -> {
                tool.completedAt = occurredAt;
                tool.state = "FAILED";
                tool.errorCode = errorCode(event);
            }
            default -> {
                // The explicit Service Provider allowlist controls future safe event types.
            }
        }
    }

    private static String errorCode(JsonNode event) {
        return optional(event.path("error"), "code");
    }

    private static Instant timestamp(JsonNode node, String field) {
        try {
            return Instant.parse(required(node, field));
        } catch (java.time.format.DateTimeParseException invalid) {
            throw protocol("FAP response timestamp is invalid", invalid);
        }
    }

    private static long positiveLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong() || value.longValue() < 1) {
            throw protocol("FAP response sequence is invalid", null);
        }
        return value.longValue();
    }

    private static long nonNegativeLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()
                || !value.canConvertToLong() || value.longValue() < 0) {
            throw protocol("FAP response duration is invalid", null);
        }
        return value.longValue();
    }

    private record ExecutionTiming(
            Instant startedAt,
            Instant completedAt,
            long durationMs) {
    }

    private static final class MutableToolCall {
        private final long sequence;
        private final String functionInvocationId;
        private final String functionRef;
        private String state = "RUNNING";
        private Instant startedAt;
        private Instant completedAt;
        private String errorCode;

        private MutableToolCall(
                long sequence,
                String functionInvocationId,
                String functionRef) {
            this.sequence = sequence;
            this.functionInvocationId = functionInvocationId;
            this.functionRef = functionRef;
        }

        private ToolCall freeze() {
            if (startedAt != null && completedAt != null && completedAt.isBefore(startedAt)) {
                throw protocol("FAP Function trace timestamps are inconsistent", null);
            }
            Long durationMs = startedAt == null || completedAt == null
                    ? null : Duration.between(startedAt, completedAt).toMillis();
            return new ToolCall(
                    sequence,
                    functionInvocationId,
                    functionRef,
                    state,
                    startedAt,
                    completedAt,
                    durationMs,
                    errorCode);
        }
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
