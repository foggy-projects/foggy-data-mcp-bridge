package com.foggyframework.analytics.console.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.console.catalog.FileAnalyticsConsoleCatalogRepository;
import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAsset;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAssetKind;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAssetStatus;
import com.foggyframework.analytics.console.model.AnalyticsConsoleCatalogState;
import com.foggyframework.analytics.console.model.AnalyticsConsoleVisibility;
import com.foggyframework.analytics.console.security.AnalyticsConsoleRole;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;
import com.foggyframework.analytics.console.service.AnalyticsConsoleService;
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
