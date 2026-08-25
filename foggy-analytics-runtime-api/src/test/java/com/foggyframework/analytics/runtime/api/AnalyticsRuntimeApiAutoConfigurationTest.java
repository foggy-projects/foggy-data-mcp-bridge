package com.foggyframework.analytics.runtime.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStore;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsCapabilitiesController;
import com.foggyframework.analytics.runtime.core.function.AnalyticsFunctionRenderOperations;
import com.foggyframework.analytics.runtime.core.function.AnalyticsAdvancedSemanticFunctionOperations;
import com.foggyframework.analytics.runtime.core.function.AnalyticsModelDependencyOperations;
import com.foggyframework.analytics.runtime.foggy.FoggySemanticRequestContextResolver;
import com.foggyframework.analytics.runtime.foggy.FoggyComposeCallerResolver;
import com.foggyframework.analytics.runtime.foggy.FoggyStableModelRevisionReadPort;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionPort;
import com.foggyframework.dataset.model.semantic.port.SemanticModelCatalogReadPort;
import com.foggyframework.dataset.model.semantic.port.SemanticQueryExecutionPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnalyticsRuntimeApiAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            AnalyticsRuntimeApiAutoConfiguration.class))
                    .withBean(CatalogSnapshotStore.class, CatalogSnapshotStore::new)
                    .withBean(
                            SemanticModelCatalogReadPort.class,
                            () -> mock(SemanticModelCatalogReadPort.class))
                    .withBean(
                            SemanticQueryExecutionPort.class,
                            () -> mock(SemanticQueryExecutionPort.class));

    @Test
    void remainsAbsentUnlessExplicitlyEnabled() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AnalyticsCapabilitiesController.class);
            assertThat(context).doesNotHaveBean(AnalyticsBundleStore.class);
        });
    }

    @Test
    void startsReadAndValidationLaneWithoutInstallingAnAuthorityFallback() {
        contextRunner
                .withPropertyValues("foggy.analytics.runtime-api.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AnalyticsCapabilitiesController.class);
                    assertThat(context).hasSingleBean(AnalyticsBundleStore.class);
                    assertThat(context).hasSingleBean(FoggyStableModelRevisionReadPort.class);
                    assertThat(context).hasSingleBean(
                            AnalyticsModelDependencyOperations.class);
                    assertThat(context).hasSingleBean(AnalyticsFunctionEndpoint.class);
                    assertThat(context)
                            .doesNotHaveBean(AnalyticsFunctionRenderOperations.class);
                    assertThat(context)
                            .doesNotHaveBean(FoggySemanticRequestContextResolver.class);
                });
    }

    @Test
    void enablesRenderLaneOnlyWhenHostProvidesAuthorityResolution() {
        contextRunner
                .withPropertyValues("foggy.analytics.runtime-api.enabled=true")
                .withBean(
                        FoggySemanticRequestContextResolver.class,
                        () -> (request, resolution) -> null)
                .run(context -> assertThat(context)
                        .hasSingleBean(AnalyticsFunctionRenderOperations.class));
    }

    @Test
    void enablesAdvancedLaneOnlyWithBothCallerAndModelAuthorityResolvers() {
        WebApplicationContextRunner candidate = contextRunner
                .withPropertyValues("foggy.analytics.runtime-api.enabled=true")
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(ComposeExecutionPort.class, () -> mock(ComposeExecutionPort.class))
                .withBean(
                        FoggySemanticRequestContextResolver.class,
                        () -> (request, resolution) -> null)
                .withBean(
                        FoggyComposeCallerResolver.class,
                        () -> request -> null);

        candidate.run(context -> assertThat(context)
                .doesNotHaveBean(AnalyticsAdvancedSemanticFunctionOperations.class));
        candidate.withBean(
                        AuthorityResolver.class,
                        () -> request -> null)
                .run(context -> assertThat(context)
                        .hasSingleBean(AnalyticsAdvancedSemanticFunctionOperations.class));
    }
}
