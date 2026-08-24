package com.foggyframework.analytics.console.api;

import com.foggyframework.analytics.console.agent.AnalyticsConsoleAgentService;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleFapBindingResolver;
import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogException;
import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.console.model.AnalyticsConsoleConversation;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;
import com.foggyframework.analytics.console.service.AnalyticsConsoleService;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionAdapter;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionInvocation;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionOutcome;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
public class AnalyticsConsoleFapCallbackController {

    private final AnalyticsConsoleProperties.Fap properties;
    private final AnalyticsConsoleFapBindingResolver bindings;
    private final AnalyticsConsoleAgentService agents;
    private final AnalyticsConsoleService console;
    private final FapAnalyticsFunctionAdapter adapter;

    public AnalyticsConsoleFapCallbackController(
            AnalyticsConsoleProperties properties,
            AnalyticsConsoleFapBindingResolver bindings,
            AnalyticsConsoleAgentService agents,
            AnalyticsConsoleService console,
            FapAnalyticsFunctionAdapter adapter) {
        this.properties = properties.getFap();
        this.bindings = bindings;
        this.agents = agents;
        this.console = console;
        this.adapter = adapter;
    }

    @PostMapping("/analytics-console/internal/fap/functions:invoke")
    public ResponseEntity<Map<String, Object>> invoke(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody FapAnalyticsCallbackRequest request) {
        authenticate(authorization);
        if (!"PROVIDER_FUNCTION_CALLBACK".equals(request.type())
                || !properties.getProviderRef().equals(request.providerRef())
                || !properties.getCallbackCapabilityId().equals(request.capabilityId())
                || properties.getCallbackCapabilityRevision()
                        != request.capabilityRevision()) {
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
        String bundleRef = string(request.arguments().get("bundleRef"));
        String artifactRef = string(request.arguments().get("artifactRef"));
        if (!console.canInvokeFap(subject, bundleRef, artifactRef)) {
            throw forbidden("FAP callback cannot access the Analytics asset");
        }
        if (!request.binding().runtimeExecutionId().equals(conversation.runtimeExecutionId())
                || !request.binding().runtimeTaskId().equals(conversation.runtimeTaskId())) {
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
        return ResponseEntity.status(outcome.recommendedHttpStatus())
                .body(outcome.callbackBody());
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
