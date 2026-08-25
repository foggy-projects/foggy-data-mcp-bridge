package com.foggyframework.analytics.console.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.console.catalog.FileAnalyticsConsoleCatalogRepository;
import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAsset;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAssetKind;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAssetStatus;
import com.foggyframework.analytics.console.model.AnalyticsConsoleCatalogState;
import com.foggyframework.analytics.console.model.AnalyticsConsoleConversationMode;
import com.foggyframework.analytics.console.model.AnalyticsConsoleVisibility;
import com.foggyframework.analytics.console.security.AnalyticsConsoleRole;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;
import com.foggyframework.analytics.console.service.AnalyticsConsoleService;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContract;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyDescription;
import com.foggyframework.analytics.function.sdk.AnalyticsFunctionClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AnalyticsConsoleAgentServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void freezesServerOwnedFapSelectionsAndPersistsOnlyOpaqueBindings() throws Exception {
        var catalog = new FileAnalyticsConsoleCatalogRepository(
                tempDir.resolve("catalog.json"), new ObjectMapper());
        AnalyticsConsoleAsset asset = asset();
        catalog.update(state -> new AnalyticsConsoleCatalogState(
                state.revision(), state.folders(), List.of(asset), state.conversations()));
        AnalyticsConsoleService console = mock(AnalyticsConsoleService.class);
        AnalyticsConsoleSubject subject = new AnalyticsConsoleSubject(
                "designer", "Designer", Set.of(AnalyticsConsoleRole.DESIGNER),
                "console", "authority-designer");
        when(console.requireAgentAsset(subject, asset.assetId())).thenReturn(asset);
        AtomicReference<AnalyticsConsoleAgentGateway.StartCommand> captured =
                new AtomicReference<>();
        AnalyticsConsoleAgentGateway gateway = new AnalyticsConsoleAgentGateway() {
            @Override
            public Accepted start(
                    AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
                    StartCommand command) {
                captured.set(command);
                return new Accepted("ask-1", "execution-1", "task-1");
            }

            @Override
            public List<Turn> turns(
                    AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
                    String requestId,
                    String externalConversationRef) {
                return List.of();
            }
        };
        AnalyticsConsoleFapBindingResolver bindings = new AnalyticsConsoleFapBindingResolver() {
            @Override
            public OutboundBinding resolve(AnalyticsConsoleSubject ignored) {
                return new OutboundBinding(
                        "Bearer secret", "workspace-1", "model-1", "variant-1");
            }

            @Override
            public AnalyticsConsoleSubject resolveCaller(
                    com.foggyframework.analytics.function.fap.FapAnalyticsFunctionInvocation.Caller caller) {
                return subject;
            }
        };
        AnalyticsConsoleProperties properties = new AnalyticsConsoleProperties();
        properties.getFap().setSkillName("analytics-guidance");
        properties.getFap().setCapabilityName("analytics-read");
        AnalyticsConsoleAgentService service = new AnalyticsConsoleAgentService(
                console, catalog, gateway, bindings, properties.getFap(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        var conversation = service.start(subject, asset.assetId(), "优化图表");

        assertThat(captured.get().skillName()).isEqualTo("analytics-guidance");
        assertThat(captured.get().capabilityName()).isEqualTo("analytics-read");
        assertThat(captured.get().initialSystemInstruction())
                .contains(asset.bundleRef(), asset.artifactRef())
                .contains("Do not claim");
        assertThat(conversation.askInvocationRef()).isEqualTo("ask-1");
        assertThat(catalog.read().conversations()).containsExactly(conversation);
        assertThat(Files.readString(tempDir.resolve("catalog.json")))
                .doesNotContain("Bearer secret");
        assertThatThrownBy(() -> service.requireCallbackConversation(
                subject,
                conversation.externalConversationRef(),
                "wrong-request",
                conversation.askInvocationRef()))
                .hasMessageContaining("not bound");
    }

    @Test
    void startsAndContinuesAQuestionWithoutCreatingAnAnalyticsAsset() throws Exception {
        var catalog = new FileAnalyticsConsoleCatalogRepository(
                tempDir.resolve("question-catalog.json"), new ObjectMapper());
        AnalyticsConsoleService console = mock(AnalyticsConsoleService.class);
        AnalyticsFunctionClient functions = mock(AnalyticsFunctionClient.class);
        String revision = "sha256:" + "b".repeat(64);
        AnalyticsFunctionContext context = AnalyticsFunctionContext.normalize(
                AnalyticsFunctionRequestContext.empty());
        when(functions.resolveModelDependency(any())).thenReturn(
                AnalyticsFunctionEnvelope.ok(
                        AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                        AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION,
                        new AnalyticsModelDependencyDescription(
                                "default", "qm", "FactOrderQueryModel", revision),
                        context));

        AtomicReference<AnalyticsConsoleAgentGateway.StartCommand> started =
                new AtomicReference<>();
        AtomicReference<AnalyticsConsoleAgentGateway.ContinueCommand> continued =
                new AtomicReference<>();
        AtomicReference<String> tracedRequest = new AtomicReference<>();
        AnalyticsConsoleAgentGateway gateway = new AnalyticsConsoleAgentGateway() {
            @Override
            public Accepted start(
                    AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
                    StartCommand command) {
                started.set(command);
                return new Accepted("ask-question-1", "execution-question", "task-question-1");
            }

            @Override
            public Accepted continueConversation(
                    AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
                    ContinueCommand command) {
                continued.set(command);
                return new Accepted("ask-question-2", "execution-question", "task-question-2");
            }

            @Override
            public List<Turn> turns(
                    AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
                    String requestId,
                    String externalConversationRef) {
                return List.of();
            }

            @Override
            public TurnDetail turnDetail(
                    AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
                    String askRequestId,
                    String expectedAskInvocationRef,
                    String expectedExternalConversationRef) {
                tracedRequest.set(askRequestId);
                return new TurnDetail(expectedAskInvocationRef, "COMPLETE", false, List.of(),
                        List.of(new ToolCall(
                                4,
                                "function-invocation-1",
                                "foggy.analytics.semantic-queries.execute@v1",
                                "SUCCEEDED",
                                Instant.EPOCH,
                                Instant.EPOCH.plusMillis(50),
                                50L,
                                null)));
            }
        };
        AnalyticsConsoleSubject subject = new AnalyticsConsoleSubject(
                "analyst", "Analyst", Set.of(AnalyticsConsoleRole.VIEWER),
                "console", "authority-analyst");
        AnalyticsConsoleFapBindingResolver bindings = bindings(subject);
        AnalyticsConsoleProperties properties = new AnalyticsConsoleProperties();
        AnalyticsConsoleProperties.QuestionProfile profile =
                new AnalyticsConsoleProperties.QuestionProfile();
        profile.setId("orders");
        profile.setDisplayName("订单分析");
        profile.setDescription("受治理的订单问数");
        profile.setNamespace("default");
        profile.setModelName("FactOrderQueryModel");
        properties.setQuestionProfiles(List.of(profile));

        ObjectMapper json = new ObjectMapper();
        AnalyticsConsoleFunctionTraceRepository functionTraces =
                mock(AnalyticsConsoleFunctionTraceRepository.class);
        AnalyticsConsoleAgentService service = new AnalyticsConsoleAgentService(
                console,
                catalog,
                gateway,
                bindings,
                properties.getFap(),
                properties.getQuestionProfiles(),
                functions,
                functionTraces,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        var conversation = service.startQuestion(subject, "orders", "本月订单量是多少？");
        var updated = service.continueConversation(
                subject, conversation.conversationId(), "按销售团队拆分");
        when(functionTraces.findByTurn(
                conversation.conversationId(), "ask-question-2")).thenReturn(List.of(
                        new AnalyticsConsoleFunctionTraceRepository.FunctionTrace(
                                conversation.conversationId(),
                                updated.askBindings().get(1).askRequestId(),
                                "ask-question-2",
                                "function-invocation-1",
                                "foggy.analytics.semantic-queries.execute@v1",
                                json.createObjectNode()
                                        .put("modelName", "FactOrderQueryModel"),
                                json.createObjectNode().put("total", 6),
                                200)));
        var summaries = service.questionConversations(subject);
        var detail = service.turnDetail(
                subject, conversation.conversationId(), "ask-question-2");

        assertThat(conversation.mode()).isEqualTo(AnalyticsConsoleConversationMode.QUESTION);
        assertThat(conversation.assetId()).isNull();
        assertThat(conversation.modelRevision()).isEqualTo(revision);
        assertThat(started.get().initialSystemInstruction())
                .contains("FactOrderQueryModel", revision)
                .contains("Do not create or modify a Report or Dashboard");
        assertThat(started.get().skillName()).isEqualTo("analytics-question-answering");
        assertThat(continued.get().runtimeExecutionId()).isEqualTo("execution-question");
        assertThat(updated.askBindings()).hasSize(2);
        assertThat(detail.askInvocationRef()).isEqualTo("ask-question-2");
        assertThat(detail.toolCalls()).singleElement().satisfies(tool -> {
            assertThat(tool.arguments().path("modelName").asText())
                    .isEqualTo("FactOrderQueryModel");
            assertThat(tool.result().path("total").asInt()).isEqualTo(6);
            assertThat(tool.callbackHttpStatus()).isEqualTo(200);
        });
        assertThat(tracedRequest.get())
                .isEqualTo(updated.askBindings().get(1).askRequestId());
        assertThat(service.conversation(subject, conversation.conversationId()))
                .isEqualTo(updated);
        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.conversationId()).isEqualTo(conversation.conversationId());
            assertThat(summary.title()).isEqualTo("订单分析");
            assertThat(summary.questionProfileId()).isEqualTo("orders");
            assertThat(summary.lastActivityAt()).isEqualTo(Instant.EPOCH);
            assertThat(summary.modelRevision()).isEqualTo(revision);
        });
        assertThat(service.requireCallbackConversation(
                subject,
                updated.externalConversationRef(),
                updated.askBindings().get(1).askRequestId(),
                "ask-question-2")).isEqualTo(updated);
        assertThat(Files.readString(tempDir.resolve("question-catalog.json")))
                .doesNotContain("本月订单量是多少", "按销售团队拆分");
    }

    @Test
    void listsOnlyQuestionConversationsOwnedByTheCurrentSubject() {
        var catalog = new FileAnalyticsConsoleCatalogRepository(
                tempDir.resolve("conversation-list-catalog.json"), new ObjectMapper());
        AnalyticsConsoleSubject analyst = new AnalyticsConsoleSubject(
                "analyst", "Analyst", Set.of(AnalyticsConsoleRole.VIEWER),
                "console", "authority-analyst");
        AnalyticsConsoleSubject other = new AnalyticsConsoleSubject(
                "other", "Other", Set.of(AnalyticsConsoleRole.VIEWER),
                "console", "authority-other");
        String revision = "sha256:" + "c".repeat(64);
        catalog.update(state -> new AnalyticsConsoleCatalogState(
                state.revision(),
                state.folders(),
                state.assets(),
                List.of(
                        questionConversation("question-old", analyst, revision, Instant.EPOCH),
                        questionConversation(
                                "question-new", analyst, revision, Instant.EPOCH.plusSeconds(60)),
                        questionConversation("question-other", other, revision, Instant.EPOCH))));
        AnalyticsConsoleAgentService service = new AnalyticsConsoleAgentService(
                mock(AnalyticsConsoleService.class),
                catalog,
                emptyGateway(),
                bindings(analyst),
                new AnalyticsConsoleProperties().getFap(),
                Clock.systemUTC());

        assertThat(service.questionConversations(analyst))
                .extracting(AnalyticsConsoleAgentService.ConversationSummary::conversationId)
                .containsExactly("question-new", "question-old");
        assertThat(service.questionConversations(other))
                .extracting(AnalyticsConsoleAgentService.ConversationSummary::conversationId)
                .containsExactly("question-other");
    }

    private static AnalyticsConsoleAgentGateway emptyGateway() {
        return new AnalyticsConsoleAgentGateway() {
            @Override
            public Accepted start(
                    AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
                    StartCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Turn> turns(
                    AnalyticsConsoleFapBindingResolver.OutboundBinding binding,
                    String requestId,
                    String externalConversationRef) {
                return List.of();
            }
        };
    }

    private static com.foggyframework.analytics.console.model.AnalyticsConsoleConversation
            questionConversation(
                    String id,
                    AnalyticsConsoleSubject owner,
                    String revision,
                    Instant createdAt) {
        return new com.foggyframework.analytics.console.model.AnalyticsConsoleConversation(
                id,
                null,
                owner.subjectRef(),
                "analytics-console." + id,
                "request-" + id,
                "ask-" + id,
                "execution-" + id,
                "task-" + id,
                createdAt,
                AnalyticsConsoleConversationMode.QUESTION,
                "orders",
                "default",
                "FactOrderQueryModel",
                revision,
                null);
    }

    private static AnalyticsConsoleFapBindingResolver bindings(
            AnalyticsConsoleSubject subject) {
        return new AnalyticsConsoleFapBindingResolver() {
            @Override
            public OutboundBinding resolve(AnalyticsConsoleSubject ignored) {
                return new OutboundBinding(
                        "Bearer secret", "workspace-1", "model-1", "variant-1");
            }

            @Override
            public AnalyticsConsoleSubject resolveCaller(
                    com.foggyframework.analytics.function.fap
                            .FapAnalyticsFunctionInvocation.Caller caller) {
                return subject;
            }
        };
    }

    private static AnalyticsConsoleAsset asset() {
        String revision = "sha256:" + "a".repeat(64);
        return new AnalyticsConsoleAsset(
                "asset-1", "销售分析", "", null, "designer",
                AnalyticsConsoleAssetKind.REPORT, "sales", "sales-report",
                "reports/sales-report.report.json", revision, null,
                AnalyticsConsoleAssetStatus.DRAFT, AnalyticsConsoleVisibility.PRIVATE,
                Set.of(), Instant.EPOCH, Instant.EPOCH, null);
    }
}
