package com.foggyframework.analytics.console.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpAnalyticsConsoleAgentGatewayTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void mapsStartContinueAndUserAssistantTurnsWithoutRebindingTheExecution()
            throws Exception {
        ObjectMapper json = new ObjectMapper();
        HttpClient http = mock(HttpClient.class);
        HttpResponse startResponse = response(202, """
                {
                  "type": "ASK_ACCEPTED",
                  "askInvocationRef": "ask-1",
                  "runtimeExecutionId": "execution-1",
                  "runtimeTaskId": "task-1"
                }
                """);
        HttpResponse continueResponse = response(202, """
                {
                  "type": "ASK_ACCEPTED",
                  "askInvocationRef": "ask-2",
                  "runtimeExecutionId": "execution-1",
                  "runtimeTaskId": "task-2"
                }
                """);
        HttpResponse turnsResponse = response(200, """
                {
                  "type": "ASK_CONVERSATION_TURN_PAGE",
                  "turns": [{
                    "askInvocationRef": "ask-2",
                    "operation": "CONTINUE",
                    "displayState": "COMPLETED",
                    "definitiveTerminal": true,
                    "createdAt": "2026-08-24T07:59:30Z",
                    "updatedAt": "2026-08-24T11:01:00Z",
                    "executionTiming": {
                      "startedAt": "2026-08-24T08:00:00Z",
                      "completedAt": "2026-08-24T08:00:06Z",
                      "durationMs": 6000
                    },
                    "userMessage": {
                      "contentState": "AVAILABLE",
                      "text": "按销售团队拆分"
                    },
                    "assistantMessage": {
                      "contentState": "AVAILABLE",
                      "text": "东区 12 单，西区 7 单。"
                    }
                  }]
                }
                """);
        HttpResponse titleResponse = response(200, """
                {
                  "type": "ASK_CONVERSATION_TURN_PAGE",
                  "turns": [{
                    "askInvocationRef": "ask-1",
                    "operation": "START",
                    "displayState": "COMPLETED",
                    "definitiveTerminal": true,
                    "createdAt": "2026-08-24T07:59:00Z",
                    "updatedAt": "2026-08-24T07:59:06Z",
                    "userMessage": {
                      "contentState": "AVAILABLE",
                      "text": "本月订单量是多少？"
                    },
                    "assistantMessage": {
                      "contentState": "AVAILABLE",
                      "text": "本月共有 19 单。"
                    }
                  }]
                }
                """);
        HttpResponse traceResponse = response(200, """
                {
                  "type": "ASK_TRACE",
                  "askInvocationRef": "ask-2",
                  "externalConversationRef": "analytics-console.conversation-1",
                  "eventHistory": {
                    "state": "COMPLETE",
                    "eventsTruncated": false
                  },
                  "events": [
                    {
                      "eventSeq": 1,
                      "eventType": "worker.operation.input.accepted",
                      "occurredAt": "2026-08-24T08:00:00Z"
                    },
                    {
                      "eventSeq": 2,
                      "eventType": "provider.operation.started",
                      "occurredAt": "2026-08-24T08:00:01Z"
                    },
                    {
                      "eventSeq": 3,
                      "eventType": "langbiz.function.started",
                      "occurredAt": "2026-08-24T08:00:02Z",
                      "functionInvocationId": "function-1",
                      "functionRef": "analytics.semantic.query@v1"
                    },
                    {
                      "eventSeq": 4,
                      "eventType": "langbiz.function.completed",
                      "occurredAt": "2026-08-24T08:00:04Z",
                      "functionInvocationId": "function-1",
                      "functionRef": "analytics.semantic.query@v1"
                    },
                    {
                      "eventSeq": 5,
                      "eventType": "provider.operation.terminal",
                      "occurredAt": "2026-08-24T08:00:06Z"
                    }
                  ]
                }
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(
                        startResponse,
                        continueResponse,
                        turnsResponse,
                        titleResponse,
                        traceResponse);
        HttpAnalyticsConsoleAgentGateway gateway =
                new HttpAnalyticsConsoleAgentGateway(
                        URI.create("http://127.0.0.1:4882"),
                        Duration.ofSeconds(2),
                        json,
                        http);
        var binding = new AnalyticsConsoleFapBindingResolver.OutboundBinding(
                "Bearer subject", "workspace-1", "model-1", "variant-1");

        var started = gateway.start(
                binding,
                new AnalyticsConsoleAgentGateway.StartCommand(
                        "request-1",
                        "analytics-console.conversation-1",
                        "本月订单量是多少？",
                        "frozen system instruction",
                        "workspace-1",
                        "model-1",
                        "variant-1",
                        "analytics-question-answering",
                        "analytics.question-read"));
        var continued = gateway.continueConversation(
                binding,
                new AnalyticsConsoleAgentGateway.ContinueCommand(
                        "request-2",
                        "analytics-console.conversation-1",
                        started.runtimeExecutionId(),
                        "按销售团队拆分",
                        "variant-1",
                        "analytics-question-answering",
                        "analytics.question-read"));
        var turns = gateway.turns(
                binding,
                "request-turns",
                "analytics-console.conversation-1");
        var title = gateway.firstUserMessage(
                binding,
                "request-title",
                "analytics-console.conversation-1");
        var detail = gateway.turnDetail(
                binding,
                "request-2",
                "ask-2",
                "analytics-console.conversation-1");

        assertThat(continued.runtimeExecutionId()).isEqualTo("execution-1");
        assertThat(turns).singleElement().satisfies(turn -> {
            assertThat(turn.operation()).isEqualTo("CONTINUE");
            assertThat(turn.userMessage()).isEqualTo("按销售团队拆分");
            assertThat(turn.assistantMessage()).isEqualTo("东区 12 单，西区 7 单。");
            assertThat(turn.startedAt()).isEqualTo(Instant.parse("2026-08-24T08:00:00Z"));
            assertThat(turn.completedAt()).isEqualTo(Instant.parse("2026-08-24T08:00:06Z"));
            assertThat(turn.durationMs()).isEqualTo(6_000L);
        });
        assertThat(title).isEqualTo("本月订单量是多少？");
        assertThat(detail.historyState()).isEqualTo("COMPLETE");
        assertThat(detail.agentActivities())
                .extracting(AnalyticsConsoleAgentGateway.AgentActivity::label)
                .containsExactly("已接收本轮问题", "Agent 开始分析", "Agent 完成本轮分析");
        assertThat(detail.toolCalls()).singleElement().satisfies(tool -> {
            assertThat(tool.functionRef()).isEqualTo("analytics.semantic.query@v1");
            assertThat(tool.state()).isEqualTo("SUCCEEDED");
            assertThat(tool.durationMs()).isEqualTo(2_000L);
        });

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(5)).send(
                requests.capture(), any(HttpResponse.BodyHandler.class));
        JsonNode startBody = json.readTree(
                requests.getAllValues().get(0).bodyPublisher().orElseThrow()
                        .contentLength() >= 0
                        ? body(requests.getAllValues().get(0))
                        : new byte[0]);
        JsonNode continueBody = json.readTree(body(requests.getAllValues().get(1)));
        assertThat(startBody.path("initialSystemInstruction").asText())
                .isEqualTo("frozen system instruction");
        assertThat(continueBody.path("runtimeExecutionId").asText())
                .isEqualTo("execution-1");
        assertThat(continueBody.has("workspaceRef")).isFalse();
        assertThat(continueBody.has("modelConfigRef")).isFalse();
        assertThat(continueBody.has("initialSystemInstruction")).isFalse();
        assertThat(startBody.has("workspaceFiles")).isFalse();
        assertThat(continueBody.has("workspaceFiles")).isFalse();
        assertThat(requests.getAllValues().get(3).uri().getQuery()).contains("limit=1");
        assertThat(requests.getAllValues().get(4).uri().getPath())
                .endsWith("/asks/requests/request-2/trace");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void addsGuardedWorkspaceFilesWhenBindingExplicitlyEnablesIt() throws Exception {
        ObjectMapper json = new ObjectMapper();
        HttpClient http = mock(HttpClient.class);
        HttpResponse firstAccepted = response(202, """
                                {
                                  "type": "ASK_ACCEPTED",
                                  "askInvocationRef": "ask-files-1",
                                  "runtimeExecutionId": "execution-files-1",
                                  "runtimeTaskId": "task-files-1"
                                }
                                """);
        HttpResponse secondAccepted = response(202, """
                                {
                                  "type": "ASK_ACCEPTED",
                                  "askInvocationRef": "ask-files-2",
                                  "runtimeExecutionId": "execution-files-1",
                                  "runtimeTaskId": "task-files-2"
                                }
                                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(firstAccepted, secondAccepted);
        HttpAnalyticsConsoleAgentGateway gateway =
                new HttpAnalyticsConsoleAgentGateway(
                        URI.create("http://127.0.0.1:4882"),
                        Duration.ofSeconds(2),
                        json,
                        http);
        var binding = new AnalyticsConsoleFapBindingResolver.OutboundBinding(
                "Bearer subject", "workspace-files-1", "model-1", "variant-1", true);

        var started = gateway.start(
                binding,
                new AnalyticsConsoleAgentGateway.StartCommand(
                        "request-files-1",
                        "analytics-console.files-1",
                        "读取工作区草稿",
                        "frozen system instruction",
                        "workspace-files-1",
                        "model-1",
                        "variant-1",
                        "analytics-question-answering",
                        "analytics.question-read"));
        gateway.continueConversation(
                binding,
                new AnalyticsConsoleAgentGateway.ContinueCommand(
                        "request-files-2",
                        "analytics-console.files-1",
                        started.runtimeExecutionId(),
                        "继续处理",
                        "variant-1",
                        "analytics-question-answering",
                        "analytics.question-read"));

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        for (HttpRequest request : requests.getAllValues()) {
            JsonNode workspaceFiles = json.readTree(body(request)).path("workspaceFiles");
            assertThat(workspaceFiles.path("protocol").asText())
                    .isEqualTo("WORKSPACE_FILES_V1");
            assertThat(workspaceFiles.path("isolationMode").asText())
                    .isEqualTo("WORKSPACE_GUARDED");
        }
    }
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void doesNotInferExecutionDurationFromLegacyAskAndTaskProjectionTimestamps()
            throws Exception {
        ObjectMapper json = new ObjectMapper();
        HttpClient http = mock(HttpClient.class);
        HttpResponse turnsResponse = response(200, """
                        {
                          "type": "ASK_CONVERSATION_TURN_PAGE",
                          "turns": [{
                            "askInvocationRef": "ask-legacy",
                            "operation": "START",
                            "displayState": "SUCCEEDED",
                            "definitiveTerminal": true,
                            "createdAt": "2026-08-24T08:00:00Z",
                            "updatedAt": "2026-08-24T11:01:00Z",
                            "userMessage": {"contentState": "AVAILABLE", "text": "问题"},
                            "assistantMessage": {"contentState": "AVAILABLE", "text": "回答"}
                          }]
                        }
                        """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(turnsResponse);
        HttpAnalyticsConsoleAgentGateway gateway =
                new HttpAnalyticsConsoleAgentGateway(
                        URI.create("http://127.0.0.1:4882"),
                        Duration.ofSeconds(2),
                        json,
                        http);

        var turns = gateway.turns(
                new AnalyticsConsoleFapBindingResolver.OutboundBinding(
                        "Bearer subject", "workspace-1", "model-1", "variant-1"),
                "request-turns",
                "analytics-console.conversation-legacy");

        assertThat(turns).singleElement().satisfies(turn -> {
            assertThat(turn.startedAt()).isNull();
            assertThat(turn.completedAt()).isNull();
            assertThat(turn.durationMs()).isNull();
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpResponse response(int status, String body) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(
                body.getBytes(StandardCharsets.UTF_8)));
        return response;
    }

    private static byte[] body(HttpRequest request) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(
                new java.util.concurrent.Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(java.nio.ByteBuffer item) {
                        byte[] bytes = new byte[item.remaining()];
                        item.get(bytes);
                        output.writeBytes(bytes);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        throw new IllegalStateException(throwable);
                    }

                    @Override
                    public void onComplete() {
                    }
                });
        return output.toByteArray();
    }
}
