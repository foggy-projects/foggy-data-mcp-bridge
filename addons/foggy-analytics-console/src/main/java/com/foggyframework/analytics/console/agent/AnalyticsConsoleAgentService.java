package com.foggyframework.analytics.console.agent;

import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogException;
import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogRepository;
import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAsset;
import com.foggyframework.analytics.console.model.AnalyticsConsoleCatalogState;
import com.foggyframework.analytics.console.model.AnalyticsConsoleConversation;
import com.foggyframework.analytics.console.security.AnalyticsConsoleRole;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;
import com.foggyframework.analytics.console.service.AnalyticsConsoleService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Freezes product selections before submitting a read-only Analytics design Ask to FAP. */
public final class AnalyticsConsoleAgentService {

    private final AnalyticsConsoleService console;
    private final AnalyticsConsoleCatalogRepository catalog;
    private final AnalyticsConsoleAgentGateway gateway;
    private final AnalyticsConsoleFapBindingResolver bindings;
    private final AnalyticsConsoleProperties.Fap properties;
    private final Clock clock;

    public AnalyticsConsoleAgentService(
            AnalyticsConsoleService console,
            AnalyticsConsoleCatalogRepository catalog,
            AnalyticsConsoleAgentGateway gateway,
            AnalyticsConsoleFapBindingResolver bindings,
            AnalyticsConsoleProperties properties) {
        this(console, catalog, gateway, bindings, properties.getFap(), Clock.systemUTC());
    }

    AnalyticsConsoleAgentService(
            AnalyticsConsoleService console,
            AnalyticsConsoleCatalogRepository catalog,
            AnalyticsConsoleAgentGateway gateway,
            AnalyticsConsoleFapBindingResolver bindings,
            AnalyticsConsoleProperties.Fap properties,
            Clock clock) {
        this.console = Objects.requireNonNull(console, "console");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AnalyticsConsoleConversation start(
            AnalyticsConsoleSubject subject,
            String assetId,
            String prompt) {
        AnalyticsConsoleAsset asset = console.requireAgentAsset(subject, assetId);
        String safePrompt = required(prompt, "prompt", 16_000);
        String conversationId = "conversation-" + UUID.randomUUID();
        String externalConversationRef = "analytics-console." + conversationId;
        String requestId = "analytics-console.ask." + UUID.randomUUID();
        AnalyticsConsoleFapBindingResolver.OutboundBinding binding = bindings.resolve(subject);
        AnalyticsConsoleAgentGateway.Accepted accepted = gateway.start(
                binding,
                new AnalyticsConsoleAgentGateway.StartCommand(
                        requestId,
                        externalConversationRef,
                        safePrompt,
                        instruction(asset),
                        binding.workspaceRef(),
                        binding.modelConfigRef(),
                        binding.modelVariantId(),
                        properties.getSkillName(),
                        properties.getCapabilityName()));
        AnalyticsConsoleConversation conversation = new AnalyticsConsoleConversation(
                conversationId,
                asset.assetId(),
                subject.subjectRef(),
                externalConversationRef,
                requestId,
                accepted.askInvocationRef(),
                accepted.runtimeExecutionId(),
                accepted.runtimeTaskId(),
                clock.instant());
        catalog.update(state -> {
            List<AnalyticsConsoleConversation> conversations =
                    new ArrayList<>(state.conversations());
            conversations.add(conversation);
            return new AnalyticsConsoleCatalogState(
                    state.revision(), state.folders(), state.assets(), conversations);
        });
        return conversation;
    }

    public List<AnalyticsConsoleAgentGateway.Turn> turns(
            AnalyticsConsoleSubject subject,
            String conversationId) {
        AnalyticsConsoleConversation conversation = requireConversation(subject, conversationId);
        AnalyticsConsoleFapBindingResolver.OutboundBinding binding = bindings.resolve(subject);
        return gateway.turns(
                binding,
                "analytics-console.turns." + UUID.randomUUID(),
                conversation.externalConversationRef());
    }

    public AnalyticsConsoleConversation requireCallbackConversation(
            AnalyticsConsoleSubject subject,
            String externalConversationRef,
            String askRequestId,
            String askInvocationRef) {
        return catalog.read().conversations().stream()
                .filter(value -> value.externalConversationRef().equals(externalConversationRef))
                .filter(value -> value.askRequestId().equals(askRequestId))
                .filter(value -> value.askInvocationRef().equals(askInvocationRef))
                .filter(value -> value.ownerSubjectRef().equals(subject.subjectRef()))
                .findFirst()
                .orElseThrow(() -> new AnalyticsConsoleCatalogException(
                        "ANALYTICS_CONSOLE_FAP_CONTEXT_FORBIDDEN",
                        "FAP callback is not bound to an Analytics Console conversation"));
    }

    private AnalyticsConsoleConversation requireConversation(
            AnalyticsConsoleSubject subject,
            String conversationId) {
        String expected = required(conversationId, "conversationId", 256);
        return catalog.read().conversations().stream()
                .filter(value -> value.conversationId().equals(expected))
                .filter(value -> value.ownerSubjectRef().equals(subject.subjectRef())
                        || subject.hasRole(AnalyticsConsoleRole.ADMIN))
                .findFirst()
                .orElseThrow(() -> new AnalyticsConsoleCatalogException(
                        "ANALYTICS_CONSOLE_CONVERSATION_NOT_FOUND",
                        "Analytics Console conversation was not found"));
    }

    private static String instruction(AnalyticsConsoleAsset asset) {
        return "You are the Foggy Analytics Console design assistant. "
                + "The server has fixed this task to asset " + asset.assetId()
                + ", bundle " + asset.bundleRef()
                + ", artifact " + asset.artifactRef()
                + ", revision " + asset.bundleRevision() + ". "
                + "Use only the selected read-only Analytics capability. "
                + "Return concrete JSON editing suggestions and explain validation issues. "
                + "Do not claim that a definition was saved, published, authorized, or executed "
                + "unless the corresponding Function result proves it. Never request raw SQL, "
                + "credentials, owner metadata, ACL filters, filesystem paths, HTML, JavaScript, "
                + "iframes, or network access.";
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || !value.equals(value.trim())
                || value.length() > maxLength) {
            throw new AnalyticsConsoleCatalogException(
                    "ANALYTICS_CONSOLE_REQUEST_INVALID", field + " is invalid");
        }
        return value;
    }
}
