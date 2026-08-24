package com.foggyframework.analytics.runtime.api;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.runtime.api.config.FoggyAnalyticsRuntimeApiProperties;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsBundlesController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsCapabilitiesController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsModelDependenciesController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsRenderController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsSemanticQueryController;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiResponseFactory;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeHttpResponseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnalyticsRuntimeEndpointQualificationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("foggy.analytics.runtime-api.enabled=true")
            .withUserConfiguration(QualificationConfiguration.class);

    @Test
    void runtimeControllersIgnoreSdkClientsThatAlsoImplementTheEndpointContract() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AnalyticsCapabilitiesController.class);
            assertThat(context).hasSingleBean(AnalyticsBundlesController.class);
            assertThat(context).hasSingleBean(AnalyticsModelDependenciesController.class);
            assertThat(context).hasSingleBean(AnalyticsRenderController.class);
            assertThat(context).hasSingleBean(AnalyticsSemanticQueryController.class);
            assertThat(context).getBeans(AnalyticsFunctionEndpoint.class).hasSize(2);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            AnalyticsCapabilitiesController.class,
            AnalyticsBundlesController.class,
            AnalyticsModelDependenciesController.class,
            AnalyticsRenderController.class,
            AnalyticsSemanticQueryController.class
    })
    static class QualificationConfiguration {

        @Bean
        @AnalyticsRuntimeEndpoint
        AnalyticsFunctionEndpoint runtimeEndpoint() {
            return mock(AnalyticsFunctionEndpoint.class);
        }

        @Bean
        SdkLikeClient sdkClient() {
            return mock(SdkLikeClient.class);
        }

        @Bean
        AnalyticsRuntimeApiResponseFactory responses() {
            FoggyAnalyticsRuntimeApiProperties properties =
                    new FoggyAnalyticsRuntimeApiProperties();
            properties.setEnabled(true);
            return new AnalyticsRuntimeApiResponseFactory(properties);
        }

        @Bean
        AnalyticsRuntimeHttpResponseMapper http() {
            return new AnalyticsRuntimeHttpResponseMapper();
        }
    }

    interface SdkLikeClient extends AnalyticsFunctionEndpoint {
    }
}
