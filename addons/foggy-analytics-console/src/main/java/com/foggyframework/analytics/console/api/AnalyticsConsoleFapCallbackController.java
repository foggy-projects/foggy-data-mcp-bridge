package com.foggyframework.analytics.console.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleAgentService;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleFapBindingResolver;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleFunctionTraceRepository;
import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogException;
import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.console.model.AnalyticsConsoleConversation;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAskBinding;
import com.foggyframework.analytics.console.model.AnalyticsConsoleConversationMode;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;
import com.foggyframework.analytics.console.service.AnalyticsConsoleService;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionOperations;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionAdapter;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionInvocation;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionOutcome;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionRefs;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;

@RestController
public class AnalyticsConsoleFapCallbackController {

    private static final Logger LOG =
            LoggerFactory.getLogger(AnalyticsConsoleFapCallbackController.class);

    private static final Set<String> DESIGN_OPERATIONS = Set.of(
            AnalyticsFunctionOperations.CAPABILITIES,
            AnalyticsFunctionOperations.BUNDLES_VALIDATE,
            AnalyticsFunctionOperations.BUNDLES_DESCRIBE,
            AnalyticsFunctionOperations.ARTIFACTS_DESCRIBE,
            AnalyticsFunctionOperations.REPORTS_PREVIEW,
            AnalyticsFunctionOperations.DASHBOARDS_PREVIEW,
            AnalyticsFunctionOperations.DASHBOARDS_RENDER);
    private static final Set<String> QUESTION_OPERATIONS = Set.of(
            AnalyticsFunctionOperations.CAPABILITIES,
            AnalyticsFunctionOperations.MODEL_DEPENDENCIES_LIST,
            AnalyticsFunctionOperations.SEMANTIC_MODELS_DESCRIBE,
            AnalyticsFunctionOperations.SEMANTIC_QUERIES_EXECUTE);

    private final AnalyticsConsoleProperties.Fap properties;
    private final AnalyticsConsoleFapBindingResolver bindings;
    private final AnalyticsConsoleAgentService agents;
    private final AnalyticsConsoleService console;
    private final FapAnalyticsFunctionAdapter adapter;
    private final AnalyticsConsoleFunctionTraceRepository functionTraces;
    private final ObjectMapper json;

    public AnalyticsConsoleFapCallbackController(
            AnalyticsConsoleProperties properties,
            AnalyticsConsoleFapBindingResolver bindings,
            AnalyticsConsoleAgentService agents,
            AnalyticsConsoleService console,
            FapAnalyticsFunctionAdapter adapter,
            AnalyticsConsoleFunctionTraceRepository functionTraces,
            ObjectMapper json) {
        this.properties = properties.getFap();
        this.bindings = bindings;
        this.agents = agents;
        this.console = console;
        this.adapter = adapter;
        this.functionTraces = functionTraces;
        this.json = json.copy().findAndRegisterModules();
    }

    @PostMapping("/analytics-console/internal/fap/functions:invoke")
    public ResponseEntity<Map<String, Object>> invoke(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody FapAnalyticsCallbackRequest request) {
        authenticate(authorization);
        if (!"PROVIDER_FUNCTION_CALLBACK".equals(request.type())
                || !properties.getProviderRef().equals(request.providerRef())) {
            throw forbidden("FAP callback Provider is not accepted");
        }
        FapAnalyticsFunctionInvocation.Caller caller =
                new FapAnalyticsFunctionInvocation.Caller(
                        request.providerRef(),
                        request.tenantRef(),
                        request.providerSubjectRef(),
                        request.externalSubjectRef());
        AnalyticsConsoleSubject subject = bindings.resolveCaller(caller);
        AnalyticsConsoleConversation conversation = agents.requireCallbackConversation(
                subject,
                request.externalConversationRef(),
                request.askRequestId(),
                request.askInvocationRef());
        requireCapability(conversation, request);
        AnalyticsConsoleAskBinding askBinding = agents.requireCallbackAskBinding(
                conversation, request.askRequestId(), request.askInvocationRef());
        String operation = FapAnalyticsFunctionRefs.operation(request.functionRef());
        Set<String> allowed = conversation.mode()
                == AnalyticsConsoleConversationMode.QUESTION
                ? QUESTION_OPERATIONS
                : DESIGN_OPERATIONS;
        if (operation == null || !allowed.contains(operation)) {
            throw forbidden("FAP callback Function is outside the conversation mode");
        }
        String bundleRef = string(request.arguments().get("bundleRef"));
        String artifactRef = string(request.arguments().get("artifactRef"));
        if (conversation.mode() == AnalyticsConsoleConversationMode.DESIGN) {
            if (!AnalyticsFunctionOperations.CAPABILITIES.equals(operation)
                    && !console.canInvokeFap(
                            subject,
                            conversation.assetId(),
                            bundleRef,
                            artifactRef)) {
                throw forbidden("FAP callback cannot access the bound Analytics asset");
            }
        } else if (!AnalyticsFunctionOperations.CAPABILITIES.equals(operation)) {
            if (!conversation.namespace().equals(
                    string(request.arguments().get("namespace")))) {
                throw forbidden("FAP callback cannot change the question namespace");
            }
            if (conversation.modelName() != null
                    && (!conversation.modelName().equals(
                            string(request.arguments().get("modelName")))
                        || !conversation.modelRevision().equals(
                            string(request.arguments().get("expectedModelRevision"))))) {
                throw forbidden("FAP callback cannot change the legacy question model scope");
            }
        }
        if (!request.binding().runtimeExecutionId().equals(
                    askBinding.runtimeExecutionId())
                || !request.binding().runtimeTaskId().equals(
                    askBinding.runtimeTaskId())) {
            throw forbidden("FAP callback Runtime binding does not match the Console conversation");
        }
        FapAnalyticsFunctionOutcome outcome = adapter.invoke(
                new FapAnalyticsFunctionInvocation(
                        request.meta().contractVersion(),
                        request.meta().requestId(),
                        request.functionInvocationId(),
                        request.functionRef(),
                        request.arguments(),
                        request.requestDigest(),
                        caller));
        recordTrace(conversation, request, outcome);
        return ResponseEntity.status(outcome.recommendedHttpStatus())
                .body(outcome.callbackBody());
    }

    private void recordTrace(
            AnalyticsConsoleConversation conversation,
            FapAnalyticsCallbackRequest request,
            FapAnalyticsFunctionOutcome outcome) {
        JsonNode result = outcome instanceof FapAnalyticsFunctionOutcome.Success success
                ? json.valueToTree(success.result())
                : json.valueToTree(outcome.callbackBody());
        try {
            functionTraces.save(new AnalyticsConsoleFunctionTraceRepository.FunctionTrace(
                    conversation.conversationId(),
                    request.askRequestId(),
                    request.askInvocationRef(),
                    request.functionInvocationId(),
                    request.functionRef(),
                    json.valueToTree(request.arguments()),
                    result,
                    outcome.recommendedHttpStatus()));
        } catch (RuntimeException unavailable) {
            LOG.warn(
                    "Analytics Function trace could not be recorded for invocation {}",
                    request.functionInvocationId(),
                    unavailable);
        }
    }

    private void requireCapability(
            AnalyticsConsoleConversation conversation,
            FapAnalyticsCallbackRequest request) {
        boolean question = conversation.mode() == AnalyticsConsoleConversationMode.QUESTION;
        String expectedId = question
                ? properties.getQuestionCallbackCapabilityId()
                : properties.getCallbackCapabilityId();
        int expectedRevision = question
                ? properties.getQuestionCallbackCapabilityRevision()
                : properties.getCallbackCapabilityRevision();
        if (!expectedId.equals(request.capabilityId())
                || expectedRevision != request.capabilityRevision()) {
            throw forbidden("FAP callback Capability is not accepted for this conversation");
        }
    }

    private void authenticate(String authorization) {
        String expected = properties.getCallbackAuthorization();
        if (authorization == null || expected == null
                || !MessageDigest.isEqual(
                        authorization.getBytes(StandardCharsets.UTF_8),
                        expected.getBytes(StandardCharsets.UTF_8))) {
            throw forbidden("FAP callback authorization was rejected");
        }
    }

    private static String string(Object value) {
        return value instanceof String text ? text : null;
    }

    private static AnalyticsConsoleCatalogException forbidden(String message) {
        return new AnalyticsConsoleCatalogException(
                "ANALYTICS_CONSOLE_FAP_CONTEXT_FORBIDDEN", message);
    }
}
