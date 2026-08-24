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
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(startResponse, continueResponse, turnsResponse);
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

        assertThat(continued.runtimeExecutionId()).isEqualTo("execution-1");
        assertThat(turns).singleElement().satisfies(turn -> {
            assertThat(turn.operation()).isEqualTo("CONTINUE");
            assertThat(turn.userMessage()).isEqualTo("按销售团队拆分");
            assertThat(turn.assistantMessage()).isEqualTo("东区 12 单，西区 7 单。");
        });

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(3)).send(
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
