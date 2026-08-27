package com.foggyframework.analytics.console.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleAgentService;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleFapBindingResolver;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleFunctionTraceRepository;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsConsoleFapCallbackControllerTest {

    private final AnalyticsConsoleFapBindingResolver bindings =
            mock(AnalyticsConsoleFapBindingResolver.class);
    private final AnalyticsConsoleAgentService agents = mock(AnalyticsConsoleAgentService.class);
    private final AnalyticsConsoleService console = mock(AnalyticsConsoleService.class);
    private final FapAnalyticsFunctionAdapter adapter = mock(FapAnalyticsFunctionAdapter.class);
    private final AnalyticsConsoleFunctionTraceRepository functionTraces =
            mock(AnalyticsConsoleFunctionTraceRepository.class);
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
                properties,
                bindings,
                agents,
                console,
                adapter,
                functionTraces,
                new ObjectMapper());
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
        Map<String, Object> callbackResult = new LinkedHashMap<>();
        callbackResult.put("accepted", true);
        callbackResult.put("optional", null);
        when(adapter.invoke(any())).thenReturn(FapAnalyticsFunctionOutcome.Success.create(
                "callback-request-1", "function-invocation-1", callbackResult));

        var response = controller.invoke("Bearer callback-secret", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("type").asText())
                .isEqualTo("PROVIDER_FUNCTION_CALLBACK_RESULT");
        assertThat(response.getBody().path("result").has("optional")).isTrue();
        assertThat(response.getBody().path("result").path("optional").isNull()).isTrue();
        var trace = forClass(
                AnalyticsConsoleFunctionTraceRepository.FunctionTrace.class);
        verify(functionTraces).save(trace.capture());
        assertThat(trace.getValue().conversationId()).isEqualTo("conversation-1");
        assertThat(trace.getValue().functionRef()).isEqualTo(request.functionRef());
        assertThat(trace.getValue().arguments().path("bundleRef").asText())
                .isEqualTo("sales");
        assertThat(trace.getValue().result().path("accepted").asBoolean()).isTrue();
        assertThat(trace.getValue().httpStatus()).isEqualTo(200);
    }

    @Test
    void namespaceScopedQuestionAcceptsFrozenV2ModelDependencyRef() {
        FapAnalyticsCallbackRequest request = advancedQuestionRequest(
                FapAnalyticsFunctionRefs.LEGACY_MODEL_DEPENDENCIES_LIST_V2,
                Map.of("namespace", "default"));
        var conversation = namespaceConversation(request, "default");
        when(bindings.resolveCaller(any())).thenReturn(subject);
        when(agents.requireCallbackConversation(
                subject,
                request.externalConversationRef(),
                request.askRequestId(),
                request.askInvocationRef())).thenReturn(conversation);
        when(agents.requireCallbackAskBinding(
                conversation,
                request.askRequestId(),
                request.askInvocationRef())).thenReturn(conversation.askBindings().get(0));
        when(adapter.invoke(any())).thenReturn(FapAnalyticsFunctionOutcome.Success.create(
                "callback-request-1", "function-invocation-1", Map.of("accepted", true)));

        var response = controller.invoke("Bearer callback-secret", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(adapter).invoke(any());
    }

    @Test
    void legacyQuestionCallbackStillPinsTheModelName() {
        String revision = "sha256:" + "b".repeat(64);
        FapAnalyticsCallbackRequest request = advancedQuestionRequest(
                FapAnalyticsFunctionRefs.SEMANTIC_QUERIES_EXECUTE,
                Map.of(
                        "namespace", "default",
                        "modelName", "AnotherQueryModel",
                        "query", Map.of("columns", java.util.List.of("orderCount"))));
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
                .hasMessageContaining("cannot change the legacy question model scope");
    }

    @Test
    void namespaceScopedQuestionAllowsAnyQmInTheBoundNamespace() {
        FapAnalyticsCallbackRequest request = questionRequest(
                "analytics.question-read", 2);
        var conversation = namespaceConversation(request, "default");
        when(bindings.resolveCaller(any())).thenReturn(subject);
        when(agents.requireCallbackConversation(
                subject,
                request.externalConversationRef(),
                request.askRequestId(),
                request.askInvocationRef())).thenReturn(conversation);
        when(agents.requireCallbackAskBinding(
                conversation,
                request.askRequestId(),
                request.askInvocationRef())).thenReturn(conversation.askBindings().get(0));
        when(adapter.invoke(any())).thenReturn(FapAnalyticsFunctionOutcome.Success.create(
                "callback-request-1", "function-invocation-1", Map.of("accepted", true)));

        var response = controller.invoke("Bearer callback-secret", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void namespaceScopedQuestionAllowsFullDslAndRestrictedCompose() {
        FapAnalyticsCallbackRequest query = advancedQuestionRequest(
                FapAnalyticsFunctionRefs.QUERY_MODEL_RUN,
                Map.of(
                        "namespace", "default",
                        "modelName", "FactOrderQueryModel",
                        "mode", "validate",
                        "payload", Map.of("columns", java.util.List.of("orderCount"))));
        FapAnalyticsCallbackRequest compose = advancedQuestionRequest(
                FapAnalyticsFunctionRefs.COMPOSE_RUN,
                Map.of(
                        "namespace", "default",
                        "mode", "validate",
                        "script", "return 1;",
                        "params", Map.of()));
        var conversation = namespaceConversation(query, "default");
        when(bindings.resolveCaller(any())).thenReturn(subject);
        when(agents.requireCallbackConversation(
                subject,
                query.externalConversationRef(),
                query.askRequestId(),
                query.askInvocationRef())).thenReturn(conversation);
        when(agents.requireCallbackAskBinding(
                conversation,
                query.askRequestId(),
                query.askInvocationRef())).thenReturn(conversation.askBindings().get(0));
        when(adapter.invoke(any())).thenReturn(FapAnalyticsFunctionOutcome.Success.create(
                "callback-request-1", "function-invocation-1", Map.of("accepted", true)));

        assertThat(controller.invoke("Bearer callback-secret", query)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(controller.invoke("Bearer callback-secret", compose)
                .getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void semanticQueryRejectionReturnsRepairablePreEffectDetailsToFap() {
        FapAnalyticsCallbackRequest request = advancedQuestionRequest(
                FapAnalyticsFunctionRefs.QUERY_MODEL_RUN,
                Map.of(
                        "namespace", "default",
                        "modelName", "FactOrderQueryModel",
                        "mode", "validate",
                        "payload", Map.of("columns", java.util.List.of("missingField"))));
        var conversation = namespaceConversation(request, "default");
        when(bindings.resolveCaller(any())).thenReturn(subject);
        when(agents.requireCallbackConversation(
                subject,
                request.externalConversationRef(),
                request.askRequestId(),
                request.askInvocationRef())).thenReturn(conversation);
        when(agents.requireCallbackAskBinding(
                conversation,
                request.askRequestId(),
                request.askInvocationRef())).thenReturn(conversation.askBindings().get(0));
        when(adapter.invoke(any())).thenReturn(new FapAnalyticsFunctionOutcome.Failure(
                "callback-request-1",
                "function-invocation-1",
                "FUNCTION_ARGUMENT_INVALID",
                "Function arguments do not match the published schema",
                false,
                422,
                new FapAnalyticsFunctionOutcome.RepairDetails(
                        FapAnalyticsFunctionRefs.QUERY_MODEL_RUN,
                        "sha256:" + "d".repeat(64),
                        java.util.List.of(new FapAnalyticsFunctionOutcome.Violation(
                                "/payload",
                                "semanticQuery",
                                "SEMANTIC_QUERY_INVALID")),
                        false)));

        var response = controller.invoke("Bearer callback-secret", request);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().path("code").asText())
                .isEqualTo("FUNCTION_ARGUMENT_INVALID");
        assertThat(response.getBody().path("modelRepairable").asBoolean()).isTrue();
        assertThat(response.getBody().path("effectPhase").asText())
                .isEqualTo("PRE_EFFECT");
        assertThat(response.getBody().path("violations").get(0)
                .path("instancePath").asText()).isEqualTo("/payload");
    }

    @Test
    void namespaceScopedQuestionRejectsCrossNamespaceQm() {
        FapAnalyticsCallbackRequest request = questionRequest(
                "analytics.question-read", 2, "other");
        var conversation = namespaceConversation(request, "default");
        when(bindings.resolveCaller(any())).thenReturn(subject);
        when(agents.requireCallbackConversation(
                subject,
                request.externalConversationRef(),
                request.askRequestId(),
                request.askInvocationRef())).thenReturn(conversation);
        when(agents.requireCallbackAskBinding(
                conversation,
                request.askRequestId(),
                request.askInvocationRef())).thenReturn(conversation.askBindings().get(0));

        assertThatThrownBy(() -> controller.invoke("Bearer callback-secret", request))
                .hasMessageContaining("cannot change the question namespace");
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
            long capabilityRevision) {
        return questionRequest(capabilityId, capabilityRevision, "default");
    }

    private static FapAnalyticsCallbackRequest questionRequest(
            String capabilityId,
            long capabilityRevision,
            String namespace) {
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
                        "namespace", namespace,
                        "modelName", "FactOrderQueryModel",
                        "query", Map.of("columns", java.util.List.of("orderCount"))),
                "sha256:" + "a".repeat(64));
    }

    private static FapAnalyticsCallbackRequest advancedQuestionRequest(
            String functionRef,
            Map<String, Object> arguments) {
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
                "analytics.question-read",
                2,
                functionRef,
                arguments,
                "sha256:" + "a".repeat(64));
    }

    private AnalyticsConsoleConversation namespaceConversation(
            FapAnalyticsCallbackRequest request,
            String namespace) {
        return new AnalyticsConsoleConversation(
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
                "default",
                namespace,
                null,
                null,
                null);
    }
}
