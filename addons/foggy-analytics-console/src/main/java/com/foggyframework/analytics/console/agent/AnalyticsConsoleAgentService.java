package com.foggyframework.analytics.console.agent;

import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogException;
import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogRepository;
import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAskBinding;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAsset;
import com.foggyframework.analytics.console.model.AnalyticsConsoleCatalogState;
import com.foggyframework.analytics.console.model.AnalyticsConsoleConversation;
import com.foggyframework.analytics.console.model.AnalyticsConsoleConversationMode;
import com.foggyframework.analytics.console.security.AnalyticsConsoleRole;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;
import com.foggyframework.analytics.console.service.AnalyticsConsoleService;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyDescription;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyResolutionRequest;
import com.foggyframework.analytics.function.sdk.AnalyticsFunctionClient;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Freezes product selections before submitting direct-question or design Asks to FAP. */
public final class AnalyticsConsoleAgentService {

    private final AnalyticsConsoleService console;
    private final AnalyticsConsoleCatalogRepository catalog;
    private final AnalyticsConsoleAgentGateway gateway;
    private final AnalyticsConsoleFapBindingResolver bindings;
    private final AnalyticsConsoleProperties.Fap properties;
    private final List<AnalyticsConsoleProperties.QuestionProfile> questionProfiles;
    private final AnalyticsFunctionClient functions;
    private final Clock clock;

    public AnalyticsConsoleAgentService(
            AnalyticsConsoleService console,
            AnalyticsConsoleCatalogRepository catalog,
            AnalyticsConsoleAgentGateway gateway,
            AnalyticsConsoleFapBindingResolver bindings,
            AnalyticsConsoleProperties properties,
            AnalyticsFunctionClient functions) {
        this(
                console,
                catalog,
                gateway,
                bindings,
                properties.getFap(),
                properties.getQuestionProfiles(),
                functions,
                Clock.systemUTC());
    }

    AnalyticsConsoleAgentService(
            AnalyticsConsoleService console,
            AnalyticsConsoleCatalogRepository catalog,
            AnalyticsConsoleAgentGateway gateway,
            AnalyticsConsoleFapBindingResolver bindings,
            AnalyticsConsoleProperties.Fap properties,
            Clock clock) {
        this(
                console,
                catalog,
                gateway,
                bindings,
                properties,
                List.of(),
                null,
                clock);
    }

    AnalyticsConsoleAgentService(
            AnalyticsConsoleService console,
            AnalyticsConsoleCatalogRepository catalog,
            AnalyticsConsoleAgentGateway gateway,
            AnalyticsConsoleFapBindingResolver bindings,
            AnalyticsConsoleProperties.Fap properties,
            List<AnalyticsConsoleProperties.QuestionProfile> questionProfiles,
            AnalyticsFunctionClient functions,
            Clock clock) {
        this.console = Objects.requireNonNull(console, "console");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.questionProfiles = List.copyOf(Objects.requireNonNull(
                questionProfiles, "questionProfiles"));
        this.functions = functions;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AnalyticsConsoleConversation start(
            AnalyticsConsoleSubject subject,
            String assetId,
            String prompt) {
        return startDesign(subject, assetId, prompt);
    }

    public AnalyticsConsoleConversation startDesign(
            AnalyticsConsoleSubject subject,
            String assetId,
            String prompt) {
        AnalyticsConsoleAsset asset = console.requireAgentAsset(subject, assetId);
        return start(
                subject,
                safePrompt(prompt),
                AnalyticsConsoleConversationMode.DESIGN,
                asset,
                null,
                instruction(asset),
                properties.getSkillName(),
                properties.getCapabilityName());
    }

    public List<QuestionProfile> questionProfiles(AnalyticsConsoleSubject subject) {
        Objects.requireNonNull(subject, "subject");
        return questionProfiles.stream()
                .map(value -> new QuestionProfile(
                        value.getId(),
                        value.getDisplayName(),
                        value.getDescription(),
                        value.getNamespace(),
                        value.getModelName()))
                .toList();
    }

    public List<ConversationSummary> questionConversations(
            AnalyticsConsoleSubject subject) {
        Objects.requireNonNull(subject, "subject");
        return catalog.read().conversations().stream()
                .filter(value -> value.mode() == AnalyticsConsoleConversationMode.QUESTION)
                .filter(value -> value.ownerSubjectRef().equals(subject.subjectRef()))
                .map(this::summarize)
                .sorted(Comparator.comparing(ConversationSummary::lastActivityAt).reversed())
                .toList();
    }

    public AnalyticsConsoleConversation conversation(
            AnalyticsConsoleSubject subject,
            String conversationId) {
        return requireConversation(subject, conversationId);
    }

    public AnalyticsConsoleConversation startQuestion(
            AnalyticsConsoleSubject subject,
            String profileId,
            String prompt) {
        AnalyticsConsoleProperties.QuestionProfile profile = requireProfile(profileId);
        AnalyticsModelDependencyDescription dependency = resolveDependency(profile);
        QuestionScope scope = new QuestionScope(
                profile.getId(),
                profile.getDisplayName(),
                dependency.namespace(),
                dependency.modelName(),
                dependency.modelRevision());
        return start(
                subject,
                safePrompt(prompt),
                AnalyticsConsoleConversationMode.QUESTION,
                null,
                scope,
                questionInstruction(scope),
                properties.getQuestionSkillName(),
                properties.getQuestionCapabilityName());
    }

    private AnalyticsConsoleConversation start(
            AnalyticsConsoleSubject subject,
            String safePrompt,
            AnalyticsConsoleConversationMode mode,
            AnalyticsConsoleAsset asset,
            QuestionScope question,
            String instruction,
            String skillName,
            String capabilityName) {
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
                        instruction,
                        binding.workspaceRef(),
                        binding.modelConfigRef(),
                        binding.modelVariantId(),
                        skillName,
                        capabilityName));
        AnalyticsConsoleConversation conversation = new AnalyticsConsoleConversation(
                conversationId,
                asset == null ? null : asset.assetId(),
                subject.subjectRef(),
                externalConversationRef,
                requestId,
                accepted.askInvocationRef(),
                accepted.runtimeExecutionId(),
                accepted.runtimeTaskId(),
                clock.instant(),
                mode,
                question == null ? null : question.profileId(),
                question == null ? null : question.namespace(),
                question == null ? null : question.modelName(),
                question == null ? null : question.modelRevision(),
                null);
        catalog.update(state -> {
            List<AnalyticsConsoleConversation> conversations =
                    new ArrayList<>(state.conversations());
            conversations.add(conversation);
            return new AnalyticsConsoleCatalogState(
                    state.revision(), state.folders(), state.assets(), conversations);
        });
        return conversation;
    }

    public AnalyticsConsoleConversation continueConversation(
            AnalyticsConsoleSubject subject,
            String conversationId,
            String prompt) {
        AnalyticsConsoleConversation conversation = requireConversation(
                subject, conversationId);
        if (!conversation.ownerSubjectRef().equals(subject.subjectRef())) {
            throw new AnalyticsConsoleCatalogException(
                    "ANALYTICS_CONSOLE_FORBIDDEN",
                    "Only the conversation owner can continue an Analytics conversation");
        }
        AnalyticsConsoleFapBindingResolver.OutboundBinding binding = bindings.resolve(subject);
        String requestId = "analytics-console.ask." + UUID.randomUUID();
        String skillName = conversation.mode() == AnalyticsConsoleConversationMode.QUESTION
                ? properties.getQuestionSkillName()
                : properties.getSkillName();
        String capabilityName = conversation.mode()
                == AnalyticsConsoleConversationMode.QUESTION
                ? properties.getQuestionCapabilityName()
                : properties.getCapabilityName();
        AnalyticsConsoleAgentGateway.Accepted accepted = gateway.continueConversation(
                binding,
                new AnalyticsConsoleAgentGateway.ContinueCommand(
                        requestId,
                        conversation.externalConversationRef(),
                        conversation.runtimeExecutionId(),
                        safePrompt(prompt),
                        binding.modelVariantId(),
                        skillName,
                        capabilityName));
        AnalyticsConsoleAskBinding askBinding = new AnalyticsConsoleAskBinding(
                requestId,
                accepted.askInvocationRef(),
                accepted.runtimeExecutionId(),
                accepted.runtimeTaskId(),
                clock.instant());
        AnalyticsConsoleConversation updated = conversation.withAskBinding(askBinding);
        catalog.update(state -> new AnalyticsConsoleCatalogState(
                state.revision(),
                state.folders(),
                state.assets(),
                state.conversations().stream()
                        .map(value -> value.conversationId().equals(conversationId)
                                ? updated
                                : value)
                        .toList()));
        return updated;
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

    public AnalyticsConsoleAgentGateway.TurnDetail turnDetail(
            AnalyticsConsoleSubject subject,
            String conversationId,
            String askInvocationRef) {
        AnalyticsConsoleConversation conversation = requireConversation(subject, conversationId);
        String expectedAskRef = required(askInvocationRef, "askInvocationRef", 256);
        AnalyticsConsoleAskBinding ask = conversation.askBindings().stream()
                .filter(value -> value.askInvocationRef().equals(expectedAskRef))
                .findFirst()
                .orElseThrow(() -> new AnalyticsConsoleCatalogException(
                        "ANALYTICS_CONSOLE_TURN_NOT_FOUND",
                        "Analytics Console conversation turn was not found"));
        AnalyticsConsoleFapBindingResolver.OutboundBinding binding = bindings.resolve(subject);
        return gateway.turnDetail(
                binding,
                ask.askRequestId(),
                ask.askInvocationRef(),
                conversation.externalConversationRef());
    }

    public AnalyticsConsoleConversation requireCallbackConversation(
            AnalyticsConsoleSubject subject,
            String externalConversationRef,
            String askRequestId,
            String askInvocationRef) {
        return catalog.read().conversations().stream()
                .filter(value -> value.externalConversationRef().equals(externalConversationRef))
                .filter(value -> value.askBinding(askRequestId, askInvocationRef).isPresent())
                .filter(value -> value.ownerSubjectRef().equals(subject.subjectRef()))
                .findFirst()
                .orElseThrow(() -> new AnalyticsConsoleCatalogException(
                        "ANALYTICS_CONSOLE_FAP_CONTEXT_FORBIDDEN",
                        "FAP callback is not bound to an Analytics Console conversation"));
    }

    public AnalyticsConsoleAskBinding requireCallbackAskBinding(
            AnalyticsConsoleConversation conversation,
            String askRequestId,
            String askInvocationRef) {
        return conversation.askBinding(askRequestId, askInvocationRef)
                .orElseThrow(() -> new AnalyticsConsoleCatalogException(
                        "ANALYTICS_CONSOLE_FAP_CONTEXT_FORBIDDEN",
                        "FAP callback Ask binding is not part of the Console conversation"));
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

    private ConversationSummary summarize(AnalyticsConsoleConversation conversation) {
        String displayName = questionProfiles.stream()
                .filter(value -> value.getId().equals(conversation.questionProfileId()))
                .map(AnalyticsConsoleProperties.QuestionProfile::getDisplayName)
                .findFirst()
                .orElse(conversation.questionProfileId());
        Instant lastActivityAt = conversation.askBindings().stream()
                .map(AnalyticsConsoleAskBinding::createdAt)
                .max(Instant::compareTo)
                .orElse(conversation.createdAt());
        return new ConversationSummary(
                conversation.conversationId(),
                displayName,
                conversation.questionProfileId(),
                conversation.createdAt(),
                lastActivityAt,
                conversation.namespace(),
                conversation.modelName(),
                conversation.modelRevision());
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

    private AnalyticsConsoleProperties.QuestionProfile requireProfile(String profileId) {
        String expected = required(profileId, "profileId", 128);
        return questionProfiles.stream()
                .filter(value -> expected.equals(value.getId()))
                .findFirst()
                .orElseThrow(() -> new AnalyticsConsoleCatalogException(
                        "ANALYTICS_CONSOLE_QUESTION_PROFILE_NOT_FOUND",
                        "Analytics question profile was not found"));
    }

    private AnalyticsModelDependencyDescription resolveDependency(
            AnalyticsConsoleProperties.QuestionProfile profile) {
        if (functions == null) {
            throw new AnalyticsConsoleCatalogException(
                    "ANALYTICS_CONSOLE_RUNTIME_UNAVAILABLE",
                    "Analytics Function client is unavailable");
        }
        AnalyticsFunctionEnvelope<AnalyticsModelDependencyDescription> outcome =
                functions.resolveModelDependency(
                        new AnalyticsModelDependencyResolutionRequest(
                                profile.getNamespace(),
                                "qm",
                                profile.getModelName(),
                                new AnalyticsFunctionRequestContext(
                                        "console-question-" + UUID.randomUUID(),
                                        "console-question-" + UUID.randomUUID())));
        if (outcome == null || !outcome.success()) {
            throw new AnalyticsConsoleCatalogException(
                    outcome == null
                            ? "ANALYTICS_CONSOLE_RUNTIME_UNAVAILABLE"
                            : outcome.error().code(),
                    outcome == null
                            ? "Analytics Runtime did not return a model revision"
                            : outcome.error().message());
        }
        AnalyticsModelDependencyDescription value = outcome.data();
        if (!profile.getNamespace().equals(value.namespace())
                || !profile.getModelName().equals(value.modelName())
                || !"qm".equals(value.modelKind())) {
            throw new AnalyticsConsoleCatalogException(
                    "ANALYTICS_CONSOLE_RUNTIME_PROTOCOL_ERROR",
                    "Analytics Runtime resolved a different question model");
        }
        return value;
    }

    private static String questionInstruction(QuestionScope scope) {
        return "You are the Foggy Analytics Console data analyst. "
                + "The server has fixed this conversation to business scope "
                + scope.displayName() + ", namespace " + scope.namespace()
                + ", QM " + scope.modelName() + ", exact revision "
                + scope.modelRevision() + ". "
                + "First describe that exact semantic model when field meaning is not already "
                + "known, then use only the governed semantic query function. Answer the user's "
                + "question directly and cite the returned rows, totals, truncation and warnings. "
                + "Ask a concise clarification when the requested metric or time scope is "
                + "ambiguous. Never request or generate raw SQL, Compose, scripts, credentials, "
                + "authority filters, owner metadata, ACL filters, filesystem paths, HTML, "
                + "JavaScript, iframes, or network access. Do not claim data was returned unless "
                + "the Function result proves it. Do not create or modify a Report or Dashboard.";
    }

    private static String safePrompt(String prompt) {
        return required(prompt, "prompt", 16_000);
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || !value.equals(value.trim())
                || value.length() > maxLength) {
            throw new AnalyticsConsoleCatalogException(
                    "ANALYTICS_CONSOLE_REQUEST_INVALID", field + " is invalid");
        }
        return value;
    }

    public record QuestionProfile(
            String profileId,
            String displayName,
            String description,
            String namespace,
            String modelName) {
    }

    public record ConversationSummary(
            String conversationId,
            String title,
            String questionProfileId,
            Instant createdAt,
            Instant lastActivityAt,
            String namespace,
            String modelName,
            String modelRevision) {
    }

    private record QuestionScope(
            String profileId,
            String displayName,
            String namespace,
            String modelName,
            String modelRevision) {
    }
}
