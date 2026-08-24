package com.foggyframework.analytics.console.api;

import com.foggyframework.analytics.console.agent.AnalyticsConsoleAgentService;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleFapBindingResolver;
import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.console.model.AnalyticsConsoleConversation;
import com.foggyframework.analytics.console.model.AnalyticsConsoleConversationMode;
import com.foggyframework.analytics.console.security.AnalyticsConsoleRole;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;
import com.foggyframework.analytics.console.service.AnalyticsConsoleService;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionAdapter;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionOutcome;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionRefs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsConsoleFapCallbackControllerTest {

    private final AnalyticsConsoleFapBindingResolver bindings =
            mock(AnalyticsConsoleFapBindingResolver.class);
    private final AnalyticsConsoleAgentService agents = mock(AnalyticsConsoleAgentService.class);
    private final AnalyticsConsoleService console = mock(AnalyticsConsoleService.class);
    private final FapAnalyticsFunctionAdapter adapter = mock(FapAnalyticsFunctionAdapter.class);
    private AnalyticsConsoleFapCallbackController controller;
    private AnalyticsConsoleSubject subject;

    @BeforeEach
    void setUp() {
        AnalyticsConsoleProperties properties = new AnalyticsConsoleProperties();
        properties.getFap().setProviderRef("provider-1");
        properties.getFap().setCallbackCapabilityId("analytics.design-read");
        properties.getFap().setCallbackCapabilityRevision(3);
        properties.getFap().setQuestionCallbackCapabilityId("analytics.question-read");
        properties.getFap().setQuestionCallbackCapabilityRevision(2);
        properties.getFap().setCallbackAuthorization("Bearer callback-secret");
        controller = new AnalyticsConsoleFapCallbackController(
                properties, bindings, agents, console, adapter);
        subject = new AnalyticsConsoleSubject(
                "designer", "Designer", Set.of(AnalyticsConsoleRole.DESIGNER),
                "console", "authority-designer");
    }

    @Test
    void rejectsCallbackOutsideTheExactServerOwnedCapability() {
        FapAnalyticsCallbackRequest request = request("different-capability", 3);
        AnalyticsConsoleConversation conversation = designConversation(request);
        when(bindings.resolveCaller(any())).thenReturn(subject);
        when(agents.requireCallbackConversation(
                subject,
                request.externalConversationRef(),
                request.askRequestId(),
                request.askInvocationRef())).thenReturn(conversation);

        assertThatThrownBy(() -> controller.invoke("Bearer callback-secret", request))
                .hasMessageContaining("Capability is not accepted");
    }

    @Test
    void invokesAdapterOnlyAfterExactConversationAndRuntimeBinding() {
        FapAnalyticsCallbackRequest request = request("analytics.design-read", 3);
        var conversation = designConversation(request);
        when(bindings.resolveCaller(any())).thenReturn(subject);
        when(agents.requireCallbackConversation(
                subject,
                request.externalConversationRef(),
                request.askRequestId(),
                request.askInvocationRef())).thenReturn(conversation);
        when(agents.requireCallbackAskBinding(
                conversation,
                request.askRequestId(),
                request.askInvocationRef())).thenReturn(
                        conversation.askBindings().get(0));
        when(console.canInvokeFap(
                subject, "asset-1", "sales", "sales-report")).thenReturn(true);
        when(adapter.invoke(any())).thenReturn(FapAnalyticsFunctionOutcome.Success.create(
                "callback-request-1", "function-invocation-1", Map.of("accepted", true)));

        var response = controller.invoke("Bearer callback-secret", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .containsEntry("type", "PROVIDER_FUNCTION_CALLBACK_RESULT");
    }

    @Test
    void questionCallbackCannotChangeTheFrozenModelRevision() {
        String revision = "sha256:" + "b".repeat(64);
        FapAnalyticsCallbackRequest request = questionRequest(
                "analytics.question-read", 2, "sha256:" + "c".repeat(64));
        var conversation = new AnalyticsConsoleConversation(
                "conversation-1",
                null,
                subject.subjectRef(),
                request.externalConversationRef(),
                request.askRequestId(),
                request.askInvocationRef(),
                request.binding().runtimeExecutionId(),
                request.binding().runtimeTaskId(),
                Instant.EPOCH,
                AnalyticsConsoleConversationMode.QUESTION,
                "orders",
                "default",
                "FactOrderQueryModel",
                revision,
                null);
        when(bindings.resolveCaller(any())).thenReturn(subject);
        when(agents.requireCallbackConversation(
                subject,
                request.externalConversationRef(),
                request.askRequestId(),
                request.askInvocationRef())).thenReturn(conversation);
        when(agents.requireCallbackAskBinding(
                conversation,
                request.askRequestId(),
                request.askInvocationRef())).thenReturn(
                        conversation.askBindings().get(0));

        assertThatThrownBy(() -> controller.invoke("Bearer callback-secret", request))
                .hasMessageContaining("cannot change the question model scope");
    }

    private static FapAnalyticsCallbackRequest request(
            String capabilityId,
            long capabilityRevision) {
        return new FapAnalyticsCallbackRequest(
                "PROVIDER_FUNCTION_CALLBACK",
                new FapAnalyticsCallbackRequest.Meta(
                        "fap.service-provider.v1alpha1", "callback-request-1"),
                "provider-1",
                "tenant-1",
                "provider-subject-1",
                "external-subject-1",
                "ask-invocation-1",
                "ask-request-1",
                "analytics-console.conversation-1",
                new FapAnalyticsCallbackRequest.Binding(
                        "worker-1", "execution-1", "task-1"),
                "function-invocation-1",
                capabilityId,
                capabilityRevision,
                FapAnalyticsFunctionRefs.REPORTS_PREVIEW,
                Map.of("bundleRef", "sales", "artifactRef", "sales-report"),
                "sha256:" + "a".repeat(64));
    }

    private AnalyticsConsoleConversation designConversation(
            FapAnalyticsCallbackRequest request) {
        return new AnalyticsConsoleConversation(
                "conversation-1", "asset-1", subject.subjectRef(),
                request.externalConversationRef(), request.askRequestId(),
                request.askInvocationRef(), request.binding().runtimeExecutionId(),
                request.binding().runtimeTaskId(), Instant.EPOCH);
    }

    private static FapAnalyticsCallbackRequest questionRequest(
            String capabilityId,
            long capabilityRevision,
            String modelRevision) {
        return new FapAnalyticsCallbackRequest(
                "PROVIDER_FUNCTION_CALLBACK",
                new FapAnalyticsCallbackRequest.Meta(
                        "fap.service-provider.v1alpha1", "callback-request-1"),
                "provider-1",
                "tenant-1",
                "provider-subject-1",
                "external-subject-1",
                "ask-invocation-1",
                "ask-request-1",
                "analytics-console.conversation-1",
                new FapAnalyticsCallbackRequest.Binding(
                        "worker-1", "execution-1", "task-1"),
                "function-invocation-1",
                capabilityId,
                capabilityRevision,
                FapAnalyticsFunctionRefs.SEMANTIC_QUERIES_EXECUTE,
                Map.of(
                        "namespace", "default",
                        "modelName", "FactOrderQueryModel",
                        "expectedModelRevision", modelRevision,
                        "query", Map.of("columns", java.util.List.of("orderCount"))),
                "sha256:" + "a".repeat(64));
    }
}
